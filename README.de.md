<div align="center">

# Simple Notes Sync

**Minimalistische Offline-Notizen mit Auto-Sync zu deinem eigenen Server**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
[![Material 3](https://img.shields.io/badge/Material_3-6750A4?style=for-the-badge&logo=material-design&logoColor=white)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-F5C400?style=for-the-badge)](LICENSE)

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="60">](https://apt.izzysoft.de/fdroid/index/apk/dev.dettmer.simplenotes)
[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60">](http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/inventory69/simple-notes-sync)
[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="60">](https://f-droid.org/packages/dev.dettmer.simplenotes/)

[📱 APK Download](https://github.com/inventory69/simple-notes-sync/releases/latest) · [📖 Dokumentation](docs/DOCS.de.md) · [🚀 Quick Start](QUICKSTART.de.md)

**🌍** **Deutsch** · [English](README.md)

</div>

---

## 📱 Screenshots

<p align="center">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/1.png" width="250" alt="Sync-Status">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/2.png" width="250" alt="Notiz bearbeiten">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/3.png" width="250" alt="Checkliste bearbeiten">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/4.png" width="250" alt="Einstellungen">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/5.png" width="250" alt="Server-Einstellungen">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/7.png" width="250" alt="Sync-Einstellungen">
</p>

---

<div align="center">

📝 Offline-first &nbsp;•&nbsp; 🔄 Smart Sync &nbsp;•&nbsp; 🔒 Self-hosted &nbsp;•&nbsp; 🔋 Akkuschonend

</div>

---

## ✨ Highlights

- ✅ **NEU: Checklisten** - Tap-to-Check, Drag & Drop
- 🌍 **NEU: Mehrsprachig** - Deutsch/Englisch mit Sprachauswahl
- 📝 **Offline-First** - Funktioniert ohne Internet
- 🔄 **Konfigurierbare Sync-Trigger** - onSave, onResume, WiFi-Verbindung, periodisch (15/30/60 Min), Boot
- 🔒 **Self-Hosted** - Deine Daten bleiben bei dir (WebDAV)
- 💾 **Lokales Backup** - Export/Import als JSON-Datei
- 🖥️ **Desktop-Integration** - Markdown-Export für Obsidian, VS Code, Typora
- 🔋 **Akkuschonend** - ~0.2% mit Defaults, bis zu ~1.0% mit Periodic Sync
- 🎨 **Material Design 3** - Dark Mode & Dynamic Colors

➡️ **Vollständige Feature-Liste:** [FEATURES.de.md](docs/FEATURES.de.md)

---

## 🚀 Schnellstart

### 1. Server Setup (5 Minuten)

```bash
git clone https://github.com/inventory69/simple-notes-sync.git
cd simple-notes-sync/server
cp .env.example .env
# Passwort in .env setzen
docker compose up -d
```

➡️ **Details:** [Server Setup Guide](server/README.de.md)

### 2. App Installation (2 Minuten)

1. [APK herunterladen](https://github.com/inventory69/simple-notes-sync/releases/latest)
2. Installieren & öffnen
3. ⚙️ Einstellungen → Server konfigurieren:
   - **URL:** `http://DEINE-SERVER-IP:8080/` _(nur Base-URL!)_
   - **User:** `noteuser`
   - **Passwort:** _(aus .env)_
   - **WLAN:** _(dein Netzwerk-Name)_
4. **Verbindung testen** → Auto-Sync aktivieren
5. Fertig! 🎉

➡️ **Ausführliche Anleitung:** [QUICKSTART.de.md](QUICKSTART.de.md)

---

## 📚 Dokumentation

| Dokument | Inhalt |
|----------|--------|
| **[QUICKSTART.de.md](QUICKSTART.de.md)** | Schritt-für-Schritt Installation |
| **[FEATURES.de.md](docs/FEATURES.de.md)** | Vollständige Feature-Liste |
| **[BACKUP.de.md](docs/BACKUP.de.md)** | Backup & Wiederherstellung |
| **[DESKTOP.de.md](docs/DESKTOP.de.md)** | Desktop-Integration (Markdown) |
| **[DOCS.de.md](docs/DOCS.de.md)** | Technische Details & Troubleshooting |
| **[CHANGELOG.de.md](CHANGELOG.de.md)** | Versionshistorie |
| **[UPCOMING.de.md](docs/UPCOMING.de.md)** | Geplante Features 🚀 |
| **[ÜBERSETZEN.md](docs/TRANSLATING.de.md)** | Übersetzungsanleitung 🌍 |

---

## 🛠️ Entwicklung

```bash
cd android
./gradlew assembleStandardRelease
```

➡️ **Build-Anleitung:** [DOCS.md](docs/DOCS.md#-build--deployment)

---

## 🤝 Contributing

Beiträge willkommen! Siehe [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 📄 Lizenz

MIT License - siehe [LICENSE](LICENSE)

---

<div align="center">

**v1.6.1** · Built with ❤️ using Kotlin + Jetpack Compose + Material Design 3

</div>
