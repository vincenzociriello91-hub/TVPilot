# TVPilot

Telecomando intelligente per smart TV **LG webOS** (pensato per la 43LM6300PLA, webOS 4.5) da qualsiasi telefono Android sulla stessa rete Wi-Fi.

**Scarica l'APK:** [ultima Release](https://github.com/vincenzociriello91-hub/TVPilot/releases/latest)

## Funzioni
- **Telecomando**: frecce/OK, volume, canali, numeri, tasti colorati, multimedia, touchpad, tastiera del telefono. Accendi (Wake-on-LAN), spegni, spegni solo lo schermo.
- **App e ingressi**: apri e chiudi app, cambia HDMI.
- **Pulizia** (stile CleanYourTV): via pubblicità e tracciamento (Live Plus, cookie pubblicitari), Home che non si apre da sola, suono di accensione, accensione dal telefono. Ogni intervento ha il suo backup.
- **Sviluppatore**
  - Impostazioni nascoste di webOS, con livello di rischio (sicura / attenzione / pericolosa) e conferma esplicita per quelle pericolose.
  - Backup automatico prima di ogni modifica: nell'app (scheda Backup, tasto Ripristina) e in `Download/TVPilot/backup` sul telefono.
  - Modalità sviluppatore ufficiale LG: installazione di file .ipk, catalogo Homebrew, rimozione app, rinnovo della sessione di 50 ore.
  - Homebrew Channel / root: SSH root, installazione e rimozione app.
- **Info**: firmware della TV con avviso quando cambia, apertura del controllo aggiornamenti sulla TV, aggiornamento dell'app, rapporti.

## Come funziona
- Collegamento SSAP via WebSocket (`wss://IP:3001`, poi `ws://IP:3000`), abbinamento confermato una volta sulla TV.
- Impostazioni interne: `settings/setSystemSettings` pubblico, altrimenti `luna://com.webos.settingsservice/setSystemSettings` tramite il trucco dell'avviso di sistema (bscpylgtv).
- Catalogo impostazioni da `bscpylgtv/docs` (modello 2019, stessa generazione webOS 4.x).
- Rapporti cifrati (RSA-OAEP-256 + AES-256-GCM) al ricevitore `rapporti-vincenzo` → repository privato `TVPilot-rapporti`.

## Compilazione
GitHub Actions compila a ogni push su `main`. Con i secret `KEYSTORE_B64` e `KEYSTORE_PASS` pubblica la Release firmata. Se la build fallisce, il registro errori va nel ramo `log-build`.

## Novità
### 1.0.0 — 15/11/2026
- Prima versione: telecomando, app e ingressi, pulizia, impostazioni nascoste con backup, modalità sviluppatore LG, Homebrew/root, avvisi firmware.
