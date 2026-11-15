package it.vincenzo.tvpilot.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import it.vincenzo.tvpilot.MainActivity
import it.vincenzo.tvpilot.R
import it.vincenzo.tvpilot.tv.TvActions

/** Widget sulla schermata Home: accendi/spegni, volume, muto, apri l'app */
class TvWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    private fun views(ctx: Context): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_tv)
        fun bind(viewId: Int, a: TvActions.Action) {
            v.setOnClickPendingIntent(
                viewId,
                PendingIntent.getBroadcast(ctx, 100 + a.ordinal, ActionReceiver.intent(ctx, a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        bind(R.id.w_power, TvActions.Action.POWER_TOGGLE)
        bind(R.id.w_vol_down, TvActions.Action.VOL_DOWN)
        bind(R.id.w_mute, TvActions.Action.MUTE)
        bind(R.id.w_vol_up, TvActions.Action.VOL_UP)
        v.setOnClickPendingIntent(
            R.id.w_open,
            PendingIntent.getActivity(ctx, 99, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        return v
    }
}
