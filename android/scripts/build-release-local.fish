#!/usr/bin/env fish

# Simple Notes Sync - Lokaler Release Build
# Erstellt signierte APKs, die mit GitHub Release-APKs kompatibel sind

set -l SCRIPT_DIR (dirname (status --current-filename))
set -l ANDROID_DIR (realpath "$SCRIPT_DIR/..")
set -l KEY_PROPERTIES "$ANDROID_DIR/key.properties"

echo "🔨 Simple Notes Sync - Lokaler Release Build"
echo ""

# 1. Prüfe ob key.properties existiert
if not test -f "$KEY_PROPERTIES"
    echo "❌ Fehler: key.properties nicht gefunden!"
    echo ""
    echo "Bitte erstelle die Datei:"
    echo "  cd $ANDROID_DIR"
    echo "  cp key.properties.example key.properties"
    echo ""
    echo "Und fülle sie mit den echten Keystore-Daten aus."
    echo "Siehe: android/LOCAL_BUILDS.md"
    exit 1
end

# 2. Prüfe ob Keystore existiert
set -l KEYSTORE "$ANDROID_DIR/app/simple-notes-release.jks"
if not test -f "$KEYSTORE"
    echo "❌ Fehler: Keystore nicht gefunden!"
    echo "  Erwartet: $KEYSTORE"
    exit 1
end

echo "✅ key.properties gefunden"
echo "✅ Keystore gefunden"
echo ""

# 3. Build-Typ abfragen
echo "Welchen Build möchtest du erstellen?"
echo "  1) Standard Flavor (empfohlen)"
echo "  2) F-Droid Flavor"
echo "  3) Beide Flavors"
echo ""
read -P "Auswahl [1-3]: " -n 1 choice

echo ""
echo ""

switch $choice
    case 1
        echo "🏗️  Baue Standard Release APKs..."
        cd "$ANDROID_DIR"
        ./gradlew assembleStandardRelease --no-daemon
        
        if test $status -eq 0
            echo ""
            echo "✅ Build erfolgreich!"
            echo ""
            echo "📦 APKs findest du hier:"
            echo "  $ANDROID_DIR/app/build/outputs/apk/standard/release/"
            ls -lh "$ANDROID_DIR/app/build/outputs/apk/standard/release/"*.apk
        end
        
    case 2
        echo "🏗️  Baue F-Droid Release APKs..."
        cd "$ANDROID_DIR"
        ./gradlew assembleFdroidRelease --no-daemon
        
        if test $status -eq 0
            echo ""
            echo "✅ Build erfolgreich!"
            echo ""
            echo "📦 APKs findest du hier:"
            echo "  $ANDROID_DIR/app/build/outputs/apk/fdroid/release/"
            ls -lh "$ANDROID_DIR/app/build/outputs/apk/fdroid/release/"*.apk
        end
        
    case 3
        echo "🏗️  Baue Standard + F-Droid Release APKs..."
        cd "$ANDROID_DIR"
        ./gradlew assembleStandardRelease assembleFdroidRelease --no-daemon
        
        if test $status -eq 0
            echo ""
            echo "✅ Build erfolgreich!"
            echo ""
            echo "📦 Standard APKs:"
            echo "  $ANDROID_DIR/app/build/outputs/apk/standard/release/"
            ls -lh "$ANDROID_DIR/app/build/outputs/apk/standard/release/"*.apk
            echo ""
            echo "📦 F-Droid APKs:"
            echo "  $ANDROID_DIR/app/build/outputs/apk/fdroid/release/"
            ls -lh "$ANDROID_DIR/app/build/outputs/apk/fdroid/release/"*.apk
        end
        
    case '*'
        echo "❌ Ungültige Auswahl"
        exit 1
end

echo ""
echo "💡 Tipp: Du kannst die APK per HTTP Server verteilen:"
echo "   cd app/build/outputs/apk/standard/release"
echo "   python3 -m http.server 8892"
