# Debug APK für Issue-Testing

Für Bug-Reports und Testing von Fixes brauchst du eine **Debug-APK**. Diese wird automatisch gebaut, wenn du auf speziellen Branches pushst.

## 🔧 Branch-Struktur für Debug-APKs

Debug-APKs werden **automatisch** gebaut für diese Branches:

| Branch-Typ | Zweck | Beispiel |
|-----------|-------|---------|
| `debug/*` | Allgemeines Testing | `debug/wifi-only-sync` |
| `fix/*` | Bug-Fixes testen | `fix/vpn-connection` |
| `feature/*` | Neue Features | `feature/grid-layout` |

**Andere Branches (main, develop, etc.) bauen KEINE Debug-APKs!**

## 📥 Debug-APK downloaden

### 1️⃣ Push zu einem Debug-Branch

```bash
# Neuen Fix-Branch erstellen
git checkout -b fix/my-bug

# Deine Änderungen machen
# ...

# Commit und Push
git add .
git commit -m "fix: beschreibung"
git push origin fix/my-bug
```

### 2️⃣ GitHub Actions Workflow starten

- GitHub → **Actions** Tab
- **Build Debug APK** Workflow sehen
- Warten bis Workflow grün ist ✅

### 3️⃣ APK herunterladen

1. Auf den grünen Workflow-Erfolg warten
2. **Artifacts** Section oben (oder unten im Workflow)
3. `simple-notes-sync-debug-*` herunterladen
4. ZIP-Datei entpacken

**Wichtig:** Artifacts sind nur **30 Tage** verfügbar!

## 📱 Installation auf Gerät

## 📱 Installation auf Gerät

### Mit ADB (Empfohlen - sauberes Testing)
```bash
# Gerät verbinden
adb devices

# Debug-APK installieren (alte Version wird nicht gelöscht)
adb install simple-notes-sync-debug.apk

# Aus dem Gerät entfernen später:
adb uninstall dev.dettmer.simplenotes
```

### Manuell auf Gerät
1. Datei auf Android-Gerät kopieren
2. **Einstellungen → Sicherheit → "Unbekannte Quellen" aktivieren**
3. Dateimanager öffnen und APK antippen
4. "Installieren" auswählen

## ⚠️ Debug-APK vs. Release-APK

| Feature | Debug | Release |
|---------|-------|---------|
| **Logging** | Voll | Minimal |
| **Signatur** | Debug-Key | Release-Key |
| **Performance** | Langsamer | Schneller |
| **Debugging** | ✅ Möglich | ❌ Nein |
| **Installation** | Mehrmals | Kann Probleme geben |

## 📊 Was zu testen ist

1. **Neue Features** - Funktionieren wie beschrieben?
2. **Bug Fixes** - Ist der Bug wirklich behoben?
3. **Kompatibilität** - Funktioniert auf deinem Gerät?
4. **Performance** - Läuft die App flüssig?

## 📝 Feedback geben

Bitte schreibe einen Kommentar im **Pull Request** oder **GitHub Issue**:
- ✅ Was funktioniert
- ❌ Was nicht funktioniert
- 📋 Fehler-Logs (adb logcat falls relevant)
- 📱 Gerät/Android-Version

## 🐛 Logs sammeln

Falls der App-Entwickler Debug-Logs braucht:

```bash
# Terminal öffnen mit adb
adb shell pm grant dev.dettmer.simplenotes android.permission.READ_LOGS

# Logs anschauen (live)
adb logcat | grep simplenotes

# Logs speichern (Datei)
adb logcat > debug-log.txt

# Nach Fehler filtern
adb logcat | grep -E "ERROR|Exception|CRASH"
```

---

**Danke fürs Testing! Dein Feedback hilft uns, die App zu verbessern.** 🙏
