# Backup e Ripristino 💾

**🌍 Lingue:** [Deutsch](BACKUP.de.md) · **Italiano** · [English](BACKUP.md)

> Proteggi le tue note localmente - indipendentemente dal server

---

## 📋 Panoramica

Il sistema di backup funziona **completamente offline** e indipendente dal server WebDAV. Perfetto per:
- 📥 Backup regolari
- 📤 Migrazione verso un nuovo server
- 🔄 Recupero dopo una perdita di dati
- 💾 Archiviazione di vecchie note

---

## 📥 Creare un backup

### Passo dopo passo

1. **Apri le impostazioni** (icona ⚙️ in alto a destra)
2. **Trova la sezione "Backup e Ripristino"**
3. **Tocca "📥 Crea backup"**
4. **Scegli la posizione:**
   - 📁 Download
   - 💳 Scheda SD
   - ☁️ Cartella cloud (Nextcloud, Google Drive, ecc.)
   - 📧 Email come allegato
5. **Fatto!** Il file di backup è stato salvato

### Formato del file

**Nome file:** `simplenotes_backup_YYYY-MM-DD_HHmmss.json`

**Esempio:** `simplenotes_backup_2026-01-05_143022.json`

**Contenuto:**
```json
{
  "version": "1.2.1",
  "exported_at": "2026-01-05T14:30:22Z",
  "notes_count": 42,
  "notes": [
    {
      "id": "abc-123-def",
      "title": "Lista della spesa",
      "content": "Latte\nPane\nFormaggio",
      "createdAt": 1704467422000,
      "updatedAt": 1704467422000
    }
  ]
}
```

**Dettagli del formato:**
- ✅ Leggibile dall'uomo (JSON formattato)
- ✅ Dati completi delle note (titolo, contenuto, ID, timestamp, colore, stato di fissaggio, cartella, elementi checklist)
- ✅ Impostazioni dell'app e metadati delle cartelle inclusi _(v2.0.0+ / v2.8.0)_
- ✅ Informazioni sulla versione per la compatibilità
- ✅ Conteggio delle note per la validazione

---

## 📤 Ripristinare un backup

### 3 Modalità di ripristino

#### 1. Unisci ⭐ _Consigliata_

**Cosa succede:**
- ✅ Le nuove note del backup vengono aggiunte
- ✅ Le note esistenti rimangono invariate
- ✅ Nessuna perdita di dati

**Quando usarla:**
- Importare un backup da un altro dispositivo
- Recuperare vecchie note
- Ripristinare note eliminate per errore

**Esempio:**
```
App:     [Nota A, Nota B, Nota C]
Backup:  [Nota A, Nota D, Nota E]
Risultato: [Nota A, Nota B, Nota C, Nota D, Nota E]
```

#### 2. Sostituisci

**Cosa succede:**
- ❌ TUTTE le note esistenti vengono eliminate
- ✅ Le note del backup vengono importate
- ⚠️ Irreversibile (tranne attraverso il backup automatico)

**Quando usarla:**
- Migrazione del server (ripartenza completa)
- Ritorno a un vecchio stato di backup
- Reinstallazione dell'app

**Esempio:**
```
App:     [Nota A, Nota B, Nota C]
Backup:  [Nota X, Nota Y]
Risultato: [Nota X, Nota Y]
```

**⚠️ Attenzione:** viene creato automaticamente un backup di sicurezza!

#### 3. Sovrascrivi i duplicati

**Cosa succede:**
- ✅ Le nuove note del backup vengono aggiunte
- 🔄 In caso di conflitto di ID, vince il backup
- ✅ Le altre note rimangono invariate

**Quando usarla:**
- Il backup è più recente dei dati dell'app
- Importare modifiche dal desktop
- Risoluzione dei conflitti

**Esempio:**
```
App:     [Nota A (v1), Nota B, Nota C]
Backup:  [Nota A (v2), Nota D]
Risultato: [Nota A (v2), Nota B, Nota C, Nota D]
```

### Procedura di ripristino

1. **Impostazioni** → **"📤 Ripristina da file"**
2. **Seleziona il file di backup** (`.json`)
3. **Scegli la modalità:**
   - 🔵 Unisci _(Predefinita)_
   - 🟡 Sovrascrivi i duplicati
   - 🔴 Sostituisci _(Attenzione!)_
4. **Conferma** - Viene creato automaticamente un backup di sicurezza
5. **Attendi** - L'importazione è in corso
6. **Fatto!** - Messaggio di successo con il numero di note importate

---

## 🛡️ Backup di sicurezza automatico

**Prima di ogni ripristino:**
- ✅ Viene creato automaticamente un backup
- 📁 Salvato in: `Android/data/dev.dettmer.simplenotes/files/`
- 🏷️ Nome file: `auto_backup_before_restore_YYYY-MM-DD_HHmmss.json`
- ⏱️ Timestamp: immediatamente prima del ripristino

**Perché?**
- Protezione contro una "Sostituzione" accidentale
- Possibilità di annullare
- Doppia sicurezza

**Accesso tramite il gestore file:**
```
/Android/data/dev.dettmer.simplenotes/files/auto_backup_before_restore_*.json
```

---

## 💡 Migliori pratiche

### Strategia di backup

#### Backup regolari
```
Giornaliero:  ❌ Troppo frequente (la sincronizzazione del server è sufficiente)
Settimanale:  ✅ Consigliato per note importanti
Mensile:      ✅ Archiviazione
Prima degli aggiornamenti: ✅ Sicurezza
```

#### Regola 3-2-1
1. **3 copie** - Originale + 2 backup
2. **2 supporti** - ad es. scheda SD + cloud
3. **1 copia fuori sede** - ad es. archiviazione cloud

### Posizioni dei backup

**Locale (rapido):**
- 📱 Memoria interna / Download
- 💳 Scheda SD
- 🖥️ PC (tramite USB)

**Cloud (sicuro):**
- ☁️ Nextcloud (self-hosted)
- 📧 Email a te stesso
- 🗄️ Syncthing (sincronizzazione tra dispositivi)

**⚠️ Da evitare:**
- ❌ Google Drive / Dropbox (privacy)
- ❌ Una sola copia
- ❌ Solo sul server (se il server fallisce)

---

## 🔧 Utilizzo avanzato

### Modificare il file di backup

Il file `.json` può essere modificato con qualsiasi editor di testo:

1. **Apri con:** VS Code, Notepad++, nano
2. **Aggiungi/rimuovi note**
3. **Cambia titolo/contenuto**
4. **Regola gli ID** (per la migrazione)
5. **Salva** e importa nell'app

**⚠️ Importante:**
- Mantieni un formato JSON valido
- Gli ID devono essere univoci (UUID)
- Timestamp in millisecondi (epoch Unix)

### Importazione in blocco

Unisci più backup:

1. Importa backup 1 (Modalità: Unisci)
2. Importa backup 2 (Modalità: Unisci)
3. Importa backup 3 (Modalità: Unisci)
4. Risultato: tutte le note combinate

### Migrazione del server

Passo dopo passo:

1. **Crea un backup** sul vecchio server
2. **Configura il nuovo server** (vedi [QUICKSTART.md](../QUICKSTART.md))
3. **Cambia l'URL del server** nelle impostazioni dell'app
4. **Ripristina il backup** (Modalità: Sostituisci)
5. **Testa la sincronizzazione** - Tutte le note sul nuovo server

---

## ❌ Risoluzione dei problemi

### "File di backup non valido"

**Cause:**
- File JSON danneggiato
- Estensione del file errata (deve essere `.json`)
- Versione dell'app non compatibile

**Soluzione:**
1. Verifica il file JSON con un validatore (ad es. jsonlint.com)
2. Verifica l'estensione del file
3. Crea un backup con la versione corrente dell'app

### "Nessun permesso per salvare"

**Cause:**
- Permesso di archiviazione mancante
- Cartella protetta da scrittura

**Soluzione:**
1. Android: Impostazioni → App → Simple Notes → Permessi
2. Attiva "Archiviazione"
3. Scegli una posizione diversa

### "Importazione fallita"

**Cause:**
- Spazio di archiviazione insufficiente
- File di backup danneggiato
- L'app si è bloccata durante l'importazione

**Soluzione:**
1. Libera spazio di archiviazione
2. Crea un nuovo file di backup
3. Riavvia l'app e riprova

---

## 🔒 Sicurezza e privacy

### Protezione dei dati
- ✅ **Archiviazione locale** - Nessun caricamento sul cloud senza la tua azione
- ✅ **Crittografia opzionale** _(v1.7.0+)_ - Proteggi i file di backup con password
- ✅ **Leggibile** - Formato JSON semplice quando non crittografato
- ⚠️ **Dati sensibili?** - Attiva la crittografia o usa strumenti esterni (ad es. 7-Zip)

### Raccomandazioni
- 🔐 Conserva i file di backup in un contenitore crittografato
- 🗑️ Elimina regolarmente i vecchi backup
- 📧 Non inviarli tramite email non crittografata
- ☁️ Usa un cloud self-hosted (Nextcloud)

---

## 📊 Dettagli tecnici

### Specifica del formato

**Struttura JSON:**
```json
{
  "version": "string",        // Versione dell'app al momento dell'esportazione
  "exported_at": "ISO8601",   // Timestamp dell'esportazione
  "notes_count": number,      // Numero di note
  "notes": [
    {
      "id": "UUID",           // ID univoco
      "title": "string",      // Titolo della nota
      "content": "string",    // Contenuto della nota
      "createdAt": number,    // Timestamp Unix (ms)
      "updatedAt": number     // Timestamp Unix (ms)
    }
  ]
}
```

### Compatibilità
- ✅ v1.2.0+ - Pienamente compatibile
- ⚠️ v1.1.x - Funzioni di base (senza backup automatico)
- ❌ v1.0.x - Non supportato

---

**📚 Vedi anche:**
- [QUICKSTART.md](../QUICKSTART.md) - Installazione e configurazione dell'app
- [FEATURES.it.md](FEATURES.it.md) - Elenco completo delle funzionalità
- [DESKTOP.it.md](DESKTOP.it.md) - Integrazione desktop con Markdown

**Ultimo aggiornamento:** v2.8.0 (2026-06-16)