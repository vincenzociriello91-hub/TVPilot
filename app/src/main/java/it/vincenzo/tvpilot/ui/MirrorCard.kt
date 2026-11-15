package it.vincenzo.tvpilot.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.mirror.MirrorService
import it.vincenzo.tvpilot.tv.Risk
import it.vincenzo.tvpilot.tv.Tv

/**
 * Due modi per vedere il telefono sulla TV:
 *  - universale (qualsiasi Android 8+): cattura dello schermo e browser della TV, senza audio;
 *  - nativo Miracast (Screen Share della TV): per i telefoni che lo hanno (Samsung Smart View, Xiaomi, Oppo, Huawei…), con audio.
 */
@Composable
fun MirrorCard() {
    val ctx = LocalContext.current
    val url by MirrorService.url.collectAsState()
    val status by MirrorService.status.collectAsState()
    var quality by remember { mutableStateOf(MirrorService.Quality.BALANCED) }

    val capture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            val i = Intent(ctx, MirrorService::class.java)
                .putExtra(MirrorService.EXTRA_CODE, r.resultCode)
                .putExtra(MirrorService.EXTRA_DATA, r.data)
                .putExtra(MirrorService.EXTRA_QUALITY, quality.name)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } else {
            Reports.log("Mirroring: permesso di cattura negato")
            UiBus.show("Mirroring annullato")
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        capture.launch(ctx.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    Section("Mirroring dello schermo", Icons.Filled.ScreenShare, subtitle = "Vedi il telefono sulla TV") {
        Text("Universale — funziona con qualsiasi telefono Android", fontWeight = FontWeight.SemiBold)
        Text(
            "Il telefono trasmette lo schermo in rete e la TV lo mostra nel suo browser. Va bene per foto, app e presentazioni; " +
                "l'audio resta sul telefono e c'è un leggero ritardo.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (url == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MirrorService.Quality.entries.forEach { q ->
                    FilterChip(selected = quality == q, onClick = { quality = q }, label = { Text(q.label) })
                }
            }
            Button(onClick = {
                Reports.log("Mirroring: richiesta avvio (${quality.name})")
                if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else capture.launch(ctx.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
            }) { Icon(Icons.Filled.ScreenShare, null); Spacer(Modifier.width(8.dp)); Text("Avvia mirroring") }
        } else {
            Notice("Attivo. Se la TV non si apre da sola, nel suo browser scrivi:\n$url", Risk.LOW)
            Button(onClick = { MirrorService.stop(ctx) }) {
                Icon(Icons.Filled.StopScreenShare, null); Spacer(Modifier.width(8.dp)); Text("Ferma mirroring")
            }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Nativo (Miracast) — con audio, se il telefono lo supporta", fontWeight = FontWeight.SemiBold)
        Text(
            "Samsung (Smart View), Xiaomi, Oppo, Realme, Huawei e altri hanno la trasmissione schermo integrata. " +
                "I Pixel e alcuni altri non ce l'hanno: per loro usa il modo universale.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = {
            tvAction("Screen Share", "Sulla TV si è aperto Screen Share") { Tv.launch("com.webos.app.miracast") }
        }) { Text("1. Apri Screen Share sulla TV") }
        OutlinedButton(onClick = {
            val opened = runCatching {
                ctx.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            }.getOrDefault(false) || runCatching {
                ctx.startActivity(
                    ctx.packageManager.getLaunchIntentForPackage("com.samsung.android.smartmirroring")!!
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ); true
            }.getOrDefault(false)
            Reports.log("Mirroring nativo: impostazioni trasmissione ${if (opened) "aperte" else "non disponibili"}")
            if (!opened) UiBus.show("Questo telefono non ha la trasmissione schermo integrata: usa il modo universale")
        }) { Icon(Icons.Filled.CastConnected, null); Spacer(Modifier.width(8.dp)); Text("2. Trasmetti dal telefono") }
    }
}
