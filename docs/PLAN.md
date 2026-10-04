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

## In progress: A4 permissions and AppOps

1. Permission audit, runtime read-back, Grant/Revoke. **Done, phone-verified (Drive, Meet, Play Store, Acode).**
2. Shared-uid permissions with system-uid write refusal. **Done, phone-verified (securitycenter).**
3. AppOps audit, OEM ops, uid/package scope split. **Done, phone-verified (Drive, securitycenter, Acode).**
4. AppOps change. **Done: package scope only, hidden where a uid mode overrides it, uid scope disabled, system uids refused. Phone-verified (`873a114`).**
5. Read-only Self-check over all packages with ROM note and issue template. **Pushed (`ba860ea`); awaiting CI and a phone run.**

## Then

- A5 boot receivers, component control, background AppOps.
- A6 notification listener, DND access, per-app notification mute.
- A7 Chain3 per-app network block and netpolicy background data.
- A8 session install for APK, APKS and XAPK, OBB placement, extraction, cache trimming, safe shared-storage cleanup. APKM is out of scope.
- 1.0: README rewrite, About, icon, fastlane, release notes, signed release, final smoke test, merge to `main`.

## Gates

No INTERNET permission. No protected bypass. No destructive batch without preview and snapshot. No support claim without a device probe. No release claim while CI or device acceptance is red. Code and its strings/resources always land in one commit. Shell filters are measured on the phone before they are relied on. A write control is shown only where a phone test showed Android accepts it. ROM-specific support comes from issue reports with phone evidence.
