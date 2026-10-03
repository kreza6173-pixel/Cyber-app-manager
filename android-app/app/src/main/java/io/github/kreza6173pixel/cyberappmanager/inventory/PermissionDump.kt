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
