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

### 💾 Backup & Restore **NEW in v1.2.0**
* **Local backup** - Export all notes as JSON file
* **Flexible restore** - 3 modes (Merge, Replace, Overwrite)
* **Automatic safety net** - Auto-backup before every restore
* **Independent from server** - Works completely offline

### 🖥️ Desktop Integration **NEW in v1.2.0**
* **Markdown export** - Notes are automatically exported as `.md` files
* **WebDAV access** - Mount WebDAV as network drive for direct access
* **Editor compatibility** - VS Code, Typora, Notepad++, or any Markdown editor
* **Last-Write-Wins** - Intelligent conflict resolution via timestamps
* **Dual-format** - JSON sync remains master, Markdown is optional mirror

### 🔄 Synchronization
* **Pull-to-refresh** for manual sync
* **Auto-sync** (15/30/60 min) only on home WiFi
* **Smart server check** - No errors on foreign networks
* **Conflict-free merging** - Your changes are never lost
* **6 sync triggers** - Periodic, app-start, WiFi, manual, pull-to-refresh, settings

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

## � Local Backup & Restore

### Create Backup

1. **Settings** → **Backup & Restore**
2. Tap **"📥 Create backup"**
3. Choose location (Downloads, SD card, cloud folder)
4. Done! All notes are saved in a `.json` file

**Filename:** `simplenotes_backup_YYYY-MM-DD_HHmmss.json`

### Restore

1. **Settings** → **"📤 Restore from file"**
2. Select backup file
3. **Choose restore mode:**
   - **Merge** _(Default)_ - Add new notes, keep existing ones
   - **Replace** - Delete all and import backup
   - **Overwrite duplicates** - Backup wins on ID conflicts
4. Confirm - _Automatic safety backup is created!_

**💡 Tip:** Before every restore, an automatic safety backup is created - your data is safe!

---

## 🖥️ Desktop Integration (WebDAV + Markdown)

### Why Markdown?

The app automatically exports your notes as `.md` files so you can edit them on desktop:

- **JSON remains master** - Primary sync mechanism (reliable, fast)
- **Markdown is mirror** - Additional export for desktop access
- **Dual-format** - Both formats are always in sync

### Setup: WebDAV as Network Drive

**With WebDAV mount ANY Markdown editor works!**

#### Windows:

1. **Open Explorer** → Right-click on "This PC"
2. **"Map network drive"**
3. **Enter WebDAV URL:** `http://YOUR-SERVER:8080/notes-md/`
4. Enter username/password
5. **Done!** - Folder appears as drive (e.g. Z:\)

#### macOS:

1. **Finder** → Menu "Go" → "Connect to Server" (⌘K)
2. **Server Address:** `http://YOUR-SERVER:8080/notes-md/`
3. Enter username/password
4. **Done!** - Folder appears under "Network"

#### Linux:

```bash
# Option 1: GNOME Files / Nautilus
Files → Other Locations → Connect to Server
Server Address: dav://YOUR-SERVER:8080/notes-md/

# Option 2: davfs2 (permanent mount)
sudo apt install davfs2
sudo mount -t davfs http://YOUR-SERVER:8080/notes-md/ /mnt/notes
```

### Workflow:

1. **Enable Markdown export** (App → Settings)
2. **Mount WebDAV** (see above)
3. **Open editor** (VS Code, Typora, Notepad++, etc.)
4. **Edit notes** - Changes are saved directly
5. **"Import Markdown Changes" in app** - Import desktop changes

**Recommended Editors:**
- **VS Code** - Free, powerful, with Markdown preview
- **Typora** - Minimalist, WYSIWYG Markdown
- **Notepad++** - Lightweight, fast
- **iA Writer** - Focused writing

- **VS Code** with WebDAV extension
- **Typora** (local copy)
- **iA Writer** (read/edit only, no auto-sync)

**⚠️ Important:** 
- Markdown export is **optional** (toggle in settings)
- JSON sync **always** works - Markdown is additional
- All 6 sync triggers remain unchanged

---

## �📚 Documentation

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

## � Changelog

All changes are documented in [CHANGELOG.md](CHANGELOG.md).

---

## �📄 License

MIT License - see [LICENSE](LICENSE)

**v1.2.0** · Built with Kotlin + Material Design 3
