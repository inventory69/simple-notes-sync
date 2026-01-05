# Changelog

All notable changes to Simple Notes Sync will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
- Created [SYNC_ARCHITECTURE.md](../project-docs/simple-notes-sync/architecture/SYNC_ARCHITECTURE.md) - Complete sync documentation
- Created [MARKDOWN_DESKTOP_REALITY_CHECK.md](../project-docs/simple-notes-sync/markdown-desktop-plan/MARKDOWN_DESKTOP_REALITY_CHECK.md) - Desktop integration analysis

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
