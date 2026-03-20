# Geplante Features 🚀

**🌍 Sprachen:** **Deutsch** · [English](UPCOMING.md)

> Was kommt als Nächstes? Hier findest du unsere Pläne für zukünftige Versionen.

---

## v1.5.0 - Jetpack Compose & Internationalisierung ✅

> **Status:** Released 🎉 (Januar 2026)

### 🎨 Jetpack Compose UI

- ✅ **Komplettes UI-Redesign** - Von XML-Views zu Jetpack Compose
- ✅ **Modernisierte Einstellungen** - 7 kategorisierte Screens
- ✅ **Selection Mode** - Long-Press für Mehrfachauswahl
- ✅ **Silent-Sync Mode** - Kein Banner bei Auto-Sync

### 🌍 Mehrsprachigkeit

- ✅ **Englisch + Deutsch** - 400+ übersetzte Strings
- ✅ **Automatische Spracherkennung** - Folgt der System-Sprache
- ✅ **Per-App Language (Android 13+)** - Native Sprachauswahl

### 🎨 UI-Verbesserungen

- ✅ **Splash Screen** - App-Foreground-Icon
- ✅ **App Icon** - In About Screen und Empty State
- ✅ **Slide-Animationen** - Flüssige Übergänge im NoteEditor

---

## v1.6.0 - Technische Modernisierung ✅

> **Status:** Released 🎉 (Januar 2026)

### ⚙️ Konfigurierbare Sync-Trigger

- ✅ **Individuelle Trigger-Kontrolle** - Jeden Sync-Trigger einzeln aktivieren/deaktivieren
- ✅ **Ereignisbasierte Defaults** - onSave, onResume, WiFi-Connect standardmäßig aktiv
- ✅ **Periodischer Sync optional** - 15/30/60 Min Intervalle (Standard: AUS)
- ✅ **Boot Sync optional** - Periodischen Sync nach Geräteneustart starten (Standard: AUS)
- ✅ **Offline-Modus UI** - Ausgegraute Toggles wenn kein Server konfiguriert
- ✅ **Akku-optimiert** - ~0.2%/Tag mit Defaults, bis zu ~1.0% mit Periodic

---

## v1.6.1 - Clean Code ✅

> **Status:** Released 🎉 (Januar 2026)

### 🧹 Code-Qualität

- ✅ **detekt: 0 Issues** - Alle 29 Code-Qualitäts-Issues behoben
- ✅ **Zero Build Warnings** - Alle 21 Deprecation Warnings eliminiert
- ✅ **ktlint reaktiviert** - Mit Compose-spezifischen Regeln
- ✅ **CI/CD Lint-Checks** - In PR Build Workflow integriert
- ✅ **Constants Refactoring** - Dimensions.kt, SyncConstants.kt

---

## v1.7.0 - Grid View, WiFi-Only & VPN ✅

> **Status:** Released 🎉 (Januar 2026)

### 🎨 Grid Layout

- ✅ **Pinterest-artiges Staggered Grid** - Lückenfreies Layout mit dynamischen Vorschauzeilen
- ✅ **Layout-Umschalter** - Zwischen Listen- und Grid-Ansicht wechseln
- ✅ **Adaptive Spalten** - 2-3 Spalten basierend auf Bildschirmgröße

### 📡 Sync-Verbesserungen

- ✅ **WiFi-Only Sync Toggle** - Nur über WiFi synchronisieren
- ✅ **VPN-Unterstützung** - Sync funktioniert korrekt über VPN-Tunnels
- ✅ **Self-Signed SSL** - Dokumentation und Unterstützung für selbstsignierte Zertifikate
- ✅ **Server-Wechsel-Erkennung** - Alle Notizen auf PENDING zurückgesetzt bei URL-Änderung

---

## v1.7.1 - Android 9 Fix & VPN ✅

> **Status:** Released 🎉 (Februar 2026)

- ✅ **Android 9 Crash Fix** - `getForegroundInfo()` für WorkManager auf API 28 implementiert
- ✅ **VPN-Kompatibilität** - WiFi Socket-Binding erkennt Wireguard VPN-Interfaces
- ✅ **SafeSardineWrapper** - Saubere HTTP-Verbindungs-Bereinigung

---

## v1.7.2 - Timestamp & Löschungs-Fixes ✅

> **Status:** Released 🎉 (Februar 2026)

- ✅ **Server-mtime als Wahrheitsquelle** - Behebt Timestamp-Probleme mit externen Editoren
- ✅ **Deletion Tracker Mutex** - Thread-sichere Batch-Löschungen
- ✅ **ISO8601 Timezone-Parsing** - Multi-Format-Unterstützung
- ✅ **E-Tag Batch-Caching** - Performance-Verbesserung
- ✅ **Memory Leak Prävention** - SafeSardineWrapper mit Closeable

---

## v1.8.0 - Widgets, Sortierung & Erweiterter Sync ✅

> **Status:** Released 🎉 (Februar 2026)

### 📌 Homescreen-Widgets

- ✅ **Volles Jetpack Glance Framework** - 5 responsive Größenklassen
- ✅ **Interaktive Checklisten** - Checkboxen die zum Server synchronisieren
- ✅ **Material You Farben** - Dynamische Farben mit einstellbarer Opazität
- ✅ **Sperr-Umschalter** - Versehentliche Bearbeitungen verhindern
- ✅ **Konfigurations-Activity** - Notiz-Auswahl und Einstellungen

### 📊 Sortierung

- ✅ **Notiz-Sortierung** - Nach Titel, Änderungsdatum, Erstelldatum, Typ
- ✅ **Checklisten-Sortierung** - Manuell, alphabetisch, offene zuerst, erledigte zuletzt
- ✅ **Visuelle Trenner** - Zwischen offenen/erledigten Gruppen
- ✅ **Drag über Grenzen** - Auto-Toggle beim Überqueren des Trenners

### 🔄 Sync-Verbesserungen

- ✅ **Parallele Downloads** - Bis zu 5 gleichzeitig (konfigurierbar)
- ✅ **Server-Löschungs-Erkennung** - Erkennt auf anderen Clients gelöschte Notizen
- ✅ **Live Sync-Fortschritt** - Phasen-Anzeige mit Zählern
- ✅ **Sync-Status Legende** - Hilfe-Dialog für alle Sync-Icons

### ✨ UX

- ✅ **Post-Update Changelog** - Zeigt lokalisierten Changelog nach Update
- ✅ **Grid als Standard** - Neue Installationen starten im Grid-Modus
- ✅ **Toast → Banner Migration** - Einheitliches Benachrichtigungssystem

---

## v1.8.1 - Bugfix & Polish ✅

> **Status:** Released 🎉 (Februar 2026)

- ✅ **Checklisten-Sortierung Persistenz** - Sortier-Option korrekt wiederhergestellt
- ✅ **Widget Scroll Fix** - Scroll funktioniert auf Standard 3×2 Widget-Größe
- ✅ **Widget Checklisten-Sortierung** - Widgets übernehmen gespeicherte Sortier-Option
- ✅ **Drag Cross-Boundary** - Drag & Drop über Checked/Unchecked-Trenner
- ✅ **Sync Rate-Limiting** - Globaler 30s Cooldown zwischen Auto-Syncs
- ✅ **Detekt: 0 Issues** - Alle 12 Findings behoben

---

## v1.8.2 - Stabilität & Editor-Fixes ✅

> **Status:** Released 🎉 (Februar 2026)

- ✅ **26 Bugfixes** - Sync-Deadlocks, Datenverlust-Prävention, Editor-UX
- ✅ **Self-Signed SSL Support** - User-CA-Zertifikate in Release-Builds
- ✅ **Widget Scroll Fix** - Scrollbarer Text in mittleren Widgets
- ✅ **Keyboard Auto-Capitalization** - Titel-Feld, Checklisten-Items
- ✅ **APK-Größen-Optimierung** - Granulare ProGuard-Regeln (< 5 MB)
- ✅ **Checklisten Drag-Stabilität** - Cross-Boundary Drag & Drop Fix

---

## v1.9.0 - Filter, Suche, Markdown & Widget-Polish ✅

> **Status:** Released 🎉 (Februar 2026)

### Part 1: Sync-Qualität & Import
- ✅ **Notiz-Import-Assistent** - Import von WebDAV oder lokal (.md, .json, .txt)
- ✅ **Parallele Uploads** - ~2× schnellerer Multi-Notiz-Sync
- ✅ **Vereinheitlichte parallele Verbindungen** - Eine Einstellung für Uploads & Downloads
- ✅ **Server-Wechsel-Fix** - E-Tag/Content-Hash-Caches bei Wechsel geleert
- ✅ **Löscherkennung-Fix** - Schwellenwert für kleine Notiz-Portfolios angehoben
- ✅ **Markdown-Export-Serialisierung** - Mutex verhindert Race Condition
- ✅ **E-Tag-Caching** - Unnötige Re-Downloads vermieden

### Part 2: UI-Features

#### 📊 Filter & Suche
- ✅ **Filter Chip Row** - Filtern nach Alle / Text / Checklisten
- ✅ **Inline-Suche** - Schnellsuchfeld in der Filter-Zeile
- ✅ **Sortierung in Filter-Zeile** - Sort-Button aus Dialog in Filter-Zeile verschoben
- ✅ **Filter-Zeile Toggle** - Tune-Button in TopAppBar zum Ein-/Ausblenden

#### ✏️ Editor
- ✅ **Markdown-Vorschau** - Live-Vorschau für Textnotizen mit Formatierungs-Toolbar
- ✅ **Checklisten Un-Check Restore** - Item kehrt an Originalposition zurück
- ✅ **Checklisten-Reihenfolge Zementierung** - Originalreihenfolge bleibt nach Einfügen/Löschen erhalten
- ✅ **Checklisten-Scroll-Verhalten** - Konsistentes Scrollen bei Check/Un-Check
- ✅ **Opt-in Autosave** - Konfigurierbarer Debounce-Autosave-Timer
- ✅ **Konfigurierbarer Sync-Ordner** - Benutzerdefinierter WebDAV-Ordnername

#### 📌 Widget-Verbesserungen
- ✅ **Monet-Farbton-Erhaltung** - Transluzenter Hintergrund behält dynamische Farben
- ✅ **Nahtlose Options-Leiste** - Hintergrund entfernt für saubereren Look
- ✅ **Checklisten-Durchstreichung** - Erledigte Items zeigen Durchstreichung
- ✅ **onStop Widget-Refresh** - Widgets aktualisieren beim Verlassen der App

#### ✨ Sonstiges
- ✅ **Benutzerdefinierter App-Titel** - Konfigurierbarer App-Name in Einstellungen
- ✅ **Scroll-to-Top bei Sync** - Liste scrollt nach oben nach manuellem Sync

---

## v2.0.0 - Compose-Rewrite & Multi-Theme ✅

> **Status:** Released 🎉 (März 2026)

### 🎨 Multi-Theme-System
- ✅ **7 Farbschemata** - Inkl. AMOLED & Dynamic Color mit animierten Übergängen und getönten Oberflächen
- ✅ **Grid-Spaltensteuerung** - 1–5 Spalten konfigurierbar in Anzeigeeinstellungen
- ✅ **Grid-Chips** - Ersetzen Radio-Buttons in Anzeigeeinstellungen

### ✨ Editor & Einstellungen
- ✅ **Vollständiges Backup/Restore** - Beinhaltet alle App-Einstellungen, nicht nur Notizen
- ✅ **Material 3 Shared-Axis-Übergänge** - Für alle Navigationen und Back-Gesten
- ✅ **Autosave-Status** - In Anzeigeeinstellungen-Untertitel angezeigt
- ✅ **Debug-Logging-Dialog** - Logging nach Export automatisch deaktivieren

### 🐛 Bug Fixes
- ✅ **Checklisten Drag-and-Drop** - Für Stabilität in langen Listen neu geschrieben
- ✅ **Offline-Löschungen** - Für nächsten Sync eingereiht
- ✅ **WebDAV-403-Kompatibilität** - HTTP 403 als existierend behandelt
- ✅ **Thread-Safety** - State-Inkonsistenz und Dispatcher-Probleme behoben
- ✅ **Ressourcen-Lecks** - InputStreams geschlossen, File I/O vom Main Thread
- ✅ **Save-on-Back Race-Condition** - TextFieldState flush + onPause-Save

### 🗑️ Legacy-Code Entfernung
- ✅ **SettingsActivity entfernt** - Durch Compose-Settings ersetzt
- ✅ **MainActivity entfernt** - Durch ComposeMainActivity ersetzt
- ✅ **NoteEditorActivity entfernt** - Durch Compose-Editor ersetzt
- ✅ **XML-Layouts, Menüs, Drawables entfernt** - Komplettes Compose UI
- ✅ **LocalBroadcastManager → SharedFlow** - Moderne Event-Architektur
- ✅ **viewModelFactory DSL** - Moderne ViewModel-Erstellung

### 🏗️ Architektur
- ✅ **WebDavSyncService → Facade-Pattern** - Aufgeteilt in 9 extrahierte Module
- ✅ **R8/ProGuard optimiert** - APK-Größe reduziert

### 📄 Lizenz
- ✅ **MIT → Apache 2.0** - Lizenzwechsel

---

## 📋 Backlog

> Features für zukünftige Überlegungen

### 🔐 Sicherheits-Verbesserungen

- **Passwortgeschützte lokale Backups** - Backup-ZIP mit Passwort verschlüsseln
- **Biometrische Entsperrung** - Fingerabdruck/Gesichtserkennung für App

### 🎨 UI Features

- **Ordner / Tags / Notebooks** - Notizen in Verzeichnisse oder mit Tags organisieren für bessere Trennung (z.B. persönliche Notizen vs. geteilte Rezepte). Unterverzeichnisse auf WebDAV ermöglichen auch ordnerbezogene Zugriffskontrolle. ([#38](https://github.com/inventory69/simple-notes-sync/discussions/38) von @happy-turtle)
- **Erledigte Checklisten ausblenden** - Option, Checklisten bei denen alle Items abgehakt sind auszublenden, mit separater Ansicht zum späteren Wiederherstellen. ([#45](https://github.com/inventory69/simple-notes-sync/discussions/45) von @isawaway)
- **Suche** - Volltextsuche in Notizen

### 🌍 Community

- **Zusätzliche Sprachen** - Community-Übersetzungen (FR, ES, IT, ...)

---

## 💡 Feedback & Wünsche

Hast du eine Idee für ein neues Feature?

- **[Feature Request erstellen](https://github.com/inventory69/simple-notes-sync/issues/new?template=feature_request.yml)**
- **[Bestehende Wünsche ansehen](https://github.com/inventory69/simple-notes-sync/issues?q=is%3Aissue+label%3Aenhancement)**

---

**Hinweis:** Diese Roadmap zeigt unsere aktuellen Pläne. Prioritäten können sich basierend auf Community-Feedback ändern.

[← Zurück zur Dokumentation](DOCS.md)
