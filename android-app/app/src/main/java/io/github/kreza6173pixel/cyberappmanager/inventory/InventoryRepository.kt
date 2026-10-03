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
sealed interface PermissionAuditResult { data class Ok(val audit: PermissionAudit) : PermissionAuditResult; data class Error(val message: String) : PermissionAuditResult }
data class ActionResult(val action: AppAction, val verdict: Verdict, val command: String, val output: String, val readBack: String, val snapshotId: String? = null)
data class PermissionChangeResult(val pkg: String, val permission: String, val grant: Boolean, val verdict: Verdict, val command: String, val output: String, val before: Boolean?, val after: Boolean?)
data class RestoreStepResult(val pkg: String, val operation: SnapshotOperation, val verdict: Verdict, val detail: String)
data class RestoreReport(val planned: Int, val results: List<RestoreStepResult>, val safetySnapshotId: String?)
data class BatchItemResult(val pkg: String, val verdict: Verdict, val detail: String)
data class BatchReport(val action: AppAction, val results: List<BatchItemResult>, val snapshotId: String?)

/** Maps a planned snapshot step to the shell action that performs it. */
fun actionFor(op: SnapshotOperation): AppAction = when (op) {
    SnapshotOperation.RESTORE_PACKAGE -> AppAction.RESTORE
    SnapshotOperation.ENABLE -> AppAction.UNFREEZE
    SnapshotOperation.DISABLE -> AppAction.FREEZE
    SnapshotOperation.SUSPEND -> AppAction.SUSPEND
    SnapshotOperation.UNSUSPEND -> AppAction.UNSUSPEND
}

/** A package can join a batch only if it is not protected and the action fits its current state. */
fun eligibleForBatch(e: AppEntry, action: AppAction): Boolean = e.protectedReason == null && action in AppActions.availableFor(e)

class InventoryRepository(private val context: Context, private val bridge: ExecBridge, private val snapshotStore: SnapshotStore = SnapshotStore(context), private val pinStore: PinStore = PinStore(context)) {
    @Volatile var cached: InventoryResult? = null; private set
    fun entryFor(pkg: String): AppEntry? = (cached as? InventoryResult.Ok)?.entries?.firstOrNull { it.pkg == pkg }
    fun snapshots(): List<Snapshot> = snapshotStore.list()
    fun deleteSnapshot(id: String): Boolean = snapshotStore.delete(id)
    fun exportSnapshots(): String = snapshotStore.exportJson()
    fun importSnapshots(text: String): Int = snapshotStore.importJson(text)
    fun pins(): Set<String> = pinStore.list()
    fun setPinned(pkg: String, pinned: Boolean): Set<String> = pinStore.set(pkg, pinned)

    suspend fun load(): InventoryResult = withContext(Dispatchers.IO) {
        val outputs = ArrayList<String>(LIST_COMMANDS.size)
        for (cmd in LIST_COMMANDS) when (val r = shell(cmd, false)) { is ShellResult.Ok -> outputs.add(r.stdout); is ShellResult.Bad -> return@withContext InventoryResult.Error(r.message).also { cached = it } }
        val all = parsePackageList(outputs[0]); val installed = parsePackageList(outputs[1]); val disabled = parsePackageList(outputs[2]); val suspended = parsePackageList(outputs[3]); val system = parsePackageList(outputs[4])
        if (installed.isEmpty()) return@withContext InventoryResult.Error("pm list packages returned no packages").also { cached = it }
        val pm = context.packageManager; val labels = HashMap<String, String>(); for (pkg in all + installed) labelOf(pm, pkg)?.let { labels[pkg] = it }
        val guard = ProtectedPackages(DeviceRoles.read(context)); val entries = mergeInventory(all, installed, disabled, suspended, system, labels, guard::reasonFor); val result = InventoryResult.Ok(entries, countsOf(entries)); cached = result; result
    }

    suspend fun details(pkg: String): DetailsResult = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext DetailsResult.Error("invalid package name: $pkg")
        val cmd = "dumpsys package " + ShellQuoting.quote(pkg) + " | grep -E " + ShellQuoting.quote(DETAIL_PATTERN) + " | head -n 20"
        when (val r = shell(cmd, true)) { is ShellResult.Ok -> { val parsed = parsePackageDetails(r.stdout); AppActions.stateFrom(parsed.userFlags)?.let { updateState(pkg, it) }; DetailsResult.Ok(parsed, r.stdout.trim()) }; is ShellResult.Bad -> DetailsResult.Error(r.message) }
    }

    /**
     * Read-only audit. Reads only the `Packages:` block of dumpsys (keeps output small and section headers intact);
     * falls back to a header-preserving grep if a ROM prints no such block.
     */
    suspend fun permissionAudit(pkg: String): PermissionAuditResult = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext PermissionAuditResult.Error("invalid package name: $pkg")
        val q = ShellQuoting.quote(pkg)
        val primary = when (val r = shell("dumpsys package $q | sed -n '/^Packages:/,/^[A-Za-z]/p'", true)) {
            is ShellResult.Ok -> r.stdout
            is ShellResult.Bad -> return@withContext PermissionAuditResult.Error(r.message)
        }
        val source = if (primary.contains("requested permissions:") || primary.contains("install permissions:")) primary else {
            when (val f = shell("dumpsys package $q | grep -E " + ShellQuoting.quote(PERMISSION_FALLBACK), true)) {
                is ShellResult.Ok -> f.stdout
                is ShellResult.Bad -> return@withContext PermissionAuditResult.Error(f.message)
            }
        }
        PermissionAuditResult.Ok(parsePermissionAudit(pkg, source))
    }

    /**
     * Grants or revokes one runtime permission for user 0. Refused for protected apps, unknown state,
     * install-time permissions and SYSTEM_FIXED / POLICY_FIXED permissions. APPLIED only when the read-back matches.
     */
    suspend fun setPermission(pkg: String, permission: String, grant: Boolean): PermissionChangeResult = withContext(Dispatchers.IO) {
        fun refused(reason: String, before: Boolean? = null) = PermissionChangeResult(pkg, permission, grant, Verdict.REFUSED, "", reason, before, before)
        if (!isValidPackageName(pkg) || !isValidPermissionName(permission)) return@withContext refused("invalid package or permission name")
        val entry = entryFor(pkg) ?: return@withContext refused("inventory not loaded")
        entry.protectedReason?.let { return@withContext refused("protected: $it") }
        val before = (permissionAudit(pkg) as? PermissionAuditResult.Ok)?.audit?.permissions?.firstOrNull { it.name == permission }
            ?: return@withContext refused("permission state unavailable")
        if (!before.changeable) return@withContext refused(if (before.fixed) "fixed by system or policy" else "not a changeable runtime permission", before.granted)
        if (before.granted == grant) return@withContext refused("already in the requested state", before.granted)
        val cmd = (if (grant) "pm grant" else "pm revoke") + " --user 0 " + ShellQuoting.quote(pkg) + " " + ShellQuoting.quote(permission)
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return@withContext PermissionChangeResult(pkg, permission, grant, Verdict.FAILED, cmd, o.message, before.granted, null)
            is ExecOutcome.Completed -> (o.result.stdout + "\n" + o.result.stderr).trim() + "\n(exit " + o.result.exitCode + ")"
        }
        val afterAudit = permissionAudit(pkg) as? PermissionAuditResult.Ok
        val after = afterAudit?.audit?.permissions?.firstOrNull { it.name == permission }?.granted
        val verdict = when {
            afterAudit == null -> Verdict.UNVERIFIABLE
            after == grant -> Verdict.APPLIED
            else -> Verdict.NOT_APPLIED
        }
        PermissionChangeResult(pkg, permission, grant, verdict, cmd, output, before.granted, after)
    }

    suspend fun perform(pkg: String, action: AppAction): ActionResult = withContext(Dispatchers.IO) {
        val entry = entryFor(pkg)
        if (entry == null || action !in AppActions.availableFor(entry)) return@withContext ActionResult(action, Verdict.REFUSED, "", entry?.protectedReason?.let { "protected: $it" } ?: "action not available for this app", "")
        val snapshotId = if (AppActions.needsConfirmation(action)) captureSnapshot("Before ${action.name.lowercase().replace('_', ' ')}") else null
        execute(pkg, action, snapshotId)
    }

    /** One snapshot for the whole batch, then one package at a time with read-back. Ineligible packages are reported, never run. */
    suspend fun performBatch(pkgs: List<String>, action: AppAction): BatchReport = withContext(Dispatchers.IO) {
        val targets = pkgs.distinct()
        val eligible = targets.filter { pkg -> entryFor(pkg)?.let { eligibleForBatch(it, action) } == true }.toSet()
        val snapshotId = if (eligible.isNotEmpty() && action != AppAction.FORCE_STOP) captureSnapshot("Before batch ${action.name.lowercase().replace('_', ' ')}") else null
        val results = targets.map { pkg ->
            if (pkg !in eligible) BatchItemResult(pkg, Verdict.REFUSED, entryFor(pkg)?.protectedReason?.let { "protected: $it" } ?: "not available for current state")
            else { val r = execute(pkg, action, snapshotId); BatchItemResult(pkg, r.verdict, r.output) }
        }
        BatchReport(action, results, snapshotId)
    }

    private suspend fun execute(pkg: String, action: AppAction, snapshotId: String?): ActionResult {
        val cmd = AppActions.command(action, pkg)
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return ActionResult(action, Verdict.FAILED, cmd, o.message, "", snapshotId)
            is ExecOutcome.Completed -> (o.result.stdout + "\n" + o.result.stderr).trim() + "\n(exit " + o.result.exitCode + ")"
        }
        val after = details(pkg)
        val flags: Map<String, String>
        val raw: String
        when (after) { is DetailsResult.Ok -> { flags = after.details.userFlags; raw = after.raw }; is DetailsResult.Error -> { flags = emptyMap(); raw = after.message } }
        val verdict = AppActions.verify(action, flags, output)
        AppActions.stateFrom(flags)?.let { updateState(pkg, it) }
        return ActionResult(action, verdict, cmd, output, raw, snapshotId)
    }

    /** Steps needed to return the device to [id]. Protected packages are never planned. */
    suspend fun planRestore(id: String): List<SnapshotStep>? = withContext(Dispatchers.IO) {
        val snapshot = snapshotStore.get(id) ?: return@withContext null
        val current = currentEntries() ?: return@withContext null
        val byPkg = current.associateBy { it.pkg }
        planSnapshotRestore(snapshot, current) { pkg -> byPkg[pkg]?.protectedReason != null }
    }

    /** Undo: saves a safety snapshot, then applies each step one package at a time with read-back. */
    suspend fun restoreSnapshot(id: String): RestoreReport = withContext(Dispatchers.IO) {
        val steps = planRestore(id) ?: return@withContext RestoreReport(0, emptyList(), null)
        if (steps.isEmpty()) return@withContext RestoreReport(0, emptyList(), null)
        val safety = captureSnapshot("Before undo")
        RestoreReport(steps.size, steps.map { runStep(it) }, safety)
    }

    private suspend fun runStep(step: SnapshotStep): RestoreStepResult {
        val action = actionFor(step.operation)
        if (entryFor(step.pkg)?.protectedReason != null) return RestoreStepResult(step.pkg, step.operation, Verdict.REFUSED, "protected")
        val cmd = runCatching { AppActions.command(action, step.pkg) }.getOrNull() ?: return RestoreStepResult(step.pkg, step.operation, Verdict.REFUSED, "invalid package name")
        val output = when (val o = bridge.execBlocking(cmd, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> return RestoreStepResult(step.pkg, step.operation, Verdict.FAILED, o.message)
            is ExecOutcome.Completed -> (o.result.stdout + "\n" + o.result.stderr).trim()
        }
        val flags = (details(step.pkg) as? DetailsResult.Ok)?.details?.userFlags ?: emptyMap()
        return RestoreStepResult(step.pkg, step.operation, AppActions.verify(action, flags, output), output)
    }

    private suspend fun currentEntries(): List<AppEntry>? = (cached as? InventoryResult.Ok)?.entries ?: (load() as? InventoryResult.Ok)?.entries

    private fun captureSnapshot(name: String): String? {
        val current = (cached as? InventoryResult.Ok)?.entries ?: return null
        synchronized(snapshotStore) {
            val now = System.currentTimeMillis()
            val latest = snapshotStore.list().firstOrNull()
            if (latest != null && latest.name == name && now - latest.createdAtMs in 0..10_000 && latest.entries == current.map { SnapshotEntry(it.pkg, it.isSystem, it.state) }.sortedBy { it.pkg }) return latest.id
            val id = UUID.randomUUID().toString()
            snapshotStore.put(snapshotOf(id, name, now, current))
            return id
        }
    }

    private fun updateState(pkg: String, state: AppState) { val ok = cached as? InventoryResult.Ok ?: return; val entries = ok.entries.map { if (it.pkg == pkg) it.copy(state = state) else it }; cached = InventoryResult.Ok(entries, countsOf(entries)) }
    private fun labelOf(pm: PackageManager, pkg: String): String? = runCatching { @Suppress("DEPRECATION") val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS); pm.getApplicationLabel(info).toString() }.getOrNull()
    private sealed interface ShellResult { data class Ok(val stdout: String) : ShellResult; data class Bad(val message: String) : ShellResult }
    private fun shell(command: String, allowExitOne: Boolean): ShellResult = when (val outcome = bridge.execBlocking(command, TIMEOUT_MS)) { is ExecOutcome.Failed -> ShellResult.Bad(outcome.message); is ExecOutcome.Completed -> { val r = outcome.result; when { r.truncated -> ShellResult.Bad("output truncated (64 KiB cap): $command"); r.exitCode == 0 || (allowExitOne && r.exitCode == 1) -> ShellResult.Ok(r.stdout); else -> ShellResult.Bad("exit ${r.exitCode}: $command\n${r.stderr.trim()}") } } }
    private companion object {
        const val TIMEOUT_MS = 20_000
        val LIST_COMMANDS = listOf("pm list packages -u | sed 's/^package://'", "pm list packages | sed 's/^package://'", "pm list packages -d | sed 's/^package://'", "pm list packages --suspended | sed 's/^package://'", "pm list packages -s -u | sed 's/^package://'")
        const val DETAIL_PATTERN = "versionName=|versionCode=|firstInstallTime=|lastUpdateTime=|installerPackageName=|User 0:"
        const val PERMISSION_FALLBACK = "requested permissions:|install permissions:|runtime permissions:|^[[:space:]]*User [0-9]+:|^[[:space:]]+[A-Za-z][A-Za-z0-9_.]*(: .*)?$"
    }
}
