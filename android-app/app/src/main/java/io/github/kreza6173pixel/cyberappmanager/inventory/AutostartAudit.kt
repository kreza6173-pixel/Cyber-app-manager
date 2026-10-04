package io.github.kreza6173pixel.cyberappmanager.inventory

/** A boot-related receiver found in a package dump. */
data class BootReceiver(val packageName: String, val component: String, val action: String)

data class BackgroundOpState(val op: String, val mode: String)

data class AutostartAudit(val packageName: String, val receivers: List<BootReceiver>, val backgroundOps: List<BackgroundOpState>, val raw: String = "")

/* Component at line start, including dumpsys' optional hex prefix. */
private val COMPONENT = Regex("^(?:[0-9a-f]+\\s+)?([A-Za-z][A-Za-z0-9_.]*/[A-Za-z.][A-Za-z0-9_.$]*)(?=$|[:\\s])")
private val BOOT = Regex("(LOCKED_BOOT_COMPLETED|BOOT_COMPLETED|QUICKBOOT_POWERON|MY_PACKAGE_REPLACED)")
private val ACTION_HEADER = Regex("^[a-z][a-z0-9_.]*:$")
private val OP = Regex("(?:RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND):\\s*([a-z]+)", RegexOption.IGNORE_CASE)

/** Best-effort parser. Boot actions are bound to the component in the same resolver filter. */
fun parseAutostartAudit(packageName: String, text: String): AutostartAudit {
    val receivers = linkedSetOf<BootReceiver>()
    var inReceiverTable = false
    var component: String? = null
    var filterBoot: String? = null
    var pendingBootHeader: String? = null
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        when {
            trimmed == "Receiver Resolver Table:" -> { inReceiverTable = true; component = null; filterBoot = null; pendingBootHeader = null }
            trimmed.endsWith(" Resolver Table:") -> { inReceiverTable = false; component = null; filterBoot = null; pendingBootHeader = null }
            trimmed in setOf("Permissions:", "Registered ContentProviders:", "Packages:", "Queries:", "Dexopt state:", "Compiler stats:") -> { inReceiverTable = false; component = null; filterBoot = null; pendingBootHeader = null }
        }
        if (!inReceiverTable) continue

        /* A new action header starts a new resolver filter. */
        if (ACTION_HEADER.matches(trimmed)) {
            component = null
            filterBoot = null
            pendingBootHeader = BOOT.find(trimmed)?.groupValues?.get(1)
            continue
        }
        COMPONENT.find(trimmed)?.let {
            component = it.groupValues[1]
            filterBoot = pendingBootHeader
            pendingBootHeader = null
            if (filterBoot != null && component.substringBefore('/') == packageName) receivers += BootReceiver(packageName, component, filterBoot!!)
        }
        /* Supports compact test/dumpsys lines: pkg/.Receiver: BOOT_COMPLETED. */
        if (component == null) {
            val compact = COMPONENT.find(trimmed)
            val boot = BOOT.find(trimmed)
            if (compact != null && boot != null && compact.groupValues[1].substringBefore('/') == packageName) receivers += BootReceiver(packageName, compact.groupValues[1], boot.groupValues[1])
        }
    }
    val ops = OP.findAll(text).map { BackgroundOpState(it.groupValues[0].substringBefore(':').uppercase(), it.groupValues[1].lowercase()) }.distinctBy { it.op }.toList()
    return AutostartAudit(packageName, receivers.toList(), ops, text)
}
