# VOID // APPS: complete project handoff

Updated: 2026-10-04
Branch: `native-app-v0`
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi Redmi Note 14, Global ROM, Android 16, SDK 36, Shizuku shell uid 2000.

This is the durable record of the product idea, engineering rules, completed work, failures, fixes, phone validation, and remaining path. The repository and latest green CI run are authoritative if this file becomes stale.

## 1. Product idea

VOID // APPS is a native Kotlin + Jetpack Compose Android package manager using Shizuku UserService/AIDL. It replaces the old Shevery/WebUI approach with a local, English-only app that can inspect and safely manage installed packages without INTERNET permission.

The product is a guarded package-state manager, not a blind debloater: inventory the device, distinguish user/system/disabled/suspended/removed states, refuse protected packages, apply reversible operations one at a time, read the real state back, and preserve recovery through snapshots, restore, and JSON import/export.

The old WebUI and shell modules are historical references only. Root and Dhizuku are not project dependencies or execution paths; the native app uses Shizuku only and reports unsupported operations honestly.

## 2. Engineering rules

No INTERNET permission. Commands only through Shizuku UserService and ExecBridge. Package and permission names validated and quoted. Every write read back; APPLIED only when read-back matches. Protected packages refused in the repository, not only hidden in the UI. Snapshots hold package state only. Bulk work is sequential with per-package results. Code and the strings/resources it uses land in one commit. CI proves build and tests; the reference phone proves behaviour. Diagnose CI failures from the actual failing test name and line in the log, never from assumption. Shell output is capped at 64 KiB by ExecBridge: filter large dumps on the device and never rely on full `dumpsys` output.

## 3. Status

| Step | Status | Phone evidence |
|---|---|---|
| M0 core | done | READY, uid 2000 |
| A1 inventory + guard + details | done | 835 total, 468 user, 367 system, 35 protected; counts match `pm` |
| A2 single-package operations | done | Suspend/Unsuspend user and system apps; protected apps show no actions |
| A3 snapshots, undo, pins, batch | done | phone-verified |
| Debloat track | done | CI green and owner phone acceptance, including search and cross-manager restore |
| A4 permissions + AppOps | **in progress** | Permission audit and Grant/Revoke phone-verified on small apps. AppOps audit card phone-verified on Drive. Large-app permission audit, OEM ops and duplicate modes fixed in the latest push, awaiting CI and re-test. AppOps writes remain |
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
- A4 AppOps audit card (CI #57 build) on Drive `com.google.android.apps.docs` (system): card rendered below Permissions, op/mode list matched the raw output for standard ops, raw toggle worked, nothing changed.

## 5. Current checkpoint and remaining path

Phone findings on Drive with the CI #57 build:

- Permission card showed `Permission state unavailable on this ROM.` Probe: `dumpsys package com.google.android.apps.docs | wc -c` = 128886 bytes, above the 64 KiB cap, so the audit was rejected as truncated. Earlier tests only used small apps.
- `MIUIOP(10008): allow` and `MIUIOP(10053): ignore` were silently dropped by the parser.
- `ACCESS_RESTRICTED_SETTINGS` printed twice (`allow`, then `default`); only the last was shown.
- `appops get --uid com.google.android.apps.docs` printed exactly the same output as `appops get <pkg>`, so `--uid` with a package name does not separate scopes on this ROM.

Latest push fixes the first three. Phone re-test (owner):

1. Drive App Details: Permissions card lists permissions (or shows the exact error text if still failing).
2. AppOps card shows `miuiop(10008)` and `miuiop(10053)` marked `OEM · read-only`, and `access_restricted_settings · default (also reported: allow)`.
3. Console probe for scope separation: `pm list packages -U com.google.android.apps.docs` to get the uid, then `appops get <that uid number>`. If it prints only the first block (the `Uid mode:` lines), uid and package scopes can be split reliably.

After the re-test:

1. Split uid and package scopes if the probe confirms it; otherwise keep the merged view with all reported modes.
2. Add guarded AppOps set/reset with valid-name and valid-mode checks, protected-app refusal, OEM refusal, confirmation, and read-back.
3. Close A4 and move to A5: boot receivers, component control, and background AppOps.
4. Continue A6 notifications/DND, A7 network controls, A8 APK/APKS/XAPK installer, then 1.0 release work.

## 6. Design and safety decisions

- AppOps is not the same thing as a runtime permission. Both are shown as separate sections.
- AppOps audit keeps the raw `appops get` output visible so ROM-specific formats are checked against reality, not guessed.
- OEM AppOps (for example `MIUIOP(n)`) are shown but never changeable; `isValidAppOp` rejects them so no set/reset command can target them.
- When Android prints one op more than once, every reported mode is shown instead of silently keeping one.
- Permission audit filters `dumpsys package` on the device with `grep -nE`; line-number gaps are turned into blank lines so section parsing matches the full dump.
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
  - Fix (`e587840`, CI #56 green): keep the start anchored and allow only an optional word prefix: `^(?:[A-Za-z ]+:\s*)?op:\s*mode(;detail)?$`.
- Disable confirmation dialog showed the Suspend warning text (`actionWarning` mapped FREEZE to `warn_suspend`); now uses `warn_freeze`.
- Full `dumpsys package` for large apps exceeds the 64 KiB cap (Drive: 128886 bytes), so the permission audit failed with a generic message; now filtered on the device and the card shows the exact error text.
- Parser dropped OEM ops with a numeric suffix (`MIUIOP(10008)`); now parsed as read-only OEM records.
- Android 16 prints some ops twice (uid scope, then package scope); the UI now lists every reported mode.
- Android may stop the target process during permission changes; UI warns before Grant/Revoke.
- Some OEM system packages cannot be removed under Shizuku shell; reported unsupported. Root/Dhizuku remain out of scope.

## 8. Remaining work, in order

1. Confirm the latest push is green, then phone re-test (section 5), including the uid probe.
2. Uid/package scope split if the probe allows it.
3. Guarded AppOps set/reset with read-back, and Drive phone test.
4. A5 boot receivers, component control, background AppOps.
5. A6 notification listener, DND access, per-app notification mute.
6. A7 Chain3 per-app network block and netpolicy background data.
7. A8 installer for APK, APKS and XAPK, OBB placement, extraction, cache trimming, safe shared-storage cleanup. APKM stays out of scope.
8. 1.0: README including disclaimers, About, icon, fastlane, release notes, signed release, final smoke test, merge to `main`.

## 9. Commit trail

- `3ad1a13` through `a9d19f0`: native foundation, A1-A3, Debloat, cross-manager restore, and Debloat UI fixes.
- `c86a46c`, `4a128f8`: A4 foundation and APKM scope decision.
- `2d66045`, `1196863`: read-only permission audit in details.
- `1ea2245`: runtime parsing and Grant/Revoke.
- `ec27559`, `49a285e`: authoritative runtime read-back.
- `a85692c`, `822fac8`, `cb24d9e`: AppOps parser foundation and two unsuccessful parser fixes (CI #53 to #55 red).
- `e587840`: anchored AppOps parser with optional word prefix (CI #56 green).
- `8a6ec66`: read-only AppOps audit card, raw output toggle, header test, Disable warning fix (phone-verified on Drive).
- current push: on-device permission dump filter, OEM AppOps, duplicate modes, exact permission error text.
