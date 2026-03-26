# Complete Feature List 📋

**🌍 Languages:** [Deutsch](FEATURES.de.md) · **English**

> All features of Simple Notes Sync in detail

---

## 📝 Note Management

### Note Types
- ✅ **Text notes** - Classic free-form notes
- ✅ **Checklists** _(NEW in v1.4.0)_ - Task lists with tap-to-check
  - ➕ Add items via input field
  - ☑️ Tap to check/uncheck
  - 📌 Long-press for drag & drop sorting
  - ~~Strikethrough~~ for completed entries
  - ↩️ Un-check returns item to original position _(v1.9.0)_

### Basic Features
- ✅ **Auto-save** - No manual saving needed
- ✅ **Title + content** - Clear structure for each note
- ✅ **Timestamps** - Creation and modification date automatically
- ✅ **Selection Mode** _(NEW in v1.5.0)_ - Long-press for multi-select and batch delete
- ✅ **Confirmation dialog** - Protection against accidental deletion
- ✅ **Jetpack Compose UI** _(NEW in v1.5.0)_ - Modern, performant user interface
- ✅ **Material Design 3** - Modern, clean UI
- ✅ **Dark mode** - Automatically based on system settings
- ✅ **Dynamic colors** - Adapts to your Android theme
- ✅ **Multi-theme** _(NEW in v2.0.0)_ - 7 color schemes with animated transitions and tinted surfaces
- ✅ **Custom app title** _(NEW in v1.9.0)_ - Configurable app name

### Editor
- ✅ **Minimalist editor** - No bells and whistles
- ✅ **Auto-focus** - Start writing immediately
- ✅ **Fullscreen mode** - Maximum writing space
- ✅ **Save button** - Manual confirmation possible
- ✅ **Back navigation** - Saves automatically when autosave is enabled _(v1.10.0)_
- ✅ **Slide animations** _(NEW in v1.5.0)_ - Smooth transitions
- ✅ **Markdown preview** _(NEW in v1.9.0)_ - Live preview with formatting toolbar
- ✅ **Opt-in autosave** _(NEW in v1.9.0)_ - Configurable debounce autosave timer
- ✅ **Undo/Redo** _(NEW in v1.10.0)_ - Full undo/redo history (up to 50 steps) with toolbar buttons
- ✅ **Share & export** _(NEW in v1.10.0)_ - Share as text or PDF, export to calendar
- ✅ **Delete with undo** _(NEW in v1.10.0)_ - Delete from editor with timed undo snackbar

---

## 📊 Views & Layout _(NEW in v1.7.0+)_

### Display Modes
- ✅ **List View** - Classic list layout
- ✅ **Grid View** _(NEW in v1.7.0)_ - Pinterest-style staggered grid with dynamic preview lines
- ✅ **Layout toggle** - Switch between list and grid in settings
- ✅ **Adaptive columns** - 2-3 columns based on screen size
- ✅ **Grid column scaling** _(NEW in v2.0.0)_ - 1–5 columns configurable in display settings
- ✅ **Grid as default** _(v1.8.0)_ - New installations default to grid view

### Note Sorting _(NEW in v1.8.0)_
- ✅ **Sort by Updated** - Newest or oldest first
- ✅ **Sort by Created** - By creation date
- ✅ **Sort by Title** - A-Z or Z-A
- ✅ **Sort by Type** - Text notes vs checklists
- ✅ **Persistent preferences** - Sort option saved across app restarts
- ✅ **Sort dialog** - Direction toggle in main screen

### Note Filtering _(NEW in v1.9.0)_
- ✅ **Filter Chip Row** - Filter by All, Text, or Checklists
- ✅ **Inline search** - Quick search in the filter row
- ✅ **Sort button** - Compact sort icon in filter row
- ✅ **Toggle visibility** - Tune button hides/shows the filter row
- ✅ **Configurable sync folder** - Custom WebDAV folder name

### Checklist Sorting _(NEW in v1.8.0)_
- ✅ **Manual** - Custom drag & drop order
- ✅ **Alphabetical** - A-Z sorting
- ✅ **Unchecked First** - Unchecked items on top
- ✅ **Checked Last** - Checked items at bottom
- ✅ **Date created** _(NEW in v1.11.0)_ - Sort by creation date (ascending or descending)
- ✅ **Visual separator** - Between unchecked/checked groups with count
- ✅ **Auto-sort on toggle** - Re-sorts when checking/unchecking items
- ✅ **Drag across boundaries** - Items auto-toggle state when crossing separator

---

## 📌 Homescreen Widgets _(NEW in v1.8.0)_

### Widget Features
- ✅ **Text note widget** - Display any note on homescreen
- ✅ **Checklist widget** - Interactive checkboxes that sync to server
- ✅ **5 size classes** - SMALL, NARROW_MED, NARROW_TALL, WIDE_MED, WIDE_TALL
- ✅ **Material You colors** - Dynamic colors matching system theme
- ✅ **Configurable opacity** - Background transparency (0-100%)
- ✅ **Lock toggle** - Prevent accidental edits
- ✅ **Auto-refresh** - Updates after sync completion
- ✅ **Configuration activity** - Note selection and settings
- ✅ **Checklist sorting** _(v1.8.1)_ - Widgets respect saved sort option
- ✅ **Visual separators** _(v1.8.1)_ - Between unchecked/checked items
- ✅ **Monet tint preservation** _(v1.9.0)_ - Translucent background keeps dynamic colors
- ✅ **Seamless options bar** _(v1.9.0)_ - Removed background for cleaner look
- ✅ **Checklist strikethrough** _(v1.9.0)_ - Completed items show strikethrough in widget
- ✅ **Auto-refresh on leave** _(v1.9.0)_ - Widgets update when leaving app

---

## 🌍 Multilingual Support _(NEW in v1.5.0)_

### Supported Languages
- ✅ **English** - Primary language (default)
- ✅ **German** - Fully translated

### Language Selection
- ✅ **Automatic detection** - Follows system language
- ✅ **Manual selection** - Switchable in settings
- ✅ **Per-App Language** - Android 13+ native language selection
- ✅ **locales_config.xml** - Complete Android integration

### Scope
- ✅ **400+ strings** - Fully translated
- ✅ **UI texts** - All buttons, dialogs, menus
- ✅ **Error messages** - Helpful localized hints
- ✅ **Settings** - 7 categorized screens

---

## 💾 Backup & Restore

### Local Backup System
- ✅ **JSON export** - All notes in one file
- ✅ **Free location choice** - Downloads, SD card, cloud folder
- ✅ **Filenames with timestamp** - `simplenotes_backup_YYYY-MM-DD_HHmmss.json`
- ✅ **Complete export** - Title, content, timestamps, IDs
- ✅ **Human-readable format** - JSON with formatting
- ✅ **Independent from server** - Works completely offline

### Restore Modes
- ✅ **Merge** - Add new notes, keep existing ones _(Default)_
- ✅ **Replace** - Delete all and import backup
- ✅ **Overwrite duplicates** - Backup wins on ID conflicts
- ✅ **Automatic safety backup** - Before every restore
- ✅ **Backup validation** - Checks format and version
- ✅ **Error handling** - Clear error messages on issues

---

## 🖥️ Desktop Integration

### Markdown Export
- ✅ **Automatic export** - Each note → `.md` file
- ✅ **Checklists as task lists** _(NEW)_ - `- [ ]` / `- [x]` format (GitHub-compatible)
- ✅ **Dual-format** - JSON (master) + Markdown (mirror)
- ✅ **Filename sanitization** - Safe filenames from titles
- ✅ **Duplicate handling** _(NEW)_ - ID suffix for same titles
- ✅ **Frontmatter metadata** - YAML with ID, timestamps, type
- ✅ **WebDAV sync** - Parallel to JSON sync
- ✅ **Optional** - Toggle in settings
- ✅ **Initial export** - All existing notes when activated
- ✅ **Progress indicator** - Shows X/Y during export

### Markdown Import
- ✅ **Desktop → App** - Import changes from desktop
- ✅ **Last-Write-Wins** - Conflict resolution via timestamp
- ✅ **Frontmatter parsing** - Reads metadata from `.md` files
- ✅ **Detect new notes** - Automatically adopt to app
- ✅ **Detect updates** - Only if desktop version is newer
- ✅ **Error tolerance** - Individual errors don't abort import

### WebDAV Access
- ✅ **Network drive mount** - Windows, macOS, Linux
- ✅ **Any Markdown editor** - VS Code, Typora, Notepad++, iA Writer
- ✅ **Live editing** - Direct access to `.md` files
- ✅ **Folder structure** - `/notes/` for JSON, `/notes-md/` for Markdown
- ✅ **Automatic folder creation** - On first sync

---

## 🔄 Synchronization

### Auto-Sync
- ✅ **Interval selection** - 15, 30 or 60 minutes
- ✅ **WiFi trigger** - Sync on WiFi connection _(no SSID restriction)_
- ✅ **Battery-friendly** - ~0.2-0.8% per day
- ✅ **Smart server check** - Sync only when server is reachable
- ✅ **WorkManager** - Reliable background execution
- ✅ **Battery optimization compatible** - Works even with Doze mode

### Sync Triggers (6 total)
1. ✅ **Periodic sync** - Automatically after interval
2. ✅ **App-start sync** - When opening the app
3. ✅ **WiFi-connect sync** - On any WiFi connection
4. ✅ **Manual sync** - Button in settings
5. ✅ **Pull-to-refresh** - Swipe gesture in notes list
6. ✅ **Settings-save sync** - After server configuration

### Sync Mechanism
- ✅ **Upload** - Local changes to server
- ✅ **Download** - Server changes to app
- ✅ **Parallel downloads** _(NEW in v1.8.0)_ - Up to 5 simultaneous downloads
- ✅ **Conflict detection** - On simultaneous changes
- ✅ **Conflict-free merging** - Last-Write-Wins via timestamp
- ✅ **Server deletion detection** _(NEW in v1.8.0)_ - Detects notes deleted on other devices
- ✅ **Sync status tracking** - LOCAL_ONLY, PENDING, SYNCED, CONFLICT, DELETED_ON_SERVER
- ✅ **Live progress UI** _(NEW in v1.8.0)_ - Phase indicators with upload/download counters
- ✅ **Error handling** - Retry on network issues
- ✅ **Offline-first** - App works without server

### Server Connection
- ✅ **WebDAV protocol** - Standard protocol
- ✅ **HTTP/HTTPS** - HTTP only local, HTTPS for external
- ✅ **Username/password** - Basic authentication
- ✅ **Connection test** - Test in settings
- ✅ **WiFi-only sync** _(NEW in v1.7.0)_ - Option to sync only on WiFi
- ✅ **VPN support** _(NEW in v1.7.0)_ - Sync works correctly through VPN tunnels
- ✅ **Self-signed SSL** _(NEW in v1.7.0)_ - Support for self-signed certificates
- ✅ **Server URL normalization** - Automatic `/notes/` and `/notes-md/` _(NEW in v1.2.1)_
- ✅ **Flexible URL input** - Both variants work: `http://server/` and `http://server/notes/`

---

## 🔒 Privacy & Security

### Self-Hosted
- ✅ **Own server** - Full control over data
- ✅ **No cloud** - No third parties
- ✅ **No tracking** - No analytics, no telemetry
- ✅ **No account** - Only server credentials
- ✅ **100% open source** - AGPL v3 License

### Data Security
- ✅ **Local storage** - App-private storage (Android)
- ✅ **WebDAV encryption** - HTTPS for external servers
- ✅ **Password storage** - Android SharedPreferences (encrypted)
- ✅ **No third-party libs** - Only Android SDK + Sardine (WebDAV)

### Developer Features
- ✅ **File logging** - Optional, only when enabled _(NEW in v1.3.2)_
- ✅ **Privacy notice** - Explicit warning on activation
- ✅ **Local logs** - Logs stay on device

---

## 🔋 Performance & Optimization

### Battery Efficiency (v1.6.0)
- ✅ **Configurable sync triggers** - Enable/disable each trigger individually
- ✅ **Smart defaults** - Only event-driven triggers active by default
- ✅ **Optimized periodic intervals** - 15/30/60 min (default: OFF)
- ✅ **WiFi-only** - No mobile data sync
- ✅ **Smart server check** - Sync only when server is reachable
- ✅ **WorkManager** - System-optimized execution
- ✅ **Doze mode compatible** - Sync runs even in standby
- ✅ **Measured consumption:**
  - Default (event-driven only): ~0.2%/day (~6.5 mAh) ⭐ _Optimal_
  - With periodic 15 min: ~1.0%/day (~30 mAh)
  - With periodic 30 min: ~0.6%/day (~19 mAh)
  - With periodic 60 min: ~0.4%/day (~13 mAh)

### App Performance
- ✅ **Offline-first** - Works without internet
- ✅ **Instant-load** - Notes load in <100ms
- ✅ **Smooth scrolling** - LazyColumn with Compose
- ✅ **Material Design 3** - Native Android UI
- ✅ **Kotlin Coroutines** - Asynchronous operations
- ✅ **Minimal APK size** - ~2 MB

---

## 🛠️ Technical Details

### Platform
- ✅ **Android 7.0+** (API 24+)
- ✅ **Target SDK 36** (Android 15)
- ✅ **Kotlin** - Modern programming language
- ✅ **Jetpack Compose** - Declarative UI framework
- ✅ **Material Design 3** - Latest design guidelines
- ✅ **Multi-theme system** _(v2.0.0)_ - 7 color schemes incl. AMOLED & Dynamic Color
- ✅ **Jetpack Glance** _(v1.8.0)_ - Widget framework

### Architecture
- ✅ **MVVM-Light** - Simple architecture
- ✅ **Single Activity** - Modern navigation
- ✅ **Kotlin Coroutines** - Async/Await pattern
- ✅ **Dispatchers.IO** - Background operations
- ✅ **SharedPreferences** - Settings storage
- ✅ **File-based storage** - JSON files locally
- ✅ **Custom exceptions** - Dedicated SyncException for better error handling _(NEW in v1.3.2)_

### Dependencies
- ✅ **AndroidX** - Jetpack libraries
- ✅ **Material Components** - Material Design 3
- ✅ **Sardine** - WebDAV client (com.thegrizzlylabs)
- ✅ **Gson** - JSON serialization
- ✅ **WorkManager** - Background tasks
- ✅ **OkHttp** - HTTP client (via Sardine)
- ✅ **Glance** _(v1.8.0)_ - Widget framework

### Build Variants
- ✅ **Standard** - Universal APK (100% FOSS, no Google dependencies)
- ✅ **F-Droid** - Identical to Standard (100% FOSS)
- ✅ **Debug/Release** - Development and production
- ✅ **No Google Services** - Completely FOSS, no proprietary libraries

---

## 📦 Server Compatibility

### Tested WebDAV Servers
- ✅ **Docker WebDAV** (recommended for self-hosting)
- ✅ **Nextcloud** - Fully compatible
- ✅ **ownCloud** - Works perfectly
- ✅ **Apache mod_dav** - Standard WebDAV
- ✅ **nginx + WebDAV** - With correct configuration

### Server Features
- ✅ **Basic Auth** - Username/password
- ✅ **Directory listing** - For download
- ✅ **PUT/GET** - Upload/download
- ✅ **MKCOL** - Create folders
- ✅ **DELETE** - Delete notes (future)

---

## 🔮 Future Features

Planned for upcoming versions – see [UPCOMING.md](UPCOMING.md) for the full roadmap.

---

## 📊 Comparison with Other Apps

| Feature | Simple Notes Sync | Google Keep | Nextcloud Notes |
|---------|------------------|-------------|-----------------|
| Offline-first | ✅ | ⚠️ Limited | ⚠️ Limited |
| Self-hosted | ✅ | ❌ | ✅ |
| Auto-sync | ✅ | ✅ | ✅ |
| Markdown export | ✅ | ❌ | ✅ |
| Desktop access | ✅ (WebDAV) | ✅ (Web) | ✅ (Web + WebDAV) |
| Local backup | ✅ | ❌ | ⚠️ Server backup |
| No Google account | ✅ | ❌ | ✅ |
| Open Source | ✅ AGPL v3 | ❌ | ✅ AGPL |
| APK size | ~2 MB | ~50 MB | ~8 MB |
| Battery usage | ~0.4%/day | ~1-2%/day | ~0.5%/day |

---

## ❓ FAQ

**Q: Do I need a server?**  
A: No! The app works completely offline. The server is optional for sync.

**Q: Which server is best?**  
A: For beginners: Docker WebDAV (simple, easy). For pros: Nextcloud (many features).

**Q: Does Markdown export work without Desktop Integration?**  
A: No, you need to activate the feature in settings.

**Q: Will my data be lost if I switch servers?**  
A: No! Create a local backup, switch servers, restore.

**Q: Why JSON + Markdown?**  
A: JSON is reliable and fast (master). Markdown is human-readable (mirror for desktop).

**Q: Can I use the app without Google Play?**  
A: Yes! Download the APK directly from GitHub or use F-Droid.

---

**Last update:** v2.1.0 (2026-03-26)
