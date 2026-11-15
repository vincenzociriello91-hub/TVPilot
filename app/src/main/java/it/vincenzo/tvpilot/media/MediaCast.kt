package it.vincenzo.tvpilot.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.mirror.MirrorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Foto, video e musica del telefono sulla TV a qualità piena, con audio (DLNA / UPnP).
 * Il telefono fa da piccolo server del file; la TV (lettore DLNA integrato delle LG) lo riproduce.
 */
object MediaCast {
    data class Playing(val title: String, val mime: String)

    val playing = MutableStateFlow<Playing?>(null)
    private var server: FileServer? = null
    private var control: String? = null

    // ---------------- server del file (con Range per poter andare avanti e indietro nei video) ----------------

    private class FileServer(private val ctx: Context, private val uri: Uri, private val mime: String, private val size: Long) {
        val token = ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        private val socket = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(8086)) }
        @Volatile private var closed = false
        val port get() = socket.localPort

        fun start() = thread(name = "media-accept", isDaemon = true) {
            while (!closed) {
                val c = runCatching { socket.accept() }.getOrNull() ?: continue
                thread(name = "media-client", isDaemon = true) { handle(c) }
            }
        }

        private fun handle(c: Socket) = c.use { s ->
            runCatching {
                val reader = s.getInputStream().bufferedReader()
                val first = reader.readLine() ?: return@runCatching
                val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                val method = first.substringBefore(' ')
                val path = first.split(' ').getOrNull(1).orEmpty()
                val out = s.getOutputStream()
                if (!path.startsWith("/$token/")) { out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray()); return@runCatching }
                val range = headers.firstOrNull { it.startsWith("range:", true) }?.substringAfter('=')?.trim()
                var start = 0L
                var end = size - 1
                if (range != null && size > 0) {
                    start = range.substringBefore('-').toLongOrNull() ?: 0L
                    end = range.substringAfter('-').toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
                }
                val length = if (size > 0) end - start + 1 else -1
                val status = if (range != null && size > 0) "206 Partial Content" else "200 OK"
                val sb = StringBuilder("HTTP/1.1 $status\r\n")
                    .append("Content-Type: $mime\r\n")
                    .append("Accept-Ranges: bytes\r\n")
                    .append("transferMode.dlna.org: Streaming\r\n")
                    .append("contentFeatures.dlna.org: ${features()}\r\n")
                    .append("Connection: close\r\n")
                if (length >= 0) sb.append("Content-Length: $length\r\n")
                if (status.startsWith("206")) sb.append("Content-Range: bytes $start-$end/$size\r\n")
                sb.append("\r\n")
                out.write(sb.toString().toByteArray())
                if (method != "HEAD") copy(out, start, length)
                out.flush()
            }
        }

        private fun copy(out: OutputStream, start: Long, length: Long) {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    fis.channel.position(start)
                    val buf = ByteArray(256 * 1024)
                    var left = if (length < 0) Long.MAX_VALUE else length
                    while (left > 0 && !closed) {
                        val n = fis.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) break
                        out.write(buf, 0, n)
                        left -= n
                    }
                }
            }
        }

        fun stop() { closed = true; runCatching { socket.close() } }
    }

    fun features() = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    // ---------------- DLNA: trova il lettore della TV e gli dice cosa riprodurre ----------------

    /** controlURL del servizio AVTransport della TV (cercato in rete con SSDP) */
    private suspend fun findControl(ctx: Context, tvIp: String): String = withContext(Dispatchers.IO) {
        control?.let { return@withContext it }
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val lock = wifi.createMulticastLock("tvpilot-dlna").apply { setReferenceCounted(false); acquire() }
        var location: String? = null
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 700
                val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                    "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n\r\n").toByteArray()
                for (target in listOf("239.255.255.250", tvIp)) s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(target), 1900))
                val end = System.currentTimeMillis() + 4000
                val buf = ByteArray(2048)
                while (location == null && System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        if (p.address.hostAddress != tvIp) continue
                        location = String(p.data, 0, p.length).lineSequence()
                            .firstOrNull { it.startsWith("location:", true) }?.substringAfter(':')?.trim()
                    } catch (_: SocketTimeoutException) { }
                }
            }
        } finally { runCatching { lock.release() } }
        val loc = location ?: error("il lettore DLNA della TV non risponde (TV accesa? Su alcune TV va attivata \"Condivisione contenuti\" nelle impostazioni)")
        val xml = it.vincenzo.tvpilot.core.Http.get(loc, 4000)
        val url = parseControlUrl(xml, loc) ?: error("la TV non espone il servizio di riproduzione")
        Reports.log("DLNA: lettore trovato")
        control = url
        url
    }

    /** Estrae il controlURL di AVTransport dalla descrizione UPnP (relativo o assoluto) */
    fun parseControlUrl(xml: String, location: String): String? {
        val service = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { it.groupValues[1] }.firstOrNull { it.contains("AVTransport") } ?: return null
        val path = Regex("<controlURL>(.*?)</controlURL>").find(service)?.groupValues?.get(1)?.trim() ?: return null
        if (path.startsWith("http")) return path
        val base = Regex("<URLBase>(.*?)</URLBase>").find(xml)?.groupValues?.get(1)?.trim()?.trimEnd('/')
            ?: URL(location).let { "${it.protocol}://${it.host}:${it.port}" }
        return base + if (path.startsWith("/")) path else "/$path"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun didl(url: String, title: String, mime: String): String {
        val cls = when {
            mime.startsWith("video") -> "object.item.videoItem"
            mime.startsWith("audio") -> "object.item.audioItem.musicTrack"
            else -> "object.item.imageItem.photo"
        }
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${esc(title)}</dc:title><upnp:class>$cls</upnp:class>" +
            "<res protocolInfo=\"http-get:*:$mime:${features()}\">${esc(url)}</res></item></DIDL-Lite>"
    }

    private suspend fun soap(controlUrl: String, action: String, args: String) = withContext(Dispatchers.IO) {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$action xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
            "<InstanceID>0</InstanceID>$args</u:$action></s:Body></s:Envelope>"
        val c = URL(controlUrl).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 5000; c.readTimeout = 8000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            c.setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = runCatching { c.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                val desc = Regex("<errorDescription>(.*?)</errorDescription>").find(err)?.groupValues?.get(1)
                error("la TV ha rifiutato $action (${desc ?: "HTTP $code"})")
            }
        } finally { c.disconnect() }
    }

    /** Manda un file del telefono alla TV e lo fa partire */
    suspend fun cast(ctx: Context, uri: Uri, mimeHint: String?, tvIp: String): String {
        val app = ctx.applicationContext
        val mime = mimeHint?.takeIf { it.contains('/') && !it.endsWith("*") } ?: app.contentResolver.getType(uri) ?: "video/mp4"
        var title = "TVPilot"
        var size = -1L
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                title = c.getString(0) ?: title
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size <= 0) size = app.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        val ip = MirrorService.localIp(app) ?: error("il telefono non è collegato al Wi-Fi")
        stopServer()
        val srv = FileServer(app, uri, mime, size).also { it.start() }
        server = srv
        val fileUrl = "http://$ip:${srv.port}/${srv.token}/media"
        val controlUrl = findControl(app, tvIp)
        runCatching { soap(controlUrl, "Stop", "") }
        try {
            soap(controlUrl, "SetAVTransportURI", "<CurrentURI>${esc(fileUrl)}</CurrentURI><CurrentURIMetaData>${esc(didl(fileUrl, title, mime))}</CurrentURIMetaData>")
            soap(controlUrl, "Play", "<Speed>1</Speed>")
        } catch (e: Exception) {
            control = null
            throw e
        }
        playing.value = Playing(title, mime)
        Reports.log("DLNA: riproduzione $mime (${size / 1024} KB)")
        return title
    }

    suspend fun pause() { control?.let { soap(it, "Pause", "") } }
    suspend fun resume() { control?.let { soap(it, "Play", "<Speed>1</Speed>") } }

    suspend fun stop() {
        runCatching { control?.let { soap(it, "Stop", "") } }
        stopServer()
        playing.value = null
        Reports.log("DLNA: riproduzione fermata")
    }

    private fun stopServer() { server?.stop(); server = null }
}
