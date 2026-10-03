package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAuditTest {
    @Test fun parsesRequestedAndGrantedState() {
        val audit = parsePermissionAudit("com.example.app", """
            requested permissions:
              android.permission.CAMERA
              android.permission.ACCESS_FINE_LOCATION
            install permissions:
              android.permission.CAMERA: granted=true
              android.permission.ACCESS_FINE_LOCATION: granted=false
            User 0:
              gids=[3003]
        """.trimIndent())
        assertEquals(2, audit.permissions.size)
        assertTrue(audit.permissions.first { it.name.endsWith("CAMERA") }.granted == true)
        assertFalse(audit.permissions.first { it.name.endsWith("LOCATION") }.granted == true)
    }

    @Test fun ignoresUnknownSectionsAndKeepsRequestedPermission() {
        val audit = parsePermissionAudit("pkg", """
            requested permissions:
              android.permission.POST_NOTIFICATIONS
            runtime permissions:
              android.permission.POST_NOTIFICATIONS: granted=false
        """.trimIndent())
        assertEquals(1, audit.permissions.size)
        assertEquals(null, audit.permissions.single().granted)
    }

    @Test fun emptyOutputDoesNotInventState() {
        val audit = parsePermissionAudit("pkg", "permission denied")
        assertTrue(audit.permissions.isEmpty())
    }
}
