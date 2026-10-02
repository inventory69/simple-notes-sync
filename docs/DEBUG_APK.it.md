# APK Debug per il Testing delle Issue

Per i bug-report e il test delle correzioni ti serve un'**APK Debug**. Viene compilata automaticamente quando fai push su branch speciali.

## 🔧 Struttura dei branch per le APK Debug

Le APK Debug vengono compilate **automaticamente** per questi branch:

| Tipo di branch | Scopo | Esempio |
|-----------|-------|---------|
| `debug/*` | Testing generale | `debug/wifi-only-sync` |
| `fix/*` | Test di bug-fix | `fix/vpn-connection` |
| `feature/*` | Nuove funzionalità | `feature/grid-layout` |

**Gli altri branch (main, develop, ecc.) NON compilano APK Debug!**

## 📥 Scaricare l'APK Debug

### 1️⃣ Push su un branch debug

```bash
# Creare un nuovo fix-branch
git checkout -b fix/my-bug

# Apportare le modifiche
# ...

# Commit e push
git add .
git commit -m "fix: descrizione"
git push origin fix/my-bug
```

### 2️⃣ Avviare il workflow GitHub Actions

- GitHub → scheda **Actions**
- Vedere il workflow **Build Debug APK**
- Attendere che il workflow diventi verde ✅

### 3️⃣ Scaricare l'APK

1. Attendere il successo verde del workflow
2. Sezione **Artifacts** in alto (o in basso nel workflow)
3. Scaricare `simple-notes-sync-debug-*`
4. Estrarre il file ZIP

**Importante:** gli artifact sono disponibili solo per **30 giorni**!

## 📱 Installazione sul dispositivo

### Con ADB (consigliato - testing pulito)
```bash
# Collegare il dispositivo
adb devices

# Installare l'APK Debug (la versione precedente non viene eliminata)
adb install simple-notes-sync-debug.apk

# Rimuovere dal dispositivo in seguito:
adb uninstall dev.dettmer.simplenotes
```

### Manualmente sul dispositivo
1. Copiare il file sul dispositivo Android
2. **Impostazioni → Sicurezza → attivare "Origini sconosciute"**
3. Aprire il gestore file e toccare l'APK
4. Selezionare "Installa"

## ⚠️ APK Debug vs. APK Release

| Funzionalità | Debug | Release |
|---------|-------|---------|
| **Registrazione** | Completa | Minima |
| **Firma** | Debug-key | Release-key |
| **Prestazioni** | Più lenta | Più veloce |
| **Debugging** | ✅ Possibile | ❌ No |
| **Installazione** | Più volte | Può dare problemi |

## 📊 Cosa testare

1. **Nuove funzionalità** - Funzionano come descritto?
2. **Bug fix** - Il bug è davvero risolto?
3. **Compatibilità** - Funziona sul tuo dispositivo?
4. **Prestazioni** - L'app gira fluida?

## 📝 Dare feedback

Scrivi un commento nella **Pull Request** o nella **GitHub Issue**:
- ✅ Cosa funziona
- ❌ Cosa non funziona
- 📋 Log degli errori (adb logcat se rilevante)
- 📱 Dispositivo/versione Android

## 🐛 Raccogliere i log

Se lo sviluppatore dell'app ha bisogno di log di debug:

```bash
# Aprire un terminale con adb
adb shell pm grant dev.dettmer.simplenotes android.permission.READ_LOGS

# Guardare i log (live)
adb logcat | grep simplenotes

# Salvare i log (file)
adb logcat > debug-log.txt

# Filtrare dopo un errore
adb logcat | grep -E "ERROR|Exception|CRASH"
```

---

**Grazie per il test! Il tuo feedback ci aiuta a migliorare l'app.** 🙏