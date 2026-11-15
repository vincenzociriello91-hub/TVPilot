package it.vincenzo.tvpilot.quick

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Tv
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Telefono e TV insieme: quando arriva una chiamata il video sulla TV va in pausa,
 * chiamate e messaggi compaiono come avviso sulla TV. Agisce solo se la TV è già accesa.
 * Richiede "Accesso alle notifiche" (Impostazioni di Android), che l'utente concede da Automazioni.
 */
class NotifyListener : NotificationListenerService() {
    companion object {
        private fun p(ctx: Context) = ctx.getSharedPreferences("tvpilot", Context.MODE_PRIVATE)
        fun pauseOnCall(ctx: Context) = p(ctx).getBoolean("pausa_chiamate", true)
        fun setPauseOnCall(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("pausa_chiamate", v).apply()
        fun messagesOnTv(ctx: Context) = p(ctx).getBoolean("messaggi_tv", true)
        fun setMessagesOnTv(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("messaggi_tv", v).apply()
        fun showText(ctx: Context) = p(ctx).getBoolean("messaggi_testo", false)
        fun setShowText(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("messaggi_testo", v).apply()
    }

    private val recent = mutableMapOf<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val isCall = n.category == Notification.CATEGORY_CALL
        val isMsg = n.category == Notification.CATEGORY_MESSAGE
        if (!isCall && !isMsg) return
        if (isCall && !pauseOnCall(this) && !messagesOnTv(this)) return
        if (isMsg && !messagesOnTv(this)) return
        // la stessa notifica si aggiorna spesso: una sola volta ogni 30 secondi
        val now = System.currentTimeMillis()
        if ((recent[sbn.key] ?: 0) > now - 30_000) return
        recent[sbn.key] = now
        if (recent.size > 50) recent.keys.take(25).forEach { recent.remove(it) }

        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
            .getOrDefault(sbn.packageName)
        val ctx = applicationContext
        Tv.scope.launch {
            // solo se la TV è già accesa: non la si accende per un messaggio
            val ok = Tv.client.connected || withTimeoutOrNull(5000) { Tv.ensureConnected() } == true
            if (!ok) return@launch
            runCatching {
                if (isCall) {
                    if (pauseOnCall(ctx)) Tv.pause()
                    Tv.toast("📞 Chiamata${if (title.isNotBlank()) " da $title" else ""}")
                    Reports.log("Chiamata in arrivo: pausa e avviso sulla TV")
                } else {
                    val body = if (showText(ctx) && text.isNotBlank()) ": ${text.take(80)}" else ""
                    Tv.toast("💬 $appName — $title$body")
                    Reports.log("Messaggio mostrato sulla TV ($appName)")
                }
            }
        }
    }
}
