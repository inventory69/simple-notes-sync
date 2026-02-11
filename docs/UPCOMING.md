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

## v2.0.0 - Legacy Cleanup

> **Status:** Planned 📝

### 🗑️ Legacy Code Removal

- **Remove SettingsActivity** - Replaced by ComposeSettingsActivity
- **Remove MainActivity** - Replaced by ComposeMainActivity
- **LocalBroadcastManager → SharedFlow** - Modern event architecture
- **ProgressDialog → Material Dialog** - Full Material 3 compliance
- **AbstractSavedStateViewModelFactory → viewModelFactory** - Modern ViewModel creation

---

## 📋 Backlog

> Features for future consideration

### 🔐 Security Enhancements

- **Password-protected local backups** - Encrypt backup ZIP with password
- **Biometric unlock option** - Fingerprint/Face unlock for app

### 🎨 UI Features

- **Categories/Tags** - Organize notes
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
