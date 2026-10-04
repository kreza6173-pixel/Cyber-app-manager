package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutostartAuditTest {
    @Test fun findsBootReceiverAndBackgroundOps() {
        val text = """
            Receiver Resolver Table:
              com.example.app/.BootReceiver:
                android.intent.action.BOOT_COMPLETED
            RUN_IN_BACKGROUND: ignore
            RUN_ANY_IN_BACKGROUND: allow
        """.trimIndent()
        val audit = parseAutostartAudit("com.example.app", text)
        assertEquals(1, audit.receivers.size)
        assertEquals("BOOT_COMPLETED", audit.receivers.single().action)
        assertEquals(listOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND"), audit.backgroundOps.map { it.op })
        assertTrue(audit.raw.isNotEmpty())
    }

    @Test fun acceptsFullyQualifiedReceiverClass() {
        val audit = parseAutostartAudit("com.example.app", "com.example.app/com.example.app.BootReceiver: LOCKED_BOOT_COMPLETED")
        assertEquals(1, audit.receivers.size)
        assertEquals("LOCKED_BOOT_COMPLETED", audit.receivers.single().action)
    }

    @Test fun ignoresReceiverFromAnotherPackage() {
        val audit = parseAutostartAudit("com.example.app", "com.other/.Receiver: BOOT_COMPLETED")
        assertTrue(audit.receivers.isEmpty())
    }

    @Test fun ignoresBootTextFromActivityAndProviderSections() {
        val text = """
            Activity Resolver Table:
              com.example.app/.MainActivity filter 1
            Receiver Resolver Table:
              com.example.app/.BootReceiver filter 2
              Action: \"android.intent.action.BOOT_COMPLETED\"
            Service Resolver Table:
              com.example.app/androidx.startup.InitializationProvider filter 3
              Action: \"android.intent.action.BOOT_COMPLETED\"
            Registered ContentProviders:
              com.example.app/androidx.startup.InitializationProvider
        """.trimIndent()
        val audit = parseAutostartAudit("com.example.app", text)
        assertEquals(listOf("com.example.app/.BootReceiver"), audit.receivers.map { it.component })
    }
}
