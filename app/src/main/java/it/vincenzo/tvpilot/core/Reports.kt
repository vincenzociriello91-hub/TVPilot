package it.vincenzo.tvpilot.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import it.vincenzo.tvpilot.BuildConfig
import it.vincenzo.tvpilot.tv.Tv
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Rapporti completi ("vita, morte e miracoli") di ogni utilizzo.
 * - Ogni operazione finisce nel registro eventi, scritto anche su disco man mano:
 *   se Android chiude l'app senza avvisare, al riavvio il rapporto "chiusura-improvvisa" recupera tutto.
 * - flush() salva il rapporto in chiaro in Download/TVPilot_report (sul telefono)
 *   e lo mette in coda cifrato (RSA-OAEP-256 + AES-256-GCM) per il ricevitore rapporti-vincenzo,
 *   che lo salva nel repository privato TVPilot-rapporti. Senza rete resta in coda.
 * Niente token nell'app: l'APK è pubblico. Il contenuto si legge solo con la chiave privata.
 */
object Reports {
    const val FOLDER = "TVPilot_report"
    const val DEFAULT_URL = "https://rapporti-vincenzo.vincenzociriello91.workers.dev"
    private const val APP = "TVPilot"

    /** Chiave pubblica propria di TVPilot (dalla 1.6.0): la privata è su Drive, TVPILOT_RAPPORTI_CHIAVE.pem */
    private const val PUB_N = "kprvn66OmJUcc7ZJedi5dNPjlSwJAJB3Q69AH-zeHoRw86alwQNCoS9T7kCYQqaVi6b_qBUeiUR4b_JOIuWfNBrQpRpQTNCsBsaN4muDCGZEyvYnj6G7fmE5rWnlDovNWqlGqARPUD-OUGrGMamJqaka9j1mPaakQpIv2okK0ihmlClB6zd4ANDFQn8FFS08vXpiSk_ld55xIcadfWLIhBCDq1eyXnG3_W1Hn455h6l-wSIIDtpHXUunZ-umTFHc2bjxgBA1QxZLZ7GvdQj-xoHTPnmz1n-KGXensWnLGHhcywSg9aAx-xwYAVlR1PmzpfLVG0cy43RRXKS8169xKwNb_HFIUYpKj1iaVA8vRi0RqBw4LHKwRv_HewckMrAPK24faSfCIiFvucVZ0_2_LCj3HwV6fZ6Yh6DTyxIADuOD0zMjgir5n5oFd6D9dMTR5yXbhl09k_BZGMRoc3-m4CQYuM8rucoYtlqwdB9SB8x7FyA_zvHc9p6xo2sTffJN"
    private const val PUB_E = "AQAB"

    private val events = mutableListOf<Pair<Long, String>>()
    private var start = System.currentTimeMillis()
    private var app: Context? = null
    private var journal: File? = null
    private var lastMsg: String? = null
    private var repeats = 0

    /** Esito dell'ultimo invio, mostrato nella scheda Info */
    @Volatile var lastSend: String = "nessun invio ancora"

    fun init(ctx: Context) {
        app = ctx.applicationContext
        journal = File(ctx.filesDir, "eventi-in-corso.txt")
        recoverPrevious(ctx)
    }

    private fun closeRepeats() {
        if (repeats == 0) return
        val note = "(il messaggio precedente si è ripetuto altre $repeats volte)"
        repeats = 0
        val t = System.currentTimeMillis()
        events += t to note
        runCatching { journal?.appendText("$t\t$note\n") }
    }

    fun log(msg: String) {
        synchronized(events) {
            if (msg == lastMsg) { repeats++; return }
            closeRepeats()
            lastMsg = msg
            val t = System.currentTimeMillis()
            events += t to msg
            if (events.size > 3000) events.removeAt(0)
            runCatching { journal?.appendText("$t\t${msg.replace('\n', ' ')}\n") }
        }
    }

    private fun iso(t: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ITALY).format(Date(t))

    /** Fotografia dello stato al momento del rapporto (niente indirizzi MAC né chiavi) */
    private fun snapshot(): JSONObject {
        val o = JSONObject()
        val ctx = app ?: return o
        fun put(k: String, v: () -> Any?) {
            runCatching { o.put(k, v() ?: JSONObject.NULL) }.onFailure { o.put(k, "errore: ${it.message}") }
        }
        put("statoTv") { Tv.client.stato.value.name }
        put("dettaglioTv") { Tv.client.dettaglio.value }
        put("connessioneSicura") { Tv.client.sicuro }
        put("tvAbbinata") { Prefs.lastHost(ctx)?.let { Prefs.clientKey(ctx, it) != null } ?: false }
        put("macSalvato") { Prefs.mac(ctx) != null }
        put("firmwareTv") { Prefs.firmware(ctx) }
        put("modelloTv") { Tv.info.value["modello"] }
        put("backupSalvati") { it.vincenzo.tvpilot.tv.Backups.list(ctx).size }
        put("passphraseSviluppatore") { Prefs.devPassphrase(ctx) != null }
        put("rapportiInCoda") { queueDir(ctx).listFiles()?.size ?: 0 }
        return o
    }

    private fun build(reason: String, error: Throwable? = null, exit: JSONObject? = null, withState: Boolean = true): JSONObject {
        val state = if (!withState) JSONObject() else runCatching { snapshot() }.getOrElse { JSONObject().put("errore", it.message) }
        return synchronized(events) {
            closeRepeats()
            lastMsg = null
            val ev = JSONArray()
            events.forEach { (t, m) -> ev.put(JSONObject().put("ora", iso(t)).put("msg", m)) }
            val json = JSONObject()
                .put("app", APP)
                .put("versione", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("android", Build.VERSION.SDK_INT)
                .put("motivo", reason)
                .put("inizio", iso(events.firstOrNull()?.first ?: start))
                .put("fine", iso(events.lastOrNull()?.first?.takeIf { exit != null } ?: System.currentTimeMillis()))
                .put("eventi", ev)
                .put("stato", state)
            if (error != null) json.put("errore", error.stackTraceToString())
            if (exit != null) json.put("uscita", exit)
            events.clear()
            runCatching { journal?.writeText("") }
            start = System.currentTimeMillis()
            json
        }
    }

    private fun queueDir(ctx: Context) = File(ctx.filesDir, "rapporti-coda").apply { mkdirs() }

    /** Copia leggibile sul telefono + copia cifrata in coda per l'invio */
    private fun save(json: JSONObject, reason: String) {
        val ctx = app ?: return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ITALY).format(Date())
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$stamp-$reason.json")
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            }
            val uri = if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) else null
            if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
            pruneLocal(ctx)
        }
        runCatching { File(queueDir(ctx), "$stamp-$reason.json").writeText(encrypt(json.toString()).toString()) }
    }

    /** Tiene solo le ultime 60 copie leggibili in Download/TVPilot_report (prima crescevano senza limite) */
    private fun pruneLocal(ctx: Context, keep: Int = 60) {
        if (Build.VERSION.SDK_INT < 29) return
        val uris = mutableListOf<android.net.Uri>()
        ctx.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf("%$FOLDER%"),
            "${MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) uris += android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
        }
        uris.drop(keep).forEach { runCatching { ctx.contentResolver.delete(it, null, null) } }
    }

    /** Cifratura compatibile con WebCrypto: AES-GCM 256 (tag in coda) + chiave AES cifrata RSA-OAEP SHA-256 */
    fun encrypt(text: String): JSONObject {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val aes = keyGen.generateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val gcm = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, aes, GCMParameterSpec(128, iv)) }
        val data = gcm.doFinal(text.toByteArray())
        val n = BigInteger(1, Base64.decode(PUB_N, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        val e = BigInteger(1, Base64.decode(PUB_E, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        val pub = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e))
        val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding").apply {
            init(Cipher.ENCRYPT_MODE, pub, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
        }
        val key = rsa.doFinal(aes.encoded)
        fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        return JSONObject().put("app", APP).put("v", BuildConfig.VERSION_NAME).put("alg", "RSA-OAEP-256+A256GCM")
            .put("key", b64(key)).put("iv", b64(iv)).put("data", b64(data))
    }

    /** Invia la coda al ricevitore (da chiamare fuori dal thread principale) */
    private val sending = java.util.concurrent.atomic.AtomicBoolean(false)

    fun sendQueue() {
        // apertura e chiusura dell'app possono chiamarla insieme: un solo invio alla volta, niente doppioni
        if (!sending.compareAndSet(false, true)) return
        try { sendQueueNow() } finally { sending.set(false) }
    }

    private fun sendQueueNow() {
        val ctx = app ?: return
        val files = queueDir(ctx).listFiles()?.sortedBy { it.name }?.take(40).orEmpty()
        if (files.isEmpty()) return
        val arr = JSONArray()
        files.forEach { f -> runCatching { arr.put(JSONObject(f.readText())) }.onFailure { f.delete() } }
        val result = runCatching { Http.post("${Prefs.reportUrl(ctx).trimEnd('/')}/r/$APP", arr.toString()) }
        lastSend = result.fold(
            onSuccess = { (code, body) ->
                if (code in 200..299) files.forEach { it.delete() }
                "${iso(System.currentTimeMillis())}: " + if (code in 200..299) "OK (${files.size} inviati)" else "errore $code ${body.take(120)}"
            },
            onFailure = { "${iso(System.currentTimeMillis())}: niente rete (${it.message}), restano in coda" },
        )
    }

    fun flush(reason: String) = save(build(reason), reason)

    fun saveCrash(e: Throwable) {
        log("CRASH: ${e.javaClass.simpleName}: ${e.message}")
        save(build("crash", e), "crash")
    }

    fun exitReasonText(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "app bloccata (non rispondeva)"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash nativo"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "memoria del telefono esaurita"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "troppe risorse usate"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "chiusa dall'utente (arresto forzato o aggiornamento)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "fermata dall'utente"
        ApplicationExitInfo.REASON_SIGNALED -> "terminata dal sistema"
        ApplicationExitInfo.REASON_EXIT_SELF -> "uscita normale"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permessi cambiati"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "un servizio da cui dipendeva si è fermato"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "avvio non riuscito"
        13 -> "chiusa dal sistema (altro motivo)"
        14 -> "congelata dal sistema"
        15 -> "stato dell'app cambiato"
        16 -> "aggiornamento dell'app"
        else -> "altro motivo ($reason)"
    }

    /** All'avvio: se la sessione precedente non ha salvato il suo rapporto, lo salva ora */
    private fun recoverPrevious(ctx: Context) {
        val lines = runCatching { journal?.readLines() }.getOrNull().orEmpty().filter { it.isNotBlank() }
        val prefs = ctx.getSharedPreferences("tvpilot", Context.MODE_PRIVATE)
        val exit = if (Build.VERSION.SDK_INT >= 30) runCatching {
            ctx.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull()
        }.getOrNull()?.takeIf { it.timestamp > prefs.getLong("ultima_uscita", 0) } else null
        exit?.let { prefs.edit().putLong("ultima_uscita", it.timestamp).apply() }
        val serious = Build.VERSION.SDK_INT >= 30 &&
            (exit?.reason == ApplicationExitInfo.REASON_ANR || exit?.reason == ApplicationExitInfo.REASON_CRASH_NATIVE)
        if (lines.isEmpty() && !serious) return
        synchronized(events) {
            lines.forEach { l ->
                val tab = l.indexOf('\t')
                val t = l.substring(0, tab.coerceAtLeast(0)).toLongOrNull() ?: return@forEach
                events += t to l.substring(tab + 1)
            }
        }
        val info = if (exit != null && Build.VERSION.SDK_INT >= 30) JSONObject()
            .put("motivo", exitReasonText(exit.reason))
            .put("descrizione", exit.description ?: "")
            .put("ora", iso(exit.timestamp))
        else JSONObject().put("motivo", "sconosciuto")
        // chiusure volute (arresto forzato, aggiornamento) non sono errori: motivo separato per non confonderle
        val voluta = Build.VERSION.SDK_INT >= 30 && exit != null && exit.reason in setOf(
            ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED, 16,
        )
        val motivo = if (voluta) "chiusura-utente" else "chiusura-improvvisa"
        save(build(motivo, exit = info, withState = false), motivo)
    }
}
