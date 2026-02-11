# Selbstsignierte SSL-Zertifikate

**Seit:** v1.7.0  
**Status:** ✅ Unterstützt

**🌍 Sprachen:** **Deutsch** · [English](SELF_SIGNED_SSL.md)

---

## Übersicht

Simple Notes Sync unterstützt die Verbindung zu WebDAV-Servern mit selbstsignierten SSL-Zertifikaten, z.B.:
- ownCloud/Nextcloud mit selbstsignierten Zertifikaten
- Synology NAS mit Standard-Zertifikaten
- Raspberry Pi oder Home-Server
- Interne Firmen-Server mit privaten CAs

## Anleitung

### Schritt 1: CA-Zertifikat des Servers exportieren

**Auf deinem Server:**

1. Finde deine Zertifikatsdatei (meist `.crt`, `.pem` oder `.der` Format)
2. Falls du das Zertifikat selbst erstellt hast, hast du es bereits
3. Für Synology NAS: Systemsteuerung → Sicherheit → Zertifikat → Exportieren
4. Für ownCloud/Nextcloud: Meist unter `/etc/ssl/certs/` auf dem Server

### Schritt 2: Zertifikat auf Android installieren

**Auf deinem Android-Gerät:**

1. **Übertrage** die `.crt` oder `.pem` Datei auf dein Handy (per E-Mail, USB, etc.)

2. **Öffne Einstellungen** → Sicherheit → Weitere Sicherheitseinstellungen (oder Verschlüsselung & Anmeldedaten)

3. **Von Speicher installieren** / "Zertifikat installieren"
   - Wähle "CA-Zertifikat"
   - **Warnung:** Android zeigt eine Sicherheitswarnung. Das ist normal.
   - Tippe auf "Trotzdem installieren"

4. **Navigiere** zu deiner Zertifikatsdatei und wähle sie aus

5. **Benenne** es erkennbar (z.B. "Mein ownCloud CA")

6. ✅ **Fertig!** Das Zertifikat wird nun systemweit vertraut

### Schritt 3: Simple Notes Sync verbinden

1. Öffne Simple Notes Sync
2. Gehe zu **Einstellungen** → **Server-Einstellungen**
3. Gib deine **`https://` Server-URL** wie gewohnt ein
4. Die App vertraut nun deinem selbstsignierten Zertifikat ✅

---

## Sicherheitshinweise

### ⚠️ Wichtig

- Die Installation eines CA-Zertifikats gewährt Vertrauen für **alle** von dieser CA signierten Zertifikate
- Installiere nur Zertifikate aus vertrauenswürdigen Quellen
- Android warnt dich vor der Installation – lies die Warnung sorgfältig

### 🔒 Warum das sicher ist

- Du installierst das Zertifikat **manuell** (bewusste Entscheidung)
- Die App nutzt Androids nativen Trust Store (keine eigene Validierung)
- Du kannst das Zertifikat jederzeit in den Android-Einstellungen entfernen
- F-Droid und Google Play konform (kein "allen vertrauen" Hack)

---

## Fehlerbehebung

### Zertifikat nicht vertraut

**Problem:** App zeigt weiterhin SSL-Fehler nach Zertifikatsinstallation

**Lösungen:**
1. **Installation prüfen:** Einstellungen → Sicherheit → Vertrauenswürdige Anmeldedaten → Tab "Nutzer"
2. **Zertifikatstyp prüfen:** Muss ein CA-Zertifikat sein, kein Server-Zertifikat
3. **App neustarten:** Simple Notes Sync schließen und wieder öffnen
4. **URL prüfen:** Muss `https://` verwenden (nicht `http://`)

### Selbstsigniert vs. CA-signiert

| Typ | Installation nötig | Sicherheit |
|-----|-------------------|------------|
| **Selbstsigniert** | ✅ Ja | Manuelles Vertrauen |
| **Let's Encrypt** | ❌ Nein | Automatisch |
| **Private CA** | ✅ Ja (CA-Root) | Automatisch für alle CA-signierten Zertifikate |

---

## Alternative: Let's Encrypt (Empfohlen)

Wenn dein Server öffentlich erreichbar ist, erwäge **Let's Encrypt** für kostenlose, automatisch erneuerte SSL-Zertifikate:

- Keine manuelle Zertifikatsinstallation nötig
- Von allen Geräten automatisch vertraut
- Einfacher für Endbenutzer

---

## Technische Details

### Implementierung

- Nutzt Androids **Network Security Config**
- Vertraut sowohl System- als auch Benutzer-CA-Zertifikaten
- Kein eigener TrustManager oder HostnameVerifier
- F-Droid und Play Store konform

### Konfiguration

Datei: `android/app/src/main/res/xml/network_security_config.xml`

```xml
<base-config>
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" />  <!-- ← Aktiviert Self-Signed Support -->
    </trust-anchors>
</base-config>
```

---

## FAQ

**F: Muss ich das Zertifikat nach App-Updates neu installieren?**  
A: Nein, Zertifikate werden systemweit gespeichert, nicht pro App.

**F: Kann ich dasselbe Zertifikat für mehrere Apps verwenden?**  
A: Ja, einmal installiert funktioniert es für alle Apps die Benutzerzertifikaten vertrauen.

**F: Wie entferne ich ein Zertifikat?**  
A: Einstellungen → Sicherheit → Vertrauenswürdige Anmeldedaten → Tab "Nutzer" → Zertifikat antippen → Entfernen

**F: Funktioniert das auf Android 14+?**  
A: Ja, getestet auf Android 7 bis 15 (API 24-35).

---

**Hilfe nötig?** Erstelle ein Issue auf [GitHub](https://github.com/inventory69/simple-notes-sync/issues)
