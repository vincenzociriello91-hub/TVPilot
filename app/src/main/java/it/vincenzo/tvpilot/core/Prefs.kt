package it.vincenzo.tvpilot.core

import android.content.Context

/** Impostazioni salvate sul telefono (solo dati della TV di casa, nessun dato personale) */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("tvpilot", Context.MODE_PRIVATE)

    fun lastHost(ctx: Context): String? = p(ctx).getString("host", null)
    fun setLastHost(ctx: Context, host: String) = p(ctx).edit().putString("host", host).apply()

    fun tvName(ctx: Context): String? = p(ctx).getString("nome_tv", null)
    fun setTvName(ctx: Context, name: String) = p(ctx).edit().putString("nome_tv", name).apply()

    /** Chiave di abbinamento data dalla TV: evita di confermare ogni volta sul televisore */
    fun clientKey(ctx: Context, host: String): String? = p(ctx).getString("key_$host", null)
    fun setClientKey(ctx: Context, host: String, key: String) = p(ctx).edit().putString("key_$host", key).apply()
    fun forgetKey(ctx: Context, host: String) = p(ctx).edit().remove("key_$host").apply()

    /** Indirizzo MAC per accendere la TV dal telefono (Wake-on-LAN), uno per TV */
    fun mac(ctx: Context, host: String? = lastHost(ctx)): String? =
        host?.let { p(ctx).getString("mac_$it", null) } ?: p(ctx).getString("mac", null)
    fun setMac(ctx: Context, mac: String, host: String? = lastHost(ctx)) {
        val e = p(ctx).edit().putString("mac", mac)
        if (host != null) e.putString("mac_$host", mac)
        e.apply()
    }

    // ---- più TV salvate ----
    data class SavedTv(val host: String, val name: String)

    fun savedTvs(ctx: Context): List<SavedTv> {
        val arr = runCatching { org.json.JSONArray(p(ctx).getString("tv_salvate", "[]")) }.getOrDefault(org.json.JSONArray())
        val list = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { SavedTv(it.optString("host"), it.optString("nome", "TV LG")) }
        }.filter { it.host.isNotBlank() }
        val last = lastHost(ctx)
        return if (last != null && list.none { it.host == last }) list + SavedTv(last, tvName(ctx) ?: "TV LG") else list
    }

    fun saveTv(ctx: Context, host: String, name: String) {
        val list = savedTvs(ctx).filter { it.host != host } + SavedTv(host, name)
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONObject().put("host", it.host).put("nome", it.name)) }
        p(ctx).edit().putString("tv_salvate", arr.toString()).apply()
    }

    fun removeTv(ctx: Context, host: String) {
        val arr = org.json.JSONArray()
        savedTvs(ctx).filter { it.host != host }.forEach { arr.put(org.json.JSONObject().put("host", it.host).put("nome", it.name)) }
        p(ctx).edit().putString("tv_salvate", arr.toString()).remove("key_$host").apply()
    }

    // ---- telecomando personalizzato ----
    /** App preferite in prima pagina: "id|titolo" */
    fun favoriteApps(ctx: Context): List<String> = p(ctx).getString("app_preferite", "")!!.split('\n').filter { it.contains('|') }
    fun setFavoriteApps(ctx: Context, list: List<String>) = p(ctx).edit().putString("app_preferite", list.joinToString("\n")).apply()
    /** Sezioni nascoste del telecomando: numeri, multimedia, colori, sistema */
    fun hiddenSections(ctx: Context): Set<String> = p(ctx).getStringSet("sezioni_nascoste", emptySet()) ?: emptySet()
    fun setHiddenSections(ctx: Context, s: Set<String>) = p(ctx).edit().putStringSet("sezioni_nascoste", s).apply()

    /** Controlli sempre disponibili: notifica fissa e tasti volume del telefono */
    fun persistentControls(ctx: Context): Boolean = p(ctx).getBoolean("controlli_fissi", false)
    fun setPersistentControls(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("controlli_fissi", v).apply()
    fun volumeKeys(ctx: Context): Boolean = p(ctx).getBoolean("tasti_volume", true)
    fun setVolumeKeys(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("tasti_volume", v).apply()

    /** Ultima versione firmware vista: se cambia, l'app avvisa che la TV si è aggiornata */
    fun firmware(ctx: Context): String? = p(ctx).getString("firmware", null)
    fun setFirmware(ctx: Context, v: String) = p(ctx).edit().putString("firmware", v).apply()
    fun lastFirmwareCheck(ctx: Context): Long = p(ctx).getLong("firmware_check", 0)
    fun setLastFirmwareCheck(ctx: Context, t: Long) = p(ctx).edit().putLong("firmware_check", t).apply()

    /** Passphrase della modalità sviluppatore LG (mostrata sulla TV, cambia a ogni riattivazione) */
    fun devPassphrase(ctx: Context): String? = p(ctx).getString("dev_pass", null)
    fun setDevPassphrase(ctx: Context, v: String) = p(ctx).edit().putString("dev_pass", v).apply()

    /** Password SSH root (Homebrew Channel): predefinita "alpine" */
    fun rootPassword(ctx: Context): String = p(ctx).getString("root_pass", null) ?: "alpine"
    fun setRootPassword(ctx: Context, v: String) = p(ctx).edit().putString("root_pass", v).apply()

    fun haptics(ctx: Context): Boolean = p(ctx).getBoolean("vibrazione", true)
    fun setHaptics(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("vibrazione", v).apply()

    fun reportUrl(ctx: Context): String = p(ctx).getString("rapporti_url", null) ?: Reports.DEFAULT_URL
    fun setReportUrl(ctx: Context, v: String) = p(ctx).edit().putString("rapporti_url", v).apply()
}
