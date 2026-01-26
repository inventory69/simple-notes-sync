<div align="center">

# Simple Notes Sync

**Minimalist offline notes with auto-sync to your own server**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
[![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=for-the-badge&logo=material-design&logoColor=white)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-F5C400?style=for-the-badge)](LICENSE)

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="60">](https://apt.izzysoft.de/fdroid/index/apk/dev.dettmer.simplenotes)
[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60">](http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/inventory69/simple-notes-sync)
[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="60">](https://f-droid.org/packages/dev.dettmer.simplenotes/)



[📱 APK Download](https://github.com/inventory69/simple-notes-sync/releases/latest) · [📖 Documentation](docs/DOCS.md) · [🚀 Quick Start](QUICKSTART.md)

**🌍** [Deutsch](README.de.md) · **English**

</div>

---

## 📱 Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="250" alt="Sync status">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="250" alt="Edit note">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="250" alt="Edit checklist">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="250" alt="Settings">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="250" alt="Server settings">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="250" alt="Sync settings">
</p>

---

<div align="center">

📝 Offline-first &nbsp;•&nbsp; 🔄 Smart Sync &nbsp;•&nbsp; 🔒 Self-hosted &nbsp;•&nbsp; 🔋 Battery-friendly

</div>

---

## ✨ Highlights

- ✅ **NEW: Checklists** - Tap-to-check, drag & drop
- 🌍 **NEW: Multilingual** - English/German with language selector
- 📝 **Offline-first** - Works without internet
- 🔄 **Configurable sync triggers** - onSave, onResume, WiFi-connect, periodic (15/30/60 min), boot
- 🔒 **Self-hosted** - Your data stays with you (WebDAV)
- 💾 **Local backup** - Export/Import as JSON file
- 🖥️ **Desktop integration** - Markdown export for Obsidian, VS Code, Typora
- 🔋 **Battery-friendly** - ~0.2% with defaults, up to ~1.0% with periodic sync
- 🎨 **Material Design 3** - Dark mode & dynamic colors

➡️ **Complete feature list:** [FEATURES.md](docs/FEATURES.md)

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

➡️ **Details:** [Server Setup Guide](server/README.md)

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

➡️ **Detailed guide:** [QUICKSTART.md](QUICKSTART.md)

---

## 🔐 APK Verification

All official releases are signed with the same certificate. 

**Recommended:** Verify with [AppVerifier](https://github.com/nicholson-lab/AppVerifier) (Android app)

**Expected SHA-256:**
```
42:A1:C6:13:BB:C6:73:04:5A:F3:DC:81:91:BF:9C:B6:45:6E:E4:4C:7D:CE:40:C7:CF:B5:66:FA:CB:69:F1:6A
```

---

## 📚 Documentation

| Document | Content |
|----------|---------|
| **[QUICKSTART.md](QUICKSTART.md)** | Step-by-step installation |
| **[FEATURES.md](docs/FEATURES.md)** | Complete feature list |
| **[BACKUP.md](docs/BACKUP.md)** | Backup & restore guide |
| **[DESKTOP.md](docs/DESKTOP.md)** | Desktop integration (Markdown) |
| **[SELF_SIGNED_SSL.md](docs/SELF_SIGNED_SSL.md)** | Self-signed SSL certificate setup |
| **[DOCS.md](docs/DOCS.md)** | Technical details & troubleshooting |
| **[CHANGELOG.md](CHANGELOG.md)** | Version history |
| **[UPCOMING.md](docs/UPCOMING.md)** | Upcoming features 🚀 |
| **[TRANSLATING.md](docs/TRANSLATING.md)** | Translation guide 🌍 |

```bash
cd android
./gradlew assembleStandardRelease
```

➡️ **Build guide:** [DOCS.md](docs/DOCS.md#-build--deployment)

---

## 🤝 Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 📄 License

MIT License - see [LICENSE](LICENSE)

---

<div align="center">

**v1.7.0** · Built with ❤️ using Kotlin + Jetpack Compose + Material Design 3

</div>
