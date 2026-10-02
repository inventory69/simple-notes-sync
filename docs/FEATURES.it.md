# Elenco Completo delle Funzionalità 📋

**🌍 Lingue:** [Deutsch](FEATURES.de.md) · **Italiano** · [English](FEATURES.md)

> Tutte le funzionalità di Simple Notes Sync in dettaglio

---

## 📝 Gestione delle Note

### Tipi di note
- ✅ **Note di testo** - Classiche note a scrittura libera
- ✅ **Checklist** _(NUOVO in v1.4.0)_ - Elenchi di attività con spunta al tocco
  - ➕ Aggiungi elementi tramite il campo di input
  - ☑️ Tocca per spuntare/sbuntare
  - 📌 Pressione lunga per ordinare tramite drag & drop
  - ~~Barrato~~ per le voci completate
  - ↩️ Sbuntare riporta l'elemento alla posizione originale _(v1.9.0)_
- ✅ **Conversione di tipo** _(NUOVO in v2.6.0)_ - Converti una nota di testo in checklist e viceversa
- ✅ **Crea da testo condiviso** _(NUOVO in v2.2.0)_ - Ricevi testo/URL condivisi da altre app come nuova nota o checklist

### Funzionalità di base
- ✅ **Salvataggio automatico** - Nessun salvataggio manuale necessario
- ✅ **Titolo + contenuto** - Struttura chiara per ogni nota
- ✅ **Timestamp** - Data di creazione e modifica automatiche
- ✅ **Modalità selezione** _(NUOVO in v1.5.0)_ - Pressione lunga per multi-selezione ed eliminazione in blocco
- ✅ **Dialogo di conferma** - Protezione contro l'eliminazione accidentale
- ✅ **Interfaccia Jetpack Compose** _(NUOVO in v1.5.0)_ - Interfaccia utente moderna e performante
- ✅ **Material Design 3** - Interfaccia moderna e pulita
- ✅ **Modalità scura** - Automaticamente in base alle impostazioni di sistema
- ✅ **Colori dinamici** - Si adatta al tema Android
- ✅ **Multi-tema** _(NUOVO in v2.0.0)_ - 7 schemi di colori con transizioni animate e superfici tinte
- ✅ **Titolo app personalizzato** _(NUOVO in v1.9.0)_ - Nome dell'app configurabile
- ✅ **Dimensione del testo regolabile** _(NUOVO in v2.8.0)_ - Ridimensionamento del testo delle note per tutta l'app

### Editor
- ✅ **Editor minimalista** - Niente fronzoli
- ✅ **Auto-focus** - Inizia a scrivere immediatamente
- ✅ **Modalità schermo intero** - Massimo spazio di scrittura
- ✅ **Pulsante salva** - Conferma manuale possibile
- ✅ **Navigazione indietro** - Salva automaticamente quando il salvataggio automatico è attivato _(v1.10.0)_
- ✅ **Animazioni di scorrimento** _(NUOVO in v1.5.0)_ - Transizioni fluide
- ✅ **Anteprima Markdown** _(NUOVO in v1.9.0)_ - Anteprima dal vivo con barra degli strumenti di formattazione
- ✅ **Salvataggio automatico opzionale** _(NUOVO in v1.9.0)_ - Timer di salvataggio automatico debounce configurabile
- ✅ **Annulla/Ripeti** _(NUOVO in v1.10.0)_ - Cronologia completa annulla/ripeti (fino a 50 passi) con pulsanti nella barra degli strumenti
- ✅ **Condivisione ed esportazione** _(NUOVO in v1.10.0)_ - Condividi come testo o PDF, esporta nel calendario
- ✅ **Eliminazione con annulla** _(NUOVO in v1.10.0)_ - Elimina dall'editor con snackbar di annullamento temporizzato
- ✅ **Evidenziazione Markdown dal vivo** _(NUOVO in v2.8.0)_ - Il Markdown viene evidenziato dal vivo mentre scrivi
- ✅ **Attivazione checkbox al tocco** _(NUOVO in v2.8.0)_ - Tocca una checkbox Markdown nell'editor per attivare/disattivare l'attività
- ✅ **Modalità di apertura predefinita** _(NUOVO in v2.8.0)_ - Scegli se le note di testo si aprono in modalità modifica o anteprima
- ✅ **Anteprima selezionabile** _(NUOVO in v2.9.0)_ - Seleziona e copia testo dall'anteprima Markdown
- ✅ **URL con collegamento automatico** _(NUOVO in v2.6.0)_ - Gli URL nudi diventano link cliccabili nell'anteprima
- ✅ **Copia e duplica** _(NUOVO in v2.6.0)_ - Copia il testo della nota o duplica gli elementi della checklist tramite il menu contestuale
- ✅ **Aggiungi testo condiviso** _(NUOVO in v2.6.0)_ - Aggiungi testo condiviso da altre app a una nota esistente
- ✅ **Elemento checklist nel calendario** _(NUOVO in v2.8.0)_ - Voce "Aggiungi al calendario" per singolo elemento nel menu contestuale
- ✅ **Incolla dagli appunti ricco** _(NUOVO in v2.10.0)_ - L'incollaggio HTML (Telegram, Word, Google Docs, browser) viene convertito in Markdown - grassetto/corsivo/link/elenchi/intestazioni/codice/citazioni, con fallback in testo semplice

---

## 📊 Visualizzazioni e Layout _(NUOVO dalla v1.7.0+)_

### Modalità di visualizzazione
- ✅ **Vista elenco** - Layout classico a elenco
- ✅ **Vista griglia** _(NUOVO in v1.7.0)_ - Griglia a scaglioni stile Pinterest con righe di anteprima dinamiche
- ✅ **Interruttore layout** - Passa tra elenco e griglia nelle impostazioni
- ✅ **Colonne adattive** - 2-3 colonne in base alle dimensioni dello schermo
- ✅ **Ridimensionamento colonne griglia** _(NUOVO in v2.0.0)_ - Da 1 a 5 colonne configurabili nelle impostazioni di visualizzazione
- ✅ **Griglia come impostazione predefinita** _(v1.8.0)_ - Le nuove installazioni usano la vista griglia come predefinita
- ✅ **Note fissate** _(NUOVO in v2.6.0)_ - Le note fissate compaiono in una sezione dedicata in alto
- ✅ **Anteprime Markdown** _(NUOVO in v2.8.0)_ - Le card di elenco/griglia visualizzano il Markdown, inclusi prefissi checklist ☑/☐ e blocchi di codice
- ✅ **Sezioni comprimibili** _(NUOVO in v2.10.0)_ - Comprimi le intestazioni Fissate/Cartelle/Note e premi a lungo la freccia dell'intestazione per riordinarle; entrambe persistono tra i riavvii

### Ordinamento delle note _(NUOVO in v1.8.0)_
- ✅ **Ordina per aggiornamento** - Dal più nuovo o dal più vecchio
- ✅ **Ordina per creazione** - Per data di creazione
- ✅ **Ordina per titolo** - A-Z o Z-A
- ✅ **Ordina per tipo** - Note di testo vs checklist
- ✅ **Preferenze persistenti** - L'opzione di ordinamento viene salvata tra i riavvii dell'app
- ✅ **Dialogo di ordinamento** - Interruttore di direzione nella schermata principale

### Filtro delle note _(NUOVO in v1.9.0)_
- ✅ **Riga di chip filtro** - Filtra per Tutte, Testo o Checklist
- ✅ **Ricerca inline** - Ricerca rapida nella riga dei filtri
- ✅ **Pulsante ordina** - Icona di ordinamento compatta nella riga dei filtri
- ✅ **Visibilità attivabile** - Il pulsante di regolazione mostra/nasconde la riga dei filtri
- ✅ **Filtro per colore** _(NUOVO in v2.5.0)_ - Filtra l'elenco per colore della nota
- ✅ **Ordinamento per colore** _(NUOVO in v2.5.1)_ - Ordina le note per colore
- ✅ **Cartella di sincronizzazione configurabile** - Nome cartella WebDAV personalizzato

### Ordinamento delle checklist _(NUOVO in v1.8.0)_
- ✅ **Manuale** - Ordine personalizzato con drag & drop
- ✅ **Alfabetico** - Ordinamento A-Z
- ✅ **Non spuntate in cima** - Gli elementi non spuntati in alto
- ✅ **Spuntate in fondo** - Gli elementi spuntati in basso
- ✅ **Data di creazione** _(NUOVO in v1.11.0)_ - Ordina per data di creazione (crescente o decrescente)
- ✅ **Separatore visivo** - Tra i gruppi non spuntati/spuntati con conteggio
- ✅ **Riordino automatico all'attivazione** - Riordina quando spunti/sbunti elementi
- ✅ **Trascinamento oltre i confini** - Gli elementi cambiano stato automaticamente quando attraversano il separatore

---

## 📁 Cartelle _(NUOVO in v2.7.0)_

- ✅ **Organizza in cartelle** - Raggruppa le note in cartelle per una separazione più chiara
- ✅ **Filtro cartelle** - Mostra le note di una singola cartella
- ✅ **Sincronizzazione cartelle** - Le cartelle corrispondono alle sottodirectory WebDAV
- ✅ **Cartelle solo locali** _(NUOVO in v2.8.0)_ - Contrassegna una cartella come solo locale così le sue note non vengono mai sincronizzate sul server
- ✅ **Rinomina ed eliminazione sicure** - Rinominare o eliminare una cartella sposta le sue note senza lasciare orfani sul server
- ✅ **Nome cartella di sincronizzazione personalizzato** - Cartella WebDAV root configurabile

---

## 🗑️ Cestino / Cestino di riciclo _(NUOVO in v2.8.0)_

- ✅ **Sposta nel cestino** - Eliminare una nota la sposta nel Cestino invece di cancellarla
- ✅ **Schermata Cestino** - Ripristina o elimina definitivamente le note dalle Impostazioni
- ✅ **Conservazione configurabile** _(NUOVO in v2.10.0)_ - Eliminazione automatica dopo Immediato / 7 / 14 / 30 / 90 giorni (predefinito 30)
- ✅ **Snackbar di annullamento** - Annulla temporizzato subito dopo l'eliminazione
- ✅ **Eliminazioni server recuperabili** - Le note eliminate su un altro dispositivo finiscono nel Cestino locale invece di sparire

---

## 🗄️ Archivio _(NUOVO in v2.11.0)_

- ✅ **Archivia le note** - Sposta una nota fuori dall'elenco principale senza eliminarla, dall'editor o tramite multi-selezione
- ✅ **Vista archivio** - Attiva l'elenco note per mostrare le note archiviate invece di quelle attive
- ✅ **Archiviazione/scaricamento in blocco** - Archivia o ripristina più note selezionate contemporaneamente
- ✅ **Snackbar di annullamento** - Annulla temporizzato subito dopo l'archiviazione o lo scaricamento

---

## 🎨 Colori delle Note _(NUOVO in v2.5.0)_

- ✅ **Note codificate a colori** - Assegna un colore a qualsiasi nota
- ✅ **Colorazione multi-selezione** - Applica un colore a più note contemporaneamente
- ✅ **Filtra per colore** - Mostra solo le note di un determinato colore
- ✅ **Ordina per colore** _(v2.5.1)_ - Raggruppa l'elenco per colore

---

## 📥 Importazione

### Importazione da Google Keep _(NUOVO in v2.5.0)_
- ✅ **Supporto export Keep** - Importa note da un export di Google Keep (Takeout)
- ✅ **Checklist e colori** - Le checklist di Keep e i colori delle etichette vengono preservati
- ✅ **Strategia di conflitto** - Scegli come gestire i duplicati durante l'importazione

### Procedura guidata di importazione note _(NUOVO in v1.9.0)_
- ✅ **Da WebDAV o locale** - Importa file `.md`, `.json`, o `.txt`
- ✅ **Seleziona tutto / deseleziona tutto** - Selezione in blocco per importazioni WebDAV
- ✅ **Strategia di conflitto** - Salta, sovrascrivi o mantieni entrambi

---

## 📌 Widget della schermata Home _(NUOVO in v1.8.0)_

### Funzionalità dei widget
- ✅ **Widget nota di testo** - Visualizza qualsiasi nota sulla schermata Home
- ✅ **Widget checklist** - Checkbox interattive che si sincronizzano con il server
- ✅ **Widget scorciatoia nuova nota** _(NUOVO in v2.2.0)_ - Widget 1×1 che apre l'editor per una nuova nota
- ✅ **Widget elenco note scorrevole** _(NUOVO in v2.8.0)_ - Un elenco scorrevole di note con Markdown inline
- ✅ **5 classi di dimensione** - SMALL, NARROW_MED, NARROW_TALL, WIDE_MED, WIDE_TALL
- ✅ **Colori Material You** - Colori dinamici che corrispondono al tema di sistema
- ✅ **Opacità configurabile** - Trasparenza dello sfondo (0-100%)
- ✅ **Interruttore di blocco** - Previene modifiche accidentali
- ✅ **Aggiornamento automatico** - Si aggiorna al completamento della sincronizzazione
- ✅ **Attività di configurazione** - Selezione della nota e impostazioni
- ✅ **Ordinamento checklist** _(v1.8.1)_ - I widget rispettano l'opzione di ordinamento salvata
- ✅ **Separatori visivi** _(v1.8.1)_ - Tra elementi non spuntati e spuntati
- ✅ **Conservazione della tinta Monet** _(v1.9.0)_ - Lo sfondo traslucido mantiene i colori dinamici
- ✅ **Barra delle opzioni senza cuciture** _(v1.9.0)_ - Sfondo rimosso per un aspetto più pulito
- ✅ **Barratura nelle checklist** _(v1.9.0)_ - Gli elementi completati mostrano la barratura nel widget
- ✅ **Aggiornamento automatico in uscita** _(v1.9.0)_ - I widget si aggiornano quando esci dall'app
- ✅ **Dimensione carattere per widget** _(v2.8.0)_ - Dimensione del carattere indipendente per ogni widget
- ✅ **Opzioni widget elenco** _(v2.8.0)_ - Nascondi intestazione/fissate/cartelle, filtra per cartella
- ✅ **Icone sensibili al tema** _(v2.7.x)_ - Le icone della barra opzioni si adattano alla modalità chiara/scura

---

## 🌍 Supporto Multilingue _(NUOVO in v1.5.0)_

### Lingue supportate
12 lingue, mantenute dalla community su [Weblate](https://hosted.weblate.org/projects/simple-notes-sync/):
- ✅ **Inglese** (predefinita) · **Tedesco** · **Spagnolo** · **Francese** · **Indonesiano** · **Italiano** · **Norvegese Bokmål** · **Polacco** · **Russo** · **Turco** · **Ucraino** · **Cinese (Semplificato)**

### Selezione della lingua
- ✅ **Rilevamento automatico** - Segue la lingua di sistema
- ✅ **Selezione manuale** - Modificabile nelle impostazioni
- ✅ **Lingua per app** - Selezione nativa della lingua su Android 13+
- ✅ **locales_config.xml** - Integrazione Android completa

### Ambito
- ✅ **400+ stringhe** - Pienamente tradotte
- ✅ **Testi UI** - Tutti i pulsanti, i dialoghi, i menu
- ✅ **Messaggi di errore** - Suggerimenti localizzati utili
- ✅ **Impostazioni** - 7 schermate categorizzate

---

## 💾 Backup e Ripristino

### Sistema di backup locale
- ✅ **Esportazione JSON** - Tutte le note in un unico file
- ✅ **Libera scelta della posizione** - Download, scheda SD, cartella cloud
- ✅ **Nomi file con timestamp** - `simplenotes_backup_YYYY-MM-DD_HHmmss.json`
- ✅ **Esportazione completa** - Titolo, contenuto, timestamp, ID
- ✅ **Formato leggibile** - JSON formattato
- ✅ **Indipendente dal server** - Funziona completamente offline

### Modalità di ripristino
- ✅ **Unisci** - Aggiungi nuove note, mantieni quelle esistenti _(Predefinita)_
- ✅ **Sostituisci** - Elimina tutto e importa il backup
- ✅ **Sovrascrivi i duplicati** - Il backup vince sui conflitti di ID
- ✅ **Backup di sicurezza automatico** - Prima di ogni ripristino
- ✅ **Validazione del backup** - Controlla il formato e la versione
- ✅ **Gestione degli errori** - Messaggi di errore chiari su eventuali problemi

---

## 🖥️ Integrazione Desktop

### Esportazione Markdown
- ✅ **Esportazione automatica** - Ogni nota → file `.md`
- ✅ **Checklist come elenchi attività** _(NUOVO)_ - Formato `- [ ]` / `- [x]` (compatibile con GitHub)
- ✅ **Doppio formato** - JSON (master) + Markdown (specchio)
- ✅ **Sanificazione dei nomi file** - Nomi file sicuri dai titoli
- ✅ **Gestione dei duplicati** _(NUOVO)_ - Suffisso ID per titoli identici
- ✅ **Metadati frontmatter** - YAML con ID, timestamp, tipo
- ✅ **Sincronizzazione WebDAV** - In parallelo alla sincronizzazione JSON
- ✅ **Opzionale** - Attivabile nelle impostazioni
- ✅ **Esportazione iniziale** - Tutte le note esistenti al momento dell'attivazione
- ✅ **Indicatore di avanzamento** - Mostra X/Y durante l'esportazione

### Importazione Markdown
- ✅ **Desktop → App** - Importa le modifiche dal desktop
- ✅ **Last-Write-Wins** - Risoluzione dei conflitti tramite timestamp
- ✅ **Parsing del frontmatter** - Legge i metadati dai file `.md`
- ✅ **Rileva nuove note** - Adottate automaticamente nell'app
- ✅ **Rileva aggiornamenti** - Solo se la versione desktop è più recente
- ✅ **Tolleranza agli errori** - I singoli errori non interrompono l'importazione

### Accesso WebDAV
- ✅ **Montaggio come unità di rete** - Windows, macOS, Linux
- ✅ **Qualsiasi editor Markdown** - VS Code, Typora, Notepad++, iA Writer
- ✅ **Modifica dal vivo** - Accesso diretto ai file `.md`
- ✅ **Struttura delle cartelle** - `/notes/` per il JSON, `/notes-md/` per il Markdown
- ✅ **Creazione automatica delle cartelle** - Alla prima sincronizzazione

---

## 🔄 Sincronizzazione

### Sincronizzazione automatica
- ✅ **Selezione dell'intervallo** - 15, 30 o 60 minuti
- ✅ **Trigger WiFi** - Sincronizza alla connessione WiFi _(nessuna restrizione SSID)_
- ✅ **Rispettoso della batteria** - ~0,2-0,8% al giorno
- ✅ **Controllo intelligente del server** - Sincronizza solo quando il server è raggiungibile
- ✅ **WorkManager** - Esecuzione in background affidabile
- ✅ **Compatibile con l'ottimizzazione della batteria** - Funziona anche con la modalità Doze

### Trigger di sincronizzazione (6 in totale)
1. ✅ **Sincronizzazione periodica** - Automaticamente dopo l'intervallo
2. ✅ **Sincronizzazione all'avvio dell'app** - Quando si apre l'app
3. ✅ **Sincronizzazione alla connessione WiFi** - A qualsiasi connessione WiFi
4. ✅ **Sincronizzazione manuale** - Pulsante nelle impostazioni
5. ✅ **Pull-to-refresh** - Gesto di scorrimento nell'elenco note
6. ✅ **Sincronizzazione al salvataggio impostazioni** - Dopo la configurazione del server

### Meccanismo di sincronizzazione
- ✅ **Caricamento** - Modifiche locali sul server
- ✅ **Scaricamento** - Modifiche del server sull'app
- ✅ **Download paralleli** _(NUOVO in v1.8.0)_ - Fino a 5 download simultanei
- ✅ **Rilevamento dei conflitti** - Su modifiche simultanee
- ✅ **Unione senza conflitti** - Last-Write-Wins tramite timestamp
- ✅ **Rilevamento eliminazioni sul server** _(NUOVO in v1.8.0)_ - Rileva note eliminate su altri dispositivi
- ✅ **Tracciamento dello stato di sincronizzazione** - LOCAL_ONLY, PENDING, SYNCED, CONFLICT, DELETED_ON_SERVER
- ✅ **UI di avanzamento dal vivo** _(NUOVO in v1.8.0)_ - Indicatori di fase con contatori di caricamento/scaricamento
- ✅ **Gestione degli errori** - Riprova in caso di problemi di rete
- ✅ **Offline-first** - L'app funziona senza server

### Connessione al server
- ✅ **Protocollo WebDAV** - Protocollo standard
- ✅ **HTTP/HTTPS** - HTTP solo in locale, HTTPS per l'esterno
- ✅ **Nome utente/password** - Autenticazione di base
- ✅ **Test di connessione** - Test nelle impostazioni
- ✅ **Sincronizzazione solo WiFi** _(NUOVO in v1.7.0)_ - Opzione per sincronizzare solo su WiFi
- ✅ **Supporto VPN** _(NUOVO in v1.7.0)_ - La sincronizzazione funziona correttamente attraverso tunnel VPN
- ✅ **SSL self-signed** _(NUOVO in v1.7.0)_ - Supporto per certificati self-signed
- ✅ **Normalizzazione dell'URL del server** - `/notes/` e `/notes-md/` automatici _(NUOVO in v1.2.1)_
- ✅ **Input URL flessibile** - Entrambe le varianti funzionano: `http://server/` e `http://server/notes/`

---

## 🔒 Privacy e Sicurezza

### Self-Hosted
- ✅ **Server proprio** - Controllo totale dei dati
- ✅ **Nessun cloud** - Nessuna terza parte
- ✅ **Nessun tracciamento** - Nessuna analisi, nessuna telemetria
- ✅ **Nessun account** - Solo le credenziali del server
- ✅ **100% open source** - Licenza AGPL v3

### Sicurezza dei dati
- ✅ **Archiviazione locale** - Archivio privato dell'app (Android)
- ✅ **Crittografia WebDAV** - HTTPS per i server esterni
- ✅ **Credenziali crittografate** _(NUOVO in v2.3.0)_ - Credenziali WebDAV crittografate tramite il Keystore Android
- ✅ **Nessuna libreria di terze parti** - Solo Android SDK + implementazione WebDAV propria (dalla v2.14.0)

### Blocco app _(NUOVO in v2.10.0)_
- ✅ **Sblocco biometrico / credenziale del dispositivo** - Blocco opzionale basato su impronta digitale, viso o PIN del dispositivo
- ✅ **Periodo di grazia configurabile** - Scegli per quanto tempo l'app resta sbloccata in background prima di ri-bloccarsi
- ✅ **Protezione dagli screenshot** - `FLAG_SECURE` blocca screenshot/registrazioni dello schermo e nasconde il contenuto nella miniatura delle app recenti quando è bloccata

### Funzionalità per sviluppatori
- ✅ **Registrazione file** - Opzionale, solo se abilitata _(NUOVO in v1.3.2)_
- ✅ **Avviso sulla privacy** - Avviso esplicito all'attivazione
- ✅ **Log locali** - I log restano sul dispositivo

---

## 🔋 Prestazioni e Ottimizzazione

### Efficienza della batteria (v1.6.0)
- ✅ **Trigger di sincronizzazione configurabili** - Attiva/disattiva ogni trigger singolarmente
- ✅ **Predefiniti intelligenti** - Solo i trigger guidati da eventi sono attivi per impostazione predefinita
- ✅ **Intervalli periodici ottimizzati** - 15/30/60 min (predefinito: OFF)
- ✅ **Solo WiFi** - Nessuna sincronizzazione dati mobili
- ✅ **Controllo intelligente del server** - Sincronizza solo quando il server è raggiungibile
- ✅ **WorkManager** - Esecuzione ottimizzata dal sistema
- ✅ **Compatibile con la modalità Doze** - La sincronizzazione funziona anche in standby
- ✅ **Consumi misurati:**
  - Predefinito (solo eventi): ~0,2%/giorno (~6,5 mAh) ⭐ _Ottimale_
  - Con periodico 15 min: ~1,0%/giorno (~30 mAh)
  - Con periodico 30 min: ~0,6%/giorno (~19 mAh)
  - Con periodico 60 min: ~0,4%/giorno (~13 mAh)

### Prestazioni dell'app
- ✅ **Offline-first** - Funziona senza internet
- ✅ **Caricamento immediato** - Le note si caricano in <100ms
- ✅ **Scorrimento fluido** - LazyColumn con Compose
- ✅ **Material Design 3** - Interfaccia Android nativa
- ✅ **Kotlin Coroutines** - Operazioni asincrone
- ✅ **APK di piccole dimensioni** - ~5 MB (ottimizzato con R8/ProGuard)

---

## 🛠️ Dettagli tecnici

### Piattaforma
- ✅ **Android 7.0+** (API 24+)
- ✅ **Target SDK 36** (Android 16)
- ✅ **Kotlin** - Linguaggio di programmazione moderno
- ✅ **Jetpack Compose** - Framework UI dichiarativo
- ✅ **Material Design 3** - Linee guida di design più recenti
- ✅ **Sistema multi-tema** _(v2.0.0)_ - 7 schemi di colori inclusi AMOLED & Dynamic Color
- ✅ **Jetpack Glance** _(v1.8.0)_ - Framework per widget

### Architettura
- ✅ **MVVM-Light** - Architettura semplice
- ✅ **Single Activity** - Navigazione moderna
- ✅ **Kotlin Coroutines** - Modello Async/Await
- ✅ **Dispatchers.IO** - Operazioni in background
- ✅ **SharedPreferences** - Archiviazione delle impostazioni
- ✅ **Archiviazione basata su file** - File JSON in locale
- ✅ **Eccezioni personalizzate** - SyncException dedicata per una migliore gestione degli errori _(NUOVO in v1.3.2)_

### Dipendenze
- ✅ **AndroidX** - Librerie Jetpack
- ✅ **Material Components** - Material Design 3
- ✅ **Client WebDAV** - Implementazione propria in `sync/webdav/` (dalla v2.14.0)
- ✅ **Gson** - Serializzazione JSON
- ✅ **WorkManager** - Attività in background
- ✅ **OkHttp** - Client HTTP
- ✅ **Glance** _(v1.8.0)_ - Framework per widget

### Varianti di build
- ✅ **Standard** - APK universale (100% FOSS, nessuna dipendenza Google)
- ✅ **F-Droid** - Identica alla Standard (100% FOSS)
- ✅ **Debug/Release** - Sviluppo e produzione
- ✅ **Nessun servizio Google** - Completamente FOSS, nessuna libreria proprietaria

---

## 📦 Compatibilità Server

### Server WebDAV testati
- ✅ **Docker WebDAV** (consigliato per il self-hosting)
- ✅ **Nextcloud** - Pienamente compatibile
- ✅ **ownCloud** - Funziona perfettamente
- ✅ **Apache mod_dav** - WebDAV standard
- ✅ **nginx + WebDAV** - Con configurazione corretta

### Funzionalità del server
- ✅ **Basic Auth** - Nome utente/password
- ✅ **Elenco directory** - Per i download
- ✅ **PUT/GET** - Caricamento/scaricamento
- ✅ **MKCOL** - Creare cartelle
- ✅ **DELETE** - Eliminare note e cartelle vuote

---

## ℹ️ Informazioni sull'app

- ✅ **Changelog nell'app** _(NUOVO in v2.9.0)_ - Schermata changelog nativa, raggiungibile dalle Impostazioni
- ✅ **Pannello "Novità"** - Evidenziazioni localizzate dopo ogni aggiornamento, con pulsante "Vedi changelog"
- ✅ **Schermata contributori** _(NUOVO in v2.9.0)_ - Ringrazia tutti coloro che hanno contribuito al progetto

---

## 🔮 Funzionalità future

Previste per le prossime versioni – vedi [UPCOMING.it.md](UPCOMING.it.md) per la roadmap completa.

---

## 📊 Confronto con altre app

| Funzionalità | Simple Notes Sync | Google Keep | Nextcloud Notes |
|---------|------------------|-------------|-----------------|
| Offline-first | ✅ | ⚠️ Limitato | ⚠️ Limitato |
| Self-hosted | ✅ | ❌ | ✅ |
| Sincronizzazione automatica | ✅ | ✅ | ✅ |
| Esportazione Markdown | ✅ | ❌ | ✅ |
| Accesso desktop | ✅ (WebDAV) | ✅ (Web) | ✅ (Web + WebDAV) |
| Backup locale | ✅ | ❌ | ⚠️ Backup server |
| Nessun account Google | ✅ | ❌ | ✅ |
| Open Source | ✅ AGPL v3 | ❌ | ✅ AGPL |
| Dimensione APK | ~5 MB | ~50 MB | ~8 MB |
| Consumo batteria | ~0,4%/giorno | ~1-2%/giorno | ~0,5%/giorno |

---

## ❓ FAQ

**D: Mi serve un server?**  
R: No! L'app funziona completamente offline. Il server è opzionale per la sincronizzazione.

**D: Quale server è il migliore?**  
R: Per i principianti: Docker WebDAV (semplice, facile). Per i professionisti: Nextcloud (molte funzionalità).

**D: L'esportazione Markdown funziona senza l'Integrazione Desktop?**  
R: No, devi attivare la funzionalità nelle impostazioni.

**D: I miei dati andranno persi se cambio server?**  
R: No! Crea un backup locale, cambia server, ripristina.

**D: Perché JSON + Markdown?**  
R: Il JSON è affidabile e veloce (master). Il Markdown è leggibile dall'uomo (specchio per il desktop).

**D: Posso usare l'app senza Google Play?**  
R: Sì! Scarica l'APK direttamente da GitHub o usa F-Droid.

---

**Ultimo aggiornamento:** v2.9.0 (2026-06-22)