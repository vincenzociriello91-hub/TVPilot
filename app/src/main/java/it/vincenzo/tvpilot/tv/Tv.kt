package it.vincenzo.tvpilot.tv

import android.content.Context
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
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

    private val connectLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Un collegamento alla volta: app, widget, orologio e programmi orari potevano partire insieme
     * e chiudersi a vicenda il socket.
     * quiet = ricollegamento automatico: se la TV è spenta niente avviso rosso, solo una riga.
     */
    suspend fun connect(ip: String, quiet: Boolean = false): Boolean = connectLock.withLock {
        connectNow(ip, quiet) || (quiet && relocated(ip))
    }

    /** Ultimo tentativo automatico fallito: per 8 s non si riprova (evita code di tentativi, es. dal touchpad) */
    @Volatile private var lastFail = 0L

    /**
     * La TV non risponde al vecchio indirizzo: forse il modem le ha dato un IP nuovo.
     * Cerca le TV sulla rete; se ce n'è una sola a un altro indirizzo, sposta lì abbinamento e MAC e si collega.
     */
    private suspend fun relocated(old: String): Boolean {
        val found = runCatching { Discovery.search(app, 2500) }.getOrDefault(emptyList()).filter { it.ip != old }
        if (found.size != 1) {
            lastFail = System.currentTimeMillis()
            return false
        }
        val ip = found[0].ip
        Prefs.clientKey(app, old)?.let { Prefs.setClientKey(app, ip, it) }
        Prefs.mac(app, old)?.let { Prefs.setMac(app, it, ip) }
        Prefs.setLastHost(app, ip)
        Reports.log("La TV ha cambiato indirizzo: $old → $ip")
        return connectNow(ip)
    }

    private suspend fun connectNow(ip: String, quiet: Boolean = false): Boolean {
        if (quiet && !reachable(ip)) {
            client.stato.value = WebOsClient.State.DISCONNECTED
            client.dettaglio.value = "TV spenta o non raggiungibile. Tocca il tasto di accensione."
            Reports.log("TV $ip non raggiungibile (probabilmente spenta)")
            lastFail = System.currentTimeMillis()
            return false
        }
        val ok = client.connect(ip)
        if (ok) runCatching { afterConnect() }.onFailure { Reports.log("Lettura info TV non riuscita: ${it.message}") }
        return ok
    }

    /** Controllo veloce (1,5 s) delle porte della TV, senza aprire il collegamento completo */
    suspend fun reachable(ip: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        listOf(3001, 3000).any { port ->
            runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress(ip, port), 1500) }; true }.getOrDefault(false)
        }
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
        // webOS 4.5: model_name è un codice interno (es. HE_DTV_W19R_AFAAABAA), il modello vero è in getSystemInfo
        m["nome"]?.takeIf { it.isNotBlank() }?.let { real ->
            m["modello"]?.let { m["codice"] = it }
            m["modello"] = real
        }
        info.value = m
        client.host?.let { h ->
            val name = Prefs.savedTvs(app).firstOrNull { it.host == h }?.name?.takeIf { it != "TV LG" && !it.startsWith("HE_") }
                ?: m["modello"]?.takeIf { it.isNotBlank() } ?: "TV LG"
            Prefs.saveTv(app, h, name)
        }
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

    /**
     * Per widget, notifica, orologio e voce: se la TV non è collegata si ricollega da sola all'ultima usata
     * (la chiave di abbinamento è salvata, quindi niente conferma sulla TV).
     */
    suspend fun ensureConnected(): Boolean = connectLock.withLock {
        if (client.connected) return@withLock true
        val host = Prefs.lastHost(app) ?: return@withLock false
        if (Prefs.clientKey(app, host) == null) return@withLock false
        if (System.currentTimeMillis() - lastFail < 8_000) return@withLock false
        connectNow(host, quiet = true) || relocated(host)
    }

    /** Accende (Wake-on-LAN) e aspetta che la TV risponda */
    suspend fun powerOn(): Boolean {
        val host = Prefs.lastHost(app)
        val mac = Prefs.mac(app) ?: return false
        Wol.wake(mac, host)
        if (host == null) return false
        // prima si aspetta che la TV apra le porte (controllo da 1,5 s), poi ci si collega una volta sola:
        // prima ogni tentativo durava fino a 14 s e l'accensione poteva superare i 2 minuti
        repeat(15) { i ->
            kotlinx.coroutines.delay(1500)
            if (reachable(host)) {
                kotlinx.coroutines.delay(1500) // il servizio SSAP parte poco dopo la rete
                if (runCatching { connect(host) }.getOrDefault(false)) return true
            }
            if (i % 3 == 2) Wol.wake(mac, host)
        }
        Reports.log("Accensione: la TV non ha risposto entro 45 s")
        return false
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

    // ---- canali ----
    data class Channel(val id: String, val number: String, val name: String)

    suspend fun channels(): List<Channel> {
        val arr = client.request("ssap://tv/getChannelList", timeoutMs = 20_000).optJSONArray("channelList") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { Channel(it.optString("channelId"), it.optString("channelNumber"), it.optString("channelName")) }
            .sortedWith(compareBy({ it.number.substringBefore('-').toIntOrNull() ?: 9999 }, { it.number }))
    }

    suspend fun openChannel(id: String) = client.ssap("tv/openChannel", JSONObject().put("channelId", id))
    suspend fun openChannelNumber(number: String) = client.ssap("tv/openChannel", JSONObject().put("channelNumber", number))

    /** Canale attuale e programma in onda (se la TV lo fornisce) */
    suspend fun currentProgram(): String {
        val ch = runCatching { client.request("ssap://tv/getCurrentChannel", log = false) }.getOrNull()
        val name = ch?.let { "${it.optString("channelNumber")} ${it.optString("channelName")}".trim() }.orEmpty()
        val prog = runCatching {
            val p = client.request("ssap://tv/getChannelProgramInfo", log = false)
            p.optJSONArray("programList")?.optJSONObject(0)?.optString("programName")
        }.getOrNull()
        return listOfNotNull(name.ifBlank { null }, prog?.ifBlank { null }).joinToString(" · ")
    }

    // ---- profili rapidi ----
    val PICTURE_MODES = listOf("vivid" to "Vivace", "normal" to "Standard", "eco" to "Eco", "cinema" to "Cinema",
        "sports" to "Sport", "game" to "Gioco", "expert1" to "Esperto")
    val SOUND_OUTPUTS = listOf("tv_speaker" to "Altoparlanti TV", "external_arc" to "HDMI ARC (soundbar)",
        "external_optical" to "Ottico", "bt_soundbar" to "Bluetooth", "headphone" to "Cuffie con filo", "tv_external_speaker" to "TV + esterno")

    suspend fun setPictureMode(mode: String) {
        val payload = JSONObject().put("category", "picture").put("settings", JSONObject().put("pictureMode", mode))
        try { client.ssap("settings/setSystemSettings", payload) }
        catch (e: Exception) { client.luna("com.webos.settingsservice/setSystemSettings", payload) }
    }

    suspend fun soundOutput(): String = runCatching {
        client.request("ssap://com.webos.service.apiadapter/audio/getSoundOutput", log = false).optString("soundOutput")
    }.getOrDefault("")

    suspend fun setSoundOutput(output: String) =
        client.ssap("com.webos.service.apiadapter/audio/changeSoundOutput", JSONObject().put("output", output))

    // ---- contenuti dal telefono ----
    fun youtubeId(url: String): String? {
        val patterns = listOf(
            Regex("youtu\\.be/([A-Za-z0-9_-]{6,})"),
            Regex("[?&]v=([A-Za-z0-9_-]{6,})"),
            Regex("youtube\\.com/(?:shorts|live|embed)/([A-Za-z0-9_-]{6,})"),
        )
        return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
    }

    /** Link YouTube nell'app YouTube della TV, tutto il resto nel browser della TV */
    suspend fun openLink(url: String): String {
        val id = youtubeId(url)
        return if (id != null) {
            client.ssap(
                "system.launcher/launch",
                JSONObject().put("id", "youtube.leanback.v4").put("contentId", id)
                    .put("params", JSONObject().put("contentTarget", "https://www.youtube.com/tv?v=$id")),
            )
            "YouTube"
        } else {
            client.ssap("system.launcher/open", JSONObject().put("target", url))
            "browser"
        }
    }

    /** Legge impostazioni (null se la TV non permette la lettura) */
    suspend fun readSettings(category: String, keys: List<String>): JSONObject? = runCatching {
        client.request(
            "ssap://settings/getSystemSettings",
            JSONObject().put("category", category).put("keys", org.json.JSONArray(keys)),
            log = false,
        ).optJSONObject("settings")
    }.getOrNull()
}
