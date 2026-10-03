# VOID // APPS: roadmap and remaining work

Updated: 2026-10-03. See `docs/HANDOFF.md` for the narrative and evidence.

## Product goal

A safe, reversible, local Android package manager built with Kotlin, Compose, and Shizuku UserService. It never pretends a shell command succeeded when the ROM rejected it. Shizuku is the only execution path; Root and Dhizuku are out of scope.

## Complete

- M0 core, Shizuku connection, UserService/AIDL, ExecBridge, console, CI.
- A1 inventory, protected guard, parsers, details, real-device counts.
- A2 single-package Suspend/Unsuspend, Disable/Enable, Force stop, Remove/Restore, Clear data, read-back.
- A3 snapshots, Restore/Undo, JSON Import/Export, Pins, batch operations. Acceptance test passed on the phone.

## Next: Debloat track

1. Port the legacy knowledge base and presets.
2. Safe/caution/core risk tags and OEM metadata.
3. Review screen: exact packages, current state, proposed operation.
4. Guard on every entry, including imported presets.
5. One snapshot per batch, sequential read-back, per-package result.
6. Report unsupported when Shizuku cannot perform the OEM operation.
7. Probe Xiaomi/HyperOS examples (calculator, compass, notes) before marking them supported.

## Then

- A4 runtime permission audit, grant/revoke, AppOps special access.
- A5 boot receivers, component control, background AppOps.
- A6 notification listener, DND access, per-app notification mute.
- A7 Chain3 per-app network block, netpolicy background data.
- A8 session install for APK/APKS/XAPK/APKM, OBB placement, extraction, cache trimming, safe shared-storage cleanup.
- 1.0: README rewrite, About, icon, fastlane, release notes, signed release, final smoke test, merge to `main`.

## Gates

No INTERNET permission. No protected bypass. No destructive batch without preview and snapshot. No support claim without a device probe. No release claim while CI or device acceptance is red.
