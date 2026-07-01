# Backup & Wiederherstellung 💾

**🌍 Sprachen:** **Deutsch** · [English](BACKUP.md)

> Sichere deine Notizen lokal - unabhängig vom Server

---

## 📋 Übersicht

Das Backup-System funktioniert **komplett offline** und unabhängig vom WebDAV-Server. Perfekt für:
- 📥 Regelmäßige Sicherungen
- 📤 Migration zu neuem Server
- 🔄 Wiederherstellung nach Datenverlust
- 💾 Archivierung alter Notizen

---

## 📥 Backup erstellen

### Schritt-für-Schritt

1. **Einstellungen öffnen** (⚙️ Icon oben rechts)
2. **"Backup & Wiederherstellung"** Section finden
3. **"📥 Backup erstellen"** antippen
4. **Speicherort wählen:**
   - 📁 Downloads
   - 💳 SD-Karte
   - ☁️ Cloud-Ordner (Nextcloud, Google Drive, etc.)
   - 📧 E-Mail als Anhang
5. **Fertig!** Backup-Datei ist gespeichert

### Dateiformat

**Dateiname:** `simplenotes_backup_YYYY-MM-DD_HHmmss.json`

**Beispiel:** `simplenotes_backup_2026-01-05_143022.json`

**Inhalt:**
```json
{
  "version": "1.2.1",
  "exported_at": "2026-01-05T14:30:22Z",
  "notes_count": 42,
  "notes": [
    {
      "id": "abc-123-def",
      "title": "Einkaufsliste",
      "content": "Milch\nBrot\nKäse",
      "createdAt": 1704467422000,
      "updatedAt": 1704467422000
    }
  ]
}
```

**Format-Details:**
- ✅ Menschenlesbar (formatiertes JSON)
- ✅ Vollständige Notizdaten (Titel, Inhalt, IDs, Timestamps, Farbe, Pin-Status, Ordner, Checklisten-Items)
- ✅ App-Einstellungen & Ordner-Metadaten inklusive _(v2.0.0+ / v2.8.0)_
- ✅ Versions-Info für Kompatibilität
- ✅ Anzahl der Notizen für Validierung

---

## 📤 Backup wiederherstellen

### 3 Wiederherstellungs-Modi

#### 1. Zusammenführen (Merge) ⭐ _Empfohlen_

**Was passiert:**
- ✅ Neue Notizen aus Backup werden hinzugefügt
- ✅ Bestehende Notizen bleiben unverändert
- ✅ Keine Datenverluste

**Wann nutzen:**
- Backup von anderem Gerät einspielen
- Alte Notizen zurückholen
- Versehentlich gelöschte Notizen wiederherstellen

**Beispiel:**
```
App:    [Notiz A, Notiz B, Notiz C]
Backup: [Notiz A, Notiz D, Notiz E]
Ergebnis: [Notiz A, Notiz B, Notiz C, Notiz D, Notiz E]
```

#### 2. Ersetzen (Replace)

**Was passiert:**
- ❌ ALLE bestehenden Notizen werden gelöscht
- ✅ Backup-Notizen werden importiert
- ⚠️ Unwiderruflich (außer durch Auto-Backup)

**Wann nutzen:**
- Server-Wechsel (kompletter Neustart)
- Zurück zu altem Backup-Stand
- App-Neuinstallation

**Beispiel:**
```
App:    [Notiz A, Notiz B, Notiz C]
Backup: [Notiz X, Notiz Y]
Ergebnis: [Notiz X, Notiz Y]
```

**⚠️ Warnung:** Automatisches Sicherheits-Backup wird erstellt!

#### 3. Duplikate überschreiben (Overwrite)

**Was passiert:**
- ✅ Neue Notizen aus Backup werden hinzugefügt
- 🔄 Bei ID-Konflikten gewinnt das Backup
- ✅ Andere Notizen bleiben unverändert

**Wann nutzen:**
- Backup ist neuer als App-Daten
- Desktop-Änderungen einspielen
- Konflikt-Auflösung

**Beispiel:**
```
App:    [Notiz A (v1), Notiz B, Notiz C]
Backup: [Notiz A (v2), Notiz D]
Ergebnis: [Notiz A (v2), Notiz B, Notiz C, Notiz D]
```

### Wiederherstellungs-Prozess

1. **Einstellungen** → **"📤 Aus Datei wiederherstellen"**
2. **Backup-Datei auswählen** (`.json`)
3. **Modus wählen:**
   - 🔵 Zusammenführen _(Standard)_
   - 🟡 Duplikate überschreiben
   - 🔴 Ersetzen _(Vorsicht!)_
4. **Bestätigen** - Automatisches Sicherheits-Backup wird erstellt
5. **Warten** - Import läuft
6. **Fertig!** - Erfolgsmeldung mit Anzahl importierter Notizen

---

## 🛡️ Automatisches Sicherheits-Backup

**Vor jeder Wiederherstellung:**
- ✅ Automatisches Backup wird erstellt
- 📁 Gespeichert in: `Android/data/dev.dettmer.simplenotes/files/`
- 🏷️ Dateiname: `auto_backup_before_restore_YYYY-MM-DD_HHmmss.json`
- ⏱️ Zeitstempel: Direkt vor Wiederherstellung

**Warum?**
- Schutz vor versehentlichem "Ersetzen"
- Möglichkeit zum Rückgängigmachen
- Doppelte Sicherheit

**Zugriff via Dateimanager:**
```
/Android/data/dev.dettmer.simplenotes/files/auto_backup_before_restore_*.json
```

---

## 💡 Best Practices

### Backup-Strategie

#### Regelmäßige Backups
```
Täglich:   ❌ Zu oft (Server-Sync reicht)
Wöchentlich: ✅ Empfohlen für wichtige Notizen
Monatlich:  ✅ Archivierung
Vor Updates: ✅ Sicherheit
```

#### 3-2-1 Regel
1. **3 Kopien** - Original + 2 Backups
2. **2 Medien** - z.B. SD-Karte + Cloud
3. **1 Offsite** - z.B. Cloud-Speicher

### Backup-Speicherorte

**Lokal (schnell):**
- 📱 Internal Storage / Downloads
- 💳 SD-Karte
- 🖥️ PC (via USB)

**Cloud (sicher):**
- ☁️ Nextcloud (Self-Hosted)
- 📧 E-Mail an sich selbst
- 🗄️ Syncthing (Sync zwischen Geräten)

**⚠️ Vermeiden:**
- ❌ Google Drive / Dropbox (Privacy)
- ❌ Nur eine Kopie
- ❌ Nur auf Server (wenn Server ausfällt)

---

## 🔧 Erweiterte Nutzung

### Backup-Datei bearbeiten

Die `.json` Datei kann mit jedem Texteditor bearbeitet werden:

1. **Öffnen mit:** VS Code, Notepad++, nano
2. **Notizen hinzufügen/entfernen**
3. **Titel/Inhalt ändern**
4. **IDs anpassen** (für Migration)
5. **Speichern** und in App importieren

**⚠️ Wichtig:**
- Valides JSON-Format behalten
- IDs müssen eindeutig sein (UUIDs)
- Timestamps in Millisekunden (Unix Epoch)

### Bulk-Import

Mehrere Backups zusammenführen:

1. Backup 1 importieren (Modus: Zusammenführen)
2. Backup 2 importieren (Modus: Zusammenführen)
3. Backup 3 importieren (Modus: Zusammenführen)
4. Ergebnis: Alle Notizen vereint

### Server-Migration

Schritt-für-Schritt:

1. **Backup erstellen** auf altem Server
2. **Neuen Server einrichten** (siehe [QUICKSTART.md](QUICKSTART.md))
3. **Server-URL ändern** in App-Einstellungen
4. **Backup wiederherstellen** (Modus: Ersetzen)
5. **Sync testen** - Alle Notizen auf neuem Server

---

## ❌ Fehlerbehebung

### "Backup-Datei ungültig"

**Ursachen:**
- Korrupte JSON-Datei
- Falsche Datei-Endung (muss `.json` sein)
- Inkompatible App-Version

**Lösung:**
1. JSON-Datei mit Validator prüfen (z.B. jsonlint.com)
2. Dateiendung überprüfen
3. Backup mit aktueller App-Version erstellen

### "Keine Berechtigung zum Speichern"

**Ursachen:**
- Speicher-Berechtigung fehlt
- Schreibgeschützter Ordner

**Lösung:**
1. Android: Einstellungen → Apps → Simple Notes → Berechtigungen
2. "Speicher" aktivieren
3. Anderen Speicherort wählen

### "Import fehlgeschlagen"

**Ursachen:**
- Zu wenig Speicherplatz
- Korrupte Backup-Datei
- App-Crash während Import

**Lösung:**
1. Speicherplatz freigeben
2. Backup-Datei neu erstellen
3. App neu starten und erneut importieren

---

## 🔒 Sicherheit & Privacy

### Daten-Schutz
- ✅ **Lokal gespeichert** - Kein Cloud-Upload ohne deine Aktion
- ✅ **Optionale Verschlüsselung** _(v1.7.0+)_ - Backup-Dateien mit Passwort schützen
- ✅ **Menschenlesbar** - Klartextformat (JSON) wenn unverschlüsselt
- ⚠️ **Sensible Daten?** - Verschlüsselung aktivieren oder externe Tools nutzen (z.B. 7-Zip)

### Empfehlungen
- 🔐 Backup-Dateien in verschlüsseltem Container speichern
- 🗑️ Alte Backups regelmäßig löschen
- 📧 Nicht per unverschlüsselter E-Mail versenden
- ☁️ Self-Hosted Cloud nutzen (Nextcloud)

---

## 📊 Technische Details

### Format-Spezifikation

**JSON-Struktur:**
```json
{
  "version": "string",        // App-Version beim Export
  "exported_at": "ISO8601",   // Zeitstempel des Exports
  "notes_count": number,      // Anzahl der Notizen
  "notes": [
    {
      "id": "UUID",           // Eindeutige ID
      "title": "string",      // Notiz-Titel
      "content": "string",    // Notiz-Inhalt
      "createdAt": number,    // Unix Timestamp (ms)
      "updatedAt": number     // Unix Timestamp (ms)
    }
  ]
}
```

### Kompatibilität
- ✅ v1.2.0+ - Vollständig kompatibel
- ⚠️ v1.1.x - Grundfunktionen (ohne Auto-Backup)
- ❌ v1.0.x - Nicht unterstützt

---

**📚 Siehe auch:**
- [QUICKSTART.md](../QUICKSTART.md) - App-Installation und Einrichtung
- [FEATURES.md](FEATURES.md) - Vollständige Feature-Liste
- [DESKTOP.md](DESKTOP.md) - Desktop-Integration mit Markdown

**Letzte Aktualisierung:** v2.8.0 (2026-06-16)
