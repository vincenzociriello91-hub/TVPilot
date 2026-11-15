package it.vincenzo.tvpilot.tv

import android.content.Context
import it.vincenzo.tvpilot.core.Reports
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Routine: più comandi in un tocco ("Sera film", "Partita", "Notte"…) */
object Routines {
    enum class StepType(val label: String) {
        POWER_ON("Accendi la TV"), PICTURE("Modo immagine"), SOUND("Uscita audio"), VOLUME("Volume"),
        APP("Apri app"), INPUT("Ingresso"), SLEEP("Spegni tra (minuti)"), ENERGY("Risparmio energetico"), POWER_OFF("Spegni la TV"),
    }

    data class Step(val type: StepType, val value: String = "")
    data class Routine(val id: String, val name: String, val steps: List<Step>)

    val ENERGY = listOf("off" to "Spento", "min" to "Minimo", "med" to "Medio", "max" to "Massimo", "auto" to "Automatico")

    private fun p(ctx: Context) = ctx.getSharedPreferences("tvpilot", Context.MODE_PRIVATE)

    fun presets(): List<Routine> = listOf(
        Routine("film", "Sera film", listOf(Step(StepType.POWER_ON), Step(StepType.PICTURE, "cinema"), Step(StepType.ENERGY, "min"), Step(StepType.VOLUME, "18"))),
        Routine("partita", "Partita", listOf(Step(StepType.POWER_ON), Step(StepType.PICTURE, "sports"), Step(StepType.VOLUME, "25"))),
        Routine("notte", "Notte", listOf(Step(StepType.VOLUME, "10"), Step(StepType.ENERGY, "max"), Step(StepType.SLEEP, "30"))),
        Routine("gioco", "Gioco", listOf(Step(StepType.POWER_ON), Step(StepType.INPUT, "HDMI_1"), Step(StepType.PICTURE, "game"))),
    )

    fun all(ctx: Context): List<Routine> {
        val raw = p(ctx).getString("routine", null) ?: return presets()
        return runCatching { fromJson(JSONArray(raw)) }.getOrDefault(presets())
    }

    fun save(ctx: Context, list: List<Routine>) {
        p(ctx).edit().putString("routine", toJson(list).toString()).apply()
    }

    fun upsert(ctx: Context, r: Routine) = save(ctx, all(ctx).filter { it.id != r.id } + r)
    fun delete(ctx: Context, id: String) = save(ctx, all(ctx).filter { it.id != id })
    fun byId(ctx: Context, id: String) = all(ctx).firstOrNull { it.id == id }

    fun toJson(list: List<Routine>): JSONArray = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().put("id", r.id).put("nome", r.name).put("passi", JSONArray().apply {
                r.steps.forEach { s -> put(JSONObject().put("tipo", s.type.name).put("valore", s.value)) }
            }))
        }
    }

    fun fromJson(a: JSONArray): List<Routine> = (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
        val steps = o.optJSONArray("passi") ?: JSONArray()
        Routine(o.getString("id"), o.optString("nome"), (0 until steps.length()).mapNotNull { i ->
            val s = steps.getJSONObject(i)
            runCatching { Step(StepType.valueOf(s.getString("tipo")), s.optString("valore")) }.getOrNull()
        })
    }

    fun describe(s: Step): String = when (s.type) {
        StepType.PICTURE -> "Immagine: " + (Tv.PICTURE_MODES.firstOrNull { it.first == s.value }?.second ?: s.value)
        StepType.SOUND -> "Audio: " + (Tv.SOUND_OUTPUTS.firstOrNull { it.first == s.value }?.second ?: s.value)
        StepType.VOLUME -> "Volume ${s.value}"
        StepType.APP -> "Apri ${s.value.substringAfter('|', s.value)}"
        StepType.INPUT -> "Ingresso ${s.value.replace('_', ' ')}"
        StepType.SLEEP -> "Spegni tra ${s.value} min"
        StepType.ENERGY -> "Risparmio: " + (ENERGY.firstOrNull { it.first == s.value }?.second ?: s.value)
        else -> s.type.label
    }

    private suspend fun runStep(s: Step) {
        when (s.type) {
            StepType.POWER_ON -> if (!Tv.ensureConnected()) { Tv.powerOn(); delay(3000) }
            StepType.POWER_OFF -> if (Tv.ensureConnected()) Tv.powerOff()
            else -> {
                if (!Tv.ensureConnected()) error("TV non raggiungibile")
                when (s.type) {
                    StepType.PICTURE -> Tv.setPictureMode(s.value)
                    StepType.SOUND -> Tv.setSoundOutput(s.value)
                    StepType.VOLUME -> Tv.setVolume(s.value.toIntOrNull() ?: 15)
                    StepType.APP -> Tv.launch(s.value.substringBefore('|'))
                    StepType.INPUT -> Tv.switchInput(s.value)
                    StepType.SLEEP -> TvActions.sleepTimer(TvActions.nearestSleep(s.value.toIntOrNull() ?: 30))
                    StepType.ENERGY -> {
                        val payload = JSONObject().put("category", "picture").put("settings", JSONObject().put("energySaving", s.value))
                        try { Tv.client.ssap("settings/setSystemSettings", payload) }
                        catch (e: Exception) { Tv.client.luna("com.webos.settingsservice/setSystemSettings", payload) }
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Esegue i passi in ordine; un passo non riuscito non ferma gli altri */
    suspend fun run(r: Routine): String {
        Reports.log("Routine \"${r.name}\" (${r.steps.size} passi)")
        val failed = mutableListOf<String>()
        for (s in r.steps) {
            val ok = runCatching { runStep(s); delay(400) }.isSuccess
            if (!ok) failed += describe(s)
        }
        val msg = if (failed.isEmpty()) "Routine \"${r.name}\" fatta" else "Routine \"${r.name}\": non riusciti ${failed.joinToString()}"
        Reports.log(msg)
        return msg
    }
}
