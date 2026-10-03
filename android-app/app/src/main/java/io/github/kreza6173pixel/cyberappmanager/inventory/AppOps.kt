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

/* Android emits both `camera: allow` and prefixed lines such as `Uid mode: COARSE_LOCATION: foreground`. */
private val APP_OP_PAIR = Regex("([a-z][a-z0-9_]*):\\s*([a-z]+)(?:;\\s*(.*))?$", RegexOption.IGNORE_CASE)

/** Parses appops output by taking the final operation/mode pair on each line. */
fun parseAppOps(packageName: String, text: String): AppOpsAudit {
    val records = linkedMapOf<String, AppOpRecord>()
    for (raw in text.lineSequence()) {
        val match = APP_OP_PAIR.findAll(raw.trim()).lastOrNull() ?: continue
        val op = match.groupValues[1].lowercase()
        val mode = match.groupValues[2].lowercase()
        if (mode in AppOpRecord.CHANGEABLE_MODES && isValidAppOp(op)) {
            records[op] = AppOpRecord(op, mode, match.groupValues.getOrNull(3).orEmpty())
        }
    }
    return AppOpsAudit(packageName, records.values.sortedBy { it.op })
}

fun isValidAppOp(name: String): Boolean = name.matches(Regex("^[a-z][a-z0-9_]*$"))
fun isValidAppOpMode(mode: String): Boolean = mode in AppOpRecord.CHANGEABLE_MODES
fun appOpsSetCommand(pkg: String, op: String, mode: String): String = "appops set $pkg $op $mode"
fun appOpsResetCommand(pkg: String, op: String): String = "appops set $pkg $op default"
