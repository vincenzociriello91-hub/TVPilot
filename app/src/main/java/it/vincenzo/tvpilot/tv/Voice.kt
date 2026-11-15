package it.vincenzo.tvpilot.tv

import it.vincenzo.tvpilot.core.Reports
import java.text.Normalizer

/** Comandi vocali in italiano: "volume 20", "alza il volume", "apri YouTube", "canale 5", "spegni tra 30 minuti"… */
object Voice {
    sealed class Command(val label: String) {
        data class VolumeSet(val value: Int) : Command("volume a $value")
        data class VolumeUp(val steps: Int) : Command("alzo il volume di $steps")
        data class VolumeDown(val steps: Int) : Command("abbasso il volume di $steps")
        object Mute : Command("muto")
        object Unmute : Command("tolgo il muto")
        object PowerOff : Command("spengo la TV")
        object PowerOn : Command("accendo la TV")
        object ScreenOff : Command("spengo lo schermo")
        data class Sleep(val minutes: Int) : Command("spegnimento tra $minutes minuti")
        object SleepOff : Command("tolgo il timer di spegnimento")
        data class Channel(val number: String) : Command("canale $number")
        object ChannelUp : Command("canale successivo")
        object ChannelDown : Command("canale precedente")
        data class Input(val hdmi: Int) : Command("HDMI $hdmi")
        data class OpenApp(val name: String) : Command("apro $name")
        object Play : Command("play")
        object Pause : Command("pausa")
        object Home : Command("Home")
        object Back : Command("indietro")
        data class Unknown(val text: String) : Command("non ho capito \"$text\"")
    }

    private val WORDS = mapOf(
        "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sette" to 7,
        "otto" to 8, "nove" to 9, "dieci" to 10, "undici" to 11, "dodici" to 12, "quindici" to 15, "venti" to 20,
        "venticinque" to 25, "trenta" to 30, "quaranta" to 40, "quarantacinque" to 45, "cinquanta" to 50, "sessanta" to 60,
        "settanta" to 70, "ottanta" to 80, "novanta" to 90, "cento" to 100,
    )

    fun normalize(text: String): String {
        var t = Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        t = t.replace("un'ora", "60 minuti").replace("un ora", "60 minuti")
            .replace("mezz'ora", "30 minuti").replace("mezzora", "30 minuti")
        WORDS.entries.sortedByDescending { it.key.length }.forEach { (w, n) -> t = t.replace(Regex("\\b${Regex.escape(w)}\\b"), n.toString()) }
        return t.replace(Regex("[.,!?]"), "").replace(Regex("\\s+"), " ").trim()
    }

    fun parse(raw: String): Command {
        val t = normalize(raw)
        fun num(r: Regex) = r.find(t)?.groupValues?.getOrNull(1)?.toIntOrNull()

        Regex("(?:spegni|spegnimento|timer)\\D*?(\\d+)\\s*(minut|or)").find(t)?.let {
            val n = it.groupValues[1].toInt()
            return Command.Sleep(if (it.groupValues[2].startsWith("or")) n * 60 else n)
        }
        if (Regex("(togli|annulla|cancella|disattiva).*timer").containsMatchIn(t)) return Command.SleepOff
        if (Regex("spegni (lo )?schermo|schermo spento").containsMatchIn(t)) return Command.ScreenOff
        if (Regex("^(spegni|spegnere)").containsMatchIn(t)) return Command.PowerOff
        if (Regex("^(accendi|accendere)").containsMatchIn(t)) return Command.PowerOn
        num(Regex("volume (?:a |al )?(\\d+)"))?.let { return Command.VolumeSet(it.coerceIn(0, 100)) }
        if (Regex("(togli|leva) (il )?muto|riattiva (l')?audio|rimetti (l')?audio").containsMatchIn(t)) return Command.Unmute
        if (Regex("\\bmuto\\b|silenzio|togli (l')?audio|zitto").containsMatchIn(t)) return Command.Mute
        if (Regex("alza|piu forte|aumenta").containsMatchIn(t)) return Command.VolumeUp(num(Regex("di (\\d+)")) ?: 3)
        if (Regex("abbassa|piu piano|diminuisci").containsMatchIn(t)) return Command.VolumeDown(num(Regex("di (\\d+)")) ?: 3)
        if (Regex("canale (su|successivo|dopo|avanti)").containsMatchIn(t)) return Command.ChannelUp
        if (Regex("canale (giu|precedente|prima|indietro)").containsMatchIn(t)) return Command.ChannelDown
        Regex("canale (\\d+)").find(t)?.let { return Command.Channel(it.groupValues[1]) }
        num(Regex("hdmi ?(\\d)"))?.let { return Command.Input(it) }
        if (Regex("^((metti in )?pausa|ferma|stop)").containsMatchIn(t)) return Command.Pause
        if (Regex("^(play|riprendi|continua|riproduci)$").containsMatchIn(t)) return Command.Play
        if (Regex("^(home|menu principale|schermata principale)").containsMatchIn(t)) return Command.Home
        if (Regex("^(indietro|torna indietro)").containsMatchIn(t)) return Command.Back
        Regex("^(?:apri|metti|avvia|lancia|vai su|guarda|fammi vedere) (?:l'app |l'applicazione |app )?(.+)").find(t)?.let {
            return Command.OpenApp(it.groupValues[1].trim())
        }
        return Command.Unknown(raw)
    }

    private val SYNONYMS = mapOf(
        "prime" to "prime video", "amazon" to "prime video", "disney" to "disney+", "mediaset" to "mediaset infinity",
        "internet" to "browser", "web" to "browser", "rai" to "raiplay", "tv" to "live tv",
        "digitale terrestre" to "live tv", "televisione" to "live tv", "canali" to "live tv",
    )

    /** Sceglie l'app più simile al nome detto */
    fun bestApp(name: String, apps: List<Tv.AppItem>): Tv.AppItem? {
        val n = normalize(SYNONYMS[normalize(name)] ?: name)
        fun score(a: Tv.AppItem): Int {
            val title = normalize(a.title)
            return when {
                title == n -> 100
                title.startsWith(n) -> 80
                title.contains(n) -> 60
                n.contains(title) && title.length >= 3 -> 50
                else -> n.split(" ").count { it.length >= 3 && title.contains(it) } * 20
            }
        }
        return apps.maxByOrNull(::score)?.takeIf { score(it) >= 20 }
    }

    /** Esegue il comando e restituisce cosa è stato fatto */
    suspend fun execute(c: Command): String {
        Reports.log("Voce: ${c.label}")
        if (c is Command.Unknown) return c.label
        if (c is Command.PowerOn) return TvActions.run(TvActions.Action.POWER_ON)
        if (!Tv.ensureConnected()) return "TV non raggiungibile"
        when (c) {
            is Command.VolumeSet -> Tv.setVolume(c.value)
            is Command.VolumeUp -> Tv.setVolume(Tv.volume().first + c.steps)
            is Command.VolumeDown -> Tv.setVolume(Tv.volume().first - c.steps)
            Command.Mute -> Tv.setMute(true)
            Command.Unmute -> Tv.setMute(false)
            Command.PowerOff -> Tv.powerOff()
            Command.ScreenOff -> Tv.screenOff()
            is Command.Sleep -> {
                val m = TvActions.nearestSleep(c.minutes)
                TvActions.sleepTimer(m)
                return "La TV si spegne tra $m minuti"
            }
            Command.SleepOff -> TvActions.sleepTimer(0)
            is Command.Channel -> Tv.openChannelNumber(c.number)
            Command.ChannelUp -> Tv.channelUp()
            Command.ChannelDown -> Tv.channelDown()
            is Command.Input -> Tv.switchInput("HDMI_${c.hdmi}")
            is Command.OpenApp -> {
                val app = bestApp(c.name, Tv.apps()) ?: return "Non trovo l'app \"${c.name}\" sulla TV"
                Tv.launch(app.id)
                return "Apro ${app.title}"
            }
            Command.Play -> Tv.play()
            Command.Pause -> Tv.pause()
            Command.Home -> Tv.client.button("HOME")
            Command.Back -> Tv.client.button("BACK")
            else -> Unit
        }
        return c.label.replaceFirstChar { it.uppercase() }
    }
}
