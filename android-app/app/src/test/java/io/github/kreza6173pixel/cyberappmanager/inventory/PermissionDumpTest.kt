package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDumpTest {
    @Test fun gapsEndSectionsLikeTheFullDump() {
        val numbered = """
            5:    requested permissions:
            6:      android.permission.CAMERA
            40:      com.example.permission.UNRELATED: prot=signature
            50:    User 0: ceDataInode=1 installed=true
            60:      runtime permissions:
            61:        android.permission.CAMERA: granted=false, flags=[ USER_SET ]
        """.trimIndent()
        val audit = parsePermissionAudit("pkg", joinNumberedLines(numbered))
        assertEquals(listOf("android.permission.CAMERA"), audit.permissions.map { it.name })
        val camera = audit.permissions.single()
        assertTrue(camera.runtime)
        assertTrue(camera.requested)
        assertEquals(false, camera.granted)
    }

    @Test fun skipsLinesWithoutLineNumbers() {
        assertEquals("", joinNumberedLines("no number here\n:empty"))
    }
}
