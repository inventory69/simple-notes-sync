#!/usr/bin/env fish

# Simple Notes Sync - GitHub Secrets Verifier
# Verifiziert ob die GitHub Secrets korrekt konfiguriert sind

set -l repo "inventory69/simple-notes-sync"

echo "🔍 GitHub Secrets Verifier"
echo ""

# Prüfe ob GitHub CLI installiert ist
if not command -v gh &> /dev/null
    echo "❌ GitHub CLI (gh) nicht gefunden!"
    echo ""
    echo "Installation:"
    echo "  Arch Linux:  sudo pacman -S github-cli"
    echo "  Ubuntu:      sudo apt install gh"
    echo "  macOS:       brew install gh"
    echo ""
    exit 1
end

# Prüfe Authentifizierung
if not gh auth status &> /dev/null
    echo "❌ Nicht bei GitHub authentifiziert!"
    echo ""
    echo "Authentifizierung starten:"
    echo "  gh auth login"
    echo ""
    exit 1
end

echo "✅ GitHub CLI authentifiziert"
echo ""

# Liste alle Secrets auf
echo "📋 Konfigurierte Secrets für $repo:"
echo ""
gh secret list --repo $repo

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Prüfe ob alle erforderlichen Secrets vorhanden sind
set -l required_secrets "KEYSTORE_BASE64" "KEYSTORE_PASSWORD" "KEY_ALIAS" "KEY_PASSWORD"
set -l missing_secrets

for secret in $required_secrets
    if not gh secret list --repo $repo | grep -q "^$secret"
        set -a missing_secrets $secret
    end
end

if test (count $missing_secrets) -gt 0
    echo "❌ Fehlende Secrets:"
    for secret in $missing_secrets
        echo "   - $secret"
    end
    echo ""
    echo "💡 Tipp: Führe create-keystore.fish aus, um die Secrets zu erstellen"
else
    echo "✅ Alle erforderlichen Secrets sind konfiguriert!"
    echo ""
    echo "Required Secrets:"
    for secret in $required_secrets
        echo "   ✓ $secret"
    end
end

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Prüfe ob key.properties lokal existiert
set -l SCRIPT_DIR (dirname (status --current-filename))
set -l ANDROID_DIR (realpath "$SCRIPT_DIR/..")
set -l KEY_PROPERTIES "$ANDROID_DIR/key.properties"

if test -f "$KEY_PROPERTIES"
    echo "✅ Lokale key.properties gefunden: $KEY_PROPERTIES"
    echo ""
    echo "📋 Inhalt (Passwörter verborgen):"
    cat "$KEY_PROPERTIES" | sed 's/\(Password=\).*/\1***HIDDEN***/g'
else
    echo "⚠️  Lokale key.properties nicht gefunden: $KEY_PROPERTIES"
    echo ""
    echo "💡 Tipp: Führe create-keystore.fish aus, um sie zu erstellen"
end

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Prüfe ob Keystore existiert
set -l KEYSTORE "$ANDROID_DIR/app/simple-notes-release.jks"
if test -f "$KEYSTORE"
    echo "✅ Keystore gefunden: $KEYSTORE"
    
    # Zeige Keystore-Info (wenn key.properties existiert)
    if test -f "$KEY_PROPERTIES"
        set -l store_password (grep "storePassword=" "$KEY_PROPERTIES" | cut -d'=' -f2)
        
        echo ""
        echo "🔑 Keystore-Informationen:"
        keytool -list -v -keystore "$KEYSTORE" -storepass "$store_password" 2>/dev/null | grep -E "(Alias|Creation date|Valid|SHA256)" | head -10
    end
else
    echo "⚠️  Keystore nicht gefunden: $KEYSTORE"
    echo ""
    echo "💡 Tipp: Führe create-keystore.fish aus, um ihn zu erstellen"
end

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Zusammenfassung
set -l issues 0

if test (count $missing_secrets) -gt 0
    set issues (math $issues + 1)
end

if not test -f "$KEY_PROPERTIES"
    set issues (math $issues + 1)
end

if not test -f "$KEYSTORE"
    set issues (math $issues + 1)
end

if test $issues -eq 0
    echo "🎉 Alles konfiguriert! Du kannst jetzt Releases erstellen."
    echo ""
    echo "🚀 Nächste Schritte:"
    echo "   1. Lokalen Build testen:"
    echo "      ./scripts/build-release-local.fish"
    echo ""
    echo "   2. Code committen und pushen:"
    echo "      git push origin main"
    echo ""
    echo "   3. GitHub Actions erstellt automatisch Release"
else
    echo "⚠️  $issues Problem(e) gefunden - siehe oben"
    echo ""
    echo "💡 Lösung: Führe create-keystore.fish aus"
end

echo ""
