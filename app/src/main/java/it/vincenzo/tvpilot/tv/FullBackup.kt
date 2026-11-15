package it.vincenzo.tvpilot.tv

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup completo: legge tutte le impostazioni che la TV lascia leggere (tutte le categorie del catalogo),
 * le salva, le confronta con lo stato attuale e rimette quelle cambiate.
 * File: memoria dell'app (backup-completo/) e Download/TVPilot/backup/completo-AAAAMMGG-HHMMSS.json
 */
object FullBackup {
    data class Snapshot(val id: String, val time: Long, val firmware: String, val values: Map<String, Map<String, Any?>>) {
        val count get() = values.values.sumOf { it.size }
    }

    data class Diff(val category: String, val key: String, val saved: Any?, val now: Any?, val risk: Risk)

    private fun dir(ctx: Context) = File(ctx.filesDir, "backup-completo").apply { mkdirs() }

    /** Legge tutto (può richiedere un minuto) */
    suspend fun read(ctx: Context, progress: (String) -> Unit): Map<String, Map<String, Any?>> {
        val out = linkedMapOf<String, Map<String, Any?>>()
        val cats = Catalog.categories(ctx)
        cats.forEachIndexed { i, c ->
            progress("Lettura ${i + 1}/${cats.size}: ${Risks.categoryName(c)}")
            val keys = Catalog.items(ctx, c).map { it.key }
            val values = runCatching { SettingsManager.readCategory(ctx, c, keys) }.getOrDefault(emptyMap()).filterValues { it != null }
            if (values.isNotEmpty()) out[c] = values
        }
        return out
    }

    suspend fun create(ctx: Context, progress: (String) -> Unit): Snapshot {
        val values = read(ctx, progress)
        val now = System.currentTimeMillis()
        val id = "completo-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ITALY).format(Date(now))
        val snap = Snapshot(id, now, Prefs.firmware(ctx) ?: "", values)
        val json = toJson(snap).toString(2)
        File(dir(ctx), "$id.json").writeText(json)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "$id.json")
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/${Backups.PUBLIC_FOLDER}")
                }
                ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)?.let { uri ->
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
            }
        }
        Reports.log("Backup completo: ${snap.count} valori in ${values.size} categorie")
        return snap
    }

    fun toJson(s: Snapshot): JSONObject {
        val cats = JSONObject()
        s.values.forEach { (c, m) -> cats.put(c, JSONObject().apply { m.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }) }
        return JSONObject().put("id", s.id).put("data", s.time).put("firmware", s.firmware).put("categorie", cats)
    }

    fun fromJson(j: JSONObject): Snapshot {
        val cats = j.getJSONObject("categorie")
        val values = cats.keys().asSequence().associateWith { c ->
            val o = cats.getJSONObject(c)
            o.keys().asSequence().associateWith { k -> o.opt(k) }
        }
        return Snapshot(j.getString("id"), j.getLong("data"), j.optString("firmware"), values)
    }

    fun list(ctx: Context): List<Snapshot> = dir(ctx).listFiles()?.mapNotNull { f ->
        runCatching { fromJson(JSONObject(f.readText())) }.getOrNull()
    }?.sortedByDescending { it.time }.orEmpty()

    fun delete(ctx: Context, id: String) { File(dir(ctx), "$id.json").delete() }

    /** Differenze tra il backup e lo stato attuale della TV */
    suspend fun compare(ctx: Context, s: Snapshot, progress: (String) -> Unit): List<Diff> {
        val diffs = mutableListOf<Diff>()
        val cats = s.values.keys.toList()
        cats.forEachIndexed { i, c ->
            progress("Confronto ${i + 1}/${cats.size}: ${Risks.categoryName(c)}")
            val saved = s.values[c].orEmpty()
            val now = runCatching { SettingsManager.readCategory(ctx, c, saved.keys.toList()) }.getOrDefault(emptyMap())
            saved.forEach { (k, v) ->
                val cur = now[k]
                if (cur != null && cur.toString() != v.toString()) diffs += Diff(c, k, v, cur, Risks.of(c, k))
            }
        }
        Reports.log("Confronto ${s.id}: ${diffs.size} differenze")
        return diffs.sortedWith(compareBy({ it.risk.ordinal }, { it.category }, { it.key }))
    }

    /** Rimette i valori del backup; le voci pericolose solo se richiesto */
    suspend fun restore(diffs: List<Diff>, includeHigh: Boolean): String {
        val chosen = diffs.filter { includeHigh || it.risk != Risk.HIGH }
        var ok = 0
        val failed = mutableListOf<String>()
        chosen.groupBy { it.category }.forEach { (c, list) ->
            val values = JSONObject().apply { list.forEach { put(it.key, it.saved ?: JSONObject.NULL) } }
            runCatching { SettingsManager.write(c, values) }
                .onSuccess { ok += list.size }
                .onFailure { failed += Risks.categoryName(c) }
        }
        Reports.log("Ripristino completo: $ok valori, non riuscite: $failed")
        val skipped = diffs.size - chosen.size
        return "Rimessi $ok valori" + (if (skipped > 0) ", $skipped pericolosi lasciati com'erano" else "") +
            (if (failed.isNotEmpty()) ". Non riuscite: ${failed.joinToString()}" else "")
    }
}
