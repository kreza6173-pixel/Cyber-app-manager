package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfCheckTest {
    @Test fun findsLinesThatAreNotOperations() {
        val text = "Uid mode: CAMERA: ignore\nFOO_BAR: weird\nNo operations.\n\nMIUIOP(10008): allow; time=+1m\nsome vendor text"
        assertEquals(listOf("FOO_BAR: weird", "some vendor text"), unparsedAppOpLines(text))
    }

    @Test fun emptyUidBlockMeansEverythingIsPackageScope() {
        val audit = parseAppOpsScoped("pkg", "WAKE_LOCK: allow\nRUN_ANY_IN_BACKGROUND: ignore", "No operations.")
        assertTrue(audit.scoped)
        assertEquals("allow", appOpModeIn(audit, "wake_lock", AppOpScope.PACKAGE))
        val inconsistent = parseAppOpsScoped("pkg", "Uid mode: CAMERA: ignore\nWAKE_LOCK: allow", "")
        assertFalse(inconsistent.scoped)
    }

    @Test fun textReportListsCountsAndProblems() {
        val report = SelfCheckReport(SelfCheckScope.SYSTEM, 3, 2, 1, 1, 0, 0, 0, 1, listOf(
            SelfCheckItem("com.example.big", true, listOf("permissions: output truncated (64 KiB cap)")),
            SelfCheckItem("com.example.oem", true, listOf("appops: 1 unrecognised line(s): FOO: bar")),
        ))
        val text = selfCheckText(report, "Xiaomi Redmi Note 14")
        assertFalse(report.complete)
        assertTrue(text.contains("group: system, checked 2 of 3 (stopped early)"))
        assertTrue(text.contains("64 KiB cap hits: 1"))
        assertTrue(text.contains("com.example.big (system)"))
        assertTrue(text.contains("  - appops: 1 unrecognised line(s): FOO: bar"))
    }

    @Test fun cleanReportSaysSo() {
        val text = selfCheckText(SelfCheckReport(SelfCheckScope.USER, 2, 2, 0, 0, 0, 0, 0, 0, emptyList()), "device")
        assertTrue(text.endsWith("no problems found"))
    }
}
