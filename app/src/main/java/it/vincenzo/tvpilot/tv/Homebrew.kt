package it.vincenzo.tvpilot.tv

import it.vincenzo.tvpilot.core.Http
import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Catalogo app della comunità webOS Homebrew (repo.webosbrew.org) */
object Homebrew {
    private const val BASE = "https://repo.webosbrew.org/api"
    const val HBCHANNEL_ID = "org.webosbrew.hbchannel"

    data class Package(
        val id: String,
        val title: String,
        val description: String,
        val version: String,
        val ipkUrl: String,
        val rootRequired: String,
        val icon: String,
        /** Perché è consigliata (null se non è tra le consigliate) */
        val tip: String? = null,
    )

    /** App consigliate: parola nel titolo o nell'id → perché installarla */
    private val RECOMMENDED = listOf(
        "hbchannel" to "Il negozio di app Homebrew: serve per tutte le altre",
        "youtube" to "YouTube senza pubblicità e con SponsorBlock",
        "jellyfin" to "Guarda film e serie dal tuo PC o NAS",
        "kodi" to "Lettore multimediale completo",
        "moonlight" to "Gioca ai giochi del PC sulla TV (con Sunshine/GeForce)",
        "piccap" to "Luci Ambilight dietro la TV (con Hyperion)",
        "hyperion" to "Luci Ambilight dietro la TV",
        "adblock" to "Blocca la pubblicità di sistema",
        "crunchyroll" to "Crunchyroll senza pubblicità",
    )

    fun tipFor(id: String, title: String): String? {
        val t = (id + " " + title).lowercase()
        return RECOMMENDED.firstOrNull { t.contains(it.first) }?.second
    }

    suspend fun all(): List<Package> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Package>()
        var page = 1
        var max = 1
        do {
            val url = if (page > 1) "$BASE/apps/$page.json" else "$BASE/apps.json"
            val j = JSONObject(Http.get(url))
            max = j.optJSONObject("paging")?.optInt("maxPage", 1) ?: 1
            val arr = j.optJSONArray("packages")
            if (arr != null) for (i in 0 until arr.length()) parse(arr.getJSONObject(i))?.let { out += it }
            page++
        } while (page <= max && page < 20)
        Reports.log("Catalogo Homebrew: ${out.size} app")
        out.sortedWith(compareBy<Package> { it.id != HBCHANNEL_ID }.thenBy { it.tip == null }.thenBy { it.title.lowercase() })
    }

    private fun parse(p: JSONObject): Package? {
        val manifest = p.optJSONObject("manifest")
            ?: p.optString("manifestUrl").takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(Http.get(it)) }.getOrNull() }
            ?: return null
        val ipk = manifest.optString("ipkUrl").takeIf { it.isNotBlank() } ?: return null
        val root = when (val r = manifest.opt("rootRequired")) {
            true -> "serve il root"
            "optional" -> "meglio con root"
            else -> if (r == null || r == false) "senza root" else r.toString()
        }
        return Package(
            p.optString("id"), p.optString("title"),
            p.optString("shortDescription").ifBlank { manifest.optString("appDescription") },
            manifest.optString("version"), ipk, root, p.optString("iconUri"),
            tipFor(p.optString("id"), p.optString("title")),
        )
    }

    suspend fun download(pkg: Package, cacheDir: File): File = withContext(Dispatchers.IO) {
        val f = File(cacheDir, "${pkg.id}.ipk")
        Http.download(pkg.ipkUrl, f)
        f
    }
}
