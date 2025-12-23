#!/usr/bin/env fish

# Simple Notes Sync - Keystore Generator
# Erstellt einen neuen Release-Keystore für App-Signierung

set -l SCRIPT_DIR (dirname (status --current-filename))
set -l ANDROID_DIR (realpath "$SCRIPT_DIR/..")
set -l KEYSTORE_PATH "$ANDROID_DIR/app/simple-notes-release.jks"
set -l KEY_PROPERTIES "$ANDROID_DIR/key.properties"

echo "🔐 Simple Notes Sync - Keystore Generator"
echo ""
echo "⚠️  WICHTIG: Dieser Keystore wird für alle zukünftigen App-Releases verwendet!"
echo "    Speichere die Zugangsdaten sicher ab (z.B. in einem Passwort-Manager)."
echo ""

# Prüfe ob Keystore bereits existiert
if test -f "$KEYSTORE_PATH"
    echo "⚠️  Ein Keystore existiert bereits:"
    echo "    $KEYSTORE_PATH"
    echo ""
    read -P "Möchtest du ihn überschreiben? (Dies macht alte APKs inkompatibel!) [j/N]: " -n 1 overwrite
    echo ""
    
    if not string match -qi "j" $overwrite
        echo "❌ Abgebrochen."
        exit 1
    end
    
    echo "🗑️  Lösche alten Keystore..."
    rm "$KEYSTORE_PATH"
end

echo ""
echo "📝 Bitte gib die folgenden Informationen ein:"
echo ""

# App-Informationen sammeln
read -P "Dein Name (z.B. 'Max Mustermann'): " developer_name
read -P "Organisation (z.B. 'dettmer.dev'): " organization
read -P "Stadt: " city
read -P "Land (z.B. 'DE'): " country

echo ""
echo "🔒 Keystore-Passwörter:"
echo ""
echo "Möchtest du sichere Passwörter automatisch generieren lassen?"
read -P "[J/n]: " -n 1 auto_generate
echo ""

if string match -qi "n" $auto_generate
    # Manuelle Passwort-Eingabe
    echo ""
    echo "📝 Manuelle Passwort-Eingabe:"
    echo ""
    
    while true
        read -sP "Keystore-Passwort: " keystore_password
        echo ""
        read -sP "Keystore-Passwort (Bestätigung): " keystore_password_confirm
        echo ""
        
        if test "$keystore_password" = "$keystore_password_confirm"
            break
        else
            echo "❌ Passwörter stimmen nicht überein. Bitte erneut eingeben."
            echo ""
        end
    end
    
    while true
        read -sP "Key-Passwort: " key_password
        echo ""
        read -sP "Key-Passwort (Bestätigung): " key_password_confirm
        echo ""
        
        if test "$key_password" = "$key_password_confirm"
            break
        else
            echo "❌ Passwörter stimmen nicht überein. Bitte erneut eingeben."
            echo ""
        end
    end
else
    # Automatische Passwort-Generierung
    echo ""
    echo "🔐 Generiere sichere Passwörter (32 Zeichen, alphanumerisch)..."
    
    # Generiere sichere, zufällige Passwörter (alphanumerisch, 32 Zeichen)
    set keystore_password (openssl rand -base64 32 | tr -d '/+=' | head -c 32)
    set key_password (openssl rand -base64 32 | tr -d '/+=' | head -c 32)
    
    echo "✅ Passwörter generiert"
    echo ""
    echo "⚠️  WICHTIG: Speichere diese Passwörter jetzt in deinem Passwort-Manager!"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Keystore-Passwort: $keystore_password"
    echo "Key-Passwort:      $key_password"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    read -P "Passwörter gespeichert? Drücke Enter zum Fortfahren..."
end

set -l key_alias "simple-notes-key"

echo ""
echo "🏗️  Erstelle Keystore..."
echo ""

# Keystore erstellen
keytool -genkey \
    -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$key_alias" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$keystore_password" \
    -keypass "$key_password" \
    -dname "CN=$developer_name, OU=Simple Notes Sync, O=$organization, L=$city, C=$country"

if test $status -ne 0
    echo ""
    echo "❌ Fehler beim Erstellen des Keystores!"
    exit 1
end

echo ""
echo "✅ Keystore erfolgreich erstellt!"
echo ""

# key.properties erstellen
echo "📝 Erstelle key.properties..."
echo "storeFile=simple-notes-release.jks" > "$KEY_PROPERTIES"
echo "storePassword=$keystore_password" >> "$KEY_PROPERTIES"
echo "keyAlias=$key_alias" >> "$KEY_PROPERTIES"
echo "keyPassword=$key_password" >> "$KEY_PROPERTIES"

echo "✅ key.properties erstellt"
echo ""

# Keystore-Info anzeigen
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📋 KEYSTORE-INFORMATIONEN - SICHER SPEICHERN!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Keystore-Pfad:      $KEYSTORE_PATH"
echo "Key-Alias:          $key_alias"
echo "Keystore-Passwort:  $keystore_password"
echo "Key-Passwort:       $key_password"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Base64-kodierten Keystore für GitHub Secrets
echo "🔐 Base64-kodierter Keystore für GitHub Secrets:"
echo ""
set -l keystore_base64 (base64 -w 0 "$KEYSTORE_PATH")
echo "$keystore_base64"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# GitHub Secrets konfigurieren
echo "� GitHub Secrets konfigurieren..."
echo ""

# Prüfe ob GitHub CLI installiert ist
if not command -v gh &> /dev/null
    echo "⚠️  GitHub CLI (gh) nicht gefunden!"
    echo ""
    echo "📝 Manuelle Konfiguration erforderlich:"
    echo ""
    echo "1. Gehe zu: https://github.com/inentory69/simple-notes-sync/settings/secrets/actions"
    echo "2. Erstelle/Aktualisiere folgende Secrets:"
    echo ""
    echo "   KEYSTORE_BASE64:"
    echo "   $keystore_base64"
    echo ""
    echo "   KEYSTORE_PASSWORD:"
    echo "   $keystore_password"
    echo ""
    echo "   KEY_ALIAS:"
    echo "   $key_alias"
    echo ""
    echo "   KEY_PASSWORD:"
    echo "   $key_password"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
else
    # GitHub CLI verfügbar - automatisch Secrets erstellen
    echo "✅ GitHub CLI gefunden"
    echo ""
    
    # Prüfe ob authentifiziert
    if not gh auth status &> /dev/null
        echo "⚠️  Nicht bei GitHub authentifiziert!"
        echo ""
        read -P "Möchtest du dich jetzt authentifizieren? [j/N]: " -n 1 do_auth
        echo ""
        
        if string match -qi "j" $do_auth
            gh auth login
        else
            echo "❌ Überspringe automatische Secret-Konfiguration"
            echo ""
            echo "📝 Manuelle Konfiguration erforderlich (siehe oben)"
            echo ""
            echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            echo ""
            return
        end
    end
    
    echo "🔐 Erstelle/Aktualisiere GitHub Secrets..."
    echo ""
    
    set -l repo "inventory69/simple-notes-sync"
    
    # KEYSTORE_BASE64
    echo "$keystore_base64" | gh secret set KEYSTORE_BASE64 --repo $repo
    if test $status -eq 0
        echo "✅ KEYSTORE_BASE64 gesetzt"
    else
        echo "❌ Fehler beim Setzen von KEYSTORE_BASE64"
    end
    
    # KEYSTORE_PASSWORD
    echo "$keystore_password" | gh secret set KEYSTORE_PASSWORD --repo $repo
    if test $status -eq 0
        echo "✅ KEYSTORE_PASSWORD gesetzt"
    else
        echo "❌ Fehler beim Setzen von KEYSTORE_PASSWORD"
    end
    
    # KEY_ALIAS
    echo "$key_alias" | gh secret set KEY_ALIAS --repo $repo
    if test $status -eq 0
        echo "✅ KEY_ALIAS gesetzt"
    else
        echo "❌ Fehler beim Setzen von KEY_ALIAS"
    end
    
    # KEY_PASSWORD
    echo "$key_password" | gh secret set KEY_PASSWORD --repo $repo
    if test $status -eq 0
        echo "✅ KEY_PASSWORD gesetzt"
    else
        echo "❌ Fehler beim Setzen von KEY_PASSWORD"
    end
    
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "✅ GitHub Secrets erfolgreich konfiguriert!"
    echo ""
    echo "🔍 Verifizieren:"
    echo "   gh secret list --repo $repo"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
end

# Signatur-Fingerprint anzeigen
echo "🔑 SHA256-Fingerprint (zur Verifikation):"
keytool -list -v -keystore "$KEYSTORE_PATH" -storepass "$keystore_password" | grep "SHA256:"
echo ""

echo "✅ Setup abgeschlossen!"
echo ""
echo "💡 Nächste Schritte:"
echo "   1. Speichere die obigen Informationen in einem Passwort-Manager"
echo "   2. Konfiguriere die GitHub Secrets (siehe oben)"
echo "   3. Teste den lokalen Build:"
echo "      cd $ANDROID_DIR"
echo "      ./gradlew assembleStandardRelease"
echo ""
