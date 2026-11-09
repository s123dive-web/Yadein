package com.yaadein.domain.polish

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.Mood
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

data class PolishResult(
    val title: String,
    val description: String,
    val date: MemoryDate,
    val coverStyle: String,
    val place: String,
    val mood: Mood?,
    /** True when a date was found in the text (otherwise [date] is today). */
    val dateDetected: Boolean,
    val category: String,
)

/**
 * Rule-based, deterministic, 100% offline "Polish": turns one line such as "goa trip 2024" or
 * "diwali ghar pe 2019" (Hinglish welcome) into a titled, dated, illustrated memory card.
 */
object PolishEngine {

    private data class Category(val key: String, val mood: Mood?, val keywords: List<String>, val descriptions: List<String>, val titleSuffix: String? = null)

    private val categories = listOf(
        Category("beach", Mood.JOYFUL, listOf("beach", "goa", "sea", "samundar", "alibaug", "kerala", "andaman", "coast", "shack", "sunburn", "waves", "ocean", "island"),
            listOf("Sun, sand and salty hair. %s is the kind of day that keeps the whole year warm.", "Waves, long lunches and nobody checking the time. %s, kept here forever.")),
        Category("mountain", Mood.CALM, listOf("trek", "mountain", "hill", "hills", "ghat", "pahad", "lonavala", "mahabaleshwar", "himalaya", "camp", "camping", "valley", "fort", "sinhagad", "rajmachi", "peak"),
            listOf("Thin air, sore legs and a view that made everyone go quiet. %s, step by step.", "Up early, up high, and every ache worth it at the top. %s stays with you.")),
        Category("travel", Mood.JOYFUL, listOf("trip", "travel", "tour", "vacation", "holiday", "safar", "yatra", "picnic", "outing", "road", "roadtrip", "journey", "weekend", "getaway", "visit", "ghoomna"),
            listOf("New roads, old jokes and too many snacks. %s was a journey worth every kilometre.", "Bags packed, playlist ready, no plan that survived the first hour. %s, happily.")),
        Category("wedding", Mood.CELEBRATION, listOf("wedding", "shaadi", "shadi", "lagna", "lagn", "sangeet", "haldi", "mehndi", "mehendi", "engagement", "sagai", "reception", "baraat", "marriage", "nikah", "bride", "groom", "vivah"),
            listOf("Flowers, music and happy tears. %s — the day two families became one big one.", "Three days of colour and laughter in one memory. %s, with everyone who matters.")),
        Category("birthday", Mood.CELEBRATION, listOf("birthday", "bday", "janmdin", "janamdin", "vadhdivas", "cake", "turned", "turns", "candles"),
            listOf("Cake, candles and a wish nobody said out loud. %s, celebrated the right way.", "Another year, another round of singing badly on purpose. %s is one to keep.")),
        Category("diwali", Mood.CELEBRATION, listOf("diwali", "deepavali", "dipavali", "diya", "diyas", "lakshmi", "faral", "phuljhadi", "rangoli", "laxmi", "padwa", "bhaubeej", "bhai dooj"),
            listOf("Diyas on every step, faral on every plate and the whole house glowing. %s, bright as ever.", "Lights, sweets and new clothes that still smelled of the shop. %s at home.")),
        Category("holi", Mood.FUNNY, listOf("holi", "rang", "colours", "colors", "gulal", "pichkari", "dhuleti", "rangpanchami"),
            listOf("Nobody recognised anybody by noon. %s, in every colour we had.", "Colour in our hair for a week and no regrets. %s, loud and happy.")),
        Category("festival", Mood.CELEBRATION, listOf("ganpati", "ganesh", "ganeshotsav", "navratri", "dussehra", "dasara", "durga", "puja", "pooja", "eid", "christmas", "xmas", "pongal", "onam", "gudi", "padwa", "sankranti", "makar", "rakhi", "raksha", "janmashtami", "dahi", "handi", "festival", "tyohar", "sanmelan", "garba", "dandiya", "aarti", "mela"),
            listOf("Drums, sweets and the whole family in new clothes. %s felt like it always did.", "The streets were loud and the house was louder. %s, celebrated together.")),
        Category("school", Mood.NOSTALGIC, listOf("school", "shala", "class", "exam", "exams", "teacher", "10th", "12th", "ssc", "hsc", "board", "result", "results", "concert", "annual day", "sports day", "tuition", "uniform"),
            listOf("Ink-stained fingers and a bell that ruled the day. %s, from the school years.", "Friends on the last bench and a teacher who saw everything. %s is still clear.")),
        Category("college", Mood.NOSTALGIC, listOf("college", "campus", "hostel", "fest", "farewell", "graduation", "graduated", "convocation", "degree", "semester", "placement", "canteen", "university", "engineering", "mba"),
            listOf("Borrowed notes, canteen chai and plans for the whole world. %s, from the college days.", "The years that felt like they would never end, and then did. %s, remembered.")),
        Category("home", Mood.CALM, listOf("home", "ghar", "house", "terrace", "balcony", "sunday", "evening", "morning", "garden", "shifting", "moved", "new house", "gruhapravesh", "griha", "vastu", "family"),
            listOf("Nothing big happened and that was the point. %s, at home, together.", "The sounds of the house doing ordinary things. %s, exactly as it should be.")),
        Category("food", Mood.JOYFUL, listOf("food", "dinner", "lunch", "breakfast", "chai", "coffee", "biryani", "restaurant", "cafe", "dhaba", "modak", "puran poli", "thali", "pav bhaji", "vada pav", "misal", "recipe", "cooking", "baked", "bake", "cake", "khana", "jevan", "sweets", "mithai"),
            listOf("Too much on the plate and still room for one more bite. %s, made with love.", "Recipes nobody wrote down, served on plates nobody matched. %s, delicious.")),
        Category("baby", Mood.PROUD, listOf("baby", "born", "newborn", "hospital", "first word", "first step", "first steps", "naming", "barsa", "naamkaran", "pregnant", "delivery", "toddler", "walked", "crawled"),
            listOf("Tiny fingers, big yawns and a house that changed overnight. %s, the beginning.", "Everyone whispered and nobody slept. %s, the smallest big day.")),
        Category("music", Mood.JOYFUL, listOf("music", "concert", "song", "songs", "gaana", "dance", "dancing", "garba", "band", "guitar", "tabla", "singing", "karaoke", "playlist", "performance", "stage"),
            listOf("Loud, off-key and perfect. %s, with the volume all the way up.", "The song that still brings the day back. %s, on repeat.")),
        Category("sports", Mood.PROUD, listOf("cricket", "match", "football", "badminton", "kabaddi", "marathon", "run", "race", "tournament", "ipl", "world cup", "gym", "cycling", "swim", "swimming", "khel", "won", "winner", "medal", "trophy"),
            listOf("Sweat, shouting and a scoreline nobody will forget. %s, played like it mattered.", "One more over, one more lap, one more go. %s, for the win.")),
        Category("rain", Mood.CALM, listOf("rain", "rains", "monsoon", "barish", "paus", "pavsala", "bhajiya", "bhajji", "pakoda", "umbrella", "flood", "cloudy", "storm"),
            listOf("Grey skies, hot bhajiyas and nowhere to be. %s, the monsoon way.", "The smell of first rain and everyone at the window. %s, soaked and smiling.")),
        Category("winter", Mood.CALM, listOf("snow", "winter", "manali", "kashmir", "shimla", "ladakh", "cold", "sweater", "thandi", "fog", "december", "bonfire", "campfire"),
            listOf("Cold hands, warm cups and breath in the air. %s, bundled up together.", "A bonfire, a blanket and stories that got longer every year. %s, in the cold.")),
    )

    private val monthNames: Map<String, Month> = buildMap {
        Month.entries.forEach { m ->
            put(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(), m)
            put(m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(), m)
        }
        put("sept", Month.SEPTEMBER)
    }

    private val seasons = mapOf(
        "summer" to (Month.MAY to "Summer"), "garmi" to (Month.MAY to "Summer"), "unhala" to (Month.MAY to "Summer"),
        "winter" to (Month.DECEMBER to "Winter"), "thandi" to (Month.DECEMBER to "Winter"), "hivala" to (Month.DECEMBER to "Winter"),
        "monsoon" to (Month.JULY to "Monsoon"), "rains" to (Month.JULY to "Monsoon"), "barish" to (Month.JULY to "Monsoon"), "pavsala" to (Month.JULY to "Monsoon"),
        "spring" to (Month.MARCH to "Spring"), "autumn" to (Month.OCTOBER to "Autumn"),
    )

    private val knownPlaces = listOf(
        "goa", "pune", "mumbai", "nashik", "nagpur", "delhi", "bangalore", "bengaluru", "hyderabad", "chennai", "kolkata", "jaipur", "udaipur",
        "lonavala", "mahabaleshwar", "alibaug", "kerala", "manali", "shimla", "kashmir", "ladakh", "rishikesh", "varanasi", "kashi", "agra",
        "ooty", "munnar", "coorg", "hampi", "mysore", "mysuru", "kolhapur", "satara", "ratnagiri", "ganpatipule", "konkan", "shirdi", "lavasa",
        "matheran", "panchgani", "andaman", "dubai", "london", "singapore", "thailand", "bali", "paris", "sinhagad", "rajmachi", "tarkarli",
    )

    private val stopWords = setOf("ka", "ki", "ke", "pe", "par", "mein", "me", "se", "aur", "and", "the", "a", "an", "at", "in", "on", "of", "with", "wala", "wali", "vala", "cha", "chi", "che", "la", "madhe", "ani", "to", "for", "our", "my", "humara", "hamara", "apna", "sath", "saath")

    fun polish(input: String, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): PolishResult {
        val raw = input.trim().replace(Regex("\\s+"), " ")
        val lower = raw.lowercase(locale)
        val tokens = lower.split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotBlank() }

        // ---- date ----
        val (date, consumedDateTokens, detected) = parseDate(tokens, lower, today)

        // ---- category ----
        val category = categories.maxByOrNull { c -> c.keywords.count { kw -> kw in lower } }
            ?.takeIf { c -> c.keywords.any { kw -> kw in lower } }
        val categoryKey = category?.key ?: "plain_${listOf("plum", "amber", "teal", "rose", "indigo")[abs(lower.hashCode()) % 5]}"

        // ---- place ----
        val place = detectPlace(lower, tokens)

        // ---- title ----
        val coreTokens = raw.split(Regex("\\s+")).filter { tok ->
            val t = tok.lowercase(locale).trim { !it.isLetterOrDigit() }
            t.isNotEmpty() && t !in consumedDateTokens && t !in stopWords
        }
        var core = coreTokens.joinToString(" ") { titleCase(it) }.trim(',', ' ', '-')
        if (core.isBlank()) core = category?.let { titleCase(it.key) } ?: "A Day To Remember"
        if (category?.key in setOf("beach", "travel")) {
            core = core.replace(Regex("\\b(Trip|Tour|Vacation|Holiday|Safar|Yatra)\\b"), "Getaway")
        }
        val title = if (detected) "$core, ${datePhrase(date, locale)}" else core

        // ---- description ----
        val subject = if (detected) "${core.substringBefore(',')} in ${datePhrase(date, locale)}" else core.substringBefore(',')
        val descriptions = category?.descriptions ?: listOf(
            "A small moment, written down so it doesn't slip away. %s, kept safe.",
            "Not every memory needs a photo. %s, in a few honest words.",
        )
        val description = descriptions[abs(lower.hashCode()) % descriptions.size].format(subject)

        return PolishResult(
            title = title,
            description = description,
            date = date,
            coverStyle = categoryKey,
            place = place,
            mood = category?.mood,
            dateDetected = detected,
            category = category?.key ?: "plain",
        )
    }

    /**
     * A title for a memory that starts from photos rather than words: the date phrase, then the festival (Phase 3),
     * the people ("with Aaji", "with Aaji and Rahul", "with Aaji, Rahul and others") or, failing people, the place.
     * Examples: "October 2024 with Aaji", "Diwali 2024 with Aaji", "20 Dec 2024 in Pune", "Summer 2018".
     */
    fun titleFor(
        date: MemoryDate,
        place: String = "",
        peopleNames: List<String> = emptyList(),
        festival: String? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        val names = peopleNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val head = if (!festival.isNullOrBlank()) "${festival.trim()} ${Dates.start(date).year}" else datePhrase(date, locale)
        val withWhom = when (names.size) {
            0 -> null
            1 -> "with ${names[0]}"
            2 -> "with ${names[0]} and ${names[1]}"
            else -> "with ${names[0]}, ${names[1]} and others"
        }
        val tail = withWhom ?: place.trim().takeIf { it.isNotEmpty() }?.let { "in $it" }
        return if (tail == null) head else "$head $tail"
    }

    private fun datePhrase(date: MemoryDate, locale: Locale): String = when (date.kind) {
        DateKind.EXACT -> Dates.label(date, locale).text
        DateKind.MONTH -> Dates.label(date, locale).text
        DateKind.YEAR -> Dates.start(date).year.toString()
        DateKind.FUZZY -> date.label ?: Dates.start(date).year.toString()
    }

    private fun titleCase(word: String): String {
        if (word.length <= 2 && word.all { it.isLetter() } && word.uppercase() == word) return word
        return word.lowercase().replaceFirstChar { it.titlecase() }
    }

    private data class ParsedDate(val date: MemoryDate, val consumed: Set<String>, val detected: Boolean)

    private fun parseDate(tokens: List<String>, lower: String, today: LocalDate): ParsedDate {
        val consumed = HashSet<String>()
        // dd/mm/yyyy or dd-mm-yyyy
        Regex("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b").find(lower)?.let { m ->
            val (d, mo, y) = m.destructured
            runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()?.let { ld ->
                consumed += m.value
                return ParsedDate(Dates.exact(ld), consumed, true)
            }
        }
        when {
            "today" in tokens || "aaj" in tokens -> { consumed += listOf("today", "aaj"); return ParsedDate(Dates.exact(today), consumed, true) }
            "yesterday" in tokens || "kal" in tokens -> { consumed += listOf("yesterday", "kal"); return ParsedDate(Dates.exact(today.minusDays(1)), consumed, true) }
        }
        val year = tokens.firstOrNull { it.length == 4 && it.all(Char::isDigit) && Dates.isValidYear(it.toInt()) }?.toInt()
        val yearRelative = when {
            "last year" in lower || "pichle saal" in lower -> today.year - 1
            else -> null
        }
        val y = year ?: yearRelative
        if (year != null) consumed += year.toString()
        if (yearRelative != null) consumed += listOf("last", "year", "pichle", "saal")
        val monthToken = tokens.firstOrNull { it in monthNames }
        val month = monthToken?.let { monthNames[it] }
        val seasonToken = tokens.firstOrNull { it in seasons }
        val dayNumber = tokens.firstOrNull { it.length <= 2 && it.all(Char::isDigit) && it.toInt() in 1..31 }?.toInt()

        if (y != null) {
            if (month != null) {
                consumed += monthToken
                if (dayNumber != null) {
                    consumed += dayNumber.toString()
                    runCatching { LocalDate.of(y, month, dayNumber) }.getOrNull()?.let { return ParsedDate(Dates.exact(it), consumed, true) }
                }
                return ParsedDate(Dates.month(YearMonth.of(y, month)), consumed, true)
            }
            if (seasonToken != null) {
                val (anchor, label) = seasons.getValue(seasonToken)
                consumed += seasonToken
                return ParsedDate(Dates.fuzzy("$label $y", y, anchor.value), consumed, true)
            }
            return ParsedDate(Dates.year(y), consumed, true)
        }
        if (month != null) {
            // Month without a year: assume the most recent occurrence.
            consumed += monthToken
            var ym = YearMonth.of(today.year, month)
            if (ym.isAfter(YearMonth.from(today))) ym = ym.minusYears(1)
            if (dayNumber != null) {
                consumed += dayNumber.toString()
                runCatching { LocalDate.of(ym.year, month, dayNumber) }.getOrNull()?.let { return ParsedDate(Dates.exact(it), consumed, true) }
            }
            return ParsedDate(Dates.month(ym), consumed, true)
        }
        return ParsedDate(Dates.exact(today), consumed, false)
    }

    private fun detectPlace(lower: String, tokens: List<String>): String {
        knownPlaces.firstOrNull { p -> tokens.contains(p) }?.let { return titleCase(it) }
        Regex("\\b(?:in|at|near|@)\\s+([\\p{L}]{3,}(?:\\s[\\p{L}]{3,})?)").find(lower)?.groupValues?.get(1)?.let { candidate ->
            val words = candidate.split(" ").filter { it !in stopWords && it !in monthNames && it !in seasons }
            if (words.isNotEmpty() && words.none { it in categories.flatMap { c -> c.keywords } }) {
                return words.joinToString(" ") { titleCase(it) }
            }
        }
        return ""
    }
}
