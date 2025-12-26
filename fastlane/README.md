# F-Droid Metadata

Diese Verzeichnisstruktur enthält alle Metadaten für die F-Droid-Veröffentlichung.

## Struktur

```
fastlane/metadata/android/de-DE/
├── title.txt                 # App-Name (max 50 Zeichen)
├── short_description.txt     # Kurzbeschreibung (max 80 Zeichen)
├── full_description.txt      # Vollständige Beschreibung (max 4000 Zeichen)
├── changelogs/
│   └── 1.txt                 # Changelog für Version 1
└── images/
    └── phoneScreenshots/     # Screenshots (PNG/JPG, 320-3840px breit)
        ├── 1.png             # Hauptansicht (Notizliste)
        ├── 2.png             # Notiz-Editor
        ├── 3.png             # Settings
        └── 4.png             # Empty State
```

## Screenshots erstellen

Verwende einen Android Emulator oder physisches Gerät mit:
- Material You Theme aktiviert
- Deutsche Sprache
- Screenshots in hoher Auflösung (1080x2400 empfohlen)

### Screenshot-Reihenfolge:
1. **Notizliste** - Mit mehreren Beispiel-Notizen, Sync-Status sichtbar
2. **Editor** - Zeige eine bearbeitete Notiz mit Titel und Inhalt
3. **Settings** - Server-Konfiguration mit erfolgreichem Server-Status
4. **Empty State** - Schöne leere Ansicht mit Material 3 Card

## F-Droid Build-Konfiguration

Die App verwendet den `fdroid` Build-Flavor ohne proprietäre Dependencies.
Siehe `build.gradle.kts` für Details.
