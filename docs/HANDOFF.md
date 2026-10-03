# VOID // APPS: complete project handoff

Updated: 2026-10-03
Branch: `native-app-v0`
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi/Redmi Android 16, SDK 36, Shizuku shell uid 2000.

This document is the durable record of the idea, decisions, completed work, failures, phone validation, and the remaining path. The repository and the latest green CI run are authoritative if this file ever becomes stale.

## 1. Product idea

VOID // APPS is a native Kotlin + Jetpack Compose Android package manager using Shizuku UserService/AIDL. It replaces the old Shevery/WebUI approach with a local, English-only app that can inspect and safely manage installed packages without INTERNET permission.

The core product is not a blind debloater. It is a guarded package-state manager: inventory the device, distinguish user/system/disabled/suspended/removed states, refuse dangerous protected packages, apply one reversible operation at a time, read the real state back, and preserve a recovery path through snapshots, restore, and JSON export/import.

The old WebUI and shell modules remain historical references only. The native app is the product being developed.

## 2. Original plan and engineering rules

The project follows M0, A1 through A8, then 1.0 release. The non-negotiable rules are:

1. No INTERNET permission, analytics, AI calls, or network lookups.
2. Commands go through Shizuku UserService and ExecBridge on IO dispatchers.
3. Package names are validated and shell-quoted.
4. Every state-changing command is read back. `APPLIED` means the read-back matches.
5. Protected packages are refused in the repository, not merely hidden in Compose UI.
6. Protected coverage includes launcher, keyboard/input, dialer, SMS, WebView provider, Shizuku, VOID itself, core packages, and overlays.
7. Bulk work is one package at a time with a per-package result.
8. Snapshots contain package state only, never APKs or private app data.
9. Root-only behaviour is never faked. If Shizuku cannot perform an OEM operation, the result must say so.
10. CI proves compilation and unit tests; the reference phone proves behaviour.

## 3. What was completed

### M0 and core

- Kotlin, Compose, Shizuku UserService/AIDL, ExecBridge, and console are active.
- Toolchain is pinned: AGP 8.13.1, Kotlin 2.2.21, Gradle 8.13, Compose BOM 2025.12.00, SDK 36, Shizuku 13.1.5.
- No INTERNET permission.
- CI is in `.github/workflows/ci.yml` and runs unit tests, lint, and debug assembly.
- Shizuku connection state and uid display are implemented.

### A1 inventory, guard, and details

- Full package inventory supports installed, removed-for-user, disabled/frozen, suspended, user, and system states.
- Reference phone inventory was validated at 835 total packages, including 468 user and 367 system packages in the earlier probe.
- Persian and Arabic digits from real dumpsys output are normalized.
- Details include version, SDK, installer, install/update dates, User 0 flags, and raw output.
- Protected package detection is enforced in the repository.

### A2 operations

- Suspend and unsuspend.
- Disable/enable, shown in the UI as freeze/unfreeze behaviour.
- Force stop.
- Remove for user and restore.
- Clear data, reported as `UNVERIFIABLE` after successful command because Android has no reliable read-back for deletion.
- System-app Suspend is intentionally available, matching the product requirement and phone testing.
- The UI now refreshes action availability from actual `dumpsys` state after reinstall, instead of trusting stale cache. This fixed the case where an app previously suspended by VOID showed the wrong action after reinstall.

### A3 snapshots and restore

- Pure Snapshot model and restore planner.
- Protected packages excluded from restore plans.
- Removed packages restored before state transitions.
- Durable JSON-backed SnapshotStore with a 50-snapshot cap.
- Durable PinStore storage foundation.
- Snapshot details are scrollable and text-selectable.
- Snapshot list has refresh, detail, delete, and restore flow.
- Restore creates a safety snapshot first, applies each package step separately, reads back each result, and shows an applied/not-applied report.
- JSON export and import use the Android file picker and merge by snapshot id.
- Public storage target is `Download/VOID APPS/`; the file is `snapshots.json` after consolidation. Import/export is the supported recovery path across uninstall/reinstall or ROM changes.
- Rapid identical snapshot creation is deduplicated within a short window. A separate real state transition remains snapshot-worthy.

Phone validation completed:

- Protected packages show no actions.
- Suspend and read-back work for user and system packages.
- Snapshot entries show 835 packages and the expected enabled/frozen states.
- Restore of one suspended app reported `1 applied · 0 not applied` and successfully unsuspended the package.
- Snapshot JSON import/export worked and restored two snapshots in the UI.
- Snapshot detail scrolling and copy/select behaviour worked.
- The user confirmed the post-reinstall action state now reflects the real Package Manager state.

## 4. Important failures and fixes

- A2 stale test expected Suspend on the wrong product contract. The test was corrected, then the product requirement was clarified to allow system-app Suspend and the test was corrected again.
- A3 UI had stale FREEZE references and non-exhaustive state branches during the transition. These were corrected before the green baseline.
- Snapshot details first used the wrong `SelectionContainer` import. CI exposed the compile error; the correct Compose foundation text-selection import was pushed.
- InventoryRepository briefly used an invalid `emptyList().let` construction, causing Kotlin type inference errors. It was replaced with explicit entries/result construction.
- MediaStore lookup initially failed to consolidate files, producing duplicate JSON files and an empty in-app list. Storage now reads matching legacy files, merges by id, writes one canonical file, and removes extras.
- App detail actions initially used a stale cached state after `dumpsys` read-back. Details now update repository state before the UI rebuilds its action list.
- Android uninstall removes app-private files. Snapshot storage was moved to public Downloads and JSON import/export was added because a fresh install may not automatically regain access to a previous MediaStore owner/file.
- The reference ROM may reject removal of some system apps under shell/Shizuku. That is an expected capability boundary; Dhizuku or root must be detected explicitly rather than simulated.

## 5. Not yet complete

- Pins UI and safe re-apply behaviour.
- Debloat knowledge base and reviewed presets.
- Batch operations with automatic pre-batch snapshot and per-package results.
- README rewrite from the legacy WebUI claims to native VOID reality.
- Final About screen, icon/branding, fastlane metadata, and release notes.
- A4 runtime permission audit, grant/revoke, and AppOps special access.
- A5 boot receivers, component control, and background AppOps.
- A6 notification listener, DND access, and per-app notification mute.
- A7 Chain3 per-app network blocking and netpolicy background-data controls.
- A8 APK/APKS/XAPK/APKM session install, OBB placement, extraction, cache trimming, and safe shared-storage cleanup.
- Final signed release build, device smoke test, HANDOFF/PLAN verification, and merge to `main`.

## 6. Next execution order

1. Finish A3 Pins and complete the A3 acceptance test.
2. Add debloat knowledge base and presets with review/preview, protected guard, capability-aware results, snapshot before batch, and rollback.
3. Implement A4, then A5, A6, A7, and A8 in order, with a phone probe before each feature.
4. Rewrite README and release documentation so no legacy WebUI or unimplemented claims remain.
5. Run the final Redmi/Xiaomi smoke test, build signed APK from CI secrets, and merge only after CI and device acceptance are green.

## 7. Commit trail for the current native branch

- `3ad1a13`: align stale action test with the then-current policy.
- `e8e7822`: connect SnapshotStore and PinStore foundation; auto-snapshot risky single actions.
- `578008e` and `37f3f5c`: restore system-app Suspend and align tests with the requested product rule.
- `7300550`, `dce8be2`, `9f870bb`: add Snapshot UI, repository delete API, and navigation.
- `d2f17e0`, `38935ff`, `33d27e2`: fix Snapshot details line breaks, scrolling/selectability, and the CI import error.
- `38a08fc`, `abbb8a1`: move storage outside app-private files and consolidate duplicate MediaStore files.
- `daf3f40`: fix inventory result construction after the type inference failure.
- `161a8a8`, `caafe42`: refresh real app state and Snapshot list.
- `d891669`: deduplicate rapid identical snapshots.
- `5c30e81`: add restore/Undo with per-package read-back.
- `8e07cd3`: add JSON import/export through the Android file picker.

## 8. Testing rules for future work

Never infer phone behaviour from a green CI run. For each risky feature: run the smallest device test, capture the actual command/read-back, check protected packages, and only then update this handoff. Keep user-facing strings English-only and do not add INTERNET permission.
