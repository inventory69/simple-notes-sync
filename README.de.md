<div align="center">
<br />
<img src="android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="Logo" />
</div>

<h1 align="center">Simple Notes Sync</h1>

<h4 align="center">Minimalistische Offline-Notizen mit intelligentem Sync - Einfachheit trifft smarte Synchronisation.</h4>

<div align="center">
  <a href="https://www.android.com/">
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  </a>
  <a href="https://kotlinlang.org/">
    <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  </a>
  <a href="https://developer.android.com/compose/">
    <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  </a>
  <a href="https://m3.material.io/">
    <img src="https://img.shields.io/badge/Material_3-6750A4?style=for-the-badge&logo=material-design&logoColor=white" alt="Material 3" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-F5C400?style=for-the-badge" alt="License" />
  </a>
</div>

<p align="center">
<div align="center" style="display:flex; justify-content:center; gap:10px;">
  <a href="https://apt.izzysoft.de/fdroid/index/apk/dev.dettmer.simplenotes" style="text-decoration:none;">
    <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80" style="vertical-align:middle;">
  </a>
  <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/inventory69/simple-notes-sync" style="text-decoration:none;">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="55" style="vertical-align:middle;">
  </a>
  <a href="https://f-droid.org/packages/dev.dettmer.simplenotes/" style="text-decoration:none;">
    <img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80" style="vertical-align:middle;">
  </a>
</div>
<div align="center">
<strong>SHA-256 Hash des Signaturzertifikats:</strong><br /> 42:A1:C6:13:BB:C6:73:04:5A:F3:DC:81:91:BF:9C:B6:45:6E:E4:4C:7D:CE:40:C7:CF:B5:66:FA:CB:69:F1:6A
</div>

<div align="center">

<br />[📱 APK Download](https://github.com/inventory69/simple-notes-sync/releases/latest) · [📖 Dokumentation](docs/DOCS.de.md) · [🚀 Schnellstart](QUICKSTART.de.md)<br />
**🌍** Deutsch · [English](README.md)

</div>

## 📱 Screenshots

<p align="center">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/1.png" width="250" alt="Sync status">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/2.png" width="250" alt="Edit note">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/3.png" width="250" alt="Edit checklist">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/4.png" width="250" alt="Settings">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/5.png" width="250" alt="Server settings">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/7.png" width="250" alt="Sync settings">
</p>

<div align="center">

  📝 Offline-first &nbsp;•&nbsp; 🔄 Smart Sync &nbsp;•&nbsp; 🔒 Self-hosted &nbsp;•&nbsp; 🔋 Akkuschonend

</div>

## ✨ Highlights

- 📝 **Offline-first** – Funktioniert ohne Internet
- 📊 **Flexible Ansichten** – Listen- und Grid-Layout
- ✅ **Checklisten** – Tap-to-Check, Drag & Drop
- 🌍 **Mehrsprachig** – Deutsch/Englisch mit Sprachauswahl
- 🔄 **Konfigurierbare Sync-Trigger** – onSave, onResume, WiFi, periodisch (15/30/60 Min), Boot
- 🔒 **Self-hosted** – Deine Daten bleiben bei dir (WebDAV)
- 💾 **Lokales Backup** – Export/Import als JSON-Datei (optional verschlüsselt)
- 🖥️ **Desktop-Integration** – Markdown-Export für Obsidian, VS Code, Typora
- 🔋 **Akkuschonend** – ~0.2% mit Defaults, bis zu ~1.0% mit Periodic Sync
- 🎨 **Material Design 3** – Dynamischer Dark/Light Mode & Farben

➡️ **Vollständige Feature-Liste:** [docs/FEATURES.de.md](docs/FEATURES.de.md)

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

## 📚 Dokumentation

| Dokument | Inhalt |
|----------|--------|
| **[QUICKSTART.de.md](QUICKSTART.de.md)** | Schritt-für-Schritt Installation |
| **[FEATURES.de.md](docs/FEATURES.de.md)** | Vollständige Feature-Liste |
| **[BACKUP.de.md](docs/BACKUP.de.md)** | Backup & Wiederherstellung |
| **[DESKTOP.de.md](docs/DESKTOP.de.md)** | Desktop-Integration (Markdown) |
| **[SELF_SIGNED_SSL.md](docs/SELF_SIGNED_SSL.md)** | Self-signed SSL Zertifikat Setup |
| **[DOCS.de.md](docs/DOCS.de.md)** | Technische Details & Troubleshooting |
| **[CHANGELOG.de.md](CHANGELOG.de.md)** | Versionshistorie |
| **[UPCOMING.de.md](docs/UPCOMING.de.md)** | Geplante Features 🚀 |
| **[ÜBERSETZEN.md](docs/TRANSLATING.de.md)** | Übersetzungsanleitung 🌍 |

## 🛠️ Entwicklung

```bash
cd android
./gradlew assembleStandardRelease
```

➡️ **Build-Anleitung:** [docs/DOCS.de.md#-build--deployment](docs/DOCS.de.md#-build--deployment)

## 🤝 Contributing

Beiträge willkommen! Siehe [CONTRIBUTING.md](CONTRIBUTING.md)

## 📄 Lizenz

MIT License – siehe [LICENSE](LICENSE)

<div align="center">
<br /><br />
**v1.7.0** · Built with ❤️ using Kotlin + Jetpack Compose + Material Design 3

</div>
