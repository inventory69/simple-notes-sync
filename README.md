# Simple Notes Sync 📝

> **Minimalistische Android Notiz-App mit automatischer WLAN-Synchronisierung**

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue.svg)](https://kotlinlang.org/)
[![Material Design 3](https://img.shields.io/badge/Material-Design%203-green.svg)](https://m3.material.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Schlanke Offline-Notizen ohne Schnickschnack - deine Daten bleiben bei dir. Automatische Synchronisierung zu deinem eigenen WebDAV-Server, kein Google, kein Microsoft, keine Cloud.

## ✨ Features

- 📝 **Offline-First** - Notizen lokal gespeichert, immer verfügbar
- 🔄 **Auto-Sync** - Konfigurierbare Intervalle (15/30/60 Min.) mit ~0.2-0.8% Akku/Tag
- 🏠 **Self-Hosted** - Deine Daten auf deinem Server (WebDAV)
- 🎨 **Material Design 3** - Modern & Dynamic Theming
- 🔋 **Akkuschonend** - Optimiert für Hintergrund-Synchronisierung
- 🔐 **Privacy-First** - Kein Tracking, keine Analytics, keine Cloud
- 🚫 **Keine Berechtigungen** - Nur Internet für WebDAV Sync

## 📥 Quick Download

**Android APK:** [📱 Neueste Version herunterladen](https://github.com/inventory69/simple-notes-sync/releases/latest)

💡 **Tipp:** Nutze [Obtainium](https://github.com/ImranR98/Obtainium) für automatische Updates!

---

## 🚀 Schnellstart

### 1️⃣ WebDAV Server starten

```fish
cd server
cp .env.example .env
# Passwort in .env anpassen
docker compose up -d
```

### 2️⃣ App installieren & konfigurieren

1. APK herunterladen und installieren
2. App öffnen → **Einstellungen** (⚙️)
3. Server konfigurieren:
   - URL: `http://192.168.0.XXX:8080/notes`
   - Benutzername: `noteuser`
   - Passwort: (aus `.env`)
4. **Auto-Sync aktivieren**
5. **Sync-Intervall wählen** (15/30/60 Min.)

**Fertig!** Notizen werden automatisch synchronisiert 🎉

---

## ⚙️ Sync-Intervalle

| Intervall | Akku/Tag | Anwendungsfall |
|-----------|----------|----------------|
| **15 Min** | ~0.8% (~23 mAh) | ⚡ Maximale Aktualität |
| **30 Min** | ~0.4% (~12 mAh) | ✓ Empfohlen - Ausgewogen |
| **60 Min** | ~0.2% (~6 mAh) | 🔋 Maximale Akkulaufzeit |

💡 **Hinweis:** Android Doze Mode kann Sync im Standby auf ~60 Min. verzögern (betrifft alle Apps).

---

## � Neue Features in v1.1.0

### Konfigurierbare Sync-Intervalle
- ⏱️ Wählbare Intervalle: 15/30/60 Minuten
- 📊 Transparente Akkuverbrauchs-Anzeige
- � Sofortige Anwendung ohne App-Neustart

### Über-Sektion
- � App-Version & Build-Datum
- 🌐 Links zu GitHub Repo & Entwickler
- ⚖️ Lizenz-Information

### Verbesserungen
- 🎯 Benutzerfreundliche Doze-Mode Erklärung
- 🔕 Keine störenden Sync-Fehler Toasts im Hintergrund
- 📝 Erweiterte Debug-Logs für Troubleshooting

---

## 🛠️ Selbst bauen

```fish
cd android
./gradlew assembleStandardRelease
# APK: android/app/build/outputs/apk/standard/release/
```

---

## 🐛 Troubleshooting

### Auto-Sync funktioniert nicht

1. **Akku-Optimierung deaktivieren**
   - Einstellungen → Apps → Simple Notes → Akku → Nicht optimieren
2. **WLAN-Verbindung prüfen**
   - Funktioniert nur im selben Netzwerk wie Server
3. **Server-Status checken**
   - Settings → "Verbindung testen"

### Server nicht erreichbar

```fish
# Status prüfen
docker compose ps

# Logs ansehen
docker compose logs -f

# IP-Adresse finden
ip addr show | grep "inet " | grep -v 127.0.0.1
```

Mehr Details: [📖 Dokumentation](DOCS.md)

---

## 🤝 Contributing

Contributions sind willkommen! Bitte öffne ein Issue oder Pull Request.

---

## 📄 Lizenz

MIT License - siehe [LICENSE](LICENSE)

---

**Version:** 1.1.0 · **Status:** ✅ Produktiv · **Gebaut mit:** Kotlin + Material Design 3
