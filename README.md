<div align="center">

# ⚡ CYBER APP MANAGER
### *The most powerful cyberpunk-themed app manager for Shevery ADB Modules*

<img width="1080" height="2400" alt="Image" src="https://github.com/user-attachments/assets/dd6ddb8c-7b06-4c89-8aac-71d328f083e7" />

[![Shevery](https://img.shields.io/badge/Shevery-ADB%20Module-00d4ff?style=flat-square&logo=android)](...)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android)](...)
[![License](https://img.shields.io/badge/License-MIT-ff3366?style=flat-square)](LICENSE)
[![Version](https://img.shields.io/badge/Version-v1.1.2-ff00aa?style=flat-square)]()

**🛡️ Freeze · 🔓 Unfreeze · 🗑️ Uninstall · ⛔ Force-Stop · 📊 Batch Operations**

</div>

## 🚀 What Makes This Different?

Most app managers give you a boring list and a single toggle. 
Cyber App Manager gives you a full neon HUD — glassmorphism cards, 
real-time stat counters, scanline overlays, and batch operations 
that feel like you're hacking the mainframe.

> "Finally, an app manager that looks as powerful as it actually is."

## ✨ Features
🧊 One-Tap Freeze | 🔓 One-Tap Unfreeze | 🗑️ Uninstall | ⛔ Force-Stop
📦 Batch Operations | 🔍 Live Search | 🏷️ Smart Tags | 🛡️ Safety Guard
📊 Live Stats Dashboard | 🖥️ Built-in Console | 🎨 Neon HUD UI
💾 Snapshot Engine | 🔁 Auto-Enforce | 🔒 100% Offline

## ⚡ Installation
1. Download cyber-app-manager-v1.1.2.zip from Releases
2. Shevery → ADB Modules → Install from storage
3. Enable module → Full Access → WebUI 🚀

## 🛡️ Safety Architecture
- Protected Packages auto-refused
- Explicit Confirmation on system apps
- Visible Console — every command logged
- Zero Network — no telemetry


---

# package on their device.

> *"Finally, an app manager that looks as powerful as it actually is."*

---

## 🏗️ Architecture

```
cyber-app-manager/
├── 📄 module.prop          # Module manifest + usesShellBridge=true
├── 📜 lib.sh               # Shared functions + protected package list
├── 📜 appctl.sh            # Package operations (freeze/unfreeze/uninstall/force-stop)
├── 📜 snapshot.sh          # Save / list / restore package states
├── 📜 service.sh           # Auto-applies enforce.list on every boot/session
├── 📜 action.sh            # Quick text summary (tap module card → Action)
├── 🌐 webui/
│   ├── index.html          # Neon HUD interface
│   ├── style.css           # Cyberpunk glassmorphism theme
│   └── script.js           # window.Shizuku bridge + batch ops engine
├── 📖 README.md
└── 📜 LICENSE
```

### 🔌 WebUI Shell Bridge

The WebUI communicates directly with the device via `window.Shizuku.exec()`, the official Shevery ADB Module API. Every command returns structured JSON:

```json
{
  "ok": true,
  "exitCode": 0,
  "stdout": "package:com.example.app\n...",
  "stderr": "",
  "timedOut": false
}
```

No external scripts needed for reading — `pm list packages` runs natively through the bridge.

---

## ⚡ Installation

### Method 1: Install from ZIP (Recommended)

1. Download **`cyber-app-manager-v1.1.2.zip`** from [Releases](../../releases)
2. Open **Shevery** → **ADB Modules** → **Install from storage**
3. Select the ZIP file
4. Enable the module and set access mode to **Full** *(or Custom + enable WebUI Shell Bridge)*
5. Tap the module card → **WebUI** 🚀

### Method 2: Install from Source

```bash
git clone https://github.com/kreza6173-pixel/cyber-app-manager.git
cd cyber-app-manager
# Copy the folder to /sdcard and install via Shevery → ADB Modules → Install from source
```

---

## 🛡️ Safety Architecture

Cyber App Manager is designed to be **powerful but safe**:

- ✅ **Protected Packages** — `android`, `com.android.systemui`, `com.android.settings`, `com.google.android.gms`, current launcher, and current IME are **automatically refused** from destructive operations
- ✅ **Explicit Confirmation** — Every batch action on system packages shows a confirmation modal
- ✅ **Visible Console** — Every shell command is logged in the built-in console drawer. Nothing happens off-screen.
- ✅ **Dry-Run Fallback** — `appctl.sh` validates before executing; direct `pm` commands are only used as fallback
- ✅ **Zero Network** — No internet permission, no telemetry, no remote assets

---

## 🎮 Usage Guide

### Basic Operations

| Action | How To |
|--------|--------|
| 🔍 Search | Type in the filter box — results update live |
| 🏷️ Filter | Tap ALL / USER / SYSTEM / FROZEN tabs |
| ☑️ Select | Tap any package row or its checkbox |
| 🧊 Freeze | Select packages → tap **FREEZE** |
| 🔓 Unfreeze | Select frozen packages → tap **UNFREEZE** |
| ⛔ Force-Stop | Select running packages → tap **FORCE-STOP** |
| 🗑️ Uninstall | Select user packages → tap **UNINSTALL** |

### Pro Tips

- **Select Page** — Selects all visible packages in the current filter
- **Clear** — Deselects everything
- **Console** — Tap the bottom bar to expand the shell log and see every command executed
- **Snapshots** — Use `snapshot.sh save` and `snapshot.sh restore` to manage package states across ROM flashes

---

## ⚙️ Requirements

| Requirement | Details |
|-------------|---------|
| 📱 Android | 8.0+ (API 26) |
| 🔧 Shevery | Latest version with ADB Modules enabled |
| 🔑 Privileges | Shizuku started (via ADB or Root) |
| 🌉 Bridge | WebUI Shell Bridge enabled in module settings |

---

## 🛠️ Building from Source

No build step required — this is a **zero-dependency** module. Just ZIP the folder:

```bash
# Linux / macOS
zip -r cyber-app-manager-v1.1.2.zip module.prop *.sh webui/ README.md LICENSE

# Or simply install the folder directly via Shevery's "Install from source"
```

---

## 🤝 Contributing

Found a bug? Got an idea? PRs are welcome!

1. Fork the repository
2. Create your feature branch (`git checkout -b feat/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feat/amazing-feature`)
5. Open a Pull Request

---

## 📜 License

```
MIT License

Copyright (c) 2026 kreza6173-pixel

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

<div align="center">

### 💚 Built with passion for the Android power-user community

**[⬇ Download Latest Release](../../releases)** · **[🐛 Report Bug](../../issues)** · **[⭐ Star This Repo](../../stargazers)**

</div>
