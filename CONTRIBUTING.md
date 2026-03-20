# Contributing to Simple Notes Sync 🤝

> Beiträge sind willkommen! / Contributions are welcome!

**🌍 Languages:** [Deutsch](#deutsch) · [English](#english)

---

## Deutsch

Danke, dass du zu Simple Notes Sync beitragen möchtest! 

### 🚀 Schnellstart

1. **Fork & Clone**
   ```bash
   git clone https://github.com/inventory69/simple-notes-sync.git
   cd simple-notes-sync
   ```

2. **Branch erstellen**
   ```bash
   git checkout -b feature/mein-feature
   # oder
   git checkout -b fix/mein-bugfix
   ```

3. **Änderungen machen**
   - Code schreiben
   - Testen
   - Committen mit aussagekräftiger Message

4. **Pull Request erstellen**
   - Push deinen Branch: `git push origin feature/mein-feature`
   - Gehe zu GitHub und erstelle einen Pull Request
   - Beschreibe deine Änderungen

### 🧪 Automatische Tests

Wenn du einen Pull Request erstellst, läuft automatisch ein **Build Check**:

- ✅ Debug APKs werden gebaut (Standard + F-Droid)
- ✅ Unit Tests werden ausgeführt
- ✅ APKs werden als Artefakte hochgeladen (zum Testen)
- ✅ Build-Status wird als Kommentar im PR gepostet

**Wichtig:** Der Build muss erfolgreich sein (grüner Haken ✅) bevor der PR gemerged werden kann.

### 📱 Android App Development

**Build lokal testen:**
```bash
cd android

# Debug Build
./gradlew assembleStandardDebug

# Tests ausführen
./gradlew test

# Lint Check
./gradlew lint
```

**Anforderungen:**
- Android SDK 36 (Target)
- Android SDK 24 (Minimum)
- JDK 17
- Kotlin 1.9+

### 📝 Code Style

- **Kotlin:** Folge den [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Formatierung:** Android Studio Default Formatter
- **Kommentare:** Deutsch oder Englisch (bevorzugt Englisch für Code)

### 🐛 Bug Reports

Nutze die [Bug Report Template](https://github.com/inventory69/simple-notes-sync/issues/new/choose) mit:
- Android Version
- App Version
- Schritte zum Reproduzieren
- Erwartetes vs. tatsächliches Verhalten

### 💡 Feature Requests

Nutze die [Feature Request Template](https://github.com/inventory69/simple-notes-sync/issues/new/choose) und beschreibe:
- Was soll hinzugefügt werden
- Warum ist es nützlich
- Wie könnte es funktionieren

### 📚 Dokumentation

Dokumentations-Verbesserungen sind auch Contributions! 

**Dateien:**
- `README.de.md` / `README.md` - Übersicht
- `QUICKSTART.de.md` / `QUICKSTART.md` - Schritt-für-Schritt Anleitung
- `docs/DOCS.de.md` / `docs/DOCS.md` - Technische Details
- `server/README.de.md` / `server/README.md` - Server Setup

**Bitte:** Halte beide Sprachen (DE/EN) synchron!

### 🌍 Übersetzungen

Hilf mit, Simple Notes Sync in neue Sprachen zu übersetzen!

**Empfohlen: Über Weblate** (kein Programmieren nötig):
👉 [Auf Weblate übersetzen](https://hosted.weblate.org/projects/simple-notes-sync/)

**Alternativ: Manuelle Übersetzung** (Pull Request):
Siehe [docs/TRANSLATING.de.md](docs/TRANSLATING.de.md) für Details.

Weblate-Übersetzungen werden automatisch als Pull Requests eingereicht und nach erfolgreichem Build gemerged.

### ✅ Pull Request Checklist

- [ ] Code kompiliert lokal (`./gradlew assembleStandardDebug`)
- [ ] Tests laufen durch (`./gradlew test`)
- [ ] Keine neuen Lint-Warnungen
- [ ] Commit-Messages sind aussagekräftig
- [ ] Dokumentation aktualisiert (falls nötig)
- [ ] Beide Sprachen aktualisiert (bei Doku-Änderungen)

### 🎯 Was wird akzeptiert?

**✅ Gerne:**
- Bug Fixes
- Performance-Verbesserungen
- Neue Features (nach Diskussion in einem Issue)
- Dokumentations-Verbesserungen
- Tests
- UI/UX Verbesserungen

**❌ Schwierig:**
- Breaking Changes (bitte erst als Issue diskutieren)
- Komplett neue Architektur
- Dependencies mit fragwürdigen Lizenzen

### 📄 Lizenz

Indem du contributest, stimmst du zu dass dein Code unter der [Apache 2.0 License](LICENSE) veröffentlicht wird.

---

## English

Thanks for wanting to contribute to Simple Notes Sync!

### 🚀 Quick Start

1. **Fork & Clone**
   ```bash
   git clone https://github.com/inventory69/simple-notes-sync.git
   cd simple-notes-sync
   ```

2. **Create Branch**
   ```bash
   git checkout -b feature/my-feature
   # or
   git checkout -b fix/my-bugfix
   ```

3. **Make Changes**
   - Write code
   - Test
   - Commit with meaningful message

4. **Create Pull Request**
   - Push your branch: `git push origin feature/my-feature`
   - Go to GitHub and create a Pull Request
   - Describe your changes

### 🧪 Automated Tests

When you create a Pull Request, an automatic **Build Check** runs:

- ✅ Debug APKs are built (Standard + F-Droid)
- ✅ Unit tests are executed
- ✅ APKs are uploaded as artifacts (for testing)
- ✅ Build status is posted as comment in PR

**Important:** The build must succeed (green checkmark ✅) before the PR can be merged.

### 📱 Android App Development

**Test build locally:**
```bash
cd android

# Debug Build
./gradlew assembleStandardDebug

# Run tests
./gradlew test

# Lint Check
./gradlew lint
```

**Requirements:**
- Android SDK 36 (Target)
- Android SDK 24 (Minimum)
- JDK 17
- Kotlin 1.9+

### 📝 Code Style

- **Kotlin:** Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Formatting:** Android Studio Default Formatter
- **Comments:** German or English (preferably English for code)

### 🐛 Bug Reports

Use the [Bug Report Template](https://github.com/inventory69/simple-notes-sync/issues/new/choose) with:
- Android version
- App version
- Steps to reproduce
- Expected vs. actual behavior

### 💡 Feature Requests

Use the [Feature Request Template](https://github.com/inventory69/simple-notes-sync/issues/new/choose) and describe:
- What should be added
- Why is it useful
- How could it work

### 📚 Documentation

Documentation improvements are also contributions!

**Files:**
- `README.de.md` / `README.md` - Overview
- `QUICKSTART.de.md` / `QUICKSTART.md` - Step-by-step guide
- `docs/DOCS.de.md` / `docs/DOCS.md` - Technical details
- `server/README.de.md` / `server/README.md` - Server setup

**Please:** Keep both languages (DE/EN) in sync!

### 🌍 Translations

Help translate Simple Notes Sync into new languages!

**Recommended: Via Weblate** (no coding required):
👉 [Translate on Weblate](https://hosted.weblate.org/projects/simple-notes-sync/)

**Alternative: Manual translation** (Pull Request):
See [docs/TRANSLATING.md](docs/TRANSLATING.md) for details.

Weblate translations are automatically submitted as pull requests and merged after a successful build.

### ✅ Pull Request Checklist

- [ ] Code compiles locally (`./gradlew assembleStandardDebug`)
- [ ] Tests pass (`./gradlew test`)
- [ ] No new lint warnings
- [ ] Commit messages are meaningful
- [ ] Documentation updated (if needed)
- [ ] Both languages updated (for doc changes)

### 🎯 What Gets Accepted?

**✅ Welcome:**
- Bug fixes
- Performance improvements
- New features (after discussion in an issue)
- Documentation improvements
- Tests
- UI/UX improvements

**❌ Difficult:**
- Breaking changes (please discuss in issue first)
- Completely new architecture
- Dependencies with questionable licenses

### 📄 License

By contributing, you agree that your code will be published under the [Apache 2.0 License](LICENSE).

---

## 🆘 Fragen? / Questions?

Öffne ein [Issue](https://github.com/inventory69/simple-notes-sync/issues) oder nutze die [Question Template](https://github.com/inventory69/simple-notes-sync/issues/new/choose).

**Happy Coding! 🚀**
