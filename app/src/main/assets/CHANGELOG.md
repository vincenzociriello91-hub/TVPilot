Versione 1.6.4 — 22/11/2026
- Pulizia e Sviluppatore: quando la TV accetta la modifica solo tramite il servizio interno, l'app aspetta qualche istante e rilegge il valore un paio di volte, invece di dire subito "non verificato".
- Messaggio più chiaro: distingue "la TV non permette di rileggere il valore" da "la TV risponde ancora con il valore vecchio".
- Rapporti: un arresto forzato o un aggiornamento dell'app non vengono più segnalati come chiusura improvvisa.

Versione 1.6.3 — 15/11/2026
- Corretto: più modifiche fatte insieme (es. "Pulizia consigliata") sovrascrivevano a vicenda il backup e ne restava solo l'ultimo. Ora ogni modifica ha il suo backup.
- Ogni modifica crea una sola copia del backup in Download (prima due).
- Ripristino più sicuro: i valori precedenti sconosciuti non vengono più mandati alla TV.
- Installazione app (sviluppatore/Homebrew): se la TV rifiuta l'installazione l'app lo dice subito, invece di aspettare fino a 10 minuti.
- Foto e video sulla TV: se la TV ha cambiato porta del lettore dopo una riaccensione, l'app la ritrova e riprova da sola.

Versione 1.6.2 — 15/11/2026
- La TV viene mostrata con il modello vero (es. 43LM6300PLA) invece del codice interno di webOS.

Versione 1.6.1 — 15/11/2026
- Corretto: con la TV spenta, muovere il dito sul touchpad metteva in fila decine di tentativi di collegamento, che continuavano per minuti anche ad app chiusa.
- Dopo un tentativo di collegamento automatico non riuscito, l'app aspetta 8 secondi prima di riprovare da sola.
- Se il modem dà alla TV un indirizzo IP nuovo, l'app la ritrova da sola sulla rete e sposta lì l'abbinamento, senza chiedere di nuovo la conferma sulla TV.

Versione 1.6.0 — 15/11/2026
- I comandi si ricollegano da soli alla TV: niente più "TV non collegata" dopo che il telefono è rimasto in tasca.
- Accensione più veloce: l'app controlla in 1,5 secondi se la TV ha risposto, invece di tentativi da 14 secondi (prima poteva superare i 2 minuti).
- All'apertura dell'app, se la TV è spenta, compare solo una riga ("TV spenta o non raggiungibile") e non più l'avviso rosso.
- Un solo collegamento alla volta: app, widget, orologio e programmi orari non si chiudono più il collegamento a vicenda.
- Messaggi di errore in italiano chiaro al posto di quelli tecnici in inglese.
- Rapporti: chiave di cifratura propria di TVPilot, invii doppi evitati, nella cartella Download/TVPilot_report restano solo le ultime 60 copie.
- App per l'orologio: non può più sostituire per sbaglio l'app del telefono; se aperta su un telefono scarica la versione giusta.

Versione 1.5.1 — 15/11/2026
- Corretto: "Controlla aggiornamenti" poteva scaricare l'app per l'orologio invece di quella per il telefono.

Versione 1.5.0 — 15/11/2026
- Nuova grafica del telecomando (stile Pixel, proposta A): nome della TV e stato in alto, un solo pulsante di accensione, croce direzionale più grande, tasti rapidi quadrati, volume e canale in due barre con il valore al centro.
- Touchpad, numeri, play/pausa, tasti colorati, menu e "solo schermo spento" sono nella riga "Altri tasti".

Versione 1.4.0 — 15/11/2026
- Nuova scheda Funzioni con quattro sezioni: Mirroring e media, Canali e profili, Automazioni, App e ingressi.
- Routine: più comandi in un tocco (Sera film, Partita, Notte, Gioco), modificabili e creabili.
- Programmi orari: accendi, spegni o avvia una routine a un'ora e nei giorni scelti.
- Limite di tempo: avviso sulla TV 5 minuti prima, poi spegnimento.
- Telefono e TV insieme: pausa del video quando arriva una chiamata, chiamate e messaggi come avviso sulla TV (testo nascosto se vuoi).
- Telecomando personalizzabile: app preferite in prima pagina e sezioni da mostrare o nascondere.
- Scorciatoie tenendo premuta l'icona: Spegni, Accendi, Muto e le prime due routine.
- Backup completo delle impostazioni della TV, confronto con lo stato attuale e ripristino.
- Catalogo Homebrew con le app consigliate in evidenza.

Versione 1.3.0 — 15/11/2026
- Nuova app per Pixel Watch / Wear OS (TVPilot-orologio.apk): accendi/spegni, volume con la corona, muto, frecce e OK, indietro, Home, play/pausa, canali. I comandi passano dal telefono.

Versione 1.2.0 — 15/11/2026
- Controlli fuori dall'app: tasti volume del telefono, notifica fissa con volume, muto e accensione, widget per la schermata Home, riquadro "TV" nelle impostazioni rapide.
- Comandi vocali in italiano: "apri YouTube", "volume 20", "canale 5", "HDMI 2", "spegni tra 30 minuti".
- Condividi → "Apri sulla TV": i link YouTube si aprono nell'app YouTube della TV, gli altri nel browser.
- Foto, video e musica del telefono sulla TV a qualità piena e con audio (DLNA), con Pausa/Riprendi/Stop.
- Canali: elenco completo, ricerca, cambio diretto e cosa va in onda.
- Profili rapidi: modo immagine, uscita audio (soundbar, Bluetooth…), timer di spegnimento della TV.
- Più TV salvate: si passa dall'una all'altra in Info.

Versione 1.1.1 — 15/11/2026
- Pulizia: gli interventi già applicati restano segnati come "Applicato" (con data) anche dopo aver chiuso l'app, quando la TV non permette di rileggerli; c'è il tasto Riapplica.

Versione 1.1.0 — 15/11/2026
- Mirroring dello schermo (scheda App): modo universale per qualsiasi telefono Android, che mostra lo schermo nel browser della TV, con qualità Fluido/Bilanciato/Nitido; l'audio resta sul telefono.
- Mirroring nativo Miracast con audio per i telefoni che lo hanno (Samsung Smart View, Xiaomi, Oppo…): apre Screen Share sulla TV e la trasmissione del telefono.

Versione 1.0.1 — 15/11/2026
- Touchpad e tasti: corretto l'errore "connection closed" del puntatore (stesso collegamento cifrato della TV, un solo canale alla volta, movimenti raggruppati).

Versione 1.0.0 — 15/11/2026
- Prima versione di TVPilot per la LG 43LM6300PLA (webOS 4.5), funziona da qualsiasi telefono Android sulla stessa rete Wi-Fi.
- Telecomando completo: frecce e OK, volume, canali, numeri, tasti colorati, multimedia, touchpad con puntatore, tastiera del telefono.
- Accendi la TV dal telefono (Wake-on-LAN), spegni, spegni solo lo schermo.
- App e ingressi: apri, chiudi, cambia HDMI.
- Pulizia stile CleanYourTV: via pubblicità e tracciamento, Home che non si apre da sola, suono di accensione, accensione dal telefono.
- Scheda Sviluppatore: impostazioni nascoste di webOS con avviso sui rischi, backup automatico prima di ogni modifica e ripristino.
- Installazione app: modalità sviluppatore ufficiale LG (con rinnovo sessione) e Homebrew Channel / root, catalogo Homebrew incluso.
- Avviso quando la TV cambia firmware e promemoria per controllare gli aggiornamenti.
- Aggiornamento dell'app dall'app, rapporti cifrati automatici.
