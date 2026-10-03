# VOID // APPS: complete project handoff

Updated: 2026-10-03
Branch: `native-app-v0`
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi/Redmi Android 16, SDK 36, Shizuku shell uid 2000.

This is the durable record of the product idea, engineering rules, completed work, failures, fixes, phone validation, and remaining path. The repository and latest green CI run are authoritative if this file becomes stale.

## 1. Product idea

VOID // APPS is a native Kotlin + Jetpack Compose Android package manager using Shizuku UserService/AIDL. It replaces the old Shevery/WebUI approach with a local, English-only app that can inspect and safely manage installed packages without INTERNET permission.

The product is a guarded package-state manager, not a blind debloater: inventory the device, distinguish user/system/disabled/suspended/removed states, refuse protected packages, apply reversible operations one at a time, read the real state back, and preserve recovery through snapshots, restore, and JSON import/export.

The old WebUI and shell modules are historical references only. Root and Dhizuku are not project dependencies or execution paths; the native app uses Shizuku only and reports unsupported operations honestly.

## 2. Original plan and engineering rules

The project follows M0, A1 through A8, then 1.0 release. Rules: no INTERNET permission; commands only through Shizuku UserService and ExecBridge; validated and quoted package names; every write read back; APPLIED only when read-back matches; protected packages refused in the repository; snapshots contain package state only; bulk work is sequential with per-package results; no fake root-only behaviour; CI proves build/tests while the reference phone proves behaviour.

## 3. Completed work

### M0 and A1

- Kotlin, Compose, Shizuku UserService/AIDL, ExecBridge, console, connection state, and uid display.
- Pinned toolchain: AGP 8.13.1, Kotlin 2.2.21, Gradle 8.13, Compose BOM 2025.12.00, SDK 36, Shizuku 13.1.5.
- No INTERNET permission.
- Package inventory for installed, removed-for-user, disabled/frozen, suspended, user, and system states.
- Persian/Arabic digit normalization for real dumpsys output.
- Details: version, SDK, installer, install/update dates, User 0 flags, raw output.
- Protected guard covers launcher, keyboard/input, dialer, SMS, WebView provider, Shizuku, VOID, core packages, and overlays.

### A2 operations

- Suspend/Unsuspend, including system-app Suspend as required by the product.
- Disable/Enable, shown as freeze/unfreeze.
- Force stop, Remove for user, Restore, and Clear data.
- Every mutation reads back real state. Clear data reports UNVERIFIABLE after command success because deletion has no reliable read-back.
- The detail screen now refreshes action availability from actual `dumpsys` state after reinstall. Phone validation confirmed that a previously VOID-suspended app now shows its real state and the correct Unsuspend action after reinstall.
- Protected packages show no actions and are refused by the repository.

### A3 recovery layer

- Snapshot model and restore planner.
- Protected packages excluded from restore plans; removed packages restored before state transitions.
- Durable JSON SnapshotStore with a 50-snapshot cap and PinStore foundation.
- Snapshot list, refresh, detail, delete, scroll/select/copy, restore preview, safety snapshot, per-package restore, read-back report.
- JSON Import/Export through the Android file picker, merging by snapshot id. Public storage target is `Download/VOID APPS/`; import/export is the supported cross-reinstall/ROM recovery path.
- Rapid identical snapshots are deduplicated within a short window; genuine state-changing operations remain snapshot-worthy.
- Phone validation confirmed: snapshot of 835 packages, Restore preview of one package, `1 applied · 0 not applied`, successful Unsuspend, safety snapshot, and JSON import/export of two snapshots.
- Latest phone evidence also confirmed protected `com.android.providers.media` shows `Protected (system data provider). Actions on this app are refused.` and raw User 0 state is displayed.
- Phone validation confirmed reference package details with `versionName=16`, `versionCode=1024`, `minSdk=36`, `targetSdk=36`, `installerPackageName=null`, and real User 0 flags.

## 4. Problems found and fixes

- Stale A2 action test and later clarified system-app Suspend requirement: tests were aligned with the actual requested contract.
- Compose `SelectionContainer` import error: corrected to `androidx.compose.foundation.text.selection.SelectionContainer`.
- Invalid `emptyList().let` in InventoryRepository: replaced with explicit typed construction.
- MediaStore lookup created duplicate JSON files and left the UI empty: storage now reads matching legacy files, merges by id, writes one canonical file, and deletes extras.
- App details initially used stale cached action state after reinstall: details now update repository state before actions rebuild.
- Android uninstall removes app-private files: durable public storage plus explicit JSON Import/Export was added.
- Multiple identical snapshots from rapid repeated callbacks: deduplicated by name, state, and short time window.
- OEM/system-package removal may be rejected under Shizuku shell. This is reported as unsupported/failed; Root and Dhizuku are intentionally out of scope.

## 5. Remaining work

- Pins UI and safe re-apply behaviour.
- Debloat knowledge base and reviewed presets, preview, protected guard, snapshot before batch, sequential results, and unsupported reporting.
- Batch operations and pure storage/import/restore tests.
- A4 runtime permissions and AppOps special access.
- A5 boot receivers, component control, and background AppOps.
- A6 notification listener, DND access, and per-app notification mute.
- A7 Chain3 per-app network blocking and netpolicy background-data controls.
- A8 APK/APKS/XAPK/APKM session install, OBB placement, extraction, cache trimming, and safe shared-storage cleanup.
- Rewrite README from legacy WebUI claims, About screen, icon/branding, fastlane metadata, release notes, signed release, final Redmi/Xiaomi smoke test, and merge to `main`.

## 6. Next execution order

1. Finish Pins and A3 batch acceptance test.
2. Add debloat knowledge base and presets with review/preview, guard, Shizuku capability results, snapshot, and rollback.
3. Implement A4, A5, A6, A7, and A8 in order, probing the reference phone before each feature.
4. Rewrite release documentation and remove legacy claims.
5. Run signed release and final device acceptance; merge only when CI and device checks are green.

## 7. Commit trail

- `3ad1a13`, `e8e7822`, `578008e`, `37f3f5c`: action policy, auto-snapshot foundation, system-app Suspend, and tests.
- `7300550`, `dce8be2`, `9f870bb`: Snapshot UI, delete API, navigation.
- `d2f17e0`, `38935ff`, `33d27e2`: detail line breaks, scroll/select, CI import fix.
- `38a08fc`, `abbb8a1`: uninstall-safe storage and file consolidation.
- `daf3f40`, `161a8a8`, `caafe42`: typed inventory result, real state refresh, Snapshot refresh.
- `d891669`: rapid duplicate snapshot deduplication.
- `5c30e81`: restore/Undo with per-package read-back.
- `8e07cd3`: JSON Import/Export.
- `ac0379d`: prior complete report.
- `8e07cd3`: final A3 JSON recovery path confirmed on phone.

## 8. Validation record

Owner-confirmed on the reference phone: Protected actions, real Freeze/Suspend state after reinstall, Snapshot display, scroll/copy, Restore/Undo, JSON Import/Export, and package details/read-back. The latest evidence shows the protected media provider refusing actions and the expected version/flags output.

Future work must keep user-facing strings English-only, avoid INTERNET permission, and never claim support without a real-device probe.
