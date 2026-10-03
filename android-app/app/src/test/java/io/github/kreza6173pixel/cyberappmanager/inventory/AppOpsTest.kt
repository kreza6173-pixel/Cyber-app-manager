package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsTest {
    @Test fun parsesCommonAppOpsOutput() {
        val audit = parseAppOps("com.example.app", """
            Uid mode: COARSE_LOCATION: foreground; time=+1m
              POST_NOTIFICATION: allow; time=+2m
              READ_CLIPBOARD: deny
              unrelated text: not-an-op
        """.trimIndent())
        assertEquals(3, audit.operations.size)
        assertEquals("allow", audit.operations.first { it.op == "post_notification" }.mode)
        assertEquals("foreground", audit.operations.first { it.op == "coarse_location" }.mode)
    }

    @Test fun ignoresUnsupportedModesAndInvalidNames() {
        val audit = parseAppOps("pkg", """
            camera: allow
            audio_record: ignored
            bad-name: deny
        """.trimIndent())
        assertEquals(1, audit.operations.size)
        assertFalse(isValidAppOp("bad-name"))
        assertTrue(isValidAppOpMode("ignore"))
    }

    @Test fun commandsUseExplicitMode() {
        assertEquals("appops set pkg camera allow", appOpsSetCommand("pkg", "camera", "allow"))
        assertEquals("appops set pkg camera default", appOpsResetCommand("pkg", "camera"))
    }
}
