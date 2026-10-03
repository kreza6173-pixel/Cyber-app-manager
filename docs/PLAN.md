# VOID // APPS: roadmap and remaining work

Updated: 2026-10-03. See `docs/HANDOFF.md` for the complete narrative and evidence.

## Product goal

A safe, reversible, local Android package manager built with Kotlin, Compose, and Shizuku UserService. It manages packages without pretending a shell command succeeded when the ROM rejected it. Root and Dhizuku are outside this project; Shizuku is the only execution path.

## Complete

- M0 core, Shizuku connection, UserService/AIDL, ExecBridge, console, CI, debug build.
- A1 inventory, protected guard, parsers, package details, real-device counts.
- A2 Suspend/Unsuspend, Disable/Enable, Force stop, Remove/Restore, Clear data verdicts, read-back.
- A3 Snapshot model, durable storage, UI, delete/detail/refresh, scroll/select, Restore/Undo, safety snapshots, per-package read-back, JSON Import/Export.

## Remaining milestones

### A3 finish

Pins UI, safe re-apply, batch operation UI, one pre-batch snapshot, sequential per-package results, pure storage/import/restore tests, and final batch acceptance test.

### Debloat track

Port the legacy knowledge base and presets; add safe/caution/core metadata and OEM information; review exact package targets before applying; refuse protected packages including imported presets; snapshot before batch; run sequentially with read-back; report unsupported where Shizuku cannot perform the OEM operation. Calculator, compass, notes, and similar Xiaomi/HyperOS packages must be probed before being marked supported.

### A4 to A8

- A4: runtime permission audit, grant/revoke, AppOps special access.
- A5: boot receivers, component control, background AppOps.
- A6: notification listener, DND access, per-app notification mute.
- A7: Chain3 per-app network block and netpolicy background-data controls.
- A8: APK/APKS/XAPK/APKM session install, OBB placement, extraction, cache trimming, safe shared-storage cleanup.

### 1.0 release

Rewrite README for native VOID reality; About/icon/branding/fastlane/release notes; verify active CI; signed release from CI secrets; final Redmi/Xiaomi smoke test; update docs; merge `native-app-v0` into `main` only after all gates pass.

## Gates

No INTERNET permission. No protected bypass. No destructive batch without preview and snapshot. No support claim without device probe. No release claim while CI or device acceptance is red.
