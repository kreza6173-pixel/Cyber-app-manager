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
| A4 permissions + AppOps | **in progress** | Permission audit and Grant/Revoke phone-verified (Drive, Meet, Play Store). Shared-uid permissions phone-verified (securitycenter). AppOps audit and scope split phone-verified (Drive uid 10176, securitycenter uid 1000). Guarded AppOps change implemented, awaiting CI and phone test |
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
- A4 AppOps audit card on Drive: card rendered below Permissions, op/mode list matched the raw output, raw toggle worked, nothing changed.
- A4 AppOps OEM and duplicates (`c08a508`): `miuiop(10008) · allow · OEM · read-only` on Drive.
- A4 AppOps scopes (`3964931`): Drive `access_restricted_settings · uid: allow · package: default`, `camera · uid: foreground`. Securitycenter (uid 1000): `receive_sms · uid: ignore · package: allow`, `system_alert_window · uid: ignore · package: default`, `miuiop(10033)`, `miuiop(10044)`, `miuiop(10053)` OEM read-only.
- A4 shared uid (`0d4cd24`): securitycenter shows the red note `Shared system uid android.uid.system (1000)`, dangerous permissions such as `ACCESS_COARSE_LOCATION` and `ACCESS_BACKGROUND_LOCATION` as `granted · runtime · fixed`, no Grant/Revoke buttons. `sed -n '/^Shared users:/,/^[A-Z]/p' | wc -c` = 37361 bytes. Drive unchanged.

## 5. Current checkpoint and remaining path

Latest code push `dc3ec81`: guarded AppOps change.

- Change button per standard op, only when uid and package scopes were split and the app is not protected. OEM ops never get a button.
- Dialog: scope (Package, or Uid; Uid disabled for system uids < 10000) and mode (allow, ignore, deny, foreground, default).
- Commands: package scope `appops set <pkg> <OP> <mode>`, uid scope `appops set <uid> <OP> <mode>` (numeric uid, same form as the probed `appops get <uid>`). Op names are sent uppercase because Android prints them uppercase. The uid form of `set` has not been probed yet; read-back decides.
- Read-back: fresh scoped audit; an op missing from a scope counts as default. APPLIED only on match, UNVERIFIABLE when the read-back cannot be split.
- AppOps changes are not part of snapshots; the result card shows the previous mode for manual reversal.

Phone test (owner), on a normal user app first, then Drive:

1. Package scope: pick an op that is listed in package scope (for example `wake_lock · package: allow`), change it to `ignore`, expect APPLIED and `package: ignore`; change it back to `allow`, expect APPLIED.
2. Uid scope on a user app: change a uid-scope op (for example `camera`) and back; expect APPLIED both times, or an honest NOT_APPLIED with the command output.
3. securitycenter: Uid option is disabled; package scope works or is honestly reported.
4. OEM ops (`miuiop(...)`) have no Change button.

After the test:

1. Read-only Self-check over all packages (permission audit, AppOps parse and split, size-cap hits, unknown lines).
2. Close A4 and move to A5: boot receivers, component control, and background AppOps.
3. Continue A6 notifications/DND, A7 network controls, A8 APK/APKS/XAPK installer, then 1.0 release work.

## 6. Design and safety decisions

- AppOps is not the same thing as a runtime permission. Both are shown as separate sections.
- AppOps audit keeps the raw output of every command visible so ROM-specific formats are checked against reality, not guessed.
- AppOps scopes: `appops get <uid>` is the uid scope; the rest of `appops get <package>` after that exact prefix is the package scope. If the prefix does not match line by line, the merged view with all reported modes is shown and changes are disabled.
- AppOps changes: one op, one scope at a time, confirmation dialog, read-back. Uid scope is refused for system uids (< 10000) because it would apply to every package in the uid.
- OEM AppOps (for example `MIUIOP(n)`) are shown but never changeable; `isValidAppOp` rejects them so no set command can target them.
- Permission audit reads only the active `Packages:` block of `dumpsys package` and stops at the next top-level header, so the hidden factory package of an updated system app cannot overwrite runtime flags.
- Shared-uid packages: runtime permissions are read from `Shared users:`. They belong to the whole uid, so Grant/Revoke is refused for system-range shared uids and warned for app-range shared uids.
- Owner decision (2026-10-04): `com.miui.securitycenter` stays unprotected. The platform already fixes its dangerous permissions (SYSTEM_FIXED), and the owner wants users to keep the other options.
- Protected packages remain read-only even when Android exposes operations.
- No change is reported APPLIED without read-back from Android.
- APKM is out of scope; installer support is APK, APKS and XAPK only.
- Root and Dhizuku are out of scope; execution is Shizuku only.

## 7. Problems found and fixes

- CI run #46 red: A4 UI and strings were split across commits; #47 fixed it. Code/resources now land together.
- First A4 audit grep dropped the runtime header; replaced by section-aware parsing.
- Runtime audit initially showed stale state; fixed with full dumpsys plus authoritative `pm check-permission` read-back.
- AppOps parser, CI #53 to #55 (verified from the CI logs): #53 failed `parsesCommonAppOpsOutput` (anchored `matchEntire` rejected the `Uid mode:` prefix); #54 removed the anchor and broke `ignoresUnsupportedModesAndInvalidNames` (`bad-name: deny` matched as `name`); #55 used `findAll().lastOrNull()`, identical results, still red. Fix `e587840` (CI #56 green): anchored start with an optional word prefix.
- Disable confirmation dialog showed the Suspend warning text; now uses `warn_freeze`.
- Full `dumpsys package` for large apps exceeds the 64 KiB cap (Drive: 128886 bytes). `c08a508` (grep filter) was pushed without measuring and was still truncated. `9055b5a` reads only the `Packages:` block, measured first.
- Shared-uid apps (securitycenter) showed runtime permissions as `unknown` because they live in `Shared users:`; fixed in `0d4cd24`.
- Parser dropped OEM ops with a numeric suffix (`MIUIOP(10008)`); now parsed as read-only OEM records.
- Android 16 prints some ops twice (uid scope, then package scope); now split by scope using `appops get <uid>`.
- Android may stop the target process during permission changes; UI warns before Grant/Revoke.
- Some OEM system packages cannot be removed under Shizuku shell; reported unsupported. Root/Dhizuku remain out of scope.

## 8. Remaining work, in order

1. Confirm `dc3ec81` is green, then phone test the AppOps change (section 5).
2. Read-only Self-check over all packages.
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
- `e587840`: anchored AppOps parser (CI #56 green).
- `8a6ec66`: AppOps audit card, raw output, Disable warning fix.
- `c08a508`: OEM AppOps and duplicate modes; its grep permission filter was still truncated.
- `9055b5a`: permission audit reads only the active `Packages:` block.
- `3964931`: AppOps uid/package scope split.
- `0d4cd24`: shared-uid runtime permissions, shared system uid write refusal.
- `dc3ec81`: guarded AppOps change with scope and read-back.
