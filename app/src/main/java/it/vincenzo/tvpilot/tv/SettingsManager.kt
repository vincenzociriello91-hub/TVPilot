package it.vincenzo.tvpilot.tv

import android.content.Context
import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Modifica delle impostazioni della TV, sempre con backup prima di toccare qualcosa */
object SettingsManager {
    private const val LUNA = "servizio interno (luna)"
    data class Result(val ok: Boolean, val message: String, val backupPath: String?)

    /** Legge i valori attuali di una categoria: prima tutti insieme, se la TV rifiuta uno per uno */
    suspend fun readCategory(ctx: Context, category: String, keys: List<String>): Map<String, Any?> {
        val all = Tv.readSettings(category, keys)
        if (all != null && all.length() > 0) return keys.associateWith { all.opt(it) }
        val out = mutableMapOf<String, Any?>()
        for (k in keys) Tv.readSettings(category, listOf(k))?.opt(k)?.let { out[k] = it }
        Reports.log("Lettura $category: ${out.size}/${keys.size} valori leggibili")
        return out
    }

    /**
     * 1) legge i valori attuali (se la TV lo permette, altrimenti usa i valori tipici di fabbrica)
     * 2) salva il backup  3) applica (API pubblica, se rifiuta servizio interno luna)  4) verifica rileggendo
     */
    suspend fun apply(ctx: Context, category: String, newValues: JSONObject, note: String): Result {
        val keys = newValues.keys().asSequence().toList()
        val current = Tv.readSettings(category, keys)
        val before = JSONObject()
        var allRead = true
        for (k in keys) {
            val v = current?.opt(k)
            if (v != null) before.put(k, v) else {
                allRead = false
                before.put(k, Catalog.typical(ctx, category, k) ?: JSONObject.NULL)
            }
        }
        val id = Backups.newId(category)
        var method = ""
        val path = Backups.save(ctx, Backups.Backup(id, System.currentTimeMillis(), category, before, newValues, allRead, "in corso", note, false), publicCopy = false)
        try {
            method = write(category, newValues)
        } catch (e: Exception) {
            Reports.log("Modifica $category non riuscita: ${e.message}")
            return Result(false, "La TV non ha accettato la modifica: ${e.message}", path)
        }
        Backups.save(ctx, Backups.Backup(id, System.currentTimeMillis(), category, before, newValues, allRead, method, note, false))
        // il servizio interno (luna) applica la modifica dopo la chiusura dell'avviso: si rilegge con qualche attesa
        var check: JSONObject? = null
        var verified = false
        for (wait in if (method == LUNA) listOf(700L, 1300L) else listOf(0L)) {
            delay(wait)
            val read = Tv.readSettings(category, keys)
            check = read
            verified = read != null && keys.all { read.opt(it)?.toString() == newValues.opt(it)?.toString() }
            if (verified) break
        }
        val msg = buildString {
            append("Fatto con $method. ")
            append(when {
                verified -> "Verificato sulla TV. "
                check == null -> "La TV non permette di rileggere il valore: controlla sullo schermo. "
                else -> "La TV risponde ancora con il valore vecchio: controlla sullo schermo o riprova. "
            })
            if (!allRead) append("Alcuni valori precedenti non erano leggibili: il backup usa quelli tipici di fabbrica. ")
            append("Backup: $path")
        }
        Reports.log("Modifica $category ${newValues.toString().take(200)} → $method, verificato=$verified, riletto=${check?.toString()?.take(200) ?: "non leggibile"}")
        return Result(true, msg, path)
    }

    /** Scrive: prima l'API pubblica (risponde con esito), poi il servizio interno (senza esito) */
    suspend fun write(category: String, values: JSONObject): String {
        val payload = JSONObject().put("category", category).put("settings", values)
        return try {
            Tv.client.ssap("settings/setSystemSettings", payload)
            "API pubblica"
        } catch (e: Exception) {
            Tv.client.luna("com.webos.settingsservice/setSystemSettings", payload)
            LUNA
        }
    }

    suspend fun restore(ctx: Context, b: Backups.Backup): Result = try {
        // i valori "prima" sconosciuti sono salvati come null: mandarli alla TV come null non è sicuro, si saltano
        val values = JSONObject()
        b.before.keys().forEach { k -> if (!b.before.isNull(k)) values.put(k, b.before.get(k)) }
        if (values.length() == 0) error("il backup non contiene valori precedenti noti: rimetti l'impostazione a mano sulla TV")
        val method = write(b.category, values)
        Backups.markRestored(ctx, b)
        Reports.log("Ripristino ${b.id} con $method")
        Result(true, "Ripristinato con $method. Valori rimessi: $values", null)
    } catch (e: Exception) {
        Reports.log("Ripristino ${b.id} non riuscito: ${e.message}")
        Result(false, "Ripristino non riuscito: ${e.message}", null)
    }
}
