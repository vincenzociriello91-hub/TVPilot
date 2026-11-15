package it.vincenzo.tvpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.BuildConfig
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.core.Updater
import it.vincenzo.tvpilot.tv.Risk
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.WebOsClient
import kotlinx.coroutines.launch

@Composable
fun InfoScreen() {
    val ctx = LocalContext.current
    val info by Tv.info.collectAsState()
    val notice by Tv.firmwareNotice.collectAsState()
    val state by Tv.client.stato.collectAsState()
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var sendMsg by remember { mutableStateOf(Reports.lastSend) }
    var haptics by remember { mutableStateOf(Prefs.haptics(ctx)) }
    var mac by remember { mutableStateOf(Prefs.mac(ctx) ?: "") }
    var showChangelog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("La tua TV", Icons.Filled.Tv, subtitle = info["modello"] ?: Prefs.tvName(ctx) ?: "LG 43LM6300PLA (webOS 4.5)") {
            if (info.isEmpty()) Text("Collega la TV per leggere modello e firmware.", style = MaterialTheme.typography.bodySmall)
            info.forEach { (k, v) -> if (v.isNotBlank()) Text("$k: $v", style = MaterialTheme.typography.bodyMedium) }
            StatusChip(if (state == WebOsClient.State.CONNECTED) "Collegata" else "Non collegata", state == WebOsClient.State.CONNECTED)
        }

        Section("Aggiornamenti della TV", Icons.Filled.SystemUpdate, subtitle = "Firmware attuale: ${Prefs.firmware(ctx) ?: "non ancora letto"}") {
            notice?.let { Notice(it, Risk.MEDIUM) }
            Text(
                "LG non pubblica un servizio aperto per sapere da fuori se c'è un firmware nuovo: l'app ti avvisa quando la TV " +
                    "cambia versione e ogni 14 giorni ti ricorda di controllare. Il controllo vero lo fa la TV.",
                style = MaterialTheme.typography.bodySmall,
            )
            Notice(
                "Se hai fatto (o vuoi fare) il root con Homebrew Channel: un aggiornamento LG può togliere il root e chiudere " +
                    "la falla usata per ottenerlo. In quel caso non aggiornare finché non controlli su rootmytv/webosbrew.",
                Risk.MEDIUM,
            )
            Button(onClick = { tvAction("Aggiornamenti TV", "Sulla TV si è aperto il controllo aggiornamenti") { Tv.openUpdateCheck() } }) {
                Text("Cerca aggiornamenti sulla TV")
            }
        }

        Section("App TVPilot", Icons.Filled.NewReleases, subtitle = "Versione ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") {
            updateMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    updateMsg = "Controllo…"
                    Tv.scope.launch {
                        val r = runCatching { Updater.latest() }
                        release = r.getOrNull()?.takeIf { Updater.isNewer(it) }
                        updateMsg = when {
                            r.isFailure -> "Controllo non riuscito: ${r.exceptionOrNull()?.message}"
                            release != null -> "Disponibile ${release!!.name}:\n${release!!.notes}"
                            else -> "Hai già l'ultima versione."
                        }
                    }
                }) { Text("Controlla aggiornamenti") }
                release?.let { rel ->
                    Button(onClick = {
                        updateMsg = "Download in corso…"
                        Tv.scope.launch {
                            runCatching { Updater.install(ctx, rel) }.onFailure { updateMsg = "Download non riuscito: ${it.message}" }
                        }
                    }) { Text("Installa") }
                }
            }
            OutlinedButton(onClick = { showChangelog = !showChangelog }) { Text(if (showChangelog) "Nascondi novità" else "Novità") }
            if (showChangelog) Text(
                remember { runCatching { ctx.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }.getOrDefault("") },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Rapporti", Icons.Filled.Description, subtitle = "Cifrati, li legge solo Claude per correggere l'app") {
            Text("Ultimo invio: $sendMsg", style = MaterialTheme.typography.bodySmall)
            Text("Copia leggibile sul telefono: Download/${Reports.FOLDER}", style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = {
                Tv.scope.launch {
                    Reports.flush("manuale")
                    Reports.sendQueue()
                    sendMsg = Reports.lastSend
                }
            }) { Text("Invia rapporto ora") }
        }

        Section("Impostazioni", Icons.Filled.Tune) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Vibrazione sui tasti", Modifier.weight(1f))
                Switch(haptics, { haptics = it; Prefs.setHaptics(ctx, it) })
            }
            OutlinedTextField(
                mac, { mac = it }, label = { Text("Indirizzo MAC della TV (per Accendi)") }, singleLine = true,
                supportingText = { Text("Si compila da solo al primo collegamento. TV: Impostazioni → Rete → Stato") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { Prefs.setMac(ctx, mac.trim()); UiBus.show("MAC salvato") }) { Text("Salva MAC") }
                OutlinedButton(onClick = {
                    Prefs.lastHost(ctx)?.let { Prefs.forgetKey(ctx, it) }
                    Tv.client.disconnect()
                    UiBus.show("Abbinamento dimenticato: al prossimo collegamento conferma sulla TV")
                }) { Text("Dimentica abbinamento") }
            }
        }
        Spacer(Modifier.padding(12.dp))
    }
}
