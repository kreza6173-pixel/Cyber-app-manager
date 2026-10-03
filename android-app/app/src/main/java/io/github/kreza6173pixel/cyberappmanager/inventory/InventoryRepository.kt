package io.github.kreza6173pixel.cyberappmanager.inventory

import android.content.Context
import android.content.pm.PackageManager
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface InventoryResult { data class Ok(val entries: List<AppEntry>, val counts: InventoryCounts) : InventoryResult; data class Error(val message: String) : InventoryResult }
sealed interface DetailsResult { data class Ok(val details: PackageDetails, val raw: String) : DetailsResult; data class Error(val message: String) : DetailsResult }
data class ActionResult(val action: AppAction, val verdict: Verdict, val command: String, val output: String, val readBack: String, val snapshotId: String? = null)

class InventoryRepository(
    private val context: Context,
    private val bridge: ExecBridge,
    private val snapshotStore: SnapshotStore = SnapshotStore(context),
    private val pinStore: PinStore = PinStore(context),
) {
    @Volatile var cached: InventoryResult? = null; private set
    fun entryFor(pkg: String): AppEntry? = (cached as? InventoryResult.Ok)?.entries?.firstOrNull { it.pkg == pkg }
    fun snapshots(): List<Snapshot> = snapshotStore.list()
    fun pins(): Set<String> = pinStore.list()
    fun setPinned(pkg: String, pinned: Boolean): Set<String> = pinStore.set(pkg, pinned)

    suspend fun load(): InventoryResult = withContext(Dispatchers.IO) {
        val outputs = ArrayList<String>(LIST_COMMANDS.size)
        for (cmd in LIST_COMMANDS) when (val r = shell(cmd, false)) {
            is ShellResult.Ok -> outputs.add(r.stdout)
            is ShellResult.Bad -> return@withContext InventoryResult.Error(r.message).also { cached = it }
        }
        val all = parsePackageList(outputs[0]); val installed = parsePackageList(outputs[1])
        val disabled = parsePackageList(outputs[2]); val suspended = parsePackageList(outputs[3]); val system = parsePackageList(outputs[4])
        if (installed.isEmpty()) return@withContext InventoryResult.Error("pm list packages returned no packages").also { cached = it }
        val pm = context.packageManager; val labels = HashMap<String, String>()
        for (pkg in all + installed) labelOf(pm, pkg)?.let { labels[pkg] = it }
        val guard = ProtectedPackages(DeviceRoles.read(context))
        val entries = mergeInventory(all, installed, disabled, suspended, system, labels, guard::reasonFor)
        val result = InventoryResult.Ok(entries, countsOf(entries)); cached = result; result
    }

    suspend fun details(pkg: String): DetailsResult = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext DetailsResult.Error("invalid package name: $pkg")
        val cmd = "dumpsys package " + ShellQuoting.quote(pkg) + " | grep -E " + ShellQuoting.quote(DETAIL_PATTERN) + " | head -n 20"
        when (val r = shell(cmd, true)) { is ShellResult.Ok -> DetailsResult.Ok(parsePackageDetails(r.stdout), r.stdout.trim()); is ShellResult.Bad -> DetailsResult.Error(r.message) }
    }

    suspend fun perform(pkg: String, action: AppAction): ActionResult = withContext(Dispatchers.IO) {
        val entry = entryFor(pkg)
        if (entry == null || action !in AppActions.availableFor(entry)) return@withContext ActionResult(action, Verdict.REFUSED, "", entry?.protectedReason?.let { "protected: $it" } ?: "action not available for this app", "")
        val snapshotId = if (AppActions.needsConfirmation(action)) captureSnapshot("Before ${action.name.lowercase().replace('_', ' ')}") else null
        val cmd = AppActions.command(action, pkg)
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext ActionResult(action, Verdict.FAILED, cmd, o.message, "", snapshotId)
            is ExecOutcome.Completed -> (o.result.stdout + "\n" + o.result.stderr).trim() + "\n(exit " + o.result.exitCode + ")"
        }
        val after = details(pkg); val flags: Map<String, String>; val raw: String
        when (after) { is DetailsResult.Ok -> { flags = after.details.userFlags; raw = after.raw }; is DetailsResult.Error -> { flags = emptyMap(); raw = after.message } }
        val verdict = AppActions.verify(action, flags, output); AppActions.stateFrom(flags)?.let { updateState(pkg, it) }
        ActionResult(action, verdict, cmd, output, raw, snapshotId)
    }

    private fun captureSnapshot(name: String): String? {
        val current = (cached as? InventoryResult.Ok)?.entries ?: return null
        val id = UUID.randomUUID().toString()
        snapshotStore.put(snapshotOf(id, name, System.currentTimeMillis(), current))
        return id
    }

    private fun updateState(pkg: String, state: AppState) { val ok = cached as? InventoryResult.Ok ?: return; val entries = ok.entries.map { if (it.pkg == pkg) it.copy(state = state) else it }; cached = InventoryResult.Ok(entries, countsOf(entries)) }
    private fun labelOf(pm: PackageManager, pkg: String): String? = runCatching { @Suppress("DEPRECATION") val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS); pm.getApplicationLabel(info).toString() }.getOrNull()
    private sealed interface ShellResult { data class Ok(val stdout: String) : ShellResult; data class Bad(val message: String) : ShellResult }
    private fun shell(command: String, allowExitOne: Boolean): ShellResult = when (val outcome = bridge.execBlocking(command, TIMEOUT_MS)) { is ExecOutcome.Failed -> ShellResult.Bad(outcome.message); is ExecOutcome.Completed -> { val r = outcome.result; when { r.truncated -> ShellResult.Bad("output truncated (64 KiB cap): $command"); r.exitCode == 0 || (allowExitOne && r.exitCode == 1) -> ShellResult.Ok(r.stdout); else -> ShellResult.Bad("exit ${r.exitCode}: $command\n${r.stderr.trim()}") } } }
    private companion object { const val TIMEOUT_MS = 20_000; val LIST_COMMANDS = listOf("pm list packages -u | sed 's/^package://'", "pm list packages | sed 's/^package://'", "pm list packages -d | sed 's/^package://'", "pm list packages --suspended | sed 's/^package://'", "pm list packages -s -u | sed 's/^package://'"); const val DETAIL_PATTERN = "versionName=|versionCode=|firstInstallTime=|lastUpdateTime=|installerPackageName=|User 0:" }
}
