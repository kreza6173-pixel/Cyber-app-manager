package io.github.kreza6173pixel.cyberappmanager.inventory

import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.exec.ExecOutcome
import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only A5 probe. Writes will only be added after a phone probe proves read-back behaviour. */
class AutostartRepository(private val bridge: ExecBridge) {
    suspend fun audit(pkg: String): Result<AutostartAudit> = withContext(Dispatchers.IO) {
        if (!isValidPackageName(pkg)) return@withContext Result.failure(IllegalArgumentException("invalid package name"))
        when (val out = bridge.execBlocking("dumpsys package ${ShellQuoting.quote(pkg)}", 20_000)) {
            is ExecOutcome.Failed -> Result.failure(IllegalStateException(out.message))
            is ExecOutcome.Completed -> if (out.result.truncated) Result.failure(IllegalStateException("output truncated (64 KiB cap)")) else Result.success(parseAutostartAudit(pkg, out.result.stdout))
        }
    }
}

/** Keeps the screen dependent on the repository, not on a second bridge instance. */
suspend fun InventoryRepository.autostartAudit(pkg: String): Result<AutostartAudit> =
    AutostartRepository(bridgeForReadOnlyAudit()).audit(pkg)

/** Internal bridge access for read-only feature repositories. */
fun InventoryRepository.bridgeForReadOnlyAudit(): ExecBridge {
    val field = InventoryRepository::class.java.getDeclaredField("bridge")
    field.isAccessible = true
    return field.get(this) as ExecBridge
}
