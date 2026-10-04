# VOID // APPS: roadmap and remaining work

Updated: 2026-10-04. See `docs/HANDOFF.md` for the narrative and evidence.

## Product goal

A safe, reversible, local Android package manager built with Kotlin, Compose, and Shizuku UserService. It never pretends a shell command succeeded when the ROM rejected it. Shizuku is the only execution path; Root and Dhizuku are out of scope.

## Complete

- M0 core, Shizuku connection, UserService/AIDL, ExecBridge, console, CI.
- A1 inventory, protected guard, parsers, details, real-device counts.
- A2 single-package Suspend/Unsuspend, Disable/Enable, Force stop, Remove/Restore, Clear data, read-back.
- A3 snapshots, Restore/Undo, JSON Import/Export, Pins, batch operations. Acceptance test passed on the phone.
- Debloat track: knowledge base, SAFE-only presets, review screen, compact disclaimer, search in preset list and package list, cross-manager restore via `pm install-existing`, one snapshot per batch, sequential read-back. CI and phone acceptance passed.

## Complete on the reference phone: A4 permissions and AppOps

1. Permission audit, runtime read-back, Grant/Revoke. **Done, phone-verified (Drive, Meet, Play Store, Acode).**
2. Shared-uid permissions with system-uid write refusal. **Done, phone-verified (securitycenter).**
3. AppOps audit, OEM ops, uid/package scope split. **Done, phone-verified (Drive, securitycenter, Acode).**
4. AppOps change. **Done: package scope only, hidden where a uid mode overrides it, uid scope disabled, system uids refused. Phone-verified (`873a114`).**
5. Read-only Self-check. **Done on the reference phone: system 311/311 clean; user 463/463 with HyperOS `MIUIOP(10017): ask` recognised as read-only.**
6. Large permission output fix. **Done: measured `android` at 108563 bytes and GMS at 83925 bytes; section extractor passed system Self-check 311/311.**

## A5 current

A5 started from the proven `kreza6173-pixel/void-autostart` module. The read-only parser foundation is pushed in `0615a610`. Its first CI runs #72 to #74 failed on one test because `package/.Receiver` shorthand was not accepted. Commit `fd067e8` fixes that parser and adds a regression test for fully qualified receiver names. Awaiting the next CI result.

The source commands to port, only after phone read-back proves them:

- Boot receiver scan: `dumpsys package <pkg>`.
- Component control: `pm disable <pkg>/<component>` and `pm enable <pkg>/<component>`.
- Background execution audit: `cmd appops get <pkg> RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND`.
- Background execution control: `cmd appops set`, with a warning that broad denial can delay notifications.

## Then

| Step | Source module | Commands to port |
|---|---|---|
| A6 notification listener, DND access, per-app notification mute | `kreza6173-pixel/void-pulse` | `pm revoke/grant POST_NOTIFICATIONS`, `cmd appops set POST_NOTIFICATIONS`, `cmd notification allow_listener/disallow_listener`, `allow_dnd/disallow_dnd` |
| A7 per-app network block, background data | `kreza6173-pixel/VOID-WALL` (`webui/wall.js`) | Chain 3 via `cmd connectivity` (Android 11+), background data via `netpolicy` |
| A8 installer for APK, APKS, XAPK, OBB, extract | `kreza6173-pixel/pulse-install` (`webui/script.js`, `service.sh`) | streamed `pm install-create` / `install-write -S <size> -` / `install-commit`, `unzip`, XAPK `manifest.json`, OBB copy, `pm path` extract |
| 1.0 release | this repository | README, About, icon, fastlane, signed release, final smoke test, merge to `main` |

## Gates

No INTERNET permission. No protected bypass. No destructive batch without preview and snapshot. No support claim without a device probe. No release claim while CI or device acceptance is red. Code and its strings/resources always land in one commit. Shell filters are measured on the phone before they are relied on. A write control is shown only where a phone test showed Android accepts it. ROM-specific support comes from issue reports with phone evidence. APKM and root-only firewall features remain out of scope.
