# VOID // APPS: plan

Updated 2026-10-03. One native app (Kotlin + Compose + Shizuku UserService) that replaces
seven Shevery WebUI modules. Built on a copy of the PULSE // BATTERY M0-M2 core, which is
already phone-proven. Read `docs/HANDOFF.md` for the rules.

Application id `io.github.kreza6173pixel.voidapps`. Working branch `native-app-v0` until 1.0,
then merged into `main` (same flow as PULSE // BATTERY).

## 1. What comes from where

| Old repo | Taken into VOID // APPS | Dropped, and why |
|---|---|---|
| Cyber-app-manager (this repo) | package registry, freeze/unfreeze, force-stop, remove for user + restore, clear data, snapshots/undo/pins, debloat presets, knowledge base, per-app details | AI advisor (needs INTERNET), netstats usage (format differs per ROM; maybe later) |
| void-autostart | boot receiver scan + component disable, RUN_IN_BACKGROUND / RUN_ANY_IN_BACKGROUND | nothing |
| privacy-audit | runtime permission audit + revoke/grant, special access read from AppOps (not from dumpsys) | substring standby parsing |
| void-pulse | notification listener access, DND access, per-app notification mute | EQ (never processed audio), mixer, light show, AI |
| pulse-install | session install `pm install-create/-write/-commit` for APK/APKS/XAPK/APKM, OBB placement, APK extract | VirusTotal + AI (INTERNET), boot auto-install |
| void-purge | `pm trim-caches`, empty-folder / log / duplicate scans in shared storage (only if the probe shows shell can read them), force-stop of user apps done correctly | root corpse finder, root cache wipe, ANR/tombstone wipe (root, untestable) |
| VOID-WALL | per-app network block via Chain3, background data via netpolicy | iptables/ip6tables/tc (root), DNS filter (never filtered), "dark web" monitor, airplane panic |

## 2. Non-negotiable product rules

1. No INTERNET permission. No analytics, no AI, no network lookups.
2. One protected-package guard for the whole app: static core list + dynamic launcher,
   keyboard, dialer, SMS, WebView provider, Shizuku manager and this app. No action
   anywhere bypasses it.
3. Every write is read back. "Applied" only if the read-back matches; otherwise show the
   real state and the raw output.
4. Every destructive batch takes a snapshot first and is undoable where Android allows.
5. Bulk operations run one package at a time with a per-package result, never as a
   fire-and-forget background loop, and never touch Shizuku or this app (that is why the
   void-purge kill button never worked: it force-stopped the bridge).
6. Nothing changes device state just because a screen was opened (VOID-WALL enabled
   Chain3 on launch; never again).
7. Root-only features are not built until a rooted tester exists.

## 3. Milestones (each: probe on the phone, then code, then phone test)

| # | Milestone | Acceptance test on the phone |
|---|---|---|
| M0 | Core copied from PULSE, renamed, CI active | READY with uid 2000; console `id` gives `uid=2000(shell)` |
| A1 | Package inventory + guard + app details | full list with user/system/disabled/removed counts matching `pm list packages` flags; launcher, keyboard, dialer, SMS shown as protected |
| A2 | Freeze/unfreeze, force-stop, remove for user, restore, clear data | each action read back; a protected app refuses with a reason |
| A3 | Snapshots, undo, pins, debloat presets | freeze 3 apps in one batch, undo restores all 3 |
| A4 | Permissions + special access (Privacy) | revoke CAMERA from one app, read back, grant back |
| A5 | Background: boot receivers + background AppOps (Autostart) | disable one receiver and one AppOp, both read back, revert |
| A6 | Notifications: listener/DND access, per-app mute | list matches Android Settings; mute one app, read back |
| A7 | Network: Chain3 per-app block, background data | block one app, its traffic stops, unblock restores; "unblock all" works |
| A8 | Install + extract + cleaner | install an .apks from Download; trim caches; one shared-storage scan |
| 1.0 | icon, About, fastlane, signed release | signed APK installed and smoke-tested |

After 1.0 the seven old module repos get a README pointer to VOID // APPS and are archived.
