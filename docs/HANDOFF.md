# VOID // APPS: handoff guide

> Read the whole file first. If anything here disagrees with the repo or a CI log, the repo
> and the log win. Updated 2026-10-03.

The core (Shizuku state machine, UserService, ExecBridge, console, CI, signing) is a copy of
PULSE // BATTERY, where every rule below was learned the hard way. The full background is in
`kreza6173-pixel/pulse-battery` `docs/HANDOFF.md`, `docs/DECISIONS.md`, `docs/LESSONS.md`.
Plan and milestones: `docs/PLAN.md`.

## 1. Status

| Step | Status | Evidence |
|---|---|---|
| M0 core copied, renamed | code done | not built yet: CI file still parked in `docs/ci.yml` |
| A1 inventory + guard | next | probe output from the phone needed first |

Reference device: Xiaomi, Android 16 (SDK 36), Shizuku as uid 2000.
Branch: `native-app-v0`. Application id: `io.github.kreza6173pixel.voidapps`.
Kotlin/AIDL package: `io.github.kreza6173pixel.cyberappmanager` (intentionally unchanged).
The repo root still holds the legacy WebUI module (`webui/`, `*.sh`, `module.prop`); it is the
reference for behaviour only and is removed at 1.0.

## 2. Architecture facts that must not change

1. The UserService is a binder: `ShizukuExecService : IUserService.Stub()`, no-arg constructor, not in the manifest.
2. `processNameSuffix("user_service")` is mandatory (Shizuku 13.1.5). One `UserServiceArgs` for bind, peek, unbind.
3. AIDL codes: `destroy() = 16777114`, `exec = 1`, `cancel = 2`. Bump `USER_SERVICE_VERSION` on any service change.
4. Commands only through `ExecBridge.execBlocking` on `Dispatchers.IO`. Output cap 64 KiB: filter on the device.
5. Parsers are pure Kotlin, unit-tested with real phone output.
6. Package names regex-checked and quoted with `ShellQuoting.quote`.
7. Every write is followed by a read-back.
8. No INTERNET permission, ever.
9. Pinned: AGP 8.13.1, Kotlin 2.2.21, Gradle 8.13, BOM 2025.12.00, SDK 36, Shizuku 13.1.5. Never SDK 37 or AGP 9.
10. Release signing only from CI Secrets; same secret names as PULSE.

## 3. Traps already paid for (in PULSE)

| Trap | Do this instead |
|---|---|
| star + slash inside a block comment (`*job*/...`) | put samples in test strings |
| `as? Generic` without type arguments | `is` check + smart cast |
| `CharArray` passed as `CharSequence` | `String(chunk, 0, n)` |
| `ExecutorService.submit { }` | `synchronized` |
| `Int.coerceIn(Long, Long)` | `toLong()` first |
| chaining `Intent.setPackage` | separate statement |
| `SmallTopAppBar` | `TopAppBar` + `@OptIn` |
| `SelectionContainer` in `LazyColumn` | Copy/Share buttons |
| `pm install /sdcard/...` | stream with `pm install-write -S <size> <session> <name> -` or stage in `/data/local/tmp` |
| `pm install-multiple` on device | adb-only; use sessions |
| bulk `am force-stop` | exclude `moe.shizuku.privileged.api` and this app, one by one, read back |
| workflow files via the API | owner pastes them (no `workflow` scope) |
| a green run | proves compile + tests only; the phone proves behaviour |

## 4. Per-push checklist

- [ ] Re-read the whole diff. One goal per push.
- [ ] Latest run green and `app-debug` artifact present. Red: first `e:` line only.
- [ ] New strings in `values/` (English). Escape apostrophes.
- [ ] After the owner confirms on the phone, update section 1.
