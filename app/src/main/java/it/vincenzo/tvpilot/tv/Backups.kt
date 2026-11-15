package it.vincenzo.tvpilot.tv

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import it.vincenzo.tvpilot.core.Reports
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup legato a ogni modifica della scheda Sviluppatore e della Pulizia.
 * Dove finisce:
 *  1. nella memoria interna dell'app (files/backup) → elenco "Backup" nella scheda Sviluppatore, con "Ripristina";
 *  2. copia leggibile in Download/TVPilot/backup sul telefono (resta anche se disinstalli l'app).
 * Ripristino: rimette sulla TV i valori "prima" salvati nel file.
 */
object Backups {
    data class Backup(
        val id: String,
        val time: Long,
        val category: String,
        val before: JSONObject,
        val after: JSONObject,
        /** true se i valori "prima" sono stati letti dalla TV; false = valori tipici di fabbrica */
        val beforeRead: Boolean,
        val method: String,
        val note: String,
        val restored: Boolean,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id).put("data", time).put("categoria", category)
            .put("prima", before).put("dopo", after).put("primaLetta", beforeRead)
            .put("metodo", method).put("nota", note).put("ripristinato", restored)

        companion object {
            fun from(j: JSONObject) = Backup(
                j.getString("id"), j.getLong("data"), j.getString("categoria"),
                j.getJSONObject("prima"), j.getJSONObject("dopo"), j.optBoolean("primaLetta"),
                j.optString("metodo"), j.optString("nota"), j.optBoolean("ripristinato"),
            )
        }
    }

    const val PUBLIC_FOLDER = "TVPilot/backup"

    private fun dir(ctx: Context) = File(ctx.filesDir, "backup").apply { mkdirs() }

    fun save(ctx: Context, b: Backup): String {
        File(dir(ctx), "${b.id}.json").writeText(b.toJson().toString(2))
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${b.id}.json")
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_FOLDER")
                }
                ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(b.toJson().toString(2).toByteArray()) }
                }
            }
        }
        Reports.log("Backup salvato ${b.id} (${b.category}: ${b.before.keys().asSequence().joinToString()})")
        return "Download/$PUBLIC_FOLDER/${b.id}.json"
    }

    fun list(ctx: Context): List<Backup> = dir(ctx).listFiles()?.mapNotNull { f ->
        runCatching { Backup.from(JSONObject(f.readText())) }.getOrNull()
    }?.sortedByDescending { it.time }.orEmpty()

    fun markRestored(ctx: Context, b: Backup) {
        File(dir(ctx), "${b.id}.json").writeText(b.copy(restored = true).toJson().toString(2))
    }

    fun delete(ctx: Context, id: String) { File(dir(ctx), "$id.json").delete() }

    fun newId(category: String): String =
        "backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ITALY).format(Date()) + "-" + category.ifBlank { "sistema" }
}
