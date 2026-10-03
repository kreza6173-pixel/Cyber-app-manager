package io.github.kreza6173pixel.cyberappmanager.inventory

/**
 * Shell command for the permission audit. The full `dumpsys package` of large apps exceeds the
 * 64 KiB ExecBridge cap (Drive: 128886 bytes), mostly because of the resolver tables.
 *
 * `sed -n '/^Packages:/,/^[A-Z]/p'` keeps only the active `Packages:` block and stops at the next
 * top-level header (`Hidden system packages:` or `Queries:`). The hidden factory package of an
 * updated system app is excluded, so it cannot overwrite runtime flags of the installed version.
 * Lines inside the block reach [parsePermissionAudit] unchanged.
 *
 * Measured on Drive (Redmi Note 14): Packages: at line 1585, next header at 1774; the whole tail
 * from Packages: to EOF was 35719 bytes.
 */
fun permissionDumpCommand(quotedPkg: String): String = "dumpsys package $quotedPkg | sed -n '/^Packages:/,/^[A-Z]/p'"

/**
 * Packages in a shared uid keep their runtime permissions in the `Shared users:` block, not in
 * `Packages:` (com.miui.securitycenter, android.uid.system/1000: `runtime permissions:` at line 4021,
 * inside Shared users: 3501..4049). Read only when [sharedUserOf] finds a shared user.
 */
fun sharedUsersDumpCommand(quotedPkg: String): String = "dumpsys package $quotedPkg | sed -n '/^Shared users:/,/^[A-Z]/p'"

/** A shared uid. Runtime permissions belong to the whole uid, so a change applies to every package in it. */
data class SharedUserInfo(val name: String, val uid: Int) {
    /** System range (for example android.uid.system/1000). Permission writes are refused for these. */
    val systemUid: Boolean get() = uid < 10000
}

private val SHARED_USER = Regex("sharedUser=SharedUserSetting\\{\\S+ ([^/\\s]+)/(\\d+)\\}")

/** First shared user in the Packages block, for example `sharedUser=SharedUserSetting{cfee48 android.uid.system/1000}`. */
fun sharedUserOf(packagesBlock: String): SharedUserInfo? =
    SHARED_USER.find(packagesBlock)?.let { m -> m.groupValues[2].toIntOrNull()?.let { SharedUserInfo(m.groupValues[1], it) } }
