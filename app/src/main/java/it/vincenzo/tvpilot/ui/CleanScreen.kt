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

    // interventi già applicati che la TV non lascia rileggere: si ricavano dall'ultimo backup (non ripristinato)
    val appliedOn = remember { mutableStateMapOf<String, Long>() }
    fun readApplied() {
        val backups = Backups.list(ctx)
        for (a in Cleanup.actions) {
            val last = backups.firstOrNull { b ->
                b.category == a.category && a.values.keys().asSequence().all { k -> b.after.opt(k)?.toString() == a.values.opt(k)?.toString() }
            }
            val sameKeysRestored = backups.firstOrNull { b -> b.category == a.category && b.restored && a.values.keys().asSequence().any { b.after.has(it) } }
            if (last != null && !last.restored && last.method != "in corso" &&
                (sameKeysRestored == null || sameKeysRestored.time < last.time)
            ) appliedOn[a.id] = last.time else appliedOn.remove(a.id)
        }
    }

    fun readStatus() = tvAction("Lettura stato") {
        readApplied()
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
            val read = status[a.id]
            val verified = a.statusOk != null && read == a.statusOk
            val applied = appliedOn[a.id]
            val subtitle = when {
                read != null -> "Valore attuale: $read"
                applied != null -> "Applicato il " + java.text.SimpleDateFormat("dd/MM 'alle' HH:mm", java.util.Locale.ITALY)
                    .format(java.util.Date(applied)) + " (la TV non permette di rileggerlo)"
                else -> "Valore attuale non leggibile dalla TV"
            }
            Section(a.title, subtitle = subtitle) {
                Text(a.description, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RiskChip(a.risk)
                    if (verified || (read == null && applied != null)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = OkGreen)
                        Text(if (verified) "Già fatto" else "Applicato", fontWeight = FontWeight.SemiBold)
                        if (!verified) TextButton(onClick = { confirm = listOf(a) }, enabled = !busy) { Text("Riapplica") }
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
                        readApplied()
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
