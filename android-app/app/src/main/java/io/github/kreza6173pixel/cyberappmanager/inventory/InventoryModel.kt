package io.github.kreza6173pixel.cyberappmanager.inventory

/** Pure inventory model. No Android imports. */

enum class AppState {
    /** Installed for user 0 and not disabled. */
    ENABLED,

    /** Installed for user 0 but disabled (`pm list packages -d`), i.e. frozen. */
    FROZEN,

    /** Known to the system (`-u`) but not installed for user 0: removed for this user. */
    REMOVED,
}

data class AppEntry(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val state: AppState,
    /** Non-null when the guard refuses actions on this package; the value says why. */
    val protectedReason: String?,
)

data class InventoryCounts(
    val total: Int,
    val user: Int,
    val system: Int,
    val frozen: Int,
    val removed: Int,
    val protectedCount: Int,
)

enum class AppFilter {
    ALL, USER, SYSTEM, FROZEN, REMOVED, PROTECTED;

    fun matches(e: AppEntry): Boolean = when (this) {
        ALL -> true
        USER -> !e.isSystem
        SYSTEM -> e.isSystem
        FROZEN -> e.state == AppState.FROZEN
        REMOVED -> e.state == AppState.REMOVED
        PROTECTED -> e.protectedReason != null
    }

    fun count(c: InventoryCounts): Int = when (this) {
        ALL -> c.total
        USER -> c.user
        SYSTEM -> c.system
        FROZEN -> c.frozen
        REMOVED -> c.removed
        PROTECTED -> c.protectedCount
    }
}

/**
 * Builds the app list from the four pm package sets.
 *
 * @param all `pm list packages -u` (includes packages removed for the user)
 * @param installed `pm list packages`
 * @param disabled `pm list packages -d`
 * @param system `pm list packages -s -u`
 */
fun mergeInventory(
    all: Set<String>,
    installed: Set<String>,
    disabled: Set<String>,
    system: Set<String>,
    labels: Map<String, String>,
    guard: (String) -> String?,
): List<AppEntry> {
    val universe = LinkedHashSet<String>(all)
    universe.addAll(installed)
    return universe.map { pkg ->
        val state = when {
            pkg !in installed -> AppState.REMOVED
            pkg in disabled -> AppState.FROZEN
            else -> AppState.ENABLED
        }
        AppEntry(
            pkg = pkg,
            label = labels[pkg]?.takeIf { it.isNotBlank() } ?: pkg,
            isSystem = pkg in system,
            state = state,
            protectedReason = guard(pkg),
        )
    }.sortedWith(compareBy<AppEntry> { it.label.lowercase() }.thenBy { it.pkg })
}

fun countsOf(entries: List<AppEntry>): InventoryCounts = InventoryCounts(
    total = entries.size,
    user = entries.count { !it.isSystem },
    system = entries.count { it.isSystem },
    frozen = entries.count { it.state == AppState.FROZEN },
    removed = entries.count { it.state == AppState.REMOVED },
    protectedCount = entries.count { it.protectedReason != null },
)

fun filterApps(entries: List<AppEntry>, filter: AppFilter, query: String): List<AppEntry> {
    val q = query.trim().lowercase()
    return entries.filter { e ->
        filter.matches(e) &&
            (q.isEmpty() || e.pkg.lowercase().contains(q) || e.label.lowercase().contains(q))
    }
}
