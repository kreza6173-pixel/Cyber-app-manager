# Changelog

## 1.2.0 — initial public release

- Package registry: search, filter (all/user/system/frozen/removed/running/selected), sort (name/risk/frozen-first/data use), batch freeze/unfreeze/force-stop/remove/restore/pin
- Knowledge base of common Android/Google/Xiaomi/Samsung/OEM packages with friendly names, categories and risk tags, plus heuristics (overlay/vendor/AOSP detection) for anything unlisted
- Debloat presets built from the knowledge base, reviewable before freezing
- Snapshots with diff/restore/export/import, auto-snapshot before destructive batches, and undo for the last 10 batches
- Pinned-frozen list re-applied automatically by `service.sh` every Shizuku session, and from the WebUI or `snapshot.sh`
- Per-app detail sheet: version, installer, dates, runtime permissions, background-activity restriction, standby bucket
- On-device data-usage aggregation (since boot / 24 h / all history / since last charge), split into mobile, Wi-Fi and VPN-tunnel traffic
- Optional bring-your-own-key AI advisor (OpenAI, Anthropic Claude, Google Gemini, DeepSeek, Mistral, xAI, or any OpenAI-compatible/local endpoint), with local validation, retry on temporary provider overload, and friendly error messages
- Dynamic protection of the current launcher, keyboard, default dialer and default SMS app, on top of a static core/overlay list
- Themed pickers throughout (no native `<select>`/`<datalist>` popups), fixed-pixel menu heights, a build-consistency check and an on-screen script-error bar
