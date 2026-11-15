package it.vincenzo.tvpilot.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.media.MediaCast
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.ui.TVPilotTheme
import kotlinx.coroutines.launch

/**
 * Condividi → "Apri sulla TV".
 * Link: YouTube nell'app YouTube della TV, il resto nel browser della TV.
 * Foto/video/musica: riproduzione DLNA; la finestra resta aperta con Pausa/Stop (serve a mantenere l'accesso al file).
 */
class ShareActivity : ComponentActivity() {
    private var status by mutableStateOf("Invio alla TV…")
    private var done by mutableStateOf(false)
    private var isMedia by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reports.log("Condivisione ricevuta: ${intent.type}")
        setContent {
            TVPilotTheme {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("TVPilot", style = MaterialTheme.typography.titleLarge)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!done) CircularProgressIndicator()
                                Text(status)
                            }
                            if (done && isMedia && MediaCast.playing.value != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { Tv.scope.launch { runCatching { MediaCast.pause() } } }) { Text("Pausa") }
                                FilledTonalButton(onClick = { Tv.scope.launch { runCatching { MediaCast.resume() } } }) { Text("Riprendi") }
                                OutlinedButton(onClick = { Tv.scope.launch { MediaCast.stop() }; finish() }) { Text("Stop") }
                            }
                            if (done) OutlinedButton(onClick = { finish() }) { Text("Chiudi") }
                        }
                    }
                }
            }
        }
        handle(intent)
    }

    private fun handle(i: Intent) {
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
        Tv.scope.launch {
            val msg = try {
                if (!Tv.ensureConnected()) "TV non raggiungibile: accendila e apri TVPilot almeno una volta"
                else if (stream != null) {
                    isMedia = true
                    val host = Prefs.lastHost(this@ShareActivity) ?: error("nessuna TV")
                    "In riproduzione sulla TV: " + MediaCast.cast(this@ShareActivity, stream, i.type, host)
                } else {
                    val url = text?.let { Regex("https?://\\S+").find(it)?.value } ?: error("nessun link nel testo condiviso")
                    "Aperto su ${Tv.openLink(url)} della TV"
                }
            } catch (e: Exception) {
                Reports.log("Condivisione non riuscita: ${e.message}")
                "Non riuscito: ${e.message}"
            }
            status = msg
            done = true
            Reports.flush("operazione-condivisione")
        }
    }
}
