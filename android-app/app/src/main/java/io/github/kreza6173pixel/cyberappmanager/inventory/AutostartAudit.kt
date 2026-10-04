package io.github.kreza6173pixel.cyberappmanager.inventory

/** A boot-related receiver found in a package dump. */
data class BootReceiver(val packageName: String, val component: String, val action: String)

data class BackgroundOpState(val op: String, val mode: String)

data class AutostartAudit(val packageName: String, val receivers: List<BootReceiver>, val backgroundOps: List<BackgroundOpState>, val raw: String = "")

/*
 * Component at line start, e.g. `pkg/.BootReceiver:`, `pkg/pkg.BootReceiver: ACTION`
 * or dumpsys style `a1b2c3 pkg/.BootReceiver filter 99`. Ends on ':', whitespace or EOL.
 */
private val COMPONENT = Regex("^(?:[0-9a-f]+\\s+)?([A-Za-z][A-Za-z0-9_.]*/[A-Za-z.][A-Za-z0-9_.$]*)(?=$|[:\\s])")
private val BOOT = Regex("(LOCKED_BOOT_COMPLETED|BOOT_COMPLETED|QUICKBOOT_POWERON|MY_PACKAGE_REPLACED)")
private val OP = Regex("(?:RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND):\\s*([a-z]+)", RegexOption.IGNORE_CASE)

/** Best-effort parser based on the proven void-autostart dump strategy. Unknown ROM shapes stay in raw output. */
fun parseAutostartAudit(packageName: String, text: String): AutostartAudit {
    val receivers = linkedSetOf<BootReceiver>()
    var component: String? = null
    var action: String? = null
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        COMPONENT.find(trimmed)?.let { component = it.groupValues[1] }
        BOOT.find(trimmed)?.let { action = it.groupValues[1] }
        if (component != null && action != null) {
            val c = component!!
            if (c.substringBefore('/') == packageName) receivers += BootReceiver(packageName, c, action!!)
            component = null; action = null
        }
    }
    val ops = OP.findAll(text).map { BackgroundOpState(it.groupValues[0].substringBefore(':').uppercase(), it.groupValues[1].lowercase()) }.distinctBy { it.op }.toList()
    return AutostartAudit(packageName, receivers.toList(), ops, text)
}
