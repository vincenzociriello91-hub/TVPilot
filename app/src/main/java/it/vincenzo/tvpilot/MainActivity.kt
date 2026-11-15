package it.vincenzo.tvpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import it.vincenzo.tvpilot.core.Prefs
import it.vincenzo.tvpilot.core.Reports
import it.vincenzo.tvpilot.tv.Tv
import it.vincenzo.tvpilot.ui.tvAction
import it.vincenzo.tvpilot.ui.AppsScreen
import it.vincenzo.tvpilot.ui.CleanScreen
import it.vincenzo.tvpilot.ui.DevScreen
import it.vincenzo.tvpilot.ui.InfoScreen
import it.vincenzo.tvpilot.ui.RemoteScreen
import it.vincenzo.tvpilot.ui.TVPilotTheme
import it.vincenzo.tvpilot.ui.UiBus

class MainActivity : ComponentActivity() {
    /** Con l'app aperta i tasti volume del telefono comandano la TV (se attivo nelle impostazioni) */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = event.keyCode
        if ((code == android.view.KeyEvent.KEYCODE_VOLUME_UP || code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) &&
            Prefs.volumeKeys(this) && Tv.client.connected
        ) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                val up = code == android.view.KeyEvent.KEYCODE_VOLUME_UP
                tvAction("Volume") {
                    if (up) Tv.volumeUp() else Tv.volumeDown()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TVPilotTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) { UiBus.messages.collect { snackbar.showSnackbar(it) } }
                val tabs = listOf(
                    "Telecomando" to Icons.Filled.SettingsRemote,
                    "App" to Icons.Filled.Apps,
                    "Pulizia" to Icons.Filled.CleaningServices,
                    "Sviluppatore" to Icons.Filled.DeveloperMode,
                    "Info" to Icons.Filled.Info,
                )
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i; Reports.log("Scheda $label") },
                                    icon = { Icon(icon, null) },
                                    label = { Text(label, maxLines = 1) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            0 -> RemoteScreen()
                            1 -> AppsScreen()
                            2 -> CleanScreen()
                            3 -> DevScreen()
                            else -> InfoScreen()
                        }
                    }
                }
            }
        }
    }
}
