# Contributing Translations 🌍

**🌍 Languages:** [Deutsch](TRANSLATING.de.md) · **English**

> How to translate Simple Notes Sync into your language!

---

## 📋 Overview

Simple Notes Sync currently supports:
- 🇺🇸 **English** (en) - Primary language
- 🇩🇪 **German** (de) - Fully translated

We welcome new translations!

---

## 🚀 Quick Start

### 1. Fork the Repository

1. Go to [github.com/inventory69/simple-notes-sync](https://github.com/inventory69/simple-notes-sync)
2. Click **Fork** (top right)
3. Clone your fork: `git clone https://github.com/YOUR-USERNAME/simple-notes-sync.git`

### 2. Create Language Files

```bash
cd simple-notes-sync/android/app/src/main/res

# Create folder for your language (e.g., French)
mkdir values-fr

# Copy strings
cp values/strings.xml values-fr/strings.xml
```

### 3. Translate Strings

Open `values-fr/strings.xml` and translate all `<string>` entries:

```xml
<!-- Original (English) -->
<string name="app_name">Simple Notes</string>
<string name="notes_title">Notes</string>

<!-- Translated (French) -->
<string name="app_name">Notes Simples</string>
<string name="notes_title">Notes</string>
```

**Important:**
- Only translate text between `>` and `</string>`
- Do NOT change `name="..."` attributes
- Keep `%s`, `%d`, `%1$s` etc. as placeholders

### 4. Update locales_config.xml

Add your language to `android/app/src/main/res/xml/locales_config.xml`:

```xml
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="de" />
    <locale android:name="fr" />  <!-- NEW -->
</locale-config>
```

### 5. Create Pull Request

1. Commit your changes
2. Push to your fork
3. Create a Pull Request with title: `Add [Language] translation`

---

## 📁 File Structure

```
android/app/src/main/res/
├── values/              # English (Fallback)
│   └── strings.xml
├── values-de/           # German
│   └── strings.xml
├── values-fr/           # French (new)
│   └── strings.xml
└── xml/
    └── locales_config.xml  # Language registration
```

---

## 📝 String Categories

The `strings.xml` contains about 400+ strings, divided into:

| Category | Description | Count |
|----------|-------------|-------|
| UI Texts | Buttons, labels, titles | ~100 |
| Settings | All 7 settings screens | ~150 |
| Dialogs | Confirmations, errors | ~80 |
| Sync | Synchronization messages | ~50 |
| Other | Tooltips, accessibility | ~30 |

---

## ✅ Quality Checklist

Before creating your Pull Request:

- [ ] All strings translated (no English leftovers)
- [ ] Placeholders (`%s`, `%d`) preserved
- [ ] No XML syntax errors
- [ ] App launches without crashes
- [ ] Text fits in UI elements (not too long)
- [ ] `locales_config.xml` updated

---

## 🔧 Testing

```bash
cd android
./gradlew app:assembleDebug

# Install APK and switch language in Android settings
```

---

## ❓ FAQ

**Do I need to translate all strings?**
> Ideally yes. Missing strings fall back to English.

**What about placeholders?**
> `%s` = text, `%d` = number. Keep position or use `%1$s` for numbering.

**How do I test my translation?**
> Build app, install, go to Android Settings → Apps → Simple Notes → Language.

---

## 🙏 Thank You!

Every translation helps Simple Notes Sync reach more people.

Questions? [Create a GitHub Issue](https://github.com/inventory69/simple-notes-sync/issues)

[← Back to Documentation](DOCS.md)
