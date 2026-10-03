# VOID // APPS: complete project handoff

Updated: 2026-10-03 (evening)
Branch: `native-app-v0`
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi/Redmi Android 16, SDK 36, Shizuku shell uid 2000.

This is the durable record of the product idea, engineering rules, completed work, failures, fixes, phone validation, and remaining path. The repository and latest green CI run are authoritative if this file becomes stale.

## 1. Product idea

VOID // APPS is a native Kotlin + Jetpack Compose Android package manager using Shizuku UserService/AIDL. It replaces the old Shevery/WebUI approach with a local, English-only app that can inspect and safely manage installed packages without INTERNET permission.

The product is a guarded package-state manager, not a blind debloater: inventory the device, distinguish user/system/disabled/suspended/removed states, refuse protected packages, apply reversible operations one at a time, read the real state back, and preserve recovery through snapshots, restore, and JSON import/export.

The old WebUI and shell modules are historical references only. Root and Dhizuku are not project dependencies or execution paths; the native app uses Shizuku only and reports unsupported operations honestly.

## 2. Engineering rules

No INTERNET permission. Commands only through Shizuku UserService and ExecBridge. Package names validated and quoted. Every write read back; APPLIED only when read-back matches. Protected packages refused in the repository, not only hidden in the UI. Snapshots hold package state only. Bulk work is sequential with per-package results. CI proves build and tests; the reference phone proves behaviour.

## 3. Status

| Step | Status | Phone evidence |
|---|---|---|
| M0 core | done | READY, uid 2000 |
| A1 inventory + guard + details | done | 835 total, 468 user, 367 system, 35 protected; counts match `pm` |
| A2 single-package operations | done | Suspend/Unsuspend user and system apps; protected apps show no actions |
| A3 snapshots, undo, pins, batch | **done** | see section 4 |
| Debloat track | next | |
| A4 to A8 | open | |
| 1.0 release | open | |

## 4. A3 acceptance (owner-confirmed on the phone)

- Snapshot list, detail (scroll and select/copy), delete, refresh.
- Restore/Undo: safety snapshot `Before undo`, one package at a time, per-package read-back report.
- JSON Import/Export through the system file picker; merge by id; one canonical `snapshots.json`.
- Real state after reinstall: actions rebuild from `dumpsys` read-back.
- Pins: Pin/Unpin in app details for user and system non-protected apps, Pinned packages screen, one canonical `pins.json` updated on every change.
- Batch acceptance test from PLAN: two user apps and one system app (`com.thirtytwo.steps`, `com.foxdebug.acode`, `com.android.egg`) suspended in one batch: `3 applied · 0 not applied`, exactly one `Before batch suspend` snapshot, Restore planned 3 changes and reported `3 applied · 0 not applied` with `unsuspend · applied` for each.
- Protected packages cannot be selected for a batch (no checkbox shown since the follow-up fix).

## 5. Problems found and fixes

- Stale A2 test and a later clarified rule that system apps keep Suspend: tests aligned with the requested contract.
- Wrong `SelectionContainer` import: fixed to `androidx.compose.foundation.text.selection.SelectionContainer`.
- Invalid `emptyList().let` in InventoryRepository: replaced with explicit typed construction.
- MediaStore duplicate files (`snapshots (1).json`, `pins (1).json` ...): the cursor loop skipped the first row, so the existing file was never found. Fixed with `moveToFirst` + do/while; reads merge every matching file and write one canonical file.
- Stale cached state after reinstall: details now update repository state before actions rebuild.
- App-private storage is wiped on uninstall, and a reinstall may not see files owned by the old install: public storage plus explicit Import/Export is the recovery path.
- Repeated identical snapshots from rapid callbacks: deduplicated by name, state and a short window.
- Batch selection showed an empty, unusable checkbox next to protected apps: replaced by an empty slot.
- Some OEM system packages cannot be removed under Shizuku shell on the reference ROM: reported as failed/unsupported. Root and Dhizuku are out of scope by owner decision.

## 6. Remaining work, in order

1. Debloat: port the legacy knowledge base and presets, risk tags, review screen with exact targets and proposed operation, guard on every entry including imported presets, one snapshot per batch, sequential read-back, honest unsupported results. Probe OEM examples (calculator, compass, notes) on the phone first.
2. A4 runtime permissions and AppOps special access.
3. A5 boot receivers, component control, background AppOps.
4. A6 notification listener, DND access, per-app notification mute.
5. A7 Chain3 per-app network block and netpolicy background data.
6. A8 APK/APKS/XAPK/APKM session install, OBB placement, extraction, cache trimming, safe shared-storage cleanup.
7. 1.0: rewrite README (still describes the legacy WebUI), About, icon, fastlane, release notes, signed release from CI secrets, final smoke test, merge to `main`.

## 7. Commit trail (native branch, this phase)

- `3ad1a13`, `e8e7822`, `578008e`, `37f3f5c`: action policy, auto-snapshot foundation, system-app Suspend.
- `7300550`, `dce8be2`, `9f870bb`: Snapshot UI, delete API, navigation.
- `d2f17e0`, `38935ff`, `33d27e2`: detail line breaks, scroll/select, import fix.
- `38a08fc`, `abbb8a1`: uninstall-safe storage, consolidation.
- `daf3f40`, `161a8a8`, `caafe42`: typed inventory result, real state refresh, Snapshot refresh.
- `d891669`: duplicate snapshot dedupe.
- `5c30e81`: Restore/Undo with per-package read-back.
- `8e07cd3`: JSON Import/Export.
- `71c4563`, `17c1528`, `dbd8ccb`: Pins in details, Pinned packages screen, canonical `pins.json`.
- `8a3927f`: batch Suspend/Unsuspend/Force stop with one snapshot and per-package results.
- this commit: no checkbox on protected rows; A3 closed in docs.
