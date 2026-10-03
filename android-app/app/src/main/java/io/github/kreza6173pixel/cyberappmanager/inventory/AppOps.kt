package io.github.kreza6173pixel.cyberappmanager.inventory

/** Android AppOps state for one package. This is separate from manifest/runtime permissions. */
data class AppOpRecord(
    val op: String,
    val mode: String,
    val detail: String = "",
) {
    val changeable: Boolean get() = mode in CHANGEABLE_MODES
    companion object { val CHANGEABLE_MODES = setOf("allow", "deny", "ignore", "foreground", "default") }
}

data class AppOpsAudit(val packageName: String, val operations: List<AppOpRecord>)

private val APP_OP_LINE = Regex("^(?:[A-Za-z0-9_.]+:)?([a-z][a-z0-9_]*):\\s*([a-z]+)(?:;\\s*(.*))?$", RegexOption.IGNORE_CASE)
private val APP_OP_LINE_ALT = Regex("^([a-z][a-z0-9_]*):\\s*([a-z]+)(?:;\\s*(.*))?$", RegexOption.IGNORE_CASE)

/** Parses `appops get <package>` without treating unrelated dumpsys prose as an operation. */
fun parseAppOps(packageName: String, text: String): AppOpsAudit {
    val records = linkedMapOf<String, AppOpRecord>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        val match = APP_OP_LINE_ALT.matchEntire(line) ?: continue
        val op = match.groupValues[1].lowercase()
        val mode = match.groupValues[2].lowercase()
        if (mode in AppOpRecord.CHANGEABLE_MODES) records[op] = AppOpRecord(op, mode, match.groupValues.getOrNull(3).orEmpty())
    }
    return AppOpsAudit(packageName, records.values.sortedBy { it.op })
}

fun isValidAppOp(name: String): Boolean = name.matches(Regex("^[a-z][a-z0-9_]*$"))
fun isValidAppOpMode(mode: String): Boolean = mode in AppOpRecord.CHANGEABLE_MODES
fun appOpsSetCommand(pkg: String, op: String, mode: String): String = "appops set $pkg $op $mode"
fun appOpsResetCommand(pkg: String, op: String): String = "appops set $pkg $op default"
