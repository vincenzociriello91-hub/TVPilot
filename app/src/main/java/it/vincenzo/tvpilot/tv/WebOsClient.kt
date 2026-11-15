package it.vincenzo.tvpilot.tv

import android.content.Context
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SsapException(msg: String) : Exception(msg)

/**
 * Collegamento alla TV LG webOS con il protocollo SSAP (WebSocket).
 * Prova prima wss://IP:3001 (firmware recenti), poi ws://IP:3000 (firmware vecchi).
 * La prima volta la TV chiede di confermare l'abbinamento; poi basta la chiave salvata.
 */
class WebOsClient(private val ctx: Context) {
    enum class State { DISCONNECTED, CONNECTING, CONFIRM_ON_TV, CONNECTED, ERROR }

    val stato = MutableStateFlow(State.DISCONNECTED)
    val dettaglio = MutableStateFlow("")
    @Volatile var host: String? = null; private set
    @Volatile var sicuro = false; private set

    @Volatile private var ws: WebSocket? = null
    @Volatile private var pointer: WebSocket? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val counter = AtomicInteger(1)
    @Volatile private var registration: CompletableDeferred<String>? = null

    /**
     * La TV usa un certificato autofirmato per wss://:3001: questo client (usato SOLO verso la TV
     * sulla rete di casa) lo accetta. Le connessioni a Internet usano HttpURLConnection con verifica normale.
     */
    private val http: OkHttpClient = run {
        val trustTv = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustTv), SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustTv)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private inner class Listener(private val opened: CompletableDeferred<Unit>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
        override fun onMessage(webSocket: WebSocket, text: String) { handle(text) }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!opened.isCompleted) opened.completeExceptionally(t)
            else if (webSocket === ws) lost("collegamento interrotto: ${t.message}")
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket === ws) lost("collegamento chiuso dalla TV ($code)")
        }
    }

    private fun lost(why: String) {
        Reports.log("TV: $why")
        ws = null
        pointer?.cancel(); pointer = null
        pending.values.forEach { it.completeExceptionally(SsapException(why)) }
        pending.clear()
        stato.value = State.DISCONNECTED
        dettaglio.value = why
    }

    suspend fun connect(ip: String): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        host = ip
        stato.value = State.CONNECTING
        dettaglio.value = "Collegamento a $ip…"
        Reports.log("Collegamento alla TV $ip")
        for ((url, secure) in listOf("wss://$ip:3001" to true, "ws://$ip:3000" to false)) {
            val opened = CompletableDeferred<Unit>()
            val socket = http.newWebSocket(Request.Builder().url(url).build(), Listener(opened))
            val ok = runCatching { withTimeout(7_000) { opened.await() } }
            if (ok.isSuccess) {
                ws = socket; sicuro = secure
                Reports.log("Socket aperto: $url")
                break
            }
            socket.cancel()
            Reports.log("Socket non aperto: $url (${ok.exceptionOrNull()?.message})")
        }
        if (ws == null) {
            stato.value = State.ERROR
            dettaglio.value = "La TV $ip non risponde. È accesa ed è sulla stessa rete Wi-Fi del telefono?"
            return@withContext false
        }
        register(ip)
    }

    private suspend fun register(ip: String): Boolean {
        val d = CompletableDeferred<String>()
        registration = d
        val key = Prefs.clientKey(ctx, ip)
        send(JSONObject().put("type", "register").put("id", "registro").put("payload", Manifest.registration(key)))
        return try {
            val newKey = withTimeout(90_000) { d.await() }
            if (newKey.isNotBlank()) Prefs.setClientKey(ctx, ip, newKey)
            Prefs.setLastHost(ctx, ip)
            stato.value = State.CONNECTED
            dettaglio.value = "Collegato a $ip" + if (sicuro) " (connessione cifrata)" else ""
            Reports.log("Abbinamento OK (chiave ${if (key == null) "nuova" else "già salvata"}, cifrata=$sicuro)")
            true
        } catch (e: Exception) {
            Reports.log("Abbinamento non riuscito: ${e.message}")
            if (key != null && e !is kotlinx.coroutines.TimeoutCancellationException) Prefs.forgetKey(ctx, ip)
            stato.value = State.ERROR
            dettaglio.value = if (e is kotlinx.coroutines.TimeoutCancellationException)
                "Nessuna conferma sulla TV. Premi Riprova e accetta la richiesta col telecomando."
            else "La TV ha rifiutato l'abbinamento (${e.message}). Riprova e conferma sulla TV."
            false
        } finally { registration = null }
    }

    private fun handle(text: String) {
        val j = runCatching { JSONObject(text) }.getOrNull() ?: return
        val id = j.optString("id")
        val type = j.optString("type")
        val payload = j.optJSONObject("payload") ?: JSONObject()
        if (id == "registro") {
            when (type) {
                "registered" -> registration?.complete(payload.optString("client-key"))
                "response" -> if (payload.optString("pairingType") == "PROMPT") {
                    stato.value = State.CONFIRM_ON_TV
                    dettaglio.value = "Sulla TV è comparsa una richiesta: accettala con il telecomando."
                    Reports.log("In attesa di conferma sulla TV")
                }
                "error" -> registration?.completeExceptionally(SsapException(j.optString("error")))
            }
            return
        }
        val d = pending.remove(id) ?: return
        if (type == "error") d.completeExceptionally(SsapException(j.optString("error", "errore")))
        else d.complete(payload)
    }

    private fun send(msg: JSONObject) {
        val s = ws ?: throw SsapException("TV non collegata")
        if (!s.send(msg.toString())) throw SsapException("invio non riuscito")
    }

    fun disconnect() {
        ws?.close(1000, "chiusura")
        ws = null
        pointer?.close(1000, "chiusura"); pointer = null
        if (stato.value != State.ERROR) stato.value = State.DISCONNECTED
    }

    val connected get() = stato.value == State.CONNECTED

    /** Richiesta SSAP: uri tipo "ssap://audio/volumeUp". Lancia SsapException se la TV risponde con errore. */
    suspend fun request(uri: String, payload: JSONObject? = null, timeoutMs: Long = 10_000, log: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val id = "r${counter.getAndIncrement()}"
            val d = CompletableDeferred<JSONObject>()
            pending[id] = d
            try {
                val msg = JSONObject().put("id", id).put("type", "request").put("uri", uri)
                if (payload != null) msg.put("payload", payload)
                send(msg)
                val r = withTimeout(timeoutMs) { d.await() }
                if (r.has("returnValue") && !r.optBoolean("returnValue", true))
                    throw SsapException(r.optString("errorText", r.optString("errorCode", "errore")))
                if (log) Reports.log("OK $uri")
                r
            } catch (e: Exception) {
                if (log) Reports.log("ERRORE $uri: ${e.message}")
                throw e
            } finally { pending.remove(id) }
        }

    suspend fun ssap(path: String, payload: JSONObject? = null) = request("ssap://$path", payload)

    /**
     * Chiamata a un servizio interno luna:// che SSAP non espone.
     * Trucco noto (bscpylgtv): un avviso di sistema con l'azione luna, chiuso subito dopo.
     * Limite: la risposta del servizio non torna indietro.
     */
    suspend fun luna(uri: String, params: JSONObject) {
        val lunaUri = "luna://$uri"
        val action = JSONObject().put("uri", lunaUri).put("params", params)
        val payload = JSONObject()
            .put("message", " ")
            .put("buttons", JSONArray().put(JSONObject().put("label", "").put("onClick", lunaUri).put("params", params)))
            .put("onclose", action)
            .put("onfail", action)
        val r = request("ssap://system.notifications/createAlert", payload)
        val alertId = r.optString("alertId")
        if (alertId.isBlank()) throw SsapException("avviso non creato")
        request("ssap://system.notifications/closeAlert", JSONObject().put("alertId", alertId))
        Reports.log("Luna inviata: $uri ${params.toString().take(200)}")
    }

    // ---- tasti e puntatore (socket separato) ----

    private suspend fun pointerSocket(): WebSocket {
        pointer?.let { return it }
        val p = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
        val path = p.getString("socketPath")
        val opened = CompletableDeferred<Unit>()
        val s = http.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.completeExceptionally(t)
                if (pointer === webSocket) pointer = null
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (pointer === webSocket) pointer = null
            }
        })
        withTimeout(5_000) { opened.await() }
        pointer = s
        return s
    }

    suspend fun button(name: String) = withContext(Dispatchers.IO) {
        Reports.log("Tasto $name")
        if (!pointerSocket().send("type:button\nname:$name\n\n")) { pointer = null; pointerSocket().send("type:button\nname:$name\n\n") }
    }

    suspend fun move(dx: Int, dy: Int) = withContext(Dispatchers.IO) {
        pointerSocket().send("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")
    }

    suspend fun click() = withContext(Dispatchers.IO) {
        Reports.log("Clic puntatore")
        pointerSocket().send("type:click\n\n")
    }

    suspend fun scroll(dy: Int) = withContext(Dispatchers.IO) {
        pointerSocket().send("type:scroll\ndx:0\ndy:$dy\n\n")
    }
}
