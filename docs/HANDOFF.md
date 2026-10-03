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

No INTERNET permission. Commands only through Shizuku UserService and ExecBridge. Package and permission names validated and quoted. Every write read back; APPLIED only when read-back matches. Protected packages refused in the repository, not only hidden in the UI. Snapshots hold package state only. Bulk work is sequential with per-package results. Code and the strings/resources it uses land in one commit. CI proves build and tests; the reference phone proves behaviour. Diagnose CI failures from the actual failing test name and line in the log, never from assumption. Shell output is capped at 64 KiB by ExecBridge: any new shell filter must be measured on the phone (`| wc -c` on a large app such as Drive) before it is relied on; an unmeasured extra read must be non-fatal. A write control is shown only where a phone test showed Android accepts it.

## 3. Status

| Step | Status | Phone evidence |
|---|---|---|
| M0 core | done | READY, uid 2000 |
| A1 inventory + guard + details | done | 835 total, 468 user, 367 system, 35 protected; counts match `pm` |
| A2 single-package operations | done | Suspend/Unsuspend user and system apps; protected apps show no actions. Suspend and Remove-for-user re-tested 2026-10-04 |
| A3 snapshots, undo, pins, batch | done | phone-verified |
| Debloat track | done | CI green and owner phone acceptance |
| A4 permissions + AppOps | **almost done** | Permissions, shared uid, AppOps audit and scope split phone-verified. AppOps change works in package scope for ops without a uid mode. Uid scope disabled after phone evidence. Latest push awaiting CI and a short phone check; then Self-check |
| A5 to A8 | open | |
| 1.0 release | open | |

## 4. Acceptance record (owner-confirmed on the phone)

- Snapshot list, detail, delete, refresh. Restore/Undo with safety snapshot and per-package read-back. JSON Import/Export. Real state after reinstall. Pins. Batch with one snapshot and sequential read-back. Protected packages cannot join a batch.
- Debloat: SAFE-only presets, compact disclaimer, search, Suspend and Remove-for-user, snapshots, read-back, cross-manager Restore.
- A4 audit: granted / not granted / unknown on user and system apps.
- A4 Grant/Revoke: `POST_NOTIFICATIONS` round trip APPLIED. Drive, Meet, Play Store: camera and location revoked in VOID, app asked again, granted in VOID, took effect in the app (`9055b5a`).
- A2 re-test (2026-10-04): Suspend and Remove-for-user on a system and a user app.
- AppOps card, OEM ops (`miuiop(...) · OEM · read-only`), duplicate modes, and uid/package scope split on Drive (uid 10176), securitycenter (uid 1000) and Acode (uid 14411).
- Shared uid (`0d4cd24`): securitycenter shows `Shared system uid android.uid.system (1000)`, dangerous permissions as `granted · runtime · fixed`, no Grant/Revoke. Shared users block 37361 bytes.
- AppOps change (`dc3ec81`), package scope on Acode: `run_any_in_background` allow to ignore APPLIED, `read_clipboard` allow to ignore APPLIED.

## 5. Current checkpoint and remaining path

AppOps change findings (phone, `dc3ec81` and `090c376`):

- Package scope works for ops that have no uid mode (Acode `run_any_in_background`, `read_clipboard`).
- Package scope for an op with a uid mode was silently kept. Probe on Acode: `appops set com.foxdebug.acode ACCEPT_HANDOVER ignore` (exit 0, no output), then `appops get com.foxdebug.acode ACCEPT_HANDOVER` printed `Uid mode: ACCEPT_HANDOVER: ignore` and `ACCEPT_HANDOVER: allow`; `od -c` showed plain text. On Drive the package mode went default to allow, then ignore and default were both kept at allow. AOSP evaluates the uid mode first, so the stuck package `allow` has no effect while the uid mode is `ignore`.
- Uid scope: `appops set 14411 CAMERA allow` and `appops set 14411 CALL_PHONE allow` on Acode returned exit 0 and kept `ignore`. Uid modes of permission-backed ops follow the runtime permission state; they are changed with Grant/Revoke, not appops.
- securitycenter: `CAPTURE_CONSENTLESS_BUGREPORT_ON_USERDEBUG_BUILD` package change NOT applied (privileged op).

Latest push:

- Uid scope refused in the repository and shown disabled in the dialog with the reason.
- Change appears only for standard ops whose package scope is not overridden by a uid mode.
- Result card says when Android returned success but kept the previous mode.

Short phone check (owner): on Acode, `accept_handover` and `camera` have no Change button; `run_any_in_background` and `read_clipboard` still change both ways.

Next:

1. Read-only Self-check over all packages (permission audit, AppOps parse and split, size-cap hits, unknown lines).
2. Close A4 and move to A5: boot receivers, component control, and background AppOps.
3. Continue A6 notifications/DND, A7 network controls, A8 APK/APKS/XAPK installer, then 1.0 release work.

## 6. Design and safety decisions

- AppOps is not the same thing as a runtime permission. Both are shown as separate sections.
- AppOps audit keeps the raw output of every command visible so ROM-specific formats are checked against reality, not guessed.
- AppOps scopes: `appops get <uid>` is the uid scope; the rest of `appops get <package>` after that exact prefix is the package scope. If the prefix does not match line by line, the merged view is shown and changes are disabled.
- AppOps changes: package scope only, one op at a time, confirmation, read-back. Not offered while a non-default uid mode is set (it takes precedence, and the ROM kept such changes). Uid scope is disabled because every uid change on this ROM was silently kept; uid modes follow runtime permissions.
- OEM AppOps (`MIUIOP(n)`) are shown but never changeable.
- Permission audit reads only the active `Packages:` block; shared-uid packages also read `Shared users:`. Grant/Revoke is refused for shared system uids.
- Owner decision (2026-10-04): `com.miui.securitycenter` stays unprotected.
- Protected packages remain read-only. No change is reported APPLIED without read-back.
- APKM is out of scope; installer support is APK, APKS and XAPK only. Root and Dhizuku are out of scope.

## 7. Problems found and fixes

- CI #46 red: UI and strings split across commits; fixed in #47.
- First A4 audit grep dropped the runtime header; replaced by section-aware parsing.
- Runtime audit showed stale state; fixed with authoritative `pm check-permission`.
- AppOps parser CI #53 to #55: anchoring regressions alternated between two tests; fixed in `e587840` (CI #56).
- Disable dialog showed the Suspend warning; fixed.
- Large apps exceed 64 KiB (Drive 128886 bytes). `c08a508` grep filter pushed unmeasured and still truncated; `9055b5a` reads the `Packages:` block, measured first.
- Shared-uid permissions showed `unknown`; fixed in `0d4cd24`.
- OEM ops dropped; now read-only records. Duplicate op lines now split by scope.
- AppOps change offered where Android silently ignores it (`dc3ec81`); restricted in `090c376` and this push.
- Android may stop the target process during permission changes; UI warns.
- Some OEM system packages cannot be removed under Shizuku shell; reported unsupported.

## 8. Remaining work, in order

1. Confirm the latest push is green and do the short Acode check (section 5).
2. Read-only Self-check over all packages.
3. A5 boot receivers, component control, background AppOps.
4. A6 notification listener, DND access, per-app notification mute.
5. A7 Chain3 per-app network block and netpolicy background data.
6. A8 installer for APK, APKS and XAPK. APKM stays out of scope.
7. 1.0: README, About, icon, fastlane, release notes, signed release, final smoke test, merge to `main`.

## 9. Commit trail

- `3ad1a13` through `a9d19f0`: foundation, A1-A3, Debloat.
- `c86a46c` to `49a285e`: A4 permissions foundation, audit, Grant/Revoke, read-back.
- `a85692c`, `822fac8`, `cb24d9e`: AppOps parser foundation, CI #53 to #55 red.
- `e587840`: anchored AppOps parser (CI #56 green).
- `8a6ec66`: AppOps audit card.
- `c08a508`: OEM ops, duplicate modes.
- `9055b5a`: permission audit from the `Packages:` block.
- `3964931`: AppOps uid/package scope split.
- `0d4cd24`: shared-uid permissions.
- `dc3ec81`: guarded AppOps change.
- `090c376`: package scope blocked while a uid mode is set.
- current push: uid scope disabled, Change shown only where it works.
