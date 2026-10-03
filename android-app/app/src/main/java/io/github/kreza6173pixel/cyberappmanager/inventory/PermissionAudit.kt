package io.github.kreza6173pixel.cyberappmanager.inventory

/** Read-only permission state exposed by Android package dumpsys output. */
data class PermissionRecord(
    val name: String,
    val requested: Boolean,
    val granted: Boolean?,
    val appOp: String? = null,
)

data class PermissionAudit(
    val packageName: String,
    val permissions: List<PermissionRecord>,
    val appOps: List<String> = emptyList(),
)

/**
 * Parses the stable permission sections emitted by `dumpsys package <package>`.
 * This deliberately ignores prose and unknown fields so ROM-specific output cannot create fake state.
 */
fun parsePermissionAudit(packageName: String, text: String): PermissionAudit {
    val records = linkedMapOf<String, PermissionRecord>()
    val requested = Regex("^([A-Za-z0-9_.]+)$")
    val grantLine = Regex("^([A-Za-z0-9_.]+): granted=(true|false)")
    val appOps = linkedSetOf<String>()
    var inRequested = false
    var inInstallPermissions = false
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        when {
            line == "requested permissions:" -> { inRequested = true; inInstallPermissions = false }
            line == "install permissions:" -> { inRequested = false; inInstallPermissions = true }
            line.endsWith(":") && line != "requested permissions:" && line != "install permissions:" -> { inRequested = false; inInstallPermissions = false }
            inRequested -> requested.matchEntire(line)?.groupValues?.get(1)?.let { name -> records[name] = PermissionRecord(name, true, records[name]?.granted) }
            inInstallPermissions -> grantLine.find(line)?.let { m ->
                val name = m.groupValues[1]
                val granted = m.groupValues[2] == "true"
                records[name] = PermissionRecord(name, records[name]?.requested == true, granted)
            }
            line.startsWith("AppOp ") -> appOps += line.removePrefix("AppOp ").trim()
        }
    }
    return PermissionAudit(packageName, records.values.sortedBy { it.name }, appOps.toList())
}
