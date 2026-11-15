package it.vincenzo.tvpilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.tv.Tv

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen() = NeedTv {
    var apps by remember { mutableStateOf<List<Tv.AppItem>>(emptyList()) }
    var inputs by remember { mutableStateOf<List<Tv.InputItem>>(emptyList()) }
    var foreground by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }

    fun reload() = tvAction("Elenco app") {
        apps = Tv.apps()
        inputs = runCatching { Tv.inputs() }.getOrDefault(emptyList())
        foreground = runCatching { Tv.foregroundApp() }.getOrDefault("")
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Section("Ingressi", Icons.Filled.Input, subtitle = "HDMI, antenna, USB") {
                if (inputs.isEmpty()) Text("Nessun ingresso letto.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inputs.forEach { inp ->
                        AssistChip(
                            onClick = { tvAction("Ingresso", "Passato a ${inp.label}") { Tv.switchInput(inp.id) } },
                            label = { Text(inp.label + if (inp.connected) " ●" else "") },
                        )
                    }
                }
            }
        }
        item {
            Section("App della TV", Icons.Filled.Apps, subtitle = if (foreground.isNotBlank()) "Aperta ora: $foreground" else null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(filter, { filter = it }, label = { Text("Cerca") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { reload() }) { Icon(Icons.Filled.Refresh, "Aggiorna") }
                }
                if (foreground.isNotBlank()) FilledTonalButton(onClick = {
                    val id = foreground
                    tvAction("Chiudi app", "App chiusa") { Tv.closeApp(id); foreground = "" }
                }) { Icon(Icons.Filled.Close, null); Spacer(Modifier.width(6.dp)); Text("Chiudi l'app aperta") }
            }
        }
        val shown = apps.filter { filter.isBlank() || it.title.contains(filter, true) || it.id.contains(filter, true) }
        items(shown, key = { it.id }) { app ->
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { tvAction("Apri app", "Aperta ${app.title}") { Tv.launch(app.id) } }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            app.id + if (app.system) " · di sistema" else "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { tvAction("Apri app", "Aperta ${app.title}") { Tv.launch(app.id) } }) { Icon(Icons.Filled.PlayArrow, "Apri") }
                    IconButton(onClick = { tvAction("Chiudi app", "Chiusa ${app.title}") { Tv.closeApp(app.id) } }) { Icon(Icons.Filled.Close, "Chiudi") }
                }
                HorizontalDivider()
            }
        }
        item {
            Text(
                "Per installare o disinstallare app usa la scheda Sviluppatore (modalità sviluppatore LG o Homebrew).",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp),
            )
        }
    }
}
