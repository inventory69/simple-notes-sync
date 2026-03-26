# Changelog

All notable changes to Simple Notes Sync will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

**🌍 Languages:** [Deutsch](CHANGELOG.de.md) · **English**

---

## [2.1.0] - 2026-03-26

### 🐛 Bug Fixes & UX Improvements

**Editor Toolbar Scaling for Narrow Displays** ([9b6ee8a](https://github.com/inventory69/simple-notes-sync/commit/9b6ee8a))
- Adaptive toolbar: on wide displays full titles + Undo/Redo in toolbar; on narrow displays / large font scale: shortened titles ("Edit", "New") + Undo/Redo in overflow menu
- Prevents text wrapping on small screens and high accessibility font scales
- Thanks to [@xdpirate](https://github.com/xdpirate) for reporting! ([#48](https://github.com/inventory69/simple-notes-sync/issues/48))

**Markdown Preview as Default View** ([f8b15a5](https://github.com/inventory69/simple-notes-sync/commit/f8b15a5))
- Existing text notes now open in Markdown preview mode by default
- New notes still open in edit mode with auto-keyboard focus
- Thanks to [@james0336](https://github.com/james0336), [@isawaway](https://github.com/isawaway), and MrsMinchen for the suggestion!

### 📝 Documentation & Metadata

**License Metadata Corrected** ([89667d1](https://github.com/inventory69/simple-notes-sync/commit/89667d1))
- Fixed remaining Apache 2.0 references in F-Droid changelogs (versionCode 27) — now all correctly show AGPL v3

**Issue Templates Simplified** ([5a56cf4](https://github.com/inventory69/simple-notes-sync/commit/5a56cf4))
- Bug report and question templates rewritten: English-only, fewer required fields, cleaner layout
- Thanks to [@xdpirate](https://github.com/xdpirate) for the feedback! ([#48](https://github.com/inventory69/simple-notes-sync/issues/48))

**Upcoming Features v2.2.0** ([5e9168b](https://github.com/inventory69/simple-notes-sync/commit/5e9168b))
- Added Share Intent ([Discussion #46](https://github.com/inventory69/simple-notes-sync/discussions/46) by [@madelgijs](https://github.com/madelgijs)), New Note Shortcut Widget ([Discussion #49](https://github.com/inventory69/simple-notes-sync/discussions/49) by [@Stowaway2979](https://github.com/Stowaway2979)), Markdown checklist button, and checklist item copy/duplicate to the roadmap

### 📦 Code Quality

**Detekt / Lint / ProGuard Audit** ([b9e6782](https://github.com/inventory69/simple-notes-sync/commit/b9e6782))
- Full code quality audit: detekt, lint, ktlint, ProGuard, and unit tests verified clean after all v2.1.0 changes

---

## [2.0.0] - 2026-03-20

### 🎨 Complete Compose Rewrite, Multi-Theme System & Architecture Overhaul

Major release: full migration to Jetpack Compose, removal of all legacy View-based code (~2,300 lines deleted), complete WebDavSyncService refactoring into focused modules, multi-theme system with 7 color schemes and animated transitions, Material 3 shared axis navigation, checklist drag-and-drop rewrite, comprehensive sync reliability fixes, and modernized dependencies.

### ✨ New Features

**Multi-Theme System with Animated Transitions and Tinted Surfaces** ([315c0a5](https://github.com/inventory69/simple-notes-sync/commit/315c0a5))
- New ThemeMode selector: System, Light, Dark, AMOLED
- 7 color schemes: Default, Blue, Green, Red, Purple, Orange, Dynamic (Material You on API 31+)
- Live preview with color swatches in Display Settings
- Crossfade theme transitions (500 ms) — smooth even in debug builds
- Tinted surface palettes for all color schemes (note cards match selected theme)
- Status bar and navigation bar colors sync at runtime on theme change

**Grid Column Scaling Control** ([3d4c2e0](https://github.com/inventory69/simple-notes-sync/commit/3d4c2e0))
- Toggle between automatic grid scaling (adaptive 150dp) and fixed column count
- Manual column count 1–5 via chip selector with mini-grid preview
- Section only visible when grid mode is active
- Included in backup/restore

**Undo/Redo Promoted to TopAppBar** ([75edf00](https://github.com/inventory69/simple-notes-sync/commit/75edf00))
- Undo and Redo moved from overflow menu to direct TopAppBar actions
- Buttons respect canUndo/canRedo state and appear dimmed when unavailable

**Display Mode Chip Selector** ([046f325](https://github.com/inventory69/simple-notes-sync/commit/046f325))
- Replace radio group with icon-above-label chips (consistent with theme/color selectors)
- Settings subtitle shows display mode, theme mode, and color theme (e.g. "List View · Dark · Default")

**Full App Settings Backup/Restore** ([4d07c11](https://github.com/inventory69/simple-notes-sync/commit/4d07c11))
- Backup/restore now includes all settings: server, sync, markdown, display, notes behaviour, notifications

**Autosave Status in Display Settings** ([92da701](https://github.com/inventory69/simple-notes-sync/commit/92da701))
- Display settings subtitle shows autosave status instead of theme info

**Disable-Logging-After-Export Dialog** ([2525a85](https://github.com/inventory69/simple-notes-sync/commit/2525a85))
- After sharing debug logs, a dialog asks whether to disable file logging

**Material 3 Shared Axis Transitions** ([3f5d19d](https://github.com/inventory69/simple-notes-sync/commit/3f5d19d), [365b0dd](https://github.com/inventory69/simple-notes-sync/commit/365b0dd))
- Horizontal shared axis (slide + fade) transitions for all navigation
- Consistent animations for both back-arrow and swipe-gesture on API 34+

### 🐛 Bug Fixes

**Checklist Drag-and-Drop Rewrite** ([89cc9a6](https://github.com/inventory69/simple-notes-sync/commit/89cc9a6))
- Complete rewrite of DragDropListState (~200 → 797 lines) fixing runaway auto-scroll, index desync during separator crossings, concurrent swap race conditions, and swap oscillation at viewport edges
- Key-based item tracking, continuous auto-scroll loop with mutex-locked swaps, anti-flapping guard, viewport-safety checks

**Offline Server Deletions Queued** ([1a5c889](https://github.com/inventory69/simple-notes-sync/commit/1a5c889))
- Notes deleted with "Delete everywhere" while server unreachable are now queued and processed on next sync instead of silently lost

**WebDAV 403 Compatibility** ([9523733](https://github.com/inventory69/simple-notes-sync/commit/9523733))
- Jianguoyun WebDAV returns 403 for HEAD on collections — now correctly treated as "exists" instead of "not found". Thanks [@james0336](https://github.com/james0336) for reporting!

**Thread-Safety and Resource Leaks** ([2ab04d1](https://github.com/inventory69/simple-notes-sync/commit/2ab04d1), [3c31f61](https://github.com/inventory69/simple-notes-sync/commit/3c31f61))
- Fix Activity leaks in NetworkMonitor, migrate syncStatus to StateFlow, add fileLock to Logger, eliminate race conditions in MainViewModel with StateFlow.update{}
- Close all InputStream/connection leaks in sardine calls, move file I/O off main thread, fix HttpURLConnection leak in server status check

**Sync Reliability** ([ec4bb1c](https://github.com/inventory69/simple-notes-sync/commit/ec4bb1c), [3d02118](https://github.com/inventory69/simple-notes-sync/commit/3d02118), [150543c](https://github.com/inventory69/simple-notes-sync/commit/150543c))
- Fix onResume sync throttle surviving process restarts (now in-memory)
- Prevent spurious sync after package update (race condition in NetworkMonitor)
- Skip auto-sync on resume from editor and settings

**Layout Scaling for Small Screens and Large Fonts** ([ef38a0e](https://github.com/inventory69/simple-notes-sync/commit/ef38a0e))
- Responsive fixes: scrollable dialogs, FilterChipRow column layout, adaptive grid threshold reduced 180→150dp. Danke an Mama <3

**Empty Plural Strings in de/tr/uk** ([2db80a4](https://github.com/inventory69/simple-notes-sync/commit/2db80a4))
- Fill missing plural forms for time-ago strings — timestamps were invisible on note cards in German, Turkish, Ukrainian

**Language Selector on API 33+** ([f54ef49](https://github.com/inventory69/simple-notes-sync/commit/f54ef49))
- Redirect to native Per-App Language Settings on API 33+ instead of in-app selector (eliminates activity-recreate flash)

**Save-on-Back Race Condition** ([afbef19](https://github.com/inventory69/simple-notes-sync/commit/afbef19))
- Flush TextFieldState and save in onPause to prevent data loss on back navigation

**Banner Color Flash** ([9b7a285](https://github.com/inventory69/simple-notes-sync/commit/9b7a285))
- Freeze last visible banner colors during dismiss animation to prevent wrong-color flash

**Splash Screen Flash** ([a5e0899](https://github.com/inventory69/simple-notes-sync/commit/a5e0899))
- Keep splash screen visible until notes are loaded — no more empty screen flash on cold start

**FAB Scrim Transition Artifact** ([833c30e](https://github.com/inventory69/simple-notes-sync/commit/833c30e), [2f75467](https://github.com/inventory69/simple-notes-sync/commit/2f75467))
- Snap FAB scrim to invisible before activity transition capture, restore fade-out animation

**Snackbar in Settings** ([24c62b8](https://github.com/inventory69/simple-notes-sync/commit/24c62b8), [0c76087](https://github.com/inventory69/simple-notes-sync/commit/0c76087))
- Replace unreliable Toast with Snackbar in all settings screens, show above keyboard

### 🏗️ Architecture & Refactoring

**WebDavSyncService Split** ([e0abff4](https://github.com/inventory69/simple-notes-sync/commit/e0abff4) → [7f467d7](https://github.com/inventory69/simple-notes-sync/commit/7f467d7))
- Split monolithic 2,735-line WebDavSyncService into 8 focused modules: SyncGateChecker, ETagCache, SyncTimestampManager, SyncExceptionMapper, SyncUrlBuilder, ConnectionManager, NoteUploader, NoteDownloader, MarkdownSyncManager (8 commits, 15→22/22)

**Legacy Code Removal** ([901ca77](https://github.com/inventory69/simple-notes-sync/commit/901ca77), [39b6e9f](https://github.com/inventory69/simple-notes-sync/commit/39b6e9f), [fb64a31](https://github.com/inventory69/simple-notes-sync/commit/fb64a31))
- Delete SettingsActivity (1,072 lines), MainActivity (857 lines), NoteEditorActivity (344 lines), all XML layouts/menus/drawables
- Compose Activities are now the sole implementations

**Modernization** ([dfd3b33](https://github.com/inventory69/simple-notes-sync/commit/dfd3b33), [ad137c3](https://github.com/inventory69/simple-notes-sync/commit/ad137c3), [65b6a26](https://github.com/inventory69/simple-notes-sync/commit/65b6a26), [9e547d2](https://github.com/inventory69/simple-notes-sync/commit/9e547d2), [07a7502](https://github.com/inventory69/simple-notes-sync/commit/07a7502))
- Replace LocalBroadcastManager with SharedFlow, migrate to viewModelFactory DSL, replace overridePendingTransition with ActivityOptions (API 34+), remove all @Suppress("DEPRECATION"), replace AlertDialog.Builder with Compose AlertDialog

### 📦 Dependencies & Build

**Dependency Updates** ([b57512f](https://github.com/inventory69/simple-notes-sync/commit/b57512f))
- Kotlin 2.0.21 → 2.1.0, Compose BOM 2026.01 → 2026.03, lifecycle 2.7 → 2.8.7, coroutines 1.7 → 1.9, navigation 2.7 → 2.8.5, activity 1.8 → 1.9.3, and 15+ more

**Code Quality** ([cf95fcd](https://github.com/inventory69/simple-notes-sync/commit/cf95fcd), [3933df0](https://github.com/inventory69/simple-notes-sync/commit/3933df0))
- Resolve all lint and detekt warnings, 500 unused strings deleted, ktlint formatting across 91 files, Kotlin 2.3.20

**APK Size Optimization** ([ce86b68](https://github.com/inventory69/simple-notes-sync/commit/ce86b68))
- Tighten R8/ProGuard keep rules — APK size 5.4 MB → 5.2 MB (-200 KB, classes.dex -507 KB)

**CI/CD** ([c6a3f25](https://github.com/inventory69/simple-notes-sync/commit/c6a3f25))
- Tag-based GitHub Actions release workflow for draft releases

### 📄 License

**License Change** ([6baaeda](https://github.com/inventory69/simple-notes-sync/commit/6baaeda))
- Changed from MIT to AGPL v3

---

## [1.12.0] - 2026-03-12

### 🌍 i18n, Settings Polish & Community

Focused release adding Chinese (Simplified) localization, proper plural forms for time-ago strings, notification status display in the settings overview, and Liberapay donation integration in the README.

### 🌍 Translations

**Chinese (Simplified) Translation** ([15a49d6](https://github.com/inventory69/simple-notes-sync/commit/15a49d6))
- Complete zh-CN localization (547 strings) based on Weblate contribution from [@heretic43](https://github.com/heretic43) ([#40](https://github.com/inventory69/simple-notes-sync/issues/40))
- App name kept as "Simple Notes" (untranslated)

### 🐛 Bug Fixes

**Proper i18n Plural Forms for Time-Ago Strings** ([c804964](https://github.com/inventory69/simple-notes-sync/commit/c804964))
- Converted `time_minutes_ago`, `time_hours_ago` and `time_days_ago` from `<string>` to `<plurals>` for proper grammatical forms in languages like Ukrainian
- Updated `Extensions.kt` to use `getQuantityString` accordingly
- All existing languages updated (en/de/tr/uk/zh)

### ✨ New Features

**Notification Status in Settings Overview** ([5d82e7d](https://github.com/inventory69/simple-notes-sync/commit/5d82e7d))
- The Sync & Notifications card on the settings hub screen now shows the current notification status alongside the trigger count
- Three states displayed: "Notifications enabled", "Errors and warnings only", "Notifications disabled"
- Consistent with the summary style of other settings cards

### 📝 Documentation

**Liberapay Donation Integration** ([7081286](https://github.com/inventory69/simple-notes-sync/commit/7081286))
- Added Shields.io donation badge to the badge row in README.md and README.de.md
- Added official Liberapay donate button in the Contributing section
- Both English and German READMEs updated

---

## [1.11.0] - 2026-03-10

### 🔔 Notifications, FAB Polish & Checklist Sorting

Focused release adding configurable notification settings with Android 13+ permission handling, ascending and descending sort-by-creation-date for checklists, a redesigned FAB overlay with animated scrim and unified action pills, restructured sync settings, hidden developer options, and several bug fixes around autosave behaviour and sync counting.

### ✨ New Features

**Notification Settings with Permission Handling** ([c1f5078](https://github.com/inventory69/simple-notes-sync/commit/c1f5078))
- Three new toggles in Sync & Notifications settings: global enable, errors-only mode, server unreachable warning
- Full Android 13+ POST_NOTIFICATIONS permission flow: request on first enable, rationale dialog on deny, app-settings redirect on permanent deny
- Permission state re-checked on every screen resume — toggle syncs with system state

**Sort Checklists by Creation Date (Ascending + Descending)** ([a265e3c](https://github.com/inventory69/simple-notes-sync/commit/a265e3c))
- New sort option "Date created ↑" (oldest first) and "Date created ↓" (newest first)
- Each checklist item gets a `createdAt` timestamp, preserved across edits and syncs
- Backward compatible: old items without timestamp use index as fallback
- Separator between unchecked/checked groups maintained for both directions

**Developer Options Hidden Behind Easter Egg** ([186a345](https://github.com/inventory69/simple-notes-sync/commit/186a345))
- Debug & Diagnostics settings hidden by default
- Unlock by tapping the app info card 5× in About screen (Android developer-options style)
- Session-only: resets on app restart, not persisted to SharedPreferences
- Countdown toast for last 2 taps, unlock confirmation toast

### 🐛 Bug Fixes

**Autosave Triggered for Empty Checklist Items** ([f7e25db](https://github.com/inventory69/simple-notes-sync/commit/f7e25db))
- Adding or deleting empty checklist items no longer triggers autosave
- No-change guard in `performSave()` and `saveOnBack()` skips save when only empty items differ from saved state
- Autosave indicator no longer misleadingly appears after adding an empty item

**Markdown Auto-Sync Double-Counting** ([7dd092e](https://github.com/inventory69/simple-notes-sync/commit/7dd092e))
- Creating a note with markdown auto-sync enabled no longer shows "2 notes synced" instead of 1
- Exported markdown files excluded from re-import in the same sync cycle via note ID tracking
- Technical re-uploads after markdown import no longer inflated the sync count
- `null` vs `emptyList()` mismatch in checklist content comparison fixed

**Misleading "Importing Markdown" Banner When No Files Changed** ([e352ce1](https://github.com/inventory69/simple-notes-sync/commit/e352ce1))
- Progress banner no longer shows a filename (e.g. "Test Neu.md") when 0 files are actually imported
- Fast-path skips IMPORTING_MARKDOWN phase entirely when all server files are unchanged
- Filename in progress update now only shown when a file is actually being downloaded

### 🎨 UI Improvements

**Animated FAB Scrim and Improved Colors** ([4a8a202](https://github.com/inventory69/simple-notes-sync/commit/4a8a202))
- FAB overlay now covers the entire screen including status bar (moved out of Scaffold)
- Semi-transparent animated scrim (50% black, 250 ms tween) replaces transparent dismiss overlay
- Sub-action colours changed from `secondaryContainer` to `primaryContainer` for better hierarchy

**Unified Action Pills** ([847154f](https://github.com/inventory69/simple-notes-sync/commit/847154f))
- Label text and icon combined into a single wide pill per action (Aegis Authenticator style)
- Entire pill is one click target — no more separate label + icon FAB
- Uses `surfaceContainerHigh` / `onSurface` for consistent appearance in dark mode

**Sync Settings Restructured** ([e9d48cb](https://github.com/inventory69/simple-notes-sync/commit/e9d48cb))
- Screen renamed from "Sync Settings" to "Sync & Notifications"
- Section header renamed from "Network & Performance" to "Network"
- Sub-headers "Instant Sync" and "Background Sync" removed for cleaner layout
- Sync triggers, network, and notifications extracted into separate composables for recomposition isolation

**Smooth Fade Transitions in Settings Navigation** ([1ef565d](https://github.com/inventory69/simple-notes-sync/commit/1ef565d))
- All settings routes now use a 700 ms fade transition instead of the abrupt default crossfade
- Defined globally on NavHost level — no per-route duplication

### 🌍 Translations

Thanks to [@FromKaniv](https://github.com/FromKaniv) for the Ukrainian translation and [@ksuheyl](https://github.com/ksuheyl) for the Turkish translation!

---

## [1.10.0] - 2026-03-01

### ✏️ Editor Overhaul, Share / Export & Sync Reliability

Major release adding PDF export, text and calendar sharing, a redesigned FAB menu, delete-from-editor with undo, batch server deletion with progress, adaptive tablet layouts, WorkManager reliability improvements, and real determinate progress bars for all sync phases.

### ✨ New Features

**Share & Export from Editor** ([e2b9f79](https://github.com/inventory69/simple-notes-sync/commit/e2b9f79), [2aca873](https://github.com/inventory69/simple-notes-sync/commit/2aca873), [57c4e96](https://github.com/inventory69/simple-notes-sync/commit/57c4e96)) _(Thanks to [@james0336](https://github.com/james0336) for requesting PDF export!)_
- New overflow menu (⋮) in the editor toolbar: Share as Text, Share as PDF, Export to Calendar
- PDF generated natively via `PdfDocument` API — no third-party library required
- Shared via `FileProvider` for secure, permission-free sharing with any PDF viewer
- Calendar export pre-fills title, all-day start date (today), and note content as description

**Expandable FAB Menu** ([85d68c4](https://github.com/inventory69/simple-notes-sync/commit/85d68c4), [61788e3](https://github.com/inventory69/simple-notes-sync/commit/61788e3))
- FAB replaced with an expanding speed-dial: tap `+` to reveal animated sub-action buttons for Text Note and Checklist
- `+` icon rotates to `×` when expanded; sub-actions slide in with staggered spring animation
- Transparent dismiss overlay closes the menu on outside tap
- Sub-action buttons and label pills use `secondaryContainer` colour, forming a visual unit
- Stronger shadow elevation (8–10 dp) ensures the FAB visually floats above note cards in both light and dark theme

**Delete Note from Editor with Undo** ([f3fd806](https://github.com/inventory69/simple-notes-sync/commit/f3fd806))
- Delete action moved from a blocking dialog to a bottom sheet confirmation
- After confirming, the editor closes and the main screen shows a timed undo snackbar
- Undo restores the note from the deleted state and cancels any scheduled server deletion

**Batch Server Deletion with Progress** ([39a873f](https://github.com/inventory69/simple-notes-sync/commit/39a873f))
- New `DELETING` sync phase shown in the banner when deleting multiple notes from the server
- Progress bar shows `current / total` with the current note title
- Phase transitions smoothly to `COMPLETED` with a result message

**Adaptive Layouts for Tablets & Landscape** ([a117cbe](https://github.com/inventory69/simple-notes-sync/commit/a117cbe))
- Editor content capped at 720 dp width, centred on wide screens
- Settings screens capped at 600 dp width, centred
- Main screen grid uses `Adaptive(180 dp)` columns — more columns appear automatically on tablets and in landscape
- Prepares for Android 16 (targetSdk 36) which ignores `screenOrientation` locks on displays ≥ 600 dp

**Undo/Redo in Note Editor** ([484bf3a](https://github.com/inventory69/simple-notes-sync/commit/484bf3a))
- Full undo/redo support for text notes and checklists via toolbar buttons
- Debounced snapshots: rapid keystrokes grouped into a single undo step (500 ms window)
- Stack limited to 50 entries; cleared on note switch to prevent cross-note undo
- Restoring a snapshot correctly updates the cursor position

**Configurable WebDAV Connection Timeout** ([b1aebc4](https://github.com/inventory69/simple-notes-sync/commit/b1aebc4))
- New Settings slider (1–30 s, default 8 s) to configure the WebDAV connection timeout
- Applied to all OkHttpClient instances (connect, read, write) used by Sardine
- Consistent, user-facing error messages for timeout, auth failure, not found, and server errors

**Markdown Auto-Sync Timeout Protection** ([7f74ae9](https://github.com/inventory69/simple-notes-sync/commit/7f74ae9))
- Enabling Markdown auto-sync now has a 10 s timeout for the initial export
- UI toggle updates optimistically and reverts if the export fails or times out
- Prevents the Settings screen from hanging on unreachable servers

**Save on Back Navigation** ([402382c](https://github.com/inventory69/simple-notes-sync/commit/402382c)) _(Thanks to [@GitNichtGibtsNicht](https://github.com/GitNichtGibtsNicht) for requesting autosave on back!)_
- Dirty notes are saved automatically when navigating back from the editor (system back + toolbar back)
- Only active when autosave is enabled; synchronous save without triggering sync
- Autosave toggle description updated to mention this behaviour

### 🐛 Bug Fixes

**Download Progress Always Indeterminate** ([c83aae3](https://github.com/inventory69/simple-notes-sync/commit/c83aae3))
- `ParallelDownloader` already tracked `completed / total` internally but `syncNotes()` hardcoded `total = 0`
- Fixed: `total` is now passed through → DOWNLOADING phase shows a real `LinearProgressIndicator`
- `importMarkdownFiles()` also reports per-file progress: banner shows `X / Y filename.md` with a determinate bar

**FGS Timeout on Android 15+** ([1e6eb64](https://github.com/inventory69/simple-notes-sync/commit/1e6eb64))
- Added `ensureActive()` checkpoints in `WebDavSyncService` download loop and markdown import so coroutines respond promptly to WorkManager cancellation on targetSdk 35+
- `CancellationException` handler in `SyncWorker` now logs the stop reason (API 31+)

**WorkManager Quota / Standby Stops Not Surfaced** ([3d66a19](https://github.com/inventory69/simple-notes-sync/commit/3d66a19))
- Added detailed stop reason logging: 16 WorkManager stop codes mapped to human-readable names
- When a sync is stopped due to JobScheduler quota or app standby, an info banner is shown next time the app comes to foreground

**Consistent Editor Overflow Menu Position** ([242ece3](https://github.com/inventory69/simple-notes-sync/commit/242ece3))
- Overflow menu (⋮) is now anchored to the `⋮` button in both text and checklist note types
- Previously the menu anchored to the outer actions `Row`, appearing too far left on checklist notes

**Markdown Import: Pre-Heading Content Lost** ([e33ac23](https://github.com/inventory69/simple-notes-sync/commit/e33ac23))
- Content before the first `#` heading was silently discarded during Markdown import
- Checklist detection improved: more item patterns are now recognised

**Checklist Autosave Not Triggered on Item Changes** ([5401df3](https://github.com/inventory69/simple-notes-sync/commit/5401df3))
- Deleting, adding, and reordering checklist items now correctly marks the note as dirty and triggers autosave

**Minimal Scroll When Adding New Checklist Items** ([c2fbe0b](https://github.com/inventory69/simple-notes-sync/commit/c2fbe0b))
- New checklist items scroll just enough to become visible instead of jumping to the top

**False Autosave on Checklist Cursor Tap** ([9ea7089](https://github.com/inventory69/simple-notes-sync/commit/9ea7089))
- Tapping a checklist item to position the cursor no longer triggers a false autosave
- No-op guards added to `updateChecklistItemText()` and `updateChecklistItemChecked()`

**Undo to Saved State Still Triggered Autosave** ([cf5027b](https://github.com/inventory69/simple-notes-sync/commit/cf5027b))
- Undoing all changes back to the last saved state now resets `isDirty` and cancels the pending autosave
- New `savedSnapshot` property captures state at load time and after every explicit save

**Foreign JSON Files Downloaded Unnecessarily** ([c409243](https://github.com/inventory69/simple-notes-sync/commit/c409243))
- Non-note JSON files (e.g. `google-services.json`) filtered before download via UUID format check

**Note Count Strings Not Pluralized Correctly** ([8ca8df3](https://github.com/inventory69/simple-notes-sync/commit/8ca8df3))
- Note count strings converted to proper Android plural forms (EN + DE)

### 🎨 UI Improvements

**Smooth Sync Banner Animations** ([c409243](https://github.com/inventory69/simple-notes-sync/commit/c409243))
- Banner enter: fadeIn (300 ms, EaseOutCubic) — no more abrupt push from top
- Banner exit: fadeOut + shrinkVertically (300/400 ms, EaseInCubic)
- Phase transitions use AnimatedContent crossfade (250 ms) for text changes
- Minimum display duration per active phase (400 ms) prevents unreadable flashes
- Auto-hide job decoupled from flow collector — guaranteed minimum display time

**Markdown Folder Mentioned in Sync Settings** ([a8bb80c](https://github.com/inventory69/simple-notes-sync/commit/a8bb80c))
- Sync folder hint now explicitly mentions the `notes-md/` subdirectory used for Markdown auto-sync

---

## [1.9.0] - 2026-02-25

### 🔄 Sync Quality, Performance & UI

Major release adding note filtering, markdown preview, configurable sync folder, opt-in autosave, widget polish, and significant sync improvements — server switch data loss fixed, parallel uploads, import wizard, and three sync edge-cases resolved.

### 🐛 Bug Fixes

**First Sync Fails if /notes/ Folder Doesn't Exist on Server** ([e012d17](https://github.com/inventory69/simple-notes-sync/commit/e012d17))
- First sync no longer fails silently when the `/notes/` directory hasn't been created on the server yet
- Root cause: `checkServerForChanges()` returned `false` (no changes) instead of `true` (proceed) when `lastSyncTime > 0` and folder was missing
- Fix: returns `true` to allow initial upload — server will create the folder on first PUT

**Server Switch Causes False "Deleted on Server" Status** ([0985209](https://github.com/inventory69/simple-notes-sync/commit/0985209))
- Switching to a new server no longer causes local notes to be falsely marked as deleted
- Root cause: E-Tag and content hash caches from the old server were not cleared, causing upload-skip to fire incorrectly — notes appeared SYNCED without being uploaded to the new server
- Fix: `clearServerCaches()` clears all E-Tag, content hash, last sync timestamp, and deletion tracker entries on server change
- `resetAllSyncStatusToPending()` now also resets DELETED_ON_SERVER status to PENDING

**Server Deletion Detection Guard Too Aggressive** ([56c0363](https://github.com/inventory69/simple-notes-sync/commit/56c0363))
- Users with 2–9 notes who deleted all from the Nextcloud web UI never got DELETED_ON_SERVER status
- Guard threshold raised from >1 to ≥10 to allow legitimate mass-deletion for small portfolios

**Parallel Markdown Export Race Condition** ([56c0363](https://github.com/inventory69/simple-notes-sync/commit/56c0363))
- Two notes with identical titles could overwrite each other's Markdown file during parallel upload
- Root cause: concurrent `exists()` → `put()` sequence without synchronization
- Fix: Markdown export serialized via Mutex (JSON uploads remain parallel)

**E-Tag Not Cached for "Local Newer" Download Skip** ([56c0363](https://github.com/inventory69/simple-notes-sync/commit/56c0363))
- When local note was newer than server version, the server E-Tag was not cached
- Caused unnecessary re-downloads on every subsequent sync
- Fix: E-Tag now saved in the else-branch of download result processing

**Tune Button Color Mismatch** ([135559a](https://github.com/inventory69/simple-notes-sync/commit/135559a))
- Untoggled tune button now uses default TopAppBar icon color instead of custom color

**Import Wizard Loses Checklist Content** ([5031848](https://github.com/inventory69/simple-notes-sync/commit/5031848))
- Checklist detection during Markdown import now preserves full note content

**Checklist Scroll Jump When Checking First Visible Item** ([8238af4](https://github.com/inventory69/simple-notes-sync/commit/8238af4))
- Checking the first visible checklist item no longer causes a scroll jump

**Checklist Original Order Lost After Insert/Delete** ([e601642](https://github.com/inventory69/simple-notes-sync/commit/e601642))
- Original item order is now cemented after insert/delete operations to prevent reordering glitches

**Inconsistent Scroll on Check/Un-Check** ([19dfb03](https://github.com/inventory69/simple-notes-sync/commit/19dfb03))
- Consistent scroll behavior when checking and unchecking checklist items

### ✨ New Features

**Notes Import Wizard** ([e012d17](https://github.com/inventory69/simple-notes-sync/commit/e012d17))
- New import screen in Settings — import notes from WebDAV server or local storage
- Supported formats: `.md` (with/without YAML frontmatter), `.json` (Simple Notes format or generic), `.txt` (plain text)
- WebDAV scan: recursive subfolder scan (depth 1), respects existing DeletionTracker entries
- Imported notes from YAML frontmatter or Simple Notes JSON are imported as SYNCED; others as PENDING
- Accessible via Settings → Import

**Parallel Uploads** ([187d338](https://github.com/inventory69/simple-notes-sync/commit/187d338))
- Notes are uploaded in parallel instead of sequentially — ~2× faster for multi-note changes
- Upload time for 4 notes reduced from ~11.5 s to ~6 s (measured on device)
- Second sync with unchanged notes: upload phase ~0 ms (all skipped via content hash)
- Bounded concurrency via Semaphore; file I/O writes serialized via Mutex
- New: `/notes-md/` existence check cached per sync run (saves ~480 ms × N exists() calls)

**Unified Parallel Connections Setting** ([ef200d0](https://github.com/inventory69/simple-notes-sync/commit/ef200d0))
- Parallel downloads (1/3/5/7/10) and uploads (hidden, max 6) merged into single "Parallel Connections" setting
- New options: 1, 3, 5 (reduced from 5 options — 7 and 10 removed since uploads cap at 6)
- Users with 7 or 10 selected are automatically migrated to 5
- Uploads capped at `min(setting, 6)` at runtime

**Filter Chip Row** ([952755f](https://github.com/inventory69/simple-notes-sync/commit/952755f), [71a0469](https://github.com/inventory69/simple-notes-sync/commit/71a0469), [07c41bb](https://github.com/inventory69/simple-notes-sync/commit/07c41bb))
- New filter bar below TopAppBar — filter notes by All / Text / Checklists
- Inline search field for quick note filtering by title
- Sort button moved from dialog into compact filter row icon
- Tune button in TopAppBar toggles filter row visibility

**Markdown Preview** ([e83a89a](https://github.com/inventory69/simple-notes-sync/commit/e83a89a))
- Live markdown preview for text notes with formatting toolbar
- Supports headings, bold, italic, strikethrough, lists, horizontal rules, code blocks
- Toggle between edit and preview mode

**Custom App Title** ([bf478c7](https://github.com/inventory69/simple-notes-sync/commit/bf478c7))
- Configurable app name in settings

**Configurable WebDAV Sync Folder** ([58cdf1e](https://github.com/inventory69/simple-notes-sync/commit/58cdf1e))
- Custom sync folder name (default: `notes`, configurable for multi-app setups)

**Opt-in Autosave** ([5800183](https://github.com/inventory69/simple-notes-sync/commit/5800183))
- Autosave with debounce timer (3s after last edit, configurable in settings)
- Disabled by default, opt-in via Settings

**Scroll to Top After Manual Sync** ([4697e49](https://github.com/inventory69/simple-notes-sync/commit/4697e49))
- Notes list scrolls to top after completing a manual sync

### 🔄 Improvements

**Widget: Monet Tint in Translucent Background** ([0f5a734](https://github.com/inventory69/simple-notes-sync/commit/0f5a734))
- Monet dynamic color tint preserved in translucent widget backgrounds

**Widget: Options Bar Background Removed** ([5e3273a](https://github.com/inventory69/simple-notes-sync/commit/5e3273a))
- Options bar background removed for seamless widget integration

**Widget: Strikethrough for Completed Items** ([eb9db2e](https://github.com/inventory69/simple-notes-sync/commit/eb9db2e))
- Completed checklist items in widgets now show strikethrough styling

**Widget: Auto-Refresh on onStop** ([2443908](https://github.com/inventory69/simple-notes-sync/commit/2443908))
- Widgets automatically refresh when leaving the app (onStop lifecycle hook)

**Checklist: Un-Check Restores Original Position** ([188a0f6](https://github.com/inventory69/simple-notes-sync/commit/188a0f6))
- Un-checking an item restores it to its original position

**Sort Button: Compact Icon Button** ([a1bd15a](https://github.com/inventory69/simple-notes-sync/commit/a1bd15a))
- Replaced AssistChip with compact IconButton + SwapVert icon

### 🛠️ Internal

**Code Quality** ([6708156](https://github.com/inventory69/simple-notes-sync/commit/6708156))
- Fixed deprecated `Icons.Outlined.Notes` → `Icons.AutoMirrored.Outlined.Notes`
- Removed unused `Color` import from ServerSettingsScreen + detekt baseline entry
- Logger timestamps use `Locale.ROOT` instead of `Locale.getDefault()`
- Removed obsolete `Build.VERSION_CODES.N` check (minSdk=24)

**Detekt Compliance** ([f0e143c](https://github.com/inventory69/simple-notes-sync/commit/f0e143c))
- Extraction of `ALL_DELETED_GUARD_THRESHOLD` constant for magic number compliance

**ProGuard/R8 Verification**
- Release build verified — no rule changes needed for v1.9.0

**Image Support Deferred to v2.0.0** ([845ba03](https://github.com/inventory69/simple-notes-sync/commit/845ba03))
- Local image embedding removed from v1.9.0 scope
- Feature preserved as v2.0.0 specification with full architecture proposal

**Weblate PR Workflow** ([efd782f](https://github.com/inventory69/simple-notes-sync/commit/efd782f))
- Weblate integration switched to PR-based translation workflow

**Documentation** ([395d154](https://github.com/inventory69/simple-notes-sync/commit/395d154))
- Documentation updated for v1.8.2 and v1.9.0 (FEATURES, UPCOMING, QUICKSTART)
- Fixed broken links across docs (closes #22)

---

## [1.8.2] - 2026-02-16

### 🔧 Stability, Editor & Widget Improvements

Major stability release fixing 26 issues — sync deadlocks, data loss prevention, SSL certificates, markdown sync loop, silent download failures, editor UX improvements, widget polish, and APK size optimization.

### 🐛 Bug Fixes

**Sync Stuck in "Already in Progress"** *(IMPL_01)* ([a62ab78](https://github.com/inventory69/simple-notes-sync/commit/a62ab78))
- Fixed 5 code paths in SyncWorker where `tryStartSync()` was called but state was never reset
- Early returns (no changes, gate blocked, server unreachable) now call `SyncStateManager.reset()`
- CancellationException handler now resets state instead of leaving it in SYNCING
- Generic Exception handler now calls `markError()` to properly transition state
- Root cause: SyncStateManager stayed in SYNCING state permanently, blocking all future syncs

**Self-Signed SSL Certificates in Release Builds** *(IMPL_02)* ([b3f4915](https://github.com/inventory69/simple-notes-sync/commit/b3f4915))
- Added `<certificates src="user" />` to network security base config
- User-installed CA certificates now work in release builds (previously debug-only)
- Required for self-hosted WebDAV servers with self-signed SSL certificates

**Text Notes Not Scrollable in Medium Widgets** *(IMPL_04)* ([8429306](https://github.com/inventory69/simple-notes-sync/commit/8429306))
- Changed NARROW_MED and WIDE_MED widget size classes to use `TextNoteFullView` (scrollable)
- Previously used `TextNotePreview` which was truncated and non-scrollable
- 2x1 and 4x1 widgets now show scrollable text content
- Removed unused `TextNotePreview` function and related constants

**Keyboard Auto-Capitalization** *(IMPL_05)* ([d93b439](https://github.com/inventory69/simple-notes-sync/commit/d93b439))
- Title field now uses `KeyboardCapitalization.Words`
- Content field now uses `KeyboardCapitalization.Sentences`
- Checklist items now use `KeyboardCapitalization.Sentences`

**Documentation: Sort Option Naming** *(IMPL_06)* ([465bd9c](https://github.com/inventory69/simple-notes-sync/commit/465bd9c))
- Changed "color"/"Farbe" to "type"/"Typ" in README files
- Updated F-Droid metadata descriptions (de-DE and en-US)

**Keyboard Auto-Scroll for Text Notes** *(IMPL_07)* ([bc266b9](https://github.com/inventory69/simple-notes-sync/commit/bc266b9))
- Migrated TextNoteContent from `TextFieldValue` API to `TextFieldState` API
- Added external `scrollState` parameter to `OutlinedTextField`
- Auto-scrolls to cursor position when keyboard opens

**Checklist Scroll Jump When Typing** *(IMPL_10)* ([974ef13](https://github.com/inventory69/simple-notes-sync/commit/974ef13))
- Replaced faulty auto-scroll logic from v1.8.1 with viewport-aware scroll
- Only scrolls if item actually extends below viewport

**Checklist Visual Glitch During Fast Scrolling** *(IMPL_11)* ([82e8972](https://github.com/inventory69/simple-notes-sync/commit/82e8972))
- Added `isDragConfirmed` state to prevent accidental drag activation during scroll
- Scoped `animateItem()` to confirmed drag operations only
- Root cause: `Modifier.animateItem()` caused fade-in/out animations when items entered/left viewport

**Checklist Drag Interrupted at Separator** *(IMPL_26)* ([8828391](https://github.com/inventory69/simple-notes-sync/commit/8828391))
- Dragging a checklist item across the checked/unchecked separator no longer drops the item
- Item stays in active drag while its checked state toggles seamlessly
- Root cause: Separate `itemsIndexed` blocks destroyed Composition on boundary crossing — unified into single `items` block

**SyncMutex Deadlock via clearSessionCache() Exception** *(IMPL_13)* ([99f451b](https://github.com/inventory69/simple-notes-sync/commit/99f451b))
- Wrapped `clearSessionCache()` in try-catch inside `finally` block
- Prevents Mutex from staying locked when cache cleanup throws

**False Error Banner on Sync Cancellation** *(IMPL_14)* ([1c45680](https://github.com/inventory69/simple-notes-sync/commit/1c45680))
- CancellationException no longer shows error banner to user
- Removed duplicate state resets in SyncWorker catch blocks

**Socket Leak in isServerReachable()** *(IMPL_15)* ([fac54d7](https://github.com/inventory69/simple-notes-sync/commit/fac54d7))
- Socket now properly closed in all code paths (was leaking on successful connect)

**CancellationException Swallowed in ParallelDownloader** *(IMPL_16)* ([4c34746](https://github.com/inventory69/simple-notes-sync/commit/4c34746))
- CancellationException now re-thrown instead of caught and retried
- Prevents infinite retry loop when WorkManager cancels sync

**Checklist Data Loss on onResume** *(IMPL_17)* ([b436623](https://github.com/inventory69/simple-notes-sync/commit/b436623))
- Checklist edits now persist when returning from notification shade or app switcher
- Root cause: `onResume()` reloaded note from database, discarding unsaved in-memory changes

**Duplicate Stale-Sync Cleanup** *(IMPL_18)* ([71ae747](https://github.com/inventory69/simple-notes-sync/commit/71ae747))
- Removed copy-paste duplicate reset block in `SimpleNotesApplication.onCreate()`

**NotesStorage Shadow + Download Cancellation** *(IMPL_19)* ([ede429c](https://github.com/inventory69/simple-notes-sync/commit/ede429c), [50ae9d8](https://github.com/inventory69/simple-notes-sync/commit/50ae9d8))
- Removed shadow `NotesStorage` instance in `hasUnsyncedChanges()` (19a)
- Replaced `runBlocking` with `coroutineScope` in `downloadRemoteNotes()` for proper cancellation (19b)
- Added read timeout to OkHttpClient instances (19c)

**Silent Download Failures Reported as Success** *(IMPL_21)* ([371d5e3](https://github.com/inventory69/simple-notes-sync/commit/371d5e3))
- Download exceptions now propagate instead of being silently caught
- Sync correctly reports failure when downloads fail

**PENDING Notes Not Detected** *(IMPL_22)* ([20de019](https://github.com/inventory69/simple-notes-sync/commit/20de019))
- `hasUnsyncedChanges()` now checks for notes with PENDING sync status
- Fixes issue where switching servers left notes unsynced

**E-Tag/Timestamp Download Order** *(IMPL_23)* ([68dbb4e](https://github.com/inventory69/simple-notes-sync/commit/68dbb4e))
- E-Tag comparison now runs before timestamp check (was skipping changed notes)
- Fixes cross-device sync where timestamps matched but content differed

**Silent Sync Promote to Visible** *(IMPL_24)* ([940a494](https://github.com/inventory69/simple-notes-sync/commit/940a494))
- Pull-to-refresh during background sync now shows sync banner instead of "already in progress" error

**Markdown Sync Feedback Loop** *(IMPL_25)* ([74194d4](https://github.com/inventory69/simple-notes-sync/commit/74194d4))
- Fixed 5 root causes creating an infinite export→import→re-export cycle
- UUID normalization, server-mtime preservation, timezone-aware comparison, path sanitization, content-type-aware comparison

### ✨ New Features

**Enter-Key Navigation from Title to Content** *(IMPL_09)* ([81b9aca](https://github.com/inventory69/simple-notes-sync/commit/81b9aca))
- Title field is now single-line with `ImeAction.Next`
- Pressing Enter/Next jumps to content field or first checklist item

### 🔄 Improvements

**Widget Content Padding** *(IMPL_08)* ([2ae5ce5](https://github.com/inventory69/simple-notes-sync/commit/2ae5ce5))
- Unified padding for all widget views: 12dp horizontal, 4dp top, 12dp bottom

**Widget Entry Spacing** *(IMPL_12)* ([c3d4b33](https://github.com/inventory69/simple-notes-sync/commit/c3d4b33))
- Increased checklist and text widget spacing for better readability

**Sync State Timeout**
- Added 5-minute timeout for stale sync states in `SyncStateManager`
- `tryStartSync()` auto-resets if existing sync is older than 5 minutes

**Cold Start State Cleanup**
- `SimpleNotesApplication.onCreate()` now resets orphaned SYNCING state

**APK Size Optimization** *(IMPL_03)* ([7867894](https://github.com/inventory69/simple-notes-sync/commit/7867894))
- Replaced broad ProGuard rule with granular rules — keeps only what reflection actually needs

**Version Bump**
- versionCode: 21 → 22
- versionName: 1.8.1 → 1.8.2

---

## [1.8.1] - 2026-02-11

### 🛠️ Bugfix & Polish Release

Checklist fixes, widget improvements, sync hardening, and code quality cleanup.

### 🐛 Bug Fixes

**Checklist Sort Persistence** ([7dbc06d](https://github.com/inventory69/simple-notes-sync/commit/7dbc06d))
- Fixed sort option not applied when reopening a checklist
- Root cause: `sortChecklistItems()` always sorted unchecked-first instead of reading `_lastChecklistSortOption`
- Now correctly restores all sort modes (Manual, Alphabetical, Unchecked/Checked First)

**Widget Scroll on Standard Size** ([c72b3fe](https://github.com/inventory69/simple-notes-sync/commit/c72b3fe))
- Fixed scroll not working on standard 3×2 widget size (110–150dp height)
- Added `NARROW_SCROLL` and `WIDE_SCROLL` size classes with 150dp threshold
- Removed `clickable` modifier from unlocked checklists to enable scrolling

**Auto-Sync Toast Removed** ([fe6935a](https://github.com/inventory69/simple-notes-sync/commit/fe6935a))
- Removed unexpected toast notification on automatic background sync
- Silent auto-sync stays silent; only errors are shown

**Gradient & Drag Regression** ([24fe32a](https://github.com/inventory69/simple-notes-sync/commit/24fe32a))
- Fixed gradient overlay regression on long checklist items
- Fixed drag-and-drop flicker when moving items between boundaries

### 🆕 New Features

**Widget Checklist Sorting & Separators** ([66d98c0](https://github.com/inventory69/simple-notes-sync/commit/66d98c0))
- Widgets now apply saved sort option from the editor
- Visual separator between unchecked/checked items (MANUAL & UNCHECKED_FIRST modes)
- Auto-sort when toggling checkboxes in the widget
- Changed ✅ → ☑️ emoji for checked items

**Checklist Preview Sorting** ([2c43b47](https://github.com/inventory69/simple-notes-sync/commit/2c43b47))
- Main screen preview (NoteCard, NoteCardCompact, NoteCardGrid) now respects saved sort option
- New `ChecklistPreviewHelper` with shared sorting logic

**Auto-Scroll on Line Wrap** ([3e4b1bd](https://github.com/inventory69/simple-notes-sync/commit/3e4b1bd))
- Checklist editor auto-scrolls when typing causes text to wrap to a new line
- Keeps cursor visible at bottom of list during editing

**Separator Drag Cross-Boundary** ([7b55811](https://github.com/inventory69/simple-notes-sync/commit/7b55811))
- Drag-and-drop now works across the checked/unchecked separator
- Items auto-toggle their checked state when dragged across boundaries
- Extracted `DraggableChecklistItem` composable for reusability

### 🔄 Improvements

**Sync Rate-Limiting & Battery Protection** ([ffe0e46](https://github.com/inventory69/simple-notes-sync/commit/ffe0e46), [a1a574a](https://github.com/inventory69/simple-notes-sync/commit/a1a574a))
- Global 30-second cooldown between sync operations (auto/WiFi/periodic)
- onSave syncs bypass global cooldown (retain own 5s throttle)
- New `SyncStateManager` singleton for centralized state tracking
- Prevents battery drain from rapid successive syncs

**Toast → Banner Migration** ([27e6b9d](https://github.com/inventory69/simple-notes-sync/commit/27e6b9d))
- All non-interactive notifications migrated to unified Banner system
- Server-delete results show as INFO/ERROR banners
- Added INFO phase to SyncPhase enum with auto-hide (2.5s)
- Snackbars with Undo actions remain unchanged

**ProGuard Rules Audit** ([6356173](https://github.com/inventory69/simple-notes-sync/commit/6356173))
- Added missing keep rules for Widget ActionCallback classes
- Added Compose-specific ProGuard rules
- Prevents ClassNotFoundException in release builds

### 🧹 Code Quality

**Detekt Compliance** ([1a6617a](https://github.com/inventory69/simple-notes-sync/commit/1a6617a))
- Resolved all 12 detekt findings (0 issues remaining)
- Refactored `NoteEditorViewModel.loadNote()` to reduce nesting depth
- Extracted constants for magic numbers in editor
- Removed unused imports from `UpdateChangelogSheet`
- Set `maxIssues: 0` in detekt config

---

## [1.8.0] - 2026-02-10

### 🚨 CRITICAL BUGFIX (Tag recreated)

**R8/ProGuard Obfuscation Fix - Prevents Data Loss**
- 🔧 **CRITICAL:** Fixed incorrect ProGuard class path for `Note$Companion$NoteRaw`
  - Original v1.8.0 had specific `-keep` rules that didn't match
  - R8 obfuscated all NoteRaw fields (id→a, title→b, ...)
  - Gson couldn't parse JSON anymore → **ALL notes appeared lost**
  - Reverted to safe broad rule: `-keep class dev.dettmer.simplenotes.** { *; }`
- 🛡️ Added safety-guards in `detectServerDeletions()`
  - Prevents mass deletion when `serverNoteIds` is empty (network errors)
  - Aborts if ALL local notes would be marked as deleted
- ✅ Notes were never actually lost (JSON files intact on disk + server)
- ✅ Downgrade to v1.7.2 restored all notes

**⚠️ If you installed original v1.8.0:** Your notes are safe! Just update.

### 🎉 Major: Widgets, Sorting & Advanced Sync

Complete widget system with interactive checklists, note sorting, and major sync improvements!

### 🆕 Homescreen Widgets

**Full Jetpack Glance Widget Framework** ([539987f](https://github.com/inventory69/simple-notes-sync/commit/539987f))
- 5 responsive size classes (SMALL, NARROW_MED, NARROW_TALL, WIDE_MED, WIDE_TALL)
- Interactive checklist checkboxes that sync immediately to server
- Material You dynamic colors with configurable background opacity (0-100%)
- Lock widget toggle to prevent accidental edits
- Read-only mode with permanent options bar for locked widgets
- Widget configuration activity with note selection and settings
- Auto-refresh after sync completion
- Tap content to open editor (unlocked) or show options (locked)
- Complete resource cleanup fixes for connection leaks

**Widget State Management:**
- NoteWidgetState keys for per-instance persistence via DataStore
- Five top-level ActionCallbacks (Toggle Checkbox, Lock, Options, Refresh, Config)
- Type-safe parameter passing with NoteWidgetActionKeys

### 📊 Note & Checklist Sorting

**Note Sorting** ([96c819b](https://github.com/inventory69/simple-notes-sync/commit/96c819b))
- Sort by: Updated (newest/oldest), Created, Title (A-Z/Z-A), Type
- Persistent sort preferences (saved in SharedPreferences)
- Sort dialog in main screen with direction toggle
- Combined sortedNotes StateFlow in MainViewModel

**Checklist Sorting** ([96c819b](https://github.com/inventory69/simple-notes-sync/commit/96c819b), [900dad7](https://github.com/inventory69/simple-notes-sync/commit/900dad7))
- Sort by: Manual, Alphabetical, Unchecked First, Checked Last
- Visual separator between unchecked/checked items with count display
- Auto-sort on item toggle and reordering
- Drag-only within same group (unchecked/checked)
- Smooth fade/slide animations for item transitions
- Unit tested with 9 test cases for sorting logic validation

### 🔄 Sync Improvements

**Server Deletion Detection** ([40d7c83](https://github.com/inventory69/simple-notes-sync/commit/40d7c83), [bf7a74e](https://github.com/inventory69/simple-notes-sync/commit/bf7a74e))
- New `DELETED_ON_SERVER` sync status for multi-device scenarios
- Detects when notes are deleted on other clients
- Zero performance impact (uses existing PROPFIND data)
- Deletion count shown in sync banner: "3 synced · 2 deleted on server"
- Edited deleted notes automatically re-upload to server (status → PENDING)

**Sync Status Legend** ([07607fc](https://github.com/inventory69/simple-notes-sync/commit/07607fc))
- Help button (?) in main screen TopAppBar
- Dialog explaining all 5 sync status icons with descriptions
- Only visible when sync is configured

**Live Sync Progress UI** ([df37d2a](https://github.com/inventory69/simple-notes-sync/commit/df37d2a))
- Real-time phase indicators: PREPARING, UPLOADING, DOWNLOADING, IMPORTING_MARKDOWN
- Upload progress shows x/y counter (known total)
- Download progress shows count (unknown total)
- Single unified SyncProgressBanner (replaces dual system)
- Auto-hide: COMPLETED (2s), ERROR (4s)
- No misleading counters when nothing to sync
- Silent auto-sync stays silent, errors always shown

**Parallel Downloads** ([bdfc0bf](https://github.com/inventory69/simple-notes-sync/commit/bdfc0bf))
- Configurable concurrent downloads (default: 3 simultaneous)
- Kotlin coroutines async/awaitAll pattern
- Individual download timeout handling
- Graceful sequential fallback on concurrent errors
- Optimized network utilization for faster sync

### ✨ UX Improvements

**Checklist Enhancements:**
- Overflow gradient for long text items ([3462f93](https://github.com/inventory69/simple-notes-sync/commit/3462f93))
- Auto-expand on focus, collapse to 5 lines when unfocused
- Drag & Drop flicker fix with straddle-target-center detection ([538a705](https://github.com/inventory69/simple-notes-sync/commit/538a705))
- Adjacency filter prevents item jumps during fast drag
- Race-condition fix for scroll + move operations

**Settings UI Polish:**
- Smooth language switching without activity recreate ([881c0fd](https://github.com/inventory69/simple-notes-sync/commit/881c0fd))
- Grid view as default for new installations ([6858446](https://github.com/inventory69/simple-notes-sync/commit/6858446))
- Sync settings restructured into clear sections: Triggers & Performance ([eaac5a0](https://github.com/inventory69/simple-notes-sync/commit/eaac5a0))
- Changelog link added to About screen ([49810ff](https://github.com/inventory69/simple-notes-sync/commit/49810ff))

**Post-Update Changelog Dialog** ([661d9e0](https://github.com/inventory69/simple-notes-sync/commit/661d9e0))
- Shows localized changelog on first launch after update
- Material 3 ModalBottomSheet with slide-up animation
- Loads F-Droid changelogs via assets (single source of truth)
- One-time display per versionCode (stored in SharedPreferences)
- Clickable GitHub link for full changelog
- Dismissable via button or swipe gesture
- Test mode in Debug Settings with reset option

**Backup Settings Improvements** ([3e946ed](https://github.com/inventory69/simple-notes-sync/commit/3e946ed))
- New BackupProgressCard with LinearProgressIndicator
- 3-phase status system: In Progress → Completion → Clear
- Success status shown for 2s, errors for 3s
- Removed redundant toast messages
- Buttons stay visible and disabled during operations
- Exception logging for better error tracking

### 🐛 Bug Fixes

**Widget Text Display** ([d045d4d](https://github.com/inventory69/simple-notes-sync/commit/d045d4d))
- Fixed text notes showing only 3 lines in widgets
- Changed from paragraph-based to line-based rendering
- LazyColumn now properly scrolls through all content
- Empty lines preserved as 8dp spacers
- Preview limits increased: compact 100→120, full 200→300 chars

### 🔧 Code Quality

**Detekt Cleanup** ([1da1a63](https://github.com/inventory69/simple-notes-sync/commit/1da1a63))
- Resolved all 22 Detekt warnings
- Removed 7 unused imports
- Defined constants for 5 magic numbers
- Optimized state reads with derivedStateOf
- Build: 0 Lint errors + 0 Detekt warnings

### 📚 Documentation

- Complete implementation plans for all 23 v1.8.0 features
- Widget system architecture and state management docs
- Sorting logic unit tests with edge case coverage
- F-Droid changelogs (English + German)

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

[1.8.1]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.8.1
[1.8.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.8.0
[1.7.2]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.7.2
[1.7.1]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.7.1
[1.7.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.7.0
[1.6.1]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.6.1
[1.6.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.6.0
[1.5.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.5.0
[1.4.1]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.4.1
[1.4.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.4.0
[1.3.2]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.3.2
[1.3.1]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.3.1
[1.3.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.3.0
[1.2.2]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.2.2
[1.2.1]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.2.1
[1.2.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.2.0
[1.1.2]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.1.2
[1.1.1]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.1.1
[1.1.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.1.0
[1.0.0]: https://github.com/inventory69/simple-notes-sync/releases/tag/v1.0.0
