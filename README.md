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

### 💾 Backup & Wiederherstellung **NEU in v1.2.0**
* **Lokales Backup** - Exportiere alle Notizen als JSON-Datei
* **Flexible Wiederherstellung** - 3 Modi (Zusammenführen, Ersetzen, Überschreiben)
* **Automatisches Sicherheitsnetz** - Auto-Backup vor jeder Wiederherstellung
* **Unabhängig vom Server** - Funktioniert komplett offline

### 🖥️ Desktop-Integration **NEU in v1.2.0**
* **Markdown-Export** - Notizen werden automatisch als `.md` Dateien exportiert
* **WebDAV-Zugriff** - Mounte WebDAV als Netzlaufwerk für direkten Zugriff
* **Editor-Kompatibilität** - VS Code, Typora, Notepad++, oder beliebiger Markdown-Editor
* **Last-Write-Wins** - Intelligente Konfliktauflösung via Zeitstempel
* **Dual-Format** - JSON-Sync bleibt Master, Markdown ist optionaler Mirror

### 🔄 Synchronisation
* **Pull-to-Refresh** für manuellen Sync
* **Auto-Sync** (15/30/60 Min) nur im Heim-WLAN
* **Smart Server-Check** - Keine Fehler in fremden Netzwerken
* **Konfliktfreies Merging** - Deine Änderungen gehen nie verloren
* **6 Sync-Trigger** - Periodic, App-Start, WiFi, Manual, Pull-to-Refresh, Settings

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

## � Lokales Backup & Wiederherstellung

### Backup erstellen

1. **Einstellungen** → **Backup & Wiederherstellung**
2. Tippe auf **"📥 Backup erstellen"**
3. Wähle Speicherort (Downloads, SD-Karte, Cloud-Ordner)
4. Fertig! Alle Notizen sind in einer `.json` Datei gesichert

**Dateiname:** `simplenotes_backup_YYYY-MM-DD_HHmmss.json`

### Wiederherstellen

1. **Einstellungen** → **"📤 Aus Datei wiederherstellen"**
2. Wähle Backup-Datei
3. **Wiederherstellungs-Modus auswählen:**
   - **Zusammenführen** _(Standard)_ - Neue Notizen hinzufügen, bestehende behalten
   - **Ersetzen** - Alle löschen und Backup importieren
   - **Duplikate überschreiben** - Backup gewinnt bei ID-Konflikten
4. Bestätigen - _Automatisches Sicherheits-Backup wird erstellt!_

**💡 Tipp:** Vor jeder Wiederherstellung wird automatisch ein Auto-Backup erstellt - deine Daten sind sicher!

---

## 🖥️ Desktop-Integration (WebDAV + Markdown)

### Warum Markdown?

Die App exportiert deine Notizen automatisch als `.md` Dateien, damit du sie auf dem Desktop bearbeiten kannst:

- **JSON bleibt Master** - Primärer Sync-Mechanismus (verlässlich, schnell)
- **Markdown ist Mirror** - Zusätzlicher Export für Desktop-Zugriff
- **Dual-Format** - Beide Formate sind immer synchron

### Setup: WebDAV als Netzlaufwerk

**Mit WebDAV-Mount funktioniert JEDER Markdown-Editor!**

#### Windows:

1. **Explorer öffnen** → Rechtsklick auf "Dieser PC"
2. **"Netzlaufwerk verbinden"** wählen
3. **WebDAV-URL eingeben:** `http://DEIN-SERVER:8080/notes-md/`
4. Benutzername/Passwort eingeben
5. **Fertig!** - Ordner erscheint als Laufwerk (z.B. Z:\)

#### macOS:

1. **Finder** → Menü "Gehe zu" → "Mit Server verbinden" (⌘K)
2. **Server-Adresse:** `http://DEIN-SERVER:8080/notes-md/`
3. Benutzername/Passwort eingeben
4. **Fertig!** - Ordner erscheint unter "Netzwerk"

#### Linux:

```bash
# Option 1: GNOME Files / Nautilus
Dateien → Andere Orte → Mit Server verbinden
Server-Adresse: dav://DEIN-SERVER:8080/notes-md/

# Option 2: davfs2 (permanent mount)
sudo apt install davfs2
sudo mount -t davfs http://DEIN-SERVER:8080/notes-md/ /mnt/notes
```

### Workflow:

1. **Markdown-Export aktivieren** (App → Einstellungen)
2. **WebDAV mounten** (siehe oben)
3. **Editor öffnen** (VS Code, Typora, Notepad++, etc.)
4. **Notizen bearbeiten** - Änderungen werden direkt gespeichert
5. **"Import Markdown Changes" in App** - Desktop-Änderungen importieren

**Empfohlene Editoren:**
- **VS Code** - Kostenlos, mächtig, mit Markdown-Preview
- **Typora** - Minimalistisch, WYSIWYG-Markdown
- **Notepad++** - Leichtgewichtig, schnell
- **iA Writer** - Fokussiertes Schreiben
3. Notizen bearbeiten - Änderungen via "Import Markdown Changes" in die App importieren

### Alternative: Direkter Zugriff

Du kannst die `.md` Dateien auch direkt mit jedem Markdown-Editor öffnen:

- **VS Code** mit WebDAV-Extension
- **Typora** (lokale Kopie)
- **iA Writer** (nur lesen/bearbeiten, kein Auto-Sync)

**⚠️ Wichtig:** 
- Markdown-Export ist **optional** (in Einstellungen ein/ausschaltbar)
- JSON-Sync funktioniert **immer** - Markdown ist zusätzlich
- Alle 6 Sync-Trigger bleiben unverändert erhalten

---

## �📚 Dokumentation

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

## � Changelog

Alle Änderungen sind in [CHANGELOG.md](CHANGELOG.md) dokumentiert.

---

## �📄 Lizenz

MIT License - siehe [LICENSE](LICENSE)

**v1.2.0** · Gebaut mit Kotlin + Material Design 3
