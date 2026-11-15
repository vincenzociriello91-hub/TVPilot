package it.vincenzo.tvpilot.quick

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Routines
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.TvActions
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Programmi orari (accendi, spegni, routine a un'ora precisa, nei giorni scelti) e limite di tempo.
 * Usa allarmi non esatti con una finestra di 5 minuti: niente permessi speciali, funziona su qualsiasi Android.
 */
object Schedules {
    data class Schedule(val id: String, val hour: Int, val minute: Int, val days: Set<Int>, val action: String, val enabled: Boolean = true) {
        val label: String get() = "%02d:%02d".format(hour, minute)
    }

    /** Giorni: Calendar.MONDAY (2) … Calendar.SUNDAY (1) */
    val DAYS = listOf(Calendar.MONDAY to "L", Calendar.TUESDAY to "M", Calendar.WEDNESDAY to "M", Calendar.THURSDAY to "G",
        Calendar.FRIDAY to "V", Calendar.SATURDAY to "S", Calendar.SUNDAY to "D")

    private fun p(ctx: Context) = ctx.getSharedPreferences("tvpilot", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Schedule> = runCatching {
        val a = JSONArray(p(ctx).getString("programmi", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val d = o.optJSONArray("giorni") ?: JSONArray()
            Schedule(o.getString("id"), o.getInt("ora"), o.getInt("minuto"), (0 until d.length()).map { d.getInt(it) }.toSet(),
                o.getString("azione"), o.optBoolean("attivo", true))
        }
    }.getOrDefault(emptyList())

    fun save(ctx: Context, list: List<Schedule>) {
        val a = JSONArray()
        list.forEach { s ->
            a.put(JSONObject().put("id", s.id).put("ora", s.hour).put("minuto", s.minute).put("giorni", JSONArray(s.days.toList()))
                .put("azione", s.action).put("attivo", s.enabled))
        }
        p(ctx).edit().putString("programmi", a.toString()).apply()
        scheduleAll(ctx)
    }

    fun actionLabel(ctx: Context, action: String): String = when {
        action == "POWER_ON" -> "Accendi la TV"
        action == "POWER_OFF" -> "Spegni la TV"
        action.startsWith("ROUTINE:") -> "Routine " + (Routines.byId(ctx, action.removePrefix("ROUTINE:"))?.name ?: "?")
        else -> action
    }

    /** Prossimo momento valido (oggi o nei giorni seguenti) */
    fun next(s: Schedule, now: Calendar = Calendar.getInstance()): Long {
        val c = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, s.hour); set(Calendar.MINUTE, s.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            if (c.timeInMillis > now.timeInMillis && (s.days.isEmpty() || c.get(Calendar.DAY_OF_WEEK) in s.days)) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    private fun pending(ctx: Context, key: String, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, key.hashCode(),
            Intent(ctx, ScheduleReceiver::class.java).setAction("it.vincenzo.tvpilot.PROGRAMMA.$key").putExtra("chiave", key).putExtra("azione", action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun alarm(ctx: Context, at: Long, key: String, action: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 5 * 60_000L, pending(ctx, key, action))
    }

    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        p(ctx).getStringSet("programmi_attivi", emptySet())?.forEach { am.cancel(pending(ctx, it, "")) }
        val active = mutableSetOf<String>()
        all(ctx).filter { it.enabled }.forEach { s ->
            alarm(ctx, next(s), s.id, s.action)
            active += s.id
        }
        p(ctx).edit().putStringSet("programmi_attivi", active).apply()
        // limite di tempo ancora in corso (es. dopo un riavvio del telefono)
        val end = limitEnd(ctx)
        if (end > System.currentTimeMillis()) setLimitAlarms(ctx, end)
    }

    // ---- limite di tempo ----
    fun limitEnd(ctx: Context): Long = p(ctx).getLong("limite_fine", 0)

    fun startLimit(ctx: Context, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        p(ctx).edit().putLong("limite_fine", end).apply()
        setLimitAlarms(ctx, end)
        Reports.log("Limite di tempo: $minutes minuti")
    }

    private fun setLimitAlarms(ctx: Context, end: Long) {
        val warn = end - 5 * 60_000L
        if (warn > System.currentTimeMillis()) alarm(ctx, warn, "limite_avviso", "LIMIT_WARN")
        alarm(ctx, end, "limite_fine", "LIMIT_OFF")
    }

    fun cancelLimit(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, "limite_avviso", "")); am.cancel(pending(ctx, "limite_fine", ""))
        p(ctx).edit().remove("limite_fine").apply()
        Reports.log("Limite di tempo annullato")
    }

    suspend fun execute(ctx: Context, action: String): String = when {
        action == "POWER_ON" -> TvActions.run(TvActions.Action.POWER_ON)
        action == "POWER_OFF" -> TvActions.run(TvActions.Action.POWER_OFF)
        action.startsWith("ROUTINE:") -> Routines.byId(ctx, action.removePrefix("ROUTINE:"))?.let { Routines.run(it) } ?: "routine non trovata"
        action == "LIMIT_WARN" -> { if (Tv.ensureConnected()) Tv.toast("TVPilot: la TV si spegne tra 5 minuti"); "avviso inviato" }
        action == "LIMIT_OFF" -> {
            p(ctx).edit().remove("limite_fine").apply()
            if (Tv.ensureConnected()) { Tv.toast("TVPilot: tempo scaduto"); kotlinx.coroutines.delay(3000); Tv.powerOff() }
            "TV spenta (limite di tempo)"
        }
        else -> "azione sconosciuta"
    }
}

/** Scatta all'ora programmata; si riprogramma da sola anche dopo il riavvio del telefono o un aggiornamento dell'app */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Schedules.scheduleAll(app)
            return
        }
        val action = intent.getStringExtra("azione") ?: return
        val pending = goAsync()
        Tv.scope.launch {
            // gli allarmi sono trasmissioni in background: c'è tempo fino a circa un minuto
            val msg = withTimeoutOrNull(45_000) { runCatching { Schedules.execute(app, action) }.getOrElse { "errore ${it.message}" } } ?: "TV non risponde"
            Reports.log("Programma ${Schedules.actionLabel(app, action)}: $msg")
            if (!action.startsWith("LIMIT")) Schedules.scheduleAll(app)
            Reports.flush("operazione-programma")
            pending.finish()
        }
    }
}
