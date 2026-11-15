package it.vincenzo.tvpilot.tv

import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Accensione dal telefono (Wake-on-LAN). Sulla TV servono "Quick Start+" e
 * "Accendi tramite Wi-Fi/rete" attivi (si attivano dalla scheda Pulizia).
 */
object Wol {
    fun macBytes(mac: String): ByteArray {
        val hex = mac.replace(Regex("[^0-9A-Fa-f]"), "")
        require(hex.length == 12) { "Indirizzo MAC non valido: $mac" }
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** Pacchetto magico: 6 byte 0xFF + MAC ripetuto 16 volte */
    fun magicPacket(mac: String): ByteArray {
        val m = macBytes(mac)
        return ByteArray(6 + 16 * 6) { i -> if (i < 6) 0xFF.toByte() else m[(i - 6) % 6] }
    }

    suspend fun wake(mac: String, lastIp: String?) = withContext(Dispatchers.IO) {
        val packet = magicPacket(mac)
        val targets = buildList {
            add("255.255.255.255")
            lastIp?.substringBeforeLast('.')?.let { add("$it.255") }
            lastIp?.let { add(it) }
        }
        DatagramSocket().use { s ->
            s.broadcast = true
            for (t in targets) for (port in listOf(9, 7)) runCatching {
                s.send(DatagramPacket(packet, packet.size, InetAddress.getByName(t), port))
            }
        }
        Reports.log("Wake-on-LAN inviato (${targets.size} indirizzi)")
    }
}
