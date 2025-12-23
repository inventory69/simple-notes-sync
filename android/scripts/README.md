# Android Build Scripts

Nützliche Scripts für die lokale Entwicklung und Release-Erstellung.

## 📜 Verfügbare Scripts

### 1. `create-keystore.fish` - Neuen Release-Keystore erstellen

**Wann verwenden:**
- ✅ Erstmaliges Setup des Projekts
- ✅ Keystore-Passwort vergessen
- ✅ Keystore beschädigt oder verloren
- ❌ **NICHT** verwenden, wenn bereits User existieren (macht alte APKs inkompatibel!)

**Verwendung:**
```bash
cd /home/liq/gitProjects/simple-notes-sync/android
./scripts/create-keystore.fish
```

**Das Script:**
1. Erstellt einen neuen 4096-Bit RSA-Keystore
2. Generiert `app/simple-notes-release.jks`
3. Erstellt `key.properties` mit den Zugangsdaten
4. Zeigt Base64-kodierten Keystore für GitHub Secrets
5. Gibt SHA256-Fingerprint zur Verifikation aus

**Output:**
- ✅ `app/simple-notes-release.jks` - Der Keystore
- ✅ `key.properties` - Lokale Signing-Konfiguration
- 📋 GitHub Secrets zum Kopieren

---

### 2. `verify-secrets.fish` - GitHub Secrets & Keystore verifizieren

**Wann verwenden:**
- ✅ Nach `create-keystore.fish` zur Verifikation
- ✅ Vor einem Release-Build zum Troubleshooting
- ✅ Um zu prüfen ob alles korrekt konfiguriert ist

**Verwendung:**
```bash
cd /home/liq/gitProjects/simple-notes-sync/android
./scripts/verify-secrets.fish
```

**Das Script prüft:**
- GitHub CLI Installation & Authentifizierung
- Ob alle 4 erforderlichen GitHub Secrets gesetzt sind
- Ob `key.properties` lokal existiert
- Ob der Keystore existiert
- Zeigt SHA256-Fingerprint des Keystores

**Output:**
- ✅ Status aller Secrets
- ✅ Status der lokalen Konfiguration
- 💡 Empfehlungen bei Problemen

---

### 3. `build-release-local.fish` - Lokal signierte Release-APKs bauen

**Wann verwenden:**
- ✅ Lokale Test-APKs erstellen, die mit Releases kompatibel sind
- ✅ APKs vor dem GitHub Release testen
- ✅ Schneller als GitHub Actions für Tests

**Voraussetzung:**
- `key.properties` muss existieren (via `create-keystore.fish` erstellt)

**Verwendung:**
```bash
cd /home/liq/gitProjects/simple-notes-sync/android
./scripts/build-release-local.fish
```

**Interaktive Auswahl:**
1. Standard Flavor (empfohlen)
2. F-Droid Flavor
3. Beide Flavors

**Output:**
- `app/build/outputs/apk/standard/release/` - Signierte Standard APKs
- `app/build/outputs/apk/fdroid/release/` - Signierte F-Droid APKs

---

## 🚀 Kompletter Workflow (von 0 auf Release)

### Erstmaliges Setup

```bash
cd /home/liq/gitProjects/simple-notes-sync/android

# 1. Keystore erstellen (mit automatischer GitHub Secrets-Konfiguration!)
./scripts/create-keystore.fish
# → Folge den Anweisungen, speichere die Passwörter!
# → GitHub Secrets werden automatisch via GitHub CLI gesetzt

# 2. Verifiziere die Konfiguration
./scripts/verify-secrets.fish
# → Prüft ob alle Secrets gesetzt sind
# → Zeigt Keystore-Informationen

# 3. Teste lokalen Build
./scripts/build-release-local.fish
# → Wähle "1" für Standard Flavor

# 4. Verifiziere Signatur
keytool -printcert -jarfile app/build/outputs/apk/standard/release/app-standard-universal-release.apk
```

### Vor jedem Release

```bash
# 1. Code committen und pushen
git add .
git commit -m "✨ Neue Features"
git push origin main

# 2. GitHub Actions erstellt automatisch Release
# → Workflow läuft: .github/workflows/build-production-apk.yml
# → Erstellt Release mit signierten APKs

# Optional: Lokalen Test-Build vorher
./scripts/build-release-local.fish
```

---

## 🔐 Sicherheitshinweise

### ⚠️ Diese Dateien NIEMALS committen:
- `key.properties` - Enthält Keystore-Passwörter
- `*.jks` / `*.keystore` - Der Keystore selbst
- Beide sind bereits in `.gitignore`

### ✅ Diese Werte sicher speichern:
- Keystore-Passwort
- Key-Alias
- Key-Passwort
- Base64-kodierter Keystore (für GitHub Secrets)

**Empfehlung:** Nutze einen Passwort-Manager (Bitwarden, 1Password, etc.)

---

## 🛠️ Troubleshooting

### "Keystore not found"
```bash
# Prüfe ob Keystore existiert
ls -la app/simple-notes-release.jks

# Falls nicht: Neu erstellen
./scripts/create-keystore.fish
```

### "key.properties not found"
```bash
# Prüfe ob Datei existiert
ls -la key.properties

# Falls nicht: Keystore neu erstellen oder manuell anlegen
./scripts/create-keystore.fish
```

### "Signature mismatch" beim App-Update
**Problem:** Lokaler Build hat andere Signatur als GitHub Release

**Ursache:** Unterschiedliche Keystores oder Passwörter

**Lösung:**
1. Vergleiche SHA256-Fingerprints:
   ```bash
   # Lokal
   keytool -list -v -keystore app/simple-notes-release.jks
   
   # GitHub Release-APK
   keytool -printcert -jarfile ~/Downloads/simple-notes-v1.1.0.apk
   ```
2. Müssen **identisch** sein!
3. Falls nicht: GitHub Secrets mit lokaler `key.properties` synchronisieren

---

## 📚 Weitere Dokumentation

- `../LOCAL_BUILDS.md` - Detaillierte Anleitung für lokale Builds
- `../.github/workflows/build-production-apk.yml` - GitHub Actions Workflow
- `../app/build.gradle.kts` - Build-Konfiguration
