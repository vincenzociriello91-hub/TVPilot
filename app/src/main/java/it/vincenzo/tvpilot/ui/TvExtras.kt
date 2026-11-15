package it.vincenzo.tvpilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.TvActions

/** Profili rapidi: modo immagine, uscita audio, timer di spegnimento */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesCard() {
    var output by remember { mutableStateOf("") }
    var picture by remember { mutableStateOf<String?>(null) }
    var sleep by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { runCatching { output = Tv.soundOutput() } }

    Section("Profili rapidi", Icons.Filled.Tune, subtitle = "Immagine, audio e timer in un tocco") {
        Text("Modo immagine", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tv.PICTURE_MODES.forEach { (id, label) ->
                FilterChip(selected = picture == id, onClick = {
                    picture = id
                    tvAction("Modo immagine", "Immagine: $label") { Tv.setPictureMode(id) }
                }, label = { Text(label) })
            }
        }
        Text("Uscita audio", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tv.SOUND_OUTPUTS.forEach { (id, label) ->
                FilterChip(selected = output == id, onClick = {
                    tvAction("Uscita audio", "Audio: $label") { Tv.setSoundOutput(id); output = id }
                }, label = { Text(label) })
            }
        }
        Text("Spegnimento automatico", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = sleep == 0, onClick = {
                sleep = 0; tvAction("Timer", "Timer spento") { TvActions.sleepTimer(0) }
            }, label = { Text("No") })
            listOf(10, 20, 30, 60, 90, 120).forEach { m ->
                FilterChip(selected = sleep == m, onClick = {
                    sleep = m; tvAction("Timer", "La TV si spegne tra $m minuti") { TvActions.sleepTimer(m) }
                }, label = { Text(if (m < 60) "$m min" else "${m / 60}${if (m % 60 == 30) ",5" else ""} h") })
            }
        }
        Text("Il timer è quello della TV: funziona anche se chiudi l'app o spegni il telefono.", style = MaterialTheme.typography.bodySmall)
    }
}

/** Canali: elenco completo, ricerca, cambio diretto, cosa va in onda */
@Composable
fun ChannelsCard() {
    var list by remember { mutableStateOf<List<Tv.Channel>?>(null) }
    var now by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    fun refreshNow() = tvAction("Canale attuale") { now = Tv.currentProgram() }
    LaunchedEffect(Unit) { refreshNow() }

    Section("Canali", Icons.Filled.LiveTv, subtitle = now.ifBlank { null }?.let { "In onda: $it" }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { tvAction("Canale") { Tv.channelDown(); refreshNow() } }, label = { Text("Canale −") })
            AssistChip(onClick = { tvAction("Canale") { Tv.channelUp(); refreshNow() } }, label = { Text("Canale +") })
            AssistChip(onClick = { tvAction("Live TV") { Tv.launch("com.webos.app.livetv") } }, label = { Text("Live TV") })
        }
        if (list == null) TextButton(onClick = {
            tvAction("Elenco canali") { list = Tv.channels(); expanded = true }
        }) { Text("Mostra tutti i canali") }
        list?.let { all ->
            if (all.isEmpty()) Text("La TV non ha restituito canali (antenna non sintonizzata?).", style = MaterialTheme.typography.bodySmall)
            else {
                OutlinedTextField(filter, { filter = it }, label = { Text("Cerca canale o numero") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val shown = all.filter { filter.isBlank() || it.name.contains(filter, true) || it.number.startsWith(filter) }
                (if (expanded || filter.isNotBlank()) shown else shown.take(15)).forEach { ch ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            tvAction("Canale", "Canale ${ch.number} ${ch.name}") { Tv.openChannel(ch.id); refreshNow() }
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ch.number, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                        Column { Text(ch.name) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
