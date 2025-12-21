# Simple Notes Sync 📝

> Minimalistische Android-App für Offline-Notizen mit automatischer WLAN-Synchronisierung

Eine schlanke Notiz-App ohne Schnickschnack - perfekt für schnelle Gedanken, die automatisch zu Hause synchronisiert werden.

---

## ✨ Features

- 📝 **Offline-first** - Notizen werden lokal gespeichert und sind immer verfügbar
- 🔄 **Auto-Sync** - Automatische Synchronisierung wenn du im Heimnetzwerk bist
- 🏠 **WebDAV Server** - Deine Daten bleiben bei dir (Docker-Container)
- 🔋 **Akkuschonend** - Nur ~0.4% Akkuverbrauch pro Tag
- 🚫 **Keine Cloud** - Keine Google, keine Microsoft, keine Drittanbieter
- 🔐 **Privacy** - Keine Tracking, keine Analytics, keine Standort-Berechtigungen

---

## 📥 Installation

### Android App

**Option 1: APK herunterladen**

1. Neueste [Release](../../releases/latest) öffnen
2. `app-debug.apk` herunterladen
3. APK auf dem Handy installieren

**Option 2: Selbst bauen**

```bash
cd android
./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

### WebDAV Server

Der Server läuft als Docker-Container und speichert deine Notizen.

```bash
cd server
cp .env.example .env
nano .env  # Passwort anpassen!
docker-compose up -d
```

**Server testen:**
```bash
curl -u noteuser:dein_passwort http://192.168.0.XXX:8080/
```

---

## 🚀 Schnellstart

1. **Server starten** (siehe oben)
2. **App installieren** und öffnen
3. **Einstellungen öffnen** (⚙️ Symbol oben rechts)
4. **Server konfigurieren:**
   - Server-URL: `http://192.168.0.XXX:8080/notes`
   - Benutzername: `noteuser`
   - Passwort: (aus `.env` Datei)
   - Auto-Sync: **AN**
5. **Fertig!** Notizen werden jetzt automatisch synchronisiert

---

## 💡 Wie funktioniert Auto-Sync?

Die App prüft **alle 30 Minuten**, ob:
- ✅ WLAN verbunden ist
- ✅ Server im gleichen Netzwerk erreichbar ist
- ✅ Neue Notizen vorhanden sind

Wenn alle Bedingungen erfüllt → **Automatische Synchronisierung**

**Wichtig:** Funktioniert nur im selben Netzwerk wie der Server (kein Internet-Zugriff nötig!)

---

## 🔋 Akkuverbrauch

| Komponente | Verbrauch/Tag |
|------------|---------------|
| WorkManager (alle 30 Min) | ~0.3% |
| Netzwerk-Checks | ~0.1% |
| **Total** | **~0.4%** |

Bei einem 3000 mAh Akku entspricht das ~12 mAh pro Tag.

---

## 📱 Screenshots

_TODO: Screenshots hinzufügen_

---

## 🛠️ Technische Details

Mehr Infos zur Architektur und Implementierung findest du in der [technischen Dokumentation](DOCS.md).

**Stack:**
- **Android:** Kotlin, Material Design 3, WorkManager
- **Server:** Docker, WebDAV (bytemark/webdav)
- **Sync:** Sardine Android (WebDAV Client)

---

## 🐛 Troubleshooting

### Server nicht erreichbar

```bash
# Server Status prüfen
docker-compose ps

# Logs ansehen
docker-compose logs -f

# IP-Adresse finden
ip addr show | grep "inet " | grep -v 127.0.0.1
```

### Auto-Sync funktioniert nicht

1. **Akku-Optimierung deaktivieren**
   - Einstellungen → Apps → Simple Notes → Akku → Nicht optimieren
2. **WLAN Verbindung prüfen**
   - App funktioniert nur im selben Netzwerk wie der Server
3. **Server-Status in App prüfen**
   - Settings → Server-Status sollte "Erreichbar" zeigen

Mehr Details in der [Dokumentation](DOCS.md).

---

## 🤝 Beitragen

Contributions sind willkommen! Bitte öffne ein Issue oder Pull Request.

---

## 📄 Lizenz

MIT License - siehe [LICENSE](LICENSE)

---

**Projekt Start:** 19. Dezember 2025  
**Status:** ✅ Funktional & Produktiv
