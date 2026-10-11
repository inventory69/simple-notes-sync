# Contributing

Thanks for helping. Bug fixes, tests and documentation improvements are always welcome. For a new feature, please start a thread in [Discussions](https://github.com/inventory69/simple-notes-sync/discussions/categories/ideas) first. The app is meant to stay simple, so not every good idea makes it in, and it is better to find that out before you write the code.

## Build

You need JDK 17 or newer and the Android SDK (compile and target SDK 36, minimum SDK 24).

```bash
git clone https://github.com/inventory69/simple-notes-sync.git
cd simple-notes-sync/android
./gradlew detekt lint assembleFdroidDebug   # what has to pass before a PR
./gradlew test                              # unit tests
```

The debug APK lands in `app/build/outputs/apk/fdroid/debug/` and installs next to a release version (package `dev.dettmer.simplenotes.debug`).

There are two flavors: `fdroid` and `standard`. Both are fully FOSS and currently identical. Keep it that way: no Google Play Services, no Firebase, no proprietary libraries.

## Pull requests

- One topic per PR, branched from `main` (`fix/...`, `feature/...`).
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `fix(sync): ...`, `feat(editor): ...`, `docs: ...`.
- Every PR runs detekt, ktlint, a debug build of both flavors and the unit tests. It must be green before it can be merged.
- New UI text goes into `values/strings.xml` (English) only. The other languages come through Weblate.
- Say how you tested it, ideally on a real device.

## Translations

Translations happen on [Weblate](https://hosted.weblate.org/engage/simple-notes-sync/) only, please do not send pull requests that edit `values-*/strings.xml` directly. Weblate opens its own pull requests, and they go through the same build check.

A new language ships once it reaches 40 percent. Below that the app would still be mostly English. Languages below the threshold stay on Weblate and ship as soon as they pass.

## Documentation

The docs are English only, the German README is a short entry point. Keep them short and task oriented, and do not add version markers like "new in v2.x", the changelog has that.

`CHANGELOG.md` and `CHANGELOG.de.md` feed the in-app changelog, so both are kept.

## Where things are

All code lives in `android/app/src/main/java/dev/dettmer/simplenotes/`:

| Package | What |
|---|---|
| `models` | `Note`, checklist items, sync status |
| `storage` | Notes as JSON files on the device, folders, trash |
| `sync` | Sync logic, triggers, conflict handling, `sync/webdav/` is the WebDAV client (OkHttp) |
| `ui` | Jetpack Compose screens: `main`, `editor`, `settings`, `theme` |
| `widget` | Home screen widgets (Glance) |
| `markdown`, `images` | Markdown rendering, HTML paste, image handling |
| `backup` | Backup and restore, backup encryption |
| `noteimport` | Google Keep and file import |
| `security` | App lock |

The architecture is plain MVVM with `StateFlow`, no dependency injection framework and no database.

## License

By contributing you agree that your code is published under the [AGPL v3](LICENSE).
