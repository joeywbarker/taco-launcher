package com.pockettaco.salsa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Reads a game's file name and splits it into parts: the real title, regions, year,
 * version, tags (Proto, Beta, Hack...), publisher and disc number.
 *
 * File names are messy. The extra details can be in (round), [square] or {curly}
 * brackets, or loose at the end of the name with no brackets at all, and in any order.
 * So every bracketed group is checked, and loose words are peeled off the end only while
 * they look like details (never from the middle, so "1942" or "Super Mario World" stay).
 */
object GameNames {

    data class Parts(
        val title: String,
        val regions: List<String> = emptyList(),
        val year: String? = null,
        val version: String? = null,
        val tags: List<String> = emptyList(),
        val publisher: String? = null,
        val disc: String? = null,
    )

    /** How names are shown. Set from Settings at startup and when changed there. */
    var showFullName by androidx.compose.runtime.mutableStateOf(false)
    var showRegion by androidx.compose.runtime.mutableStateOf(false)
    var showYear by androidx.compose.runtime.mutableStateOf(false)
    var showPublisher by androidx.compose.runtime.mutableStateOf(false)
    var showExtension by androidx.compose.runtime.mutableStateOf(false)
    var showTags by androidx.compose.runtime.mutableStateOf(false)

    /** The five details; "nameFull" (entire file name) means all of them are on. */
    val DETAILS = listOf("nameRegion", "nameYear", "namePublisher", "nameExtension", "nameTags")

    /** Systems with their own name settings (switches that are on); others follow the main ones. */
    val systemFlags = androidx.compose.runtime.mutableStateMapOf<String, Set<String>>()

    fun load(settings: Settings) {
        showFullName = settings.nameFlag("nameFull")
        showRegion = settings.nameFlag("nameRegion")
        showYear = settings.nameFlag("nameYear")
        showPublisher = settings.nameFlag("namePublisher")
        showExtension = settings.nameFlag("nameExtension")
        showTags = settings.nameFlag("nameTags")
        systemFlags.clear()
        SYSTEMS.forEach { sys -> settings.systemNameFlags(sys.id)?.let { systemFlags[sys.id] = it } }
    }

    /** The main switches (from Settings) as a set of the ones that are on. */
    fun mainFlags(): Set<String> = buildSet {
        if (showFullName) add("nameFull")
        if (showRegion) add("nameRegion")
        if (showYear) add("nameYear")
        if (showPublisher) add("namePublisher")
        if (showExtension) add("nameExtension")
        if (showTags) add("nameTags")
    }

    fun flagsFor(system: String?): Set<String> = system?.let { systemFlags[it] } ?: mainFlags()

    /**
     * Flips one switch, keeping them linked: "entire file name" on turns every detail on;
     * any detail off turns it off; all details on turns it on.
     */
    fun toggled(flags: Set<String>, key: String, on: Boolean): Set<String> {
        val out = flags.toMutableSet()
        if (on) out += key else out -= key
        if (key == "nameFull") { if (on) out += DETAILS }
        else if (!on) out -= "nameFull"
        else if (DETAILS.all { it in out }) out += "nameFull"
        return out
    }

    /** The name to show for a game, following the Settings choices (or its system's own). */
    fun label(fileName: String, baseName: String, system: String? = null): String {
        val f = flagsFor(system)
        if ("nameFull" in f) return fileName
        val p = parse(baseName)
        val sb = StringBuilder(p.title)
        if (p.disc != null) sb.append(" (").append(p.disc).append(')')
        if ("nameRegion" in f && p.regions.isNotEmpty()) sb.append(" (").append(p.regions.joinToString("/")).append(')')
        if ("nameYear" in f && p.year != null) sb.append(" (").append(p.year).append(')')
        if ("namePublisher" in f && p.publisher != null) sb.append(" (").append(p.publisher).append(')')
        if ("nameTags" in f) {
            val extra = listOfNotNull(p.version) + p.tags
            if (extra.isNotEmpty()) sb.append(" [").append(extra.joinToString(", ")).append(']')
        }
        if ("nameExtension" in f && '.' in fileName) sb.append('.').append(fileName.substringAfterLast('.'))
        return sb.toString()
    }

    /** Just the clean title, used for sorting so "The" and brackets don't change the order. */
    fun sortKey(baseName: String): String = parse(baseName).title.lowercase().removePrefix("the ")

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Parts>()

    fun parse(baseName: String): Parts = cache.getOrPut(baseName) {
        // A name we can't read must never stop the app from opening: show it as it is.
        runCatching { doParse(baseName) }.getOrElse { Parts(baseName.trim()) }
    }

    private fun doParse(raw: String): Parts {
        var s = raw
        // "Title # NDS" marks the system on .zip files; it's not part of the name.
        s = s.replace(Regex("""\s*#\s*[A-Za-z0-9 ]+$"""), "")
        // Names_with_underscores or.dots.instead.of.spaces
        if (' ' !in s) {
            s = s.replace('_', ' ')
            if (' ' !in s && s.count { it == '.' } >= 2) s = s.replace(Regex("""\.(?![0-9])"""), " ")
        }

        val regions = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var year: String? = null
        var version: String? = null
        var publisher: String? = null
        var disc: String? = null

        fun addRegion(r: String) { if (r !in regions) regions += r }
        fun addTag(t: String) { if (t !in tags) tags += t }

        /** Try to file one word or phrase. Returns false if it doesn't look like a detail. */
        fun classify(textIn: String, bracketed: Boolean): Boolean {
            val text = textIn.trim().trim('-', ',', ';').trim()
            if (text.isEmpty()) return true
            val lower = text.lowercase()
            regionOf(text, bracketed)?.let { list -> list.forEach(::addRegion); return true }
            if (Regex("""(19[7-9]\d|20[0-4]\d)(-\d\d(-\d\d)?)?""").matches(text) ||
                (bracketed && Regex("""(19|20)\d[x?]""", RegexOption.IGNORE_CASE).matches(text))) {
                year = text.take(4); return true
            }
            Regex("""v\.?\s?(\d+(\.\d+)*[a-z]?)""", RegexOption.IGNORE_CASE).matchEntire(text)?.let {
                version = "v" + it.groupValues[1]; return true
            }
            Regex("""rev\.?\s*([0-9a-z.]+)""", RegexOption.IGNORE_CASE).matchEntire(text)?.let {
                version = "Rev " + it.groupValues[1].uppercase(); return true
            }
            Regex("""(disc|disk|cd|side)\s*([0-9a-z]+)(\s*of\s*\d+)?""", RegexOption.IGNORE_CASE).matchEntire(text)?.let {
                disc = it.groupValues[1].replaceFirstChar { c -> c.uppercase() } + " " + it.groupValues[2].uppercase()
                return true
            }
            STATUS[lower]?.let { addTag(it); return true }
            if (bracketed) {
                goodCode(text)?.let { if (it.isNotEmpty()) addTag(it); return true }
                if (isLanguages(text)) return true
                if (Regex("""(beta|proto|prototype|demo|sample)\s*\d*""", RegexOption.IGNORE_CASE).matches(text)) {
                    addTag(STATUS[lower.filter { it.isLetter() }] ?: text); return true
                }
            }
            if (lower in PUBLISHERS) { publisher = PUBLISHERS[lower]; return true }
            return false
        }

        // 1) Bracketed groups, anywhere in the name.
        // Every bracket is escaped: Android's regex engine rejects a bare { or } (unlike desktop
        // Java), and that crashed the app as soon as it read the game list.
        val group = Regex("""\(([^()]*)\)|\[([^\[\]]*)\]|\{([^\{\}]*)\}""")
        for (m in group.findAll(s)) {
            val inside = m.value.drop(1).dropLast(1)
            if (classify(inside, bracketed = true)) continue
            // "USA, Europe", "Proto, 1989": try each piece.
            val pieces = inside.split(',', ';', '/').map { it.trim() }.filter { it.isNotEmpty() }
            val unfiled = pieces.filterNot { classify(it, bracketed = true) }
            // Anything left we don't recognise is most likely the publisher or a note.
            if (unfiled.isNotEmpty()) {
                val note = unfiled.joinToString(", ")
                if (publisher == null && unfiled.size == 1 && note.length <= 30) publisher = note else addTag(note)
            }
        }
        var title = s.replace(group, " ")

        // 2) Loose details at the end, with no brackets: "Tetris NINTENDO 1989 USA V.1.64 PROTO".
        val words = title.trim().split(Regex("""\s+""")).toMutableList()
        val peeled = mutableListOf<String>()
        while (words.size > 1) {
            val last = words.last()
            val two = if (words.size > 2) words[words.size - 2] + " " + last else null
            if (two != null && Regex("""(rev|disc|disk|side|cd)\s+\S+""", RegexOption.IGNORE_CASE).matches(two) &&
                classifyLoose(two, ::classify)) { peeled += two; words.removeAt(words.lastIndex); words.removeAt(words.lastIndex); continue }
            if (classifyLoose(last, ::classify)) { peeled += last; words.removeAt(words.lastIndex); continue }
            break
        }
        // A year on its own at the end is probably part of the name ("FIFA 2002"); only drop it
        // when other details were peeled off with it.
        if (peeled.size == 1 && Regex("""(19|20)\d\d""").matches(peeled[0])) {
            words += peeled[0]; year = null
        }
        title = words.joinToString(" ")

        // 3) Tidy up: spacing, dangling dashes, and "Legend of Zelda, The" -> "The Legend of Zelda".
        title = title.replace(Regex("""\s+"""), " ").trim().trim('-', ',', '_', '.').trim()
        Regex("""^(.*?),\s*(The|A|An)(\s*-\s*.*|:.*)?$""", RegexOption.IGNORE_CASE).matchEntire(title)?.let {
            title = it.groupValues[2] + " " + it.groupValues[1] + it.groupValues[3]
        }
        if (title.isEmpty()) title = raw.trim()
        return Parts(title, regions, year, version, tags, publisher, disc)
    }

    /** Loose (no brackets) words only count when they are clearly details. */
    private fun classifyLoose(word: String, classify: (String, Boolean) -> Boolean): Boolean {
        val w = word.trim().trim('-', ',')
        if (w.isEmpty()) return true
        val lower = w.lowercase()
        val looksLikeDetail = lower in LOOSE_REGIONS || lower in STATUS || lower in PUBLISHERS ||
            Regex("""(19[7-9]\d|20[0-4]\d)""").matches(w) ||
            Regex("""v\.?\d+(\.\d+)*[a-z]?""", RegexOption.IGNORE_CASE).matches(w) ||
            Regex("""(rev|disc|disk|side|cd)\s+\S+""", RegexOption.IGNORE_CASE).matches(w)
        return looksLikeDetail && classify(w, false)
    }

    private fun regionOf(text: String, bracketed: Boolean): List<String>? {
        val lower = text.lowercase()
        REGIONS[lower]?.let { return if (!bracketed && lower !in LOOSE_REGIONS) null else listOf(it) }
        // GoodTools style: (U), (JU), (UE), (JUE)
        if (bracketed && text.length in 1..4 && text.all { it.isUpperCase() && it in LETTER_REGIONS }) {
            return text.map { LETTER_REGIONS.getValue(it) }
        }
        return null
    }

    /** GoodTools codes like [!], [a1], [b2], [h1C], [T+Eng]. Returns the tag to show ("" = none). */
    private fun goodCode(text: String): String? = when {
        text == "!" || text == "!p" -> ""
        Regex("""a\d*""").matches(text) -> "Alt"
        Regex("""b\d*""").matches(text) -> "Bad dump"
        Regex("""h\d*\w*""").matches(text) && text.length <= 5 -> "Hack"
        Regex("""t\d*""").matches(text) -> "Trainer"
        Regex("""f\d*""").matches(text) -> "Fixed"
        Regex("""o\d*""").matches(text) -> "Overdump"
        Regex("""p\d*""").matches(text) -> "Pirate"
        Regex("""T[+-]\s*\w+.*""").matches(text) -> "Translated"
        else -> null
    }

    private fun isLanguages(text: String): Boolean {
        val parts = text.split(',', '+').map { it.trim().lowercase() }
        return parts.isNotEmpty() && parts.all { it in LANGUAGES }
    }

    private val LETTER_REGIONS = mapOf(
        'U' to "US", 'E' to "EU", 'J' to "JP", 'W' to "World", 'K' to "KR", 'C' to "CN",
        'B' to "BR", 'A' to "AU", 'G' to "DE", 'F' to "FR", 'S' to "ES", 'I' to "IT",
    )

    private val REGIONS = mapOf(
        "usa" to "US", "us" to "US", "u" to "US", "america" to "US", "ntsc-u" to "US",
        "europe" to "EU", "eur" to "EU", "eu" to "EU", "e" to "EU", "pal" to "EU",
        "japan" to "JP", "jpn" to "JP", "jp" to "JP", "j" to "JP", "ntsc-j" to "JP",
        "world" to "World", "w" to "World",
        "germany" to "DE", "france" to "FR", "spain" to "ES", "italy" to "IT",
        "netherlands" to "NL", "sweden" to "SE", "korea" to "KR", "china" to "CN",
        "brazil" to "BR", "australia" to "AU", "asia" to "Asia", "canada" to "CA",
        "uk" to "UK", "united kingdom" to "UK", "taiwan" to "TW", "hong kong" to "HK",
        "russia" to "RU", "scandinavia" to "Scand", "denmark" to "DK", "norway" to "NO",
        "finland" to "FI", "portugal" to "PT", "greece" to "GR", "poland" to "PL",
    )

    /** Region words safe to peel off the end without brackets ("World" isn't: Super Mario World). */
    private val LOOSE_REGIONS = setOf("usa", "us", "europe", "eur", "japan", "jpn", "ntsc-u", "ntsc-j", "pal")

    private val STATUS = mapOf(
        "proto" to "Proto", "prototype" to "Proto", "beta" to "Beta", "demo" to "Demo",
        "sample" to "Sample", "hack" to "Hack", "unl" to "Unlicensed", "unlicensed" to "Unlicensed",
        "pirate" to "Pirate", "kiosk" to "Kiosk", "alt" to "Alt", "aftermarket" to "Aftermarket",
        "homebrew" to "Homebrew", "translated" to "Translated", "debug" to "Debug", "promo" to "Promo",
        "virtual console" to "Virtual Console", "sgb enhanced" to "SGB Enhanced",
        "gb compatible" to "GB Compatible", "rumble version" to "Rumble", "competition cart" to "Competition",
        "beta1" to "Beta", "beta2" to "Beta", "proto1" to "Proto", "proto2" to "Proto",
    )

    private val PUBLISHERS = listOf(
        "Nintendo", "Capcom", "Konami", "Sega", "Square", "Enix", "SquareSoft", "Square Enix", "Hudson",
        "Namco", "Bandai", "Taito", "THQ", "Acclaim", "Ubisoft", "Activision", "Atlus", "Banpresto",
        "Natsume", "Jaleco", "Irem", "Tecmo", "Koei", "Sunsoft", "SNK", "Midway", "Rare", "HAL",
        "Electronic Arts", "EA", "Eidos", "Infogrames", "Atari", "Codemasters", "Ocean", "Titus",
        "Hudson Soft", "Game Freak", "Intelligent Systems", "Camelot", "Treasure", "Technos",
        "Data East", "Vic Tokai", "Kemco", "Imagineer", "Takara", "Tomy", "Epoch", "Seta", "Asmik",
    ).associateBy { it.lowercase() }

    private val LANGUAGES = setOf(
        "en", "fr", "de", "es", "it", "nl", "sv", "no", "da", "fi", "pt", "ja", "zh", "ko", "ru", "pl",
        "ca", "el", "hu", "cs", "tr", "ar", "he",
    )
}
