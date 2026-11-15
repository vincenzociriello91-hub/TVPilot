package it.vincenzo.tvpilot.tv

import org.json.JSONObject

/** Interventi di pulizia pronti (stile CleanYourTV): ognuno passa da SettingsManager, quindi ha il suo backup */
object Cleanup {
    data class Action(
        val id: String,
        val title: String,
        val description: String,
        val category: String,
        val values: JSONObject,
        val risk: Risk = Risk.LOW,
        /** chiave da leggere per mostrare lo stato attuale */
        val statusKey: String? = null,
        val statusOk: String? = null,
    )

    val actions = listOf(
        Action(
            "pubblicita", "Via la pubblicità dalla Home",
            "Toglie banner promozionali nella Home, pubblicità personalizzata e pubblicità nel salvaschermo.",
            "general", JSONObject().put("homePromotion", "off").put("customizedAd", "off").put("screenSaverAd", "off"),
            statusKey = "homePromotion", statusOk = "off",
        ),
        Action(
            "tracciamento", "Niente raccolta dati di visione",
            "Disattiva Live Plus (riconoscimento di ciò che guardi) e i cookie pubblicitari.",
            "option", JSONObject().put("livePlus", "off"),
            statusKey = "livePlus", statusOk = "off",
        ),
        Action(
            "cookie", "Niente cookie pubblicitari e consigli personali",
            "Spegne il cookie pubblicitario e i consigli basati su quello che guardi.",
            "general", JSONObject().put("adCookie", "off")
                .put("personalRecommend", JSONObject().put("changedByUser", true).put("value", "off")),
            statusKey = "adCookie", statusOk = "off",
        ),
        Action(
            "home", "La Home non si apre da sola",
            "All'accensione la TV va direttamente all'ultimo ingresso invece di aprire la barra Home.",
            "general", JSONObject().put("homeAutoLaunch", "off"),
            statusKey = "homeAutoLaunch", statusOk = "off",
        ),
        Action(
            "suono", "Silenzia il suono di accensione",
            "Niente jingle LG quando accendi la TV.",
            "general", JSONObject().put("tvOnSound", "off"),
            statusKey = "tvOnSound", statusOk = "off",
        ),
        Action(
            "accensione", "Accensione dal telefono",
            "Attiva Quick Start+ e l'accensione via rete: così il pulsante Accendi dell'app funziona. Consuma qualche watt in più in standby.",
            "option", JSONObject().put("quickStartMode", "on"),
            statusKey = "quickStartMode", statusOk = "on",
        ),
        Action(
            "wol", "Accensione tramite rete (Wake-on-LAN)",
            "Seconda parte dell'accensione dal telefono: la TV ascolta il segnale di accensione sulla rete.",
            "network", JSONObject().put("wolwowlOnOff", "true"),
            statusKey = "wolwowlOnOff", statusOk = "true",
        ),
        Action(
            "eco", "Risparmio energetico: minimo",
            "Riduce un po' la retroilluminazione per consumare meno senza peggiorare molto l'immagine.",
            "picture", JSONObject().put("energySaving", "min"), Risk.LOW,
            statusKey = "energySaving", statusOk = "min",
        ),
    )
}
