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

    /** Indirizzo MAC per accendere la TV dal telefono (Wake-on-LAN) */
    fun mac(ctx: Context): String? = p(ctx).getString("mac", null)
    fun setMac(ctx: Context, mac: String) = p(ctx).edit().putString("mac", mac).apply()

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
