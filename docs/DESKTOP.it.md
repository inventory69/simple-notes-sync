# Integrazione Desktop 🖥️

**🌍 Lingue:** [Deutsch](DESKTOP.de.md) · **Italiano** · [English](DESKTOP.md)

> Modifica le tue note con qualsiasi editor Markdown sul desktop

---

## 📋 Panoramica

L'integrazione desktop ti permette di modificare le note su PC/Mac:
- 📝 Funziona con qualsiasi editor Markdown
- 🔄 Sincronizzazione automatica tramite WebDAV
- 💾 Doppio formato: JSON (master) + Markdown (specchio)
- ⚡ Risoluzione dei conflitti Last-Write-Wins

---

## 🎯 Perché Markdown?

### Architettura a doppio formato

```
┌─────────────────────────────────────┐
│         Android App                 │
│                                     │
│  ┌──────────┐      ┌─────────────┐ │
│  │   JSON   │ ──→  │  Markdown   │ │
│  │ (Master) │      │  (Mirror)   │ │
│  └──────────┘      └─────────────┘ │
└────────┬────────────────┬───────────┘
         │                │
         ↓                ↓
    WebDAV Server
         │                │
    ┌────┴────┐      ┌────┴──────┐
    │ /notes/ │      │ /notes-md/│
    │ *.json  │      │ *.md      │
    └─────────┘      └───────────┘
         ↑                ↑
         │                │
    ┌────┴────────────────┴───────────┐
    │      Desktop Editor             │
    │  (VS Code, Typora, ecc.)        │
    └──────────────────────────────────┘
```

### Vantaggi

**JSON (Master):**
- ✅ Affidabile e veloce
- ✅ Dati strutturati (ID, timestamp)
- ✅ Meccanismo di sincronizzazione primario
- ✅ Sempre attivo

**Markdown (Specchio):**
- ✅ Leggibile dall'uomo
- ✅ Compatibile con gli editor desktop
- ✅ Evidenziazione della sintassi
- ✅ Attivabile opzionalmente

---

## 🚀 Avvio rapido

### 1. Prima sincronizzazione

**Importante:** esegui una sincronizzazione PRIMA di attivare l'integrazione desktop!

1. **Configura l'app** (vedi [QUICKSTART.md](../QUICKSTART.md))
2. **Testa la connessione al server**
3. **Crea la prima nota**
4. **Sincronizza** (pull-to-refresh o sincronizzazione automatica)
5. ✅ Il server crea automaticamente le cartelle `/notes/` e `/notes-md/`

### 2. Attiva l'integrazione desktop

1. **Impostazioni** → **Integrazione Desktop**
2. **Attiva l'interruttore**
3. **Inizia l'esportazione iniziale** - Mostra lo stato di avanzamento (X/Y)
4. ✅ Tutte le note esistenti vengono esportate come `.md`

### 3. Monta WebDAV come unità di rete

#### Windows

```
1. Apri Esplora file
2. Tasto destro su "Questo PC"
3. "Connetti a unità di rete"
4. Inserisci l'URL: http://TUO-SERVER:8080/notes-md/
5. Nome utente: noteuser
6. Password: (la tua password WebDAV)
7. Lettera di unità: Z:\ (o qualsiasi)
8. Fatto!
```

**Accesso:** `Z:\` in Esplora file

#### macOS

```
1. Apri Finder
2. Menu "Vai" → "Connetti al server" (⌘K)
3. Indirizzo del server: http://TUO-SERVER:8080/notes-md/
4. Connetti
5. Nome utente: noteuser
6. Password: (la tua password WebDAV)
7. Fatto!
```

**Accesso:** Finder → Rete → notes-md

#### Linux (GNOME)

```
1. Apri File / Nautilus
2. "Altre posizioni"
3. "Connetti al server"
4. Indirizzo del server: dav://TUO-SERVER:8080/notes-md/
5. Nome utente: noteuser
6. Password: (la tua password WebDAV)
7. Fatto!
```

**Accesso:** `/run/user/1000/gvfs/dav:host=...`

#### Linux (davfs2 - permanente)

```bash
# Installazione
sudo apt install davfs2

# Crea il punto di montaggio
sudo mkdir -p /mnt/notes-md

# Monta una volta
sudo mount -t davfs http://TUO-SERVER:8080/notes-md/ /mnt/notes-md

# Permanente in /etc/fstab
echo "http://TUO-SERVER:8080/notes-md/ /mnt/notes-md davfs rw,user,noauto 0 0" | sudo tee -a /etc/fstab
```

**Accesso:** `/mnt/notes-md/`

---

## 📝 Editor Markdown

### Editor consigliati

#### 1. VS Code ⭐ _Consigliato_

**Vantaggi:**
- ✅ Gratuito e open source
- ✅ Anteprima Markdown (Ctrl+Shift+V)
- ✅ Evidenziazione della sintassi
- ✅ Integrazione Git
- ✅ Estensioni (controllo ortografico, ecc.)

**Configurazione:**
```
1. Installa VS Code
2. Monta l'unità WebDAV
3. Apri la cartella: Z:\notes-md\ (Windows) o /mnt/notes-md (Linux)
4. Fatto! Modifica i file Markdown
```

**Estensioni (opzionali):**
- `Markdown All in One` - Scorciatoie e anteprima
- `Markdown Preview Enhanced` - Anteprima migliore
- `Code Spell Checker` - Controllo ortografico

#### 2. Typora

**Vantaggi:**
- ✅ Editor Markdown WYSIWYG
- ✅ Design minimalista
- ✅ Anteprima dal vivo
- ⚠️ A pagamento (~15€)

**Configurazione:**
```
1. Installa Typora
2. Monta il WebDAV
3. Apri la cartella in Typora
4. Modifica le note
```

#### 3. Notepad++

**Vantaggi:**
- ✅ Leggero
- ✅ Veloce
- ✅ Evidenziazione della sintassi
- ⚠️ Nessuna anteprima Markdown

**Configurazione:**
```
1. Installa Notepad++
2. Monta il WebDAV
3. Apri i file direttamente
```

#### 4. Obsidian

**Vantaggi:**
- ✅ Filosofia del secondo cervello
- ✅ Vista grafica per i collegamenti
- ✅ Molti plugin
- ⚠️ Possibili conflitti di sincronizzazione (2 master)

**Configurazione:**
```
1. Installa Obsidian
2. Apri WebDAV come vault
3. Attenzione: Obsidian crea i propri metadati!
```

**⚠️ Non consigliato:** può modificare il frontmatter

---

## 📄 Formato dei file Markdown

### Struttura

Ogni nota viene esportata come file `.md` con frontmatter YAML:

```markdown
---
id: abc-123-def-456
created: 2026-01-05T14:30:22Z
updated: 2026-01-05T14:30:22Z
tags: []
---

# Titolo della nota

Contenuto della nota...
```

### Campi del frontmatter

| Campo | Tipo | Descrizione | Obbligatorio |
|-------|------|-------------|----------|
| `id` | UUID | ID univoco della nota | ✅ Sì |
| `created` | ISO8601 | Data di creazione | ✅ Sì |
| `updated` | ISO8601 | Data di modifica | ✅ Sì |
| `tags` | Array | Tag (futuro) | ❌ No |

### Nomi dei file

**Regole di sanificazione:**
```
Titolo: "La mia lista della spesa 🛒"
→ Nome file: "La_mia_lista_della_spesa.md"

Rimossi:
- Emoji: 🛒 → rimossa
- Caratteri speciali: / \ : * ? " < > | → rimossi
- Spazi multipli → spazio singolo
- Spazi → trattino basso _
```

**Esempi:**
```
"Appunti riunione 2026" → "Appunti_riunione_2026.md"
"To-Do: Progetto" → "To-Do_Progetto.md"
"Vacanze ☀️" → "Vacanze.md"
```

---

## 🔄 Sincronizzazione

### Flusso di lavoro: Android → Desktop

1. **Crea/modifica una nota nell'app**
2. **Esegui la sincronizzazione** (automatica o manuale)
3. **Il JSON viene caricato** (`/notes/abc-123.json`)
4. **Il Markdown viene esportato** (`/notes-md/Titolo_Nota.md`) _(solo se l'Integrazione Desktop è ATTIVA)_
5. **L'editor desktop mostra le modifiche** (dopo l'aggiornamento)

> 📁 **Cartelle** _(v2.7.0)_ - Le note all'interno di una cartella vengono sincronizzate in una sottocartella corrispondente, ad es. `/notes/Lavoro/abc-123.json` e `/notes-md/Lavoro/Titolo_Nota.md`. Le cartelle marcate **solo locali** restano sul dispositivo e non vengono mai caricate.

### Flusso di lavoro: Desktop → Android

1. **Modifica il file Markdown** (nella cartella montata)
2. **Salva** - Il file è immediatamente sul server
3. **Nell'app: esegui l'importazione Markdown**
   - Impostazioni → "Importa modifiche Markdown"
   - Oppure: importazione automatica a ogni sincronizzazione (futuro)
4. **L'app adotta le modifiche** (sela versione desktop è più recente)

### Risoluzione dei conflitti: Last-Write-Wins

**Regola:** vince la versione più recente (per timestamp `updated`)

**Esempio:**
```
Versione app:     updated: 2026-01-05 14:00
Versione desktop: updated: 2026-01-05 14:30
→ Vince il desktop (timestamp più recente)
```

**Automatico:**
- ✅ All'importazione Markdown
- ⚠️ Nessuna unione dei conflitti - solo sovrascrittura completa

**Eccezione — sincronizzazione JSON dalla v2.16.0.** Last-Write-Wins non si applica più al master JSON
quando ti farebbe perdere una modifica. Se la nota è cambiata anche sul server dall'ultima volta che questo
dispositivo l'ha vista, la sincronizzazione segnala un conflitto invece di sovrascrivere e ti chiede di scegliere
una versione. Vedi [Risoluzione dei conflitti](DOCS.md#conflict-resolution). Lo specchio Markdown è ancora
Last-Write-Wins semplice - è uno specchio, non un master.

---

## ⚙️ Impostazioni

### Interruttore Integrazione Desktop

**Impostazioni → Integrazione Desktop**

**ATTIVA:**
- ✅ Nuove note → esportate automaticamente come `.md`
- ✅ Note modificate → aggiornamento `.md`
- ✅ Note eliminate → il `.md` rimane (futuro: anche eliminazione)

**DISATTIVATA:**
- ❌ Nessuna esportazione Markdown
- ✅ La sincronizzazione JSON continua normalmente
- ✅ I file `.md` esistenti rimangono

### Esportazione iniziale

**Cosa succede all'attivazione:**
1. Tutte le note esistenti vengono analizzate
2. Il dialogo di avanzamento mostra lo stato (ad es. "23/42")
3. Ogni nota viene esportata come `.md`
4. In caso di errore: la singola nota viene saltata
5. Messaggio di successo con il numero di note esportate

**Tempo:** ~1-2 secondi ogni 50 note

---

## 🛠️ Utilizzo avanzato

### Creazione manuale di Markdown

Puoi creare file `.md` manualmente:

```markdown
---
id: 00000000-0000-0000-0000-000000000001
created: 2026-01-05T12:00:00Z
updated: 2026-01-05T12:00:00Z
---

# Nuova nota desktop

Contenuto...
```

**⚠️ Importante:**
- `id` deve essere un UUID valido (ad es. con uuidgen.io)
- Timestamp in formato ISO8601
- Frontmatter racchiuso con `---`

### Operazioni in blocco

**Modifica di più note contemporaneamente:**

1. Monta il WebDAV
2. Apri tutti i file `.md` in VS Code
3. Trova e Sostituisci in tutti i file (Ctrl+Shift+H)
4. Salva
5. Nell'app: "Importa modifiche Markdown"

### Script

**Esempio: ordina tutte le note per data**

```bash
#!/bin/bash
cd /mnt/notes-md/

# Ordina tutti i file .md per data di modifica
for file in *.md; do
  updated=$(grep "^updated:" "$file" | cut -d' ' -f2)
  echo "$updated $file"
done | sort
```

---

## ❌ Risoluzione dei problemi

### "404 Not Found" durante il montaggio di WebDAV

**Causa:** la cartella `/notes-md/` non esiste

**Soluzione:**
1. **Esegui la prima sincronizzazione** - La cartella viene creata automaticamente
2. OPPURE: creala manualmente tramite il terminale:
   ```bash
   curl -X MKCOL -u noteuser:password http://server:8080/notes-md/
   ```

### I file Markdown non compaiono

**Causa:** integrazione desktop non attivata

**Soluzione:**
1. Impostazioni → "Integrazione Desktop" ATTIVA
2. Attendi l'esportazione iniziale
3. Aggiorna la cartella WebDAV

### Le modifiche dal desktop non compaiono nell'app

**Causa:** importazione Markdown non eseguita

**Soluzione:**
1. Impostazioni → "Importa modifiche Markdown"
2. OPPURE: attendi la sincronizzazione automatica (funzione futura)

### Errore "Frontmatter mancante"

**Causa:** file `.md` senza frontmatter YAML valido

**Soluzione:**
1. Apri il file nell'editor
2. Aggiungi il frontmatter all'inizio:
   ```yaml
   ---
   id: NUOVO-UUID-QUI
   created: 2026-01-05T12:00:00Z
   updated: 2026-01-05T12:00:00Z
   ---
   ```
3. Salva e importa di nuovo

---

## 🔒 Sicurezza e migliori pratiche

### Cosa fare ✅

- ✅ **Backup prima delle modifiche in blocco** - Crea un backup locale
- ✅ **Un editor alla volta** - Non modificare nell'app E sul desktop in parallelo
- ✅ **Attendi la sincronizzazione** - Esegui la sincronizzazione prima della modifica desktop
- ✅ **Rispetta il frontmatter** - Non modificarlo manualmente (a meno che tu non sappia cosa fai)

### Cosa evitare ❌

- ❌ **Modifica parallela** - App e desktop contemporaneamente → conflitti
- ❌ **Eliminare il frontmatter** - La nota non potrà più essere importata
- ❌ **Cambiare gli ID** - La nota viene riconosciuta come nuova
- ❌ **Manipolare i timestamp** - La risoluzione dei conflitti non funziona

### Flusso di lavoro consigliato

```
1. Sincronizza nell'app (pull-to-refresh)
2. Apri il desktop
3. Apporta le modifiche
4. Salva
5. Nell'app: "Importa modifiche Markdown"
6. Verifica
7. Esegui un'altra sincronizzazione
```

---

## 📊 Confronto: JSON vs Markdown

| Aspetto | JSON | Markdown |
|--------|------|----------|
| **Formato** | Strutturato | Testo continuo |
| **Leggibilità (umana)** | ⚠️ Media | ✅ Buona |
| **Leggibilità (macchina)** | ✅ Perfetta | ⚠️ Richiede parsing |
| **Metadati** | Nativi | Frontmatter |
| **Editor** | Editor di codice | Tutti gli editor di testo |
| **Velocità di sincronizzazione** | ✅ Veloce | ⚠️ Più lenta |
| **Affidabilità** | ✅ 100% | ⚠️ Possibili errori di frontmatter |
| **Mobile-first** | ✅ Sì | ❌ No |
| **Desktop-first** | ❌ No | ✅ Sì |

**Conclusione:** usare entrambi i formati = migliore esperienza su entrambe le piattaforme!

---

## 🔮 Funzionalità future

Previste dalla v1.3.0+:

- ⏳ **Importazione Markdown automatica** - Automaticamente a ogni sincronizzazione
- ⏳ **Sincronizzazione bidirezionale** - Senza importazione manuale
- ⏳ **Anteprima Markdown** - Nell'app
- ⏳ **Interfaccia conflitti** - Su modifiche simultanee
- ⏳ **Tag nel frontmatter** - Sincronizzati con l'app
- ⏳ **Allegati** - Immagini/file in Markdown

---

**📚 Vedi anche:**
- [QUICKSTART.md](../QUICKSTART.md) - Configurazione dell'app
- [FEATURES.it.md](FEATURES.it.md) - Elenco completo delle funzionalità
- [BACKUP.it.md](BACKUP.it.md) - Backup e ripristino

**Ultimo aggiornamento:** v2.7.0 (2026-05-30)