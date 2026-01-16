# Simple Notes Sync 📝

> Minimalistische Offline-Notizen mit Auto-Sync zu deinem eigenen Server

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![Material Design 3](https://img.shields.io/badge/Material-Design%203-green.svg)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/dev.dettmer.simplenotes)

**📱 [APK Download](https://github.com/inventory69/simple-notes-sync/releases/latest)** · **📖 [Dokumentation](docs/DOCS.de.md)** · **🚀 [Quick Start](QUICKSTART.de.md)**

**🌍 Sprachen:** **Deutsch** · [English](README.md)

---

## 📱 Screenshots

<p align="center">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/1.jpg" width="250" alt="Notizliste">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/2.jpg" width="250" alt="Notiz bearbeiten">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/3.jpg" width="250" alt="Einstellungen">
</p>

---

## ✨ Highlights

- ✅ **NEU: Checklisten** - Tap-to-Check, Drag & Drop
- 🌍 **NEU: Mehrsprachig** - Deutsch/Englisch mit Sprachauswahl
- 📝 **Offline-First** - Funktioniert ohne Internet
- 🔄 **Auto-Sync** - Bei WiFi-Verbindung (15/30/60 Min)
- 🔒 **Self-Hosted** - Deine Daten bleiben bei dir (WebDAV)
- 💾 **Lokales Backup** - Export/Import als JSON-Datei
- 🖥️ **Desktop-Integration** - Markdown-Export für Obsidian, VS Code, Typora
- 🔋 **Akkuschonend** - ~0.2-0.8% pro Tag
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

**v1.4.1** · Built with ❤️ using Kotlin + Material Design 3
