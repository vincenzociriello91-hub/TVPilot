package it.vincenzo.tvpilot.quick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import it.vincenzo.tvpilot.MainActivity
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.TvActions
import kotlinx.coroutines.launch

/**
 * Controlli sempre a portata: notifica fissa con i tasti principali e
 * tasti del volume del telefono che comandano la TV (sessione multimediale "remota"), anche a schermo spento.
 */
class ControlService : Service() {
    companion object {
        private const val CHANNEL = "controlli"
        private const val ID = 8
        @Volatile private var instance: ControlService? = null

        fun start(ctx: Context) {
            val i = Intent(ctx, ControlService::class.java)
            runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
                .onFailure { Reports.log("Controlli fissi non avviati: ${it.message}") }
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, ControlService::class.java)) }

        /** Aggiorna il testo della notifica dopo un'azione */
        fun refresh(ctx: Context, text: String) { instance?.update(text) }
    }

    private var session: MediaSession? = null
    private var volume = 20

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val n = build("Tocca i tasti per comandare la TV")
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(ID, n)
        if (Prefs.volumeKeys(this)) startVolumeSession()
        Reports.log("Controlli fissi attivi")
    }

    /** Volume remoto: i tasti fisici del telefono mandano su/giù alla TV */
    private fun startVolumeSession() {
        val s = MediaSession(this, "TVPilot")
        val provider = object : VolumeProvider(VOLUME_CONTROL_RELATIVE, 100, volume) {
            override fun onAdjustVolume(direction: Int) {
                if (direction == 0) return
                Tv.scope.launch {
                    val msg = runCatching { TvActions.run(if (direction > 0) TvActions.Action.VOL_UP else TvActions.Action.VOL_DOWN) }
                        .getOrDefault("TV non raggiungibile")
                    runCatching { Tv.volume().first }.getOrNull()?.let { volume = it; currentVolume = it }
                    update(msg)
                }
            }
        }
        s.setPlaybackToRemote(provider)
        s.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        s.isActive = true
        session = s
    }

    private fun action(a: TvActions.Action, label: String, icon: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            this, a.ordinal, ActionReceiver.intent(this, a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun build(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Controlli TV", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("TVPilot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(action(TvActions.Action.VOL_DOWN, "Vol −", android.R.drawable.ic_media_previous))
            .addAction(action(TvActions.Action.MUTE, "Muto", android.R.drawable.ic_lock_silent_mode))
            .addAction(action(TvActions.Action.VOL_UP, "Vol +", android.R.drawable.ic_media_next))
            .addAction(action(TvActions.Action.POWER_TOGGLE, "Accendi/Spegni", android.R.drawable.ic_lock_power_off))
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2).also { st -> session?.let { st.setMediaSession(it.sessionToken) } })
            .build()
    }

    fun update(text: String) {
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, build(text)) }
    }

    override fun onDestroy() {
        instance = null
        session?.release()
        session = null
        Reports.log("Controlli fissi spenti")
        super.onDestroy()
    }
}
