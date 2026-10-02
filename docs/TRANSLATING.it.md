# Contribuire con le Traduzioni 🌍

**🌍 Lingue:** [Deutsch](TRANSLATING.de.md) · **Italiano** · [English](TRANSLATING.md)

> Come tradurre Simple Notes Sync nella tua lingua!

---

## 📋 Panoramica

Simple Notes Sync supporta attualmente **12 lingue**:

🇺🇸 Inglese (en, primaria) · 🇩🇪 Tedesco (de) · 🇪🇸 Spagnolo (es) · 🇫🇷 Francese (fr) · 🇮🇩 Indonesiano (in) · 🇮🇹 Italiano (it) · 🇳🇴 Norvegese Bokmål (nb-rNO) · 🇵🇱 Polacco (pl) · 🇷🇺 Russo (ru) · 🇹🇷 Turco (tr) · 🇺🇦 Ucraino (uk) · 🇨🇳 Cinese, Semplificato (zh-rCN)

Altre lingue sono in fase di traduzione su Weblate ma non sono ancora distribuite — una locale viene
aggiunta all'app solo quando supera la soglia del 40% di copertura, altrimenti l'interfaccia
sarebbe ancora quasi interamente in inglese.

Accogliamo con piacere nuove traduzioni e miglioramenti a quelle esistenti!

---

## 🌐 Traduci tramite Weblate (Consigliato)

Il modo più semplice per contribuire con le traduzioni è tramite **Weblate** — nessuna conoscenza di programmazione richiesta:

👉 **[Traduci su Weblate](https://hosted.weblate.org/projects/simple-notes-sync/)**

1. Crea un account Weblate gratuito
2. Vai al progetto Simple Notes Sync
3. Seleziona la tua lingua (o richiedine una nuova)
4. Inizia a tradurre direttamente nel browser

Weblate crea automaticamente le pull request con le tue traduzioni. Queste PR passano attraverso lo stesso controllo di build CI di tutti gli altri contributi. Una volta che la build passa, vengono approvate e unite.

---

## 🚀 Traduzione Manuale (Alternativa)

Se preferisci lavorare direttamente con i file sorgente:

### 1. Fork del Repository

1. Vai su [github.com/inventory69/simple-notes-sync](https://github.com/inventory69/simple-notes-sync)
2. Clicca **Fork** (in alto a destra)
3. Clona il tuo fork: `git clone https://github.com/TUO-NOME-UTENTE/simple-notes-sync.git`

### 2. Crea i File della Lingua

```bash
cd simple-notes-sync/android/app/src/main/res

# Crea la cartella per la tua lingua (ad es. francese)
mkdir values-fr

# Copia le stringhe
cp values/strings.xml values-fr/strings.xml
```

### 3. Traduci le Stringhe

Apri `values-fr/strings.xml` e traduci tutte le voci `<string>`:

```xml
<!-- Originale (inglese) -->
<string name="settings">Settings</string>
<string name="notes_title">Notes</string>

<!-- Tradotto (francese) -->
<string name="settings">Paramètres</string>
<string name="notes_title">Notes</string>
```

**Importante:**
- Traduci solo il testo tra `>` e `</string>`
- NON modificare gli attributi `name="..."`
- NON tradurre `app_name` — mantienilo come "Simple Notes"
- Mantieni `%s`, `%d`, `%1$s` ecc. come segnaposto
- Mantieni invariati i caratteri emoji (📝, ✅, ecc.)

### 4. Aggiorna locales_config.xml

Aggiungi la tua lingua a `android/app/src/main/res/xml/locales_config.xml`:

```xml
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="de" />
    <locale android:name="fr" />  <!-- NUOVO -->
</locale-config>
```

**Registra anche la locale per la build:** aggiungila a `localeFilters` in `android/app/build.gradle.kts`, altrimenti la nuova lingua verrà rimossa dall'APK per mantenerne piccole le dimensioni:

```kotlin
localeFilters += listOf(
    "en", "de", "es", "hi", "in", "it", "nb-rNO", "ru", "tr", "uk", "zh-rCN",
    "fr",  // NUOVO
)
```

### 5. Crea una Pull Request

1. Esegui il commit delle tue modifiche
2. Esegui il push sul tuo fork
3. Crea una Pull Request con il titolo: `Add [Language] translation`

---

## 📁 Struttura dei File

```
android/app/src/main/res/
├── values/              # Inglese (Fallback)
│   └── strings.xml
├── values-de/           # Tedesco
│   └── strings.xml
├── values-fr/           # Francese (nuovo)
│   └── strings.xml
└── xml/
    └── locales_config.xml  # Registrazione delle lingue
```

---

## 📝 Categorie di Stringhe

Il file `strings.xml` contiene circa 440+ stringhe (incluse 5 forme plurali), suddivise in:

| Categoria | Descrizione | Conteggio |
|----------|-------------|-------|
| Testi UI | Pulsanti, etichette, titoli | ~120 |
| Impostazioni | Tutte le schermate delle impostazioni | ~150 |
| Dialoghi | Conferme, errori | ~80 |
| Sincronizzazione | Messaggi di sincronizzazione | ~50 |
| Altro | Suggerimenti, accessibilità, widget | ~40 |

---

## ✅ Checklist di Qualità

Prima di creare la tua Pull Request (non necessaria per i contributi Weblate):

- [ ] Tutte le stringhe tradotte (nessun residuo in inglese)
- [ ] `app_name` lasciato come "Simple Notes"
- [ ] Segnaposto (`%s`, `%d`) preservati
- [ ] Caratteri emoji invariati
- [ ] Nessun errore di sintassi XML
- [ ] L'app si avvia senza crash
- [ ] Il testo si adatta agli elementi UI (non troppo lungo)
- [ ] `locales_config.xml` aggiornato

---

## 🔧 Test

```bash
cd android
./gradlew app:assembleDebug

# Installa l'APK e cambia la lingua nelle impostazioni Android
```

---

## ❓ FAQ

**Devo tradurre tutte le stringhe?**
> Idealmente sì. Le stringhe mancanti ripiegano sull'inglese.

**E i segnaposto?**
> `%s` = testo, `%d` = numero. Mantieni la posizione o usa `%1$s` per la numerazione.

**Come testo la mia traduzione?**
> Compila l'app, installala, vai su Impostazioni Android → App → Simple Notes → Lingua.

---

## 🙏 Grazie!

Ogni traduzione aiuta Simple Notes Sync a raggiungere più persone.

Domande? [Crea una GitHub Issue](https://github.com/inventory69/simple-notes-sync/issues)

[← Torna alla Documentazione](DOCS.it.md)