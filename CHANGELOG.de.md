# Changelog

Alle wichtigen Änderungen an Simple Notes Sync werden in dieser Datei dokumentiert.

Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

**🌍 Sprachen:** **Deutsch** · [English](CHANGELOG.md)

---

## [2.7.2] - 2026-06-07

### 🐛 Bug-Fixes

**Sync: Ordner & falsches Gelöscht-Flag auf no-ETag-Servern repariert** ([1d3a5a1](https://github.com/inventory69/simple-notes-sync/commit/1d3a5a1))
- Auf WebDAV-Servern ohne ETags konnten übersprungene Notizen dauerhaft einen veralteten `folderName` oder ein falsches `DELETED_ON_SERVER`-Flag behalten; beide Fälle werden jetzt beim Überspringen korrigiert – ohne Re-Upload-Loop

**UI: Notizliste startet nach Kaltstart wieder oben** ([a9d4533](https://github.com/inventory69/simple-notes-sync/commit/a9d4533))
- Das Staggered-Grid scrollte beim initialen Laden automatisch von Index 0 weg (Compose-Foundation-Quirk) und versteckte den „Angeheftete"-Abschnittskopf; ein Top-Settle-Guard hält die Liste nun oben, bis das Layout stabil ist

**Widget: OptionsBar-Icons im hellen Modus sichtbar** ([c448a43](https://github.com/inventory69/simple-notes-sync/commit/c448a43))
- Widget-OptionsBar-Icons (Sperren, Aktualisieren, Einstellungen, In-App-Öffnen) hatten eine hartcodierte weiße Füllfarbe und waren auf hellen Hintergründen unsichtbar; sie passen sich jetzt über `ColorFilter.tint(onSurface)` dem System-Theme an

### 🌍 Übersetzungen

- **Spanisch**: weiter ausgebaut — Francisco Isaac Ordoñez Pedrero
- **Indonesisch**: aktualisiert — Arif Budiman
- **Vereinfachtes Chinesisch**: aktualisiert — heretic43
- **Norwegisch Bokmål**: aktualisiert — xdpirate

---

## [2.7.1] - 2026-06-02

### 🐛 Bug-Fixes

**Ordner-Sync: Waisendateien auf dem Server** ([77b4029](https://github.com/inventory69/simple-notes-sync/commit/77b4029))
- Notizen, die beim Ordner-Löschen zurück ins Root verschoben werden, werden nun für die Server-Löschung aus ihrem alten Unterordner-Pfad vorgemerkt – keine verwaisten Dateien mehr auf dem Server
- URL-kodierte Ordnernamen (Leerzeichen als `%20`) führten dazu, dass `deleteServerFolderIfEmpty` die Bereinigung übersprungen hat – behoben
- Leere Server-Ordnerverzeichnisse werden nun nach Batch-Löschungen aufgeräumt ([4e05ad1](https://github.com/inventory69/simple-notes-sync/commit/4e05ad1))

**Ordner-Sync: Endlosschleife PENDING nach Ordner-Verschieben** ([18f186a](https://github.com/inventory69/simple-notes-sync/commit/18f186a))
- Der Server-Scan dedupliziert Notizen jetzt nach `noteId`; nach einem Ordner-Move konnte dieselbe Notiz in mehreren WebDAV-Verzeichnissen auftauchen und widersprüchliche Ordnerzuweisungen verursachen, was zu einem endlosen PENDING-Zustand führte

**Sync: Stilles Sync überschreibt keine sichtbaren Banner mehr** ([1f43b50](https://github.com/inventory69/simple-notes-sync/commit/1f43b50))
- Stille Hintergrund-Syncs können jetzt kein Ergebnis-Banner mehr leeren oder ersetzen, dessen Auto-Hide-Timer noch läuft

**UI: Banner-Inhalt flackerte beim Ausblenden** ([af07399](https://github.com/inventory69/simple-notes-sync/commit/af07399))
- Behoben: `AnimatedContent` erhielt `IDLE` während der Ausblend-Animation und renderte kurz den leeren Zustand; der sichtbare Inhalt bleibt jetzt stabil bis die Animation abgeschlossen ist

**UI: Ordner-Dialog-Polishing** ([fb72623](https://github.com/inventory69/simple-notes-sync/commit/fb72623))
- Tastatur öffnet sich jetzt automatisch beim Öffnen des Dialogs (kein extra Tap mehr nötig)
- Erster Buchstabe des Ordnernamens wird automatisch großgeschrieben

**UI: Falscher Plural-String beim Ordner-Löschen** ([99dfbae](https://github.com/inventory69/simple-notes-sync/commit/99dfbae))
- Das Bestätigungs-Banner nach dem Ordner-Löschen nutzt jetzt die korrekte Plural-Ressource je nachdem, ob Notizen nur lokal oder auch auf dem Server gelöscht wurden

### 🌍 Übersetzungen

- **Spanisch**: von teilweise auf breite Abdeckung erweitert, Terminologie-Fix (`borrar` → `eliminar`) ([2f19d18](https://github.com/inventory69/simple-notes-sync/commit/2f19d18)), Locale in `locales_config` registriert ([974efe1](https://github.com/inventory69/simple-notes-sync/commit/974efe1))
- **Vereinfachtes Chinesisch**: 7 Debug-Screen-Strings ergänzt (100% Abdeckung) ([2f19d18](https://github.com/inventory69/simple-notes-sync/commit/2f19d18))

---

## [2.7.0] - 2026-05-30

### ✨ Neue Features

**Ordner-Unterstützung** ([e38553d](https://github.com/inventory69/simple-notes-sync/commit/e38553d))
- Notizen können in Ordner organisiert werden; jede Notiz trägt ein optionales `folderName`-Feld
- Ordner erstellen, umbenennen und löschen über dedizierte CRUD-Dialoge
- Notizen per Bottom-Sheet in der Notizliste in Ordner verschieben
- Ordner-Navigation in der Hauptansicht filtert die Liste nach Ordner
- Ordner werden mit Tombstone-basiertem Lösch-Tracking persistiert (`FolderStore`)
- Ordner synchronisieren bidirektional via WebDAV mit ordner-bewusstem URL-Routing (`FolderSyncManager`)
- Danke an [@happy-turtle](https://github.com/happy-turtle) und [@racehd](https://github.com/racehd) für die Idee, und an [@afoni95](https://github.com/afoni95) fürs Testen!

### 🐛 Bug-Fixes

**Raster & Angepinnte Notizen – Polishing** ([1e02895](https://github.com/inventory69/simple-notes-sync/commit/1e02895))
- Pop-in-Artefakt beim Erscheinen angepinnter Notizen im gestaffelten Raster behoben
- Scroll-Position wird beim Filterwechsel wieder auf den Anfang zurückgesetzt ([2375c97](https://github.com/inventory69/simple-notes-sync/commit/2375c97))
- Berechnungen der gestaffelten Raster-Aufteilung gecacht, weniger Layout-Stress ([dc117ba](https://github.com/inventory69/simple-notes-sync/commit/dc117ba))
- Visuellen Glitch beim GridColumnChip in der Spaltenauswahl der Anzeigeeinstellungen behoben ([27a7c0a](https://github.com/inventory69/simple-notes-sync/commit/27a7c0a))

### 🌍 Übersetzungen

- **Spanisch** (neu): [08f0948](https://github.com/inventory69/simple-notes-sync/commit/08f0948)

---

## [2.6.0] - 2026-05-24

### ✨ Neue Features

**Angepinnte-Notizen-Bereich** ([e849bdf](https://github.com/inventory69/simple-notes-sync/commit/e849bdf))
- Notizen können per Multi-Select-Batch-Aktion angepinnt werden und erscheinen in einem eigenen „Angepinnt"-Bereich oberhalb der regulären Liste
- Bereich nur sichtbar, wenn mindestens eine Notiz angepinnt ist; angepinnte Karten zeigen ein Pin-Symbol
- Funktioniert in Listen- und Rasteransicht
- Vorgeschlagen von [@ASFHU](https://github.com/ASFHU), [@james0336](https://github.com/james0336), [@isawaway](https://github.com/isawaway)!

**Typkonvertierung: Text ↔ Checkliste** ([714a7e6](https://github.com/inventory69/simple-notes-sync/commit/714a7e6))
- Jede Textnotiz lässt sich in eine Checkliste umwandeln und zurück; GFM-Checkbox-Notation (`- [ ]` / `- [x]`) bleibt in beide Richtungen erhalten
- Vollständige Rückgängig/Wiederholen-Unterstützung für Typkonvertierungen

**Bare-URLs im Markdown-Preview als Links** ([745a4cd](https://github.com/inventory69/simple-notes-sync/commit/745a4cd))
- Nackte `http://`- und `https://`-URLs werden im Markdown-Preview automatisch als anklickbare Links erkannt
- Abschließende Satzzeichen werden nicht in den Link einbezogen
- Vorgeschlagen von [@james0336](https://github.com/james0336), [@isawaway](https://github.com/isawaway)!

**„Text kopieren" im Editor-Überlaufmenü** ([83e6a3c](https://github.com/inventory69/simple-notes-sync/commit/83e6a3c))
- Kopiert den vollständigen Notizinhalt (Titel + Text) mit einem Tippen in die Zwischenablage; Android 13+ zeigt die Systembestätigung, ältere Versionen eine Snackbar
- Danke an [@xdpirate](https://github.com/xdpirate) für den Vorschlag!

**Geteilten Text an bestehende Notiz anhängen** ([dc59351](https://github.com/inventory69/simple-notes-sync/commit/dc59351))
- Der Teilen-Intent bietet jetzt eine dritte Option, um freigegebenen Text an eine bestehende Notiz anzuhängen
- Eine Notizauswahl (sortiert nach zuletzt geändert) ermöglicht die Auswahl; der Editor öffnet sich mit dem zusammengeführten Inhalt zur Kontrolle

### 🐛 Bug-Fixes

**Einstellungen reagieren nicht auf Offline-Modus-Umschalten** ([b388016](https://github.com/inventory69/simple-notes-sync/commit/b388016))
- `isServerConfigured` war eine einfache Funktion, die einmalig pro Composable aufgerufen wurde; durch Umstellung auf `StateFlow` reagiert die UI jetzt sofort auf Änderungen
- Statusbeschriftungen unterscheiden nun korrekt zwischen „Offline-Modus aktiv" und „Kein Server konfiguriert"

**Parallele-Verbindungen-Einstellung im Offline-Modus aktiv** ([c6cf4e5](https://github.com/inventory69/simple-notes-sync/commit/c6cf4e5))
- Die Radiogruppe für parallele Verbindungen blieb interaktiv, während alle anderen Sync-Einstellungen ausgegraut waren; sie wird jetzt korrekt deaktiviert

**Checkliste: Unicode-Aufzählungszeichen beim Einfügen via Teilen-Intent** ([8e67ccd](https://github.com/inventory69/simple-notes-sync/commit/8e67ccd))
- `• Element` (U+2022), `✓ Element`, `☑ Element` und `✔ Element` werden beim Einfügen via Teilen-Intent jetzt korrekt normalisiert

**Checkliste: Trennlinie springt beim Einblenden/Ausblenden der Tastatur** ([8295522](https://github.com/inventory69/simple-notes-sync/commit/8295522))
- Der Trenner zwischen abgehakten und nicht abgehakten Items fehlte `animateItem()` und sprang anstatt zu gleiten, wenn die Bildschirmtastatur die Liste verkleinerte

**Checkliste: Gemischte GFM- und Plaintext-Zeilen nicht zeilenweise geparst** ([2bd8106](https://github.com/inventory69/simple-notes-sync/commit/2bd8106))
- Gemischte Eingaben (teils GFM, teils Klartext) wurden als Rohtext behandelt und ließen `- [ ] Milch` als wörtlichen Itemtext stehen; der Parser klassifiziert nun jede Zeile einzeln

---

## [2.5.2] - 2026-05-22

### 🐛 Bug-Fixes

**Navigationsleistenfarbe wird bei Android < 10 im Dunkelmodus nicht gesetzt** ([5554710](https://github.com/inventory69/simple-notes-sync/commit/5554710))
- Auf API < 30 blieb die Navigationsleiste hell, obwohl Dunkelmodus aktiv war; das Window-Flag `navigationBarColor` wird jetzt explizit für diese Geräte gesetzt
- Danke an [dfxflkbvnsr](https://github.com/dfxflkbvnsr) für den Hinweis!

**Snackbar verdeckt FAB auf dem Hauptbildschirm** ([a9ff4f5](https://github.com/inventory69/simple-notes-sync/commit/a9ff4f5))
- Die Snackbar erschien hinter dem Floating Action Button; sie wird jetzt korrekt oberhalb des FAB angezeigt
- Danke an [dfxflkbvnsr](https://github.com/dfxflkbvnsr) für den Hinweis!

**Zurück-Taste wird im Auswahlmodus nicht abgefangen** ([b8689eb](https://github.com/inventory69/simple-notes-sync/commit/b8689eb))
- Ein Druck auf Zurück beim Auswählen von Notizen beendete den Auswahlmodus nicht; der Back-Handler wird jetzt korrekt registriert, sobald die Auswahl aktiv ist
- Danke an [dfxflkbvnsr](https://github.com/dfxflkbvnsr) für den Hinweis!

**Farbauswahl im Multi-Select zeigt keine aktuelle Farbe** ([1365893](https://github.com/inventory69/simple-notes-sync/commit/1365893))
- Der Farb-Picker im Multi-Select öffnete sich immer ohne vorausgewählte Farbe; er markiert jetzt die gemeinsame Farbe, wenn alle ausgewählten Notizen dieselbe haben

**Notizfarbe geht beim Wechsel in den Auswahlmodus verloren** ([9c4cc5d](https://github.com/inventory69/simple-notes-sync/commit/9c4cc5d))
- Ein Tippen auf eine Notizkarte zum Starten der Auswahl setzte die Notizfarbe auf „Keine" zurück; die Farbe bleibt jetzt erhalten

**Checklisten: UNCHECKED_FIRST-Sortierung springt beim Umschalten** ([2c008fd](https://github.com/inventory69/simple-notes-sync/commit/2c008fd))
- `UNCHECKED_FIRST` teilte sich denselben `sortedBy { originalOrder }`-Zweig wie `MANUAL`, wodurch Items beim Abhaken sprangen (Abgehakte landeten am Ende der abgehakten Gruppe, Abgehobene an der Spitze der nicht-abgehakten Gruppe)
- Behoben durch einen eigenen stabilen Filter-Zweig für `UNCHECKED_FIRST`, der die relative Reihenfolge innerhalb jeder Gruppe erhält; `MANUAL` behält die `originalOrder`-basierte Wiederherstellung unverändert
- Danke an [@WuTangWilli](https://github.com/WuTangWilli) für den Hinweis!

### 🌍 Übersetzungen

- **Indonesisch** (100 %): [@arifpedia](https://github.com/arifpedia) / Arif Budiman
- **Chinesisch (Vereinfacht)** (100 %): heretic43
- **Hindi** (teilweise): Silent Coder

---

## [2.5.1] - 2026-05-16

> 🎉 **Erstes Google-Play-Production-Release** — v2.5.0 war als initialer Production-Build vorbereitet, wurde aber nie im Play Store veröffentlicht. v2.5.1 ist die Version, die tatsächlich in Production geht. Sie vervollständigt das Notizfarben-Feature aus v2.5.0 durch das eine fehlende Stück: die Sortierung nach Farbe.

### ✨ Neue Features

**Notizlisten-Farb-Sortierung** ([93f7852](https://github.com/inventory69/simple-notes-sync/commit/93f7852))
- Neue Option **Farbe** im Sort-Dialog gruppiert Notizen in kanonischer Palettenreihenfolge (Rot → Orange → Gelb → Grün → Blaugrün → Blau → Dunkelblau → Lila → Rosa → Braun → Grau)
- Farblose Notizen landen am Ende (aufsteigend) bzw. am Anfang (absteigend) der Liste
- Innerhalb einer Farbgruppe werden Notizen nach Änderungsdatum absteigend sortiert — konsistent mit der bestehenden Typ-Sortierung
- Die Sortierauswahl bleibt über App-Neustarts hinweg in den SharedPreferences erhalten

---

## [2.5.0] - 2026-05-15

> 🎉 **Erstes Google-Play-Production-Release!** Nach mehreren Beta-Runden ist v2.5.0 der erste Build, der in den Production-Track im Play Store wandert. Riesiges Dankeschön an alle, die frühe Builds installiert, Probleme gemeldet und uns geholfen haben, die letzten Ecken auszubügeln.

### ✨ Neue Features

**Google-Keep-Import** ([#65](https://github.com/inventory69/simple-notes-sync/issues/65))
- Notizen direkt aus einer Google-Keep- / Google-Takeout-`.zip` importieren — komplett auf dem Gerät, kein Upload
- Vorab-Analyse klassifiziert das Archiv (aktiv / archiviert / Papierkorb / Labels / geteilt / Gesamtgröße), bevor du bestätigst
- **Konflikt-Strategie** pro Lauf: *immer neu erstellen* (Standard), *überspringen, falls vorhanden* oder *ersetzen, falls vorhanden* — Vergleich per Inhalts-Hash (Titel + Body + Checklisten-Items)
- Optionale Schalter für **archivierte** und **Papierkorb**-Notizen
- Eingerückte Checklisten bis 3 Ebenen erhalten (DnD-Verhalten unverändert)
- Keep-`color` wird mitimportiert und sofort angezeigt (siehe *Notizfarben* unten); `isPinned` wird im Notiz-Modell gespeichert (Anzeige folgt in einem späteren Release)
- Labels werden in einer separaten `notes_labels.json`-Indexdatei gespeichert
- Abbrechbar während des Imports; bereits importierte Notizen bleiben erhalten; Ergebnis-Dialog zeigt importiert / ersetzt / übersprungen / Fehler mit ausklappbarer Fehlerliste
- Bestätigungs-Schritt bei Archiven über 200 MB
- Einmaliger Auto-Sync am Ende (nur wenn ≥ 1 Notiz importiert oder ersetzt wurde; respektiert vorhandenes Sync-beim-Speichern-Gate, Throttle und Offline-Modus)
- Zu finden unter **Einstellungen → Import → Google Keep importieren**
- Danke an [@simianaya](https://github.com/simianaya) für den Feature-Wunsch!

**Notizfarben** ([#65](https://github.com/inventory69/simple-notes-sync/issues/65))
- Neue Farbpalette (Keine, Rot, Orange, Gelb, Grün, Blaugrün, Blau, Dunkelblau, Lila, Rosa, Braun, Grau) mit getrennten Light-/Dark-Slots, damit Farben in beiden Themes lesbar bleiben ([4465615](https://github.com/inventory69/simple-notes-sync/commit/4465615))
- Notizkarten auf dem Startbildschirm werden in der gewählten Farbe eingefärbt ([7ced207](https://github.com/inventory69/simple-notes-sync/commit/7ced207))
- Neuer Farbwähler als Bottom-Sheet ([cd0361e](https://github.com/inventory69/simple-notes-sync/commit/cd0361e))
- Farbe einer Notiz im Editor über das Overflow-Menü setzen ([b244db9](https://github.com/inventory69/simple-notes-sync/commit/b244db9))
- Mehrfachauswahl auf dem Startbildschirm: Farbe für mehrere Notizen gleichzeitig setzen ([7341462](https://github.com/inventory69/simple-notes-sync/commit/7341462))
- Im Editor wird die Farbe als dezenter 3-dp-Akzentstreifen statt als ganzflächige Tönung dargestellt — angenehm zu lesen ([bd5c943](https://github.com/inventory69/simple-notes-sync/commit/bd5c943))
- Farben aus Google-Keep-Importen werden automatisch auf diese Palette gemappt
- Danke an [@simianaya](https://github.com/simianaya) für den Feature-Wunsch!

**Konflikt-Strategie für lokale & WebDAV-Importe**
- Die Konflikt-Strategie, die bereits beim Keep-Import bestand, gibt es jetzt auch für den **lokalen Datei-Import** ([3a3d55c](https://github.com/inventory69/simple-notes-sync/commit/3a3d55c)) und den **WebDAV-Scan-Import** ([d369536](https://github.com/inventory69/simple-notes-sync/commit/d369536))
- Pro Import-Lauf wählbar: *immer neu erstellen*, *überspringen* oder *ersetzen* — gleicher Inhalts-Hash-Vergleich wie beim Keep-Import, damit alle drei Quellen konsistent funktionieren
- Der Button für den lokalen Datei-Import nutzt jetzt den Standard-`SettingsButton` für ein einheitliches Erscheinungsbild ([75f4dbd](https://github.com/inventory69/simple-notes-sync/commit/75f4dbd))

**Alle auswählen / Auswahl aufheben beim WebDAV-Import** ([d928ddb](https://github.com/inventory69/simple-notes-sync/commit/d928ddb))
- Nach dem Scan eines WebDAV-Ordners kannst du mit einem einzigen Toggle alle Ergebnisse auf einmal an- oder abwählen
- Spart bei großen Notizsammlungen viel Tippen

**Notizlisten-Farbfilter** ([e773a05](https://github.com/inventory69/simple-notes-sync/commit/e773a05))
- Palette-Icon-Chip in der Filterleiste öffnet ein Farbfilter-Dropdown mit der Anzahl Notizen je Farbe
- Kombiniert sich mit dem bestehenden Typ-Filter (UND-Logik) und wird in den SharedPreferences gespeichert
- Text- und Listen-Chips auf reine Icon-Darstellung umgestellt für ein einheitlicheres Erscheinungsbild
- Schließt [#65](https://github.com/inventory69/simple-notes-sync/issues/65)

### ✨ Verbesserungen

**Checklisten-Tap-Animation**
- Beim Abhaken eines Checklisten-Eintrags läuft jetzt eine brandneue Animation: kurzer Scale-Pop auf der Zeile, sanftes Radial-Glow und ein animiertes Abhaken ([7156c12](https://github.com/inventory69/simple-notes-sync/commit/7156c12), [2551d21](https://github.com/inventory69/simple-notes-sync/commit/2551d21))
- Der erste Eintrag „springt" beim Öffnen des Editors nicht mehr — die Placement-Animation wird beim ersten Render unterdrückt ([2515874](https://github.com/inventory69/simple-notes-sync/commit/2515874))
- Die Liste behält ihre Scroll-Position korrekt bei, wenn Einträge zwischen dem unchecked- und checked-Zweig wechseln ([9792968](https://github.com/inventory69/simple-notes-sync/commit/9792968))

### 🐛 Fehlerbehebungen

- **Importierte Notizen bekommen immer `SyncStatus.PENDING`** ([51ea959](https://github.com/inventory69/simple-notes-sync/commit/51ea959)) — importierte Notizen werden jetzt zuverlässig vom nächsten Sync-Lauf erfasst, egal aus welcher Quelle sie kommen
- **Editor-Autosave berücksichtigt Farbänderungen** ([a0a34b9](https://github.com/inventory69/simple-notes-sync/commit/a0a34b9)) — eine im Editor geänderte Notizfarbe wird nicht mehr vom No-Change-Guard verworfen

### ♻️ Intern

**`SyncScheduler` extrahiert**
- Die duplizierte `triggerOnSaveSync()`-Logik aus `NoteEditorViewModel` und `MainViewModel` liegt jetzt zentral in `sync/SyncScheduler` (Verhalten strikt äquivalent zu v2.4.0; wird vom Keep-Import-Flow wiederverwendet)

**APK-Größe −11 % durch Locale- & Ressourcen-Filterung** ([5a631cb](https://github.com/inventory69/simple-notes-sync/commit/5a631cb))
- `localeFilters` auf die 10 unterstützten Sprachen beschränkt — entfernt ungenutzte Übersetzungen aus `resources.arsc`
- Striktes Ressourcen-Shrinking (`keep.xml`) — sicher, da die App keine dynamischen Ressourcen-Zugriffe hat
- Packaging-Excludes um Build-time-Metadaten erweitert
- Ergebnis: 5,23 MB → 4,65 MB (−577 KB, −11 %); `resources.arsc`: 1,49 MB → 0,95 MB (−540 KB)

### 📚 Dokumentation

- Neue Anleitung: `project-docs/simple-notes-sync/v2.5.0/google-keep-import-user-guide.de.md` (EN + DE)
- Neues QA-Cheatsheet: `project-docs/simple-notes-sync/v2.5.0/google-keep-import-adb-tests.md`

### 🙏 Danksagungen

- [@simianaya](https://github.com/simianaya) — für den Wunsch nach Google-Keep-Import und Notizfarben ([#65](https://github.com/inventory69/simple-notes-sync/issues/65))
- Alle, die frühe Google-Keep-Exporte getestet und Sonderfälle gemeldet haben
- Alle Beta-Tester, die uns zum **ersten Play-Store-Production-Release** geholfen haben — danke! 🎉

---

## [2.4.0] - 2026-05-04

### ✨ Neue Features

**Persistentes Sync-Debug-Log** ([1de5fdb](https://github.com/inventory69/simple-notes-sync/commit/1de5fdb), [1234f6c](https://github.com/inventory69/simple-notes-sync/commit/1234f6c), [d8d4284](https://github.com/inventory69/simple-notes-sync/commit/d8d4284), [13defa9](https://github.com/inventory69/simple-notes-sync/commit/13defa9), [62e75e7](https://github.com/inventory69/simple-notes-sync/commit/62e75e7))
- Neuer persistenter `SyncDebugLogger`, der strukturierte Einträge in `sync_debug.log` schreibt — hilft beim Diagnostizieren von Hintergrund-Sync-Problemen
- Loggt jedes `SyncWorker`-Ergebnis (Start, Erfolg, Soft-Fehler, Retry, finales Scheitern)
- Schema um `attempt`-Zähler und `holder` (Prozess-Tag) erweitert, um App-/Worker-Kontexte zu unterscheiden
- WIFI_CONNECT-Events mit `first|change`-Grund annotiert
- Expliziter finaler Status, wenn WorkManager Retries verwirft — keine unsichtbaren Fehler mehr
- **Standardmäßig deaktiviert** ([26cdf47](https://github.com/inventory69/simple-notes-sync/commit/26cdf47)) — in den Einstellungen → Debug aktivierbar, um beim Melden von Sync-Problemen Logs zu sammeln

**Cold-Start-Guard nach langer Prozess-Pause überspringen** ([17d57d5](https://github.com/inventory69/simple-notes-sync/commit/17d57d5))
- Nach längerer Inaktivität (z. B. über Nacht) wird der Cold-Start-Sync-Guard übersprungen, damit der erste Sync sofort beim Resume läuft

### 🐛 Fehlerbehebungen

**Zuverlässiger WiFi-Connect-Sync-Trigger**
- Validiertes WiFi (nicht nur SSID) erforderlich, bevor Sync ausgelöst wird ([a03eadf](https://github.com/inventory69/simple-notes-sync/commit/a03eadf))
- WiFi-Trigger-Sync wird bei vorübergehender Nicht-Erreichbarkeit erneut versucht ([bea0558](https://github.com/inventory69/simple-notes-sync/commit/bea0558))
- Globaler Cooldown wird für WiFi-Connect-Trigger umgangen — der erste Sync nach Reconnect läuft tatsächlich ([5195ee0](https://github.com/inventory69/simple-notes-sync/commit/5195ee0))
- WiFi-Trigger-Backoff bei 30 s linear gedeckelt, um ausufernde Verzögerungen zu vermeiden ([5ff2a70](https://github.com/inventory69/simple-notes-sync/commit/5ff2a70))
- WiFi-Fallback-Intervall auf 30 min verkürzt für schnellere Process-Death-Recovery ([42e6a64](https://github.com/inventory69/simple-notes-sync/commit/42e6a64))

**Günstigere, robustere Erreichbarkeits-Prüfung**
- Günstiger Reachability-Check vor teurem PROPFIND ([f46f326](https://github.com/inventory69/simple-notes-sync/commit/f46f326))
- `monitoringStartTime` als volatile markiert und auf `elapsedRealtime()` umgestellt — vermeidet Wall-Clock-Drift ([7f980fb](https://github.com/inventory69/simple-notes-sync/commit/7f980fb))
- Redundanten onResume-Sync-Cooldown entfernt ([69a7b05](https://github.com/inventory69/simple-notes-sync/commit/69a7b05))

**Einstellungen & Logging**
- Debug-Log-Export auf allen Geräten robust (SAF-Edge-Cases behandelt) ([bbf466b](https://github.com/inventory69/simple-notes-sync/commit/bbf466b))
- Der „Logs löschen"-Button löscht jetzt auch `sync_debug.log` ([caa329e](https://github.com/inventory69/simple-notes-sync/commit/caa329e))

### ♻️ Intern

**WebDAV-Server-Migration** ([41ecb13](https://github.com/inventory69/simple-notes-sync/commit/41ecb13))
- Self-hosted Referenz-Server von `bytemark/webdav` auf `hacdias/webdav` migriert — aktive Wartung und bessere Protokoll-Abdeckung
- Keine Auswirkungen für App-Nutzer; betrifft nur den optionalen mitgelieferten Server in `server/`

### 🌍 Übersetzungen

- **Neue Sprache: Indonesisch (`in`)** — danke an [Arif Budiman](https://hosted.weblate.org/user/aribudiman/) ([c537455](https://github.com/inventory69/simple-notes-sync/commit/c537455), [6bc7a4e](https://github.com/inventory69/simple-notes-sync/commit/6bc7a4e))
- **Neue Sprache: Norwegisch Bokmål (`nb`)** — danke an [xdpirate](https://hosted.weblate.org/user/xdpirate/) ([3ce9164](https://github.com/inventory69/simple-notes-sync/commit/3ce9164), [d11b34d](https://github.com/inventory69/simple-notes-sync/commit/d11b34d))
- **Neue Sprache: Italienisch (`it`)** — danke an [Jean-Pierre](https://hosted.weblate.org/user/Jeannot/) ([3467756](https://github.com/inventory69/simple-notes-sync/commit/3467756), [4bf449c](https://github.com/inventory69/simple-notes-sync/commit/4bf449c))
- **Neue Sprache: Hindi (`hi`)** — danke an [Silent Coder](https://hosted.weblate.org/user/silentcoder/) ([2c54735](https://github.com/inventory69/simple-notes-sync/commit/2c54735), [9ba64fd](https://github.com/inventory69/simple-notes-sync/commit/9ba64fd), [ecfb129](https://github.com/inventory69/simple-notes-sync/commit/ecfb129))
- **Neue Sprache: Russisch (`ru`)** — danke an [PONYATIN](https://hosted.weblate.org/user/PONYATIN/) ([4078a59](https://github.com/inventory69/simple-notes-sync/commit/4078a59), [f96055e](https://github.com/inventory69/simple-notes-sync/commit/f96055e), [1eeebc1](https://github.com/inventory69/simple-notes-sync/commit/1eeebc1))
- **Chinesisch (Vereinfacht) aktualisiert** — danke an [heretic43](https://hosted.weblate.org/user/heretic43/) ([7a2defb](https://github.com/inventory69/simple-notes-sync/commit/7a2defb))
- Neue Locales `in` und `nb` in `locales_config.xml` registriert ([349e972](https://github.com/inventory69/simple-notes-sync/commit/349e972)); `ru`, `it`, `hi` in [84a5282](https://github.com/inventory69/simple-notes-sync/commit/84a5282)

### 🙏 Danksagungen

Ein riesiges Dankeschön an die Weblate-Übersetzer, die Simple Notes Sync in immer mehr Sprachen verfügbar machen:
- **Arif Budiman** — Indonesisch
- **xdpirate** — Norwegisch Bokmål
- **Jean-Pierre** — Italienisch
- **Silent Coder** — Hindi
- **PONYATIN** — Russisch
- **heretic43** — Chinesisch (Vereinfacht)

---

## [2.3.1] - 2026-04-24

> Nur als Beta-Release im Google Play veröffentlicht. Nicht auf F-Droid erschienen; die Änderungen sind in v2.4.0 enthalten.

### 🐛 Fehlerbehebungen

**Sichtbarkeits-bewusster Fehler-Banner bei Soft-Sync-Fehlern** ([c4a40f8](https://github.com/inventory69/simple-notes-sync/commit/c4a40f8))
- Stille Hintergrund-Sync-Fehler erscheinen nicht mehr als roter Fehler-Banner — sie setzen sauber zu IDLE zurück
- Fehler werden nur dann zu einem sichtbaren Banner, wenn der Nutzer die App tatsächlich aktiv ansieht
- Neue `SyncStateManager.errorIfVisible()`-API wird von `SyncWorker` und `MainViewModel.triggerAutoSync` für alle Soft-Fehler-Pfade verwendet
- Verhindert verwirrende kurzzeitige Warnungen bei Hintergrund-Retries

---

## [2.3.0] - 2026-04-18

### 🛡️ Sicherheit

**WebDAV-Zugangsdaten werden jetzt verschlüsselt gespeichert** ([bf117f8](https://github.com/inventory69/simple-notes-sync/commit/bf117f8))
- WebDAV-Benutzername und -Passwort wurden bisher als Klartext in regulären SharedPreferences gespeichert
- Migration zu `EncryptedSharedPreferences` mit AES256-GCM-Verschlüsselung
- Einmalige Auto-Migration beim ersten Start: bestehende Zugangsdaten werden in den verschlüsselten Speicher übernommen und aus dem Klartext-Speicher entfernt

### ✨ Neue Features

**Akkuoptimierung-Dialog bei Sync-Aktivierung & Migration** ([da2ab36](https://github.com/inventory69/simple-notes-sync/commit/da2ab36))
- Wenn der Nutzer den Offline-Modus deaktiviert, prüft die App sofort die Akkuoptimierungs-Ausnahme und zeigt den System-Dialog bei Bedarf
- Einmalige Migration für bestehende Nutzer: Nutzer mit bereits aktiviertem Sync, die noch nie gefragt wurden, sehen den Dialog einmalig beim nächsten App-Start
- Verwendet neuen SharedPreferences-Key `battery_opt_migration_shown`

### 🐛 Fehlerbehebungen

**Markdown-Auto-Sync löst beim Speichern nicht aus** ([1756af4](https://github.com/inventory69/simple-notes-sync/commit/1756af4))
- SharedPreferences für Markdown-Export/Auto-Import wurden erst nach erfolgreichem Initial-Export gespeichert — bei Fehler (HTTP 405, Timeout, Netzwerkfehler) wurden die Prefs nie gesetzt und On-Save-Export hat nie ausgelöst
- Prefs werden jetzt sofort nach Server-Konfigurations-Validierung gespeichert; Initial-Export ist Best-Effort
- HTTP-405-Fallback in `ensureMarkdownDirExists()` hinzugefügt (List-after-failed-exists-Pattern)
- Danke an [@minosimo](https://github.com/minosimo) für den detaillierten Bug-Report mit Logs! ([#50](https://github.com/inventory69/simple-notes-sync/issues/50))

**MKCOL-404-Fehlerbehandlung und WebDAV-Validierung verbessert** ([8c5907a](https://github.com/inventory69/simple-notes-sync/commit/8c5907a))
- `SafeSardineWrapper.createDirectory()`: 404 mit `list()`-Fallback behandeln (analog zur bestehenden 405-Behandlung)
- `WebDavSyncService.testConnection()`: WebDAV-Fähigkeit via PROPFIND nach HEAD-Check verifizieren, um falschen „Erreichbar"-Status zu verhindern
- `SyncExceptionMapper`: MKCOL-Fehler erkennen und benutzerfreundliche Meldung mit WebDAV-URL-Hinweis anzeigen
- Danke an [@Ichigo-Meow](https://github.com/Ichigo-Meow) für die Meldung! ([#55](https://github.com/inventory69/simple-notes-sync/issues/55))

**Phantom-„Untitled"-Notizen vom WebDAV-Root-Scan stoppen** ([fab23eb](https://github.com/inventory69/simple-notes-sync/commit/fab23eb))
- Fremde JSON-Dateien im WebDAV-Root (z. B. `info.json`) wurden bei jedem Sync geparst und erzeugten eine neue „Untitled"-Geistnotiz mit zufälliger UUID — als SYNCED gespeichert, sofort als DELETED_ON_SERVER markiert, und so endlos akkumuliert
- Der v1.2.0-Root-Fallback im normalen Sync-Pfad ist deaktiviert; der Migrations-Scan läuft weiterhin in `restoreFromServer()`
- UUID-Format- und id-vs-Dateinamen-Prüfungen in `NoteDownloader` Phase 2 als Defense-in-Depth ergänzt
- Danke an [@angeld-jr2](https://github.com/angeld-jr2) für das ausführliche Debug-Log, das die Diagnose ermöglichte! ([#62](https://github.com/inventory69/simple-notes-sync/issues/62))

**Notizen ohne Titel in Backup-Validierung erlauben** ([d41d02b](https://github.com/inventory69/simple-notes-sync/commit/d41d02b))
- Backup-Wiederherstellung lehnte v2.2.0-Backups mit titellosen Notizen (z. B. Checklisten) ab
- `validateBackup()` verhält sich nun wie der Editor: eine Notiz ist nur ungültig, wenn Titel UND Inhalt/Checklisten-Items leer sind
- Danke an [@angeld-jr2](https://github.com/angeld-jr2) für die Meldung!

**HTTP 401 beim Verzeichnis-Anlegen als Auth-Fehler erkennen** ([02c3f77](https://github.com/inventory69/simple-notes-sync/commit/02c3f77))
- `ensureNotesDirectoryExists()` und `ensureMarkdownDirectoryExists()` haben 401 verschluckt und zu MKCOL durchgereicht — Anzeige war „Sync-Ordner kann nicht erstellt werden" statt „Authentifizierung fehlgeschlagen"
- Auth-Fehler werden jetzt vor MKCOL erkannt und durchgereicht; Defense-in-Depth in `SyncExceptionMapper`

**Widget-Checklisten-Sortierung mit Editor für alle Sortier-Optionen abgeglichen** ([dcc740b](https://github.com/inventory69/simple-notes-sync/commit/dcc740b))
- Widget `ToggleChecklistItemAction` unterstützte nur MANUAL und UNCHECKED_FIRST
- Gemeinsame `ChecklistSorter`-Utility extrahiert und in Widget und Editor eingesetzt — konsistente Sortierung für alle sieben Optionen
- Danke an MrsMinchen für den Beitrag!

**Auto-Speichern bei Änderung der Sortier-Option auslösen** ([06c5228](https://github.com/inventory69/simple-notes-sync/commit/06c5228))
- `sortChecklistItems(option)` setzte weder `isDirty` noch `scheduleAutosave` — Sortier-Änderungen gingen ohne explizites Speichern verloren
- Widget-Updates auch nach Auto-Save und saveOnBack hinzugefügt
- Danke an freemen für die Meldung!

**Widget-Inhalt kürzen, um TransactionTooLargeException zu verhindern** ([7aba796](https://github.com/inventory69/simple-notes-sync/commit/7aba796))
- Text-Notizen auf 100 Zeilen, Checklisten auf 100 Items im Widget begrenzt — sehr lange Notizen sprengten das 1MB-Binder-IPC-Limit für RemoteViews

**Widget-Empty-State lokalisieren und Tap-to-Reconfigure** ([0b7dbf8](https://github.com/inventory69/simple-notes-sync/commit/0b7dbf8))
- Hardcoded „Note not found" durch String-Resource ersetzt
- Tippen aufs Widget öffnet die Konfigurations-Activity, sodass Nutzer nach gelöschten Notiz-Daten wiederherstellen können

**NotesStorage-Datei-I/O vom Main Thread weg** ([645ce9e](https://github.com/inventory69/simple-notes-sync/commit/645ce9e))
- `saveNote`, `loadNote`, `loadAllNotes`, `deleteNote` sind jetzt suspend-Funktionen auf `Dispatchers.IO`
- Behebt Race-Condition-Crash in `loadAllNotes()` (FileNotFoundException zwischen listFiles/readText)
- Behebt leeres TextFieldState beim ersten Öffnen einer Notiz nach App-Start
- Behebt fälschliches PENDING-Markieren bei Zurück-Navigation aus leerem Editor
- Behebt „Notiz nicht gefunden"-Anzeige im Widget: `loadNoteSync()` läuft jetzt innerhalb `provideContent`, sodass jedes Widget-Update frische Daten lädt

**Editor-State-Flackern beim asynchronen Notiz-Laden verhindern** ([b7b3a1c](https://github.com/inventory69/simple-notes-sync/commit/b7b3a1c))
- Async-Laden zeigte 1+ Frames lang TEXT-Mode-Defaults, was bei Checklisten kurzzeitig falschen TopBar-Titel und falschen Inhalts-Typ verursachte
- `isNewNote=false` wird jetzt synchron vor der Coroutine gesetzt; isLoading gated den gesamten Screen

**Mutex-geschütztes Deletion-Tracking nutzen** ([e777259](https://github.com/inventory69/simple-notes-sync/commit/e777259))
- `deleteNote()` nutzt jetzt `trackDeletionSafe()`, um Race Conditions bei Batch-Löschungen zu verhindern; die ungeschützte Legacy-Variante ist deprecated

**Listen-Scrollposition beim Rückkehren aus dem Editor erhalten** ([c5b4955](https://github.com/inventory69/simple-notes-sync/commit/c5b4955))
- Neue-Notiz-Erkennung vom unsortierten Load in den sortierten Flow verschoben — Bearbeiten einer existierenden Notiz setzt die Scrollposition nicht mehr zurück

**Navigation-Flags über Process Death persistieren** ([78b331b](https://github.com/inventory69/simple-notes-sync/commit/78b331b))
- `cameFromEditor`/`cameFromSettings` werden via `onSaveInstanceState` gespeichert/wiederhergestellt — verhindert falsches Scroll-to-Top und Sync-Unterdrückung nach System-Process-Kill

**onSave-Sync nach Editor-Löschung auslösen** ([5c7f008](https://github.com/inventory69/simple-notes-sync/commit/5c7f008))
- `deleteNoteFromEditor` löst jetzt einen Sync aus, um die Löschung sofort an den Server zu propagieren — konsistent mit `saveNote`

**Erreichbarkeits-Check für Settings-`syncNow()`** ([d1928be](https://github.com/inventory69/simple-notes-sync/commit/d1928be))
- Der „Jetzt synchronisieren"-Pfad in den Einstellungen war der einzige Aufrufer, der `SyncGateChecker.isServerReachable()` umging — unerreichbare Server lösten FATAL-Exceptions statt sauberem Abbruch aus

**HTTP-HEAD-Check im Erreichbarkeits-Gate** ([13ad82e](https://github.com/inventory69/simple-notes-sync/commit/13ad82e))
- `SyncGateChecker` führt nach dem TCP-Socket-Check einen HEAD-Request aus, um sicherzustellen, dass der Server tatsächlich HTTP spricht — verhindert False-Positives bei Servern mit TLS-Problemen

**Zeitstempel-basierte Stale-Sync-State-Erkennung** ([bd394fa](https://github.com/inventory69/simple-notes-sync/commit/bd394fa))
- `SyncStateManager` setzt `SYNCING`-Zustand älter als 5 Minuten automatisch zurück; aufgerufen aus `Application.onCreate` und `MainViewModel.init`, um sowohl Process Death als auch Configuration Changes abzudecken

**Logging in 18 stillen Catch-Blöcken** ([728f33a](https://github.com/inventory69/simple-notes-sync/commit/728f33a))
- `catch (_: Exception)` durch geloggte Exceptions ersetzt in `ImportWizard`, `WebDavSyncService`, `ConnectionManager`, `SyncGateChecker`, `ThemePreferences`, `MainViewModel`, `NoteDownloader` und Widget-Code

**Logging und Nutzer-Hinweise für stille Fehler-Pfade** ([e6dac28](https://github.com/inventory69/simple-notes-sync/commit/e6dac28))
- `WidgetConfig` loggt jetzt Fehler; Import zeigt einen sichtbaren Hinweis, wenn trotz Kandidaten null Notizen importiert wurden

**Logging für 403-Workaround in `SafeSardineWrapper.exists`** ([4917fc4](https://github.com/inventory69/simple-notes-sync/commit/4917fc4))
- Warnung im Log, wenn der Jianguoyun-403-als-existiert-Workaround greift, sodass False-Positives in Debug-Logs sichtbar werden

**Null-Check für `sardine.getInputStream` in `readContent`** ([98a778f](https://github.com/inventory69/simple-notes-sync/commit/98a778f))
- Sardine kann für nicht existierende Ressourcen null zurückgeben; Safe-Call verhindert NPE beim Import

**Deprecated-APIs und Lint-Warnungen behoben** ([6b87dd2](https://github.com/inventory69/simple-notes-sync/commit/6b87dd2))
- `LocalClipboardManager` → `LocalClipboard` + `ClipEntry`
- `Icons.Filled.PlaylistAdd` → `Icons.AutoMirrored.Filled.PlaylistAdd`
- `EncryptedSharedPreferences`/`MasterKey`: Suppression dokumentiert (kein Ersatz für Android 7+ verfügbar)
- Alle `SharedPreferences.edit().putX().apply()`-Ketten zu KTX-`edit { }`-Blöcken konvertiert

**NPE in UrlValidator bei fehlerhaften URLs verhindern** ([1a3c6a7](https://github.com/inventory69/simple-notes-sync/commit/1a3c6a7))
- `parsedUrl.host` kann bei URLs wie `http:///path` null sein; Safe-Call mit Early-Return

**Hardcoded Deutsch im Akkuoptimierungs-Fallback ersetzen** ([98c39a6](https://github.com/inventory69/simple-notes-sync/commit/98c39a6))
- `ComposeSettingsActivity` nutzt jetzt die existierende String-Resource `battery_optimization_open_settings_failed`

**Hinweis bei Widget-Konfig-Ladefehler** ([8d7d03f](https://github.com/inventory69/simple-notes-sync/commit/8d7d03f))
- Die Widget-Konfigurations-Activity loggt jetzt den Fallback auf Defaults und zeigt einen Hinweis, wenn die vorherige Konfiguration nicht geladen werden konnte

### ✨ Verbesserungen

**Alle Toast-Nachrichten zu Material 3 Snackbar migriert** ([a834e25](https://github.com/inventory69/simple-notes-sync/commit/a834e25))
- Alle 9 `Toast.makeText()`-Aufrufstellen durch ViewModel-getriebene Snackbar-Events ersetzt — einheitliches UX
- `emitSnackbar()`-Helper in `MainViewModel` und `NoteEditorViewModel` ergänzt; `showToast()`-Extension deprecated

**Zentrale Spacing-Tokens** ([db4ce8f](https://github.com/inventory69/simple-notes-sync/commit/db4ce8f))
- `SpacingXSmall` (2dp), `SpacingMediumLarge` (12dp), `SpacingXXLarge` (32dp) zu `Dimensions` ergänzt; `MarkdownRenderer` auf zentrale Tokens migriert

**Hardcoded Legacy-Pfad-Filter durch Konstanten ersetzen** ([5f4d044](https://github.com/inventory69/simple-notes-sync/commit/5f4d044))
- `Constants.DEFAULT_SYNC_FOLDER_NAME` und `SyncUrlBuilder.MARKDOWN_SUFFIX` statt Magic Strings im Root-Fallback-Filter

**`DEFAULT_OFFLINE_MODE`-Konstante extrahieren** ([051054d](https://github.com/inventory69/simple-notes-sync/commit/051054d))
- Magic-Boolean in 3 `getBoolean()`-Aufrufen ersetzt; Kommentar dokumentiert, warum Default `true` ist (sicher bei Erstinstallation)

### 🔧 Interne Änderungen

- **StateFlow-Migration im `NoteEditorViewModel`** ([9071905](https://github.com/inventory69/simple-notes-sync/commit/9071905)) — `existingNote`, `isDirty`, `hasUnsavedChecklistEdits`, `isRestoringSnapshot` werden jetzt von `MutableStateFlow` getragen
- **In-Memory-Cache für `loadAllNotes` mit 2s TTL** ([8b74b43](https://github.com/inventory69/simple-notes-sync/commit/8b74b43)) — verhindert wiederholtes Lesen und Parsen aller JSON-Dateien bei jedem onResume; race-sicher via `AtomicLong`-Versions-Counter
- **`android.util.Log` durch Project Logger in 4 Dateien ersetzt** ([57c3246](https://github.com/inventory69/simple-notes-sync/commit/57c3246)) — `DragDropListState`, `NoteEditorScreen`, `ChecklistItemRow`, `SettingsViewModel`
- **`getTimeoutMs` in `ConnectionManager` dedupliziert** ([ea05e03](https://github.com/inventory69/simple-notes-sync/commit/ea05e03)) — `SyncGateChecker` delegiert jetzt
- **`BatteryOptimizationHelper` extrahiert** ([c3a1e3b](https://github.com/inventory69/simple-notes-sync/commit/c3a1e3b)) — `ComposeSettingsActivity` und `ComposeMainActivity` dedupliziert; `setAutoSync()` zeigt den Dialog nur, wenn nicht bereits exempt
- **Schreibreihenfolge SharedPreferences/StateFlow vereinheitlicht** ([908493b](https://github.com/inventory69/simple-notes-sync/commit/908493b)) — erst Prefs, dann State; In-Memory-Zustand divergiert nicht mehr bei Teilfehlern
- **`WidgetUpdateHelper` extrahiert** ([00f66db](https://github.com/inventory69/simple-notes-sync/commit/00f66db)) — einheitlicher Helper für das `GlanceAppWidgetManager → getGlanceIds → forEach update`-Pattern
- **`@Immutable` auf `Note` dokumentiert** ([15c6a12](https://github.com/inventory69/simple-notes-sync/commit/15c6a12))
- **Leere Product Flavors dokumentiert** ([602c06a](https://github.com/inventory69/simple-notes-sync/commit/602c06a))
- **Redundantes `viewBinding = false` entfernt** ([0e02124](https://github.com/inventory69/simple-notes-sync/commit/0e02124))
- **Widget-Magic-Numbers in benannte Konstanten extrahiert** ([2a84b32](https://github.com/inventory69/simple-notes-sync/commit/2a84b32))
- **Sichere Long-zu-Int-Konvertierung für Socket-Timeout** ([b77ac4d](https://github.com/inventory69/simple-notes-sync/commit/b77ac4d))
- **`MAX_LOG_ENTRIES` von 500 auf 5000 erhöht** ([b18fa8f](https://github.com/inventory69/simple-notes-sync/commit/b18fa8f)) — vollständige Sync-Zyklen mit 30+ Notizen können 500 Zeilen überschreiten
- **Toten Legacy-`toReadableTime` ohne Context-Param entfernt** ([d3ea343](https://github.com/inventory69/simple-notes-sync/commit/d3ea343))
- **Versions-Bump 2.2.0 → 2.3.0** ([71cf215](https://github.com/inventory69/simple-notes-sync/commit/71cf215))

### 🌍 Übersetzungen

- Chinesisch (vereinfacht) über Weblate aktualisiert ([1be36bb](https://github.com/inventory69/simple-notes-sync/commit/1be36bb)) — danke an [@heretic43](https://github.com/heretic43)!

Übersetzungs-Hosting freundlicherweise bereitgestellt von [Weblate](https://hosted.weblate.org/projects/simple-notes-sync/) — danke für das Sponsoring von Open-Source-Projekten! 🙏

### 🙏 Danksagungen

- [@angeld-jr2](https://github.com/angeld-jr2) — meldete den Backup-Validierungs-Crash und lieferte das Debug-Log, das den Phantom-Notizen-Bug diagnostizierbar machte
- [@Ichigo-Meow](https://github.com/Ichigo-Meow) — meldete das MKCOL/WebDAV-Validierungs-Problem ([#55](https://github.com/inventory69/simple-notes-sync/issues/55))
- [@minosimo](https://github.com/minosimo) — meldete die Markdown-Auto-Sync-Regression ([#50](https://github.com/inventory69/simple-notes-sync/issues/50))
- MrsMinchen — beigetragen: Widget-Checklisten-Sortier-Fix
- freemen — meldete das fehlende Auto-Speichern bei Sortier-Änderungen

---

## [2.2.0] - 2026-03-30

### ✨ Neue Features

**Share-Intent: Text als neue Notiz oder Checkliste empfangen** ([766f67e](https://github.com/inventory69/simple-notes-sync/commit/766f67e))
- Text/plain-Share-Intents aus anderen Apps als neue Notizen oder Checklisten empfangen
- Danke an [@madelgijs](https://github.com/madelgijs) für den Feature-Wunsch! ([Discussion #46](https://github.com/inventory69/simple-notes-sync/discussions/46))

**Neue-Notiz-Shortcut-Widget** ([5c79ab6](https://github.com/inventory69/simple-notes-sync/commit/5c79ab6))
- Homescreen-Widget mit Auto-Layout zum schnellen Erstellen neuer Notizen
- Danke an [@Stowaway2979](https://github.com/Stowaway2979) für den Feature-Wunsch! ([Discussion #49](https://github.com/inventory69/simple-notes-sync/discussions/49))

**Checklisten-Button in Markdown-Toolbar** ([2157a09](https://github.com/inventory69/simple-notes-sync/commit/2157a09))
- Checklisten-Items direkt aus der Markdown-Editor-Toolbar einfügen

**Checklisten-Item-Kontextmenü: Kopieren, Duplizieren, In-Checkliste-Kopieren** ([d98edd7](https://github.com/inventory69/simple-notes-sync/commit/d98edd7))
- Fokussiertes MoreVert-Menü an Checklisten-Items mit Text kopieren, Item duplizieren und Item in andere Checkliste kopieren
- Danke an freemen für den Feature-Wunsch!

**Auto-Einklappen erweiterter Items beim Ziehen** ([c030794](https://github.com/inventory69/simple-notes-sync/commit/c030794))
- Erweiterte Checklisten-Items klappen automatisch ein wenn Drag-and-Drop startet

### 🐛 Fehlerbehebungen

**Checklisten-Titel-Korruption durch fehlende Leerzeile behoben** ([c6cd50e](https://github.com/inventory69/simple-notes-sync/commit/c6cd50e))
- **Kritischer Fix:** `toMarkdown()` hat keine Leerzeile zwischen `# Titel` und erstem Checklisten-Item geschrieben → progressive Titel-Korruption bei jedem Sync-Zyklus (erstes Item wurde in den Titel verschluckt)
- Defensives Parsing in `fromMarkdown()` und `fromJson()` erkennt und repariert korrupte Titel
- Einmalige Migration repariert alle lokal gespeicherten korrupten Checklisten-Notizen beim ersten Start nach dem Update
- CRLF-Zeilenumbruch-Normalisierung im Markdown-Parser verhindert Parse-Fehler bei Windows-bearbeiteten Dateien
- Korruptions-Warn-Logging im Markdown-Sync-Import zur Überwachung
- Danke an freemen für die Hilfe beim Aufspüren der Korruptions-Kaskade!

**WiFi-Sync-WorkManager-Fallback** ([ee0b54c](https://github.com/inventory69/simple-notes-sync/commit/ee0b54c))
- Connectivity-Change-WorkManager-Fallback für zuverlässigen WiFi-getriggerten Sync hinzugefügt

### 🌍 Übersetzungen

- Chinesisch (vereinfacht) über Weblate aktualisiert ([9bcd4db](https://github.com/inventory69/simple-notes-sync/commit/9bcd4db), [864b23e](https://github.com/inventory69/simple-notes-sync/commit/864b23e))

### 📦 Code-Qualität

- APK-Packaging optimiert: gebündelte kotlin_builtins und LICENSE-Dateien entfernt (−28 KB)

---

## [2.1.0] - 2026-03-26

### 🐛 Fehlerbehebungen & UX-Verbesserungen

**Editor-Toolbar-Skalierung für schmale Displays** ([9b6ee8a](https://github.com/inventory69/simple-notes-sync/commit/9b6ee8a))
- Adaptive Toolbar: auf breiten Displays volle Titel + Undo/Redo in der Toolbar; auf schmalen Displays / großer Schriftskalierung: gekürzte Titel („Bearbeiten", „Neu") + Undo/Redo im Overflow-Menü
- Verhindert Textumbruch auf kleinen Bildschirmen und bei hoher Barrierefreiheits-Schriftskalierung
- Danke an [@xdpirate](https://github.com/xdpirate) für die Meldung! ([#48](https://github.com/inventory69/simple-notes-sync/issues/48))

**Markdown-Vorschau als Standardansicht** ([f8b15a5](https://github.com/inventory69/simple-notes-sync/commit/f8b15a5))
- Bestehende Textnotizen öffnen jetzt standardmäßig in der Markdown-Vorschau
- Neue Notizen starten weiterhin im Bearbeitungsmodus mit automatischem Tastatur-Fokus
- Danke an [@james0336](https://github.com/james0336), [@isawaway](https://github.com/isawaway) und MrsMinchen für den Vorschlag!

### 📝 Dokumentation & Metadaten

**Lizenz-Metadaten korrigiert** ([89667d1](https://github.com/inventory69/simple-notes-sync/commit/89667d1))
- Verbleibende Apache-2.0-Referenzen in F-Droid-Changelogs (versionCode 27) korrigiert — zeigen jetzt alle korrekt AGPL v3

**Issue-Templates vereinfacht** ([5a56cf4](https://github.com/inventory69/simple-notes-sync/commit/5a56cf4))
- Bug-Report- und Frage-Templates neu geschrieben: nur Englisch, weniger Pflichtfelder, aufgeräumteres Layout
- Danke an [@xdpirate](https://github.com/xdpirate) für das Feedback! ([#48](https://github.com/inventory69/simple-notes-sync/issues/48))

**Geplante Features v2.2.0** ([5e9168b](https://github.com/inventory69/simple-notes-sync/commit/5e9168b))
- Share-Intent ([Discussion #46](https://github.com/inventory69/simple-notes-sync/discussions/46) von [@madelgijs](https://github.com/madelgijs)), Neue-Notiz-Shortcut-Widget ([Discussion #49](https://github.com/inventory69/simple-notes-sync/discussions/49) von [@Stowaway2979](https://github.com/Stowaway2979)), Markdown-Checklisten-Button und Checklisten-Item-Kopieren zur Roadmap hinzugefügt

### 📦 Code-Qualität

**Detekt / Lint / ProGuard-Audit** ([b9e6782](https://github.com/inventory69/simple-notes-sync/commit/b9e6782))
- Vollständiger Code-Qualitäts-Audit: detekt, lint, ktlint, ProGuard und Unit-Tests nach allen v2.1.0-Änderungen verifiziert

---

## [2.0.0] - 2026-03-20

### 🎨 Kompletter Compose-Rewrite, Multi-Theme-System & Architektur-Überarbeitung

Major-Release: vollständige Migration zu Jetpack Compose, Entfernung aller Legacy-View-Codes (~2.300 Zeilen gelöscht), komplettes WebDavSyncService-Refactoring in fokussierte Module, Multi-Theme-System mit 7 Farbschemata und animierten Übergängen, Material 3 Shared-Axis-Navigation, Checklisten-Drag-and-Drop-Rewrite, umfassende Sync-Zuverlässigkeitsfixes und modernisierte Abhängigkeiten.

### ✨ Neue Features

**Multi-Theme-System mit animierten Übergängen und getönten Oberflächen** ([315c0a5](https://github.com/inventory69/simple-notes-sync/commit/315c0a5))
- Neuer ThemeMode-Selector: System, Hell, Dunkel, AMOLED
- 7 Farbschemata: Standard, Blau, Grün, Rot, Lila, Orange, Dynamisch (Material You ab API 31)
- Live-Vorschau mit Farbfeldern in den Anzeige-Einstellungen
- Crossfade-Theme-Übergänge (500 ms) — flüssig auch in Debug-Builds
- Getönte Oberflächenpaletten für alle Farbschemata (Notizkarten passen zum gewählten Theme)
- Status- und Navigationsleistenfarben synchronisieren sich bei Theme-Wechsel

**Grid-Spalten-Skalierungssteuerung** ([3d4c2e0](https://github.com/inventory69/simple-notes-sync/commit/3d4c2e0))
- Umschalten zwischen automatischer Grid-Skalierung (adaptive 150dp) und fester Spaltenanzahl
- Manuelle Spaltenanzahl 1–5 über Chip-Selector mit Mini-Grid-Vorschau
- Abschnitt nur sichtbar wenn Grid-Modus aktiv
- In Backup/Restore enthalten

**Undo/Redo direkt in der TopAppBar** ([75edf00](https://github.com/inventory69/simple-notes-sync/commit/75edf00))
- Undo und Redo vom Overflow-Menü zu direkten TopAppBar-Aktionen verschoben
- Buttons respektieren canUndo/canRedo-State und erscheinen ausgegraut wenn nicht verfügbar

**Anzeigemodus-Chip-Selector** ([046f325](https://github.com/inventory69/simple-notes-sync/commit/046f325))
- Radiogruppe durch Icon-über-Label-Chips ersetzt (konsistent mit Theme-/Farb-Selektoren)
- Einstellungs-Untertitel zeigt Anzeigemodus, Theme und Farbschema (z.B. „Listenansicht · Dunkel · Standard")

**Vollständiges App-Einstellungen-Backup/-Restore** ([4d07c11](https://github.com/inventory69/simple-notes-sync/commit/4d07c11))
- Backup/Restore umfasst jetzt alle Einstellungen: Server, Sync, Markdown, Anzeige, Notiz-Verhalten, Benachrichtigungen

**Autosave-Status in Anzeige-Einstellungen** ([92da701](https://github.com/inventory69/simple-notes-sync/commit/92da701))
- Anzeige-Einstellungen-Untertitel zeigt Autosave-Status statt Theme-Info

**Logging-Deaktivierungs-Dialog nach Export** ([2525a85](https://github.com/inventory69/simple-notes-sync/commit/2525a85))
- Nach dem Teilen der Debug-Logs fragt ein Dialog, ob das Datei-Logging deaktiviert werden soll

**Material 3 Shared-Axis-Übergänge** ([3f5d19d](https://github.com/inventory69/simple-notes-sync/commit/3f5d19d), [365b0dd](https://github.com/inventory69/simple-notes-sync/commit/365b0dd))
- Horizontale Shared-Axis-Übergänge (Slide + Fade) für alle Navigationen
- Konsistente Animationen für Zurück-Pfeil und Wisch-Geste ab API 34

### 🐛 Fehlerbehebungen

**Checklisten-Drag-and-Drop-Rewrite** ([89cc9a6](https://github.com/inventory69/simple-notes-sync/commit/89cc9a6))
- Kompletter Rewrite von DragDropListState (~200 → 797 Zeilen): unkontrolliertes Auto-Scroll, Index-Desync bei Separator-Kreuzungen, gleichzeitige Swap-Race-Conditions und Swap-Oszillation an Viewport-Kanten behoben
- Key-basiertes Item-Tracking, kontinuierliche Auto-Scroll-Schleife mit Mutex-gesperrten Swaps, Anti-Flapping-Guard, Viewport-Sicherheitschecks

**Offline-Server-Löschungen in Warteschlange** ([1a5c889](https://github.com/inventory69/simple-notes-sync/commit/1a5c889))
- Bei „Überall löschen" werden Notizen jetzt in eine Warteschlange gestellt und beim nächsten Sync verarbeitet, statt lautlos verloren zu gehen

**WebDAV-403-Kompatibilität** ([9523733](https://github.com/inventory69/simple-notes-sync/commit/9523733))
- Jianguoyun-WebDAV gibt 403 für HEAD auf Collections zurück — wird jetzt korrekt als „existiert" behandelt statt „nicht gefunden". Danke [@james0336](https://github.com/james0336) für die Meldung!

**Thread-Sicherheit und Ressourcen-Lecks** ([2ab04d1](https://github.com/inventory69/simple-notes-sync/commit/2ab04d1), [3c31f61](https://github.com/inventory69/simple-notes-sync/commit/3c31f61))
- Activity-Leaks in NetworkMonitor behoben, syncStatus zu StateFlow migriert, fileLock für Logger, Race-Conditions in MainViewModel mit StateFlow.update{} eliminiert
- Alle InputStream-/Connection-Leaks in Sardine-Aufrufen geschlossen, File-I/O vom Main-Thread verlagert, HttpURLConnection-Leak im Server-Statuscheck behoben

**Sync-Zuverlässigkeit** ([ec4bb1c](https://github.com/inventory69/simple-notes-sync/commit/ec4bb1c), [3d02118](https://github.com/inventory69/simple-notes-sync/commit/3d02118), [150543c](https://github.com/inventory69/simple-notes-sync/commit/150543c))
- onResume-Sync-Throttle überlebt keinen Prozess-Neustart mehr (jetzt In-Memory)
- Falscher Sync nach Paket-Update verhindert (Race-Condition in NetworkMonitor)
- Auto-Sync bei Rückkehr vom Editor und Einstellungen übersprungen

**Layout-Skalierung für kleine Bildschirme und große Schriften** ([ef38a0e](https://github.com/inventory69/simple-notes-sync/commit/ef38a0e))
- Responsive Fixes: scrollbare Dialoge, FilterChipRow Spaltenlayout, adaptiver Grid-Schwellwert 180→150dp reduziert. Danke an Mama <3

**Leere Pluralformen in de/tr/uk** ([2db80a4](https://github.com/inventory69/simple-notes-sync/commit/2db80a4))
- Fehlende Pluralformen für Zeitangaben ergänzt — Zeitstempel waren auf Notizkarten in Deutsch, Türkisch und Ukrainisch unsichtbar

**Sprachauswahl auf API 33+** ([f54ef49](https://github.com/inventory69/simple-notes-sync/commit/f54ef49))
- Weiterleitung zu nativen App-Spracheinstellungen auf API 33+ statt In-App-Auswahl (eliminiert Activity-Neustart-Flash)

**Speichern-bei-Zurück-Race-Condition** ([afbef19](https://github.com/inventory69/simple-notes-sync/commit/afbef19))
- TextFieldState flushen und in onPause speichern um Datenverlust bei Zurück-Navigation zu verhindern

**Banner-Farb-Blitz** ([9b7a285](https://github.com/inventory69/simple-notes-sync/commit/9b7a285))
- Letzte sichtbare Banner-Farben während Dismiss-Animation einfrieren

**Splash-Screen-Blitz** ([a5e0899](https://github.com/inventory69/simple-notes-sync/commit/a5e0899))
- Splash-Screen bleibt sichtbar bis Notizen geladen sind — kein leerer Bildschirm mehr beim Kaltstart

**FAB-Scrim-Übergangs-Artefakt** ([833c30e](https://github.com/inventory69/simple-notes-sync/commit/833c30e), [2f75467](https://github.com/inventory69/simple-notes-sync/commit/2f75467))
- FAB-Scrim wird vor Activity-Transition-Erfassung auf unsichtbar gesetzt, Fade-Out-Animation wiederhergestellt

**Snackbar in Einstellungen** ([24c62b8](https://github.com/inventory69/simple-notes-sync/commit/24c62b8), [0c76087](https://github.com/inventory69/simple-notes-sync/commit/0c76087))
- Unzuverlässigen Toast durch Snackbar in allen Einstellungsscreens ersetzt, wird über Tastatur angezeigt

### 🏗️ Architektur & Refactoring

**WebDavSyncService-Aufteilung** ([e0abff4](https://github.com/inventory69/simple-notes-sync/commit/e0abff4) → [7f467d7](https://github.com/inventory69/simple-notes-sync/commit/7f467d7))
- Monolithischer 2.735-Zeilen-WebDavSyncService in 8 fokussierte Module aufgeteilt: SyncGateChecker, ETagCache, SyncTimestampManager, SyncExceptionMapper, SyncUrlBuilder, ConnectionManager, NoteUploader, NoteDownloader, MarkdownSyncManager

**Legacy-Code-Entfernung** ([901ca77](https://github.com/inventory69/simple-notes-sync/commit/901ca77), [39b6e9f](https://github.com/inventory69/simple-notes-sync/commit/39b6e9f), [fb64a31](https://github.com/inventory69/simple-notes-sync/commit/fb64a31))
- SettingsActivity (1.072 Zeilen), MainActivity (857 Zeilen), NoteEditorActivity (344 Zeilen), alle XML-Layouts/Menüs/Drawables gelöscht
- Compose-Activities sind jetzt die einzigen Implementierungen

**Modernisierung** ([dfd3b33](https://github.com/inventory69/simple-notes-sync/commit/dfd3b33), [ad137c3](https://github.com/inventory69/simple-notes-sync/commit/ad137c3), [65b6a26](https://github.com/inventory69/simple-notes-sync/commit/65b6a26), [9e547d2](https://github.com/inventory69/simple-notes-sync/commit/9e547d2), [07a7502](https://github.com/inventory69/simple-notes-sync/commit/07a7502))
- LocalBroadcastManager durch SharedFlow ersetzt, viewModelFactory-DSL, overridePendingTransition durch ActivityOptions (API 34+), alle @Suppress("DEPRECATION") entfernt, AlertDialog.Builder durch Compose-AlertDialog

### 📦 Abhängigkeiten & Build

**Dependency-Updates** ([b57512f](https://github.com/inventory69/simple-notes-sync/commit/b57512f))
- Kotlin 2.0.21 → 2.1.0, Compose BOM 2026.01 → 2026.03, Lifecycle 2.7 → 2.8.7, Coroutines 1.7 → 1.9, Navigation 2.7 → 2.8.5, Activity 1.8 → 1.9.3 und 15+ weitere

**Code-Qualität** ([cf95fcd](https://github.com/inventory69/simple-notes-sync/commit/cf95fcd), [3933df0](https://github.com/inventory69/simple-notes-sync/commit/3933df0))
- Alle Lint- und Detekt-Warnungen behoben, 500 unbenutzte Strings gelöscht, ktlint-Formatierung in 91 Dateien, Kotlin 2.3.20

**APK-Größenoptimierung** ([ce86b68](https://github.com/inventory69/simple-notes-sync/commit/ce86b68))
- R8/ProGuard-Keep-Regeln verschärft — APK-Größe 5,4 MB → 5,2 MB (-200 KB, classes.dex -507 KB)

**CI/CD** ([c6a3f25](https://github.com/inventory69/simple-notes-sync/commit/c6a3f25))
- Tag-basierter GitHub-Actions-Release-Workflow für Draft-Releases

### 📄 Lizenz

**Lizenzwechsel** ([6baaeda](https://github.com/inventory69/simple-notes-sync/commit/6baaeda))
- Von MIT zu AGPL v3 gewechselt

---

## [1.12.0] - 2026-03-12

### 🌍 Internationalisierung, Einstellungen & Community

Fokussiertes Release mit Chinesisch (vereinfacht) Lokalisierung, korrekten Pluralformen für Zeitangaben, Benachrichtigungsstatus-Anzeige in der Einstellungs-Übersicht und Liberapay-Spendenintegration in der README.

### 🌍 Übersetzungen

**Chinesisch (vereinfacht) Übersetzung** ([15a49d6](https://github.com/inventory69/simple-notes-sync/commit/15a49d6))
- Vollständige zh-CN Lokalisierung (547 Strings) basierend auf Weblate-Beitrag von [@heretic43](https://github.com/heretic43) ([#40](https://github.com/inventory69/simple-notes-sync/issues/40))
- App-Name bleibt als „Simple Notes" (nicht übersetzt)

### 🐛 Fehlerbehebungen

**Korrekte i18n-Pluralformen für Zeitangaben** ([c804964](https://github.com/inventory69/simple-notes-sync/commit/c804964))
- `time_minutes_ago`, `time_hours_ago` und `time_days_ago` von `<string>` zu `<plurals>` konvertiert für grammatisch korrekte Formen in Sprachen wie Ukrainisch
- `Extensions.kt` auf `getQuantityString` umgestellt
- Alle bestehenden Sprachen aktualisiert (en/de/tr/uk/zh)

### ✨ Neue Features

**Benachrichtigungsstatus in der Einstellungs-Übersicht** ([5d82e7d](https://github.com/inventory69/simple-notes-sync/commit/5d82e7d))
- Die Sync & Benachrichtigungen-Karte auf dem Einstellungs-Hauptscreen zeigt jetzt den aktuellen Benachrichtigungsstatus neben der Trigger-Anzahl an
- Drei Zustände: „Benachrichtigungen aktiv", „Nur Fehler und Warnungen", „Benachrichtigungen deaktiviert"
- Konsistent mit dem Zusammenfassungsstil der anderen Einstellungskarten

### 📝 Dokumentation

**Liberapay-Spendenintegration** ([7081286](https://github.com/inventory69/simple-notes-sync/commit/7081286))
- Shields.io-Spendenbadge in die Badge-Reihe von README.md und README.de.md eingefügt
- Offizieller Liberapay-Spendenbutton im Contributing-Abschnitt hinzugefügt
- Beide Sprachversionen (EN/DE) der README aktualisiert

---

## [1.11.0] - 2026-03-10

### 🔔 Benachrichtigungen, FAB-Überarbeitung & Checklisten-Sortierung

Fokussiertes Release mit konfigurierbaren Benachrichtigungseinstellungen und Android 13+ Berechtigungshandling, auf- und absteigende Sortierung nach Erstellungsdatum für Checklisten, überarbeitetem FAB-Overlay mit animiertem Scrim und vereinheitlichten Action-Pills, umstrukturierten Sync-Einstellungen, versteckten Entwickleroptionen und mehreren Bugfixes rund um Autosave-Verhalten und Sync-Zählung.

### ✨ Neue Features

**Benachrichtigungseinstellungen mit Berechtigungshandling** ([c1f5078](https://github.com/inventory69/simple-notes-sync/commit/c1f5078))
- Drei neue Toggles unter Sync & Benachrichtigungen: Global ein/aus, Nur-Fehler-Modus, Server-nicht-erreichbar-Warnung
- Vollständiger Android 13+ POST_NOTIFICATIONS Permission-Flow: Abfrage beim ersten Aktivieren, Erklärungsdialog bei Ablehnung, Weiterleitung zu App-Einstellungen bei dauerhafter Ablehnung
- Berechtigungsstatus wird bei jedem Screen-Resume geprüft — Toggle synchronisiert mit Systemstatus

**Checklisten nach Erstellungsdatum sortieren (auf- und absteigend)** ([a265e3c](https://github.com/inventory69/simple-notes-sync/commit/a265e3c))
- Neue Sortieroption „Erstellungsdatum ↑" (älteste zuerst) und „Erstellungsdatum ↓" (neueste zuerst)
- Jedes Checklisten-Item erhält einen `createdAt`-Zeitstempel, der über Bearbeitungen und Syncs erhalten bleibt
- Abwärtskompatibel: alte Items ohne Zeitstempel verwenden Index als Fallback
- Separator zwischen offenen/erledigten Gruppen bleibt bei beiden Richtungen erhalten

**Entwickleroptionen hinter Easter Egg versteckt** ([186a345](https://github.com/inventory69/simple-notes-sync/commit/186a345))
- Debug & Diagnose-Einstellungen standardmäßig ausgeblendet
- Freischaltung durch 5× Tippen auf die App-Info-Karte im Über-Screen (Android-Entwickleroptionen-Stil)
- Nur für die Sitzung: wird beim App-Neustart zurückgesetzt, nicht in SharedPreferences persistiert
- Countdown-Toast für die letzten 2 Taps, Bestätigungs-Toast bei Freischaltung

### 🐛 Fehlerbehebungen

**Autosave bei leeren Checklisten-Items ausgelöst** ([f7e25db](https://github.com/inventory69/simple-notes-sync/commit/f7e25db))
- Hinzufügen oder Löschen leerer Checklisten-Items löst kein Autosave mehr aus
- No-Change-Guard in `performSave()` und `saveOnBack()` überspringt Speichern wenn nur leere Items vom gespeicherten Stand abweichen
- Autosave-Indikator erscheint nicht mehr irreführend nach dem Hinzufügen eines leeren Items

**Markdown-Auto-Sync: Doppelzählung** ([7dd092e](https://github.com/inventory69/simple-notes-sync/commit/7dd092e))
- Das Erstellen einer Notiz mit aktiviertem Markdown-Auto-Sync zeigt nicht mehr „2 Notizen synchronisiert" statt 1
- Exportierte Markdown-Dateien werden im selben Sync-Zyklus per Notiz-ID-Tracking vom Re-Import ausgeschlossen
- Technische Re-Uploads nach Markdown-Import zählen nicht mehr zum Sync-Count
- `null` vs. `emptyList()`-Inkonsistenz im Checklisten-Inhaltsvergleich behoben

**Irreführendes „Markdown importieren"-Banner bei unveränderter Dateilage** ([e352ce1](https://github.com/inventory69/simple-notes-sync/commit/e352ce1))
- Progress-Banner zeigt keinen Dateinamen (z. B. „Test Neu.md") mehr wenn tatsächlich 0 Dateien importiert werden
- Fast-Path überspringt die IMPORTING_MARKDOWN-Phase vollständig wenn alle Server-Dateien unverändert sind
- Dateiname im Progress-Update wird nur noch gesetzt wenn die Datei tatsächlich heruntergeladen wird

### 🎨 UI-Verbesserungen

**Animierter FAB-Scrim und verbesserte Farben** ([4a8a202](https://github.com/inventory69/simple-notes-sync/commit/4a8a202))
- FAB-Overlay deckt jetzt den gesamten Bildschirm ab, inklusive Statusleiste (aus dem Scaffold herausbewegt)
- Semi-transparenter animierter Scrim (50 % Schwarz, 250 ms Tween) ersetzt transparentes Dismiss-Overlay
- Sub-Action-Farben von `secondaryContainer` auf `primaryContainer` geändert für bessere Hierarchie

**Vereinheitlichte Action-Pills** ([847154f](https://github.com/inventory69/simple-notes-sync/commit/847154f))
- Label-Text und Icon in einer einzigen breiten Pill pro Aktion zusammengefasst (Aegis Authenticator-Stil)
- Gesamte Pill ist ein Klickziel — kein separates Label + Icon-FAB mehr
- Verwendet `surfaceContainerHigh` / `onSurface` für konsistente Darstellung im Dark Mode

**Sync-Einstellungen umstrukturiert** ([e9d48cb](https://github.com/inventory69/simple-notes-sync/commit/e9d48cb))
- Screen umbenannt von „Sync-Einstellungen" zu „Sync & Benachrichtigungen"
- Sektions-Header umbenannt von „Netzwerk & Performance" zu „Netzwerk"
- Sub-Header „Sofort-Sync" und „Hintergrund-Sync" entfernt für saubereres Layout
- Sync-Trigger, Netzwerk und Benachrichtigungen in separate Composables extrahiert für isolierte Recomposition

**Smooth Fade-Transitions in der Settings-Navigation** ([1ef565d](https://github.com/inventory69/simple-notes-sync/commit/1ef565d))
- Alle Settings-Routen nutzen jetzt einen 700 ms Fade statt des abrupten Standard-Crossfades
- Global auf NavHost-Ebene definiert — kein duplizierter Code pro Route

### 🌍 Übersetzungen

Dank an [@FromKaniv](https://github.com/FromKaniv) für die ukrainische Übersetzung und [@ksuheyl](https://github.com/ksuheyl) für die türkische Übersetzung!

---

## [1.10.0] - 2026-03-01

### ✏️ Editor-Überarbeitung, Teilen / Export & Sync-Zuverlässigkeit

Großes Release mit PDF-Export, Text- und Kalender-Teilen, überarbeitetem FAB-Menü, Löschen aus dem Editor mit Rückgängig, Batch-Serverlöschung mit Fortschrittsanzeige, adaptiven Tablet-Layouts, WorkManager-Zuverlässigkeitsverbesserungen und echten deterministischen Fortschrittsbalken für alle Sync-Phasen.

### ✨ Neue Features

**Teilen & Exportieren aus dem Editor** ([e2b9f79](https://github.com/inventory69/simple-notes-sync/commit/e2b9f79), [2aca873](https://github.com/inventory69/simple-notes-sync/commit/2aca873), [57c4e96](https://github.com/inventory69/simple-notes-sync/commit/57c4e96)) _(Danke an [@james0336](https://github.com/james0336) für den PDF-Export-Wunsch!)_
- Neues Überlaufmenü (⋮) in der Editor-Toolbar: Als Text teilen, Als PDF teilen, In Kalender exportieren
- PDF nativ über `PdfDocument`-API erstellt — keine Drittanbieter-Bibliothek erforderlich
- Teilen via `FileProvider` für sichere Freigabe mit beliebigen PDF-Viewern
- Kalender-Export füllt Titel, ganztägiges Startdatum (heute) und Notizinhalt als Beschreibung vor

**Aufklappendes FAB-Menü** ([85d68c4](https://github.com/inventory69/simple-notes-sync/commit/85d68c4), [61788e3](https://github.com/inventory69/simple-notes-sync/commit/61788e3))
- FAB durch Speed-Dial ersetzt: Tippen auf `+` zeigt animierte Sub-Action-Buttons für Text-Notiz und Checkliste
- `+`-Icon rotiert zu `×` beim Aufklappen; Sub-Actions gleiten mit gestaffelter Feder-Animation ein
- Transparentes Dismiss-Overlay schließt das Menü beim Tippen außerhalb
- Sub-Action-Buttons und Label-Pills nutzen `secondaryContainer`-Farbe als visuelle Einheit
- Stärkere Schatten-Elevation (8–10 dp) für klare visuelle Trennung vom Notiz-Grid in hellem und dunklem Theme

**Notiz aus Editor löschen mit Rückgängig** ([f3fd806](https://github.com/inventory69/simple-notes-sync/commit/f3fd806))
- Löschaktion von blockierendem Dialog auf ein Bottom Sheet umgestellt
- Nach Bestätigung schließt der Editor und der Hauptscreen zeigt eine timed Rückgängig-Snackbar
- Rückgängig stellt die Notiz wieder her und bricht eine geplante Server-Löschung ab

**Batch-Serverlöschung mit Fortschrittsanzeige** ([39a873f](https://github.com/inventory69/simple-notes-sync/commit/39a873f))
- Neue `DELETING`-Sync-Phase im Banner beim Batch-Löschen mehrerer Notizen vom Server
- Fortschrittsbalken zeigt `aktuell / gesamt` mit dem aktuellen Notiz-Titel
- Phase wechselt sanft zu `COMPLETED` mit einer Ergebnis-Meldung

**Adaptive Layouts für Tablets & Querformat** ([a117cbe](https://github.com/inventory69/simple-notes-sync/commit/a117cbe))
- Editor-Inhalt auf maximal 720 dp Breite begrenzt und auf breiten Bildschirmen zentriert
- Einstellungs-Screens auf maximal 600 dp Breite begrenzt und zentriert
- Haupt-Grid nutzt `Adaptive(180 dp)` Spalten — auf Tablets und im Querformat erscheinen automatisch mehr Spalten
- Vorbereitung für Android 16 (targetSdk 36), das `screenOrientation`-Locks auf Displays ≥ 600 dp ignoriert

**Rückgängig/Wiederherstellen im Notiz-Editor** ([484bf3a](https://github.com/inventory69/simple-notes-sync/commit/484bf3a))
- Vollständige Undo/Redo-Unterstützung für Text-Notizen und Checklisten via Toolbar-Buttons
- Debounced Snapshots: schnelles Tippen wird zu einem einzelnen Undo-Schritt gruppiert (500 ms Fenster)
- Stack auf 50 Einträge begrenzt; wird beim Notizwechsel geleert
- Wiederhergestellte Snapshots aktualisieren die Cursor-Position korrekt

**Konfigurierbarer WebDAV-Verbindungs-Timeout** ([b1aebc4](https://github.com/inventory69/simple-notes-sync/commit/b1aebc4))
- Neuer Settings-Slider (1–30 s, Standard 8 s) zur Konfiguration des WebDAV-Timeouts
- Wird auf alle OkHttpClient-Instanzen angewendet (Connect, Read, Write)
- Einheitliche Fehlermeldungen für Timeout, Auth-Fehler, Nicht gefunden und Server-Fehler

**Markdown-Auto-Sync Timeout-Schutz** ([7f74ae9](https://github.com/inventory69/simple-notes-sync/commit/7f74ae9))
- Aktivierung von Markdown-Auto-Sync hat jetzt einen 10-s-Timeout für den initialen Export
- UI-Toggle aktualisiert optimistisch und kehrt bei Fehler oder Timeout zurück
- Verhindert, dass der Einstellungs-Screen bei unerreichbaren Servern hängt

**Speichern beim Zurücknavigieren** ([402382c](https://github.com/inventory69/simple-notes-sync/commit/402382c)) _(Danke an [@GitNichtGibtsNicht](https://github.com/GitNichtGibtsNicht) für den Autosave-beim-Zurück-Wunsch!)_
- Ungespeicherte Notizen werden beim Verlassen des Editors automatisch gespeichert (System-Zurück + Toolbar-Zurück)
- Nur aktiv wenn Autosave aktiviert ist; synchrones Speichern ohne Sync auszulösen
- Autosave-Toggle-Beschreibung erwähnt jetzt dieses Verhalten

### 🐛 Fehlerbehebungen

**Download-Fortschritt immer indeterminate** ([c83aae3](https://github.com/inventory69/simple-notes-sync/commit/c83aae3))
- `ParallelDownloader` trackte `abgeschlossen / gesamt` intern korrekt, aber `syncNotes()` hat `total = 0` hardcodiert
- Behoben: `total` wird jetzt korrekt weitergegeben → DOWNLOADING-Phase zeigt einen echten `LinearProgressIndicator`
- `importMarkdownFiles()` meldet jetzt ebenfalls Datei-für-Datei-Fortschritt: Banner zeigt `X / Y dateiname.md` mit determiniertem Balken

**FGS-Timeout auf Android 15+** ([1e6eb64](https://github.com/inventory69/simple-notes-sync/commit/1e6eb64))
- `ensureActive()`-Checkpoints in der Download-Schleife und beim Markdown-Import von `WebDavSyncService` hinzugefügt, damit Coroutines bei WorkManager-Abbruch auf targetSdk 35+ sofort reagieren
- `CancellationException`-Handler in `SyncWorker` loggt jetzt den Stop-Grund (API 31+)

**WorkManager-Quota / Standby-Stops nicht sichtbar** ([3d66a19](https://github.com/inventory69/simple-notes-sync/commit/3d66a19))
- Detailliertes Stop-Reason-Logging: 16 WorkManager-Stop-Codes auf lesbare Namen gemappt
- Wenn ein Sync durch JobScheduler-Quota oder App-Standby gestoppt wird, erscheint beim nächsten App-Start ein Info-Banner

**Konsistente Position des Editor-Überlaufmenüs** ([242ece3](https://github.com/inventory69/simple-notes-sync/commit/242ece3))
- Überlaufmenü (⋮) ist jetzt am `⋮`-Button verankert, auch bei Checklisten-Notizen
- Zuvor war das Menü an der äußeren Actions-`Row` verankert und erschien bei Checklisten zu weit links

**Markdown-Import: Inhalt vor Überschrift verloren** ([e33ac23](https://github.com/inventory69/simple-notes-sync/commit/e33ac23))
- Inhalt vor dem ersten `#`-Heading wurde beim Markdown-Import still verworfen
- Checklisten-Erkennung verbessert: mehr Item-Muster werden jetzt erkannt

**Checklisten-Autosave nicht bei Item-Änderungen ausgelöst** ([5401df3](https://github.com/inventory69/simple-notes-sync/commit/5401df3))
- Löschen, Hinzufügen und Verschieben von Checklisten-Items markiert die Notiz jetzt korrekt als geändert und löst Autosave aus

**Minimales Scrollen beim Hinzufügen neuer Checklisten-Items** ([c2fbe0b](https://github.com/inventory69/simple-notes-sync/commit/c2fbe0b))
- Neue Checklisten-Items scrollen nur so weit, dass das Item sichtbar wird, statt zur Listenoberseite zu springen

**Falsches Autosave beim Tippen in Checkliste** ([9ea7089](https://github.com/inventory69/simple-notes-sync/commit/9ea7089))
- Antippen eines Checklisten-Items zum Platzieren des Cursors löst kein falsches Autosave mehr aus
- No-Op-Guards in `updateChecklistItemText()` und `updateChecklistItemChecked()`

**Undo auf Originalzustand löste trotzdem Autosave aus** ([cf5027b](https://github.com/inventory69/simple-notes-sync/commit/cf5027b))
- Rückgängig-Machen aller Änderungen zum letzten Speicherzustand setzt `isDirty` jetzt korrekt zurück und bricht das ausstehende Autosave ab
- Neues `savedSnapshot`-Property erfasst den Zustand beim Laden und nach jedem expliziten Speichern

**Fremde JSON-Dateien unnötig heruntergeladen** ([c409243](https://github.com/inventory69/simple-notes-sync/commit/c409243))
- Nicht-Notiz-JSON-Dateien (z.B. `google-services.json`) werden jetzt vor dem Download via UUID-Format-Check gefiltert

**Notiz-Anzahl-Strings nicht korrekt pluralisiert** ([8ca8df3](https://github.com/inventory69/simple-notes-sync/commit/8ca8df3))
- Notiz-Anzahl-Strings in korrekte Android-Pluralformen konvertiert (EN + DE)

### 🎨 UI-Verbesserungen

**Sanfte Sync-Banner-Animationen** ([c409243](https://github.com/inventory69/simple-notes-sync/commit/c409243))
- Banner-Einblendung: fadeIn (300 ms, EaseOutCubic) — kein abruptes „Reinschieben von oben" mehr
- Banner-Ausblendung: fadeOut + shrinkVertically (300/400 ms, EaseInCubic)
- Phasen-Übergänge nutzen AnimatedContent-Crossfade (250 ms) für Textwechsel
- Mindest-Anzeigedauer pro aktiver Phase (400 ms) verhindert unlesbare Blitze
- Auto-Hide-Job vom Flow-Collector entkoppelt — garantierte Mindest-Anzeigedauer

**Markdown-Ordner in Sync-Einstellungen erwähnt** ([a8bb80c](https://github.com/inventory69/simple-notes-sync/commit/a8bb80c))
- Sync-Ordner-Hinweis erwähnt jetzt explizit das `notes-md/`-Unterverzeichnis, das für Markdown-Auto-Sync verwendet wird

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
