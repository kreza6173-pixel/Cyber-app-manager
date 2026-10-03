# VOID // APPS: roadmap and remaining work

Updated: 2026-10-03. The project is a native Kotlin + Compose + Shizuku UserService app. The original idea and all completed work are recorded in `docs/HANDOFF.md`.

## Product goal

Provide a safe, reversible, local package manager for Android that can inspect and manage user and system packages without root when Shizuku is sufficient, while accurately reporting when Dhizuku or root is required. The app must never pretend an operation succeeded.

## Completed baseline

- M0 core, Shizuku state machine, UserService/AIDL, ExecBridge, console, CI, and debug build.
- A1 inventory, protected guard, parser coverage, package details, and real-device counts.
- A2 Suspend/Unsuspend, Disable/Enable, Force stop, Remove/Restore, Clear data verdicts, and read-back.
- A3 Snapshot model, durable JSON storage, Snapshot UI, delete/detail/refresh, scroll/select, Restore/Undo, per-package read-back, safety snapshots, and JSON import/export.

## Milestones

### A3: finish the recovery layer

Status: mostly complete. Remaining:

- Pins UI: pin/unpin in app details and list, display pinned state, safe re-apply only for valid non-protected packages.
- Batch operation UI: select packages, preview exact targets, snapshot before execution, process sequentially, show per-package results.
- Add pure tests for storage round-trip, malformed JSON, duplicate merge, ordering/cap, import/export, and restore result mapping.

Acceptance: select three non-protected apps, execute a batch state change, see one pre-batch snapshot and three individual results, then restore them through Undo.

### A4: permissions and special access

- Runtime permission inventory per app.
- Grant/revoke through the supported Package Manager path.
- AppOps list/read/write for special access, with capability checks.
- Read back every change and distinguish unsupported from failed.

### A5: background and autostart

- Boot receiver/component inventory and guarded enable/disable.
- RUN_IN_BACKGROUND and RUN_ANY_IN_BACKGROUND AppOps.
- Probe Xiaomi/HyperOS behaviour before claiming support.

### A6: notifications

- Notification listener access inventory.
- DND access inventory.
- Per-app notification mute where the ROM supports it.
- Exact read-back and settings parity.

### A7: network controls

- Chain3 per-app network blocking.
- Background-data controls through netpolicy.
- No fake DNS filtering, iptables shortcut, or untested root path.
- Prove block and unblock on the reference device.

### A8: install and cleanup

- Session install for APK/APKS/XAPK/APKM.
- OBB placement and extraction.
- Cache trimming and carefully scoped shared-storage scans.
- Report shell limitations rather than claiming root-only cleanup.

### Debloat track

Debloat is a product track built on top of A3, not a blind delete button:

1. Port the legacy knowledge base and presets.
2. Add safe/caution/core risk classification and OEM/package metadata.
3. Add a review screen with exact package list, current state, capability, and proposed operation.
4. Refuse protected packages everywhere, including imported presets.
5. Snapshot before a batch and provide per-package read-back.
6. Report `unsupported` when the ROM requires Dhizuku or root for removal.
7. Test calculator, compass, notes, and other OEM examples on Xiaomi/HyperOS before marking them supported.

### 1.0 release

- Rewrite README for native VOID reality and remove legacy claims.
- About screen, final icon/branding, fastlane metadata, and release notes.
- Ensure `.github/workflows/ci.yml` is the active workflow.
- Build and verify signed release APK using CI secrets only.
- Install and smoke-test on the Redmi/Xiaomi reference phone with Shizuku uid 2000.
- Update HANDOFF and PLAN with verified results.
- Merge `native-app-v0` into `main` only after CI and device acceptance are green.

## Engineering gates

- No INTERNET permission.
- No destructive batch without preview and snapshot.
- No claim of support without a real-device probe.
- No protected-package bypass.
- No release claim while CI or device acceptance is red.
