package it.vincenzo.tvpilot.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import it.vincenzo.tvpilot.MainActivity
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Tv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.security.SecureRandom

/**
 * Mirroring universale: funziona con qualsiasi telefono Android 8+, anche senza Miracast.
 * Cattura lo schermo (MediaProjection), lo comprime in JPEG e lo serve in rete;
 * la TV lo mostra aprendo il suo browser sull'indirizzo del telefono. Niente audio (resta sul telefono).
 */
class MirrorService : Service() {
    enum class Quality(val label: String, val maxSide: Int, val jpeg: Int, val frameMs: Long) {
        FLUID("Fluido", 960, 55, 40),
        BALANCED("Bilanciato", 1280, 65, 60),
        SHARP("Nitido", 1600, 80, 100),
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_QUALITY = "qualita"
        private const val CHANNEL = "mirroring"
        private const val PORT = 8085

        /** Indirizzo del flusso mentre il mirroring è attivo, null se spento */
        val url = MutableStateFlow<String?>(null)
        val status = MutableStateFlow("")

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MirrorService::class.java)) }

        /** IPv4 del telefono sulla rete Wi-Fi attuale */
        fun localIp(ctx: Context): String? {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
            val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var server: MjpegServer? = null
    private var frames = 0
    private var started = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || projection != null) return START_NOT_STICKY
        // Android 14: il servizio in primo piano va avviato PRIMA di chiedere la proiezione
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(7, notification)
        try {
            begin(intent)
        } catch (e: Exception) {
            Reports.log("Mirroring non avviato: ${e.javaClass.simpleName}: ${e.message}")
            status.value = "Mirroring non avviato: ${e.message}"
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data: Intent = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)) ?: error("permesso di cattura mancante")
        val quality = runCatching { Quality.valueOf(intent.getStringExtra(EXTRA_QUALITY) ?: "") }.getOrDefault(Quality.BALANCED)
        val ip = localIp(this) ?: error("il telefono non è collegato al Wi-Fi")

        val token = ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val srv = MjpegServer(PORT, token).also { it.start() }
        server = srv

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(code, data) ?: error("cattura dello schermo rifiutata")
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { Reports.log("Mirroring fermato dal sistema"); stopSelf() }
        }, Handler(Looper.getMainLooper()))

        // dimensioni: lato lungo limitato dalla qualità scelta, numeri pari per l'encoder
        val dm = resources.displayMetrics
        val scale = minOf(1f, quality.maxSide.toFloat() / maxOf(dm.widthPixels, dm.heightPixels))
        val w = ((dm.widthPixels * scale).toInt() / 2) * 2
        val h = ((dm.heightPixels * scale).toInt() / 2) * 2

        val thread = HandlerThread("mirror-encode").also { it.start() }
        worker = thread
        val handler = Handler(thread.looper)
        val ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = ir
        var bitmap: Bitmap? = null
        var last = 0L
        val jpegOut = ByteArrayOutputStream(256 * 1024)
        ir.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - last < quality.frameMs || srv.clients.get() == 0) return@setOnImageAvailableListener
                last = now
                val plane = img.planes[0]
                val rowW = plane.rowStride / plane.pixelStride
                val bmp = bitmap?.takeIf { it.width == rowW && it.height == h }
                    ?: Bitmap.createBitmap(rowW, h, Bitmap.Config.ARGB_8888).also { bitmap = it }
                bmp.copyPixelsFromBuffer(plane.buffer)
                val frame = if (rowW == w) bmp else Bitmap.createBitmap(bmp, 0, 0, w, h)
                jpegOut.reset()
                frame.compress(Bitmap.CompressFormat.JPEG, quality.jpeg, jpegOut)
                if (frame !== bmp) frame.recycle()
                srv.publish(jpegOut.toByteArray())
                frames++
            } catch (e: Exception) {
                Reports.log("Mirroring: errore fotogramma ${e.message}")
            } finally {
                img.close()
            }
        }, handler)

        display = proj.createVirtualDisplay(
            "TVPilot", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.surface, null, handler,
        )
        started = System.currentTimeMillis()
        val address = "http://$ip:${srv.port}/$token/"
        url.value = address
        status.value = "Mirroring attivo (${quality.label}, ${w}x$h)"
        Reports.log("Mirroring avviato: ${quality.label} ${w}x$h, porta ${srv.port}")
        openOnTv(address)
    }

    /** Apre il browser della TV sull'indirizzo del flusso */
    private fun openOnTv(address: String) {
        Tv.scope.launch {
            if (!Tv.client.connected) { status.value = "Mirroring attivo: collega la TV per aprirlo sullo schermo"; return@launch }
            runCatching { Tv.client.ssap("system.launcher/open", JSONObject().put("target", address)) }
                .recoverCatching { Tv.client.ssap("system.launcher/launch", JSONObject().put("id", "com.webos.app.browser").put("params", JSONObject().put("target", address))) }
                .onSuccess { Reports.log("Mirroring: browser della TV aperto") }
                .onFailure { status.value = "Apri il browser della TV e vai su $address"; Reports.log("Mirroring: apertura browser non riuscita ${it.message}") }
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mirroring", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("TVPilot: mirroring sulla TV")
            .setContentText("Lo schermo del telefono è visibile sulla TV. Tocca per fermarlo dall'app.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        val secs = (System.currentTimeMillis() - started) / 1000
        if (started > 0) Reports.log("Mirroring chiuso: ${secs}s, $frames fotogrammi (${if (secs > 0) frames / secs else 0} al secondo)")
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        runCatching { server?.stop() }
        worker?.quitSafely()
        projection = null
        url.value = null
        if (status.value.startsWith("Mirroring attivo")) status.value = "Mirroring fermato"
        Tv.scope.launch { runCatching { if (Tv.client.connected) Tv.closeApp("com.webos.app.browser") } }
        Tv.scope.launch { Reports.flush("operazione-mirroring"); Reports.sendQueue() }
        super.onDestroy()
    }
}
