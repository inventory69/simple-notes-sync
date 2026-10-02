# Simple Notes Sync - Documentazione Tecnica

Questo file contiene informazioni tecniche dettagliate su implementazione, architettura e funzionalità avanzate.

**🌍 Lingue:** [Deutsch](DOCS.de.md) · **Italiano** · [English](DOCS.md)

---

## 📐 Architettura

### Panoramica generale

```
┌─────────────────┐
│  Android App    │
│  (Kotlin)       │
└────────┬────────┘
         │ WebDAV/HTTP
         │
┌────────▼────────┐
│  WebDAV Server  │
│  (Docker)       │
└─────────────────┘
```

### Architettura dell'app Android

```
app/
├── models/
│   ├── Note.kt              # Data class per le note
│   └── SyncStatus.kt        # Enum dello stato di sincronizzazione
├── storage/
│   ├── NotesStorage.kt      # Archiviazione locale in file JSON
│   ├── FolderStore.kt       # Definizioni e metadati delle cartelle
│   └── TrashManager.kt      # Gestione cestino / conservazione
├── noteimport/              # Procedura di importazione note (incl. Google Keep)
├── sync/
│   ├── WebDavSyncService.kt # Facciata di sincronizzazione (delega ai moduli)
│   ├── SyncGateChecker.kt   # Validazione pre-sincronizzazione
│   ├── ETagCache.kt         # Cache degli E-Tag
│   ├── SyncTimestampManager.kt # Tracciamento dei timestamp
│   ├── ConnectionManager.kt # Ciclo di vita della connessione HTTP
│   ├── NoteUploader.kt      # Logica di caricamento
│   ├── NoteDownloader.kt    # Logica di scaricamento
│   ├── MarkdownSyncManager.kt # Sincronizzazione bidirezionale Markdown
│   ├── FolderSyncManager.kt # Sincronizzazione cartelle ↔ sottodirectory
│   ├── NetworkMonitor.kt    # Rilevamento WiFi
│   ├── SyncWorker.kt        # Worker WorkManager in background
│   └── BootReceiver.kt      # Gestore riavvio del dispositivo
├── ui/
│   ├── main/                # Schermata principale (Compose)
│   ├── editor/              # Editor note (Compose)
│   ├── settings/            # Schermate impostazioni (Compose)
│   └── widget/              # Widget schermata Home (Glance)
└── utils/
    ├── Constants.kt         # Costanti dell'app
    ├── NotificationHelper.kt# Gestione notifiche
    └── Logger.kt            # Registrazione debug/release
```

---

## 🔄 Implementazione della Sincronizzazione Automatica

### Attività periodica WorkManager

La sincronizzazione automatica si basa su **WorkManager** con la seguente configurazione:

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.UNMETERED)  // Solo WiFi
    .build()

val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
    30, TimeUnit.MINUTES,  // Ogni 30 minuti
    10, TimeUnit.MINUTES   // Intervallo flessibile
)
    .setConstraints(constraints)
    .build()
```

**Perché WorkManager?**
- ✅ Funziona anche quando l'app è chiusa
- ✅ Riavvio automatico dopo il riavvio del dispositivo
- ✅ Efficiente per la batteria (gestito da Android)
- ✅ Esecuzione garantita quando i vincoli sono soddisfatti

### Rilevamento della rete

Usiamo il **Confronto dell'IP del gateway** per verificare se il server è raggiungibile:

```kotlin
fun isInHomeNetwork(): Boolean {
    val gatewayIP = getGatewayIP()         // ad es. 192.168.0.1
    val serverIP = extractIPFromUrl(serverUrl)  // ad es. 192.168.0.188
    
    return isSameNetwork(gatewayIP, serverIP)  // Controlla la rete /24
}
```

**Vantaggi:**
- ✅ Nessun permesso di localizzazione necessario
- ✅ Funziona con tutte le versioni di Android
- ✅ Affidabile e veloce

### Flusso di sincronizzazione

```
1. WorkManager si attiva (ogni 30 min)
   ↓
2. Controlla: WiFi connesso?
   ↓
3. Controlla: stessa rete del server?
   ↓
4. Carica le note locali
   ↓
5. Carica nuove/modificate note → Server
   ↓
6. Scarica le note remote ← Server
   ↓
7. Unisci e risolvi i conflitti
   ↓
8. Aggiorna l'archiviazione locale
   ↓
9. Mostra notifica (se ci sono modifiche)
```

---

## 🔄 Panoramica dei Trigger di Sincronizzazione

L'app usa **4 diversi trigger di sincronizzazione** con casi d'uso differenti:

| Trigger | File | Funzione | Quando? | Pre-controllo? |
|---------|------|----------|---------|------------|
| **1. Sincronizzazione manuale** | `ComposeMainActivity` | `triggerManualSync()` | L'utente tocca il pulsante di sync nel menu | ✅ Sì |
| **2. Auto-sync (onResume)** | `ComposeMainActivity` | `triggerAutoSync()` | App aperta/ripresa | ✅ Sì |
| **3. Sync in background (periodica)** | `SyncWorker.kt` | `doWork()` | Ogni 15/30/60 minuti (configurabile) | ✅ Sì |
| **4. Sync alla connessione WiFi** | `NetworkMonitor.kt` → `SyncWorker.kt` | `triggerWifiConnectSync()` | WiFi connesso | ✅ Sì |

### Controllo di raggiungibilità del server (pre-controllo)

**Tutti e 4 i trigger di sincronizzazione** eseguono un **pre-controllo** prima della sincronizzazione vera e propria:

```kotlin
// WebDavSyncService.kt - isServerReachable()
suspend fun isServerReachable(): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2000)  // Timeout 2 s
        }
        true
    } catch (e: Exception) {
        Logger.d(TAG, "Server not reachable: ${e.message}")
        false
    }
}
```

**Perché il controllo via Socket invece della richiesta HTTP?**
- ⚡ **Più veloce:** la connessione socket è istantanea, la richiesta HTTP è più lenta
- 🔋 **Efficiente per la batteria:** nessun overhead HTTP (header, handshake TLS, ecc.)
- 🎯 **Più preciso:** controlla solo la raggiungibilità di rete, non la logica del server
- 🛡️ **Previene errori:** rileva reti WiFi estranee prima che si verifichi un errore di sincronizzazione

**Quando fallisce il controllo?**
- ❌ Server offline/non raggiungibile
- ❌ Rete WiFi errata (ad es. WiFi del bar)
- ❌ Rete non ancora pronta (ritardo DHCP/routing dopo la connessione WiFi)
- ❌ La VPN blocca l'accesso al server
- ❌ Nessun URL del server WebDAV configurato

### Comportamento della sincronizzazione per tipo di trigger

| Trigger | Quando il server non è raggiungibile | Sincronizzazione riuscita | Limitazione |
|---------|--------------------------|---------------------------|------------|
| Sincronizzazione manuale | Toast: "Server non raggiungibile" | Toast: "✅ Sincronizzati: X note" | Nessuna |
| Auto-sync (onResume) | Interruzione silenziosa (niente toast) | Toast: "✅ Sincronizzati: X note" | Max. 1x/min |
| Sync in background | Interruzione silenziosa (niente toast) | Silenziosa (solo SharedFlow) | 15/30/60 min |
| Sync alla connessione WiFi | Interruzione silenziosa (niente toast) | Silenziosa (solo SharedFlow) | Basata su WiFi |

---

## 🔋 Ottimizzazione della Batteria

### v1.6.0: Trigger di Sincronizzazione Configurabili

Dalla v1.6.0 ogni trigger di sincronizzazione può essere attivato/disattivato individualmente. Questo dà agli utenti un controllo preciso sul consumo della batteria.

#### Panoramica dei trigger di sincronizzazione

| Trigger | Predefinito | Impatto sulla batteria | Descrizione |
|---------|---------|----------------|-------------|
| **Sincronizzazione manuale** | Sempre attivo | 0 (avviato dall'utente) | Pulsante barra strumenti / Pull-to-refresh |
| **Sync su salvataggio** | ✅ ATTIVO | ~0,5 mAh/salvataggio | Sincronizza subito dopo il salvataggio di una nota |
| **Sync su ripresa** | ✅ ATTIVO | ~0,3 mAh/ripresa | Sincronizza quando l'app viene aperta (limitazione 60 s) |
| **Connessione WiFi** | ✅ ATTIVO | ~0,5 mAh/connessione | Sincronizza quando il WiFi è connesso |
| **Sync periodica** | ❌ DISATTIVO | 0,2-0,8%/giorno | Sincronizzazione in background ogni 15/30/60 min |
| **Sync all'avvio** | ❌ DISATTIVO | ~0,1 mAh/avvio | Avvia la sincronizzazione in background dopo il riavvio |

#### Calcolo del consumo della batteria

**Scenario di utilizzo tipico (predefiniti):**
- Su salvataggio: ~5 salvataggi/giorno × 0,5 mAh = **~2,5 mAh**
- Su ripresa: ~10 aperture/giorno × 0,3 mAh = **~3 mAh**
- Connessione WiFi: ~2 connessioni/giorno × 0,5 mAh = **~1 mAh**
- **Totale: ~6,5 mAh/giorno (~0,2% con batteria da 3000 mAh)**

**Con sync periodica attivata (15/30/60 min):**

| Intervallo | Sync/giorno | Batteria/giorno | Totale (con predefiniti) |
|----------|-----------|-----------------|--------------------------|
| **15 min** | ~96 | ~23 mAh | ~30 mAh (~1,0%) |
| **30 min** | ~48 | ~12 mAh | ~19 mAh (~0,6%) |
| **60 min** | ~24 | ~6 mAh | ~13 mAh (~0,4%) |

#### Scomposizione dei componenti

| Componente | Frequenza | Consumo | Dettagli |
|-----------|-----------|---------|----------|
| Attivazione WorkManager | Per sync | ~0,15 mAh | Il sistema si attiva |
| Controllo di rete | Per sync | ~0,03 mAh | Controllo IP gateway |
| Sincronizzazione WebDAV | Solo se ci sono modifiche | ~0,25 mAh | HTTP PUT/GET |
| **Totale per sync** | - | **~0,25 mAh** | Ottimizzato |

### Ottimizzazioni

1. **Pre-controlli prima della sincronizzazione**
   ```kotlin
   // L'ordine conta! Prima i controlli più economici
   if (!hasUnsyncedChanges()) return  // Controllo locale (economico)
   if (!isServerReachable()) return   // Controllo di rete (costoso)
   performSync()                       // Solo se entrambi passano
   ```

2. **Limitazione**
   - Su ripresa: intervallo minimo di 60 secondi
   - Su salvataggio: intervallo minimo di 5 secondi
   - Periodica: intervalli di 15/30/60 minuti

3. **Cache IP**
   ```kotlin
   private var cachedServerIP: String? = null
   // Risoluzione DNS solo una volta all'avvio, non a ogni controllo
   ```

4. **Registrazione condizionale**
   ```kotlin
   object Logger {
       fun d(tag: String, msg: String) {
           if (BuildConfig.DEBUG) Log.d(tag, msg)
       }
   }
   ```

5. **Vincoli di rete**
   - Solo WiFi (non dati mobili)
   - Solo quando il server è raggiungibile
   - Nessun listener permanente

---

## 📦 Dettagli della Sincronizzazione WebDAV

### Flusso di caricamento

```kotlin
suspend fun uploadNotes(): Int {
    val localNotes = storage.loadAllNotes()
    var uploadedCount = 0
    
    for (note in localNotes) {
        if (note.syncStatus == SyncStatus.PENDING) {
            val jsonContent = note.toJson()
            val remotePath = "$serverUrl/${note.id}.json"
            
            // v2.16.0: prima un PROPFIND per ogni cartella — se l'E-Tag del
            // server non corrisponde più a quello in cache, questo è un conflitto
            // e nulla viene scritto. If-Match funziona come secondo livello
            // (vedi Risoluzione dei conflitti).
            webdav.put(remotePath, jsonContent.toByteArray(), "application/json", ifMatch)
            
            storage.saveNote(note.copy(syncStatus = SyncStatus.SYNCED))
            uploadedCount++
        }
    }
    
    return uploadedCount
}
```

### Flusso di scaricamento

```kotlin
suspend fun downloadNotes(): DownloadResult {
    val remoteFiles = webdav.list(serverUrl)
    var downloadedCount = 0
    var conflictCount = 0
    
    for (file in remoteFiles) {
        if (!file.name.endsWith(".json")) continue
        
        val content = webdav.get(file.href)
        val remoteNote = Note.fromJson(content)
        val localNote = storage.loadNote(remoteNote.id)
        
        if (localNote == null) {
            // Nuova nota dal server
            storage.saveNote(remoteNote)
            downloadedCount++
        } else if (localNote.updatedAt < remoteNote.updatedAt) {
            // Il server ha la versione più recente. Vince solo se la copia locale
            // non contiene una modifica non caricata — altrimenti è un conflitto
            // (vedi sotto).
            if (localNote.syncStatus.holdsLocalEdit) {
                storage.saveNote(localNote.copy(syncStatus = SyncStatus.CONFLICT))
                conflictCount++
            } else {
                storage.saveNote(remoteNoteFoldered.copy(syncStatus = SyncStatus.SYNCED))
                downloadedCount++
            }
        }
    }
    
    return DownloadResult(downloadedCount, conflictCount)
}
```

### Risoluzione dei Conflitti

Strategia: **Last-Write-Wins**, tranne quando si perderebbe una modifica locale. Non esiste
unione automatica né copia di conflitto — entrambe sono scelte deliberate, vedi *Non implementato* sotto.

Un conflitto viene rilevato in due punti:

**1. In caricamento (`NoteUploader`).** Dalla v2.16.0, su due livelli.

Il livello che applica davvero la protezione è un **PROPFIND prima che il primo byte venga scritto**.
Per ogni cartella che contiene una nota da caricare *con* un E-Tag in cache, l'uploader recupera
gli E-Tag correnti dal server e li confronta lui stesso. Una discrepanza significa che la copia sul
server è cambiata dall'ultima volta che questo dispositivo l'ha vista — la nota viene marcata come
conflitto e nessun `PUT` avviene:

```kotlin
// NoteUploader.checkPreconditions()
if (isStaleAgainstServer(note, cachedETag, serverSnapshot)) {
    return markConflict(note, storageMutex, why = "server_etag_changed")
}
```

Questo deve avvenire lato client perché non deve dipendere da una funzionalità del server: il server
consigliato in [`server/README.md`](../server/README.md) (hacdias/webdav su
`golang.org/x/net/webdav`) **non** valuta le precondizioni di scrittura — un `PUT` con un
`If-Match` sbagliato risponde `201` e sovrascrive. Misurato per la v2.16.0. Non far mai più
dipendere la protezione dai conflitti dal solo `If-Match`.

Il secondo livello è quella precondizione `If-Match`, inviata con il `PUT` e attiva sui server che la
onorano (sabre/dav: Nextcloud, ownCloud, Baïkal). Chiude la finestra di gara tra il PROPFIND
e il `PUT`:

```kotlin
// NoteUploader.uploadSingle()
val putEtag = try {
    putWithPrecondition(webdav, noteUrl, jsonBytes, cachedETag)
} catch (e: WebDavException) {
    if (e.statusCode == 412) return markConflict(note, storageMutex, why = "if_match_412")
    throw e
}
```

Un server che non sa valutare `If-Match` (`400`/`501`) riceve un solo tentativo senza la precondizione,
e questo viene ricordato nella configurazione del server — il livello PROPFIND continua a proteggerlo,
e un dispositivo che non riuscisse più a caricare affatto sarebbe peggio.

Costo: un PROPFIND per ogni cartella che ha qualcosa da caricare con un E-Tag in cache. Una
sincronizzazione senza modifiche non arriva mai a questo punto, e un primo caricamento di nuove note
non elenca nulla — senza E-Tag in cache non c'è nulla da confrontare.

**2. In scaricamento (`NoteDownloader`).** La copia sul server è più recente *e* la nota locale
contiene ancora una modifica mai arrivata al server (`PENDING` o `CONFLICT`). La versione locale
viene mantenuta e marcata, la copia scaricata viene scartata.

**Cosa fa una nota marcata.** Nulla, di proposito. L'uploader prende solo `LOCAL_ONLY` e
`PENDING`, quindi non viene mai inviata; dalla v2.16.0 il downloader non la sovrascrive più
(prima, la marcatura sopravviveva esattamente un ciclo di sincronizzazione e poi la versione locale
veniva silenziosamente sostituita). La nota resta fuori sincronizzazione finché non decide una persona.
Per ogni sincronizzazione che rileva conflitti viene inviata una notifica, e la nota porta un'icona
di avviso nell'elenco.

**Risolverlo.** Aprire la nota mostra un banner nell'editor con le due opzioni — la stessa coppia
che il client desktop offre come `resolve_conflict(id, "keep_mine" | "use_server")`:

| Azione | Cosa succede (`SyncConflictResolver`) |
|---|---|
| **Tieni la mia** | E-Tag in cache e hash del contenuto vengono eliminati, la nota torna a `PENDING`. Il prossimo caricamento parte senza precondizione e vince. |
| **Usa la versione del server** | La copia sul server viene recuperata con un singolo `GET`, sostituisce la nota locale come `SYNCED` e viene caricata nell'editor aperto. |

#### Non implementato (deliberatamente)

- **Nessuna copia di conflitto.** Le prime revisioni di questo documento descrivevano una
  `resolveConflict()` che salvava la versione remota accanto a quella locale come "… (Conflitto)".
  Una funzione del genere non è mai esistita nel pacchetto `sync/`.
- **Nessun merge a tre vie a livello di riga.** Richiederebbe una revisione base comune, cioè una
  cronologia delle versioni sul server, e produrrebbe comunque risultati che nessuno vuole per il
  testo in prosa. Mantenere una versione e lasciar scegliere una persona risolve lo stesso problema
  a una frazione del costo.

---

## 🔔 Notifiche

### Canali di notifica

```kotlin
val channel = NotificationChannel(
    "notes_sync_channel",
    "Sincronizzazione Note",
    NotificationManager.IMPORTANCE_DEFAULT
)
```

### Notifica di successo

```kotlin
fun showSyncSuccess(context: Context, count: Int) {
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAGS)
    
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("Sincronizzazione riuscita")
        .setContentText("$count note sincronizzate")
        .setContentIntent(pendingIntent)  // Il tocco apre l'app
        .setAutoCancel(true)              // Si chiude al tocco
        .build()
    
    notificationManager.notify(NOTIFICATION_ID, notification)
}
```

---

## 🛡️ Permessi

L'app richiede **permessi minimi**:

```xml
<!-- Rete -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- Notifiche -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Boot Receiver -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Ottimizzazione batteria (opzionale) -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

**Nessun permesso di localizzazione!**  
Usiamo il confronto dell'IP del gateway invece del rilevamento SSID. Nessun permesso di localizzazione richiesto.

---

## 🧪 Test

### Server di test

```bash
# Il server WebDAV è raggiungibile?
curl -u noteuser:password http://192.168.0.188:8080/

# Carica un file
echo '{"test":"data"}' > test.json
curl -u noteuser:password -T test.json http://192.168.0.188:8080/test.json

# Scarica un file
curl -u noteuser:password http://192.168.0.188:8080/test.json
```

### Test dell'app Android

**Test unitari:**
```bash
cd android
./gradlew test
```

**Test strumentati:**
```bash
./gradlew connectedAndroidTest
```

**Checklist di test manuale:**

- [ ] Crea nota → visibile nell'elenco
- [ ] Modifica nota → le modifiche vengono salvate
- [ ] Elimina nota → rimossa dall'elenco
- [ ] Sync manuale → stato del server "Raggiungibile"
- [ ] Auto-sync → notifica dopo ~30 min
- [ ] Chiudi l'app → l'auto-sync continua
- [ ] Riavvio del dispositivo → l'auto-sync parte automaticamente
- [ ] Server offline → notifica di errore
- [ ] Tocco sulla notifica → l'app si apre

---

## 🚀 Build e Distribuzione

### Build Debug

```bash
cd android
./gradlew assembleFdroidDebug
# APK: app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

### Build Release

```bash
./gradlew assembleFdroidRelease
# APK: app/build/outputs/apk/fdroid/release/app-fdroid-release.apk
```

### Firma (per la Distribuzione)

```bash
# Crea il keystore
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias

# Firma l'APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore my-release-key.jks \
  app-release-unsigned.apk my-alias

# Ottimizza
zipalign -v 4 app-release-unsigned.apk app-release.apk
```

---

## 🐛 Debugging

### Filtro LogCat

```bash
# Solo i log dell'app
adb logcat -s SimpleNotesApp NetworkMonitor SyncWorker WebDavSyncService

# Con timestamp
adb logcat -v time -s SyncWorker

# Salva su file
adb logcat -s SyncWorker > sync_debug.log
```

### Problemi comuni

**Problema: l'auto-sync non funziona**
```
Soluzione: disattiva l'ottimizzazione della batteria
Impostazioni → App → Simple Notes → Batteria → Non ottimizzare
```

**Problema: server non raggiungibile**
```
Controlla:
1. Il server è in esecuzione? → docker compose ps
2. L'IP è corretto? → ip addr show
3. La porta è aperta? → telnet 192.168.0.188 8080
4. Firewall? → sudo ufw allow 8080
```

**Problema: le notifiche non compaiono**
```
Controlla:
1. Il permesso di notifica è concesso?
2. Non disturbare attivo?
3. L'app in background? → Forza stop e riavvia
```

---

## 📚 Dipendenze

```gradle
// Kotlin 2.3.20

// Core
androidx.core:core-ktx:1.18.0
androidx.appcompat:appcompat:1.7.1
com.google.android.material:material:1.14.0

// Jetpack Compose (BOM) — incl. Glance per i widget
androidx.compose:compose-bom:2026.05.01

// Lifecycle
androidx.lifecycle:lifecycle-runtime-ktx:2.10.0

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0

// WorkManager
androidx.work:work-runtime-ktx:2.11.2

// JSON
com.google.code.gson:gson:2.14.0

// Client WebDAV: implementazione propria dalla v2.14.0 (sync/webdav/, ~620 righe).
// La dipendenza sardine-android è stata rimossa con quella release.
// Trasporto HTTP: com.squareup.okhttp3:okhttp + com.burgstaller:okhttp-digest
```

---

## 🔮 Roadmap

Vedi [UPCOMING.it.md](UPCOMING.it.md) per la roadmap completa e le funzionalità previste.

---

## 📖 Ulteriore Documentazione

- [Project Docs](https://github.com/inventory69/project-docs/tree/main/simple-notes-sync)
- [Sync Architecture](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/SYNC_ARCHITECTURE.md) - **Documentazione dettagliata dei trigger di sincronizzazione**
- [Android Guide](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/ANDROID_GUIDE.md)
- [Bugfix Documentation](https://github.com/inventory69/project-docs/blob/main/simple-notes-sync/BUGFIX_SYNC_SPAM_AND_NOTIFICATIONS.md)

---

**Ultimo aggiornamento:** giugno 2026