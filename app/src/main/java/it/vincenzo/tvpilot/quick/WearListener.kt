package it.vincenzo.tvpilot.quick

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.TvActions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Riceve i comandi dal Pixel Watch (/tvpilot/azione/NOME) e risponde con l'esito (/tvpilot/esito) */
class WearListener : WearableListenerService() {
    override fun onMessageReceived(e: MessageEvent) {
        if (!e.path.startsWith("/tvpilot/azione/")) return
        val a = runCatching { TvActions.Action.valueOf(e.path.substringAfterLast('/')) }.getOrNull() ?: return
        Reports.log("Orologio: $a")
        // il servizio gira già su un thread a parte: si può aspettare l'esito
        val msg = runBlocking {
            withTimeoutOrNull(20_000) { runCatching { TvActions.run(a) }.getOrElse { "Errore: ${it.message}" } } ?: "TV non risponde"
        }
        runCatching { Wearable.getMessageClient(this).sendMessage(e.sourceNodeId, "/tvpilot/esito", msg.toByteArray()) }
    }
}
