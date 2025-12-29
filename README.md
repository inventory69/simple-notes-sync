# Simple Notes Sync 📝

> Minimalistische Offline-Notizen mit Auto-Sync zu deinem eigenen Server

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![Material Design 3](https://img.shields.io/badge/Material-Design%203-green.svg)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**📱 [APK Download](https://github.com/inventory69/simple-notes-sync/releases/latest)** · **📖 [Dokumentation](DOCS.md)** · **🚀 [Quick Start](QUICKSTART.md)**

**🌍 Sprachen:** **Deutsch** · [English](README.en.md)

---

## 📱 Screenshots

<p align="center">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/1.jpg" width="250" alt="Notizliste">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/2.jpg" width="250" alt="Notiz bearbeiten">
  <img src="fastlane/metadata/android/de-DE/images/phoneScreenshots/3.jpg" width="250" alt="Einstellungen">
</p>

---

## Features

### 📝 Notizen
* Einfache Textnotizen mit automatischem Speichern
* Swipe-to-Delete mit Bestätigung
* Material Design 3 Editor

### 🔄 Synchronisation
* **Pull-to-Refresh** für manuellen Sync
* **Auto-Sync** (15/30/60 Min) nur im Heim-WLAN
* **Smart Server-Check** - Keine Fehler in fremden Netzwerken
* **Konfliktfreies Merging** - Deine Änderungen gehen nie verloren

### 🔒 Privacy & Self-Hosted
* **WebDAV-Server** (Nextcloud, ownCloud, etc.)
* **Docker Setup-Anleitung** in den Docs enthalten
* **Deine Daten bleiben bei dir** - Kein Tracking, keine Cloud
* **HTTP nur lokal** - HTTPS für externe Server
* **100% Open Source** (MIT Lizenz)

### 🔋 Performance
* **Akkuschonend** (~0.2-0.8% pro Tag)
* **Offline-First** - Funktioniert ohne Internet
* **Dark Mode** & Dynamic Colors

---

## 🚀 Quick Start

### 1. Server Setup

```bash
cd server
cp .env.example .env
# Passwort in .env setzen
docker compose up -d
```

➡️ **Details:** [Server Setup Guide](server/README.md)

### 2. App Installation

1. [APK herunterladen](https://github.com/inventory69/simple-notes-sync/releases/latest)
2. Installieren & öffnen
3. ⚙️ Einstellungen → Server konfigurieren
4. Auto-Sync aktivieren

➡️ **Details:** [Vollständige Anleitung](QUICKSTART.md)

---

## 📚 Dokumentation

- **[Quick Start Guide](QUICKSTART.md)** - Schritt-für-Schritt Anleitung für Endbenutzer
- **[Server Setup](server/README.md)** - WebDAV Server konfigurieren
- **[Vollständige Docs](DOCS.md)** - Features, Troubleshooting, Build-Anleitung

---

## 🛠️ Entwicklung

```bash
cd android
./gradlew assembleStandardRelease
```

➡️ **Details:** [Build-Anleitung in DOCS.md](DOCS.md)

---

## 🤝 Contributing

Beiträge sind willkommen! Siehe [CONTRIBUTING.md](CONTRIBUTING.md) für Details.

---

## 📄 Lizenz

MIT License - siehe [LICENSE](LICENSE)

**v1.1.2** · Gebaut mit Kotlin + Material Design 3
