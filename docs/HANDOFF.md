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

No INTERNET permission. Commands only through Shizuku UserService and ExecBridge. Package and permission names validated and quoted. Every write read back; APPLIED only when read-back matches. Protected packages refused in the repository, not only hidden in the UI. Snapshots hold package state only. Bulk work is sequential with per-package results. Code and the strings/resources it uses land in one commit. CI proves build and tests; the reference phone proves behaviour. Diagnose CI failures from the actual failing test name and line in the log, never from assumption. Shell output is capped at 64 KiB by ExecBridge: any new shell filter must be measured on the phone (`| wc -c` on a large app such as Drive) before it is relied on; an unmeasured extra read must be non-fatal.

## 3. Status

| Step | Status | Phone evidence |
|---|---|---|
| M0 core | done | READY, uid 2000 |
| A1 inventory + guard + details | done | 835 total, 468 user, 367 system, 35 protected; counts match `pm` |
| A2 single-package operations | done | Suspend/Unsuspend user and system apps; protected apps show no actions. Suspend and Remove-for-user re-tested 2026-10-04 on a system and a user app |
| A3 snapshots, undo, pins, batch | done | phone-verified |
| Debloat track | done | CI green and owner phone acceptance, including search and cross-manager restore |
| A4 permissions + AppOps | **in progress** | Permission audit and Grant/Revoke phone-verified on Drive, Meet, Play Store. AppOps card, OEM ops, uid/package scope split phone-verified on Drive. Shared-uid runtime permissions (MIUI) implemented, awaiting CI and phone test. AppOps writes remain |
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
- A4 large apps (`9055b5a`): Drive, Meet and Play Store permission lists load. Camera and location were revoked in VOID, the app asked for them again on launch, then granted again in VOID and the grant took effect inside the app.
- A2 re-test (2026-10-04): Suspend and Remove-for-user succeeded again on a system app and a user app.
- A4 AppOps audit card on Drive `com.google.android.apps.docs` (system): card rendered below Permissions, op/mode list matched the raw output, raw toggle worked, nothing changed.
- A4 AppOps follow-up (`c08a508`): `miuiop(10008) · allow · OEM · read-only` and `access_restricted_settings · default (also reported: allow)` shown on Drive.
- A4 AppOps scopes (`3964931`): Drive shows `access_restricted_settings · uid: allow · package: default`, `camera · uid: foreground`, `wake_lock · package: allow`, `miuiop(10008) · package: allow · OEM · read-only`.

## 5. Current checkpoint and remaining path

Phone findings used by the latest pushes:

- Drive: full `dumpsys package` = 128886 bytes (above the 64 KiB cap). Packages 1585, Hidden system packages 1774, Queries 1904. `sed -n '/^Packages:/,$p' | wc -c` = 35719.
- Drive uid 10176: `appops get 10176` prints only the uid block, exactly the first block of `appops get <pkg>`. `appops get --uid <pkg>` does not separate scopes on this ROM.
- `com.miui.securitycenter` layout: Activity Resolver Table 1 ... Permissions 1074..1373 (44 blocks), Registered ContentProviders 1380, ContentProvider Authorities 1456, Key Set Manager 1569, **Packages 1573**, Hidden system packages 2319, Queries 3036, **Shared users 3501**, Dexopt state 4049, Compiler stats 4057.
- securitycenter is in shared user `android.uid.system/1000` (`sharedUser=SharedUserSetting{cfee48 android.uid.system/1000}` at 1576). `ACCESS_COARSE_LOCATION` appears as requested at 1742, but `runtime permissions:` exists only at 4021, inside Shared users: `ACCESS_COARSE_LOCATION: granted=true, flags=[ SYSTEM_FIXED|GRANTED_BY_DEFAULT|RESTRICTION_SYSTEM_EXEMPT|RESTRICTION_UPGRADE_EXEMPT]`. VOID read only Packages:, so it showed `unknown`. Another app manager shows it `dangerous|granted` and its revoke fails.

Latest push reads `Shared users:` for shared-uid packages (second call, non-fatal) and refuses Grant/Revoke for shared system uids. Phone test (owner):

1. `com.miui.securitycenter`: dangerous permissions show `granted · runtime · fixed`, a red note `Shared system uid android.uid.system (1000)`, and no Grant/Revoke buttons.
2. Size check: `dumpsys package com.miui.securitycenter | sed -n '/^Shared users:/,/^[A-Z]/p' | wc -c` (must be under 65536; if not, the app silently keeps the Packages-only view).
3. Drive, Meet, Play Store still behave as before (no shared user there).

After the test:

1. Guarded AppOps set/reset with scope (`appops set --uid` for uid scope, `appops set` for package scope), valid-name and valid-mode checks, protected-app and OEM refusal, refusal of uid scope on shared system uids, confirmation, and read-back.
2. Read-only Self-check over all packages.
3. Close A4 and move to A5: boot receivers, component control, and background AppOps.
4. Continue A6 notifications/DND, A7 network controls, A8 APK/APKS/XAPK installer, then 1.0 release work.

## 6. Design and safety decisions

- AppOps is not the same thing as a runtime permission. Both are shown as separate sections.
- AppOps audit keeps the raw output of every command visible so ROM-specific formats are checked against reality, not guessed.
- AppOps scopes: `appops get <uid>` is the uid scope; the rest of `appops get <package>` after that exact prefix is the package scope. If the prefix does not match line by line, the merged view with all reported modes is shown.
- OEM AppOps (for example `MIUIOP(n)`) are shown but never changeable; `isValidAppOp` rejects them so no set/reset command can target them.
- Permission audit reads only the active `Packages:` block of `dumpsys package` and stops at the next top-level header, so the hidden factory package of an updated system app cannot overwrite runtime flags. Lines reach the parser unchanged.
- Shared-uid packages: runtime permissions are read from `Shared users:`. They belong to the whole uid, so Grant/Revoke is refused for system-range shared uids (< 10000, for example android.uid.system) and warned for app-range shared uids.
- Owner decision (2026-10-04): `com.miui.securitycenter` stays unprotected. The platform already fixes its dangerous permissions (SYSTEM_FIXED), and the owner wants users to keep the other options.
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
- Full `dumpsys package` for large apps exceeds the 64 KiB cap (Drive: 128886 bytes). First fix (`c08a508`, `grep -nE` on permission-shaped lines) was pushed without measuring and was still truncated on the phone. Second fix (`9055b5a`) reads only the `Packages:` block, measured first; phone-verified on Drive, Meet, Play Store.
- Shared-uid apps (securitycenter) showed runtime permissions as `unknown` because they live in `Shared users:`; now read from there.
- Permission card error now shows the exact error text with copy/share.
- Parser dropped OEM ops with a numeric suffix (`MIUIOP(10008)`); now parsed as read-only OEM records.
- Android 16 prints some ops twice (uid scope, then package scope); now split by scope using `appops get <uid>`.
- Android may stop the target process during permission changes; UI warns before Grant/Revoke.
- Some OEM system packages cannot be removed under Shizuku shell; reported unsupported. Root/Dhizuku remain out of scope.

## 8. Remaining work, in order

1. Confirm the latest push is green, then phone test (section 5) on securitycenter, including the size check.
2. Guarded AppOps set/reset with scope and read-back, Drive phone test.
3. Read-only Self-check over all packages.
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
- `c08a508`: OEM AppOps and duplicate modes (phone-verified), exact permission error text; its grep permission filter was still truncated on Drive.
- `9055b5a`: permission audit reads only the active `Packages:` block (phone-verified on Drive, Meet, Play Store).
- `3964931`: AppOps uid/package scope split (phone-verified on Drive).
- current push: shared-uid runtime permissions from `Shared users:`, shared system uid write refusal.
