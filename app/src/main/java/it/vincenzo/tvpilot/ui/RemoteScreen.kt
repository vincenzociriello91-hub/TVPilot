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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state != WebOsClient.State.CONNECTED) ConnectCard()

        // accensione / spegnimento
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = {
                val mac = Prefs.mac(ctx)
                if (mac == null) UiBus.show("Collega la TV almeno una volta da accesa: l'app salva il suo indirizzo per accenderla.")
                else tvAction("Accensione", "Segnale di accensione inviato") { Wol.wake(mac, Prefs.lastHost(ctx)) }
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PowerSettingsNew, null, tint = Color(0xFF34A853)); Spacer(Modifier.width(6.dp)); Text("Accendi")
            }
            FilledTonalButton(onClick = { tvAction("Spegnimento", "TV spenta") { Tv.powerOff() } }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PowerSettingsNew, null, tint = Color(0xFFD93025)); Spacer(Modifier.width(6.dp)); Text("Spegni")
            }
            FilledTonalIconButton(onClick = { tvAction("Schermo", "Schermo spento: l'audio continua. Premi un tasto per riaccenderlo") { Tv.screenOff() } }) {
                Icon(Icons.Filled.DesktopAccessDisabled, "Spegni solo lo schermo")
            }
        }

        VoiceButton()
        FavoritesRow(favorites)

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Tasti" to Icons.Filled.Dialpad, "Touchpad" to Icons.Filled.Mouse, "Tastiera" to Icons.Filled.Keyboard)
                .forEachIndexed { i, (label, icon) ->
                    SegmentedButton(
                        selected = mode == i, onClick = { mode = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        icon = { Icon(icon, null, Modifier.size(18.dp)) },
                    ) { Text(label) }
                }
        }

        when (mode) {
            0 -> DPad(::press)
            1 -> Touchpad()
            else -> KeyboardPanel()
        }

        // tasti di sistema
        if ("sistema" !in hidden) Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            RoundKey(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") { press("BACK") }
            RoundKey(Icons.Filled.Home, "Home") { press("HOME") }
            RoundKey(Icons.Filled.Settings, "Impostazioni rapide") { press("QMENU") }
            RoundKey(Icons.Filled.Menu, "Menu") { press("MENU") }
            RoundKey(Icons.Filled.Info, "Info") { press("INFO") }
        }

        // volume e canali
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Rocker("VOL", volume?.let { if (it.second) "muto" else "${it.first}" }, Modifier.weight(1f),
                onUp = { tvAction("Volume") { Tv.volumeUp(); refreshVolume() } },
                onDown = { tvAction("Volume") { Tv.volumeDown(); refreshVolume() } })
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundKey(Icons.AutoMirrored.Filled.VolumeOff, "Muto") {
                    tvAction("Muto") { Tv.setMute(!(volume?.second ?: false)); refreshVolume() }
                }
                RoundKey(Icons.Filled.Dialpad, "Numeri") { showNumbers = !showNumbers }
            }
            Rocker("CH", null, Modifier.weight(1f),
                onUp = { tvAction("Canale") { Tv.channelUp() } },
                onDown = { tvAction("Canale") { Tv.channelDown() } })
        }

        if (showNumbers) NumberPad(::press)

        // multimedia
        if ("multimedia" !in hidden) Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            RoundKey(Icons.Filled.FastRewind, "Indietro veloce") { press("REWIND") }
            RoundKey(Icons.Filled.PlayArrow, "Play") { tvAction("Play") { Tv.play() } }
            RoundKey(Icons.Filled.Pause, "Pausa") { tvAction("Pausa") { Tv.pause() } }
            RoundKey(Icons.Filled.Stop, "Stop") { press("STOP") }
            RoundKey(Icons.Filled.FastForward, "Avanti veloce") { press("FASTFORWARD") }
        }

        // tasti colorati
        if ("colori" !in hidden) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("RED" to Color(0xFFD93025), "GREEN" to Color(0xFF34A853), "YELLOW" to Color(0xFFF9AB00), "BLUE" to Color(0xFF1A73E8))
                .forEach { (name, c) ->
                    Surface(
                        onClick = { press(name) }, shape = RoundedCornerShape(50), color = c,
                        modifier = Modifier.weight(1f).height(36.dp),
                    ) {}
                }
        }
        TextButton(onClick = { customizing = true }, modifier = Modifier.fillMaxWidth()) { Text("Personalizza il telecomando") }
        Spacer(Modifier.height(24.dp))
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
            Modifier.size(250.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
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
            FilledIconButton(onClick = { press("ENTER") }, modifier = Modifier.size(96.dp)) {
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
                        if (dx != 0 || dy != 0) tvAction("Puntatore") { Tv.client.move(dx, dy) }
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
