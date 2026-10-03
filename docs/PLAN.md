# VOID // APPS: roadmap and remaining work

Updated: 2026-10-03. See `docs/HANDOFF.md` for the narrative and evidence.

## Product goal

A safe, reversible, local Android package manager built with Kotlin, Compose, and Shizuku UserService. It never pretends a shell command succeeded when the ROM rejected it. Shizuku is the only execution path; Root and Dhizuku are out of scope.

## Complete

- M0 core, Shizuku connection, UserService/AIDL, ExecBridge, console, CI.
- A1 inventory, protected guard, parsers, details, real-device counts.
- A2 single-package Suspend/Unsuspend, Disable/Enable, Force stop, Remove/Restore, Clear data, read-back.
- A3 snapshots, Restore/Undo, JSON Import/Export, Pins, batch operations. Acceptance test passed on the phone.
- Debloat track: knowledge base, SAFE-only presets, review screen, compact disclaimer, search in preset list and package list, cross-manager restore via `pm install-existing`, one snapshot per batch, sequential read-back. CI and phone acceptance passed.

## In progress: A4 permissions and AppOps

1. Parse the permission sections from `dumpsys package` without inventing state on unknown ROM output. **Done in this push, unit-tested.**
2. Add a read-only permission audit to app details, with requested/granted/unknown states.
3. Add guarded runtime grant/revoke with read-back where Shizuku and Android permit it.
4. Add AppOps special-access audit and honest unsupported results per ROM/API.

## Then

- A5 boot receivers, component control, background AppOps.
- A6 notification listener, DND access, per-app notification mute.
- A7 Chain3 per-app network block, netpolicy background data.
- A8 session install for APK, APKS and XAPK, OBB placement, extraction, cache trimming, safe shared-storage cleanup.
- 1.0: README rewrite, About, icon, fastlane, release notes, signed release, final smoke test, merge to `main`.

## Gates

No INTERNET permission. No protected bypass. No destructive batch without preview and snapshot. No support claim without a device probe. No release claim while CI or device acceptance is red.
