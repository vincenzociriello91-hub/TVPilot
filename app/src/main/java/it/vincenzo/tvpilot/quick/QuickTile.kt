package it.vincenzo.tvpilot.quick

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.TvActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Riquadro nelle impostazioni rapide: un tocco accende o spegne la TV */
class QuickTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        show(if (Tv.client.connected) "Accesa" else "Tocca per accendere/spegnere", Tv.client.connected)
    }

    override fun onClick() {
        super.onClick()
        show("Attendi…", true)
        Tv.scope.launch {
            val msg = runCatching { TvActions.run(TvActions.Action.POWER_TOGGLE) }.getOrDefault("TV non raggiungibile")
            withContext(Dispatchers.Main) { show(msg, msg == "TV accesa") }
        }
    }

    private fun show(subtitle: String, active: Boolean) {
        val t = qsTile ?: return
        t.label = "TV"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = subtitle
        t.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
