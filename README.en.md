# Simple Notes Sync 📝

> Minimalist offline notes with auto-sync to your own server

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![Material Design 3](https://img.shields.io/badge/Material-Design%203-green.svg)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**📱 [APK Download](https://github.com/inventory69/simple-notes-sync/releases/latest)** · **📖 [Documentation](DOCS.en.md)** · **🚀 [Quick Start](QUICKSTART.en.md)**

**🌍 Languages:** [Deutsch](README.md) · **English**

---

## 📱 Screenshots

<p align="center">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/1.jpg" width="250" alt="Notes list">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/2.jpg" width="250" alt="Edit note">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/3.jpg" width="250" alt="Settings">
</p>

---

## Features

### 📝 Notes
* Simple text notes with auto-save
* Swipe-to-delete with confirmation
* Material Design 3 editor

### 🔄 Synchronization
* **Pull-to-refresh** for manual sync
* **Auto-sync** (15/30/60 min) only on home WiFi
* **Smart server check** - No errors on foreign networks
* **Conflict-free merging** - Your changes are never lost

### 🔒 Privacy & Self-Hosted
* **WebDAV server** (Nextcloud, ownCloud, etc.)
* **Docker setup guide** included in docs
* **Your data stays with you** - No tracking, no cloud
* **HTTP only local** - HTTPS for external servers
* **100% open source** (MIT License)

### 🔋 Performance
* **Battery-friendly** (~0.2-0.8% per day)
* **Offline-first** - Works without internet
* **Dark mode** & dynamic colors

---

## 🚀 Quick Start

### 1. Server Setup

```bash
cd server
cp .env.example .env
# Set password in .env
docker compose up -d
```

➡️ **Details:** [Server Setup Guide](server/README.en.md)

### 2. App Installation

1. [Download APK](https://github.com/inventory69/simple-notes-sync/releases/latest)
2. Install & open
3. ⚙️ Settings → Configure server
4. Enable auto-sync

➡️ **Details:** [Complete guide](QUICKSTART.en.md)

---

## 📚 Documentation

- **[Quick Start Guide](QUICKSTART.en.md)** - Step-by-step guide for end users
- **[Server Setup](server/README.en.md)** - Configure WebDAV server
- **[Complete Docs](DOCS.en.md)** - Features, troubleshooting, build instructions

---

## 🛠️ Development

```bash
cd android
./gradlew assembleStandardRelease
```

➡️ **Details:** [Build instructions in DOCS.en.md](DOCS.en.md)

---

## 🤝 Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## 📄 License

MIT License - see [LICENSE](LICENSE)

**v1.1.2** · Built with Kotlin + Material Design 3
