package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppActionsTest {

    private fun entry(state: AppState, system: Boolean, reason: String? = null) =
        AppEntry("com.example.app", "Example", system, state, reason)

    /** Real `User 0:` line from the phone, as parsed by parsePackageDetails. */
    private val realEnabled = parsePackageDetails(
        " User 0: ceDataInode=913457 deDataInode=913262 installed=true hidden=false suspended=false " +
            "distractionFlags=0 stopped=false notLaunched=false enabled=0 instant=false virtual=false quarantined=false",
    ).userFlags

    @Test
    fun protectedAppsGetNoActions() {
        assertTrue(AppActions.availableFor(entry(AppState.ENABLED, true, "current launcher")).isEmpty())
    }

    @Test
    fun actionsDependOnStateAndKind() {
        assertEquals(
            listOf(AppAction.FREEZE, AppAction.FORCE_STOP, AppAction.CLEAR_DATA),
            AppActions.availableFor(entry(AppState.ENABLED, false)),
        )
        assertEquals(
            listOf(AppAction.FREEZE, AppAction.FORCE_STOP, AppAction.REMOVE, AppAction.CLEAR_DATA),
            AppActions.availableFor(entry(AppState.ENABLED, true)),
        )
        assertEquals(listOf(AppAction.UNFREEZE), AppActions.availableFor(entry(AppState.FROZEN, false)))
        assertEquals(listOf(AppAction.UNFREEZE, AppAction.REMOVE), AppActions.availableFor(entry(AppState.FROZEN, true)))
        assertEquals(listOf(AppAction.RESTORE), AppActions.availableFor(entry(AppState.REMOVED, true)))
    }

    @Test
    fun commandsAreQuotedAndUserScoped() {
        assertEquals("pm disable-user --user 0 'com.example.app'", AppActions.command(AppAction.FREEZE, "com.example.app"))
        assertEquals("pm enable --user 0 'com.example.app'", AppActions.command(AppAction.UNFREEZE, "com.example.app"))
        assertEquals("am force-stop --user 0 'com.example.app'", AppActions.command(AppAction.FORCE_STOP, "com.example.app"))
        assertEquals("pm uninstall -k --user 0 'com.example.app'", AppActions.command(AppAction.REMOVE, "com.example.app"))
        assertEquals("pm install-existing --user 0 'com.example.app'", AppActions.command(AppAction.RESTORE, "com.example.app"))
        assertEquals("pm clear --user 0 'com.example.app'", AppActions.command(AppAction.CLEAR_DATA, "com.example.app"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun commandRejectsInjection() {
        AppActions.command(AppAction.FREEZE, "com.x; reboot")
    }

    @Test
    fun verifyUsesReadBack() {
        assertEquals(Verdict.APPLIED, AppActions.verify(AppAction.UNFREEZE, realEnabled, ""))
        assertEquals(Verdict.NOT_APPLIED, AppActions.verify(AppAction.FREEZE, realEnabled, "new state: disabled-user"))
        assertEquals(Verdict.APPLIED, AppActions.verify(AppAction.FREEZE, realEnabled + ("enabled" to "3"), ""))
        assertEquals(Verdict.NOT_APPLIED, AppActions.verify(AppAction.FORCE_STOP, realEnabled, ""))
        assertEquals(Verdict.APPLIED, AppActions.verify(AppAction.FORCE_STOP, realEnabled + ("stopped" to "true"), ""))
        assertEquals(Verdict.APPLIED, AppActions.verify(AppAction.REMOVE, realEnabled + ("installed" to "false"), ""))
        assertEquals(Verdict.APPLIED, AppActions.verify(AppAction.RESTORE, realEnabled, ""))
        assertEquals(Verdict.UNVERIFIABLE, AppActions.verify(AppAction.CLEAR_DATA, realEnabled, "Success"))
        assertEquals(Verdict.NOT_APPLIED, AppActions.verify(AppAction.CLEAR_DATA, realEnabled, "Failed"))
        assertEquals(Verdict.UNVERIFIABLE, AppActions.verify(AppAction.FREEZE, emptyMap(), ""))
    }

    @Test
    fun stateFromFlags() {
        assertEquals(AppState.ENABLED, AppActions.stateFrom(realEnabled))
        assertEquals(AppState.FROZEN, AppActions.stateFrom(realEnabled + ("enabled" to "3")))
        assertEquals(AppState.REMOVED, AppActions.stateFrom(realEnabled + ("installed" to "false")))
        assertNull(AppActions.stateFrom(emptyMap()))
    }
}
