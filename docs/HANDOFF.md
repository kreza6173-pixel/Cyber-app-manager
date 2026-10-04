# VOID // APPS: complete project handoff

Updated: 2026-10-04
Branch: `native-app-v0`
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi Redmi Note 14, HyperOS Global ROM, Android 16, SDK 36, Shizuku shell uid 2000.

This is the durable record of product decisions, engineering rules, phone evidence and remaining work.

## Product idea

VOID // APPS is a local Kotlin + Jetpack Compose Android package manager using Shizuku UserService/AIDL. It has no INTERNET permission, refuses protected packages, performs reversible operations and reads state back before reporting success.

## Engineering rules

No INTERNET permission. Commands only through Shizuku and ExecBridge. Validate and quote package and permission names. Every write is read back; APPLIED means the new state matches. Protected packages are refused in the repository. Code and resources land together. Diagnose CI failures from logs. ExecBridge output is capped at 64 KiB, so shell filters are measured on the phone before being relied on and extra reads are non-fatal. A write control is shown only after a phone probe proves it works.

## Status

| Step | Status | Evidence |
|---|---|---|
| M0 core | done | Shizuku READY, uid 2000 |
| A1 inventory + guard + details | done | Counts match pm on reference phone |
| A2 package operations | done | Suspend, remove, restore and read-back phone-verified |
| A3 snapshots, undo, pins, batch | done | Phone-verified |
| Debloat | done | CI green and phone acceptance |
| A4 permissions + AppOps | done on reference phone | Self-check system 311/311 clean; user 463/463; HyperOS ask parsed read-only |
| A5 to A8 | open | A5 parser foundation pushed; CI fix pending verification |
| 1.0 release | open | |

## Acceptance record

- Permission Grant/Revoke round trips were verified on Drive, Meet, Play Store and Acode.
- Shared system uid `android.uid.system/1000` is read-only and refuses writes.
- AppOps uid/package scope split was verified on Drive, securitycenter and Acode.
- Package-scope AppOps changes work only when no non-default uid mode overrides them. Uid-scope changes are disabled after the ROM silently kept CAMERA and CALL_PHONE changes.
- `MIUIOP(n)` is read-only. HyperOS `MIUIOP(10017): ask` is now a recognised read-only mode.
- Self-check system group: 311/311, no permission errors, cap hits, AppOps errors, unsplit output or unknown lines.

## Current checkpoint

A4 is closed on the reference phone. A5 has a read-only parser and probe foundation in commit `0615a610`. The duplicate test path was removed in `92b31b0`, and the handoff history was restored in `2a3bd59`.

A5 follows the proven `kreza6173-pixel/void-autostart` module:

- Boot receiver discovery from `dumpsys package`, looking for `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `QUICKBOOT_POWERON` and `MY_PACKAGE_REPLACED`.
- Background state uses `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` AppOps.
- The parser preserves raw output and reports unknown ROM shapes rather than guessing.
- The first A5 CI runs (#72, #73 and #74) all failed at the same unit test, `AutostartAuditTest.findsBootReceiverAndBackgroundOps`, line 17.
- Root cause: the parser accepted fully qualified receiver classes but rejected Android's common shorthand form `package/.Receiver` because the class pattern required a letter immediately after `/`.
- Commit `fd067e8` fixes the pattern to accept shorthand classes and adds a fully qualified receiver test. No A5 write control is exposed yet.

The old source commands are `pm disable <package>/<component>`, `pm enable <package>/<component>` and `cmd appops set`, but they are not trusted by VOID until a phone probe proves the read-back.

## Remaining work

1. Confirm CI green for `fd067e8`.
2. Add the read-only A5 UI and phone probe.
3. Add guarded component/background writes only where read-back proves them.
4. A6 notification mute, notification-listener access and DND access, based on `void-pulse`.
5. A7 Chain3/netpolicy based on `VOID-WALL`.
6. A8 streamed APK/APKS/XAPK installer based on `pulse-install`; APKM stays out of scope.
7. 1.0 README, About, icon, fastlane, signed release, smoke test and merge to `main`.

## Safety decisions

AppOps is separate from runtime permissions. OEM operations are shown but never changed. Self-check never writes. ROM-specific differences are expected and supported through issue reports. `com.miui.securitycenter` stays unprotected, but system-uid writes are refused. Root, Dhizuku and APKM are out of scope.

## Commit trail

- `e587840`: anchored AppOps parser, CI #56 green.
- `9055b5a`: measured permission audit sections.
- `0d4cd24`: shared-uid permissions.
- `dc3ec81`, `090c376`, `873a114`: guarded AppOps changes and phone findings.
- `ba860ea`: Self-check, ROM report template and system-uid refusal.
- `d8f885a`: HyperOS `ask` mode read-only.
- `54b9703`: permission-section extractor; system Self-check 311/311 clean.
- `077746ab`: A5/A6 source-module evidence.
- `0615a610`: A5 parser and read-only repository foundation.
- `92b31b0`: duplicate test path cleanup.
- `2a3bd59`: handoff history restored.
- `fd067e8`: A5 receiver shorthand parser fix and regression test.
