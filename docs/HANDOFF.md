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
| A4 permissions + AppOps | **in progress** | read-only audit verified on Acode (user) and Android Easter Egg (system); Grant/Revoke pushed, awaiting phone test; AppOps next |
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
- A4 audit: permission list with granted / not granted / unknown states on user and system apps. `WRITE_MEDIA_STORAGE` correctly shows unknown on a user app (requested, no state exposed).

## 5. A4 design

- Audit reads the `Packages:` block of `dumpsys package <pkg>` with `sed`, so section headers survive and output stays small; a header-preserving `grep` is the fallback.
- Sections parsed: `requested permissions:`, `install permissions:`, and `runtime permissions:` under `User 0:` only. Any non-matching line ends the current section, so other users and unknown ROM output never create state.
- A permission is changeable only if it is runtime, has a known state and carries no `SYSTEM_FIXED` / `POLICY_FIXED` flag.
- Grant/Revoke: repository refuses invalid names, protected apps, missing inventory, unknown state, non-changeable permissions and no-op changes. Otherwise runs `pm grant|revoke --user 0`, re-reads the audit, and reports APPLIED / NOT_APPLIED / UNVERIFIABLE with before/after state.
- Permission changes are not part of package snapshots; the result card records the previous state for manual reversal.

## 6. Installer scope decision

The planned installer supports **APK, APKS and XAPK** only. APKM is explicitly out of scope and must not be added to the implementation or release claims.

## 7. Problems found and fixes

- Stale A2 test and a later clarified rule that system apps keep Suspend: tests aligned with the requested contract.
- Wrong `SelectionContainer` import: fixed.
- Invalid `emptyList().let` in InventoryRepository: replaced with explicit typed construction.
- MediaStore duplicate files: fixed with `moveToFirst` + do/while; one canonical file.
- Stale cached state after reinstall: details update repository state before actions rebuild.
- App-private storage is wiped on uninstall: public storage plus Import/Export is the recovery path.
- Repeated identical snapshots: deduplicated.
- Protected package batch checkbox: replaced by an empty slot.
- Debloat disclaimer and search field took too much space: compact disclaimer card with full text in a dialog, fixed-height single-line search.
- CI run #46 red: A4 UI code and its strings were pushed in two commits; #47 fixed it. Rule added: code and resources in one commit.
- First A4 audit relied on grep dropping the `runtime permissions:` header, so runtime lines were read as install-time by accident. Replaced by section-aware parsing.
- Some OEM system packages cannot be removed under Shizuku shell on the reference ROM: reported as failed/unsupported. Root and Dhizuku remain out of scope.

## 8. Remaining work, in order

1. Finish A4: phone test of Grant/Revoke, then AppOps special-access audit and guarded set/reset.
2. A5 boot receivers, component control, background AppOps.
3. A6 notification listener, DND access, per-app notification mute.
4. A7 Chain3 per-app network block and netpolicy background data.
5. A8 installer for APK, APKS and XAPK, OBB placement, extraction, cache trimming, safe shared-storage cleanup. APKM stays out of scope.
6. 1.0: rewrite README (still describes the legacy WebUI) including the Debloat disclaimer, About, icon, fastlane, release notes, signed release from CI secrets, final smoke test, merge to `main`.

## 9. Commit trail

- `3ad1a13` through `a9d19f0`: native foundation, A1-A3, Debloat, cross-manager restore, Debloat UI fixes.
- `c86a46c`, `4a128f8`: A4 parser foundation; APKM removed from scope.
- `2d66045`, `1196863`: read-only permission audit in details (+ strings fix).
- current push: runtime section parsing with flags, guarded Grant/Revoke with read-back, tests, docs.
