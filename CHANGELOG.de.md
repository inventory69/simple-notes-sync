# Changelog

Alle wichtigen Änderungen an Simple Notes Sync werden in dieser Datei dokumentiert.

Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

**🌍 Sprachen:** **Deutsch** · [English](CHANGELOG.md)

---

## [1.10.0] - 2026-02-27

### ✏️ Editor-Qualität & Sync-Polish

Feature-Release mit Rückgängig/Wiederherstellen, konfigurierbarem Verbindungs-Timeout, Speichern beim Zurücknavigieren, Autosave-Fixes, sanfteren Sync-Banner-Animationen und einem Pre-Download-Filter für fremde JSON-Dateien.

### ✨ Neue Features

**Rückgängig/Wiederherstellen im Notiz-Editor** ([484bf3a](https://github.com/inventory69/simple-notes-sync/commit/484bf3a))
- Vollständige Undo/Redo-Unterstützung für Text-Notizen und Checklisten via Toolbar-Buttons
- Debounced Snapshots: schnelles Tippen wird zu einem einzelnen Undo-Schritt gruppiert (500 ms Fenster)
- Stack auf 50 Einträge begrenzt; wird beim Notizwechsel geleert um Cross-Note-Undo zu verhindern
- Wiederhergestellte Snapshots aktualisieren die Cursor-Position korrekt

**Konfigurierbarer WebDAV-Verbindungs-Timeout** ([b1aebc4](https://github.com/inventory69/simple-notes-sync/commit/b1aebc4))
- Neuer Settings-Slider (1–30 s, Standard 8 s) zur Konfiguration des WebDAV-Timeouts
- Wird auf alle OkHttpClient-Instanzen angewendet (Connect, Read, Write)
- Einheitliche, nutzerfreundliche Fehlermeldungen für Timeout, Auth-Fehler, Nicht gefunden und Server-Fehler

**Markdown-Auto-Sync Timeout-Schutz** ([7f74ae9](https://github.com/inventory69/simple-notes-sync/commit/7f74ae9))
- Aktivierung von Markdown-Auto-Sync hat jetzt einen 10-s-Timeout für den initialen Export
- UI-Toggle aktualisiert optimistisch und kehrt bei Fehler oder Timeout zurück
- Verhindert, dass der Einstellungs-Screen bei unerreichbaren Servern endlos hängt

**Speichern beim Zurücknavigieren** ([402382c](https://github.com/inventory69/simple-notes-sync/commit/402382c))
- Ungespeicherte Notizen werden beim Verlassen des Editors automatisch gespeichert (System-Zurück + Toolbar-Zurück)
- Nur aktiv wenn Autosave aktiviert ist; synchrones Speichern ohne Sync auszulösen
- Autosave-Toggle-Beschreibung erwähnt jetzt dieses Verhalten

### 🐛 Fehlerbehebungen

**Falsches Autosave beim Tippen in Checkliste** ([9ea7089](https://github.com/inventory69/simple-notes-sync/commit/9ea7089))
- Antippen eines Checklisten-Items zum Platzieren des Cursors löst kein falsches Autosave mehr aus
- No-Op-Guards in `updateChecklistItemText()` und `updateChecklistItemChecked()` — nur dirty markieren wenn sich der Wert tatsächlich geändert hat

**Undo auf Originalzustand löste trotzdem Autosave aus** ([cf5027b](https://github.com/inventory69/simple-notes-sync/commit/cf5027b))
- Rückgängig-Machen aller Änderungen zum letzten Speicherzustand setzt `isDirty` jetzt korrekt zurück und bricht das ausstehende Autosave ab
- Neues `savedSnapshot`-Property erfasst den Zustand beim Laden und nach jedem Speichern
- `applySnapshot()` vergleicht gegen `savedSnapshot` um den Dirty-State zu bestimmen

**Fremde JSON-Dateien unnötig heruntergeladen** ([c409243](https://github.com/inventory69/simple-notes-sync/commit/c409243))
- Nicht-Notiz-JSON-Dateien (z.B. `google-services.json`) werden jetzt vor dem Download via UUID-Format-Check gefiltert
- Zuvor: Datei wurde heruntergeladen, geparst und nach ID-Mismatch verworfen — verschwendete Bandbreite und kurzes Aufblitzen im Sync-Banner

**Notiz-Anzahl-Strings nicht korrekt pluralisiert** ([8ca8df3](https://github.com/inventory69/simple-notes-sync/commit/8ca8df3))
- Notiz-Anzahl-Strings in korrekte Android-Pluralformen konvertiert (EN + DE)

### 🎨 UI-Verbesserungen

**Sanfte Sync-Banner-Animationen** ([c409243](https://github.com/inventory69/simple-notes-sync/commit/c409243))
- Banner-Einblendung: fadeIn (300 ms, EaseOutCubic) — kein abruptes „Reinschieben von oben" mehr
- Banner-Ausblendung: fadeOut + shrinkVertically (300/400 ms, EaseInCubic)
- Phasen-Übergänge nutzen AnimatedContent-Crossfade (250 ms) für Textwechsel
- Mindest-Anzeigedauer pro aktiver Phase (400 ms) verhindert unlesbare Blitze
- Auto-Hide-Job vom Flow-Collector entkoppelt — garantierte Mindest-Anzeigedauer für Abgeschlossen/Fehler/Info-States

---

## [1.9.0] - 2026-02-25

### 🔄 Sync-Qualität, Performance & UI

Großes Release mit Notiz-Filterung, Markdown-Vorschau, konfigurierbarem Sync-Ordner, Opt-in-Autosave, Widget-Polish und signifikanten Sync-Verbesserungen — Server-Wechsel-Datenverlust behoben, parallele Uploads, Import-Assistent und drei Sync-Edge-Cases gelöst.

### 🐛 Fehlerbehebungen

**Erster Sync schlägt fehl wenn /notes/-Ordner auf Server fehlt** ([e012d17](https://github.com/inventory69/simple-notes-sync/commit/e012d17))
- Der erste Sync schlägt nicht mehr still fehl, wenn das Verzeichnis `/notes/` noch nicht auf dem Server angelegt wurde
- Ursache: `checkServerForChanges()` lieferte `false` (keine Änderungen) statt `true` (fortfahren), wenn `lastSyncTime > 0` und der Ordner fehlte
- Fix: gibt `true` zurück, damit der initiale Upload startet — der Server legt den Ordner beim ersten PUT automatisch an

**Server-Wechsel verursacht falschen "Auf Server gelöscht"-Status** ([0985209](https://github.com/inventory69/simple-notes-sync/commit/0985209))
- Der Wechsel zu einem neuen Server markiert lokale Notizen nicht mehr fälschlich als gelöscht
- Ursache: E-Tag- und Content-Hash-Caches des alten Servers wurden nicht geleert — Upload-Skip feuerte fälschlich, Notizen erschienen als SYNCED ohne tatsächlich hochgeladen zu sein
- Fix: `clearServerCaches()` leert alle E-Tag-, Content-Hash-, Sync-Timestamp- und Deletion-Tracker-Einträge bei Server-Wechsel
- `resetAllSyncStatusToPending()` setzt jetzt auch DELETED_ON_SERVER auf PENDING zurück

**Server-Löscherkennung zu aggressiv bei wenigen Notizen** ([56c0363](https://github.com/inventory69/simple-notes-sync/commit/56c0363))
- Nutzer mit 2–9 Notizen, die alle über die Nextcloud-Web-UI löschten, bekamen nie den DELETED_ON_SERVER-Status
- Guard-Schwellenwert von >1 auf ≥10 angehoben

**Race Condition bei parallelem Markdown-Export mit gleichen Titeln** ([56c0363](https://github.com/inventory69/simple-notes-sync/commit/56c0363))
- Zwei Notizen mit identischem Titel konnten sich gegenseitig die Markdown-Datei überschreiben
- Ursache: gleichzeitige `exists()` → `put()`-Sequenz ohne Synchronisation
- Fix: Markdown-Export wird per Mutex serialisiert (JSON-Uploads bleiben parallel)

**E-Tag nicht gecacht bei "Lokal neuer"-Download-Skip** ([56c0363](https://github.com/inventory69/simple-notes-sync/commit/56c0363))
- Wenn eine lokale Notiz neuer war als die Server-Version, wurde der Server-E-Tag nicht gespeichert
- Verursachte unnötige Re-Downloads bei jedem folgenden Sync
- Fix: E-Tag wird jetzt auch im else-Branch der Download-Ergebnis-Verarbeitung gespeichert

**Tune-Button-Farbe passt nicht zur Standard-Iconfarbe** ([135559a](https://github.com/inventory69/simple-notes-sync/commit/135559a))
- Untoggled-Tune-Button nutzt jetzt die Standard-TopAppBar-Iconfarbe statt einer eigenen Farbe

**Import-Assistent verliert Checklisten-Inhalt** ([5031848](https://github.com/inventory69/simple-notes-sync/commit/5031848))
- Checklisten-Erkennung beim Markdown-Import behält jetzt den vollständigen Notiz-Inhalt

**Checklisten-Scroll-Sprung beim Abhaken des ersten sichtbaren Items** ([8238af4](https://github.com/inventory69/simple-notes-sync/commit/8238af4))
- Abhaken des ersten sichtbaren Checklisten-Items verursacht keinen Scroll-Sprung mehr

**Checklisten-Originalreihenfolge verloren nach Einfügen/Löschen** ([e601642](https://github.com/inventory69/simple-notes-sync/commit/e601642))
- Originalreihenfolge wird nach Einfüge-/Löschoperationen zementiert, um Reihenfolge-Glitches zu verhindern

**Inkonsistentes Scrollen beim Check/Un-Check** ([19dfb03](https://github.com/inventory69/simple-notes-sync/commit/19dfb03))
- Konsistentes Scroll-Verhalten beim Abhaken und Aufheben von Checklisten-Items

### ✨ Neue Features

**Notizen-Import-Assistent** ([e012d17](https://github.com/inventory69/simple-notes-sync/commit/e012d17))
- Neuer Import-Screen in den Einstellungen — Notizen von WebDAV-Server oder lokalem Speicher importieren
- Unterstützte Formate: `.md` (mit/ohne YAML-Frontmatter), `.json` (Simple Notes Format oder generisch), `.txt` (Klartext)
- WebDAV-Scan: rekursiver Unterordner-Scan (Tiefe 1), berücksichtigt bestehende DeletionTracker-Einträge
- Notizen mit YAML-Frontmatter oder Simple Notes JSON werden als SYNCED importiert; andere als PENDING
- Erreichbar über Einstellungen → Import

**Parallele Uploads** ([187d338](https://github.com/inventory69/simple-notes-sync/commit/187d338))
- Notizen werden parallel statt sequentiell hochgeladen — ~2× schneller bei mehreren geänderten Notizen
- Upload-Zeit für 4 Notizen von ~11,5 s auf ~6 s reduziert (auf Gerät gemessen)
- Zweiter Sync mit unveränderten Notizen: Upload-Phase ~0 ms (alle per Content-Hash übersprungen)
- Begrenzte Parallelität via Semaphore; Datei-I/O-Schreibzugriffe via Mutex serialisiert
- Neu: `/notes-md/`-Existenzprüfung pro Sync-Lauf gecacht (spart ~480 ms × N exists()-Aufrufe)

**Vereinheitlichte Parallele-Verbindungen-Einstellung** ([ef200d0](https://github.com/inventory69/simple-notes-sync/commit/ef200d0))
- Parallele Downloads (1/3/5/7/10) und Uploads (versteckt, max 6) zu einer einzelnen "Parallele Verbindungen"-Einstellung zusammengeführt
- Neue Optionen: 1, 3, 5 (reduziert von 5 Optionen — 7 und 10 entfernt da Uploads auf 6 begrenzt)
- Nutzer mit 7 oder 10 werden automatisch auf 5 migriert
- Uploads zur Laufzeit auf `min(Einstellung, 6)` begrenzt

**Filter Chip Row** ([952755f](https://github.com/inventory69/simple-notes-sync/commit/952755f), [71a0469](https://github.com/inventory69/simple-notes-sync/commit/71a0469), [07c41bb](https://github.com/inventory69/simple-notes-sync/commit/07c41bb))
- Neue Filter-Leiste unter der TopAppBar — Notizen filtern nach Alle / Text / Checklisten
- Inline-Suchfeld für schnelle Notiz-Filterung nach Titel
- Sortier-Button aus Dialog in kompaktes Filter-Row-Icon verschoben
- Tune-Button in TopAppBar schaltet Filter-Zeile ein/aus

**Markdown-Vorschau** ([e83a89a](https://github.com/inventory69/simple-notes-sync/commit/e83a89a))
- Live-Markdown-Vorschau für Textnotizen mit Formatierungs-Toolbar
- Unterstützt Überschriften, Fett, Kursiv, Durchgestrichen, Listen, Trennlinien, Code-Blöcke
- Umschalten zwischen Bearbeitungs- und Vorschaumodus

**Benutzerdefinierter App-Titel** ([bf478c7](https://github.com/inventory69/simple-notes-sync/commit/bf478c7))
- Konfigurierbarer App-Name in den Einstellungen

**Konfigurierbarer WebDAV-Sync-Ordner** ([58cdf1e](https://github.com/inventory69/simple-notes-sync/commit/58cdf1e))
- Eigener Sync-Ordnername (Standard: `notes`, konfigurierbar für Multi-App-Setups)

**Opt-in Autosave** ([5800183](https://github.com/inventory69/simple-notes-sync/commit/5800183))
- Autosave mit Debounce-Timer (3s nach letzter Bearbeitung, konfigurierbar in Einstellungen)
- Standardmäßig deaktiviert, Opt-in über Einstellungen

**Nach oben scrollen nach manuellem Sync** ([4697e49](https://github.com/inventory69/simple-notes-sync/commit/4697e49))
- Notizliste scrollt nach Abschluss eines manuellen Syncs nach oben

### 🔄 Verbesserungen

**Widget: Monet-Farbton in transluzenten Hintergründen** ([0f5a734](https://github.com/inventory69/simple-notes-sync/commit/0f5a734))
- Monet-Dynamic-Color-Farbton in transluzenten Widget-Hintergründen erhalten

**Widget: Options-Leisten-Hintergrund entfernt** ([5e3273a](https://github.com/inventory69/simple-notes-sync/commit/5e3273a))
- Options-Leisten-Hintergrund für nahtlose Widget-Integration entfernt

**Widget: Durchstreichung für erledigte Items** ([eb9db2e](https://github.com/inventory69/simple-notes-sync/commit/eb9db2e))
- Erledigte Checklisten-Items in Widgets zeigen jetzt Durchstreichung

**Widget: Auto-Refresh bei onStop** ([2443908](https://github.com/inventory69/simple-notes-sync/commit/2443908))
- Widgets aktualisieren automatisch beim Verlassen der App (onStop Lifecycle Hook)

**Checkliste: Abhaken-Rückgängig stellt Originalposition wieder her** ([188a0f6](https://github.com/inventory69/simple-notes-sync/commit/188a0f6))
- Aufheben eines Hakens stellt das Item an seiner Originalposition wieder her

**Sortier-Button: Kompakter Icon-Button** ([a1bd15a](https://github.com/inventory69/simple-notes-sync/commit/a1bd15a))
- AssistChip durch kompakten IconButton + SwapVert-Icon ersetzt

### 🛠️ Intern

**Code-Qualität** ([6708156](https://github.com/inventory69/simple-notes-sync/commit/6708156))
- Deprecated `Icons.Outlined.Notes` → `Icons.AutoMirrored.Outlined.Notes` behoben
- Ungenutzten `Color`-Import aus ServerSettingsScreen + Detekt-Baseline-Eintrag entfernt
- Logger-Timestamps nutzen `Locale.ROOT` statt `Locale.getDefault()`
- Obsoleten `Build.VERSION_CODES.N`-Check entfernt (minSdk=24)

**Detekt-Compliance** ([f0e143c](https://github.com/inventory69/simple-notes-sync/commit/f0e143c))
- `ALL_DELETED_GUARD_THRESHOLD`-Konstante für MagicNumber-Compliance extrahiert

**ProGuard/R8-Verifikation**
- Release-Build verifiziert — keine Regeländerungen für v1.9.0 nötig

**Image Support auf v2.0.0 verschoben** ([845ba03](https://github.com/inventory69/simple-notes-sync/commit/845ba03))
- Lokales Bild-Embedding aus v1.9.0 Scope entfernt
- Feature als v2.0.0 Spezifikation mit vollständigem Architektur-Vorschlag erhalten

**Weblate PR-Workflow** ([efd782f](https://github.com/inventory69/simple-notes-sync/commit/efd782f))
- Weblate-Integration auf PR-basierten Übersetzungs-Workflow umgestellt

**Dokumentation** ([395d154](https://github.com/inventory69/simple-notes-sync/commit/395d154))
- Dokumentation für v1.8.2 und v1.9.0 aktualisiert (FEATURES, UPCOMING, QUICKSTART)
- Fehlerhafte Links in Docs behoben (schließt #22)

---

## [1.8.2] - 2026-02-16

### 🔧 Stabilität, Editor- & Widget-Verbesserungen

Großes Stabilitäts-Release mit 26 behobenen Problemen — Sync-Deadlocks, Datenverlust-Prävention, SSL-Zertifikate, Markdown-Sync-Loop, stille Download-Fehler, Editor-UX-Verbesserungen, Widget-Polish und APK-Größenoptimierung.

### 🐛 Fehlerbehebungen

**Sync blockiert dauerhaft bei "Bereits aktiv"** *(IMPL_01)* ([a62ab78](https://github.com/inventory69/simple-notes-sync/commit/a62ab78))
- 5 Code-Pfade in SyncWorker behoben, bei denen `tryStartSync()` aufgerufen wurde, aber der State nie zurückgesetzt wurde
- Early Returns rufen nun `SyncStateManager.reset()` auf
- CancellationException-Handler setzt State jetzt zurück statt ihn im SYNCING-Zustand zu belassen
- Ursache: SyncStateManager blieb dauerhaft im SYNCING-State

**Selbstsignierte SSL-Zertifikate in Release-Builds** *(IMPL_02)* ([b3f4915](https://github.com/inventory69/simple-notes-sync/commit/b3f4915))
- `<certificates src="user" />` zur Netzwerk-Sicherheitskonfiguration hinzugefügt
- User-installierte CA-Zertifikate funktionieren jetzt auch in Release-Builds

**Text-Notizen nicht scrollbar in mittleren Widgets** *(IMPL_04)* ([8429306](https://github.com/inventory69/simple-notes-sync/commit/8429306))
- NARROW_MED und WIDE_MED Widget-Größenklassen nutzen jetzt `TextNoteFullView` (scrollbar)
- 2x1- und 4x1-Widgets zeigen jetzt scrollbaren Textinhalt

**Tastatur Auto-Großschreibung** *(IMPL_05)* ([d93b439](https://github.com/inventory69/simple-notes-sync/commit/d93b439))
- Titel: `KeyboardCapitalization.Words`, Inhalt/Checklisten: `KeyboardCapitalization.Sentences`

**Dokumentation: Sortieroption-Benennung** *(IMPL_06)* ([465bd9c](https://github.com/inventory69/simple-notes-sync/commit/465bd9c))
- "color"/"Farbe" zu "type"/"Typ" in README-Dateien und F-Droid-Metadaten geändert

**Tastatur-Auto-Scroll für Text-Notizen** *(IMPL_07)* ([bc266b9](https://github.com/inventory69/simple-notes-sync/commit/bc266b9))
- TextNoteContent von `TextFieldValue`-API zu `TextFieldState`-API migriert
- Scrollt automatisch zur Cursor-Position wenn Tastatur öffnet

**Checklisten-Scroll-Sprung beim Tippen** *(IMPL_10)* ([974ef13](https://github.com/inventory69/simple-notes-sync/commit/974ef13))
- Fehlerhafte Auto-Scroll-Logik aus v1.8.1 durch Viewport-aware Scroll ersetzt
- Scrollt nur wenn Item tatsächlich unter den sichtbaren Bereich ragt

**Visueller Glitch beim schnellen Scrollen in Checklisten** *(IMPL_11)* ([82e8972](https://github.com/inventory69/simple-notes-sync/commit/82e8972))
- `isDragConfirmed`-State verhindert versehentliche Drag-Aktivierung beim Scrollen
- Ursache: `Modifier.animateItem()` verursachte Fade-Animationen beim Scrolling

**Checklisten-Drag am Separator unterbrochen** *(IMPL_26)* ([8828391](https://github.com/inventory69/simple-notes-sync/commit/8828391))
- Drag über die Erledigt/Offen-Trennlinie hinaus bricht nicht mehr ab
- Item bleibt im aktiven Drag während der Haken nahtlos gesetzt/entfernt wird
- Ursache: Getrennte `itemsIndexed`-Blöcke zerstörten die Composition beim Grenzübertritt — zu einheitlichem `items`-Block zusammengeführt

**SyncMutex-Deadlock durch clearSessionCache()-Exception** *(IMPL_13)* ([99f451b](https://github.com/inventory69/simple-notes-sync/commit/99f451b))
- `clearSessionCache()` in try-catch gewrappt im `finally`-Block
- Verhindert, dass Mutex dauerhaft gesperrt bleibt

**Falscher Error-Banner bei Sync-Abbruch** *(IMPL_14)* ([1c45680](https://github.com/inventory69/simple-notes-sync/commit/1c45680))
- CancellationException zeigt keinen Error-Banner mehr
- Doppelte State-Resets in SyncWorker catch-Blöcken entfernt

**Socket-Leak in isServerReachable()** *(IMPL_15)* ([fac54d7](https://github.com/inventory69/simple-notes-sync/commit/fac54d7))
- Socket wird jetzt in allen Code-Pfaden korrekt geschlossen

**CancellationException in ParallelDownloader verschluckt** *(IMPL_16)* ([4c34746](https://github.com/inventory69/simple-notes-sync/commit/4c34746))
- CancellationException wird jetzt weitergeworfen statt gefangen und erneut versucht
- Verhindert Endlosschleife wenn WorkManager Sync abbricht

**Checklisten-Datenverlust bei onResume** *(IMPL_17)* ([b436623](https://github.com/inventory69/simple-notes-sync/commit/b436623))
- Checklisten-Änderungen bleiben erhalten beim Zurückkehren aus Benachrichtigungsleiste
- Ursache: `onResume()` lud Notiz aus Datenbank neu, verwarf ungespeicherte Änderungen

**Doppelter Stale-Sync Cleanup** *(IMPL_18)* ([71ae747](https://github.com/inventory69/simple-notes-sync/commit/71ae747))
- Copy-Paste-Duplikat in `SimpleNotesApplication.onCreate()` entfernt

**NotesStorage-Shadow + Download-Abbruch** *(IMPL_19)* ([ede429c](https://github.com/inventory69/simple-notes-sync/commit/ede429c), [50ae9d8](https://github.com/inventory69/simple-notes-sync/commit/50ae9d8))
- Shadow-`NotesStorage`-Instanz in `hasUnsyncedChanges()` entfernt (19a)
- `runBlocking` durch `coroutineScope` ersetzt für korrekte Abbruch-Propagation (19b)
- Read-Timeout zu OkHttpClient-Instanzen hinzugefügt (19c)

**Stille Download-Fehler als Erfolg gemeldet** *(IMPL_21)* ([371d5e3](https://github.com/inventory69/simple-notes-sync/commit/371d5e3))
- Download-Exceptions werden jetzt propagiert statt still verschluckt
- Sync meldet korrekt Fehler wenn Downloads fehlschlagen

**PENDING-Notizen nicht erkannt** *(IMPL_22)* ([20de019](https://github.com/inventory69/simple-notes-sync/commit/20de019))
- `hasUnsyncedChanges()` prüft jetzt auf Notizen mit PENDING-Sync-Status
- Behebt Problem beim Server-Wechsel

**E-Tag/Timestamp Download-Reihenfolge** *(IMPL_23)* ([68dbb4e](https://github.com/inventory69/simple-notes-sync/commit/68dbb4e))
- E-Tag-Vergleich läuft jetzt vor Timestamp-Check (übersprang geänderte Notizen)
- Behebt Cross-Device-Sync wo Timestamps stimmten aber Inhalt sich unterschied

**Silent Sync zu sichtbarem Sync** *(IMPL_24)* ([940a494](https://github.com/inventory69/simple-notes-sync/commit/940a494))
- Pull-to-Refresh während Hintergrund-Sync zeigt jetzt Sync-Banner statt "bereits aktiv"-Fehler

**Markdown-Sync Feedback-Loop** *(IMPL_25)* ([74194d4](https://github.com/inventory69/simple-notes-sync/commit/74194d4))
- 5 Ursachen behoben die einen endlosen Export→Import→Re-Export-Zyklus verursachten
- UUID-Normalisierung, Server-mtime-Erhaltung, Zeitzonen-Vergleich, Pfad-Sanitierung, Inhaltstyp-Vergleich

### ✨ Neue Features

**Enter-Taste: Navigation von Titel zu Inhalt** *(IMPL_09)* ([81b9aca](https://github.com/inventory69/simple-notes-sync/commit/81b9aca))
- Titel-Feld ist jetzt einzeilig mit `ImeAction.Next`
- Enter/Weiter springt zum Inhaltsfeld oder erstem Checklisten-Item

### 🔄 Verbesserungen

**Widget-Inhalts-Padding** *(IMPL_08)* ([2ae5ce5](https://github.com/inventory69/simple-notes-sync/commit/2ae5ce5))
- Einheitliches Padding für alle Widget-Ansichten: 12dp horizontal, 4dp oben, 12dp unten

**Widget-Eintrags-Abstände** *(IMPL_12)* ([c3d4b33](https://github.com/inventory69/simple-notes-sync/commit/c3d4b33))
- Erhöhte Abstände in Checklisten- und Text-Widgets für bessere Lesbarkeit

**Sync-State-Timeout**
- 5-Minuten-Timeout für verwaiste Sync-States in `SyncStateManager`
- Verhindert dauerhaften Deadlock selbst wenn alle anderen Schutzmaßnahmen versagen

**Kaltstart State-Cleanup**
- `SimpleNotesApplication.onCreate()` setzt jetzt verwaiste SYNCING-States zurück

**APK-Größenoptimierung** *(IMPL_03)* ([7867894](https://github.com/inventory69/simple-notes-sync/commit/7867894))
- Breite ProGuard-Regel durch granulare Regeln ersetzt — behält nur was Reflection braucht

**Versionsanhebung**
- versionCode: 21 → 22
- versionName: 1.8.1 → 1.8.2

---

## [1.8.1] - 2026-02-11

### 🛠️ Bugfix & Polish Release

Checklisten-Fixes, Widget-Verbesserungen, Sync-Härtung und Code-Qualität.

### 🐛 Fehlerbehebungen

**Checklisten-Sortierung Persistenz** ([7dbc06d](https://github.com/inventory69/simple-notes-sync/commit/7dbc06d))
- Sortier-Option wurde beim erneuten Öffnen einer Checkliste nicht angewendet
- Ursache: `sortChecklistItems()` sortierte immer unchecked-first statt `_lastChecklistSortOption` zu lesen
- Alle Sortier-Modi werden nun korrekt wiederhergestellt (Manuell, Alphabetisch, Unchecked/Checked First)

**Widget-Scroll bei Standard-Größe** ([c72b3fe](https://github.com/inventory69/simple-notes-sync/commit/c72b3fe))
- Scrollen funktionierte nicht bei Standard-3×2-Widget-Größe (110–150dp Höhe)
- Neue Größenklassen `NARROW_SCROLL` und `WIDE_SCROLL` mit 150dp-Schwelle
- `clickable`-Modifier bei entsperrten Checklisten entfernt, um Scrollen zu ermöglichen

**Auto-Sync Toast entfernt** ([fe6935a](https://github.com/inventory69/simple-notes-sync/commit/fe6935a))
- Unerwartete Toast-Benachrichtigung bei automatischem Hintergrund-Sync entfernt
- Stiller Auto-Sync bleibt still; nur Fehler werden angezeigt

**Gradient- & Drag-Regression** ([24fe32a](https://github.com/inventory69/simple-notes-sync/commit/24fe32a))
- Gradient-Overlay-Regression bei langen Checklisten-Items behoben
- Drag-and-Drop-Flackern beim Verschieben zwischen Bereichen behoben

### 🆕 Neue Funktionen

**Widget-Checklisten: Sortierung & Trennlinien** ([66d98c0](https://github.com/inventory69/simple-notes-sync/commit/66d98c0))
- Widgets übernehmen die gespeicherte Sortier-Option aus dem Editor
- Visuelle Trennlinie zwischen unerledigten/erledigten Items (MANUAL & UNCHECKED_FIRST)
- Auto-Sortierung beim Abhaken von Checkboxen im Widget
- Emoji-Änderung: ✅ → ☑️ für erledigte Items

**Checklisten-Vorschau-Sortierung** ([2c43b47](https://github.com/inventory69/simple-notes-sync/commit/2c43b47))
- Hauptbildschirm-Vorschau (NoteCard, NoteCardCompact, NoteCardGrid) zeigt gespeicherte Sortierung
- Neuer `ChecklistPreviewHelper` mit geteilter Sortier-Logik

**Auto-Scroll bei Zeilenumbruch** ([3e4b1bd](https://github.com/inventory69/simple-notes-sync/commit/3e4b1bd))
- Checklisten-Editor scrollt automatisch wenn Text in eine neue Zeile umbricht
- Cursor bleibt am unteren Rand sichtbar während der Eingabe

**Separator Drag Cross-Boundary** ([7b55811](https://github.com/inventory69/simple-notes-sync/commit/7b55811))
- Drag-and-Drop funktioniert nun über die Checked/Unchecked-Trennlinie hinweg
- Items wechseln automatisch ihren Status beim Verschieben über die Grenze
- Extrahiertes `DraggableChecklistItem`-Composable für Wiederverwendbarkeit

### 🔄 Verbesserungen

**Sync-Ratenlimit & Akkuschutz** ([ffe0e46](https://github.com/inventory69/simple-notes-sync/commit/ffe0e46), [a1a574a](https://github.com/inventory69/simple-notes-sync/commit/a1a574a))
- Globaler 30-Sekunden-Cooldown zwischen Sync-Operationen (Auto/WiFi/Periodisch)
- onSave-Syncs umgehen den globalen Cooldown (behalten eigenen 5s-Throttle)
- Neuer `SyncStateManager`-Singleton für zentrales State-Tracking
- Verhindert Akkuverbrauch durch schnelle aufeinanderfolgende Syncs

**Toast → Banner-Migration** ([27e6b9d](https://github.com/inventory69/simple-notes-sync/commit/27e6b9d))
- Alle nicht-interaktiven Benachrichtigungen auf einheitliches Banner-System migriert
- Server-Lösch-Ergebnisse als INFO/ERROR-Banner angezeigt
- INFO-Phase zu SyncPhase-Enum mit Auto-Hide (2,5s) hinzugefügt
- Snackbars mit Undo-Aktionen bleiben unverändert

**ProGuard-Regeln Audit** ([6356173](https://github.com/inventory69/simple-notes-sync/commit/6356173))
- Fehlende Keep-Regeln für Widget-ActionCallback-Klassen hinzugefügt
- Compose-spezifische ProGuard-Regeln hinzugefügt
- Verhindert ClassNotFoundException in Release-Builds

### 🧹 Code-Qualität

**Detekt-Compliance** ([1a6617a](https://github.com/inventory69/simple-notes-sync/commit/1a6617a))
- Alle 12 Detekt-Findings behoben (0 Issues verbleibend)
- `NoteEditorViewModel.loadNote()` refactored um Verschachtelungstiefe zu reduzieren
- Konstanten für Magic Numbers im Editor extrahiert
- Unbenutzte Imports aus `UpdateChangelogSheet` entfernt
- `maxIssues: 0` in Detekt-Konfiguration gesetzt

---

## [1.8.0] - 2026-02-10

### 🚨 CRITICAL BUGFIX (Tag neu erstellt)

**R8/ProGuard Obfuscation Fix - Verhindert Datenverlust**
- 🔧 **KRITISCH:** Falscher ProGuard-Klassenpfad für `Note$Companion$NoteRaw` korrigiert
  - Original v1.8.0 hatte spezifische `-keep` Regeln die nicht griffen
  - R8 obfuskierte alle NoteRaw-Felder (id→a, title→b, ...)
  - Gson konnte JSON nicht mehr parsen → **ALLE Notizen erschienen verschwunden**
  - Zurück zur sicheren breiten Regel: `-keep class dev.dettmer.simplenotes.** { *; }`
- 🛡️ Safety-Guards in `detectServerDeletions()` hinzugefügt
  - Verhindert Massenlöschung bei leeren `serverNoteIds` (Netzwerkfehler)
  - Abort wenn ALLE lokalen Notizen als gelöscht erkannt würden
- ✅ Notizen waren nie wirklich verloren (JSON-Dateien intakt auf Disk + Server)
- ✅ Downgrade auf v1.7.2 holte alle Notizen zurück

**⚠️ Falls du v1.8.0 erste Version installiert hattest:** Deine Notizen sind sicher! Einfach updaten.

### 🎉 Major: Widgets, Sortierung & Erweiterte Sync-Features

Komplettes Widget-System mit interaktiven Checklisten, Notiz-Sortierung und umfangreiche Sync-Verbesserungen!

### 🆕 Homescreen-Widgets

**Vollständiges Jetpack Glance Widget-Framework** ([539987f](https://github.com/inventory69/simple-notes-sync/commit/539987f))
- 5 responsive Größenklassen (SMALL, NARROW_MED, NARROW_TALL, WIDE_MED, WIDE_TALL)
- Interaktive Checklist-Checkboxen die sofort zum Server synchronisieren
- Material You Dynamic Colors mit konfigurierbarer Hintergrund-Transparenz (0-100%)
- Widget-Sperre-Toggle zum Verhindern versehentlicher Änderungen
- Read-Only-Modus mit permanenter Options-Leiste für gesperrte Widgets
- Widget-Konfigurations-Activity mit Notiz-Auswahl und Einstellungen
- Auto-Refresh nach Sync-Abschluss
- Tippen auf Inhalt öffnet Editor (entsperrt) oder zeigt Optionen (gesperrt)
- Vollständige Resource-Cleanup-Fixes für Connection Leaks

**Widget State Management:**
- NoteWidgetState Keys für pro-Instanz-Persistierung via DataStore
- Fünf Top-Level ActionCallbacks (Toggle Checkbox, Lock, Options, Refresh, Config)
- Type-Safe Parameter-Übergabe mit NoteWidgetActionKeys

### 📊 Notiz- & Checklisten-Sortierung

**Notiz-Sortierung** ([96c819b](https://github.com/inventory69/simple-notes-sync/commit/96c819b))
- Sortieren nach: Aktualisiert (neueste/älteste), Erstellt, Titel (A-Z/Z-A), Typ
- Persistente Sortierungs-Präferenzen (gespeichert in SharedPreferences)
- Sortierungs-Dialog im Hauptbildschirm mit Richtungs-Toggle
- Kombinierte sortedNotes StateFlow im MainViewModel

**Checklisten-Sortierung** ([96c819b](https://github.com/inventory69/simple-notes-sync/commit/96c819b), [900dad7](https://github.com/inventory69/simple-notes-sync/commit/900dad7))
- Sortieren nach: Manual, Alphabetisch, Offen zuerst, Erledigt zuletzt
- Visueller Separator zwischen offenen/erledigten Items mit Anzahl-Anzeige
- Auto-Sort bei Item-Toggle und Neuordnung
- Drag nur innerhalb gleicher Gruppe (offen/erledigt)
- Sanfte Fade/Slide-Animationen für Item-Übergänge
- Unit-getestet mit 9 Testfällen für Sortierungs-Logik-Validierung

### 🔄 Sync-Verbesserungen

**Server-Löschungs-Erkennung** ([40d7c83](https://github.com/inventory69/simple-notes-sync/commit/40d7c83), [bf7a74e](https://github.com/inventory69/simple-notes-sync/commit/bf7a74e))
- Neuer `DELETED_ON_SERVER` Sync-Status für Multi-Device-Szenarien
- Erkennt wenn Notizen auf anderen Clients gelöscht wurden
- Zero Performance-Impact (nutzt existierende PROPFIND-Daten)
- Löschungs-Anzahl im Sync-Banner: "3 synchronisiert · 2 auf Server gelöscht"
- Bearbeitete gelöschte Notizen werden automatisch zum Server hochgeladen (Status → PENDING)

**Sync-Status-Legende** ([07607fc](https://github.com/inventory69/simple-notes-sync/commit/07607fc))
- Hilfe-Button (?) in Hauptbildschirm TopAppBar
- Dialog erklärt alle 5 Sync-Status-Icons mit Beschreibungen
- Nur sichtbar wenn Sync konfiguriert ist

**Live-Sync-Fortschritts-UI** ([df37d2a](https://github.com/inventory69/simple-notes-sync/commit/df37d2a))
- Echtzeit-Phasen-Indikatoren: PREPARING, UPLOADING, DOWNLOADING, IMPORTING_MARKDOWN
- Upload-Fortschritt zeigt x/y Counter (bekannte Gesamtzahl)
- Download-Fortschritt zeigt Anzahl (unbekannte Gesamtzahl)
- Einheitliches SyncProgressBanner (ersetzt Dual-System)
- Auto-Hide: COMPLETED (2s), ERROR (4s)
- Keine irreführenden Counter wenn nichts zu synchronisieren ist
- Stiller Auto-Sync bleibt still, Fehler werden immer angezeigt

**Parallele Downloads** ([bdfc0bf](https://github.com/inventory69/simple-notes-sync/commit/bdfc0bf))
- Konfigurierbare gleichzeitige Downloads (Standard: 3 simultan)
- Kotlin Coroutines async/awaitAll Pattern
- Individuelle Download-Timeout-Behandlung
- Graceful sequentieller Fallback bei gleichzeitigen Fehlern
- Optimierte Netzwerk-Auslastung für schnelleren Sync

### ✨ UX-Verbesserungen

**Checklisten-Verbesserungen:**
- Überlauf-Verlauf für lange Text-Items ([3462f93](https://github.com/inventory69/simple-notes-sync/commit/3462f93))
- Auto-Expand bei Fokus, Collapse auf 5 Zeilen bei Fokus-Verlust
- Drag & Drop Flackern-Fix mit Straddle-Target-Center-Erkennung ([538a705](https://github.com/inventory69/simple-notes-sync/commit/538a705))
- Adjacency-Filter verhindert Item-Sprünge bei schnellem Drag
- Race-Condition-Fix für Scroll + Move-Operationen

**Einstellungs-UI-Polish:**
- Sanfter Sprachwechsel ohne Activity-Recreate ([881c0fd](https://github.com/inventory69/simple-notes-sync/commit/881c0fd))
- Raster-Ansicht als Standard für Neu-Installationen ([6858446](https://github.com/inventory69/simple-notes-sync/commit/6858446))
- Sync-Einstellungen umstrukturiert in klare Sektionen: Auslöser & Performance ([eaac5a0](https://github.com/inventory69/simple-notes-sync/commit/eaac5a0))
- Changelog-Link zum About-Screen hinzugefügt ([49810ff](https://github.com/inventory69/simple-notes-sync/commit/49810ff))

**Post-Update Changelog-Dialog** ([661d9e0](https://github.com/inventory69/simple-notes-sync/commit/661d9e0))
- Zeigt lokalisierten Changelog beim ersten Start nach Update
- Material 3 ModalBottomSheet mit Slide-up-Animation
- Lädt F-Droid Changelogs via Assets (Single Source of Truth)
- Einmalige Anzeige pro versionCode (gespeichert in SharedPreferences)
- Klickbarer GitHub-Link für vollständigen Changelog
- Durch Button oder Swipe-Geste schließbar
- Test-Modus in Debug-Einstellungen mit Reset-Option

**Backup-Einstellungs-Verbesserungen** ([3e946ed](https://github.com/inventory69/simple-notes-sync/commit/3e946ed))
- Neue BackupProgressCard mit LinearProgressIndicator
- 3-Phasen-Status-System: In Progress → Abschluss → Löschen
- Erfolgs-Status für 2s angezeigt, Fehler für 3s
- Redundante Toast-Nachrichten entfernt
- Buttons bleiben sichtbar und deaktiviert während Operationen
- Exception-Logging für besseres Error-Tracking

### 🐛 Fehlerbehebungen

**Widget-Text-Anzeige** ([d045d4d](https://github.com/inventory69/simple-notes-sync/commit/d045d4d))
- Text-Notizen zeigen nicht mehr nur 3 Zeilen in Widgets
- Von Absatz-basiert zu Zeilen-basiertem Rendering geändert
- LazyColumn scrollt jetzt korrekt durch gesamten Inhalt
- Leere Zeilen als 8dp Spacer beibehalten
- Vorschau-Limits erhöht: compact 100→120, full 200→300 Zeichen

### 🔧 Code-Qualität

**Detekt-Cleanup** ([1da1a63](https://github.com/inventory69/simple-notes-sync/commit/1da1a63))
- Alle 22 Detekt-Warnungen behoben
- 7 ungenutzte Imports entfernt
- Konstanten für 5 Magic Numbers definiert
- State-Reads mit derivedStateOf optimiert
- Build: 0 Lint-Fehler + 0 Detekt-Warnungen

### 📚 Dokumentation

- Vollständige Implementierungs-Pläne für alle 23 v1.8.0 Features
- Widget-System-Architektur und State-Management-Docs
- Sortierungs-Logik Unit-Tests mit Edge-Case-Coverage
- F-Droid Changelogs (Englisch + Deutsch)

---

## [1.7.2] - 2026-02-04

### 🐛 Kritische Fehlerbehebungen

#### JSON/Markdown Timestamp-Synchronisation

**Problem:** Externe Editoren (Obsidian, Typora, VS Code, eigene Editoren) aktualisieren Markdown-Inhalt, aber nicht den YAML `updated:` Timestamp, wodurch die Android-App Änderungen überspringt.

**Lösung:**
- Server-Datei Änderungszeit (`mtime`) wird jetzt als Source of Truth statt YAML-Timestamp verwendet
- Inhaltsänderungen werden via Hash-Vergleich erkannt
- Notizen nach Markdown-Import als `PENDING` markiert → JSON automatisch beim nächsten Sync hochgeladen
- Behebt Sortierungsprobleme nach externen Bearbeitungen

#### SyncStatus auf Server immer PENDING

**Problem:** Alle JSON-Dateien auf dem Server enthielten `"syncStatus": "PENDING"` auch nach erfolgreichem Sync, was externe Clients verwirrte.

**Lösung:**
- Status wird jetzt auf `SYNCED` gesetzt **vor** JSON-Serialisierung
- Server- und lokale Kopien sind jetzt konsistent
- Externe Web/Tauri-Editoren können Sync-Status korrekt interpretieren

#### Deletion Tracker Race Condition

**Problem:** Batch-Löschungen konnten Lösch-Einträge verlieren durch konkurrierenden Dateizugriff.

**Lösung:**
- Mutex-basierte Synchronisation für Deletion Tracking
- Neue `trackDeletionSafe()` Funktion verhindert Race Conditions
- Garantiert Zombie-Note-Prevention auch bei schnellen Mehrfach-Löschungen

#### ISO8601 Timezone-Parsing

**Problem:** Markdown-Importe schlugen fehl mit Timezone-Offsets wie `+01:00` oder `-05:00`.

**Lösung:**
- Multi-Format ISO8601 Parser mit Fallback-Kette
- Unterstützt UTC (Z), Timezone-Offsets (+01:00, +0100) und Millisekunden
- Kompatibel mit Obsidian, Typora, VS Code Timestamps

### ⚡ Performance-Verbesserungen

#### E-Tag Batch Caching

- E-Tags werden jetzt in einer einzigen Batch-Operation geschrieben statt N einzelner Schreibvorgänge
- Performance-Gewinn: ~50-100ms pro Sync mit mehreren Notizen
- Reduzierte Disk-I/O-Operationen

#### Memory Leak Prevention

- `SafeSardineWrapper` implementiert jetzt `Closeable` für explizites Resource-Cleanup
- HTTP Connection Pool wird nach Sync korrekt aufgeräumt
- Verhindert Socket-Exhaustion bei häufigen Syncs

### 🔧 Technische Details

- **IMPL_001:** `kotlinx.coroutines.sync.Mutex` für thread-sicheres Deletion Tracking
- **IMPL_002:** Pattern-basierter ISO8601 Parser mit 8 Format-Varianten
- **IMPL_003:** Connection Pool Eviction + Dispatcher Shutdown in `close()`
- **IMPL_004:** Batch `SharedPreferences.Editor` Updates
- **IMPL_014:** Server `mtime` Parameter in `Note.fromMarkdown()`
- **IMPL_015:** `syncStatus` vor `toJson()` Aufruf gesetzt

### 📚 Dokumentation

- External Editor Specification für Web/Tauri-Editor-Entwickler
- Detaillierte Implementierungs-Dokumentation für alle Bugfixes

---

## [1.7.1] - 2026-02-02

### 🐛 Kritische Fehlerbehebungen

#### Android 9 App-Absturz Fix ([#15](https://github.com/inventory69/simple-notes-sync/issues/15))

**Problem:** App stürzte auf Android 9 (API 28) ab wenn WorkManager Expedited Work für Hintergrund-Sync verwendet wurde.

**Root Cause:** Wenn `setExpedited()` in WorkManager verwendet wird, muss die `CoroutineWorker` die Methode `getForegroundInfo()` implementieren um eine Foreground Service Notification zurückzugeben. Auf Android 9-11 ruft WorkManager diese Methode auf, aber die Standard-Implementierung wirft `IllegalStateException: Not implemented`.

**Lösung:** `getForegroundInfo()` in `SyncWorker` implementiert um eine korrekte `ForegroundInfo` mit Sync-Progress-Notification zurückzugeben.

**Details:**
- `ForegroundInfo` mit Sync-Progress-Notification für Android 9-11 hinzugefügt
- Android 10+: Setzt `FOREGROUND_SERVICE_TYPE_DATA_SYNC` für korrekte Service-Typisierung
- Foreground Service Permissions in AndroidManifest.xml hinzugefügt
- Notification zeigt Sync-Progress mit indeterminiertem Progress Bar
- Danke an [@roughnecks](https://github.com/roughnecks) für das detaillierte Debugging!

#### VPN-Kompatibilitäts-Fix ([#11](https://github.com/inventory69/simple-notes-sync/issues/11))

- WiFi Socket-Binding erkennt jetzt korrekt Wireguard VPN-Interfaces (tun*, wg*, *-wg-*)
- Traffic wird korrekt durch VPN-Tunnel geleitet statt direkt über WiFi
- Behebt "Verbindungs-Timeout" beim Sync zu externen Servern über VPN

### 🔧 Technische Änderungen

- Neue `SafeSardineWrapper` Klasse stellt korrektes HTTP-Connection-Cleanup sicher
- Weniger unnötige 401-Authentifizierungs-Challenges durch preemptive Auth-Header
- ProGuard-Regel hinzugefügt um harmlose TextInclusionStrategy-Warnungen zu unterdrücken
- VPN-Interface-Erkennung via `NetworkInterface.getNetworkInterfaces()` Pattern-Matching
- Foreground Service Erkennung und Notification-System für Hintergrund-Sync-Tasks

### 🌍 Lokalisierung

- Hardcodierte deutsche Fehlermeldungen behoben - jetzt String-Resources für korrekte Lokalisierung
- Deutsche und englische Strings für Sync-Progress-Notifications hinzugefügt

---

## [1.7.0] - 2026-01-26

### 🎉 Major: Grid-Ansicht, Nur-WLAN Sync & VPN-Unterstützung

Pinterest-Style Grid, Nur-WLAN Sync-Modus und korrekte VPN-Unterstützung!

### 🎨 Grid-Layout

- Pinterest-Style Staggered Grid ohne Lücken
- Konsistente 12dp Abstände zwischen Cards
- Scroll-Position bleibt erhalten nach Einstellungen
- Neue einheitliche `NoteCardGrid` mit dynamischen Vorschauzeilen (3 klein, 6 groß)

### 📡 Sync-Verbesserungen

- **Nur-WLAN Sync Toggle** - Sync nur wenn WLAN verbunden
- **VPN-Unterstützung** - Sync funktioniert korrekt bei aktivem VPN (Traffic über VPN)
- **Server-Wechsel Erkennung** - Alle Notizen auf PENDING zurückgesetzt bei Server-URL Änderung
- **Schnellere Server-Prüfung** - Socket-Timeout von 2s auf 1s reduziert
- **"Sync läuft bereits" Feedback** - Zeigt Snackbar wenn Sync bereits läuft

### 🔒 Self-Signed SSL Unterstützung

- **Dokumentation hinzugefügt** - Anleitung für selbst-signierte Zertifikate
- Nutzt Android's eingebauten CA Trust Store
- Funktioniert mit ownCloud, Nextcloud, Synology, Home-Servern

### 🔧 Technisch

- `NoteCardGrid` Komponente mit dynamischen maxLines
- FullLine Spans entfernt für lückenloses Layout
- `resetAllSyncStatusToPending()` in NotesStorage
- VPN-Erkennung in `getOrCacheWiFiAddress()`

---

## [1.6.1] - 2026-01-20

### 🧹 Code-Qualität & Build-Verbesserungen

- **detekt: 0 Issues** - Alle 29 Code-Qualitäts-Issues behoben
  - Triviale Fixes: Unused Imports, MaxLineLength
  - Datei umbenannt: DragDropState.kt → DragDropListState.kt
  - MagicNumbers → Constants (Dimensions.kt, SyncConstants.kt)
  - SwallowedException: Logger.w() für besseres Error-Tracking hinzugefügt
  - LongParameterList: ChecklistEditorCallbacks data class erstellt
  - LongMethod: ServerSettingsScreen in Komponenten aufgeteilt
  - @Suppress Annotationen für Legacy-Code (WebDavSyncService, SettingsActivity)

- **Zero Build Warnings** - Alle 21 Deprecation Warnings eliminiert
  - File-level @Suppress für deprecated Imports
  - ProgressDialog, LocalBroadcastManager, AbstractSavedStateViewModelFactory
  - onActivityResult, onRequestPermissionsResult
  - Gradle Compose Config bereinigt (StrongSkipping ist jetzt Standard)

- **ktlint reaktiviert** - Linting mit Compose-spezifischen Regeln wieder aktiviert
  - .editorconfig mit Compose Formatierungsregeln erstellt
  - Legacy-Dateien ausgeschlossen: WebDavSyncService.kt, build.gradle.kts
  - ignoreFailures=true für graduelle Migration

- **CI/CD Verbesserungen** - GitHub Actions Lint-Checks integriert
  - detekt + ktlint + Android Lint laufen vor Build in pr-build-check.yml
  - Stellt Code-Qualität bei jedem Pull Request sicher

### 🔧 Technische Verbesserungen

- **Constants Refactoring** - Bessere Code-Organisation
  - ui/theme/Dimensions.kt: UI-bezogene Konstanten
  - utils/SyncConstants.kt: Sync-Operations Konstanten

- **Vorbereitung für v2.0.0** - Legacy-Code für Entfernung markiert
  - SettingsActivity und MainActivity (ersetzt durch Compose-Versionen)
  - Alle deprecated APIs mit Removal-Plan dokumentiert

---

## [1.6.0] - 2026-01-19

### 🎉 Major: Konfigurierbare Sync-Trigger

Feingranulare Kontrolle darüber, wann deine Notizen synchronisiert werden - wähle die Trigger, die am besten zu deinem Workflow passen!

### ⚙️ Sync-Trigger System

- **Individuelle Trigger-Kontrolle** - Jeden Sync-Trigger einzeln in den Einstellungen aktivieren/deaktivieren
- **5 Unabhängige Trigger:**
  - **onSave Sync** - Sync sofort nach dem Speichern einer Notiz (5s Throttle)
  - **onResume Sync** - Sync beim Öffnen der App (60s Throttle)
  - **WiFi-Connect Sync** - Sync bei WiFi-Verbindung
  - **Periodischer Sync** - Hintergrund-Sync alle 15/30/60 Minuten (konfigurierbar)
  - **Boot Sync** - Startet Hintergrund-Sync nach Geräteneustart

- **Smarte Defaults** - Nur ereignisbasierte Trigger standardmäßig aktiv (onSave, onResume, WiFi-Connect)
- **Akku-optimiert** - ~0.2%/Tag mit Defaults, bis zu ~1.0% mit aktiviertem periodischen Sync
- **Offline-Modus UI** - Ausgegraute Sync-Toggles wenn kein Server konfiguriert
- **Dynamischer Settings-Subtitle** - Zeigt Anzahl aktiver Trigger im Haupteinstellungs-Screen

### 🔧 Server-Konfiguration Verbesserungen

- **Offline-Modus Toggle** - Alle Netzwerkfunktionen mit einem Schalter deaktivieren
- **Getrennte Protokoll & Host Eingabe** - Protokoll (http/https) als nicht-editierbares Präfix angezeigt
- **Klickbare Settings-Cards** - Gesamte Card klickbar für bessere UX
- **Klickbare Toggle-Zeilen** - Text/Icon klicken um Switches zu bedienen (nicht nur der Switch selbst)

### 🐛 Bug Fixes

- **Fix:** Fehlender 5. Sync-Trigger (Boot) in der Haupteinstellungs-Screen Subtitle-Zählung
- **Fix:** Offline-Modus Status wird nicht aktualisiert beim Zurückkehren aus Einstellungen
- **Fix:** Pull-to-Refresh funktioniert auch im Offline-Modus

### 🔧 Technische Verbesserungen

- **Reaktiver Offline-Modus Status** - StateFlow stellt sicher, dass UI korrekt aktualisiert wird
- **Getrennte Server-Config Checks** - `hasServerConfig()` vs `isServerConfigured()` (offline-aware)
- **Verbesserte Konstanten** - Alle Sync-Trigger Keys und Defaults in Constants.kt
- **Bessere Code-Organisation** - Settings-Screens für Klarheit refactored

### Looking Ahead

> 🚀 **v1.7.0** wird Server-Ordner Prüfung und weitere Community-Features bringen.
> Feature-Requests sind willkommen als [GitHub Issue](https://github.com/inventory69/simple-notes-sync/issues).

---

## [1.5.0] - 2026-01-15

### 🎉 Major: Jetpack Compose UI Redesign

Das komplette UI wurde von XML-Views auf Jetpack Compose migriert. Die App ist jetzt moderner, schneller und flüssiger.

### 🌍 New Feature: Internationalization (i18n)

- **Englische Sprachunterstützung** - Alle 400+ Strings übersetzt
- **Automatische Spracherkennung** - Folgt der System-Sprache
- **Manuelle Sprachauswahl** - In den Einstellungen umschaltbar
- **Per-App Language (Android 13+)** - Native Spracheinstellung über System-Settings
- **locales_config.xml** - Vollständige Android-Integration

### ⚙️ Modernized Settings

- **7 kategorisierte Settings-Screens** - Übersichtlicher und intuitiver
- **Compose Navigation** - Flüssige Übergänge zwischen Screens
- **Konsistentes Design** - Material Design 3 durchgängig

### ✨ UI Improvements

- **Selection Mode** - Long-Press für Mehrfachauswahl statt Swipe-to-Delete
- **Batch Delete** - Mehrere Notizen gleichzeitig löschen
- **Silent-Sync Mode** - Kein Banner bei Auto-Sync (nur bei manuellem Sync)
- **App Icon in About Screen** - Hochwertige Darstellung
- **App Icon in Empty State** - Statt Emoji bei leerer Notizliste
- **Splash Screen Update** - Verwendet App-Foreground-Icon
- **Slide Animations** - Flüssige Animationen im NoteEditor

### 🔧 Technical Improvements

- **Jetpack Compose** - Komplette UI-Migration
- **Compose ViewModel Integration** - StateFlow für reactive UI
- **Improved Code Quality** - Detekt/Lint Warnings behoben
- **Unused Imports Cleanup** - Sauberer Codebase

### Looking Ahead

> 🚀 **v1.6.0** wird Server-Ordner Prüfung und weitere technische Modernisierungen bringen.
> Feature-Requests gerne als [GitHub Issue](https://github.com/inventory69/simple-notes-sync/issues) einreichen.

---

## [1.4.1] - 2026-01-11

### Fixed

- **🗑️ Löschen älterer Notizen (v1.2.0 Kompatibilität)**
  - Notizen aus App-Version v1.2.0 oder früher werden jetzt korrekt vom Server gelöscht
  - Behebt Problem bei Multi-Device-Nutzung mit älteren Notizen

- **🔄 Checklisten-Sync Abwärtskompatibilität**
  - Checklisten werden jetzt auch als Text-Fallback im `content`-Feld gespeichert
  - Ältere App-Versionen (v1.3.x) zeigen Checklisten als lesbaren Text
  - Format: GitHub-Style Task-Listen (`[ ] Item` / `[x] Item`)
  - Recovery-Mode: Falls Checklisten-Items verloren gehen, werden sie aus dem Content wiederhergestellt

### Improved

- **📝 Checklisten Auto-Zeilenumbruch**
  - Lange Checklisten-Texte werden jetzt automatisch umgebrochen
  - Keine Begrenzung auf 3 Zeilen mehr
  - Enter-Taste erstellt weiterhin ein neues Item

### Looking Ahead

> 🚀 **v1.5.0** wird das nächste größere Release. Wir sammeln Ideen und Feedback!  
> Feature-Requests gerne als [GitHub Issue](https://github.com/inventory69/simple-notes-sync/issues) einreichen.

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
