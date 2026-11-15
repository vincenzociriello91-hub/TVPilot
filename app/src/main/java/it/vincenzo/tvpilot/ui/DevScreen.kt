package it.vincenzo.tvpilot.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Backups
import it.vincenzo.tvpilot.tv.Catalog
import it.vincenzo.tvpilot.tv.FullBackup
import it.vincenzo.tvpilot.tv.Homebrew
import it.vincenzo.tvpilot.tv.Risk
import it.vincenzo.tvpilot.tv.Risks
import it.vincenzo.tvpilot.tv.SettingsManager
import it.vincenzo.tvpilot.tv.Ssh
import it.vincenzo.tvpilot.tv.Tv
import org.json.JSONObject
import java.io.File

/** Scheda Sviluppatore: impostazioni nascoste (con avvisi e backup), backup, modalità sviluppatore LG, Homebrew/root */
@Composable
fun DevScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Impostazioni nascoste", "Backup", "Sviluppatore LG", "Homebrew (root)")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> NeedTv { HiddenSettings() }
            1 -> BackupList()
            2 -> DevModePanel()
            else -> RootPanel()
        }
    }
}

// ---------------- impostazioni nascoste ----------------

@Composable
private fun HiddenSettings() {
    val ctx = LocalContext.current
    var category by remember { mutableStateOf<String?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var editing by remember { mutableStateOf<Catalog.Item?>(null) }
    var loading by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    val cat = category
    if (cat == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Notice(
                "Qui ci sono le impostazioni interne di webOS che il menu della TV non mostra. Ogni modifica salva prima un backup " +
                    "(scheda Backup). Le voci rosse possono bloccare menu o telecomando: cambiale solo se sai cosa fanno.",
                Risk.MEDIUM, "Zona sviluppatore",
            )
            Catalog.categories(ctx).forEach { c ->
                val items = Catalog.items(ctx, c)
                val worst = items.maxOfOrNull { it.risk.ordinal }?.let { Risk.entries[it] } ?: Risk.LOW
                Row(
                    Modifier.fillMaxWidth().clickable { category = c; values.clear() }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Risks.categoryName(c), style = MaterialTheme.typography.titleMedium)
                        Text("${items.size} voci", style = MaterialTheme.typography.bodySmall)
                    }
                    RiskChip(worst)
                }
                HorizontalDivider()
            }
        }
        return
    }

    val items = remember(cat) { Catalog.items(ctx, cat) }
    LaunchedEffect(cat) {
        loading = true
        val read = runCatching { SettingsManager.readCategory(ctx, cat, items.map { it.key }) }.getOrDefault(emptyMap())
        read.forEach { (k, v) -> if (v != null) values[k] = Catalog.show(v) }
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { category = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
            Text(Risks.categoryName(cat), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.padding(4.dp))
        }
        Text(
            "Valori letti dalla TV: ${values.size}/${items.size}. Dove la TV non lascia leggere, è mostrato il valore tipico di fabbrica.",
            style = MaterialTheme.typography.bodySmall,
        )
        lastResult?.let { Notice(it, title = "Ultima modifica") }
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { editing = item }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                    val v = values[item.key]
                    Text(
                        if (v != null) "Attuale: ${v.take(80)}" else "Tipico: ${Catalog.show(item.typical).take(80)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RiskChip(item.risk)
            }
            HorizontalDivider()
        }
    }

    editing?.let { item ->
        EditDialog(item, values[item.key], onDismiss = { editing = null }) { newValue ->
            editing = null
            tvAction("Modifica") {
                val r = SettingsManager.apply(ctx, item.category, JSONObject().put(item.key, newValue), "Sviluppatore: ${item.category}.${item.key}")
                lastResult = r.message
                if (r.ok) values[item.key] = Catalog.show(newValue)
                reportOperation("impostazione")
            }
        }
    }
}

@Composable
private fun EditDialog(item: Catalog.Item, current: String?, onDismiss: () -> Unit, onApply: (Any) -> Unit) {
    val choices = Catalog.choices(item.allowed)
    var text by remember { mutableStateOf(current ?: Catalog.show(item.typical).let { if (it == "—") "" else it }) }
    var understood by remember { mutableStateOf(item.risk != Risk.HIGH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.key, fontFamily = FontFamily.Monospace) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RiskChip(item.risk)
                Notice(Risks.explain(item.category, item.key), item.risk)
                Text("Categoria: ${Risks.categoryName(item.category)}", style = MaterialTheme.typography.bodySmall)
                Text("Valore tipico di fabbrica: ${Catalog.show(item.typical)}", style = MaterialTheme.typography.bodySmall)
                Text("Valore attuale: ${current ?: "non leggibile"}", style = MaterialTheme.typography.bodySmall)
                if (item.allowed is JSONObject) Text("Valori ammessi: ${item.allowed}", style = MaterialTheme.typography.bodySmall)
                if (choices.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    choices.forEach { c -> FilterChip(selected = text == c, onClick = { text = c }, label = { Text(c) }) }
                }
                OutlinedTextField(text, { text = it }, label = { Text("Nuovo valore") }, modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp))
                Text("Prima di applicare verrà salvato un backup del valore attuale.", style = MaterialTheme.typography.bodySmall)
                if (item.risk == Risk.HIGH) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(understood, { understood = it })
                    Text("Ho capito il rischio e voglio procedere")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(Catalog.parse(text, item.typical)) }, enabled = understood && text.isNotBlank()) { Text("Applica con backup") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

// ---------------- backup ----------------

@Composable
private fun BackupList() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Backups.list(ctx)) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("Backup delle modifiche", Icons.Filled.Backup) {
            Text(
                "Dove finiscono: nella memoria dell'app (questo elenco) e come file in Download/${Backups.PUBLIC_FOLDER} sul telefono, " +
                    "uno per modifica. Il file contiene i valori \"prima\" e \"dopo\".\n\n" +
                    "Come si ripristina: tocca Ripristina sulla modifica da annullare, con la TV collegata. " +
                    "L'app rimette sulla TV i valori \"prima\". Se il backup dice \"valori tipici\", la TV non lasciava leggere il valore " +
                    "originale e viene rimesso quello di fabbrica.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FullBackupSection()
        msg?.let { Notice(it, title = "Esito") }
        if (list.isEmpty()) Text("Nessun backup: non hai ancora modificato impostazioni.", style = MaterialTheme.typography.bodyMedium)
        list.forEach { b ->
            Section(
                Risks.categoryName(b.category),
                subtitle = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALY).format(java.util.Date(b.time)) +
                    if (b.restored) " · ripristinato" else "",
            ) {
                Text(b.note, style = MaterialTheme.typography.bodyMedium)
                Text("Prima${if (!b.beforeRead) " (valori tipici)" else ""}: ${b.before}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text("Dopo: ${b.after}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text("Metodo: ${b.method} · File: Download/${Backups.PUBLIC_FOLDER}/${b.id}.json", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        tvAction("Ripristino") {
                            val r = SettingsManager.restore(ctx, b)
                            msg = r.message
                            list = Backups.list(ctx)
                            reportOperation("ripristino")
                        }
                    }) { Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(6.dp)); Text("Ripristina") }
                    OutlinedButton(onClick = { Backups.delete(ctx, b.id); list = Backups.list(ctx) }) {
                        Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Elimina")
                    }
                }
            }
        }
    }
}

// ---------------- backup completo ----------------

@Composable
private fun FullBackupSection() {
    val ctx = LocalContext.current
    var snaps by remember { mutableStateOf(FullBackup.list(ctx)) }
    var progress by remember { mutableStateOf<String?>(null) }
    var diffs by remember { mutableStateOf<List<FullBackup.Diff>?>(null) }
    var includeHigh by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    Section("Backup completo della TV", Icons.Filled.Backup, subtitle = "Tutte le impostazioni che la TV lascia leggere") {
        Text(
            "Fotografa le impostazioni della TV (richiede circa un minuto). Poi puoi confrontarle con lo stato di adesso, " +
                "per esempio dopo un aggiornamento LG, e rimettere quelle cambiate. File in Download/${Backups.PUBLIC_FOLDER}.",
            style = MaterialTheme.typography.bodySmall,
        )
        progress?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.padding(end = 8.dp)); Text(it) } }
        result?.let { Notice(it, title = "Esito") }
        Button(onClick = {
            tvAction("Backup completo") {
                progress = "Inizio…"
                try {
                    val s = FullBackup.create(ctx) { p -> progress = p }
                    snaps = FullBackup.list(ctx)
                    result = "Salvati ${s.count} valori. File: Download/${Backups.PUBLIC_FOLDER}/${s.id}.json"
                    reportOperation("backup-completo")
                } finally { progress = null }
            }
        }, enabled = progress == null && Tv.client.connected) { Text("Fai il backup completo") }
        snaps.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALY).format(java.util.Date(s.time)))
                    Text("${s.count} valori · firmware ${s.firmware.ifBlank { "?" }}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    tvAction("Confronto") {
                        progress = "Confronto…"
                        try { diffs = FullBackup.compare(ctx, s) { p -> progress = p } } finally { progress = null }
                    }
                }, enabled = progress == null && Tv.client.connected) { Text("Confronta") }
                IconButton(onClick = { FullBackup.delete(ctx, s.id); snaps = FullBackup.list(ctx) }) {
                    Icon(Icons.Filled.Delete, "Elimina")
                }
            }
        }
        diffs?.let { d ->
            HorizontalDivider()
            if (d.isEmpty()) Text("Nessuna differenza: la TV è come nel backup.", fontWeight = FontWeight.SemiBold)
            else {
                Text("${d.size} impostazioni cambiate rispetto al backup", fontWeight = FontWeight.SemiBold)
                d.take(60).forEach { x ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Risks.categoryName(x.category)} · ${x.key}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text("prima ${Catalog.show(x.saved).take(40)} → ora ${Catalog.show(x.now).take(40)}", style = MaterialTheme.typography.bodySmall)
                        }
                        RiskChip(x.risk)
                    }
                }
                if (d.size > 60) Text("…e altre ${d.size - 60}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeHigh, { includeHigh = it })
                    Text("Rimetti anche quelle pericolose")
                }
                FilledTonalButton(onClick = {
                    val list = d
                    tvAction("Ripristino completo") {
                        result = FullBackup.restore(list, includeHigh)
                        diffs = null
                        reportOperation("ripristino-completo")
                    }
                }) { Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(6.dp)); Text("Rimetti come nel backup") }
            }
        }
    }
}

// ---------------- installazione app (comune a sviluppatore e root) ----------------

@Composable
private fun AppInstaller(mode: Ssh.Mode) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<List<Ssh.TvApp>>(emptyList()) }
    var catalog by remember { mutableStateOf<List<Homebrew.Package>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() = tvAction("Elenco app") { installed = Ssh.listApps(mode) }
    LaunchedEffect(mode) { refresh() }

    fun install(file: File, label: String) {
        busy = true
        tvAction("Installazione") {
            status = "$label: preparazione…"
            status = try { Ssh.installIpk(mode, file) { p -> status = "$label: $p" } } catch (e: Exception) { "Errore: ${e.message}" }
            busy = false
            refresh()
            reportOperation("installazione")
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val f = File(ctx.cacheDir, "scelto.ipk")
            runCatching { ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } }
                .onSuccess { install(f, "File dal telefono") }
                .onFailure { status = "File non leggibile: ${it.message}" }
        }
    }

    Section("Installa app sulla TV", Icons.Filled.Download, subtitle = mode.label) {
        status?.let { Notice(it, title = "Stato") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch("*/*") }, enabled = !busy) {
                Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("File .ipk")
            }
            OutlinedButton(onClick = { tvAction("Catalogo") { catalog = Homebrew.all() } }) {
                Icon(Icons.Filled.Storefront, null); Spacer(Modifier.width(6.dp)); Text("Catalogo")
            }
        }
        catalog?.let { list ->
            Text("Catalogo Homebrew: ${list.size} app", fontWeight = FontWeight.SemiBold)
            list.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (p.tip != null) "★ " else "") + "${p.title} ${p.version}", style = MaterialTheme.typography.titleSmall)
                        if (p.tip != null) Text("Consigliata: ${p.tip}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text("${p.description.take(110)} · ${p.rootRequired}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        tvAction("Download") { install(Homebrew.download(p, ctx.cacheDir), p.title) }
                    }, enabled = !busy) { Text("Installa") }
                }
                HorizontalDivider()
            }
        }
    }

    Section("App installate", subtitle = if (mode == Ssh.Mode.DEV) "Solo quelle installate in modalità sviluppatore" else "App rimovibili") {
        if (installed.isEmpty()) Text("Nessuna (o non ancora collegato).", style = MaterialTheme.typography.bodySmall)
        installed.forEach { a ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.title.ifBlank { a.id }); Text("${a.id} ${a.version}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = {
                    tvAction("Rimozione") { status = Ssh.removeApp(mode, a.id); refresh(); reportOperation("rimozione") }
                }) { Icon(Icons.Filled.Delete, "Rimuovi") }
            }
        }
        TextButton(onClick = { refresh() }) { Text("Aggiorna elenco") }
    }
}

// ---------------- modalità sviluppatore LG ----------------

@Composable
private fun DevModePanel() {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    var pass by remember { mutableStateOf(Prefs.devPassphrase(ctx) ?: "") }
    var connected by remember { mutableStateOf(Ssh.session(Ssh.Mode.DEV) != null) }
    var session by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("Modalità sviluppatore ufficiale LG", Icons.Filled.Code, subtitle = "Senza root, non tocca la garanzia") {
            Text(
                "1. Crea un account gratuito su developer.lge.com.\n" +
                    "2. Sulla TV apri LG Content Store, cerca e installa \"Developer Mode\".\n" +
                    "3. Apri Developer Mode, accedi con l'account, attiva Dev Mode Status (la TV si riavvia).\n" +
                    "4. Riapri Developer Mode e attiva Key Server: compare una passphrase di 6 caratteri.\n" +
                    "5. Scrivila qui sotto e premi Collega.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { uri.openUri("https://webostv.developer.lge.com/develop/getting-started/developer-mode-app") }) {
                Text("Guida LG (sito ufficiale)")
            }
            Notice("La sessione sviluppatore dura circa 50 ore: poi le app installate così smettono di aprirsi finché non la rinnovi da qui.", Risk.MEDIUM)
            OutlinedTextField(
                pass, { pass = it.trim().uppercase() }, label = { Text("Passphrase (sulla TV)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val host = Prefs.lastHost(ctx)
                    if (host == null) UiBus.show("Collega prima la TV dalla scheda Telecomando")
                    else tvAction("Sviluppatore", "Collegato alla modalità sviluppatore") {
                        Ssh.connectDev(host, pass)
                        Prefs.setDevPassphrase(ctx, pass)
                        connected = true
                    }
                }, enabled = pass.length >= 6) { Text(if (connected) "Ricollega" else "Collega") }
                if (connected) OutlinedButton(onClick = { Ssh.disconnect(Ssh.Mode.DEV); connected = false }) { Text("Scollega") }
            }
        }
        if (connected) {
            Section("Sessione", Icons.Filled.Timer) {
                session?.let { Text("Tempo rimasto: $it") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        tvAction("Sessione") {
                            val t = Ssh.devToken() ?: error("token non leggibile")
                            session = Ssh.devRemaining(t)
                        }
                    }) { Text("Controlla") }
                    Button(onClick = {
                        tvAction("Rinnovo") {
                            val t = Ssh.devToken() ?: error("token non leggibile")
                            val ok = Ssh.devExtend(t)
                            session = if (ok) Ssh.devRemaining(t) else "rinnovo rifiutato da LG"
                            UiBus.show(if (ok) "Sessione rinnovata" else "Rinnovo non riuscito")
                        }
                    }) { Text("Rinnova ora") }
                }
            }
            AppInstaller(Ssh.Mode.DEV)
        }
        Spacer(Modifier.padding(12.dp))
    }
}

// ---------------- Homebrew Channel / root ----------------

@Composable
private fun RootPanel() {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    var password by remember { mutableStateOf(Prefs.rootPassword(ctx)) }
    var connected by remember { mutableStateOf(Ssh.session(Ssh.Mode.ROOT) != null) }
    var info by remember { mutableStateOf<String?>(null) }
    var hbInstalled by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        if (Tv.client.connected) hbInstalled = runCatching { Tv.apps().any { it.id == Homebrew.HBCHANNEL_ID } }.getOrNull()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("Homebrew Channel e root", Icons.Filled.LockOpen, subtitle = "Per utenti esperti") {
            Notice(
                "Il root sfrutta una falla di webOS: può far decadere la garanzia, un aggiornamento LG può toglierlo, " +
                    "e un errore nei comandi da root può rendere la TV instabile. Prima di farlo disattiva gli aggiornamenti automatici della TV.",
                Risk.HIGH, "Leggi prima",
            )
            Text(
                when (hbInstalled) {
                    true -> "Homebrew Channel è installato sulla TV."
                    false -> "Homebrew Channel non risulta installato."
                    null -> "Collega la TV per controllare se Homebrew Channel è installato."
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Come ottenere il root (lo fai tu, dipende dal firmware):\n" +
                    "• Firmware vecchi: RootMyTV (si apre il sito dal browser della TV).\n" +
                    "• Firmware più recenti: gli exploit indicati da webosbrew.org per webOS 4.5.\n" +
                    "Dopo il root, in Homebrew Channel → Impostazioni attiva \"SSH Server\" (utente root, password alpine).\n" +
                    "Senza root puoi comunque usare Homebrew Channel installandolo dalla scheda Sviluppatore LG.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { uri.openUri("https://www.webosbrew.org/rooting/") }) { Text("webosbrew.org") }
                TextButton(onClick = { uri.openUri("https://rootmytv.github.io/") }) { Text("RootMyTV") }
            }
            Text("Firmware attuale della TV: ${Prefs.firmware(ctx) ?: "collega la TV"}", style = MaterialTheme.typography.bodySmall)
        }
        Section("Collegamento root (SSH)", Icons.Filled.Tune) {
            OutlinedTextField(
                password, { password = it }, label = { Text("Password root") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Predefinita di Homebrew Channel: alpine. Cambiala sulla TV con il comando passwd.") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val host = Prefs.lastHost(ctx)
                    if (host == null) UiBus.show("Collega prima la TV dalla scheda Telecomando")
                    else tvAction("Root", "Collegato come root") {
                        Ssh.connectRoot(host, password)
                        Prefs.setRootPassword(ctx, password)
                        connected = true
                        info = Ssh.rootInfo()
                        Reports.log("Root: ${info?.lineSequence()?.firstOrNull()}")
                    }
                }) { Text(if (connected) "Ricollega" else "Collega come root") }
                if (connected) OutlinedButton(onClick = { Ssh.disconnect(Ssh.Mode.ROOT); connected = false }) { Text("Scollega") }
            }
            info?.let { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
        if (connected) AppInstaller(Ssh.Mode.ROOT)
        Spacer(Modifier.padding(12.dp))
    }
}
