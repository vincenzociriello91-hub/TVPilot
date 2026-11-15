# TVPilot

Telecomando intelligente per smart TV **LG webOS** (pensato per la 43LM6300PLA, webOS 4.5) da qualsiasi telefono Android sulla stessa rete Wi-Fi.

**Scarica l'APK:** [ultima Release](https://github.com/vincenzociriello91-hub/TVPilot/releases/latest)

## Funzioni
- **Telecomando**: frecce/OK, volume, canali, numeri, tasti colorati, multimedia, touchpad, tastiera del telefono. Accendi (Wake-on-LAN), spegni, spegni solo lo schermo.
- **Mirroring**: universale (qualsiasi Android 8+, nel browser della TV, senza audio) e nativo Miracast con Screen Share della TV.
- **App e ingressi**: apri e chiudi app, cambia HDMI.
- **Pulizia** (stile CleanYourTV): via pubblicità e tracciamento (Live Plus, cookie pubblicitari), Home che non si apre da sola, suono di accensione, accensione dal telefono. Ogni intervento ha il suo backup.
- **Sviluppatore**
  - Impostazioni nascoste di webOS, con livello di rischio (sicura / attenzione / pericolosa) e conferma esplicita per quelle pericolose.
  - Backup automatico prima di ogni modifica: nell'app (scheda Backup, tasto Ripristina) e in `Download/TVPilot/backup` sul telefono.
  - Modalità sviluppatore ufficiale LG: installazione di file .ipk, catalogo Homebrew, rimozione app, rinnovo della sessione di 50 ore.
  - Homebrew Channel / root: SSH root, installazione e rimozione app.
- **Info**: firmware della TV con avviso quando cambia, apertura del controllo aggiornamenti sulla TV, aggiornamento dell'app, rapporti.

## Orologio (Pixel Watch / Wear OS)
L'app per orologio è sospesa: il codice resta nella cartella `wear/`, ma non viene più compilata né pubblicata.

## Come funziona
- Collegamento SSAP via WebSocket (`wss://IP:3001`, poi `ws://IP:3000`), abbinamento confermato una volta sulla TV.
- Impostazioni interne: `settings/setSystemSettings` pubblico, altrimenti `luna://com.webos.settingsservice/setSystemSettings` tramite il trucco dell'avviso di sistema (bscpylgtv).
- Catalogo impostazioni da `bscpylgtv/docs` (modello 2019, stessa generazione webOS 4.x).
- Rapporti cifrati (RSA-OAEP-256 + AES-256-GCM) al ricevitore `rapporti-vincenzo` → repository privato `TVPilot-rapporti`.

## Compilazione
GitHub Actions compila a ogni push su `main` e pubblica la Release firmata con la chiave in `firma/` (scelta voluta: repository tutto pubblico; i secret `KEYSTORE_B64`/`KEYSTORE_PASS`, se impostati, hanno la precedenza). Se la build fallisce, il registro errori va nel ramo `log-build`.

## Novità
### 1.6.2 — 15/11/2026
- Nome del modello della TV corretto.

### 1.6.1 — 15/11/2026
- Corretti i tentativi di collegamento a raffica dal touchpad con la TV spenta.
- La TV viene ritrovata da sola se cambia indirizzo IP.

### 1.6.0 — 15/11/2026
- Ricollegamento automatico prima di ogni comando, accensione più rapida, errori in italiano chiaro.
- Rapporti con chiave propria e copie locali limitate a 60.
- L'APK dell'orologio non può più sostituire l'app del telefono.

### 1.5.1 — 15/11/2026
- Corretto l'aggiornamento dall'app che scaricava l'APK dell'orologio.

### 1.5.0 — 15/11/2026
- Nuova grafica del telecomando (stile Pixel).

### 1.4.0 — 15/11/2026
- Routine, programmi orari, limite di tempo, chiamate e messaggi sulla TV, telecomando personalizzabile, scorciatoie, backup completo, Homebrew consigliate.

### 1.3.0 — 15/11/2026
- App per Pixel Watch / Wear OS.

### 1.2.0 — 15/11/2026
- Controlli fuori dall'app (tasti volume, notifica, widget, riquadro rapido), comandi vocali, Condividi → TV, foto/video/musica via DLNA, canali, profili rapidi, più TV.

### 1.1.1 — 15/11/2026
- Pulizia: stato "Applicato" ricordato anche dopo la chiusura dell'app.

### 1.1.0 — 15/11/2026
- Mirroring dello schermo: universale per qualsiasi telefono Android e nativo Miracast.

### 1.0.1 — 15/11/2026
- Touchpad e tasti: corretto l'errore "connection closed" del puntatore.

### 1.0.0 — 15/11/2026
- Prima versione: telecomando, app e ingressi, pulizia, impostazioni nascoste con backup, modalità sviluppatore LG, Homebrew/root, avvisi firmware.
