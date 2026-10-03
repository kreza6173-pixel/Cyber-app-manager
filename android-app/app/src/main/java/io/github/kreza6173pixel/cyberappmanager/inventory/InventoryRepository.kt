package io.github.kreza6173pixel.cyberappmanager.inventory

import android.content.Context
import android.content.pm.PackageManager
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface InventoryResult {
    data class Ok(val entries: List<AppEntry>, val counts: InventoryCounts) : InventoryResult
    data class Error(val message: String) : InventoryResult
}

sealed interface DetailsResult {
    data class Ok(val details: PackageDetails, val raw: String) : DetailsResult
    data class Error(val message: String) : DetailsResult
}

/**
 * Package state comes from `pm` (the tool A2 uses to change it), labels from PackageManager.
 * Every call runs through the Shizuku user service on Dispatchers.IO.
 */
class InventoryRepository(private val context: Context, private val bridge: ExecBridge) {

    /** Last loaded inventory, kept so returning from the detail screen does not reload. */
    @Volatile
    var cached: InventoryResult? = null
        private set

    fun entryFor(pkg: String): AppEntry? =
        (cached as? InventoryResult.Ok)?.entries?.firstOrNull { it.pkg == pkg }

    suspend fun load(): InventoryResult = withContext(Dispatchers.IO) {
        val outputs = ArrayList<String>(LIST_COMMANDS.size)
        for (cmd in LIST_COMMANDS) {
            when (val r = shell(cmd, allowExitOne = false)) {
                is ShellResult.Ok -> outputs.add(r.stdout)
                is ShellResult.Bad -> {
                    val error = InventoryResult.Error(r.message)
                    cached = error
                    return@withContext error
                }
            }
        }
        val all = parsePackageList(outputs[0])
        val installed = parsePackageList(outputs[1])
        val disabled = parsePackageList(outputs[2])
        val system = parsePackageList(outputs[3])

        if (installed.isEmpty()) {
            // An empty list would make every app look removed. Never show that.
            val error = InventoryResult.Error("pm list packages returned no packages")
            cached = error
            return@withContext error
        }

        val pm = context.packageManager
        val labels = HashMap<String, String>()
        for (pkg in all + installed) {
            labelOf(pm, pkg)?.let { labels[pkg] = it }
        }
        val guard = ProtectedPackages(DeviceRoles.read(context))
        val entries = mergeInventory(all, installed, disabled, system, labels, guard::reasonFor)
        val result = InventoryResult.Ok(entries, countsOf(entries))
        cached = result
        result
    }

    suspend fun details(pkg: String): DetailsResult = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) {
            return@withContext DetailsResult.Error("invalid package name: $pkg")
        }
        val cmd = "dumpsys package " + ShellQuoting.quote(pkg) +
            " | grep -E " + ShellQuoting.quote(DETAIL_PATTERN) + " | head -n 20"
        when (val r = shell(cmd, allowExitOne = true)) {
            is ShellResult.Ok -> DetailsResult.Ok(parsePackageDetails(r.stdout), r.stdout.trim())
            is ShellResult.Bad -> DetailsResult.Error(r.message)
        }
    }

    private fun labelOf(pm: PackageManager, pkg: String): String? = runCatching {
        @Suppress("DEPRECATION")
        val info = pm.getApplicationInfo(
            pkg,
            PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        pm.getApplicationLabel(info).toString()
    }.getOrNull()

    private sealed interface ShellResult {
        data class Ok(val stdout: String) : ShellResult
        data class Bad(val message: String) : ShellResult
    }

    /** A truncated or failed command is an error, never a silently short list. */
    private fun shell(command: String, allowExitOne: Boolean): ShellResult =
        when (val outcome = bridge.execBlocking(command, TIMEOUT_MS)) {
            is ExecOutcome.Failed -> ShellResult.Bad(outcome.message)
            is ExecOutcome.Completed -> {
                val r = outcome.result
                when {
                    r.truncated -> ShellResult.Bad("output truncated (64 KiB cap): $command")
                    r.exitCode == 0 || (allowExitOne && r.exitCode == 1) -> ShellResult.Ok(r.stdout)
                    else -> ShellResult.Bad("exit " + r.exitCode + ": " + command + "\n" + r.stderr.trim())
                }
            }
        }

    private companion object {
        const val TIMEOUT_MS = 20_000

        /** Order matters: all, installed, disabled, system. `sed` keeps each list small. */
        val LIST_COMMANDS = listOf(
            "pm list packages -u | sed 's/^package://'",
            "pm list packages | sed 's/^package://'",
            "pm list packages -d | sed 's/^package://'",
            "pm list packages -s -u | sed 's/^package://'",
        )

        const val DETAIL_PATTERN =
            "versionName=|versionCode=|firstInstallTime=|lastUpdateTime=|installerPackageName=|User 0:"
    }
}
