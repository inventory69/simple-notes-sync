# Features

Everything Simple Notes Sync does, as of v2.21.0. What changed in which version is in the [changelog](../CHANGELOG.md).

## Writing

- Text notes and checklists. A text note can be turned into a checklist and back.
- Markdown with live highlighting while you type, and a rendered preview with selectable text. Choose whether notes open in the editor or in the preview.
- A formatting toolbar, including a code block button.
- Tables, with a toolbar button to insert one. The preview and the PDF export draw a real grid, note cards and widgets show each row on one line.
- Bare URLs become links. Checkboxes written in Markdown can be ticked in the editor.
- Pasting from a web page, Word, Google Docs or Telegram keeps headings, lists, links and tables as Markdown. Plain text is the fallback.
- Images: insert from the picker or paste from the clipboard, resize and align them, place several side by side, open them full screen. New images start at a default size you choose. Compression is configurable (compressed, lossless, original); the first two remove EXIF and location data.
- Undo and redo, up to 50 steps.
- A word and character counter below the note. Tap it to switch.
- Autosave 3 seconds after the last change and when you leave the editor. Can be turned off.
- Share a note as text or PDF (with formatting and images), add it or a single checklist item to your calendar, copy or duplicate it.
- Receive text shared from other apps as a new note, or append it to an existing one.

## Checklists

- Tap to check, long press to drag. Dragging an item across the divider checks or unchecks it.
- Sort manually, alphabetically, unchecked first, checked last, or by creation date. Checked items sit below a divider with a count.
- Unchecking puts an item back where it was.
- Read mode: item texts are locked, drag handles and menus are hidden, tapping a row checks it. Existing checklists can open in read mode by default.

## Organizing and display

- Folders. A folder can be marked local only, then its notes never leave the device.
- Pinned notes stay on top in their own section. The Pinned, Folders and Notes sections can be collapsed and reordered.
- Colors for notes, a default color for new notes, and filtering and sorting by color.
- Sort by last edit, creation date, title, type or color. Moving, pinning or coloring a note does not count as an edit. Each folder remembers its own sort order, one tap resets it.
- Filter by text notes or checklists, and search across all folders.
- Archive notes you want out of the way without deleting them.
- Trash. Deleted notes stay there for 30 days by default (configurable from immediately up to 90 days) and can be restored. Every delete has an undo snackbar.
- List or grid. The grid has 1 to 5 columns, and the preview length goes from title only to extra long. Timestamps and the note icon can be hidden.
- Light, dark and AMOLED mode, seven color schemes including Dynamic Color on Android 12 and newer, and five text sizes.
- A custom app title, an in-app changelog and a "What's new" sheet after updates.

## Widgets

- A single note or checklist, a scrollable list of notes, and a one tap "new note" button.
- Tap a text note in the widget to edit it in a small card over the home screen. Checklist widgets have a "+" to add items.
- Check items directly in the widget.
- Per widget: background opacity, text size and a lock against accidental edits. The list widget can hide its header, pinned notes or folders, and show a single folder.
- Images in widgets, and widgets as large as your launcher allows.

## Sync

- Works with any WebDAV server: Nextcloud, ownCloud, a NAS or the [Docker server](../server/README.md) in this repo. Basic and Digest authentication.
- Syncs after saving, on app start, when Wi-Fi connects, every 15, 30 or 60 minutes and after a restart. Each trigger can be turned off. See [when the app syncs](../QUICKSTART.md#3-when-the-app-syncs).
- Folders become subfolders on the server, images are synced too.
- A note changed on two devices is not overwritten. You choose which version stays, with a side by side comparison. See [SYNC.md](SYNC.md#conflicts).
- Notes deleted on another device land in the local trash.
- A sync status dialog that shows the last successful sync, conflicts, missing files and failed syncs, each with a next step.
- Notifications only for errors, warnings and conflicts by default.
- Optional Markdown copies of every note on the server, for Obsidian or any other editor. Changes made there come back into the app.
- [Simple Notes Desktop](https://github.com/inventory69/simple-notes-desktop) for Windows and Linux uses the same server folder.
- Sync pauses safely when another device encrypted the sync folder. See [SYNC.md](SYNC.md#sync-paused).
- A configurable sync folder name, VPNs, and a Wi-Fi only option.
- Self-signed certificates: trust one by its SHA-256 fingerprint in the connection test, or install your own CA on the phone. See [self-signed certificates](../QUICKSTART.md#4-self-signed-certificates).

## Backup

- Backup to a single JSON file wherever you want, including folders and images, and optionally all app settings.
- Optional password encryption.
- Restore by merging, replacing everything, or letting the backup win on duplicates. A safety backup is made before every restore.
- Details in [BACKUP.md](BACKUP.md).

## Import

- Google Keep: import a Takeout export, including checklists and colors.
- Import `.md`, `.json` and `.txt` files from the device or from the WebDAV server, with a choice for duplicates.

## Privacy and security

- No account, no analytics, no ads, no Google services. AGPL v3.
- Server credentials are encrypted with a key from the Android Keystore and excluded from Android cloud backups and device transfers.
- `http://` only for addresses in your home network, everything else needs HTTPS.
- App lock with fingerprint, face or device PIN, a configurable grace period, and screenshot protection that also hides the app in the recent apps view.
- An activity log of what happened to which note and why a sync started. It stays on the device.
- An optional debug log. When you share it, server address, username, note titles and folder names are replaced with placeholders.

### Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Talk to your WebDAV server |
| `ACCESS_NETWORK_STATE` | Know whether you are on Wi-Fi, for the background triggers |
| `POST_NOTIFICATIONS` | Report sync problems and conflicts |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask to be exempt from battery optimization, so background sync runs |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Run an immediate sync on Android 9 to 11, where WorkManager needs a foreground service for it |
| `RECEIVE_BOOT_COMPLETED` | Restart background sync after a reboot, only if you turned that on |
| `USE_BIOMETRIC` | App lock |

No location, contacts, storage or camera permission. Files and images are picked through the Android file picker.

## Languages

12 languages: English, German, Spanish, French, Indonesian, Italian, Norwegian Bokmål, Polish, Russian, Turkish, Ukrainian and Chinese (Simplified). The app follows the system language, or you pick one in the settings. Translations are done by volunteers on [Weblate](https://hosted.weblate.org/engage/simple-notes-sync/), a new language ships once it reaches 40 percent of about 950 strings.

## Requirements

Android 7.0 or newer. A WebDAV server only if you want to sync.
