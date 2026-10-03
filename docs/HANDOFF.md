# VOID // APPS: complete project handoff

Updated: 2026-10-03 (night)
Branch: `native-app-v0`
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi Redmi Note 14, Global ROM, Android 16, SDK 36, Shizuku shell uid 2000.

This is the durable record of the product idea, engineering rules, completed work, failures, fixes, phone validation, and remaining path. The repository and latest green CI run are authoritative if this file becomes stale.

## 1. Product idea

VOID // APPS is a native Kotlin + Jetpack Compose Android package manager using Shizuku UserService/AIDL. It replaces the old Shevery/WebUI approach with a local, English-only app that can inspect and safely manage installed packages without INTERNET permission.

The product is a guarded package-state manager, not a blind debloater: inventory the device, distinguish user/system/disabled/suspended/removed states, refuse protected packages, apply reversible operations one at a time, read the real state back, and preserve recovery through snapshots, restore, and JSON import/export.

The old WebUI and shell modules are historical references only. Root and Dhizuku are not project dependencies or execution paths; the native app uses Shizuku only and reports unsupported operations honestly.

## 2. Engineering rules

No INTERNET permission. Commands only through Shizuku UserService and ExecBridge. Package and permission names validated and quoted. Every write read back; APPLIED only when read-back matches. Protected packages refused in the repository, not only hidden in the UI. Snapshots hold package state only. Bulk work is sequential with per-package results. Code and the strings/resources it uses land in one commit. CI proves build and tests; the reference phone proves behaviour. Diagnose CI failures from the actual failing test name and line in the log, never from assumption.

## 3. Status

| Step | Status | Phone evidence |
|---|---|---|
| M0 core | done | READY, uid 2000 |
| A1 inventory + guard + details | done | 835 total, 468 user, 367 system, 35 protected; counts match `pm` |
| A2 single-package operations | done | Suspend/Unsuspend user and system apps; protected apps show no actions |
| A3 snapshots, undo, pins, batch | done | phone-verified |
| Debloat track | done | CI green and owner phone acceptance, including search and cross-manager restore |
| A4 permissions + AppOps | **in progress** | Permission audit and Grant/Revoke phone-verified. AppOps parser/model implemented and unit-tested; CI #53 to #55 red on parser edge cases, root cause fixed in the latest push (see section 7). AppOps UI and writes remain |
| A5 to A8 | open | |
| 1.0 release | open | |

## 4. Acceptance record (owner-confirmed on the phone)

- Snapshot list, detail (scroll and select/copy), delete, refresh.
- Restore/Undo: safety snapshot `Before undo`, one package at a time, per-package read-back report.
- JSON Import/Export through the system file picker; merge by id; one canonical `snapshots.json`.
- Real state after reinstall: details update repository state before actions rebuild.
- Pins: Pin/Unpin for user and system non-protected apps, Pinned packages screen, one canonical `pins.json`.
- Batch: two user apps and one system app suspended in one batch, one snapshot, sequential read-back, restored successfully.
- Protected packages cannot be selected for a batch.
- Debloat: SAFE-only presets, compact disclaimer (tested on Xiaomi Redmi Note 14 Global ROM; guidance, not a guarantee), preset/package search, Suspend and Remove-for-user, snapshots, read-back reports, Restore for packages removed earlier by another manager.
- A4 audit: permission list with granted / not granted / unknown states on user and system apps. `WRITE_MEDIA_STORAGE` correctly shows unknown on a user app.
- A4 Grant/Revoke: `POST_NOTIFICATIONS` grant and revoke both returned APPLIED with matching before/after read-back. The target app stopped after revoke, matching Android process enforcement behavior.

## 5. Current checkpoint and remaining path

The latest successful phone checkpoint is permission audit plus runtime Grant/Revoke. CI #53 to #55 were red in `AppOpsTest`; the real root cause and fix are recorded in section 7. The latest push must be confirmed green before continuing.

After CI is green:

1. Add AppOps audit to App Details using `appops get <package>`.
2. Show AppOps modes (`allow`, `deny`, `ignore`, `foreground`, `default`) separately from manifest permissions.
3. Add guarded AppOps set/reset with valid-name and valid-mode checks, protected-app refusal, confirmation, and read-back.
4. Phone-test Drive as a system app and a normal user app, then record unsupported OEM/API behavior honestly.
5. Close A4 and move to A5: boot receivers, component control, and background AppOps.
6. Continue A6 notifications/DND, A7 network controls, A8 APK/APKS/XAPK installer, then 1.0 release work.

## 6. Design and safety decisions

- AppOps is not the same thing as a runtime permission. Both are shown as separate sections.
- Protected packages remain read-only even when Android exposes operations.
- No change is reported APPLIED without read-back from Android.
- APKM is out of scope; installer support is APK, APKS and XAPK only.
- Root and Dhizuku are out of scope; execution is Shizuku only.

## 7. Problems found and fixes

- CI run #46 red: A4 UI and strings were split across commits; #47 fixed it. Code/resources now land together.
- First A4 audit grep dropped the runtime header; replaced by section-aware parsing.
- Runtime audit initially showed stale state; fixed with full dumpsys plus authoritative `pm check-permission` read-back.
- AppOps parser, CI #53 to #55 (verified from the CI logs):
  - #53 failed `parsesCommonAppOpsOutput` (AppOpsTest.kt:16): the regex was anchored with `matchEntire`, so `Uid mode: COARSE_LOCATION: foreground` (space inside the prefix) never matched.
  - #54 removed the start anchor. That fixed test 1 but broke `ignoresUnsupportedModesAndInvalidNames` (AppOpsTest.kt:27): `bad-name: deny` matched mid-line as op `name`, giving 2 operations instead of 1.
  - #55 switched to `findAll().lastOrNull()`, which produces identical results on these inputs, so it stayed red on line 27. The earlier note blaming the prefixed-line case for #55 was wrong.
  - Fix: keep the start anchored and allow only an optional word prefix: `^(?:[A-Za-z ]+:\s*)?op:\s*mode(;detail)?$`. Both tests satisfied by the same pattern.
- Android may stop the target process during permission changes; UI warns before Grant/Revoke.
- Some OEM system packages cannot be removed under Shizuku shell; reported unsupported. Root/Dhizuku remain out of scope.

## 8. Remaining work, in order

1. Confirm the anchored AppOps parser fix is green.
2. AppOps audit UI, guarded set/reset, read-back, and Drive phone test.
3. A5 boot receivers, component control, background AppOps.
4. A6 notification listener, DND access, per-app notification mute.
5. A7 Chain3 per-app network block and netpolicy background data.
6. A8 installer for APK, APKS and XAPK, OBB placement, extraction, cache trimming, safe shared-storage cleanup. APKM stays out of scope.
7. 1.0: README including disclaimers, About, icon, fastlane, release notes, signed release, final smoke test, merge to `main`.

## 9. Commit trail

- `3ad1a13` through `a9d19f0`: native foundation, A1-A3, Debloat, cross-manager restore, and Debloat UI fixes.
- `c86a46c`, `4a128f8`: A4 foundation and APKM scope decision.
- `2d66045`, `1196863`: read-only permission audit in details.
- `1ea2245`: runtime parsing and Grant/Revoke.
- `ec27559`, `49a285e`: authoritative runtime read-back.
- `a85692c`, `822fac8`, `cb24d9e`: AppOps parser foundation and two unsuccessful parser fixes (CI #53 to #55 red).
- current push: anchored AppOps parser with optional word prefix; corrected CI diagnosis.
