# Supporto Certificati SSL Self-Signed

**Dal:** v1.7.0  
**Stato:** ✅ Supportato

---

## Panoramica

Simple Notes Sync ora supporta la connessione a server WebDAV con certificati SSL self-signed, come ad esempio:
- ownCloud/Nextcloud con certificati self-signed
- Synology NAS con certificati predefiniti
- Raspberry Pi o server domestici
- Server aziendali interni con CA private

## Come Usare

### Passo 1: Esporta il Certificato CA del Tuo Server

**Sul tuo server:**

1. Individua il file del certificato (di solito in formato `.crt`, `.pem`, o `.der`)
2. Se hai creato tu il certificato, lo possiedi già
3. Per Synology NAS: Pannello di controllo → Sicurezza → Certificato → Esporta
4. Per ownCloud/Nextcloud: di solito in `/etc/ssl/certs/` sul server

### Passo 2: Installa il Certificato su Android

**Sul tuo dispositivo Android:**

1. **Trasferisci** il file `.crt` o `.pem` sul telefono (tramite email, USB, ecc.)

2. **Apri Impostazioni** → Sicurezza → Altre impostazioni di sicurezza (o Crittografia e credenziali)

3. **Installa da archiviazione** / "Installa un certificato"
   - Scegli "Certificato CA"
   - **Avvertenza:** Android mostrerà un avviso di sicurezza. È normale.
   - Tocca "Installa comunque"

4. **Sfoglia** fino al file del certificato e selezionalo

5. **Dagli un nome** riconoscibile (ad es. "La mia CA ownCloud")

6. ✅ **Fatto!** Il certificato ora è riconosciuto a livello di sistema

### Passo 3: Connetti Simple Notes Sync

1. Apri Simple Notes Sync
2. Vai su **Impostazioni** → **Impostazioni Server**
3. Inserisci il tuo URL del server **`https://`** come di consueto
4. L'app ora riconoscerà il tuo certificato self-signed ✅

---

## Note di Sicurezza

### ⚠️ Importante

- Installare un certificato CA concede la fiducia a **tutti** i certificati firmati da quella CA
- Installa solo certificati da fonti di cui ti fidi
- Android ti avviserà prima dell'installazione – leggi attentamente l'avviso

### 🔒 Perché è Sicuro

- Installi il certificato **manualmente** (decisione consapevole)
- L'app usa il trust store nativo di Android (nessuna validazione personalizzata)
- Puoi rimuovere il certificato in qualsiasi momento dalle Impostazioni Android
- Conforme a F-Droid e Google Play (nessun trucco "trust all")

---

## Risoluzione dei Problemi

### Certificato Non Riconosciuto

**Problema:** l'app mostra ancora un errore SSL dopo aver installato il certificato

**Soluzioni:**
1. **Verifica l'installazione:** Impostazioni → Sicurezza → Credenziali di fiducia → scheda Utente
2. **Controlla il tipo di certificato:** deve essere un certificato CA, non un certificato server
3. **Riavvia l'app:** chiudi e riapri Simple Notes Sync
4. **Controlla l'URL:** deve usare `https://` (non `http://`)

### Errore "Network Security Policy"

**Problema:** Android 7+ limita i certificati utente per le app

**Soluzione:** questa app è configurata per riconoscere i certificati utente ✅  
Se il problema persiste, controlla:
- Il certificato è installato nella scheda "Utente" (non "Sistema")
- Il certificato non è scaduto
- L'URL del server corrisponde al Common Name (CN) o allo Subject Alternative Name (SAN) del certificato

### Self-Signed vs. Firmato da CA

| Tipo | Installazione Richiesta | Sicurezza |
|------|-------------------------|-----------|
| **Self-Signed** | ✅ Sì | Fiducia manuale |
| **Let's Encrypt** | ❌ No | Automatica |
| **CA privata** | ✅ Sì (root CA) | Automatica per tutti i cert firmati dalla CA |

---

## Alternativa: Usa Let's Encrypt (Consigliato)

Se il tuo server è accessibile pubblicamente, considera l'uso di **Let's Encrypt** per certificati SSL gratuiti e rinnovati automaticamente:

- Nessuna installazione manuale del certificato necessaria
- Riconosciuto automaticamente da tutti i dispositivi
- Più semplice per gli utenti finali

**Guide di configurazione:**
- [ownCloud Let's Encrypt](https://doc.owncloud.com/server/admin_manual/installation/letsencrypt/)
- [Nextcloud Let's Encrypt](https://docs.nextcloud.com/server/latest/admin_manual/installation/letsencrypt.html)
- [Synology Let's Encrypt](https://kb.synology.com/en-us/DSM/tutorial/How_to_enable_HTTPS_and_create_a_certificate_signing_request_on_your_Synology_NAS)

---

## Dettagli Tecnici

### Implementazione

- Usa la **Network Security Config** di Android
- Si fida sia dei certificati CA di sistema che di quelli utente
- Nessun TrustManager o hostname verifier personalizzato
- Conforme a F-Droid e alla conformità Play Store

### Configurazione

File: `android/app/src/main/res/xml/network_security_config.xml`

```xml
<base-config>
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" />  <!-- ← Abilita il supporto self-signed -->
    </trust-anchors>
</base-config>
```

---

## FAQ

**D: Devo reinstallare il certificato dopo gli aggiornamenti dell'app?**  
R: No, i certificati sono archiviati a livello di sistema, non per app.

**D: Posso usare lo stesso certificato per più app?**  
R: Sì, una volta installato funziona per tutte le app che riconoscono i certificati utente.

**D: Come posso rimuovere un certificato?**  
R: Impostazioni → Sicurezza → Credenziali di fiducia → scheda Utente → Tocca il certificato → Rimuovi

**D: Funziona su Android 14+?**  
R: Sì, testato da Android 7 a 15 (API 24-35).

---

## Problemi Correlati

- [GitHub Issue #X](link) - Richiesta utente per il supporto ownCloud
- [Feature Analysis](../project-docs/simple-notes-sync/features/SELF_SIGNED_SSL_CERTIFICATES_ANALYSIS.md) - Analisi tecnica

---

**Hai bisogno di aiuto?** Apri un issue su [GitHub](https://github.com/inventory69/simple-notes-sync/issues)