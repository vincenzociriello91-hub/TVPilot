package it.vincenzo.tvpilot.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import it.vincenzo.tvpilot.BuildConfig
import org.json.JSONObject
import java.io.File

/** Aggiornamento dall'app: legge l'ultima Release pubblica di TVPilot e installa sopra la versione attuale */
object Updater {
    data class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String)

    fun latest(): Release? {
        val j = JSONObject(Http.get("https://api.github.com/repos/vincenzociriello91-hub/TVPilot/releases/latest"))
        val code = j.getString("tag_name").removePrefix("v").toIntOrNull() ?: return null
        val assets = j.getJSONArray("assets")
        // solo l'APK del telefono: TVPilot-orologio.apk ha lo stesso applicationId e si installerebbe al suo posto
        val list = (0 until assets.length()).map { assets.getJSONObject(it) }
        val apk = list.firstOrNull { it.getString("name") == "TVPilot.apk" }
            ?: list.firstOrNull { val n = it.getString("name").lowercase(); n.endsWith(".apk") && !n.contains("orologio") && !n.contains("wear") }
            ?: return null
        return Release(code, j.optString("name"), j.optString("body"), apk.getString("browser_download_url"))
    }

    fun isNewer(r: Release) = r.versionCode > BuildConfig.VERSION_CODE

    /** Scarica l'APK (in background) e apre l'installer di sistema */
    fun install(ctx: Context, r: Release) {
        val f = File(ctx.cacheDir, "TVPilot-update.apk")
        Http.download(r.apkUrl, f)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Reports.log("Aggiornamento app scaricato: ${r.name}")
    }
}
