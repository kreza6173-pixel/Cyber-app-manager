package io.github.kreza6173pixel.cyberappmanager.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDumpTest {
    @Test fun commandKeepsOnlyThePackagesBlock() {
        val cmd = permissionDumpCommand("'com.example.app'")
        assertEquals("dumpsys package 'com.example.app' | sed -n '/^Packages:/,/^[A-Z]/p'", cmd)
        assertFalse(cmd.contains("grep"))
    }

    /** Shape of the block sed returns: active package, then the next top-level header line. */
    @Test fun parsesThePackagesBlockAsReturnedBySed() {
        val block = """
            Packages:
              Package [com.example.app] (abc123):
                requested permissions:
                  android.permission.CAMERA
                  android.permission.INTERNET
                install permissions:
                  android.permission.INTERNET: granted=true
                User 0: ceDataInode=1 installed=true hidden=false
                  gids=[3003]
                  runtime permissions:
                    android.permission.CAMERA: granted=false, flags=[ USER_SET ]
            Hidden system packages:
        """.trimIndent()
        val audit = parsePermissionAudit("com.example.app", block)
        assertEquals(listOf("android.permission.CAMERA", "android.permission.INTERNET"), audit.permissions.map { it.name })
        val camera = audit.permissions.first { it.name == "android.permission.CAMERA" }
        assertTrue(camera.runtime)
        assertEquals(false, camera.granted)
        assertEquals(true, audit.permissions.first { it.name == "android.permission.INTERNET" }.granted)
    }
}
