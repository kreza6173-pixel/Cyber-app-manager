package io.github.kreza6173pixel.cyberappmanager.inventory

/** A boot-related receiver found in a package dump. */
data class BootReceiver(val packageName: String, val component: String, val action: String)

data class BackgroundOpState(val op: String, val mode: String)

data class AutostartAudit(val packageName: String, val receivers: List<BootReceiver>, val backgroundOps: List<BackgroundOpState>, val raw: String = "")

/* Component at line start, including dumpsys' optional hex prefix. */
private val COMPONENT = Regex("^(?:[0-9a-f]+\\s+)?([A-Za-z][A-Za-z0-9_.]*/[A-Za-z.][A-Za-z0-9_.$]*)(?=$|[:\\s])")
private val BOOT = Regex("(LOCKED_BOOT_COMPLETED|BOOT_COMPLETED|QUICKBOOT_POWERON|MY_PACKAGE_REPLACED)")
private val OP = Regex("(?:RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND):\\s*([a-z]+)", RegexOption.IGNORE_CASE)

/** Best-effort parser. Only Receiver Resolver Table entries can become boot receivers. */
fun parseAutostartAudit(packageName: String, text: String): AutostartAudit {
    val receivers = linkedSetOf<BootReceiver>()
    var inReceiverTable = true
    var component: String? = null
    var action: String? = null
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        when {
            trimmed == "Receiver Resolver Table:" -> inReceiverTable = true
            trimmed.endsWith(" Resolver Table:") && trimmed != "Receiver Resolver Table:" -> {
                inReceiverTable = false
                component = null
                action = null
            }
            trimmed in setOf("Permissions:", "Registered ContentProviders:", "Packages:", "Queries:", "Dexopt state:", "Compiler stats:") -> {
                inReceiverTable = false
                component = null
                action = null
            }
        }
        if (!inReceiverTable) continue
        COMPONENT.find(trimmed)?.let {
            /* A new component starts a new filter; never carry an action across it. */
            component = it.groupValues[1]
            action = null
        }
        BOOT.find(trimmed)?.let { action = it.groupValues[1] }
        if (component != null && action != null) {
            val c = component!!
            if (c.substringBefore('/') == packageName) receivers += BootReceiver(packageName, c, action!!)
            component = null
            action = null
        }
    }
    val ops = OP.findAll(text).map { BackgroundOpState(it.groupValues[0].substringBefore(':').uppercase(), it.groupValues[1].lowercase()) }.distinctBy { it.op }.toList()
    return AutostartAudit(packageName, receivers.toList(), ops, text)
}
