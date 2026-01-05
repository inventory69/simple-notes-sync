# Complete Feature List 📋

**🌍 Languages:** [Deutsch](FEATURES.md) · **English**

> All features of Simple Notes Sync in detail

---

## 📝 Note Management

### Basic Features
- ✅ **Simple text notes** - Focus on content, no distractions
- ✅ **Auto-save** - No manual saving needed
- ✅ **Title + content** - Clear structure for each note
- ✅ **Timestamps** - Creation and modification date automatically
- ✅ **Swipe-to-delete** - Intuitive gesture for deletion
- ✅ **Confirmation dialog** - Protection against accidental deletion
- ✅ **Material Design 3** - Modern, clean UI
- ✅ **Dark mode** - Automatically based on system settings
- ✅ **Dynamic colors** - Adapts to your Android theme

### Editor
- ✅ **Minimalist editor** - No bells and whistles
- ✅ **Auto-focus** - Start writing immediately
- ✅ **Fullscreen mode** - Maximum writing space
- ✅ **Save button** - Manual confirmation possible
- ✅ **Back navigation** - Saves automatically

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
- ✅ **Dual-format** - JSON (master) + Markdown (mirror)
- ✅ **Filename sanitization** - Safe filenames from titles
- ✅ **Frontmatter metadata** - YAML with ID, timestamps, tags
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
- ✅ **WiFi binding** - Only in configured home WiFi
- ✅ **Battery-friendly** - ~0.2-0.8% per day
- ✅ **Smart server check** - No errors on foreign networks
- ✅ **WorkManager** - Reliable background execution
- ✅ **Battery optimization compatible** - Works even with Doze mode

### Sync Triggers (6 total)
1. ✅ **Periodic sync** - Automatically after interval
2. ✅ **App-start sync** - When opening the app
3. ✅ **WiFi-connect sync** - When home WiFi connects
4. ✅ **Manual sync** - Button in settings
5. ✅ **Pull-to-refresh** - Swipe gesture in notes list
6. ✅ **Settings-save sync** - After server configuration

### Sync Mechanism
- ✅ **Upload** - Local changes to server
- ✅ **Download** - Server changes to app
- ✅ **Conflict detection** - On simultaneous changes
- ✅ **Conflict-free merging** - Last-Write-Wins via timestamp
- ✅ **Sync status tracking** - LOCAL_ONLY, PENDING, SYNCED, CONFLICT
- ✅ **Error handling** - Retry on network issues
- ✅ **Offline-first** - App works without server

### Server Connection
- ✅ **WebDAV protocol** - Standard protocol
- ✅ **HTTP/HTTPS** - HTTP only local, HTTPS for external
- ✅ **Username/password** - Basic authentication
- ✅ **Connection test** - Test in settings
- ✅ **Gateway SSID** - WiFi name for auto-sync
- ✅ **Server URL normalization** - Automatic `/notes/` and `/notes-md/` _(NEW in v1.2.1)_
- ✅ **Flexible URL input** - Both variants work: `http://server/` and `http://server/notes/`

---

## 🔒 Privacy & Security

### Self-Hosted
- ✅ **Own server** - Full control over data
- ✅ **No cloud** - No third parties
- ✅ **No tracking** - No analytics, no telemetry
- ✅ **No account** - Only server credentials
- ✅ **100% open source** - MIT License

### Data Security
- ✅ **Local storage** - App-private storage (Android)
- ✅ **WebDAV encryption** - HTTPS for external servers
- ✅ **Password storage** - Android SharedPreferences (encrypted)
- ✅ **No third-party libs** - Only Android SDK + Sardine (WebDAV)

---

## 🔋 Performance & Optimization

### Battery Efficiency
- ✅ **Optimized sync intervals** - 15/30/60 min
- ✅ **WiFi-only** - No mobile data sync
- ✅ **Smart server check** - Only in home WiFi
- ✅ **WorkManager** - System-optimized execution
- ✅ **Doze mode compatible** - Sync runs even in standby
- ✅ **Measured consumption:**
  - 15 min: ~0.8% / day (~23 mAh)
  - 30 min: ~0.4% / day (~12 mAh) ⭐ _Recommended_
  - 60 min: ~0.2% / day (~6 mAh)

### App Performance
- ✅ **Offline-first** - Works without internet
- ✅ **Instant-load** - Notes load in <100ms
- ✅ **Smooth scrolling** - RecyclerView with ViewHolder
- ✅ **Material Design 3** - Native Android UI
- ✅ **Kotlin Coroutines** - Asynchronous operations
- ✅ **Minimal APK size** - ~2 MB

---

## 🛠️ Technical Details

### Platform
- ✅ **Android 8.0+** (API 26+)
- ✅ **Target SDK 36** (Android 15)
- ✅ **Kotlin** - Modern programming language
- ✅ **Material Design 3** - Latest design guidelines
- ✅ **ViewBinding** - Type-safe view references

### Architecture
- ✅ **MVVM-Light** - Simple architecture
- ✅ **Single Activity** - Modern navigation
- ✅ **Kotlin Coroutines** - Async/Await pattern
- ✅ **Dispatchers.IO** - Background operations
- ✅ **SharedPreferences** - Settings storage
- ✅ **File-based storage** - JSON files locally

### Dependencies
- ✅ **AndroidX** - Jetpack libraries
- ✅ **Material Components** - Material Design 3
- ✅ **Sardine** - WebDAV client (com.thegrizzlylabs)
- ✅ **Gson** - JSON serialization
- ✅ **WorkManager** - Background tasks
- ✅ **OkHttp** - HTTP client (via Sardine)

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

Planned for upcoming versions (see [TODO.md](project-docs/simple-notes-sync/planning/TODO.md)):

### v1.3.0 - Web Editor & Organization
- ⏳ **Browser-based editor** - Edit notes in web browser
- ⏳ **WebDAV access via browser** - No mount needed
- ⏳ **Mobile-optimized** - Responsive design
- ⏳ **Offline-capable** - Progressive Web App (PWA)
- ⏳ **Tags/labels** - Categorize notes
- ⏳ **Search** - Full-text search in all notes
- ⏳ **Sorting** - By date, title, tags
- ⏳ **Filter** - Filter by tags

### v1.4.0 - Sharing & Export
- ⏳ **Share note** - Via share intent
- ⏳ **Export single note** - As .txt or .md
- ⏳ **Import from text** - Via share intent

### v1.5.0 - Advanced Editor Features
- ⏳ **Markdown preview** - In-app rendering
- ⏳ **Checklists** - TODO lists in notes
- ⏳ **Syntax highlighting** - For code snippets

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
| Open source | ✅ MIT | ❌ | ✅ AGPL |
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

**Last update:** v1.2.1 (2026-01-05)
