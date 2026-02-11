# F-Droid Metadata

Diese Verzeichnisstruktur enthält alle Metadaten für die F-Droid-Veröffentlichung.

## Struktur

```
fastlane/metadata/android/
├── de-DE/                          # Deutsche Lokalisierung (primär)
│   ├── title.txt                   # App-Name (max 50 Zeichen)
│   ├── short_description.txt       # Kurzbeschreibung (max 80 Zeichen)
│   ├── full_description.txt        # Vollständige Beschreibung (max 4000 Zeichen)
│   ├── changelogs/
│   │   ├── 1.txt ... 21.txt        # Changelogs pro versionCode (max 500 Zeichen!)
│   └── images/
│       └── phoneScreenshots/       # Screenshots (PNG/JPG, 320-3840px breit)
│           ├── 1.png ... 5.png
└── en-US/                          # Englische Lokalisierung
    ├── title.txt
    ├── short_description.txt
    ├── full_description.txt
    ├── changelogs/
    │   ├── 1.txt ... 21.txt
    └── images/
        └── phoneScreenshots/
```

## Wichtige Limits

| Feld | Max. Länge | Hinweis |
|------|-----------|---------|
| `title.txt` | 50 Zeichen | App-Name |
| `short_description.txt` | 80 Zeichen | Kurzbeschreibung |
| `full_description.txt` | 4000 Zeichen | Vollständige Beschreibung |
| `changelogs/*.txt` | **500 Bytes** | Pro versionCode, **Bytes nicht Zeichen!** |

> **Achtung:** Changelogs werden in **Bytes** gemessen! UTF-8 Umlaute (ä, ö, ü) zählen als 2 Bytes.

## Screenshots erstellen

Verwende ein physisches Gerät oder Emulator mit:
- Material You Theme aktiviert
- Deutsche/Englische Sprache je nach Locale
- Screenshots in hoher Auflösung (1080x2400 empfohlen)

## F-Droid Build-Konfiguration

Die App verwendet den `fdroid` Build-Flavor ohne proprietäre Dependencies.
Siehe `android/app/build.gradle.kts` für Details.

## Aktuelle Version

- **versionName:** 1.8.1
- **versionCode:** 21
