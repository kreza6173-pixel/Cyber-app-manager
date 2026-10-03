package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAuditTest {
    @Test fun runtimeSectionIsNotLostWhenFullDumpsysContainsQueries() {
        val text = """
            Packages:
              Package [com.example.app]:
                requested permissions:
                  android.permission.POST_NOTIFICATIONS
                install permissions:
                  android.permission.INTERNET: granted=true
                User 0:
                  runtime permissions:
                    android.permission.POST_NOTIFICATIONS: granted=true, flags=[ USER_SET]
            Queries:
              queryable packages:
        """.trimIndent()
        val audit = parsePermissionAudit("com.example.app", text)
        val p = audit.permissions.single { it.name.endsWith("POST_NOTIFICATIONS") }
        assertTrue(p.runtime)
        assertEquals(true, p.granted)
        assertTrue(p.changeable)
    }

    @Test fun runtimeStatesRemainSeparatePerUser() {
        val text = """
            Packages:
              Package [pkg]:
                requested permissions:
                  android.permission.CAMERA
                User 0:
                  runtime permissions:
                    android.permission.CAMERA: granted=false, flags=[]
                User 10:
                  runtime permissions:
                    android.permission.CAMERA: granted=true, flags=[]
        """.trimIndent()
        val p = parsePermissionAudit("pkg", text).permissions.single()
        assertFalse(p.granted == true)
    }
}
