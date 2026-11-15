package it.vincenzo.tvpilot.ui

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.quick.NotifyListener
import it.vincenzo.tvpilot.quick.Schedules
import it.vincenzo.tvpilot.quick.ShortcutActivity
import it.vincenzo.tvpilot.tv.Routines
import it.vincenzo.tvpilot.tv.Tv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Scheda Funzioni: Media, Canali e profili, Automazioni, App della TV */
@Composable
fun FeaturesScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Mirroring e media", "Canali e profili", "Automazioni", "App e ingressi")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> NeedTv { Scroll { MirrorCard(); MediaCard() } }
            1 -> NeedTv { Scroll { ProfilesCard(); ChannelsCard() } }
            2 -> Scroll { RoutinesCard(); SchedulesCard(); LimitCard(); PhoneTvCard() }
            else -> AppsScreen()
        }
    }
}

@Composable
private fun Scroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
        Spacer(Modifier.padding(12.dp))
    }
}

// ---------------- routine ----------------

@Composable
private fun RoutinesCard() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Routines.all(ctx)) }
    var editing by remember { mutableStateOf<Routines.Routine?>(null) }
    Section("Routine", Icons.Filled.AutoAwesome, subtitle = "Più comandi in un tocco") {
        list.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text(r.steps.joinToString(" · ") { Routines.describe(it) }, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { tvAction("Routine") { UiBus.show(Routines.run(r)) } }) { Icon(Icons.Filled.PlayArrow, "Avvia") }
                TextButton(onClick = { editing = r }) { Text("Modifica") }
            }
            HorizontalDivider()
        }
        OutlinedButton(onClick = { editing = Routines.Routine("r${System.currentTimeMillis()}", "Nuova routine", emptyList()) }) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Nuova routine")
        }
        Text("Le prime due routine compaiono anche tenendo premuta l'icona di TVPilot.", style = MaterialTheme.typography.bodySmall)
    }
    editing?.let { r ->
        RoutineEditor(r, onDismiss = { editing = null }, onDelete = {
            Routines.delete(ctx, r.id); list = Routines.all(ctx); editing = null; ShortcutActivity.updateRoutineShortcuts(ctx)
        }) { saved ->
            Routines.upsert(ctx, saved); list = Routines.all(ctx); editing = null; ShortcutActivity.updateRoutineShortcuts(ctx)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditor(r: Routines.Routine, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Routines.Routine) -> Unit) {
    var name by remember { mutableStateOf(r.name) }
    val steps = remember { mutableStateListOf<Routines.Step>().apply { addAll(r.steps) } }
    var adding by remember { mutableStateOf<Routines.StepType?>(null) }
    var apps by remember { mutableStateOf<List<Tv.AppItem>>(emptyList()) }
    LaunchedEffect(adding) { if (adding == Routines.StepType.APP && apps.isEmpty()) runCatching { apps = Tv.apps() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Routine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${Routines.describe(s)}", Modifier.weight(1f))
                        IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Filled.Delete, "Togli") }
                    }
                }
                Text("Aggiungi un passo", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Routines.StepType.entries.forEach { t ->
                        FilterChip(selected = adding == t, onClick = {
                            if (t == Routines.StepType.POWER_ON || t == Routines.StepType.POWER_OFF) { steps += Routines.Step(t); adding = null }
                            else adding = t
                        }, label = { Text(t.label) })
                    }
                }
                val type = adding
                val choices: List<Pair<String, String>> = when (type) {
                    Routines.StepType.PICTURE -> Tv.PICTURE_MODES
                    Routines.StepType.SOUND -> Tv.SOUND_OUTPUTS
                    Routines.StepType.ENERGY -> Routines.ENERGY
                    Routines.StepType.INPUT -> (1..4).map { "HDMI_$it" to "HDMI $it" }
                    Routines.StepType.SLEEP -> listOf(10, 20, 30, 60, 90, 120).map { "$it" to "$it min" }
                    Routines.StepType.VOLUME -> listOf(5, 10, 15, 20, 25, 30, 40).map { "$it" to "$it" }
                    Routines.StepType.APP -> apps.map { "${it.id}|${it.title}" to it.title }
                    else -> emptyList()
                }
                if (type != null && choices.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { (value, label) ->
                        FilterChip(selected = false, onClick = { steps += Routines.Step(type, value); adding = null }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Routines.Routine(r.id, name.ifBlank { "Routine" }, steps.toList())) }) { Text("Salva") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Elimina") }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}

// ---------------- programmi orari ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedulesCard() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Schedules.all(ctx)) }
    var adding by remember { mutableStateOf(false) }
    Section("Programmi orari", Icons.Filled.Schedule, subtitle = "Accendi, spegni o avvia una routine a un'ora precisa") {
        if (list.isEmpty()) Text("Nessun programma.", style = MaterialTheme.typography.bodySmall)
        list.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("${s.label} · ${Schedules.actionLabel(ctx, s.action)}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (s.days.isEmpty()) "Tutti i giorni" else Schedules.DAYS.filter { it.first in s.days }.joinToString(" ") { it.second },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(s.enabled, { on -> list = list.map { if (it.id == s.id) it.copy(enabled = on) else it }; Schedules.save(ctx, list) })
                IconButton(onClick = { list = list.filter { it.id != s.id }; Schedules.save(ctx, list) }) { Icon(Icons.Filled.Delete, "Elimina") }
            }
        }
        OutlinedButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Nuovo programma") }
        Text(
            "L'orario può slittare di qualche minuto per risparmiare batteria. L'accensione richiede Quick Start+ (scheda Pulizia).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (adding) {
        var hour by remember { mutableStateOf("21") }
        var minute by remember { mutableStateOf("00") }
        val days = remember { mutableStateListOf<Int>() }
        var action by remember { mutableStateOf("POWER_OFF") }
        val routines = remember { Routines.all(ctx) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Nuovo programma") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("Ora") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Text(":")
                        OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("Minuti") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Text("Giorni (nessuno = tutti)", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Schedules.DAYS.forEach { (d, l) ->
                            FilterChip(selected = d in days, onClick = { if (d in days) days.remove(d) else days.add(d) }, label = { Text(l) })
                        }
                    }
                    Text("Cosa fare", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("POWER_ON" to "Accendi", "POWER_OFF" to "Spegni") + routines.map { "ROUTINE:${it.id}" to it.name }).forEach { (a, l) ->
                            FilterChip(selected = action == a, onClick = { action = a }, label = { Text(l) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 21
                    val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    list = list + Schedules.Schedule("p${System.currentTimeMillis()}", h, m, days.toSet(), action)
                    Schedules.save(ctx, list)
                    adding = false
                    reportOperation("programma")
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Annulla") } },
        )
    }
}

// ---------------- limite di tempo ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitCard() {
    val ctx = LocalContext.current
    var end by remember { mutableStateOf(Schedules.limitEnd(ctx)) }
    val active = end > System.currentTimeMillis()
    Section("Limite di tempo", Icons.Filled.HourglassBottom, subtitle = "Per bambini o ospiti") {
        if (active) {
            Text("La TV si spegne alle ${SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(end))} (avviso sullo schermo 5 minuti prima).")
            OutlinedButton(onClick = { Schedules.cancelLimit(ctx); end = 0 }) { Text("Annulla limite") }
        } else {
            Text("Dopo il tempo scelto la TV mostra un avviso e si spegne. Funziona anche se chiudi l'app.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 60, 90, 120, 180).forEach { m ->
                    FilterChip(selected = false, onClick = {
                        Schedules.startLimit(ctx, m); end = Schedules.limitEnd(ctx)
                        tvAction("Limite") { if (Tv.client.connected) Tv.toast("TVPilot: limite di $m minuti attivo") }
                    }, label = { Text(if (m < 60) "$m min" else "${m / 60}${if (m % 60 == 30) ",5" else ""} h") })
                }
            }
        }
    }
}

// ---------------- telefono e TV ----------------

@Composable
private fun PhoneTvCard() {
    val ctx = LocalContext.current
    val enabled = remember {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        flat.contains(ComponentName(ctx, NotifyListener::class.java).flattenToString())
    }
    var pause by remember { mutableStateOf(NotifyListener.pauseOnCall(ctx)) }
    var msgs by remember { mutableStateOf(NotifyListener.messagesOnTv(ctx)) }
    var text by remember { mutableStateOf(NotifyListener.showText(ctx)) }
    Section("Telefono e TV insieme", Icons.Filled.PhoneInTalk, subtitle = "Pausa sulle chiamate, avvisi sulla TV") {
        if (!enabled) {
            Notice("Serve l'accesso alle notifiche: tocca il pulsante e attiva TVPilot. Le notifiche non escono dal telefono, vanno solo alla tua TV.")
            Button(onClick = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Attiva accesso alle notifiche") }
        } else Text("Attivo. Agisce solo quando la TV è già accesa.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Metti in pausa quando arriva una chiamata", Modifier.weight(1f))
            Switch(pause, { pause = it; NotifyListener.setPauseOnCall(ctx, it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Mostra messaggi e chiamate sulla TV", Modifier.weight(1f))
            Switch(msgs, { msgs = it; NotifyListener.setMessagesOnTv(ctx, it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Mostra anche il testo del messaggio")
                Text("Spento: compaiono solo app e mittente (chi è in stanza non legge)", style = MaterialTheme.typography.bodySmall)
            }
            Switch(text, { text = it; NotifyListener.setShowText(ctx, it) })
        }
        FilledTonalButton(onClick = { tvAction("Prova", "Avviso di prova inviato") { Tv.toast("💬 TVPilot — prova di avviso") } }) { Text("Prova un avviso sulla TV") }
    }
}
