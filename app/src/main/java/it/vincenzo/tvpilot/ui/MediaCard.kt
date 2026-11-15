package it.vincenzo.tvpilot.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.media.MediaCast
import it.vincenzo.tvpilot.tv.Tv

/** Foto, video e musica del telefono sulla TV (DLNA) e link da aprire sulla TV */
@Composable
fun MediaCard() {
    val ctx = LocalContext.current
    val playing by MediaCast.playing.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val host = Prefs.lastHost(ctx)
            if (host == null) UiBus.show("Collega prima la TV")
            else {
                busy = true
                tvAction("Invio alla TV") {
                    try {
                        val title = MediaCast.cast(ctx, uri, null, host)
                        UiBus.show("In riproduzione sulla TV: $title")
                        reportOperation("media")
                    } finally { busy = false }
                }
            }
        }
    }

    Section("Foto, video e musica", Icons.Filled.PermMedia, subtitle = "Dal telefono alla TV, qualità piena e audio") {
        Text(
            "Scegli un file: la TV lo riproduce con il suo lettore. Va bene con qualsiasi telefono Android. " +
                "Puoi anche usare Condividi → \"Apri sulla TV\" dalla Galleria o da altre app.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = { picker.launch(arrayOf("image/*", "video/*", "audio/*")) }, enabled = !busy) {
            Icon(Icons.Filled.PermMedia, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Invio…" else "Scegli dal telefono")
        }
        playing?.let { p ->
            Text("In riproduzione: ${p.title}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { tvAction("Pausa") { MediaCast.pause() } }) { Text("Pausa") }
                FilledTonalButton(onClick = { tvAction("Riprendi") { MediaCast.resume() } }) { Text("Riprendi") }
                OutlinedButton(onClick = { tvAction("Stop") { MediaCast.stop() } }) { Text("Stop") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(link, { link = it.trim() }, label = { Text("Link (YouTube o sito)") }, singleLine = true, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = {
                val u = link
                tvAction("Link") { UiBus.show("Aperto su ${Tv.openLink(u)} della TV") }
            }, enabled = link.startsWith("http")) { Icon(Icons.Filled.Link, "Apri") }
        }
    }
}
