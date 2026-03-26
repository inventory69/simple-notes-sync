# Upcoming Features 🚀

**🌍 Languages:** [Deutsch](UPCOMING.de.md) · **English**

> What's next? Here you'll find our plans for future versions.

---

## v1.5.0 - Jetpack Compose & Internationalization ✅

> **Status:** Released 🎉 (January 2026)

### 🎨 Jetpack Compose UI

- ✅ **Complete UI redesign** - From XML views to Jetpack Compose
- ✅ **Modernized settings** - 7 categorized screens
- ✅ **Selection Mode** - Long-press for multi-select
- ✅ **Silent-Sync Mode** - No banner during auto-sync

### 🌍 Multi-Language Support

- ✅ **English + German** - 400+ translated strings
- ✅ **Automatic language detection** - Follows system language
- ✅ **Per-App Language (Android 13+)** - Native language selection

### 🎨 UI Improvements

- ✅ **Splash screen** - App foreground icon
- ✅ **App icon** - In About screen and empty state
- ✅ **Slide animations** - Smooth transitions in NoteEditor

---

## v1.6.0 - Technical Modernization ✅

> **Status:** Released 🎉 (January 2026)

### ⚙️ Configurable Sync Triggers

- ✅ **Individual trigger control** - Enable/disable each sync trigger separately
- ✅ **Event-driven defaults** - onSave, onResume, WiFi-Connect active by default
- ✅ **Periodic sync optional** - 15/30/60 min intervals (default: OFF)
- ✅ **Boot sync optional** - Start periodic sync after device restart (default: OFF)
- ✅ **Offline mode UI** - Grayed-out toggles when no server configured
- ✅ **Battery optimized** - ~0.2%/day with defaults, up to ~1.0% with periodic

---

## v1.6.1 - Clean Code ✅

> **Status:** Released 🎉 (January 2026)

### 🧹 Code Quality

- ✅ **detekt: 0 issues** - All 29 code quality issues fixed
- ✅ **Zero build warnings** - All 21 deprecation warnings eliminated
- ✅ **ktlint reactivated** - With Compose-specific rules
- ✅ **CI/CD lint checks** - Integrated into PR build workflow
- ✅ **Constants refactoring** - Dimensions.kt, SyncConstants.kt

---

## v1.7.0 - Grid View, WiFi-Only & VPN ✅

> **Status:** Released 🎉 (January 2026)

### 🎨 Grid Layout

- ✅ **Pinterest-style staggered grid** - Gapless layout with dynamic preview lines
- ✅ **Layout toggle** - Switch between list and grid in settings
- ✅ **Adaptive columns** - 2-3 columns based on screen size

### 📡 Sync Improvements

- ✅ **WiFi-only sync toggle** - Sync only when connected to WiFi
- ✅ **VPN support** - Sync works correctly through VPN tunnels
- ✅ **Self-signed SSL** - Documentation and support for self-signed certificates
- ✅ **Server change detection** - All notes reset to PENDING when server URL changes

---

## v1.7.1 - Android 9 Fix & VPN ✅

> **Status:** Released 🎉 (February 2026)

- ✅ **Android 9 crash fix** - Implemented `getForegroundInfo()` for WorkManager on API 28
- ✅ **VPN compatibility** - WiFi socket binding detects Wireguard VPN interfaces
- ✅ **SafeSardineWrapper** - Proper HTTP connection cleanup

---

## v1.7.2 - Timestamp & Deletion Fixes ✅

> **Status:** Released 🎉 (February 2026)

- ✅ **Server mtime as source of truth** - Fixes external editor timestamp issues
- ✅ **Deletion tracker mutex** - Thread-safe batch deletes
- ✅ **ISO8601 timezone parsing** - Multi-format support
- ✅ **E-Tag batch caching** - Performance improvement
- ✅ **Memory leak prevention** - SafeSardineWrapper with Closeable

---

## v1.8.0 - Widgets, Sorting & Advanced Sync ✅

> **Status:** Released 🎉 (February 2026)

### 📌 Homescreen Widgets

- ✅ **Full Jetpack Glance framework** - 5 responsive size classes
- ✅ **Interactive checklists** - Checkboxes that sync to server
- ✅ **Material You colors** - Dynamic colors with configurable opacity
- ✅ **Lock toggle** - Prevent accidental edits
- ✅ **Configuration activity** - Note selection and settings

### 📊 Sorting

- ✅ **Note sorting** - By title, date modified, date created, type
- ✅ **Checklist sorting** - Manual, alphabetical, unchecked first, checked last
- ✅ **Visual separators** - Between unchecked/checked groups
- ✅ **Drag across boundaries** - Auto-toggle state on cross-boundary drag

### 🔄 Sync Improvements

- ✅ **Parallel downloads** - Up to 5 simultaneous (configurable)
- ✅ **Server deletion detection** - Detects notes deleted on other clients
- ✅ **Live sync progress** - Phase indicators with counters
- ✅ **Sync status legend** - Help dialog explaining all sync icons

### ✨ UX

- ✅ **Post-update changelog** - Shows localized changelog on first launch after update
- ✅ **Grid as default** - New installations default to grid view
- ✅ **Toast → Banner migration** - Unified notification system

---

## v1.8.1 - Bugfix & Polish ✅

> **Status:** Released 🎉 (February 2026)

- ✅ **Checklist sort persistence** - Sort option correctly restored when reopening
- ✅ **Widget scroll fix** - Scroll works on standard 3×2 widget size
- ✅ **Widget checklist sorting** - Widgets apply saved sort option
- ✅ **Drag cross-boundary** - Drag & drop across checked/unchecked separator
- ✅ **Sync rate-limiting** - Global 30s cooldown between auto-syncs
- ✅ **Detekt: 0 issues** - All 12 findings resolved

---

## v1.8.2 - Stability & Editor Fixes ✅

> **Status:** Released 🎉 (February 2026)

- ✅ **26 bugfixes** - Sync deadlocks, data loss prevention, editor UX
- ✅ **Self-signed SSL support** - User CA certificates in release builds
- ✅ **Widget scroll fix** - Scrollable text in medium widgets
- ✅ **Keyboard auto-capitalization** - Title field, checklist items
- ✅ **APK size optimization** - Granular ProGuard rules (< 5 MB)
- ✅ **Checklist drag stability** - Cross-boundary drag & drop fix

---

## v1.9.0 - Filter, Search, Markdown & Widget Polish ✅

> **Status:** Released 🎉 (February 2026)

### Part 1: Sync Quality & Import
- ✅ **Notes Import Wizard** - Import from WebDAV or local (.md, .json, .txt)
- ✅ **Parallel uploads** - ~2× faster multi-note sync
- ✅ **Unified parallel connections** - Single setting for uploads & downloads
- ✅ **Server switch fix** - E-Tag/content-hash caches cleared on change
- ✅ **Deletion detection fix** - Threshold raised for small note portfolios
- ✅ **Markdown export serialization** - Mutex prevents race condition
- ✅ **E-Tag caching** - Skip redundant re-downloads

### Part 2: UI Features

#### 📊 Filter & Search
- ✅ **Filter Chip Row** - Filter by All / Text / Checklists
- ✅ **Inline search** - Quick search field in filter row
- ✅ **Sort in filter row** - Sort button moved from dialog to filter row
- ✅ **Filter row toggle** - Tune button in TopAppBar to show/hide

#### ✏️ Editor
- ✅ **Markdown preview** - Live preview for text notes with formatting toolbar
- ✅ **Checklist un-check restore** - Item returns to original position
- ✅ **Checklist order cementing** - Original order preserved after insert/delete
- ✅ **Checklist scroll behavior** - Consistent scrolling on check/un-check
- ✅ **Opt-in autosave** - Configurable debounce autosave timer
- ✅ **Configurable sync folder** - Custom WebDAV folder name

#### 📌 Widget Improvements
- ✅ **Monet tint preservation** - Translucent background keeps dynamic colors
- ✅ **Seamless options bar** - Removed background for cleaner look
- ✅ **Checklist strikethrough** - Completed items show strikethrough
- ✅ **onStop widget refresh** - Widgets update when leaving app

#### ✨ Other
- ✅ **Custom app title** - Configurable app name in settings
- ✅ **Scroll to top on sync** - List scrolls to top after manual sync

---

## v2.0.0 - Compose Rewrite & Multi-Theme ✅

> **Status:** Released 🎉 (March 2026)

### 🎨 Multi-Theme System
- ✅ **7 color schemes** - Including AMOLED & Dynamic Color with animated transitions and tinted surfaces
- ✅ **Grid column scaling** - 1–5 columns configurable in display settings
- ✅ **Grid chips** - Replace radio buttons in display settings

### ✨ Editor & Settings
- ✅ **Full backup/restore** - Includes all app settings, not just notes
- ✅ **Material 3 shared axis transitions** - For all navigation and back gestures
- ✅ **Autosave status** - Shown in display settings subtitle
- ✅ **Debug logging dialog** - Disable logging after export

### 🐛 Bug Fixes
- ✅ **Checklist drag-and-drop** - Rewritten for stability in long lists
- ✅ **Offline deletions** - Queued for next sync
- ✅ **WebDAV 403 compatibility** - HTTP 403 treated as existing
- ✅ **Thread-safety** - State inconsistency and dispatcher issues fixed
- ✅ **Resource leaks** - InputStreams closed, file I/O off main thread
- ✅ **Save-on-back race condition** - TextFieldState flush + onPause save

### 🗑️ Legacy Code Removal
- ✅ **SettingsActivity removed** - Replaced by Compose settings
- ✅ **MainActivity removed** - Replaced by ComposeMainActivity
- ✅ **NoteEditorActivity removed** - Replaced by Compose editor
- ✅ **XML layouts, menus, drawables removed** - Full Compose UI
- ✅ **LocalBroadcastManager → SharedFlow** - Modern event architecture
- ✅ **viewModelFactory DSL** - Modern ViewModel creation

### 🏗️ Architecture
- ✅ **WebDavSyncService → Facade pattern** - Split into 9 extracted modules
- ✅ **R8/ProGuard optimized** - Reduced APK size

### 📄 License
- ✅ **MIT → AGPL v3** - License changed

---

## v2.2.0 - Share Intent, Widgets & Editor Polish

> **Status:** Planned 📋

### 📤 Share Intent

- **Receive shared content** - Accept text and URLs from other apps via Android Share Intent ([Discussion #46](https://github.com/inventory69/simple-notes-sync/discussions/46) by [@madelgijs](https://github.com/madelgijs))
- **Create note from shared text** - Shared content creates a new note or appends to an existing one
- **URL handling** - URLs shared from browsers are formatted as clickable Markdown links

### 📌 New Note Shortcut Widget

- **One-tap new note** - Home screen widget that opens the editor immediately for a new note ([Discussion #49](https://github.com/inventory69/simple-notes-sync/discussions/49) by [@Stowaway2979](https://github.com/Stowaway2979))
- **Minimal footprint** - Small 1×1 widget with the app icon and a `+` badge
- **Instant typing** - Editor opens with keyboard focused on the title field

### ✏️ Editor Enhancements

- **Markdown checklist button** - New toolbar button to insert `- [ ]` / `- [x]` Markdown checkbox syntax, with toggle support for existing lines
- **Checklist item copy & duplicate** - Long-press context menu on checklist items: copy text to clipboard or duplicate item below

---

## 📋 Backlog

> Features for future consideration

### 🔐 Security Enhancements

- **Password-protected local backups** - Encrypt backup ZIP with password
- **Biometric unlock option** - Fingerprint/Face unlock for app

### 🎨 UI Features

- **Folders / Tags / Notebooks** - Organize notes into directories or with tags for better separation (e.g. personal vs. shared recipes). Subdirectories on WebDAV could also enable per-folder access control. ([#38](https://github.com/inventory69/simple-notes-sync/discussions/38) by @happy-turtle)
- **Hide completed checklists** - Option to hide checklists where all items are checked, with a separate view to recover them later. ([#45](https://github.com/inventory69/simple-notes-sync/discussions/45) by @isawaway)
- **Search** - Full-text search in notes

### 🌍 Community

- **Additional languages** - Community translations (FR, ES, IT, ...)

---

## 💡 Feedback & Suggestions

Have an idea for a new feature?

- **[Create a feature request](https://github.com/inventory69/simple-notes-sync/issues/new?template=feature_request.yml)**
- **[View existing requests](https://github.com/inventory69/simple-notes-sync/issues?q=is%3Aissue+label%3Aenhancement)**

---

**Note:** This roadmap shows our current plans. Priorities may change based on community feedback.

[← Back to documentation](DOCS.md)
