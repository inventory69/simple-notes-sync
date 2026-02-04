# Changelog

All notable changes to Simple Notes Sync will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

**🌍 Languages:** [Deutsch](CHANGELOG.de.md) · **English**

---

## [1.7.2] - 2026-02-04

### 🐛 Critical Bug Fixes

#### JSON/Markdown Timestamp Sync

**Problem:** External editors (Obsidian, Typora, VS Code, custom editors) update Markdown content but don't update YAML `updated:` timestamp, causing the Android app to skip changes.

**Solution:**
- Server file modification time (`mtime`) is now used as source of truth instead of YAML timestamp
- Content changes detected via hash comparison
- Notes marked as `PENDING` after Markdown import → JSON automatically re-uploaded on next sync
- Fixes sorting issues after external edits

#### SyncStatus on Server Always PENDING

**Problem:** All JSON files on server contained `"syncStatus": "PENDING"` even after successful sync, confusing external clients.

**Solution:**
- Status is now set to `SYNCED` **before** JSON serialization
- Server and local copies are now consistent
- External web/Tauri editors can correctly interpret sync state

#### Deletion Tracker Race Condition

**Problem:** Batch deletes could lose deletion records due to concurrent file access.

**Solution:**
- Mutex-based synchronization for deletion tracking
- New `trackDeletionSafe()` function prevents race conditions
- Guarantees zombie note prevention even with rapid deletes

#### ISO8601 Timezone Parsing

**Problem:** Markdown imports failed with timezone offsets like `+01:00` or `-05:00`.

**Solution:**
- Multi-format ISO8601 parser with fallback chain
- Supports UTC (Z), timezone offsets (+01:00, +0100), and milliseconds
- Compatible with Obsidian, Typora, VS Code timestamps

### ⚡ Performance Improvements

#### E-Tag Batch Caching

- E-Tags are now written in single batch operation instead of N individual writes
- Performance gain: ~50-100ms per sync with multiple notes
- Reduced disk I/O operations

#### Memory Leak Prevention

- `SafeSardineWrapper` now implements `Closeable` for explicit resource cleanup
- HTTP connection pool is properly evicted after sync
- Prevents socket exhaustion during frequent syncs

### 🔧 Technical Details

- **IMPL_001:** `kotlinx.coroutines.sync.Mutex` for thread-safe deletion tracking
- **IMPL_002:** Pattern-based ISO8601 parser with 8 format variants
- **IMPL_003:** Connection pool eviction + dispatcher shutdown in `close()`
- **IMPL_004:** Batch `SharedPreferences.Editor` updates
- **IMPL_014:** Server `mtime` parameter in `Note.fromMarkdown()`
- **IMPL_015:** `syncStatus` set before `toJson()` call

### 📚 Documentation

- External Editor Specification for web/Tauri editor developers
- Detailed implementation documentation for all bugfixes

---

## [1.7.1] - 2026-02-02

### 🐛 Critical Bug Fixes

#### Android 9 App Crash Fix ([#15](https://github.com/inventory69/simple-notes-sync/issues/15))

**Problem:** App crashed on Android 9 (API 28) when using WorkManager Expedited Work for background sync.

**Root Cause:** When `setExpedited()` is used in WorkManager, the `CoroutineWorker` must implement `getForegroundInfo()` to return a Foreground Service notification. On Android 9-11, WorkManager calls this method, but the default implementation throws `IllegalStateException: Not implemented`.

**Solution:** Implemented `getForegroundInfo()` in `SyncWorker` to return a proper `ForegroundInfo` with sync progress notification.

**Details:**
- Added `ForegroundInfo` with sync progress notification for Android 9-11
- Android 10+: Sets `FOREGROUND_SERVICE_TYPE_DATA_SYNC` for proper service typing
- Added Foreground Service permissions to AndroidManifest.xml
- Notification shows sync progress with indeterminate progress bar
- Thanks to [@roughnecks](https://github.com/roughnecks) for the detailed debugging!

#### VPN Compatibility Fix ([#11](https://github.com/inventory69/simple-notes-sync/issues/11))

- WiFi socket binding now correctly detects Wireguard VPN interfaces (tun*, wg*, *-wg-*)
- Traffic routes through VPN tunnel instead of bypassing it directly to WiFi
- Fixes "Connection timeout" when syncing to external servers via VPN

### 🔧 Technical Changes

- New `SafeSardineWrapper` class ensures proper HTTP connection cleanup
- Reduced unnecessary 401 authentication challenges with preemptive auth headers
- Added ProGuard rule to suppress harmless TextInclusionStrategy warnings on older Android versions
- VPN interface detection via `NetworkInterface.getNetworkInterfaces()` pattern matching
- Foreground Service detection and notification system for background sync tasks

### 🌍 Localization

- Fixed hardcoded German error messages - now uses string resources for proper localization
- Added German and English strings for sync progress notifications

---

## [1.7.0] - 2026-01-26

### 🎉 Major: Grid View, WiFi-Only Sync & VPN Support

Pinterest-style grid, WiFi-only sync mode, and proper VPN support!

### 🎨 Grid Layout

- Pinterest-style staggered grid without gaps
- Consistent 12dp spacing between cards
- Scroll position preserved when returning from settings
- New unified `NoteCardGrid` with dynamic preview lines (3 small, 6 large)

### 📡 Sync Improvements

- **WiFi-only sync toggle** - Sync only when connected to WiFi
- **VPN support** - Sync works correctly when VPN is active (traffic routes through VPN)
- **Server change detection** - All notes reset to PENDING when server URL changes
- **Faster server check** - Socket timeout reduced from 2s to 1s
- **"Sync already running" feedback** - Shows snackbar when sync is in progress

### 🔒 Self-Signed SSL Support

- **Documentation added** - Guide for using self-signed certificates
- Uses Android's built-in CA trust store
- Works with ownCloud, Nextcloud, Synology, home servers

### 🔧 Technical

- `NoteCardGrid` component with dynamic maxLines
- Removed FullLine spans for gapless layout
- `resetAllSyncStatusToPending()` in NotesStorage
- VPN detection in `getOrCacheWiFiAddress()`

---

## [1.6.1] - 2026-01-20

### 🧹 Code Quality & Build Improvements

- **detekt: 0 issues** - All 29 code quality issues resolved
  - Trivial fixes: Unused imports, MaxLineLength
  - File rename: DragDropState.kt → DragDropListState.kt
  - MagicNumbers → Constants (Dimensions.kt, SyncConstants.kt)
  - SwallowedException: Logger.w() added for better error tracking
  - LongParameterList: ChecklistEditorCallbacks data class created
  - LongMethod: ServerSettingsScreen split into components
  - @Suppress annotations for legacy code (WebDavSyncService, SettingsActivity)

- **Zero build warnings** - All 21 deprecation warnings eliminated
  - File-level @Suppress for deprecated imports
  - ProgressDialog, LocalBroadcastManager, AbstractSavedStateViewModelFactory
  - onActivityResult, onRequestPermissionsResult
  - Gradle Compose config cleaned up (StrongSkipping is now default)

- **ktlint reactivated** - Linting re-enabled with Compose-specific rules
  - .editorconfig created with Compose formatting rules
  - Legacy files excluded: WebDavSyncService.kt, build.gradle.kts
  - ignoreFailures=true for gradual migration

- **CI/CD improvements** - GitHub Actions lint checks integrated
  - detekt + ktlint + Android Lint run before build in pr-build-check.yml
  - Ensures code quality on every pull request

### 🔧 Technical Improvements

- **Constants refactoring** - Better code organization
  - ui/theme/Dimensions.kt: UI-related constants
  - utils/SyncConstants.kt: Sync operation constants

- **Preparation for v2.0.0** - Legacy code marked for removal
  - SettingsActivity and MainActivity (replaced by Compose versions)
  - All deprecated APIs documented with removal plan

---

## [1.6.0] - 2026-01-19

### 🎉 Major: Configurable Sync Triggers

Fine-grained control over when your notes sync - choose which triggers fit your workflow best!

### ⚙️ Sync Trigger System

- **Individual trigger control** - Enable/disable each sync trigger separately in settings
- **5 Independent Triggers:**
  - **onSave Sync** - Sync immediately after saving a note (5s throttle)
  - **onResume Sync** - Sync when app is opened (60s throttle)
  - **WiFi-Connect Sync** - Sync when WiFi is connected
  - **Periodic Sync** - Background sync every 15/30/60 minutes (configurable)
  - **Boot Sync** - Start background sync after device restart

- **Smart Defaults** - Only event-driven triggers active by default (onSave, onResume, WiFi-Connect)
- **Battery Optimized** - ~0.2%/day with defaults, up to ~1.0% with periodic sync enabled
- **Offline Mode UI** - Grayed-out sync toggles when no server configured
- **Dynamic Settings Subtitle** - Shows count of active triggers on main settings screen

### 🔧 Server Configuration Improvements

- **Offline Mode Toggle** - Disable all network features with one switch
- **Split Protocol & Host** - Protocol (http/https) shown as non-editable prefix
- **Clickable Settings Cards** - Full card clickable for better UX
- **Clickable Toggle Rows** - Click text/icon to toggle switches (not just the switch itself)

### 🐛 Bug Fixes

- **Fixed:** Missing 5th sync trigger (Boot) in main settings screen subtitle count
- **Various fixes** - UI improvements and stability enhancements

### 🔧 Technical Improvements

- **Reactive offline mode state** - StateFlow ensures UI updates correctly
- **Separated server config checks** - `hasServerConfig()` vs `isServerConfigured()` (offline-aware)
- **Improved constants** - All sync trigger keys and defaults in Constants.kt
- **Better code organization** - Settings screens refactored for clarity

### Looking Ahead

> 🚀 **v1.7.0** will bring server folder checking and additional community features.
> Feature requests welcome as [GitHub Issue](https://github.com/inventory69/simple-notes-sync/issues).

---

## [1.5.0] - 2026-01-15

### 🎉 Major: Jetpack Compose UI Redesign

The complete UI has been migrated from XML Views to Jetpack Compose. The app is now more modern, faster, and smoother.

### 🌍 New Feature: Internationalization (i18n)

- **English language support** - All 400+ strings translated
- **Automatic language detection** - Follows system language
- **Manual language selection** - Switchable in settings
- **Per-App Language (Android 13+)** - Native language setting via system settings
- **locales_config.xml** - Complete Android integration

### ⚙️ Modernized Settings

- **7 categorized settings screens** - Clearer and more intuitive
- **Compose Navigation** - Smooth transitions between screens
- **Consistent design** - Material Design 3 throughout

### ✨ UI Improvements

- **Selection Mode** - Long-press for multi-select instead of swipe-to-delete
- **Batch Delete** - Delete multiple notes at once
- **Silent-Sync Mode** - No banner during auto-sync (only for manual sync)
- **App Icon in About Screen** - High-quality display
- **App Icon in Empty State** - Instead of emoji when note list is empty
- **Splash Screen Update** - Uses app foreground icon
- **Slide Animations** - Smooth animations in NoteEditor

### 🔧 Technical Improvements

- **Jetpack Compose** - Complete UI migration
- **Compose ViewModel Integration** - StateFlow for reactive UI
- **Improved Code Quality** - Detekt/Lint warnings fixed
- **Unused Imports Cleanup** - Cleaner codebase

### Looking Ahead

> 🚀 **v1.6.0** will bring server folder checking and further technical modernizations.
> Feature requests welcome as [GitHub Issue](https://github.com/inventory69/simple-notes-sync/issues).

---

## [1.4.1] - 2026-01-11

### Fixed

- **🗑️ Deleting older notes (v1.2.0 compatibility)**
  - Notes from app version v1.2.0 or earlier are now correctly deleted from the server
  - Fixes issue with multi-device usage with older notes

- **🔄 Checklist sync backward compatibility**
  - Checklists now also saved as text fallback in the `content` field
  - Older app versions (v1.3.x) display checklists as readable text
  - Format: GitHub-style task lists (`[ ] Item` / `[x] Item`)
  - Recovery mode: If checklist items are lost, they are recovered from content

### Improved

- **📝 Checklist auto line-wrap**
  - Long checklist texts now automatically wrap
  - No more limit to 3 lines
  - Enter key still creates a new item

### Looking Ahead

> 🚀 **v1.5.0** will be the next major release. We're collecting ideas and feedback!
> Feature requests welcome as [GitHub Issue](https://github.com/inventory69/simple-notes-sync/issues).

---

## [1.4.0] - 2026-01-10

### 🎉 New Feature: Checklists

- **✅ Checklist Notes**
  - New note type: Checklists with tap-to-toggle items
  - Add items via dedicated input field with "+" button
  - Drag & drop reordering (long-press to activate)
  - Swipe-to-delete items
  - Visual distinction: Checked items get strikethrough styling
  - Type selector when creating new notes (Text or Checklist)

- **📝 Markdown Integration**
  - Checklists export as GitHub-style task lists (`- [ ]` / `- [x]`)
  - Compatible with Obsidian, Notion, and other Markdown editors
  - Full round-trip: Edit in Obsidian → Sync back to app
  - YAML frontmatter includes `type: checklist` for identification

### Fixed

- **� Markdown Parsing Robustness**
  - Fixed content extraction after title (was returning empty for some formats)
  - Now handles single newline after title (was requiring double newline)
  - Protection: Skips import if parsed content is empty but local has content

- **📂 Duplicate Filename Handling**
  - Notes with identical titles now get unique Markdown filenames
  - Format: `title_shortid.md` (e.g., `test_71540ca9.md`)
  - Prevents data loss from filename collisions

- **🔔 Notification UX**
  - No sync notifications when app is in foreground
  - User sees changes directly in UI - no redundant notification
  - Background syncs still show notifications as expected

### Privacy Improvements

- **🔒 WiFi Permissions Removed**
  - Removed `ACCESS_WIFI_STATE` permission
  - Removed `CHANGE_WIFI_STATE` permission
  - WiFi binding now works via IP detection instead of SSID matching
  - Cleaned up all SSID-related code from codebase and documentation

### Technical Improvements

- **📦 New Data Model**
  - `NoteType` enum: `TEXT`, `CHECKLIST`
  - `ChecklistItem` data class with id, text, isChecked, order
  - `Note.kt` extended with `noteType` and `checklistItems` fields

- **🔄 Sync Protocol v1.4.0**
  - JSON format updated to include checklist fields
  - Full backward compatibility with v1.3.x notes
  - Robust JSON parsing with manual field extraction

---

## [1.3.2] - 2026-01-10

### Changed
- **🧹 Code-Qualität: "Clean Slate" Release**
  - Alle einfachen Lint-Issues behoben (Phase 1-7 des Cleanup-Plans)
  - Unused Imports und Members entfernt
  - Magic Numbers durch benannte Konstanten ersetzt
  - SwallowedExceptions mit Logger.w() versehen
  - MaxLineLength-Verstöße reformatiert
  - ConstructorParameterNaming (snake_case → camelCase mit @SerializedName)
  - Custom Exceptions: SyncException.kt und ValidationException.kt erstellt

### Added  
- **📝 F-Droid Privacy Notice**
  - Datenschutz-Hinweis für die Datei-Logging-Funktion
  - Erklärt dass Logs nur lokal gespeichert werden
  - Erfüllt F-Droid Opt-in Consent-Anforderungen

### Technical Improvements
- **⚡ Neue Konstanten für bessere Wartbarkeit**
  - `SYNC_COMPLETED_DELAY_MS`, `ERROR_DISPLAY_DELAY_MS` (MainActivity)
  - `CONNECTION_TIMEOUT_MS` (SettingsActivity)
  - `SOCKET_TIMEOUT_MS`, `MAX_FILENAME_LENGTH`, `ETAG_PREVIEW_LENGTH` (WebDavSyncService)
  - `AUTO_CANCEL_TIMEOUT_MS` (NotificationHelper)
  - RFC 1918 IP-Range Konstanten (UrlValidator)
  - `DAYS_THRESHOLD`, `TRUNCATE_SUFFIX_LENGTH` (Extensions)

- **🔒 @Suppress Annotations für legitime Patterns**
  - ReturnCount: Frühe Returns für Validierung sind idiomatisch
  - LoopWithTooManyJumpStatements: Komplexe Sync-Logik dokumentiert

### Notes
- Komplexe Refactorings (LargeClass, LongMethod) für v1.3.3+ geplant
- Deprecation-Warnungen (LocalBroadcastManager, ProgressDialog) bleiben bestehen

---

## [1.3.1] - 2026-01-08

### Fixed
- **🔧 Multi-Device JSON Sync (Danke an Thomas aus Bielefeld)**
  - JSON-Dateien werden jetzt korrekt zwischen Geräten synchronisiert
  - Funktioniert auch ohne aktiviertes Markdown
  - Hybrid-Optimierung: Server-Timestamp (Primary) + E-Tag (Secondary) Checks
  - E-Tag wird nach Upload gecached um Re-Download zu vermeiden

### Performance Improvements
- **⚡ JSON Sync Performance-Parität**
  - JSON-Sync erreicht jetzt gleiche Performance wie Markdown (~2-3 Sekunden)
  - Timestamp-basierte Skip-Logik für unveränderte Dateien (~500ms pro Datei gespart)
  - E-Tag-Matching als Fallback für Dateien die seit letztem Sync modifiziert wurden
  - **Beispiel:** 24 Dateien von 12-14s auf ~2.7s reduziert (keine Änderungen)

- **⏭️ Skip unveränderte Dateien** (Haupt-Performance-Fix!)
  - JSON-Dateien: Überspringt alle Notizen, die seit letztem Sync nicht geändert wurden
  - Markdown-Dateien: Überspringt unveränderte MD-Dateien basierend auf Server-Timestamp
  - **Spart ~500ms pro Datei** bei Nextcloud (~20 Dateien = 10 Sekunden gespart!)
  - Von 21 Sekunden Sync-Zeit auf 2-3 Sekunden reduziert

- **⚡ Session-Caching für WebDAV** 
  - Sardine-Client wird pro Sync-Session wiederverwendet (~600ms gespart)
  - WiFi-IP-Adresse wird gecacht statt bei jeder Anfrage neu ermittelt (~300ms gespart)
  - `/notes/` Ordner-Existenz wird nur einmal pro Sync geprüft (~500ms gespart)
  - **Gesamt: ~1.4 Sekunden zusätzlich gespart**

- **📝 Content-basierte Markdown-Erkennung**
  - Extern bearbeitete Markdown-Dateien werden auch erkannt wenn YAML-Timestamp nicht aktualisiert wurde
  - Löst das Problem: Obsidian/Texteditor-Änderungen wurden nicht importiert
  - Hybridansatz: Erst Timestamp-Check (schnell), dann Content-Vergleich (zuverlässig)

### Added
- **🔄 Sync-Status-Anzeige (UI)**
  - Sichtbares Banner "Synchronisiere..." mit ProgressBar während Sync läuft
  - Sync-Button und Pull-to-Refresh werden deaktiviert während Sync aktiv
  - Verhindert versehentliche Doppel-Syncs durch visuelle Rückmeldung
  - Auch in Einstellungen: "Jetzt synchronisieren" Button wird deaktiviert

### Fixed
- **🔧 Sync-Mutex verhindert doppelte Syncs**
  - Keine doppelten Toast-Nachrichten mehr bei schnellem Pull-to-Refresh
  - Concurrent Sync-Requests werden korrekt blockiert

- **🐛 Lint-Fehler behoben**
  - `View.generateViewId()` statt hardcodierte IDs in RadioButtons
  - `app:tint` statt `android:tint` für AppCompat-Kompatibilität

### Added
- **🔍 detekt Code-Analyse**
  - Statische Code-Analyse mit detekt 1.23.4 integriert
  - Pragmatische Konfiguration für Sync-intensive Codebasis
  - 91 Issues identifiziert (als Baseline für v1.4.0)

- **🏗️ Debug Build mit separatem Package**
  - Debug-APK kann parallel zur Release-Version installiert werden
  - Package: `dev.dettmer.simplenotes.debug` (Debug) vs `dev.dettmer.simplenotes` (Release)
  - App-Name zeigt "Simple Notes (Debug)" für einfache Unterscheidung

- **📊 Debug-Logging UI**
  - Neuer "Debug Log" Button in Einstellungen → Erweitert
  - Zeigt letzte Sync-Logs mit Zeitstempeln
  - Export-Funktion für Fehlerberichte

### Technical
- `WebDavSyncService`: Hybrid-Optimierung für JSON-Downloads (Timestamp PRIMARY, E-Tag SECONDARY)
- `WebDavSyncService`: E-Tag refresh nach Upload statt Invalidierung (verhindert Re-Download)
- E-Tag Caching: `SharedPreferences` mit Key-Pattern `etag_json_{noteId}`
- Skip-Logik: `if (serverModified <= lastSync) skip` → ~1ms pro Datei
- Fallback E-Tag: `if (serverETag == cachedETag) skip` → für Dateien modifiziert nach lastSync
- PROPFIND nach PUT: Fetch E-Tag nach Upload für korrektes Caching
- `SyncStateManager`: Neuer Singleton mit `StateFlow<Boolean>` für Sync-Status
- `MainActivity`: Observer auf `SyncStateManager.isSyncing` für UI-Updates
- Layout: `sync_status_banner` mit `ProgressBar` + `TextView`
- `WebDavSyncService`: Skip-Logik für unveränderte JSON/MD Dateien basierend auf `lastSyncTimestamp`
- `WebDavSyncService`: Neue Session-Cache-Variablen (`sessionSardine`, `sessionWifiAddress`, `notesDirEnsured`)
- `getOrCreateSardine()`: Cached Sardine-Client mit automatischer Credentials-Konfiguration
- `getOrCacheWiFiAddress()`: WiFi-Adresse wird nur einmal pro Sync ermittelt
- `clearSessionCache()`: Aufräumen am Ende jeder Sync-Session
- `ensureNotesDirectoryExists()`: Cached Directory-Check
- Content-basierter Import: Vergleicht MD-Content mit lokaler Note wenn Timestamps gleich
- Build-Tooling: detekt aktiviert, ktlint vorbereitet (deaktiviert wegen Parser-Problemen)
- Debug BuildType: `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-debug"`

---

## [1.3.0] - 2026-01-07

### Added
- **🚀 Multi-Device Sync** (Thanks to Thomas from Bielefeld for reporting!)
  - Automatic download of new notes from other devices
  - Deletion tracking prevents "zombie notes" (deleted notes don't come back)
  - Smart cleanup: Re-created notes (newer timestamp) are downloaded
  - Works with all devices: v1.2.0, v1.2.1, v1.2.2, and v1.3.0

- **🗑️ Server Deletion via Swipe Gesture**
  - Swipe left on notes to delete from server (requires confirmation)
  - Prevents duplicate notes on other devices
  - Works with deletion tracking system
  - Material Design confirmation dialog

- **⚡ E-Tag Performance Optimization**
  - Smart server checking with E-Tag caching (~150ms vs 3000ms for "no changes")
  - 20x faster when server has no updates
  - E-Tag hybrid approach: E-Tag for JSON (fast), timestamp for Markdown (reliable)
  - Battery-friendly with minimal server requests

- **📥 Markdown Auto-Sync Toggle**
  - NEW: Unified Auto-Sync toggle in Settings (replaces separate Export/Auto-Import toggles)
  - When enabled: Notes export to Markdown AND import changes automatically
  - When disabled: Manual sync button appears for on-demand synchronization
  - Performance: Auto-Sync OFF = 0ms overhead

- **🔘 Manual Markdown Sync Button**
  - Manual sync button for performance-conscious users
  - Shows import/export counts after completion
  - Only visible when Auto-Sync is disabled
  - On-demand synchronization (~150-200ms only when triggered)

- **⚙️ Server-Restore Modes**
  - MERGE: Keep local notes + add server notes
  - REPLACE: Delete all local + download from server
  - OVERWRITE: Update duplicates, keep non-duplicates
  - Restore modes now work correctly for WebDAV restore

### Technical
- New `DeletionTracker` model with JSON persistence
- `NotesStorage`: Added deletion tracking methods
- `WebDavSyncService.hasUnsyncedChanges()`: Intelligent server checks with E-Tag caching
- `WebDavSyncService.downloadRemoteNotes()`: Deletion-aware downloads
- `WebDavSyncService.restoreFromServer()`: Support for restore modes
- `WebDavSyncService.deleteNoteFromServer()`: Server deletion with YAML frontmatter scanning
- `WebDavSyncService.importMarkdownFiles()`: Automatic Markdown import during sync
- `WebDavSyncService.manualMarkdownSync()`: Manual sync with result counts
- `MainActivity.setupSwipeToDelete()`: Two-stage swipe deletion with confirmation
- E-Tag caching in SharedPreferences for performance

---

## [1.2.2] - 2026-01-06

### Fixed
- **Backward Compatibility for v1.2.0 Users (Critical)**
  - App now reads BOTH old (Root) AND new (`/notes/`) folder structures
  - Users upgrading from v1.2.0 no longer lose their existing notes
  - Server-Restore now finds notes from v1.2.0 stored in Root folder
  - Automatic deduplication prevents loading the same note twice
  - Graceful error handling if Root folder is not accessible

### Technical
- `WebDavSyncService.downloadRemoteNotes()` - Dual-mode download (Root + /notes/)
- `WebDavSyncService.restoreFromServer()` - Now uses dual-mode download
- Migration happens naturally: new uploads go to `/notes/`, old notes stay readable

---

## [1.2.1] - 2026-01-05

### Fixed
- **Markdown Initial Export Bugfix**
  - Existing notes are now exported as Markdown when Desktop Integration is activated
  - Previously, only new notes created after activation were exported
  - Progress dialog shows export status with current/total counter
  - Error handling for network issues during export
  - Individual note failures don't abort the entire export

- **Markdown Directory Structure Fix**
  - Markdown files now correctly land in `/notes-md/` folder
  - Smart URL detection supports both Root-URL and `/notes` URL structures
  - Previously, MD files were incorrectly placed in the root directory
  - Markdown import now finds files correctly

- **JSON URL Normalization**
  - Simplified server configuration: enter only base URL (e.g., `http://server:8080/`)
  - App automatically creates `/notes/` for JSON files and `/notes-md/` for Markdown
  - Smart detection: both `http://server:8080/` and `http://server:8080/notes/` work correctly
  - Backward compatible: existing setups with `/notes` in URL continue to work
  - No migration required for existing users

### Changed
- **Markdown Directory Creation**
  - `notes-md/` folder is now created on first sync (regardless of Desktop Integration setting)
  - Prevents 404 errors when mounting WebDAV folder
  - Better user experience: folder is visible before enabling the feature

- **Settings UI Improvements**
  - Updated example URL from `/webdav` to `/notes` to match app behavior
  - Example now shows: `http://192.168.0.188:8080/notes`

### Technical
- `WebDavSyncService.ensureMarkdownDirectoryExists()` - Creates MD folder early
- `WebDavSyncService.getMarkdownUrl()` - Smart URL detection for both structures
- `WebDavSyncService.exportAllNotesToMarkdown()` - Exports all local notes with progress callback
- `SettingsActivity.onMarkdownExportToggled()` - Triggers initial export with ProgressDialog

---

## [1.2.0] - 2026-01-04

### Added
- **Local Backup System**
  - Export all notes as JSON file to any location (Downloads, SD card, cloud folder)
  - Import backup with 3 modes: Merge, Replace, or Overwrite duplicates
  - Automatic safety backup created before every restore
  - Backup validation (format and version check)
  
- **Markdown Desktop Integration**
  - Optional Markdown export parallel to JSON sync
  - `.md` files synced to `notes-md/` folder on WebDAV
  - YAML frontmatter with `id`, `created`, `updated`, `device`
  - Manual import button to pull Markdown changes from server
  - Last-Write-Wins conflict resolution via timestamps
  
- **Settings UI Extensions**
  - New "Backup & Restore" section with local + server restore
  - New "Desktop Integration" section with Markdown toggle
  - Universal restore dialog with radio button mode selection

### Changed
- **Server Restore Behavior**: Users now choose restore mode (Merge/Replace/Overwrite) instead of hard-coded replace-all

### Technical
- `BackupManager.kt` - Complete backup/restore logic
- `Note.toMarkdown()` / `Note.fromMarkdown()` - Markdown conversion with YAML frontmatter
- `WebDavSyncService` - Extended for dual-format sync (JSON master + Markdown mirror)
- ISO8601 timestamp formatting for desktop compatibility
- Filename sanitization for safe Markdown file names

### Documentation
- Added WebDAV mount instructions (Windows, macOS, Linux)
- Complete sync architecture documentation
- Desktop integration analysis

---

## [1.1.2] - 2025-12-28

### Fixed
- **"Job was cancelled" Error**
  - Fixed coroutine cancellation in sync worker
  - Proper error handling for interrupted syncs
  
- **UI Improvements**
  - Back arrow instead of X in note editor (better UX)
  - Pull-to-refresh for manual sync trigger
  - HTTP/HTTPS protocol selection with radio buttons
  - Inline error display (no toast spam)
  
- **Performance & Battery**
  - Sync only on actual changes (saves battery)
  - Auto-save notifications removed
  - 24-hour server offline warning instead of instant error
  
### Changed
- Settings grouped into "Auto-Sync" and "Sync Interval" sections
- HTTP only allowed for local networks (RFC 1918 IPs)
- Swipe-to-delete without UI flicker

---

## [1.1.1] - 2025-12-27

### Fixed
- **WiFi Connect Sync**
  - No error notifications in foreign WiFi networks
  - Server reachability check before sync (2s timeout)
  - Silent abort when server offline
  - Pre-check waits until network is ready
  - No errors during network initialization
  
### Changed
- **Notifications**
  - Old sync notifications cleared on app start
  - Error notifications auto-dismiss after 30 seconds
  
### UI
- Sync icon only shown when sync is configured
- Swipe-to-delete without flicker
- Scroll to top after saving note

### Technical
- Server check with 2-second timeout before sync attempts
- Network readiness check in WiFi connect trigger
- Notification cleanup on MainActivity.onCreate()

---

## [1.1.0] - 2025-12-26

### Added
- **Configurable Sync Intervals**
  - User choice: 15, 30, or 60 minutes
  - Real-world battery impact displayed (15min: ~0.8%/day, 30min: ~0.4%/day, 60min: ~0.2%/day)
  - Radio button selection in settings
  - Doze Mode optimization (syncs batched in maintenance windows)
  
- **About Section**
  - App version from BuildConfig
  - Links to GitHub repository and developer profile
  - MIT license information
  - Material 3 card design

### Changed
- Settings UI redesigned with grouped sections
- Periodic sync updated dynamically when interval changes
- WorkManager uses selected interval for background sync

### Removed
- Debug/Logs section from settings (cleaner UI)

### Technical
- `PREF_SYNC_INTERVAL_MINUTES` preference key
- NetworkMonitor reads interval from SharedPreferences
- `ExistingPeriodicWorkPolicy.UPDATE` for live interval changes

---

## [1.0.0] - 2025-12-25

### Added
- Initial release
- WebDAV synchronization
- Note creation, editing, deletion
- 6 sync triggers:
  - Periodic sync (configurable interval)
  - App start sync
  - WiFi connect sync
  - Manual sync (menu button)
  - Pull-to-refresh
  - Settings "Sync Now" button
- Material 3 design
- Light/Dark theme support
- F-Droid compatible (100% FOSS)

---

[1.2.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.2.0
[1.1.2]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.1.2
[1.1.1]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.1.1
[1.1.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.1.0
[1.0.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.0.0
