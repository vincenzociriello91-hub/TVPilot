package it.vincenzo.tvpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.tv.Backups
import it.vincenzo.tvpilot.tv.Cleanup
import it.vincenzo.tvpilot.tv.Risk
import it.vincenzo.tvpilot.tv.SettingsManager
import it.vincenzo.tvpilot.tv.Tv

/** Pulizia stile CleanYourTV: pubblicità, tracciamento, avvio, accensione dal telefono. Ogni intervento ha il backup. */
@Composable
fun CleanScreen() = NeedTv {
    val ctx = LocalContext.current
    val status = remember { mutableStateMapOf<String, String>() }
    var confirm by remember { mutableStateOf<List<Cleanup.Action>?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun readStatus() = tvAction("Lettura stato") {
        for (a in Cleanup.actions) {
            val k = a.statusKey ?: continue
            Tv.readSettings(a.category, listOf(k))?.opt(k)?.let { status[a.id] = it.toString() }
        }
    }
    LaunchedEffect(Unit) { readStatus() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("Pulizia della TV", Icons.Filled.CleaningServices, subtitle = "Meno pubblicità, meno tracciamento, avvio più pulito") {
            Notice(
                "Prima di ogni modifica l'app salva un backup dei valori attuali. Lo trovi in Sviluppatore → Backup " +
                    "(con il tasto Ripristina) e come file in Download/${Backups.PUBLIC_FOLDER} sul telefono.",
            )
            Button(onClick = { confirm = Cleanup.actions.filter { it.id !in setOf("accensione", "wol", "eco") } }, enabled = !busy) {
                Icon(Icons.Filled.AutoFixHigh, null); Spacer(Modifier.width(8.dp)); Text("Pulizia consigliata (tutto in una volta)")
            }
        }
        result?.let { Notice(it, title = "Esito") }
        Cleanup.actions.forEach { a ->
            val done = a.statusOk != null && status[a.id] == a.statusOk
            Section(a.title, subtitle = status[a.id]?.let { "Valore attuale: $it" } ?: "Valore attuale non leggibile dalla TV") {
                Text(a.description, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RiskChip(a.risk)
                    if (done) {
                        Icon(Icons.Filled.CheckCircle, null, tint = OkGreen)
                        Text("Già fatto", fontWeight = FontWeight.SemiBold)
                    } else FilledTonalButton(onClick = { confirm = listOf(a) }, enabled = !busy) { Text("Applica") }
                }
            }
        }
        Spacer(Modifier.padding(12.dp))
    }

    confirm?.let { list ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (list.size == 1) list[0].title else "Pulizia consigliata") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { Text("• ${it.title}") }
                    Notice("Backup automatico prima della modifica. Per annullare: Sviluppatore → Backup → Ripristina.", Risk.LOW)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    busy = true
                    tvAction("Pulizia") {
                        val lines = mutableListOf<String>()
                        for (a in list) {
                            val r = SettingsManager.apply(ctx, a.category, a.values, "Pulizia: ${a.title}")
                            lines += (if (r.ok) "✓ " else "✗ ") + a.title + if (!r.ok) " — ${r.message}" else ""
                            if (r.ok) a.statusOk?.let { status[a.id] = it }
                        }
                        result = lines.joinToString("\n") + "\nBackup in Download/${Backups.PUBLIC_FOLDER}"
                        busy = false
                        reportOperation("pulizia")
                    }
                }) { Text("Applica") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annulla") } },
        )
    }
}
