package it.vincenzo.tvpilot.tv

/** Quanto è rischioso cambiare un'impostazione nascosta (logica pura, testata in RisksTest) */
enum class Risk(val label: String) { LOW("Sicura"), MEDIUM("Attenzione"), HIGH("Pericolosa") }

object Risks {
    /** Categorie da hotel/negozio/blocchi: possono bloccare menu o telecomando, a volte serve il reset di fabbrica */
    private val HIGH_CATEGORIES = setOf("commercial", "commercialTv", "hotelMode", "lock", "support", "twinTv", "")
    private val MEDIUM_CATEGORIES = setOf("option", "other", "channel", "time", "timemachine", "voiceframework", "caption", "network", "musicCatch")

    /** Parole che rendono pericolosa una chiave in qualsiasi categoria */
    private val HIGH_WORDS = listOf(
        "storemode", "demo", "country", "eula", "ipcontrol", "factory", "password", "pin", "lock", "hotel",
        "installation", "powerontime", "standby", "usbclone", "serial", "region", "locale", "smartservice",
    )
    private val SAFE_PUBLIC = mapOf(
        "network" to setOf("deviceName", "wolwowlOnOff", "bleAdvertisingOnOff"),
        "option" to setOf("livePlus", "quickStartMode"),
        "other" to setOf("simplinkEnable"),
    )

    fun of(category: String, key: String): Risk {
        if (SAFE_PUBLIC[category]?.contains(key) == true) return Risk.LOW
        val k = key.lowercase()
        if (category in HIGH_CATEGORIES || HIGH_WORDS.any { k.contains(it) }) return Risk.HIGH
        if (category in MEDIUM_CATEGORIES) return Risk.MEDIUM
        return Risk.LOW
    }

    fun explain(category: String, key: String): String = when (of(category, key)) {
        Risk.HIGH -> "Può bloccare menu o telecomando, cambiare paese e servizi o mettere la TV in modalità negozio/hotel. " +
            "Il backup permette di tornare indietro, ma in rari casi serve il ripristino di fabbrica."
        Risk.MEDIUM -> "Cambia il comportamento della TV (canali, orari, servizi). Di solito si torna indietro senza problemi con il backup."
        Risk.LOW -> "Impostazione di immagine, audio o interfaccia: nessun rischio, si annulla dal backup."
    }

    fun categoryName(c: String): String = when (c) {
        "" -> "Sistema (EULA)"
        "3d" -> "3D"
        "aspectRatio" -> "Formato immagine"
        "caption" -> "Sottotitoli"
        "channel" -> "Canali"
        "commercial" -> "Modalità commerciale"
        "commercialTv" -> "TV commerciale"
        "general" -> "Generale (Home, pubblicità, salvaschermo)"
        "hotelMode" -> "Modalità hotel"
        "lock" -> "Blocchi e parental control"
        "musicCatch" -> "Music catch"
        "network" -> "Rete"
        "option" -> "Opzioni (Live Plus, Quick Start, negozio)"
        "other" -> "Altro (SIMPLINK, eco)"
        "picture" -> "Immagine"
        "sound" -> "Audio"
        "support" -> "Supporto"
        "time" -> "Ora e timer"
        "timemachine" -> "Registrazione"
        "timer" -> "Timer"
        "twinTv" -> "Twin TV"
        "voiceframework" -> "Comandi vocali"
        else -> c
    }
}
