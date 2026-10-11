<div align="center">
<img src="android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="Logo" />
</div>

<h1 align="center">Simple Notes Sync</h1>

<div align="center">

[![Release](https://img.shields.io/github/v/release/inventory69/simple-notes-sync?style=for-the-badge&label=Release)](https://github.com/inventory69/simple-notes-sync/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![License](https://img.shields.io/badge/License-AGPL_v3-blue?style=for-the-badge&logo=gnu)](LICENSE)
[![Donate](https://img.shields.io/liberapay/receives/inventory?style=for-the-badge&logo=liberapay&logoColor=white&label=Donate)](https://liberapay.com/inventory/)

</div>

<div align="center">

<a href="https://apt.izzysoft.de/fdroid/index/apk/dev.dettmer.simplenotes">
<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
alt="Get it on IzzyOnDroid" align="center" height="80" /></a>

<a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/inventory69/simple-notes-sync">
<img src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png"
alt="Get it on Obtainium" align="center" height="80" />
</a>

<a href="https://f-droid.org/packages/dev.dettmer.simplenotes">
<img src="https://f-droid.org/badge/get-it-on.png"
alt="Get it on F-Droid" align="center" height="80" /></a>

<a href="https://play.google.com/store/apps/details?id=dev.dettmer.simplenotes">
<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
alt="Get it on Google Play" align="center" height="80" /></a>

</div>

<div align="center">
<strong>SHA-256 hash of the signing certificate:</strong><br />42:A1:C6:13:BB:C6:73:04:5A:F3:DC:81:91:BF:9C:B6:45:6E:E4:4C:7D:CE:40:C7:CF:B5:66:FA:CB:69:F1:6A
<br /><br />
<strong>English</strong> · <a href="README.de.md">Deutsch</a>
</div>

Notes for Android that work offline and sync with your own WebDAV server. No account, no cloud, no tracking.

I built this so my notes would sync with the WebDAV server at home on their own, as soon as I get home. That is still the core idea. Everything else grew around it, and the app works just as well without any server.

<p align="center">
  <img src="docs/screenshots/en/1-overview.png" width="250" alt="Notes overview">
  <img src="docs/screenshots/en/2-editor.png" width="250" alt="Note editor">
  <img src="docs/screenshots/en/3-checklist.png" width="250" alt="Checklist">
  <img src="docs/screenshots/en/4-sync.png" width="250" alt="Self-hosted server settings">
  <img src="docs/screenshots/en/5-keep-import.png" width="250" alt="Google Keep import">
  <img src="docs/screenshots/en/6-themes.png" width="250" alt="Themes and AMOLED">
</p>

## Highlights

- **Your server, no account.** Notes live on your phone and sync with any WebDAV server: Nextcloud, ownCloud, a NAS or the small Docker server in this repo. Without a server the app works just as well.
- **No silent overwrites.** If a note changed on two devices, you compare both versions side by side and pick the one that stays.
- **Markdown that pastes well.** Live highlighting, tables and a preview. Pasting from a web page, Word or Google Docs keeps headings, lists, links and tables. Share a note as a PDF with formatting and images.
- **Images in notes.** Paste or pick them, resize them, place several side by side.
- **Fast checklists.** Drag an item across the divider to check it, unchecking puts it back where it was, and a read mode turns a list into tap to check.
- **Widgets you can write in.** Edit a note in a small card over the home screen and check items right in the widget.
- **Obsidian and desktop.** Optional Markdown copies of every note on the server, edits made there come back into the app. [Simple Notes Desktop](https://github.com/inventory69/simple-notes-desktop) for Windows and Linux uses the same folder.
- **Coming from Google Keep?** Import a Takeout export, checklists and colors included.
- **Private by design.** Local only folders that never leave the phone, app lock, encrypted backups, no analytics and no Google services. 12 languages.

All features: [docs/FEATURES.md](docs/FEATURES.md)

## Quick start

**Server** (any machine with Docker):

1. Get the server files:
   ```bash
   git clone https://github.com/inventory69/simple-notes-sync.git
   cd simple-notes-sync/server
   ```
2. Create the config and set a password under `users:`:
   ```bash
   cp config.yml.example config.yml
   ```
3. Start it:
   ```bash
   docker compose up -d
   ```

**App:**

1. Install it from one of the stores above.
2. Open Settings → Server Settings, switch off Offline Mode and enter
   URL `http://SERVER-IP:8080/`, username `noteuser` and the password from `config.yml`.
3. Tap **Test Connection**. From now on your notes sync on their own.

Already running Nextcloud or another WebDAV server? Skip the server part. The [quick start guide](QUICKSTART.md) covers that, HTTPS and when the app syncs in the background.

## What's next

Optional end-to-end encryption for the sync folder is in the works ([#9](https://github.com/inventory69/simple-notes-sync/issues/9)). There is no release date yet. Ideas and votes on what comes after that live in [Discussions](https://github.com/inventory69/simple-notes-sync/discussions/categories/ideas).

## Documentation

- [Quick start](QUICKSTART.md): server, app, background sync, self-signed certificates, updates
- [Features](docs/FEATURES.md): everything the app does, and the permissions it asks for
- [Sync](docs/SYNC.md): what lands on the server, conflicts, deletions, Markdown files and the desktop app
- [Backup](docs/BACKUP.md): backup files, encryption and restore modes
- [Troubleshooting](docs/TROUBLESHOOTING.md): ordered checks for the common problems
- [Server](server/README.md): the Docker server in detail
- [Changelog](CHANGELOG.md)

## Contributing

Bug reports and pull requests are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md). Feature ideas go to [Discussions](https://github.com/inventory69/simple-notes-sync/discussions/categories/ideas).

Translations happen on [Weblate](https://hosted.weblate.org/engage/simple-notes-sync/), which hosts this project for free. Thank you!

[![Translation status](https://hosted.weblate.org/widget/simple-notes-sync/android-app/svg-badge.svg)](https://hosted.weblate.org/engage/simple-notes-sync/)

If the app is useful to you, you can support it on [Liberapay](https://liberapay.com/inventory/).

## License

GNU Affero General Public License v3.0, see [LICENSE](LICENSE).
