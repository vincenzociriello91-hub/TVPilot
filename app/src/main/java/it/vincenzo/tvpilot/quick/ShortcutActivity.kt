package it.vincenzo.tvpilot.quick

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.Toast
import it.vincenzo.tvpilot.R
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Routines
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.TvActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scorciatoie tenendo premuta l'icona (Spegni, Accendi, Muto, routine): esegue e si chiude subito.
 * Si possono usare anche dalle Routine di Google e da app come Tasker.
 */
class ShortcutActivity : Activity() {
    companion object {
        const val EXTRA_ACTION = "azione"
        const val EXTRA_ROUTINE = "routine"

        /** Scorciatoie dinamiche per le prime routine */
        fun updateRoutineShortcuts(ctx: Context) {
            val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
            val list = Routines.all(ctx).take(2).map { r ->
                ShortcutInfo.Builder(ctx, "routine_${r.id}")
                    .setShortLabel(r.name.take(10))
                    .setLongLabel("Routine ${r.name}")
                    .setIcon(Icon.createWithResource(ctx, R.mipmap.ic_launcher))
                    .setIntent(Intent(ctx, ShortcutActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_ROUTINE, r.id))
                    .build()
            }
            runCatching { sm.dynamicShortcuts = list }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra(EXTRA_ACTION)
        val routine = intent.getStringExtra(EXTRA_ROUTINE)
        val app = applicationContext
        Reports.log("Scorciatoia: ${action ?: "routine $routine"}")
        Tv.scope.launch {
            val msg = runCatching {
                when {
                    routine != null -> Routines.byId(app, routine)?.let { Routines.run(it) } ?: "Routine non trovata"
                    action != null -> TvActions.run(TvActions.Action.valueOf(action))
                    else -> "Scorciatoia vuota"
                }
            }.getOrElse { "Errore: ${it.message}" }
            withContext(Dispatchers.Main) { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }
}
