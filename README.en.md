# Simple Notes Sync 📝

> Minimalist offline notes with auto-sync to your own server

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![Material Design 3](https://img.shields.io/badge/Material-Design%203-green.svg)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/dev.dettmer.simplenotes)

**📱 [APK Download](https://github.com/inventory69/simple-notes-sync/releases/latest)** · **📖 [Documentation](docs/DOCS.en.md)** · **🚀 [Quick Start](QUICKSTART.en.md)**

**🌍 Languages:** [Deutsch](README.md) · **English**

---

## 📱 Screenshots

<p align="center">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/1.jpg" width="250" alt="Notes list">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/2.jpg" width="250" alt="Edit note">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/3.jpg" width="250" alt="Settings">
</p>

---

## ✨ Highlights

- ✅ **NEW: Checklists** - Tap-to-check, drag & drop, swipe-to-delete
- 📝 **Offline-first** - Works without internet
- 🔄 **Auto-sync** - On WiFi connection (15/30/60 min)
- 🔒 **Self-hosted** - Your data stays with you (WebDAV)
- 💾 **Local backup** - Export/Import as JSON file
- 🖥️ **Desktop integration** - Markdown export for Obsidian, VS Code, Typora
- 🔋 **Battery-friendly** - ~0.2-0.8% per day
- 🎨 **Material Design 3** - Dark mode & dynamic colors

➡️ **Complete feature list:** [FEATURES.en.md](docs/FEATURES.en.md)

---

## 🚀 Quick Start

### 1. Server Setup (5 minutes)

```bash
git clone https://github.com/inventory69/simple-notes-sync.git
cd simple-notes-sync/server
cp .env.example .env
# Set password in .env
docker compose up -d
```

➡️ **Details:** [Server Setup Guide](server/README.en.md)

### 2. App Installation (2 minutes)

1. [Download APK](https://github.com/inventory69/simple-notes-sync/releases/latest)
2. Install & open
3. ⚙️ Settings → Configure server:
   - **URL:** `http://YOUR-SERVER-IP:8080/` _(base URL only!)_
   - **User:** `noteuser`
   - **Password:** _(from .env)_
   - **WiFi:** _(your network name)_
4. **Test connection** → Enable auto-sync
5. Done! 🎉

➡️ **Detailed guide:** [QUICKSTART.en.md](QUICKSTART.en.md)

---

## 📚 Documentation

| Document | Content |
|----------|---------|
| **[QUICKSTART.en.md](QUICKSTART.en.md)** | Step-by-step installation |
| **[FEATURES.en.md](docs/FEATURES.en.md)** | Complete feature list |
| **[BACKUP.en.md](docs/BACKUP.en.md)** | Backup & restore guide |
| **[DESKTOP.en.md](docs/DESKTOP.en.md)** | Desktop integration (Markdown) |
---

## 🛠️ Development

```bash
cd android
./gradlew assembleStandardRelease
```

➡️ **Build guide:** [DOCS.en.md](docs/DOCS.en.md#-build--deployment)

---

## 🤝 Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 📄 License

MIT License - see [LICENSE](LICENSE)

---

**v1.4.0** · Built with ❤️ using Kotlin + Material Design 3
