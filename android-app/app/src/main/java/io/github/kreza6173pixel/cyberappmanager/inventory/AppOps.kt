package io.github.kreza6173pixel.cyberappmanager.inventory

/**
 * Android AppOps state for one package. This is separate from manifest/runtime permissions.
 * [oem] marks vendor operations such as `MIUIOP(10008)`: shown for honesty, never changeable.
 * [alsoReported] lists other modes Android printed for the same op (for example uid scope and package scope).
 */
data class AppOpRecord(
    val op: String,
    val mode: String,
    val detail: String = "",
    val oem: Boolean = false,
    val alsoReported: List<String> = emptyList(),
) {
    val changeable: Boolean get() = !oem && mode in CHANGEABLE_MODES
    companion object { val CHANGEABLE_MODES = setOf("allow", "deny", "ignore", "foreground", "default") }
}

data class AppOpsAudit(val packageName: String, val operations: List<AppOpRecord>)

/*
 * Android emits both `camera: allow` and prefixed lines such as `Uid mode: COARSE_LOCATION: foreground`.
 * The line start stays anchored so fragments like `name: deny` inside `bad-name: deny` are never parsed;
 * only an optional word prefix (letters and spaces, then a colon) is allowed before the operation.
 * OEM ops carry a numeric suffix in parentheses, for example `MIUIOP(10008): allow`.
 */
private val APP_OP_LINE = Regex(
    "^(?:[A-Za-z ]+:\\s*)?([a-z][a-z0-9_]*(?:\\(\\d+\\))?):\\s*([a-z]+)(?:;\\s*(.*))?$",
    RegexOption.IGNORE_CASE,
)
private val OEM_APP_OP = Regex("^[a-z][a-z0-9_]*\\(\\d+\\)$")

/** Parses `appops get <package>` without treating unrelated prose or malformed names as operations. */
fun parseAppOps(packageName: String, text: String): AppOpsAudit {
    val records = linkedMapOf<String, AppOpRecord>()
    for (raw in text.lineSequence()) {
        val match = APP_OP_LINE.find(raw.trim()) ?: continue
        val op = match.groupValues[1].lowercase()
        val mode = match.groupValues[2].lowercase()
        if (mode !in AppOpRecord.CHANGEABLE_MODES) continue
        val oem = OEM_APP_OP.matches(op)
        if (!oem && !isValidAppOp(op)) continue
        val previous = records[op]
        val also = if (previous == null) emptyList() else (previous.alsoReported + previous.mode).distinct().filter { it != mode }
        records[op] = AppOpRecord(op, mode, match.groupValues.getOrNull(3).orEmpty(), oem, also)
    }
    return AppOpsAudit(packageName, records.values.sortedBy { it.op })
}

/** Strict: OEM ops never pass, so set/reset commands can only target standard AppOps names. */
fun isValidAppOp(name: String): Boolean = name.matches(Regex("^[a-z][a-z0-9_]*$"))
fun isValidAppOpMode(mode: String): Boolean = mode in AppOpRecord.CHANGEABLE_MODES
fun appOpsSetCommand(pkg: String, op: String, mode: String): String = "appops set $pkg $op $mode"
fun appOpsResetCommand(pkg: String, op: String): String = "appops set $pkg $op default"
