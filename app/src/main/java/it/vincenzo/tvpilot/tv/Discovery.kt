package it.vincenzo.tvpilot.tv

import android.content.Context
import android.net.wifi.WifiManager
import it.vincenzo.tvpilot.core.Http
import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Ricerca delle TV LG webOS sulla rete Wi-Fi (SSDP, servizio "webos-second-screen") */
object Discovery {
    data class Found(val ip: String, val name: String)

    private const val ST = "urn:lge-com:service:webos-second-screen:1"

    fun searchMessage(): String =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $ST\r\n\r\n"

    /** Estrae l'intestazione LOCATION (senza badare a maiuscole) da una risposta SSDP */
    fun location(response: String): String? = response.lineSequence()
        .firstOrNull { it.startsWith("location:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()

    /** Nome leggibile dalla descrizione UPnP (<friendlyName>) */
    fun friendlyName(xml: String): String? =
        Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()

    suspend fun search(ctx: Context, timeoutMs: Int = 3500): List<Found> = withContext(Dispatchers.IO) {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("tvpilot-ssdp").apply { setReferenceCounted(false); acquire() }
        val found = linkedMapOf<String, String?>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 600
                val msg = searchMessage().toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(msg, msg.size, group, 1900)) }
                val end = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        socket.receive(p)
                        val text = String(p.data, 0, p.length)
                        if (!text.contains("webos-second-screen", ignoreCase = true) && !text.contains("LG", ignoreCase = true)) continue
                        val ip = p.address.hostAddress ?: continue
                        if (ip !in found) found[ip] = location(text)
                    } catch (_: SocketTimeoutException) { }
                }
            }
        } catch (e: Exception) {
            Reports.log("Ricerca TV: errore ${e.message}")
        } finally {
            runCatching { lock.release() }
        }
        val list = found.map { (ip, loc) ->
            val name = loc?.let { runCatching { friendlyName(Http.get(it, 2500)) }.getOrNull() } ?: "TV LG"
            Found(ip, name)
        }
        Reports.log("Ricerca TV: ${list.size} trovate")
        list
    }
}
