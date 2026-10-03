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
| A3 snapshots, undo, pins, batch | done | see section 4 |
| Debloat track | **done** | CI green and owner phone acceptance, including search and cross-manager restore |
| A4 permissions + AppOps | **in progress** | permission parser and unit tests added; UI and writes remain |
| A5 to A8 | open | |
| 1.0 release | open | |

## 4. A3 acceptance (owner-confirmed on the phone)

- Snapshot list, detail (scroll and select/copy), delete, refresh.
- Restore/Undo: safety snapshot `Before undo`, one package at a time, per-package read-back report.
- JSON Import/Export through the system file picker; merge by id; one canonical `snapshots.json`.
- Real state after reinstall: details now update repository state before actions rebuild.
- Pins: Pin/Unpin in app details for user and system non-protected apps, Pinned packages screen, one canonical `pins.json` updated on every change.
- Batch acceptance: two user apps and one system app suspended in one batch, one snapshot, sequential read-back, then restored successfully.
- Protected packages cannot be selected for a batch.
- Debloat: SAFE-only knowledge-base presets, compact disclaimer tested on Xiaomi Redmi Note 14 Global ROM, preset/package search, Suspend and Remove-for-user, snapshots, read-back reports, and Restore for packages previously removed by another manager.

## 5. A4 foundation in this push

- Added `PermissionAudit` and a defensive parser for requested/install permission sections from `dumpsys package`.
- Unknown ROM sections are ignored rather than treated as granted or denied.
- Unit tests cover granted/denied parsing, unknown sections, and empty output.
- Next A4 push will expose this audit in app details, then add guarded grant/revoke and AppOps auditing.

## 6. Problems found and fixes

- Stale A2 test and a later clarified rule that system apps keep Suspend: tests aligned with the requested contract.
- Wrong `SelectionContainer` import: fixed to `androidx.compose.foundation.text.selection.SelectionContainer`.
- Invalid `emptyList().let` in InventoryRepository: replaced with explicit typed construction.
- MediaStore duplicate files: fixed with `moveToFirst` + do/while; reads merge every matching file and writes one canonical file.
- Stale cached state after reinstall: details now update repository state before actions rebuild.
- App-private storage is wiped on uninstall, so public storage plus explicit Import/Export is the recovery path.
- Repeated identical snapshots: deduplicated by name, state and a short window.
- Protected package batch checkbox: replaced by an empty slot.
- Some OEM system packages cannot be removed under Shizuku shell on the reference ROM: reported as failed/unsupported. Root and Dhizuku remain out of scope.

## 7. Remaining work, in order

1. Finish A4: details permission audit, guarded runtime grant/revoke, AppOps special-access audit and honest unsupported results.
2. A5 boot receivers, component control, background AppOps.
3. A6 notification listener, DND access, per-app notification mute.
4. A7 Chain3 per-app network block and netpolicy background data.
5. A8 APK/APKS/XAPK/APKM session install, OBB placement, extraction, cache trimming, safe shared-storage cleanup.
6. 1.0: rewrite README (still describes the legacy WebUI), About, icon, fastlane, release notes, signed release from CI secrets, final smoke test, merge to `main`.

## 8. Commit trail

- `3ad1a13` through `a9d19f0`: native foundation, A1-A3, Debloat, cross-manager restore, and Debloat UI fixes.
- current push: A4 permission parser and unit tests; roadmap updated.
