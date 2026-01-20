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

## v1.7.0 - Staggered Grid Layout

> **Status:** Planned 📝

### 🎨 Adaptive Layout

- **Staggered Grid** - Pinterest-style layout using `LazyVerticalStaggeredGrid`
- **Smart sizing** - Small notes (short text, few checklist items) displayed compactly
- **Layout toggle** - Switch between List and Grid view in settings
- **Adaptive columns** - 2-3 columns based on screen size
- **120 FPS optimized** - Lazy loading for smooth scrolling with many notes

### 🔧 Server Folder Check

- **WebDAV folder check** - Checks if folder exists and is writable on server
- **Better error messages** - Helpful hints for server problems
- **Connection test improvement** - Checks read/write permissions

### 🔧 Technical Improvements

- **Code refactoring** - Split LargeClass components (WebDavSyncService, SettingsActivity)
- **Improved progress dialogs** - Material Design 3 compliant

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

- **Widget** - Quick access from homescreen
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
