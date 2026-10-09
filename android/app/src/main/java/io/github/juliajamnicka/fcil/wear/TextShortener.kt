package io.github.juliajamnicka.fcil.wear

/** Shortens stop names and headsigns to fit a watch row (docs/SPEC.md 6). */
object TextShortener {
    private val abbreviations = listOf(
        "náměstí" to "nám.",
        "Náměstí" to "Nám.",
        "nádraží" to "nádr.",
        "sídliště" to "sídl.",
        "smyčka" to "sm.",
        "Vozovna" to "Voz.",
        "točna" to "toč.",
        "hřbitov" to "hřb.",
        "Ústřední" to "Ústř.",
        "železniční stanice" to "žel. st.",
    )

    fun shorten(text: String, max: Int): String {
        var s = text.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
        if (s.length <= max) return s
        for ((long, short) in abbreviations) {
            s = s.replace(long, short)
            if (s.length <= max) return s
        }
        return s.take(max - 1).trimEnd(' ', ',', '-') + "…"
    }
}
