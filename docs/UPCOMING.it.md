# Funzionalità in Arrivo 🚀

**🌍 Lingue:** [Deutsch](UPCOMING.de.md) · **Italiano** · [English](UPCOMING.md)

> Cosa succederà? Qui trovi i nostri piani per le versioni future.

---

## v1.5.0 - Jetpack Compose e Internazionalizzazione ✅

> **Stato:** Rilasciata 🎉 (gennaio 2026)

### 🎨 Interfaccia Jetpack Compose

- ✅ **Ridisegno completo dell'interfaccia** - Dalle viste XML a Jetpack Compose
- ✅ **Impostazioni modernizzate** - 7 schermate categorizzate
- ✅ **Modalità selezione** - Pressione lunga per la multi-selezione
- ✅ **Modalità di sync silenziosa** - Nessun banner durante l'auto-sync

### 🌍 Supporto Multi-Lingua

- ✅ **Inglese + Tedesco** - 400+ stringhe tradotte
- ✅ **Rilevamento automatico della lingua** - Segue la lingua di sistema
- ✅ **Lingua per app (Android 13+)** - Selezione nativa della lingua

### 🎨 Miglioramenti dell'interfaccia

- ✅ **Schermata di avvio** - Icona dell'app in primo piano
- ✅ **Icona dell'app** - Nella schermata Info e nello stato vuoto
- ✅ **Animazioni di scorrimento** - Transizioni fluide nel NoteEditor

---

## v1.6.0 - Modernizzazione Tecnica ✅

> **Stato:** Rilasciata 🎉 (gennaio 2026)

### ⚙️ Trigger di Sincronizzazione Configurabili

- ✅ **Controllo individuale dei trigger** - Attiva/disattiva ogni trigger di sincronizzazione separatamente
- ✅ **Predefiniti guidati da eventi** - onSave, onResume, WiFi-Connect attivi per impostazione predefinita
- ✅ **Sync periodica opzionale** - Intervalli 15/30/60 min (predefinito: OFF)
- ✅ **Sync all'avvio opzionale** - Avvia la sync periodica dopo il riavvio del dispositivo (predefinito: OFF)
- ✅ **Interfaccia modalità offline** - Interruttori attenuati quando nessun server è configurato
- ✅ **Batteria ottimizzata** - ~0,2%/giorno con i predefiniti, fino a ~1,0% con la periodica

---

## v1.6.1 - Codice Pulito ✅

> **Stato:** Rilasciata 🎉 (gennaio 2026)

### 🧹 Qualità del Codice

- ✅ **detekt: 0 problemi** - Tutti i 29 problemi di qualità del codice risolti
- ✅ **Zero avvisi di build** - Tutti i 21 avvisi di deprecazione eliminati
- ✅ **ktlint riattivato** - Con regole specifiche per Compose
- ✅ **Controlli lint CI/CD** - Integrati nel workflow di build delle PR
- ✅ **Refactoring delle costanti** - Dimensions.kt, SyncConstants.kt

---

## v1.7.0 - Vista Griglia, Solo WiFi e VPN ✅

> **Stato:** Rilasciata 🎉 (gennaio 2026)

### 🎨 Layout a Griglia

- ✅ **Griglia a scaglioni stile Pinterest** - Layout senza spazi con righe di anteprima dinamiche
- ✅ **Interruttore layout** - Passa tra elenco e griglia nelle impostazioni
- ✅ **Colonne adattive** - 2-3 colonne in base alle dimensioni dello schermo

### 📡 Miglioramenti della Sincronizzazione

- ✅ **Interruttore sync solo WiFi** - Sincronizza solo quando connesso al WiFi
- ✅ **Supporto VPN** - La sincronizzazione funziona correttamente attraverso tunnel VPN
- ✅ **SSL self-signed** - Documentazione e supporto per certificati self-signed
- ✅ **Rilevamento cambiamento server** - Tutte le note vengono riportate a PENDING quando l'URL del server cambia

---

## v1.7.1 - Fix Android 9 e VPN ✅

> **Stato:** Rilasciata 🎉 (febbraio 2026)

- ✅ **Fix crash Android 9** - Implementato `getForegroundInfo()` per WorkManager su API 28
- ✅ **Compatibilità VPN** - Il binding del socket WiFi rileva le interfacce VPN Wireguard
- ✅ **SafeSardineWrapper** - Pulizia corretta della connessione HTTP

---

## v1.7.2 - Fix Timestamp ed Eliminazione ✅

> **Stato:** Rilasciata 🎉 (febbraio 2026)

- ✅ **mtime del server come fonte di verità** - Risolve i problemi di timestamp dell'editor esterno
- ✅ **Mutex del tracciamento eliminazioni** - Eliminazioni in blocco thread-safe
- ✅ **Parsing timezone ISO8601** - Supporto multi-formato
- ✅ **Cache E-Tag in blocco** - Miglioramento delle prestazioni
- ✅ **Prevenzione perdita di memoria** - SafeSardineWrapper con Closeable

---

## v1.8.0 - Widget, Ordinamento e Sync Avanzata ✅

> **Stato:** Rilasciata 🎉 (febbraio 2026)

### 📌 Widget della schermata Home

- ✅ **Framework Jetpack Glance completo** - 5 classi di dimensione reattive
- ✅ **Checklist interattive** - Checkbox che si sincronizzano con il server
- ✅ **Colori Material You** - Colori dinamici con opacità configurabile
- ✅ **Interruttore di blocco** - Previene modifiche accidentali
- ✅ **Attività di configurazione** - Selezione della nota e impostazioni

### 📊 Ordinamento

- ✅ **Ordinamento note** - Per titolo, data modifica, data creazione, tipo
- ✅ **Ordinamento checklist** - Manuale, alfabetico, non spuntate prima, spuntate per ultime
- ✅ **Separatori visivi** - Tra i gruppi non spuntati/spuntati
- ✅ **Trascinamento oltre i confini** - Attivazione automatica dello stato al trascinamento oltre i confini

### 🔄 Miglioramenti della Sincronizzazione

- ✅ **Download paralleli** - Fino a 5 simultanei (configurabili)
- ✅ **Rilevamento eliminazioni sul server** - Rileva note eliminate su altri client
- ✅ **Avanzamento sync dal vivo** - Indicatori di fase con contatori
- ✅ **Legenda stato sync** - Dialogo di aiuto che spiega tutte le icone di sync

### ✨ UX

- ✅ **Changelog post-aggiornamento** - Mostra il changelog localizzato al primo avvio dopo un aggiornamento
- ✅ **Griglia come predefinita** - Le nuove installazioni usano la vista griglia come predefinita
- ✅ **Migrazione Toast → Banner** - Sistema di notifica unificato

---

## v1.8.1 - Fix e Rifinitura ✅

> **Stato:** Rilasciata 🎉 (febbraio 2026)

- ✅ **Persistenza ordinamento checklist** - L'opzione di ordinamento viene ripristinata correttamente alla riapertura
- ✅ **Fix scorrimento widget** - Lo scorrimento funziona sulla dimensione standard 3×2 del widget
- ✅ **Ordinamento checklist nel widget** - I widget applicano l'opzione di ordinamento salvata
- ✅ **Trascinamento oltre i confini** - Drag & drop oltre il separatore spuntato/non spuntato
- ✅ **Limitazione della velocità di sync** - Cooldown globale di 30 s tra auto-sync
- ✅ **detekt: 0 problemi** - Tutti i 12 risultati risolti

---

## v1.8.2 - Fix di Stabilità e Editor ✅

> **Stato:** Rilasciata 🎉 (febbraio 2026)

- ✅ **26 bugfix** - Deadlock di sync, prevenzione perdita dati, UX editor
- ✅ **Supporto SSL self-signed** - Certificati CA utente nelle build release
- ✅ **Fix scorrimento widget** - Testo scorrevole nei widget medi
- ✅ **Auto-maiuscola della tastiera** - Campo titolo, elementi checklist
- ✅ **Ottimizzazione dimensione APK** - Regole ProGuard granulari (< 5 MB)
- ✅ **Stabilità drag checklist** - Fix drag & drop oltre i confini

---

## v1.9.0 - Filtro, Ricerca, Markdown e Rifinitura Widget ✅

> **Stato:** Rilasciata 🎉 (febbraio 2026)

### Parte 1: Qualità Sync e Importazione
- ✅ **Procedura di importazione note** - Importa da WebDAV o locale (.md, .json, .txt)
- ✅ **Caricamenti paralleli** - Sync multi-nota ~2× più veloce
- ✅ **Connessioni parallele unificate** - Unica impostazione per caricamenti e scaricamenti
- ✅ **Fix cambio server** - Le cache E-Tag/hash contenuto vengono azzerate al cambiamento
- ✅ **Fix rilevamento eliminazioni** - Soglia alzata per piccoli portafogli di note
- ✅ **Serializzazione esportazione Markdown** - Il mutex previene la race condition
- ✅ **Cache E-Tag** - Salta i riscaricamenti ridondanti

### Parte 2: Funzionalità UI

#### 📊 Filtro e Ricerca
- ✅ **Riga di chip filtro** - Filtra per Tutte / Testo / Checklist
- ✅ **Ricerca inline** - Campo di ricerca rapida nella riga dei filtri
- ✅ **Ordina nella riga dei filtri** - Pulsante ordina spostato dal dialogo alla riga dei filtri
- ✅ **Attivazione riga filtri** - Pulsante di regolazione nella TopAppBar per mostrare/nascondere

#### ✏️ Editor
- ✅ **Anteprima Markdown** - Anteprima dal vivo per note di testo con barra degli strumenti di formattazione
- ✅ **Ripristino sbunta checklist** - L'elemento torna alla posizione originale
- ✅ **Consolidamento ordine checklist** - L'ordine originale viene preservato dopo inserimento/eliminazione
- ✅ **Comportamento scorrimento checklist** - Scorrimento coerente allo spuntare/sbuntare
- ✅ **Salvataggio automatico opzionale** - Timer di salvataggio automatico debounce configurabile
- ✅ **Cartella di sincronizzazione configurabile** - Nome cartella WebDAV personalizzato

#### 📌 Miglioramenti dei Widget
- ✅ **Conservazione della tinta Monet** - Lo sfondo traslucido mantiene i colori dinamici
- ✅ **Barra opzioni senza cuciture** - Sfondo rimosso per un aspetto più pulito
- ✅ **Barratura nelle checklist** - Gli elementi completati mostrano la barratura
- ✅ **Aggiornamento widget su onStop** - I widget si aggiornano quando esci dall'app

#### ✨ Altro
- ✅ **Titolo app personalizzato** - Nome dell'app configurabile nelle impostazioni
- ✅ **Scorri in alto su sync** - L'elenco scorre in alto dopo la sync manuale

---

## v2.0.0 - Riscrittura Compose e Multi-Tema ✅

> **Stato:** Rilasciata 🎉 (marzo 2026)

### 🎨 Sistema Multi-Tema
- ✅ **7 schemi di colori** - Inclusi AMOLED e Dynamic Color con transizioni animate e superfici tinte
- ✅ **Ridimensionamento colonne griglia** - Da 1 a 5 colonne configurabili nelle impostazioni di visualizzazione
- ✅ **Chip della griglia** - Sostituiscono i pulsanti radio nelle impostazioni di visualizzazione

### ✨ Editor e Impostazioni
- ✅ **Backup/ripristino completo** - Include tutte le impostazioni dell'app, non solo le note
- ✅ **Transizioni Material 3 shared axis** - Per tutta la navigazione e i gesti indietro
- ✅ **Stato salvataggio automatico** - Mostrato nel sottotitolo delle impostazioni di visualizzazione
- ✅ **Dialogo di logging di debug** - Disattiva il logging dopo l'esportazione

### 🐛 Bugfix
- ✅ **Drag-and-drop delle checklist** - Riscritto per stabilità negli elenchi lunghi
- ✅ **Eliminazioni offline** - Accodate per la sync successiva
- ✅ **Compatibilità WebDAV 403** - L'HTTP 403 viene trattato come esistente
- ✅ **Thread-safety** - Risolti incoerenze di stato e problemi di dispatcher
- ✅ **Perdite di risorse** - InputStream chiusi, I/O file fuori dal thread principale
- ✅ **Race condition salva-su-indietro** - Flush TextFieldState + salvataggio onPause

### 🗑️ Rimozione Codice Legacy
- ✅ **SettingsActivity rimossa** - Sostituita dalle impostazioni Compose
- ✅ **MainActivity rimossa** - Sostituita da ComposeMainActivity
- ✅ **NoteEditorActivity rimossa** - Sostituita dall'editor Compose
- ✅ **Layout XML, menu, drawable rimossi** - Interfaccia Compose completa
- ✅ **LocalBroadcastManager → SharedFlow** - Architettura degli eventi moderna
- ✅ **DSL viewModelFactory** - Creazione ViewModel moderna

### 🏗️ Architettura
- ✅ **WebDavSyncService → pattern Facade** - Suddiviso in 9 moduli estratti
- ✅ **Ottimizzato con R8/ProGuard** - Dimensione APK ridotta

### 📄 Licenza
- ✅ **MIT → AGPL v3** - Licenza cambiata

---

## v2.2.0 - Share Intent, Widget e Rifinitura Editor

> **Stato:** Rilasciata 🎉 (marzo 2026)

### 📤 Share Intent

- **Ricevi contenuto condiviso** - Accetta testo e URL da altre app tramite Android Share Intent ([Discussione #46](https://github.com/inventory69/simple-notes-sync/discussions/46) di [@madelgijs](https://github.com/madelgijs))
- **Crea nota da testo condiviso** - Il contenuto condiviso crea una nuova nota o si aggiunge a una esistente
- **Gestione URL** - Gli URL condivisi dai browser vengono formattati come link Markdown cliccabili

### 📌 Widget Scorciatoia Nuova Nota

- **Nuova nota con un tocco** - Widget della schermata Home che apre subito l'editor per una nuova nota ([Discussione #49](https://github.com/inventory69/simple-notes-sync/discussions/49) di [@Stowaway2979](https://github.com/Stowaway2979))
- **Impronta minima** - Widget 1×1 piccolo con l'icona dell'app e un badge `+`
- **Digitazione istantanea** - L'editor si apre con la tastiera focalizzata sul campo titolo

### ✏️ Miglioramenti dell'Editor

- **Pulsante checklist Markdown** - Nuovo pulsante nella barra degli strumenti per inserire la sintassi checkbox Markdown `- [ ]` / `- [x]`, con supporto di attivazione per le righe esistenti
- **Copia e duplica elementi checklist** - Menu contestuale a pressione lunga sugli elementi della checklist: copia testo negli appunti o duplica l'elemento sotto

---

## v2.3.0 – v2.9.0 ✅

> **Stato:** Rilasciate 🎉

Distribuito da quando questa roadmap è stata rivista l'ultima volta: credenziali crittografate, il logger di debug sync persistente, **importazione da Google Keep**, **colori delle note**, **note fissate**, conversione testo ↔ checklist, **cartelle** (incluse solo-locali), un **cestino / bin per il riciclo** con conservazione configurabile, Markdown dal vivo nell'editor e nelle anteprime delle card, il widget elenco note scorrevole, un changelog nell'app e la schermata contributori, e la crescita a 12 lingue. Vedi il [CHANGELOG](../CHANGELOG.md) per la cronologia completa per versione.

---

## 📋 Backlog

> Funzionalità da considerare in futuro

### 🔐 Miglioramenti della Sicurezza

- **Backup locali protetti da password** - Crittografa il file di backup con una password

### 🎨 Funzionalità UI

- **Nascondi checklist completate** - Opzione per nascondere le checklist in cui tutti gli elementi sono spuntati, con una vista separata per recuperarle in seguito. ([#45](https://github.com/inventory69/simple-notes-sync/discussions/45) di @isawaway)

### ✅ Consegnate di recente

- **Cartelle / Quaderni** ✅ _(v2.7.0)_ - incluse cartelle solo-locali che non vengono mai sincronizzate ([#38](https://github.com/inventory69/simple-notes-sync/discussions/38) di @happy-turtle)
- **Ricerca** ✅ _(v1.9.0)_ - ricerca inline nella riga dei filtri
- **Lingue aggiuntive** ✅ - ora 12 lingue tramite Weblate

---

## 💡 Feedback e Suggerimenti

Hai un'idea per una nuova funzionalità?

- **[Crea una richiesta di funzionalità](https://github.com/inventory69/simple-notes-sync/issues/new?template=feature_request.yml)**
- **[Visualizza le richieste esistenti](https://github.com/inventory69/simple-notes-sync/issues?q=is%3Aissue+label%3Aenhancement)**

---

**Nota:** questa roadmap mostra i nostri piani attuali. Le priorità possono cambiare in base al feedback della community.

[← Torna alla documentazione](DOCS.it.md)