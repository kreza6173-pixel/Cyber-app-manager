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

    @Test fun ignoresReceiverFromAnotherPackage() {
        val audit = parseAutostartAudit("com.example.app", "com.other/.Receiver: BOOT_COMPLETED")
        assertTrue(audit.receivers.isEmpty())
    }
}
