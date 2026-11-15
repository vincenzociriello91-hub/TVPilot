package it.vincenzo.tvpilot

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.tv.WebOsClient
import kotlinx.coroutines.launch

class TVPilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Tv.init(this)
        Reports.init(this)
        Reports.log("Avvio app")

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { Reports.saveCrash(e) }
            previous?.uncaughtException(t, e)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Reports.log("App in primo piano")
                // ricollega da solo alla TV usata l'ultima volta
                val host = Prefs.lastHost(this@TVPilotApp)
                val st = Tv.client.stato.value
                if (host != null && st != WebOsClient.State.CONNECTED && st != WebOsClient.State.CONNECTING) {
                    Tv.scope.launch { Tv.connect(host) }
                }
                Tv.scope.launch { Reports.sendQueue() }
            }

            override fun onStop(owner: LifecycleOwner) {
                // chiusura normale: rapporto completo dell'utilizzo
                Reports.log("App in background / chiusa")
                Tv.scope.launch {
                    Reports.flush("chiusura")
                    Reports.sendQueue()
                }
            }
        })
    }
}
