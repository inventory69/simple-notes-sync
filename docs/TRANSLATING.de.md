# Übersetzung beitragen 🌍

**🌍 Sprachen:** **Deutsch** · [English](TRANSLATING.md)

> So kannst du Simple Notes Sync in deine Sprache übersetzen!

---

## 📋 Übersicht

Simple Notes Sync unterstützt aktuell:
- 🇺🇸 **Englisch** (en) - Primärsprache
- 🇩🇪 **Deutsch** (de) - Vollständig übersetzt

Wir freuen uns über neue Übersetzungen!

---

## 🚀 Schnellstart

### 1. Repository forken

1. Gehe zu [github.com/inventory69/simple-notes-sync](https://github.com/inventory69/simple-notes-sync)
2. Klicke auf **Fork** (oben rechts)
3. Clone dein Fork: `git clone https://github.com/DEIN-USERNAME/simple-notes-sync.git`

### 2. Sprachdateien erstellen

```bash
cd simple-notes-sync/android/app/src/main/res

# Ordner für deine Sprache erstellen (z.B. Französisch)
mkdir values-fr

# Strings kopieren
cp values/strings.xml values-fr/strings.xml
```

### 3. Strings übersetzen

Öffne `values-fr/strings.xml` und übersetze alle `<string>`-Einträge:

```xml
<!-- Original (Englisch) -->
<string name="app_name">Simple Notes</string>
<string name="notes_title">Notes</string>

<!-- Übersetzt (Französisch) -->
<string name="app_name">Notes Simples</string>
<string name="notes_title">Notes</string>
```

**Wichtig:**
- Übersetze nur den Text zwischen `>` und `</string>`
- Ändere NICHT die `name="..."` Attribute
- Behalte `%s`, `%d`, `%1$s` etc. als Platzhalter

### 4. locales_config.xml aktualisieren

Füge deine Sprache in `android/app/src/main/res/xml/locales_config.xml` hinzu:

```xml
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="de" />
    <locale android:name="fr" />  <!-- NEU -->
</locale-config>
```

### 5. Pull Request erstellen

1. Committe deine Änderungen
2. Pushe zu deinem Fork
3. Erstelle einen Pull Request mit Titel: `Add [Language] translation`

---

## 📁 Dateistruktur

```
android/app/src/main/res/
├── values/              # Englisch (Fallback)
│   └── strings.xml
├── values-de/           # Deutsch
│   └── strings.xml
├── values-fr/           # Französisch (neu)
│   └── strings.xml
└── xml/
    └── locales_config.xml  # Sprachregistrierung
```

---

## 📝 String-Kategorien

Die `strings.xml` enthält etwa 400+ Strings, aufgeteilt in:

| Kategorie | Beschreibung | Anzahl |
|-----------|--------------|--------|
| UI Texte | Buttons, Labels, Titel | ~100 |
| Settings | Alle 7 Einstellungs-Screens | ~150 |
| Dialoge | Bestätigungen, Fehler | ~80 |
| Sync | Synchronisations-Meldungen | ~50 |
| Sonstige | Tooltips, Accessibility | ~30 |

---

## ✅ Qualitätscheckliste

Vor dem Pull Request:

- [ ] Alle Strings übersetzt (keine englischen Reste)
- [ ] Platzhalter (`%s`, `%d`) beibehalten
- [ ] Keine XML-Syntaxfehler
- [ ] App startet ohne Crashes
- [ ] Text passt in UI-Elemente (nicht zu lang)
- [ ] `locales_config.xml` aktualisiert

---

## 🔧 Testen

```bash
cd android
./gradlew app:assembleDebug

# APK installieren und Sprache in Android-Einstellungen wechseln
```

---

## ❓ FAQ

**Muss ich alle Strings übersetzen?**
> Idealerweise ja. Fehlende Strings fallen auf Englisch zurück.

**Was passiert mit Platzhaltern?**
> `%s` = Text, `%d` = Zahl. Position beibehalten oder mit `%1$s` nummerieren.

**Wie teste ich meine Übersetzung?**
> App bauen, installieren, in Android-Einstellungen → Apps → Simple Notes → Sprache wählen.

---

## 🙏 Danke!

Jede Übersetzung hilft Simple Notes Sync mehr Menschen zu erreichen.

Bei Fragen: [GitHub Issue erstellen](https://github.com/inventory69/simple-notes-sync/issues)

[← Zurück zur Dokumentation](DOCS.md)
