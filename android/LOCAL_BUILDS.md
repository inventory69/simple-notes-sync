# Lokale Gradle Builds mit Release-Signierung

Dieses Dokument erklärt, wie du lokal signierte APKs erstellst, die mit den GitHub Release-APKs kompatibel sind.

## Problem

- **GitHub Actions** erstellt signierte Release-APKs mit dem Production-Keystore
- **Lokale Debug-Builds** verwenden einen temporären Debug-Key
- ❌ **Resultat:** Nutzer können lokale Debug-APKs NICHT über Release-APKs installieren (Signature Mismatch!)

## Lösung: Lokale Release-Builds mit Production-Key

### 1️⃣ Keystore-Konfiguration einrichten

Du hast bereits den Keystore: `/android/app/simple-notes-release.jks`

Erstelle eine `key.properties` Datei im `/android/` Ordner:

```bash
cd /home/liq/gitProjects/simple-notes-sync/android
cp key.properties.example key.properties
```

Bearbeite `key.properties` mit den echten Werten:

```properties
storeFile=simple-notes-release.jks
storePassword=<dein-keystore-password>
keyAlias=<dein-key-alias>
keyPassword=<dein-key-password>
```

**Wichtig:** Die Werte müssen **exakt** mit den GitHub Secrets übereinstimmen:
- `KEYSTORE_PASSWORD` → `storePassword`
- `KEY_ALIAS` → `keyAlias`
- `KEY_PASSWORD` → `keyPassword`

### 2️⃣ Lokal signierte Release-APKs bauen

```bash
cd android
./gradlew assembleStandardRelease
```

Die signierten APKs findest du dann hier:
```
android/app/build/outputs/apk/standard/release/
├── app-standard-universal-release.apk
├── app-standard-arm64-v8a-release.apk
└── app-standard-armeabi-v7a-release.apk
```

### 3️⃣ F-Droid Flavor bauen (optional)

```bash
./gradlew assembleFdroidRelease
```

### 4️⃣ Beide Flavors gleichzeitig bauen

```bash
./gradlew assembleStandardRelease assembleFdroidRelease
```

## Verifizierung der Signatur

Um zu prüfen, ob dein lokaler Build die gleiche Signatur wie die Release-Builds hat:

```bash
# Signatur von lokalem Build anzeigen
keytool -printcert -jarfile app/build/outputs/apk/standard/release/app-standard-universal-release.apk

# Signatur von GitHub Release-APK anzeigen (zum Vergleich)
keytool -printcert -jarfile ~/Downloads/simple-notes-sync-v1.1.0-standard-universal.apk
```

Die **SHA256** Fingerprints müssen **identisch** sein!

## Troubleshooting

### ❌ Build schlägt fehl: "Keystore not found"

**Problem:** `key.properties` oder Keystore-Datei fehlt

**Lösung:** 
1. Prüfe, ob `key.properties` existiert: `ls -la key.properties`
2. Prüfe, ob der Keystore existiert: `ls -la app/simple-notes-release.jks`

### ❌ "Signature mismatch" beim Update

**Problem:** Der lokale Build verwendet einen anderen Key als die Release-Builds

**Lösung:**
1. Vergleiche die Signaturen mit `keytool` (siehe oben)
2. Stelle sicher, dass `key.properties` die **exakten** GitHub Secret-Werte enthält
3. Deinstalliere die alte Version und installiere die neue (als letzter Ausweg)

### ❌ Build verwendet Debug-Signatur

**Problem:** `build.gradle.kts` findet `key.properties` nicht

**Lösung:**
```bash
# Prüfe, ob die Datei im richtigen Verzeichnis liegt
ls -la android/key.properties  # ✅ Richtig
ls -la android/app/key.properties  # ❌ Falsch
```

## Sicherheitshinweise

⚠️ **NIEMALS** diese Dateien committen:
- `key.properties` (in `.gitignore`)
- `*.jks` / `*.keystore` (in `.gitignore`)

✅ **Schon in `.gitignore`:**
```gitignore
key.properties
*.jks
*.keystore
```

⚠️ Die GitHub Secrets (`KEYSTORE_PASSWORD`, etc.) und die lokale `key.properties` müssen **synchron** bleiben!

## Workflow-Vergleich

### GitHub Actions Build
```yaml
- Lädt Keystore aus Base64 Secret
- Erstellt key.properties aus Secrets
- Baut mit: ./gradlew assembleStandardRelease
- ✅ Produktions-signiert
```

### Lokaler Build
```bash
# Mit key.properties konfiguriert:
./gradlew assembleStandardRelease
# ✅ Produktions-signiert (gleiche Signatur wie GitHub!)

# Ohne key.properties:
./gradlew assembleStandardRelease
# ⚠️ Debug-signiert (inkompatibel mit Releases!)
```

## Quick Reference

```bash
# Release-APK bauen (signiert, klein, optimiert)
./gradlew assembleStandardRelease

# Debug-APK bauen (unsigniert, groß, debuggable)
./gradlew assembleStandardDebug

# APK per HTTP Server verteilen
cd app/build/outputs/apk/standard/release
python3 -m http.server 8892
```
