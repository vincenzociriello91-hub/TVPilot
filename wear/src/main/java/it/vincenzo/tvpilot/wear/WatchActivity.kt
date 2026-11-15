package it.vincenzo.tvpilot.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Telecomando TVPilot al polso: i comandi vanno al telefono (app TVPilot), che li manda alla TV.
 * La corona gira il volume.
 */
class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WatchRemote() } }
    }
}

private const val PATH = "/tvpilot/azione/"
private const val REPLY = "/tvpilot/esito"

@Composable
private fun WatchRemote() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var status by remember { mutableStateOf("TVPilot") }
    val focus = remember { FocusRequester() }
    var rotary by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val listener = MessageClient.OnMessageReceivedListener { e ->
            if (e.path == REPLY) status = String(e.data)
        }
        val client = Wearable.getMessageClient(ctx)
        client.addListener(listener)
        onDispose { client.removeListener(listener) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun send(action: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            status = try {
                val nodes = Wearable.getNodeClient(ctx).connectedNodes.await()
                if (nodes.isEmpty()) "Telefono non collegato"
                else {
                    nodes.forEach { Wearable.getMessageClient(ctx).sendMessage(it.id, PATH + action, ByteArray(0)).await() }
                    "…"
                }
            } catch (e: Exception) {
                "Errore: ${e.message}"
            }
        }
    }

    val state = rememberScalingLazyListState()
    ScalingLazyColumn(
        state = state,
        modifier = Modifier
            .onRotaryScrollEvent { ev ->
                // la corona regola il volume: uno scatto ogni ~60 pixel di rotazione
                rotary += ev.verticalScrollPixels
                if (rotary > 60) { rotary = 0f; send("VOL_UP") }
                if (rotary < -60) { rotary = 0f; send("VOL_DOWN") }
                true
            }
            .focusRequester(focus)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(status, textAlign = TextAlign.Center, style = MaterialTheme.typography.caption1) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key(Icons.Filled.PowerSettingsNew, "Accendi/Spegni", Color(0xFFFF6B6B)) { send("POWER_TOGGLE") }
                Key(Icons.Filled.VolumeOff, "Muto") { send("MUTE") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key(Icons.Filled.Remove, "Volume giù") { send("VOL_DOWN") }
                Key(Icons.Filled.Add, "Volume su") { send("VOL_UP") }
            }
        }
        item { Key(Icons.Filled.KeyboardArrowUp, "Su") { send("UP") } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Key(Icons.Filled.KeyboardArrowLeft, "Sinistra") { send("LEFT") }
                Button(onClick = { send("OK") }) { Text("OK") }
                Key(Icons.Filled.KeyboardArrowRight, "Destra") { send("RIGHT") }
            }
        }
        item { Key(Icons.Filled.KeyboardArrowDown, "Giù") { send("DOWN") } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") { send("BACK") }
                Key(Icons.Filled.Home, "Home") { send("HOME") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key(Icons.Filled.PlayArrow, "Play") { send("PLAY") }
                Key(Icons.Filled.Pause, "Pausa") { send("PAUSE") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { send("CH_DOWN") }, colors = ButtonDefaults.secondaryButtonColors()) { Text("CH−") }
                Button(onClick = { send("CH_UP") }, colors = ButtonDefaults.secondaryButtonColors()) { Text("CH+") }
            }
        }
        item {
            Text(
                "Gira la corona per il volume", style = MaterialTheme.typography.caption3,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Key(icon: ImageVector, desc: String, tint: Color? = null, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.secondaryButtonColors()) {
        Icon(icon, desc, tint = tint ?: MaterialTheme.colors.onSecondary)
    }
}
