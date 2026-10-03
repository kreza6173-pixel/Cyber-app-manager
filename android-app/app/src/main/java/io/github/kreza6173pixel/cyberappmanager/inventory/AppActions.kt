package io.github.kreza6173pixel.cyberappmanager.inventory

import io.github.kreza6173pixel.cyberappmanager.exec.ShellQuoting

enum class AppAction { FREEZE, UNFREEZE, FORCE_STOP, REMOVE, RESTORE, CLEAR_DATA }

enum class Verdict {
    /** The read-back shows the requested state. */
    APPLIED,

    /** The read-back shows the old state (or something else). */
    NOT_APPLIED,

    /** The command reported success but Android offers no read-back for it. */
    UNVERIFIABLE,

    /** The guard or the current state forbids the action; nothing was run. */
    REFUSED,

    /** The command could not be run at all. */
    FAILED,
}

/** Pure rules for A2 actions. No Android imports. */
object AppActions {

    fun availableFor(e: AppEntry): List<AppAction> {
        if (e.protectedReason != null) return emptyList()
        return when (e.state) {
            AppState.ENABLED -> buildList {
                add(AppAction.FREEZE)
                add(AppAction.FORCE_STOP)
                if (e.isSystem) add(AppAction.REMOVE)
                add(AppAction.CLEAR_DATA)
            }
            AppState.FROZEN -> buildList {
                add(AppAction.UNFREEZE)
                if (e.isSystem) add(AppAction.REMOVE)
            }
            AppState.REMOVED -> listOf(AppAction.RESTORE)
        }
    }

    fun needsConfirmation(a: AppAction): Boolean =
        a == AppAction.FREEZE || a == AppAction.REMOVE || a == AppAction.CLEAR_DATA

    fun command(a: AppAction, pkg: String): String {
        require(isValidPackageName(pkg)) { "invalid package name" }
        val q = ShellQuoting.quote(pkg)
        return when (a) {
            AppAction.FREEZE -> "pm disable-user --user 0 $q"
            AppAction.UNFREEZE -> "pm enable --user 0 $q"
            AppAction.FORCE_STOP -> "am force-stop --user 0 $q"
            AppAction.REMOVE -> "pm uninstall -k --user 0 $q"
            AppAction.RESTORE -> "pm install-existing --user 0 $q"
            AppAction.CLEAR_DATA -> "pm clear --user 0 $q"
        }
    }

    /**
     * @param after the `User 0:` flags read back after the command
     * @param output stdout + stderr of the command
     */
    fun verify(a: AppAction, after: Map<String, String>, output: String): Verdict {
        if (a == AppAction.CLEAR_DATA) {
            return if (output.contains("Success")) Verdict.UNVERIFIABLE else Verdict.NOT_APPLIED
        }
        if (after.isEmpty()) return Verdict.UNVERIFIABLE
        val enabled = after["enabled"]
        val ok = when (a) {
            AppAction.FREEZE -> enabled == "2" || enabled == "3"
            AppAction.UNFREEZE -> enabled == "0" || enabled == "1"
            AppAction.FORCE_STOP -> after["stopped"] == "true"
            AppAction.REMOVE -> after["installed"] == "false"
            AppAction.RESTORE -> after["installed"] == "true"
            AppAction.CLEAR_DATA -> false
        }
        return if (ok) Verdict.APPLIED else Verdict.NOT_APPLIED
    }

    /** Derives the list state from read-back flags; null when the flags are missing. */
    fun stateFrom(flags: Map<String, String>): AppState? {
        if (flags.isEmpty()) return null
        val enabled = flags["enabled"]
        return when {
            flags["installed"] == "false" -> AppState.REMOVED
            enabled == "2" || enabled == "3" -> AppState.FROZEN
            else -> AppState.ENABLED
        }
    }
}
