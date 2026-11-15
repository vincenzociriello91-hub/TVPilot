package it.vincenzo.tvpilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Discovery
import it.vincenzo.tvpilot.tv.Risk
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.WebOsClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Messaggi brevi mostrati in basso (snackbar) */
object UiBus {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    fun show(msg: String) { messages.tryEmit(msg) }
}

/** Esegue un comando verso la TV senza bloccare la grafica; gli errori diventano un messaggio in italiano */
fun tvAction(label: String, ok: String? = null, block: suspend () -> Unit) {
    Tv.scope.launch {
        try {
            block()
            if (ok != null) UiBus.show(ok)
        } catch (e: Exception) {
            Reports.log("Azione \"$label\" non riuscita: ${e.message}")
            UiBus.show(
                if (!Tv.client.connected) "$label: TV non collegata"
                else "$label: ${e.message ?: "errore"}"
            )
        }
    }
}

/** Rapporto dopo un'operazione importante, inviato subito in background */
fun reportOperation(name: String) {
    Tv.scope.launch {
        Reports.flush("operazione-$name")
        Reports.sendQueue()
    }
}

/** Collegamento: ricerca automatica, elenco TV trovate, IP a mano */
@Composable
fun ConnectCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Tv.client.stato.collectAsState()
    val detail by Tv.client.dettaglio.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<Discovery.Found>?>(null) }
    var ip by remember { mutableStateOf(Prefs.lastHost(ctx) ?: "") }

    fun connect(target: String) {
        Prefs.setLastHost(ctx, target)
        scope.launch { Tv.connect(target) }
    }

    Section("Collega la TV", Icons.Filled.Tv, subtitle = "Telefono e TV devono essere sulla stessa rete Wi-Fi") {
        when (state) {
            WebOsClient.State.CONNECTING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text(detail)
            }
            WebOsClient.State.CONFIRM_ON_TV -> Notice(detail, Risk.MEDIUM, "Guarda la TV")
            WebOsClient.State.ERROR -> Notice(detail, Risk.HIGH)
            else -> if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                searching = true
                scope.launch {
                    found = Discovery.search(ctx)
                    searching = false
                    if (found?.size == 1) { ip = found!![0].ip; connect(ip) }
                }
            }, enabled = !searching) {
                Icon(Icons.Filled.Search, null); Spacer(Modifier.width(6.dp))
                Text(if (searching) "Cerco…" else "Cerca TV")
            }
            if (Prefs.lastHost(ctx) != null) OutlinedButton(onClick = { connect(Prefs.lastHost(ctx)!!) }) {
                Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Riprova")
            }
        }
        found?.let { list ->
            if (list.isEmpty()) Text("Nessuna TV trovata. Accendila, oppure scrivi l'indirizzo IP (TV: Impostazioni → Rete → Stato rete).",
                style = MaterialTheme.typography.bodySmall)
            list.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { ip = f.ip; connect(f.ip) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Tv, null); Spacer(Modifier.width(10.dp))
                    Column { Text(f.name); Text(f.ip, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                ip, { ip = it.trim() }, label = { Text("Indirizzo IP della TV") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { if (ip.isNotBlank()) connect(ip) }) { Text("Collega") }
        }
    }
}

/** Mostrato nelle schede che richiedono la TV collegata */
@Composable
fun NeedTv(content: @Composable () -> Unit) {
    val state by Tv.client.stato.collectAsState()
    if (state == WebOsClient.State.CONNECTED) content()
    else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { ConnectCard() }
}
