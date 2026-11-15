package it.vincenzo.tvpilot.tv

import android.content.Context
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Punto unico per la TV: collegamento, informazioni e comandi di alto livello */
object Tv {
    lateinit var app: Context
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val client by lazy { WebOsClient(app) }

    /** modello, firmware, webOS, paese… letti dopo il collegamento */
    val info = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Avviso firmware (null = nessuno) */
    val firmwareNotice = MutableStateFlow<String?>(null)

    fun init(ctx: Context) { app = ctx.applicationContext }

    data class AppItem(val id: String, val title: String, val icon: String, val removable: Boolean, val system: Boolean)
    data class InputItem(val id: String, val label: String, val connected: Boolean)

    suspend fun connect(ip: String): Boolean {
        val ok = client.connect(ip)
        if (ok) runCatching { afterConnect() }.onFailure { Reports.log("Lettura info TV non riuscita: ${it.message}") }
        return ok
    }

    /** Dopo il collegamento: info TV, MAC per l'accensione, controllo firmware */
    private suspend fun afterConnect() {
        val m = mutableMapOf<String, String>()
        runCatching {
            val sw = client.ssap("com.webos.service.update/getCurrentSWInformation")
            m["prodotto"] = sw.optString("product_name")
            m["modello"] = sw.optString("model_name")
            m["firmware"] = "${sw.optString("major_ver")}.${sw.optString("minor_ver")}"
            m["paese"] = sw.optString("country")
            m["tipo"] = sw.optString("sw_type")
        }
        runCatching {
            val sys = client.ssap("system/getSystemInfo")
            sys.optString("modelName").takeIf { it.isNotBlank() }?.let { m["nome"] = it }
            sys.optString("receiverType").takeIf { it.isNotBlank() }?.let { m["ricevitore"] = it }
        }
        runCatching {
            val net = client.ssap("com.webos.service.connectionmanager/getinfo")
            val mac = listOf("wiredInfo", "wifiInfo").firstNotNullOfOrNull { k ->
                net.optJSONObject(k)?.optString("macAddress")?.takeIf { it.length >= 12 }
            }
            if (mac != null) Prefs.setMac(app, mac)
        }
        info.value = m
        Reports.log("TV: ${m["modello"]} firmware ${m["firmware"]} (${m["prodotto"]})")
        m["firmware"]?.takeIf { it.length > 1 }?.let { checkFirmware(it) }
    }

    private fun checkFirmware(current: String) {
        val old = Prefs.firmware(app)
        if (old != null && old != current) {
            firmwareNotice.value = "La TV si è aggiornata: firmware $old → $current. " +
                "Se usavi il root Homebrew, controlla che sia ancora attivo."
            Reports.log("Firmware cambiato $old → $current")
        } else if (System.currentTimeMillis() - Prefs.lastFirmwareCheck(app) > 14L * 24 * 3600 * 1000) {
            firmwareNotice.value = "Sono passati più di 14 giorni dall'ultimo controllo aggiornamenti della TV. " +
                "Tocca \"Cerca aggiornamenti sulla TV\" nella scheda Info."
        }
        Prefs.setFirmware(app, current)
    }

    // ---- comandi ----
    suspend fun volumeUp() = client.ssap("audio/volumeUp")
    suspend fun volumeDown() = client.ssap("audio/volumeDown")
    suspend fun setMute(mute: Boolean) = client.ssap("audio/setMute", JSONObject().put("mute", mute))
    suspend fun setVolume(v: Int) = client.ssap("audio/setVolume", JSONObject().put("volume", v.coerceIn(0, 100)))

    /** volume e muto: webOS 4.5 risponde con volumeStatus, i firmware vecchi con volume/muted */
    suspend fun volume(): Pair<Int, Boolean> {
        val r = client.request("ssap://audio/getVolume", log = false)
        val vs = r.optJSONObject("volumeStatus")
        return if (vs != null) vs.optInt("volume") to vs.optBoolean("muteStatus")
        else r.optInt("volume") to r.optBoolean("muted")
    }

    suspend fun channelUp() = client.ssap("tv/channelUp")
    suspend fun channelDown() = client.ssap("tv/channelDown")
    suspend fun powerOff() = client.ssap("system/turnOff")
    suspend fun screenOff() = client.ssap("com.webos.service.tvpower/power/turnOffScreen", JSONObject().put("standbyMode", "active"))
    suspend fun screenOn() = client.ssap("com.webos.service.tvpower/power/turnOnScreen", JSONObject().put("standbyMode", "active"))
    suspend fun play() = client.ssap("media.controls/play")
    suspend fun pause() = client.ssap("media.controls/pause")
    suspend fun toast(msg: String) = client.ssap("system.notifications/createToast", JSONObject().put("message", msg))
    suspend fun insertText(t: String) = client.ssap("com.webos.service.ime/insertText", JSONObject().put("text", t).put("replace", 0))
    suspend fun deleteChars(n: Int) = client.ssap("com.webos.service.ime/deleteCharacters", JSONObject().put("count", n))
    suspend fun enterKey() = client.ssap("com.webos.service.ime/sendEnterKey")
    suspend fun launch(id: String) = client.ssap("system.launcher/launch", JSONObject().put("id", id))
    suspend fun closeApp(id: String) = client.ssap("system.launcher/close", JSONObject().put("id", id))
    suspend fun reboot() = client.ssap("com.webos.service.devicereset/requestReboot", JSONObject().put("reason", "remoteKey"))
    suspend fun openUpdateCheck() {
        Prefs.setLastFirmwareCheck(app, System.currentTimeMillis())
        firmwareNotice.value = null
        launch("com.webos.app.softwareupdate")
    }

    suspend fun foregroundApp(): String = client.ssap("com.webos.applicationManager/getForegroundAppInfo").optString("appId")

    suspend fun apps(): List<AppItem> {
        val arr = client.ssap("com.webos.applicationManager/listLaunchPoints").optJSONArray("launchPoints") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            AppItem(it.optString("id"), it.optString("title"), it.optString("icon"), it.optBoolean("removable"), it.optBoolean("systemApp"))
        }.sortedBy { it.title.lowercase() }
    }

    suspend fun inputs(): List<InputItem> {
        val arr = client.ssap("tv/getExternalInputList").optJSONArray("devices") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            InputItem(it.optString("id"), it.optString("label"), it.optBoolean("connected"))
        }
    }

    suspend fun switchInput(id: String) = client.ssap("tv/switchInput", JSONObject().put("inputId", id))

    /** Legge impostazioni (null se la TV non permette la lettura) */
    suspend fun readSettings(category: String, keys: List<String>): JSONObject? = runCatching {
        client.request(
            "ssap://settings/getSystemSettings",
            JSONObject().put("category", category).put("keys", org.json.JSONArray(keys)),
            log = false,
        ).optJSONObject("settings")
    }.getOrNull()
}
