package it.vincenzo.tvpilot.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.vincenzo.tvpilot.tv.Risk

private val BrandBlue = Color(0xFF1A73E8)
val OkGreen = Color(0xFF34A853)
val WarnAmber = Color(0xFFF9AB00)
val DangerRed = Color(0xFFD93025)

/** Stile Google Pixel: colori dinamici dallo sfondo del telefono (Android 12+), angoli molto arrotondati */
@Composable
fun TVPilotTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF8AB4F8))
        else -> lightColorScheme(primary = BrandBlue)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Scheda di una sezione: icona in un cerchio colorato, titolo, contenuto */
@Composable
fun Section(title: String, icon: ImageVector? = null, subtitle: String? = null, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** Riquadro di avviso: info (blu), attenzione (giallo) o pericolo (rosso) */
@Composable
fun Notice(text: String, level: Risk = Risk.LOW, title: String? = null) {
    val (bg, fg) = when (level) {
        Risk.LOW -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Risk.MEDIUM -> Color(0x33F9AB00) to MaterialTheme.colorScheme.onSurface
        Risk.HIGH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(if (level == Risk.LOW) Icons.Filled.Info else Icons.Filled.Warning, null, tint = fg)
            Spacer(Modifier.width(10.dp))
            Column {
                if (title != null) Text(title, color = fg, fontWeight = FontWeight.SemiBold)
                Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Pastiglia colorata con il livello di rischio */
@Composable
fun RiskChip(risk: Risk) {
    val c = when (risk) { Risk.LOW -> OkGreen; Risk.MEDIUM -> WarnAmber; Risk.HIGH -> DangerRed }
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text(risk.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun StatusChip(label: String, on: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) OkGreen else MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
