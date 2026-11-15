package it.vincenzo.tvpilot.tv

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import it.vincenzo.tvpilot.core.Http
import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * SSH verso la TV, in due modi:
 *  - SVILUPPATORE: modalità sviluppatore ufficiale LG (app "Developer Mode" sulla TV), utente prisoner, porta 9922,
 *    chiave scaricata da http://IP:9991/webos_rsa e sbloccata con la passphrase mostrata sulla TV.
 *  - ROOT: TV con root e Homebrew Channel (SSH attivo nelle sue impostazioni), utente root, porta 22.
 */
object Ssh {
    enum class Mode(val label: String) { DEV("Modalità sviluppatore LG"), ROOT("Root (Homebrew Channel)") }

    data class TvApp(val id: String, val title: String, val version: String)

    init {
        // webOS usa algoritmi vecchi (ssh-rsa con SHA-1): li riabilito solo per questo client
        JSch.setConfig("server_host_key", JSch.getConfig("server_host_key") + ",ssh-rsa")
        JSch.setConfig("PubkeyAcceptedAlgorithms", JSch.getConfig("PubkeyAcceptedAlgorithms") + ",ssh-rsa")
        JSch.setConfig("kex", JSch.getConfig("kex") + ",diffie-hellman-group14-sha1,diffie-hellman-group1-sha1")
    }

    private val sessions = mutableMapOf<Mode, Session>()

    suspend fun connectDev(host: String, passphrase: String): Session = withContext(Dispatchers.IO) {
        sessions[Mode.DEV]?.takeIf { it.isConnected }?.let { return@withContext it }
        val key = try {
            Http.bytes("http://$host:9991/webos_rsa", 6000)
        } catch (e: Exception) {
            throw IllegalStateException("Chiave non disponibile: sulla TV apri l'app Developer Mode e attiva \"Key Server\". (${e.message})")
        }
        val jsch = JSch()
        jsch.addIdentity("webos", key, null, passphrase.trim().toByteArray())
        val s = jsch.getSession("prisoner", host, 9922)
        s.setConfig("StrictHostKeyChecking", "no")
        s.connect(10_000)
        sessions[Mode.DEV] = s
        Reports.log("SSH sviluppatore collegato")
        s
    }

    suspend fun connectRoot(host: String, password: String): Session = withContext(Dispatchers.IO) {
        sessions[Mode.ROOT]?.takeIf { it.isConnected }?.let { return@withContext it }
        val s = JSch().getSession("root", host, 22)
        s.setPassword(password)
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.connect(10_000)
        sessions[Mode.ROOT] = s
        Reports.log("SSH root collegato")
        s
    }

    fun session(mode: Mode): Session? = sessions[mode]?.takeIf { it.isConnected }

    fun disconnect(mode: Mode) { sessions.remove(mode)?.disconnect() }

    /** Esegue un comando; stopWhen permette di fermarsi appena l'output contiene l'esito */
    suspend fun exec(s: Session, cmd: String, timeoutMs: Long = 60_000, stopWhen: ((String) -> Boolean)? = null): String =
        withContext(Dispatchers.IO) {
            val ch = s.openChannel("exec") as ChannelExec
            ch.setCommand(cmd)
            val err = ByteArrayOutputStream()
            ch.setErrStream(err)
            val input = ch.inputStream
            ch.connect(10_000)
            val sb = StringBuilder()
            val buf = ByteArray(4096)
            val end = System.currentTimeMillis() + timeoutMs
            while (true) {
                while (input.available() > 0) {
                    val n = input.read(buf)
                    if (n < 0) break
                    sb.append(String(buf, 0, n))
                }
                if (stopWhen?.invoke(sb.toString()) == true) break
                if (ch.isClosed && input.available() <= 0) break
                if (System.currentTimeMillis() > end) { sb.append("\n[tempo scaduto]"); break }
                Thread.sleep(150)
            }
            ch.disconnect()
            val e = err.toString()
            Reports.log("SSH: ${cmd.take(120)} → ${sb.length} caratteri${if (e.isNotBlank()) ", errori: ${e.take(200)}" else ""}")
            if (e.isNotBlank()) "$sb\n$e" else sb.toString()
        }

    private fun luna(mode: Mode) = if (mode == Mode.ROOT) "luna-send" else "luna-send-pub"

    private fun q(json: JSONObject) = "'" + json.toString().replace("'", "'\\''") + "'"

    /** Carica l'IPK sulla TV e lo installa; restituisce il testo dell'esito */
    suspend fun installIpk(mode: Mode, local: File, progress: (String) -> Unit): String {
        val s = session(mode) ?: error("Prima collegati (${mode.label})")
        val remote = "/media/developer/temp/tvpilot_${System.currentTimeMillis()}.ipk"
        exec(s, "mkdir -p /media/developer/temp")
        progress("Invio del file alla TV…")
        withContext(Dispatchers.IO) {
            val ch = s.openChannel("sftp") as ChannelSftp
            ch.connect(10_000)
            try { local.inputStream().use { ch.put(it, remote) } } finally { ch.disconnect() }
        }
        progress("Installazione in corso…")
        val params = JSONObject().put("id", "com.ares.defaultName").put("ipkUrl", remote).put("subscribe", true)
        val out = exec(s, "${luna(mode)} -n 50 luna://com.webos.appInstallService/dev/install ${q(params)}", 600_000) { o ->
            o.contains("installed", true) || o.contains("FAILED", false) || o.contains("\"returnValue\":false")
        }
        exec(s, "rm -f $remote")
        val ok = out.contains("installed", true) && !out.contains("FAILED")
        Reports.log("Installazione IPK ${local.name} (${mode.name}): ${if (ok) "OK" else "errore"}")
        return if (ok) "Installata. La trovi tra le app della TV." else "Installazione non riuscita:\n${out.takeLast(600)}"
    }

    suspend fun listApps(mode: Mode): List<TvApp> {
        val s = session(mode) ?: return emptyList()
        val uri = if (mode == Mode.ROOT) "luna://com.webos.applicationManager/listApps" else "luna://com.webos.applicationManager/dev/listApps"
        val out = exec(s, "${luna(mode)} -n 1 $uri '{}'", 20_000)
        val json = runCatching { JSONObject(out.substring(out.indexOf('{'))) }.getOrNull() ?: return emptyList()
        val arr = json.optJSONArray("apps") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { mode == Mode.DEV || it.optBoolean("removable", false) || it.optString("folderPath").contains("/media/developer") }
            .map { TvApp(it.optString("id"), it.optString("title"), it.optString("version")) }
            .sortedBy { it.title.lowercase() }
    }

    suspend fun removeApp(mode: Mode, id: String): String {
        val s = session(mode) ?: error("Prima collegati")
        val uri = if (mode == Mode.ROOT) "luna://com.webos.appInstallService/remove" else "luna://com.webos.appInstallService/dev/remove"
        val out = exec(s, "${luna(mode)} -n 20 $uri ${q(JSONObject().put("id", id).put("subscribe", true))}", 120_000) { o ->
            o.contains("removed", true) || o.contains("\"returnValue\":false")
        }
        Reports.log("Rimozione app $id (${mode.name})")
        return if (out.contains("removed", true)) "App rimossa." else "Rimozione non riuscita:\n${out.takeLast(400)}"
    }

    // ---- sessione modalità sviluppatore (scade dopo circa 50 ore) ----

    suspend fun devToken(): String? {
        val s = session(Mode.DEV) ?: return null
        return exec(s, "cat /var/luna/preferences/devmode_enabled", 10_000).trim().takeIf { it.length in 16..200 && !it.contains(' ') }
    }

    suspend fun devRemaining(token: String): String = withContext(Dispatchers.IO) {
        val j = JSONObject(Http.get("https://developer.lge.com/secure/CheckDevModeSession.dev?sessionToken=$token"))
        j.optString("errorMsg").ifBlank { j.toString() }
    }

    suspend fun devExtend(token: String): Boolean = withContext(Dispatchers.IO) {
        val r = Http.get("https://developer.lge.com/secure/ResetDevModeSession.dev?sessionToken=$token")
        val ok = runCatching { JSONObject(r).optString("result") == "success" }.getOrDefault(false)
        Reports.log("Rinnovo sessione sviluppatore: ${if (ok) "OK" else r.take(200)}")
        ok
    }

    /** Verifica root: chi sono, e se c'è Homebrew Channel */
    suspend fun rootInfo(): String {
        val s = session(Mode.ROOT) ?: return "Non collegato"
        return exec(s, "id; uname -r; ls -d /media/developer/apps/usr/palm/applications/org.webosbrew.hbchannel 2>/dev/null && echo HBCHANNEL_OK", 10_000)
    }
}
