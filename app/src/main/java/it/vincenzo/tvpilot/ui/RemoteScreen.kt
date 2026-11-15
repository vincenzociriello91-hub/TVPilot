@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vincenzo.tvpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.DesktopAccessDisabled
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.Voice
import it.vincenzo.tvpilot.tv.WebOsClient
import it.vincenzo.tvpilot.tv.Wol
import kotlinx.coroutines.delay

@Composable
fun RemoteScreen() {
    val ctx = LocalContext.current
    val state by Tv.client.stato.collectAsState()
    val haptic = LocalHapticFeedback.current
    var mode by remember { mutableIntStateOf(0) } // 0 tasti, 1 touchpad, 2 tastiera
    var showNumbers by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var hidden by remember { mutableStateOf(Prefs.hiddenSections(ctx)) }
    var favorites by remember { mutableStateOf(Prefs.favoriteApps(ctx)) }
    var customizing by remember { mutableStateOf(false) }

    fun press(name: String) {
        if (Prefs.haptics(ctx)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tvAction("Tasto") { Tv.client.button(name) }
    }

    fun refreshVolume() = tvAction("Volume") { volume = Tv.volume() }

    LaunchedEffect(state) {
        while (state == WebOsClient.State.CONNECTED) {
            runCatching { volume = Tv.volume() }
            delay(5000)
        }
    }

    val connected = state == WebOsClient.State.CONNECTED
    val info by Tv.info.collectAsState()
    var channel by remember { mutableStateOf("") }
    var panel by remember { mutableStateOf<String?>(null) } // touchpad, tastiera, numeri, multimedia, colori, menu
    LaunchedEffect(state) {
        while (state == WebOsClient.State.CONNECTED) {
            runCatching { channel = Tv.client.request("ssap://tv/getCurrentChannel", log = false).optString("channelNumber") }
            delay(10_000)
        }
    }
    fun toggle(p: String) { panel = if (panel == p) null else p }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // intestazione: nome TV, stato, accensione
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    Prefs.savedTvs(ctx).firstOrNull { it.host == Prefs.lastHost(ctx) }?.name ?: "La tua TV",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) OkGreen else MaterialTheme.colorScheme.outline))
                    Text(
                        if (connected) "${info["modello"]?.takeIf { it.isNotBlank() } ?: "TV LG"} · collegata" else "non collegata",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            FilledIconButton(
                onClick = {
                    if (connected) tvAction("Spegnimento", "TV spenta") { Tv.powerOff() }
                    else {
                        val mac = Prefs.mac(ctx)
                        if (mac == null) UiBus.show("Collega la TV almeno una volta da accesa: l'app salva il suo indirizzo per accenderla.")
                        else tvAction("Accensione", "Segnale di accensione inviato") { Tv.powerOn() }
                    }
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (connected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (connected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) { Icon(Icons.Filled.PowerSettingsNew, if (connected) "Spegni la TV" else "Accendi la TV", Modifier.size(26.dp)) }
        }

        if (!connected) ConnectCard()

        VoiceButton()
        FavoritesRow(favorites)

        when (panel) {
            "touchpad" -> Touchpad()
            "tastiera" -> KeyboardPanel()
            else -> DPad(::press)
        }

        // tasti rapidi
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            SquareKey(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") { press("BACK") }
            SquareKey(Icons.Filled.Home, "Home") { press("HOME") }
            SquareKey(Icons.AutoMirrored.Filled.VolumeOff, "Muto") { tvAction("Muto") { Tv.setMute(!(volume?.second ?: false)); refreshVolume() } }
            SquareKey(Icons.Filled.Tune, "Impostazioni rapide") { press("QMENU") }
            SquareKey(Icons.Filled.Keyboard, "Tastiera", selected = panel == "tastiera") { toggle("tastiera") }
        }

        // volume e canali
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PillRocker("VOLUME", volume?.let { if (it.second) "muto" else "${it.first}" } ?: "–", primary = true, modifier = Modifier.weight(1f),
                onDown = { tvAction("Volume") { Tv.volumeDown(); refreshVolume() } },
                onUp = { tvAction("Volume") { Tv.volumeUp(); refreshVolume() } })
            PillRocker("CANALE", channel.ifBlank { "–" }, primary = false, modifier = Modifier.weight(1f),
                onDown = { tvAction("Canale") { Tv.channelDown() } },
                onUp = { tvAction("Canale") { Tv.channelUp() } })
        }

        // altri tasti, su richiesta
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chips = buildList {
                add("touchpad" to "Touchpad")
                add("numeri" to "Numeri")
                if ("multimedia" !in hidden) add("multimedia" to "Play/pausa")
                if ("colori" !in hidden) add("colori" to "Tasti colorati")
                if ("sistema" !in hidden) add("menu" to "Menu e info")
                add("schermo" to "Solo schermo off")
            }
            items(chips.size) { i ->
                val (key, label) = chips[i]
                androidx.compose.material3.FilterChip(
                    selected = panel == key,
                    onClick = {
                        if (key == "schermo") tvAction("Schermo", "Schermo spento: l'audio continua. Premi un tasto per riaccenderlo") { Tv.screenOff() }
                        else toggle(key)
                    },
                    label = { Text(label) },
                )
            }
        }

        when (panel) {
            "numeri" -> NumberPad(::press)
            "multimedia" -> Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                RoundKey(Icons.Filled.FastRewind, "Indietro veloce") { press("REWIND") }
                RoundKey(Icons.Filled.PlayArrow, "Play") { tvAction("Play") { Tv.play() } }
                RoundKey(Icons.Filled.Pause, "Pausa") { tvAction("Pausa") { Tv.pause() } }
                RoundKey(Icons.Filled.Stop, "Stop") { press("STOP") }
                RoundKey(Icons.Filled.FastForward, "Avanti veloce") { press("FASTFORWARD") }
            }
            "colori" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("RED" to Color(0xFFD93025), "GREEN" to Color(0xFF34A853), "YELLOW" to Color(0xFFF9AB00), "BLUE" to Color(0xFF1A73E8))
                    .forEach { (name, c) ->
                        Surface(onClick = { press(name) }, shape = RoundedCornerShape(50), color = c, modifier = Modifier.weight(1f).height(40.dp)) {}
                    }
            }
            "menu" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { press("MENU") }) { Icon(Icons.Filled.Menu, null); Spacer(Modifier.width(6.dp)); Text("Menu") }
                FilledTonalButton(onClick = { press("INFO") }) { Icon(Icons.Filled.Info, null); Spacer(Modifier.width(6.dp)); Text("Info") }
                FilledTonalButton(onClick = { press("EXIT") }) { Text("Esci") }
            }
            else -> Unit
        }

        TextButton(onClick = { customizing = true }, modifier = Modifier.fillMaxWidth()) { Text("Personalizza il telecomando") }
        Spacer(Modifier.height(16.dp))
    }

    if (customizing) CustomizeDialog(
        hidden, favorites,
        onDismiss = { customizing = false },
    ) { h, f ->
        hidden = h; favorites = f
        Prefs.setHiddenSections(ctx, h); Prefs.setFavoriteApps(ctx, f)
        customizing = false
    }
}

/** App preferite: un tocco e si aprono sulla TV */
@Composable
private fun FavoritesRow(favorites: List<String>) {
    if (favorites.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(favorites.size) { i ->
            val parts = favorites[i].split('|', limit = 2)
            val id = parts[0]
            val title = parts.getOrElse(1) { parts[0] }
            androidx.compose.material3.AssistChip(onClick = { tvAction("Apri app", "Aperta $title") { Tv.launch(id) } }, label = { Text(title) })
        }
    }
}

/** Scegli le app in prima pagina e le sezioni da mostrare */
@Composable
private fun CustomizeDialog(hidden: Set<String>, favorites: List<String>, onDismiss: () -> Unit, onSave: (Set<String>, List<String>) -> Unit) {
    val h = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(hidden) } }
    val f = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(favorites) } }
    var apps by remember { mutableStateOf<List<Tv.AppItem>>(emptyList()) }
    LaunchedEffect(Unit) { runCatching { apps = Tv.apps() } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personalizza") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sezioni", fontWeight = FontWeight.SemiBold)
                listOf("sistema" to "Tasti Indietro/Home/Menu", "multimedia" to "Tasti multimedia", "colori" to "Tasti colorati").forEach { (k, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(k !in h, { on -> if (on) h.remove(k) else h.add(k) })
                        Text(l)
                    }
                }
                Text("App in prima pagina", fontWeight = FontWeight.SemiBold)
                if (apps.isEmpty()) Text("Collega la TV per vedere le app.", style = MaterialTheme.typography.bodySmall)
                apps.forEach { a ->
                    val key = "${a.id}|${a.title}"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(f.any { it.startsWith(a.id + "|") }, { on ->
                            if (on) f.add(key) else f.removeAll { it.startsWith(a.id + "|") }
                        })
                        Text(a.title)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(h.toSet(), f.toList()) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Microfono: riconoscimento vocale del telefono (qualsiasi Android con Google o servizio vocale) */
@Composable
private fun VoiceButton() {
    var heard by remember { mutableStateOf<String?>(null) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val text = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (text != null) {
            val cmd = Voice.parse(text)
            heard = "\"$text\""
            tvAction("Voce") { UiBus.show(Voice.execute(cmd)) }
        }
    }
    FilledTonalButton(
        onClick = {
            val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Es. \"apri YouTube\", \"volume 20\", \"spegni tra 30 minuti\"")
            runCatching { launcher.launch(i) }.onFailure { UiBus.show("Riconoscimento vocale non disponibile su questo telefono") }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(8.dp))
        Text(heard?.let { "Ultimo comando: $it" } ?: "Comando vocale")
    }
}

/** Tasto quadrato arrotondato (stile A) */
@Composable
private fun SquareKey(icon: ImageVector, desc: String, selected: Boolean = false, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = if (selected) IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
        ) else IconButtonDefaults.filledTonalIconButtonColors(),
    ) { Icon(icon, desc) }
}

/** Volume / canale: pillola orizzontale con − valore + */
@Composable
private fun PillRocker(label: String, value: String, primary: Boolean, modifier: Modifier, onDown: () -> Unit, onUp: () -> Unit) {
    val colors = if (primary) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.filledTonalIconButtonColors()
    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.height(68.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.padding(horizontal = 7.dp)) {
            FilledIconButton(onClick = onDown, modifier = Modifier.size(54.dp), colors = colors) { Icon(Icons.Filled.Remove, "$label giù") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            FilledIconButton(onClick = onUp, modifier = Modifier.size(54.dp), colors = colors) { Icon(Icons.Filled.Add, "$label su") }
        }
    }
}

@Composable
private fun RoundKey(icon: ImageVector, desc: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) { Icon(icon, desc) }
}

@Composable
private fun Rocker(label: String, value: String?, modifier: Modifier, onUp: () -> Unit, onDown: () -> Unit) {
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
            FilledIconButton(onClick = onUp, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Add, "$label su") }
            Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(value ?: " ", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
            FilledIconButton(onClick = onDown, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Remove, "$label giù") }
        }
    }
}

@Composable
private fun DPad(press: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(252.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val big = Modifier.size(72.dp)
            val colors = IconButtonDefaults.iconButtonColors()
            Box(Modifier.align(Alignment.TopCenter)) {
                androidx.compose.material3.IconButton(onClick = { press("UP") }, big, colors = colors) { Icon(Icons.Filled.KeyboardArrowUp, "Su", Modifier.size(44.dp)) }
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                androidx.compose.material3.IconButton(onClick = { press("DOWN") }, big) { Icon(Icons.Filled.KeyboardArrowDown, "Giù", Modifier.size(44.dp)) }
            }
            Box(Modifier.align(Alignment.CenterStart)) {
                androidx.compose.material3.IconButton(onClick = { press("LEFT") }, big) { Icon(Icons.Filled.KeyboardArrowLeft, "Sinistra", Modifier.size(44.dp)) }
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                androidx.compose.material3.IconButton(onClick = { press("RIGHT") }, big) { Icon(Icons.Filled.KeyboardArrowRight, "Destra", Modifier.size(44.dp)) }
            }
            FilledIconButton(onClick = { press("ENTER") }, modifier = Modifier.size(106.dp)) {
                Text("OK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Touchpad() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.4f)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { tvAction("Clic") { Tv.client.click() } })
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val dx = (drag.x * 1.4f).toInt()
                        val dy = (drag.y * 1.4f).toInt()
                        // i movimenti sono decine al secondo: solo con la TV già collegata, senza ricollegamento a ogni movimento
                        if ((dx != 0 || dy != 0) && Tv.client.connected) tvAction("Puntatore", reconnect = false) { Tv.client.move(dx, dy) }
                    }
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Trascina per muovere il puntatore\nTocca per fare clic", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { tvAction("Scorri") { Tv.client.scroll(-3) } }) { Text("Scorri su") }
            TextButton(onClick = { tvAction("Scorri") { Tv.client.scroll(3) } }) { Text("Scorri giù") }
        }
    }
}

@Composable
private fun KeyboardPanel() {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Apri un campo di testo sulla TV (es. ricerca YouTube), poi scrivi qui.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(text, { text = it }, label = { Text("Testo da inviare") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { val t = text; tvAction("Testo", "Testo inviato") { Tv.insertText(t) }; text = "" }) { Text("Invia") }
            FilledTonalIconButton(onClick = { tvAction("Cancella") { Tv.deleteChars(1) } }) { Icon(Icons.Filled.Backspace, "Cancella") }
            FilledTonalIconButton(onClick = { tvAction("Invio") { Tv.enterKey() } }) { Icon(Icons.AutoMirrored.Filled.KeyboardReturn, "Invio") }
        }
    }
}

@Composable
private fun NumberPad(press: (String) -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("-", "0", "LIST"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                r.forEach { k ->
                    FilledTonalButton(
                        onClick = { press(if (k == "-") "DASH" else k) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (k == "LIST") "Lista" else k) }
                }
            }
        }
    }
}
