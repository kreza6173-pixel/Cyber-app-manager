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

No INTERNET permission. Commands only through Shizuku UserService and ExecBridge. Package and permission names validated and quoted. Every write read back; APPLIED only when read-back matches. Protected packages refused in the repository, not only hidden in the UI. Snapshots hold package state only. Bulk work is sequential with per-package results. Code and the strings/resources it uses land in one commit. CI proves build and tests; the reference phone proves behaviour.

## 3. Status

| Step | Status | Phone evidence |
|---|---|---|
| M0 core | done | READY, uid 2000 |
| A1 inventory + guard + details | done | 835 total, 468 user, 367 system, 35 protected; counts match `pm` |
| A2 single-package operations | done | Suspend/Unsuspend user and system apps; protected apps show no actions |
| A3 snapshots, undo, pins, batch | done | see section 4 |
| Debloat track | done | CI green and owner phone acceptance, including search and cross-manager restore |
| A4 permissions + AppOps | **in progress** | read-only audit verified on Acode (user) and Android Easter Egg (system); Grant/Revoke verified on a real running app; Android stopped the app after revoke as a platform side effect; AppOps next |
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
- A4 Grant/Revoke: `POST_NOTIFICATIONS` grant and revoke both returned APPLIED with matching before/after read-back. The target app stopped after revoke, matching Android’s process enforcement behavior; warning text now makes this explicit.

## 5. A4 design

- Audit reads the `Packages:` block of `dumpsys package <pkg>` with `sed`, with a header-preserving grep fallback.
- Sections parsed: `requested permissions:`, `install permissions:`, and `runtime permissions:` under `User 0:` only. Unknown output never creates state.
- A permission is changeable only if runtime, known, and not `SYSTEM_FIXED` / `POLICY_FIXED`.
- Grant/Revoke runs `pm grant|revoke --user 0`, re-reads the audit, and reports APPLIED / NOT_APPLIED / UNVERIFIABLE with before/after state.
- Android may stop the target process while applying a permission change. VOID treats the permission state as the thing being changed, not app liveness.
- Permission changes are not part of package snapshots; the result card records previous state for manual reversal.

## 6. Installer scope decision

The planned installer supports **APK, APKS and XAPK** only. APKM is explicitly out of scope and must not be added to implementation or release claims.

## 7. Problems found and fixes

- Stale A2 test and clarified system-app Suspend rule: tests aligned.
- Wrong SelectionContainer import: fixed.
- Invalid typed inventory construction: fixed.
- MediaStore duplicate files: fixed with moveToFirst + do/while; one canonical file.
- Stale state after reinstall: details update repository state before actions rebuild.
- App-private storage wiped on uninstall: public storage plus Import/Export recovery.
- Repeated identical snapshots: deduplicated.
- Protected batch checkbox: replaced by an empty slot.
- Debloat disclaimer/search space: compact card and fixed-height search.
- CI run #46 red: A4 UI and strings split across commits; #47 fixed it. Code and resources now land together.
- First A4 audit grep dropped the runtime header: replaced by section-aware parsing.
- Runtime permission changes can stop the target app process on Android; UI now warns before revoke/grant.
- Some OEM system packages cannot be removed under Shizuku shell: reported unsupported. Root and Dhizuku out of scope.

## 8. Remaining work, in order

1. Finish A4: AppOps special-access audit and guarded set/reset with honest unsupported results.
2. A5 boot receivers, component control, background AppOps.
3. A6 notification listener, DND access, per-app notification mute.
4. A7 Chain3 per-app network block and netpolicy background data.
5. A8 installer for APK, APKS and XAPK, OBB placement, extraction, cache trimming, safe shared-storage cleanup. APKM stays out of scope.
6. 1.0: rewrite README including Debloat disclaimer, About, icon, fastlane, release notes, signed release, final smoke test, merge to `main`.

## 9. Commit trail

- `3ad1a13` through `a9d19f0`: native foundation, A1-A3, Debloat, cross-manager restore, and Debloat UI fixes.
- `c86a46c`, `4a128f8`: A4 parser foundation; APKM removed from scope.
- `2d66045`, `1196863`: read-only permission audit in details (+ strings fix).
- `1ea2245`: runtime section parsing with flags, guarded Grant/Revoke with read-back, tests, docs.
- current push: permission-change warning and documentation of Android process-stop behavior.
