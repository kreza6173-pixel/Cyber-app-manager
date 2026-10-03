package io.github.kreza6173pixel.cyberappmanager.inventory

/**
 * `grep -E` pattern that keeps every line [parsePermissionAudit] can use: the three section headers,
 * `User N:` lines, and permission-shaped lines. Large apps (Drive: 128886 bytes) exceed the 64 KiB
 * ExecBridge cap with the full `dumpsys package` output, so filtering happens on the device.
 */
const val PERMISSION_DUMP_PATTERN = "^[[:space:]]*(requested permissions:|install permissions:|runtime permissions:|User [0-9]+:|[A-Za-z][A-Za-z0-9_.]*(: .*)?$)"

/**
 * Rebuilds `grep -n` output. A gap in line numbers means Android printed a line that was filtered out;
 * it becomes a blank line so [parsePermissionAudit] ends the current section exactly as on the full dump.
 */
fun joinNumberedLines(text: String): String {
    val out = StringBuilder()
    var previous = -1
    for (raw in text.lineSequence()) {
        val colon = raw.indexOf(':')
        if (colon <= 0) continue
        val number = raw.substring(0, colon).toIntOrNull() ?: continue
        if (previous >= 0 && number != previous + 1) out.append('\n')
        out.append(raw, colon + 1, raw.length).append('\n')
        previous = number
    }
    return out.toString()
}
