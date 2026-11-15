package it.vincenzo.tvpilot.tv

import it.vincenzo.tvpilot.core.Reports
import org.json.JSONObject

/**
 * Azioni rapide usate da widget, notifica, riquadro impostazioni rapide, orologio e voce.
 * Si ricollegano da sole alla TV; l'accensione usa il Wake-on-LAN.
 */
object TvActions {
    enum class Action { POWER_ON, POWER_OFF, POWER_TOGGLE, VOL_UP, VOL_DOWN, MUTE, PLAY, PAUSE, HOME, BACK, OK, UP, DOWN, LEFT, RIGHT, CH_UP, CH_DOWN }

    /** Esegue e restituisce un testo breve con l'esito */
    suspend fun run(action: Action): String {
        Reports.log("Azione rapida $action")
        if (action == Action.POWER_ON) return if (Tv.powerOn()) "TV accesa" else "Accensione inviata"
        val connected = Tv.ensureConnected()
        if (action == Action.POWER_TOGGLE) {
            return if (connected) { Tv.powerOff(); "TV spenta" } else if (Tv.powerOn()) "TV accesa" else "Accensione inviata"
        }
        if (!connected) return "TV non raggiungibile"
        when (action) {
            Action.POWER_OFF -> Tv.powerOff()
            Action.VOL_UP -> Tv.volumeUp()
            Action.VOL_DOWN -> Tv.volumeDown()
            Action.MUTE -> { val (_, muted) = Tv.volume(); Tv.setMute(!muted) }
            Action.PLAY -> Tv.play()
            Action.PAUSE -> Tv.pause()
            Action.CH_UP -> Tv.channelUp()
            Action.CH_DOWN -> Tv.channelDown()
            Action.HOME -> Tv.client.button("HOME")
            Action.BACK -> Tv.client.button("BACK")
            Action.OK -> Tv.client.button("ENTER")
            Action.UP -> Tv.client.button("UP")
            Action.DOWN -> Tv.client.button("DOWN")
            Action.LEFT -> Tv.client.button("LEFT")
            Action.RIGHT -> Tv.client.button("RIGHT")
            else -> Unit
        }
        return when (action) {
            Action.POWER_OFF -> "TV spenta"
            Action.VOL_UP, Action.VOL_DOWN, Action.MUTE -> runCatching { Tv.volume() }.getOrNull()
                ?.let { if (it.second) "Muto" else "Volume ${it.first}" } ?: "OK"
            else -> "OK"
        }
    }

    /** Timer di spegnimento della TV stessa (impostazione time.sleepTimer): 0 = spento */
    suspend fun sleepTimer(minutes: Int) {
        val value = if (minutes <= 0) "off" else minutes.toString()
        val payload = JSONObject().put("category", "time").put("settings", JSONObject().put("sleepTimer", value))
        try { Tv.client.ssap("settings/setSystemSettings", payload) }
        catch (e: Exception) { Tv.client.luna("com.webos.settingsservice/setSystemSettings", payload) }
        Reports.log("Timer spegnimento: $value")
    }

    /** Valori ammessi dalla TV per il timer */
    val SLEEP_VALUES = listOf(10, 20, 30, 60, 90, 120, 180, 240)

    fun nearestSleep(minutes: Int): Int = SLEEP_VALUES.minBy { kotlin.math.abs(it - minutes) }
}
