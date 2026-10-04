# VOID // APPS: complete project handoff

Updated: 2026-10-04
Branch: `native-app-v0`
Application ID: `io.github.kreza6173pixel.voidapps`
Reference device: Xiaomi Redmi Note 14, HyperOS Global ROM, Android 16, SDK 36, Shizuku shell uid 2000.

## Status

A4 permissions and AppOps is done on the reference phone. System Self-check passed 311/311 with no permission errors, AppOps errors, unsplit output or unknown lines; the HyperOS `MIUIOP(10017): ask` mode is parsed read-only for user apps.

A5 has started with a read-only parser and probe foundation. It follows the proven `void-autostart` module: boot-family receiver discovery from `dumpsys package`, plus `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` state. No A5 write control is exposed yet. Writes will require a device probe and fresh read-back, because this ROM previously returned exit 0 while silently keeping AppOps modes.

## A5 source evidence

- Boot receiver source commands: `dumpsys package <pkg>`, then `pm disable <pkg>/<component>` or `pm enable <pkg>/<component>`.
- Background source commands: `cmd appops get <pkg> RUN_IN_BACKGROUND`, `cmd appops get <pkg> RUN_ANY_IN_BACKGROUND`, then `cmd appops set`.
- The old module's broad background denial can affect notifications and all background execution, so receiver-level control stays the preferred surgical option.

## Remaining

1. Run CI for the A5 parser foundation.
2. Add the read-only A5 UI and phone probe.
3. Add guarded component/background writes only where read-back proves they work.
4. A6 notifications and DND, A7 Chain3/netpolicy, A8 APK/APKS/XAPK installer, then 1.0 release.

No INTERNET permission, protected-package bypass, or APKM support is being added.
