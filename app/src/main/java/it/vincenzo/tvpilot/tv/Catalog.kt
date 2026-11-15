package it.vincenzo.tvpilot.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Elenco delle impostazioni di webOS 4.x (dalla documentazione di bscpylgtv, modello 2019 della stessa generazione
 * della 43LM6300PLA). Contiene il valore tipico di fabbrica e i valori ammessi quando sono noti.
 */
object Catalog {
    data class Item(
        val category: String,
        val key: String,
        val typical: Any?,
        val allowed: Any?,
        val risk: Risk,
        val readable: Boolean,
    )

    private var data: JSONObject? = null

    fun load(ctx: Context): JSONObject = data ?: JSONObject(
        ctx.assets.open("impostazioni.json").bufferedReader().use { it.readText() }
    ).also { data = it }

    fun categories(ctx: Context): List<String> {
        val c = load(ctx).getJSONObject("categorie")
        return c.keys().asSequence().toList().sortedWith(
            compareBy<String> { cat -> c.getJSONObject(cat).keys().asSequence().map { Risks.of(cat, it).ordinal }.maxOrNull() ?: 0 }
                .thenBy { Risks.categoryName(it) }
        )
    }

    fun items(ctx: Context, category: String): List<Item> {
        val root = load(ctx)
        val cat = root.getJSONObject("categorie").optJSONObject(category) ?: return emptyList()
        val values = root.getJSONObject("valori").optJSONObject(category)
        val readable = root.getJSONObject("leggibili").optJSONArray(category)
            ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
        return cat.keys().asSequence().map { k ->
            Item(category, k, cat.opt(k), values?.opt(k), Risks.of(category, k), k in readable)
        }.sortedWith(compareBy<Item> { it.risk.ordinal }.thenBy { it.key.lowercase() }).toList()
    }

    fun typical(ctx: Context, category: String, key: String): Any? =
        load(ctx).getJSONObject("categorie").optJSONObject(category)?.opt(key)

    /** Converte il testo scritto dall'utente nel tipo del valore tipico (numero, booleano, JSON o testo) */
    fun parse(text: String, like: Any?): Any {
        val t = text.trim()
        return when (like) {
            is Boolean -> t.equals("true", true) || t == "1" || t.equals("on", true)
            is Int, is Long -> t.toLongOrNull() ?: t
            is Double -> t.toDoubleOrNull() ?: t
            is JSONObject -> runCatching { JSONObject(t) }.getOrElse { t }
            is JSONArray -> runCatching { JSONArray(t) }.getOrElse { t }
            else -> t
        }
    }

    /** Valori ammessi come lista di stringhe (se il catalogo li elenca) */
    fun choices(allowed: Any?): List<String> = when (allowed) {
        is JSONArray -> (0 until allowed.length()).map { allowed.get(it).toString() }
        else -> emptyList()
    }

    fun show(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "—"
        else -> v.toString()
    }
}
