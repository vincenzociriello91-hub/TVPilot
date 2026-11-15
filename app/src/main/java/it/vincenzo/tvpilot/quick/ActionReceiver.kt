package it.vincenzo.tvpilot.quick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.TvActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Riceve i tocchi di widget e notifica ed esegue l'azione sulla TV (anche con l'app chiusa) */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "it.vincenzo.tvpilot.AZIONE"
        const val EXTRA = "azione"

        fun intent(ctx: Context, a: TvActions.Action): Intent =
            Intent(ctx, ActionReceiver::class.java).setAction("$ACTION.${a.name}").putExtra(EXTRA, a.name)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val a = runCatching { TvActions.Action.valueOf(intent.getStringExtra(EXTRA) ?: "") }.getOrNull() ?: return
        val pending = goAsync()
        val app = context.applicationContext
        Tv.scope.launch {
            val timeout = if (a == TvActions.Action.POWER_ON || a == TvActions.Action.POWER_TOGGLE) 25_000L else 9_000L
            val msg = withTimeoutOrNull(timeout) { runCatching { TvActions.run(a) }.getOrElse { "Errore: ${it.message}" } } ?: "TV non risponde"
            withContext(Dispatchers.Main) {
                if (a in setOf(TvActions.Action.POWER_ON, TvActions.Action.POWER_OFF, TvActions.Action.POWER_TOGGLE) || msg.startsWith("TV non") || msg.startsWith("Errore"))
                    Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
                ControlService.refresh(app, msg)
            }
            pending.finish()
        }
    }
}
