package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun ignoresHeadersAndEmptyMarkers() {
        val audit = parseAppOps("pkg", """
            No operations.
            Package com.example.app:
              Uid mode: LEGACY_STORAGE: allow
              CAMERA: ignore; rejectTime=+3h ago
        """.trimIndent())
        assertEquals(listOf("camera", "legacy_storage"), audit.operations.map { it.op })
        assertEquals("ignore", audit.operations.first { it.op == "camera" }.mode)
    }

    /** Lines taken from Drive on the Redmi Note 14 (Android 16, HyperOS). */
    @Test fun keepsOemOpsReadOnlyAndReportsDuplicateModes() {
        val audit = parseAppOps("com.google.android.apps.docs", """
            Uid mode: ACCESS_RESTRICTED_SETTINGS: allow
            ACCESS_RESTRICTED_SETTINGS: default; time=+19h26m44s501ms ago
            MIUIOP(10008): allow; time=+4h5m22s172ms ago
            MIUIOP(10053): ignore
        """.trimIndent())
        assertEquals(3, audit.operations.size)
        val oem = audit.operations.first { it.op == "miuiop(10008)" }
        assertTrue(oem.oem)
        assertFalse(oem.changeable)
        assertEquals(2, audit.operations.count { it.oem })
        val restricted = audit.operations.first { it.op == "access_restricted_settings" }
        assertEquals("default", restricted.mode)
        assertEquals(listOf("allow"), restricted.alsoReported)
        assertFalse(isValidAppOp("miuiop(10008)"))
    }

    /** Shape measured on Drive: `appops get <uid>` is exactly the first block of `appops get <package>`. */
    @Test fun splitsUidAndPackageScopes() {
        val uid = """
            Uid mode: CAMERA: foreground
            ACCESS_RESTRICTED_SETTINGS: allow
        """.trimIndent()
        val full = uid + "\n" + """
            WAKE_LOCK: allow; time=+4h4m40s197ms ago; duration=+51ms
            ACCESS_RESTRICTED_SETTINGS: default; time=+19h26m44s501ms ago
            MIUIOP(10053): ignore
        """.trimIndent()
        val audit = parseAppOpsScoped("com.google.android.apps.docs", full, uid)
        assertEquals(4, audit.operations.size)
        assertTrue(audit.operations.all { it.scoped })
        val restricted = audit.operations.first { it.op == "access_restricted_settings" }
        assertEquals("allow", restricted.uidMode)
        assertEquals("default", restricted.packageMode)
        val camera = audit.operations.first { it.op == "camera" }
        assertEquals("foreground", camera.uidMode)
        assertNull(camera.packageMode)
        assertEquals("allow", audit.operations.first { it.op == "wake_lock" }.packageMode)
        assertTrue(audit.operations.first { it.op == "miuiop(10053)" }.oem)
    }

    @Test fun fallsBackToMergedWhenUidPrefixDoesNotMatch() {
        val audit = parseAppOpsScoped("pkg", "CAMERA: allow\nWAKE_LOCK: allow", "Uid mode: CAMERA: ignore")
        assertFalse(audit.operations.any { it.scoped })
        assertEquals(2, audit.operations.size)
    }

    @Test fun findsExactPackageUid() {
        val out = "package:com.google.android.apps.docs.editors.docs uid:10200\npackage:com.google.android.apps.docs uid:10176"
        assertEquals(10176, uidOf("com.google.android.apps.docs", out))
        assertNull(uidOf("com.missing", out))
    }

    @Test fun commandsUseExplicitMode() {
        assertEquals("appops set pkg camera allow", appOpsSetCommand("pkg", "camera", "allow"))
        assertEquals("appops set pkg camera default", appOpsResetCommand("pkg", "camera"))
    }
}
