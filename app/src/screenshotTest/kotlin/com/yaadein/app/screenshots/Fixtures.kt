package com.yaadein.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.graphics.painter.Painter
import com.yaadein.core.ui.media.ProvidePreviewImages
import com.yaadein.core.ui.theme.YaadeinTheme
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.PersonStory
import com.yaadein.domain.model.Relation
import com.yaadein.domain.settings.AppTheme
import com.yaadein.domain.settings.DarkMode
import java.time.LocalDate
import java.time.YearMonth

/** Deterministic sample data for screenshot tests (no database, no Hilt). */
object Fixtures {
    val aai = Person(1, "Sunita Deshmukh", "Aai", Relation.FAMILY, LocalDate.of(1966, 8, 14).toEpochDay(), avatarColorIndex = 0)
    val baba = Person(2, "Vijay Deshmukh", "Baba", Relation.FAMILY, LocalDate.of(1962, 1, 26).toEpochDay(), avatarColorIndex = 1)
    val rahul = Person(3, "Rahul Deshmukh", "Rahul", Relation.FAMILY, LocalDate.of(1994, 3, 10).toEpochDay(), avatarColorIndex = 2)
    val priya = Person(4, "Priya Deshmukh", "Priya", Relation.FAMILY, LocalDate.of(1998, 11, 2).toEpochDay(), avatarColorIndex = 3)
    val aaji = Person(5, "Shalini Joshi", "Aaji", Relation.FAMILY, LocalDate.of(1942, 5, 5).toEpochDay(), avatarColorIndex = 4)
    val neha = Person(6, "Neha Kulkarni", "Neha", Relation.FRIEND, LocalDate.of(1995, 7, 21).toEpochDay(), avatarColorIndex = 5)
    val arjun = Person(7, "Arjun Mehta", null, Relation.FRIEND, null, avatarColorIndex = 6)
    val kabir = Person(8, "Kabir Deshmukh", "Kabir", Relation.FAMILY, LocalDate.of(2023, 9, 9).toEpochDay(), avatarColorIndex = 2)
    val people = listOf(aai, baba, rahul, priya, aaji, neha, arjun, kabir)

    private fun media(id: Long, memoryId: Long, kind: MediaKind = MediaKind.PHOTO) =
        Media(id = id, memoryId = memoryId, kind = kind, encryptedPath = "m$id", thumbPath = "t$id", sizeBytes = 1, width = 1200, height = 900, sha256 = "x", position = id.toInt())

    val cards: List<MemoryCard> = listOf(
        MemoryCard(
            Memory(1, MemoryType.EVENT, "Kabir's first birthday", "Balloons everywhere, cake on his nose, and Aaji singing the aarti louder than anyone.", Dates.exact(LocalDate.of(2024, 9, 9)), "Home, Pune", coverStyle = "birthday"),
            listOf(aai, baba, rahul, priya, aaji, kabir), media(1, 1), 6,
        ),
        MemoryCard(
            Memory(2, MemoryType.TEXT_ONLY, "Goa Getaway, December 2024", "Three days of beach shacks, sunburn and Rahul losing every round of cards.", Dates.exact(LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 23)), "Palolem, Goa", coverStyle = "beach"),
            listOf(rahul, priya, neha, arjun), null, 0,
        ),
        MemoryCard(
            Memory(3, MemoryType.EVENT, "Diwali at home", "Aai's faral, Baba on the ladder with the lights, Priya doing the rangoli at 6 am.", Dates.exact(LocalDate.of(2024, 10, 31)), "Pune", coverStyle = "diwali"),
            listOf(aai, baba, priya), media(3, 3), 4,
        ),
        MemoryCard(
            Memory(4, MemoryType.TEXT_ONLY, "Lockdown terrace evenings", "Chai, Ludo, and sunsets. Strange year, warm memories.", Dates.month(YearMonth.of(2020, 5)), "Home", coverStyle = "home"),
            listOf(aai, baba, rahul, priya), null, 0,
        ),
        MemoryCard(
            Memory(5, MemoryType.TEXT_ONLY, "Summer in Mahabaleshwar", "Strawberries with cream, foggy points, and Baba's old Maruti making it up the ghat.", Dates.fuzzy("Summer 2018", 2018, 5), "Mahabaleshwar", coverStyle = "mountain"),
            listOf(aai, baba, rahul, priya), null, 0,
        ),
        MemoryCard(
            Memory(6, MemoryType.TEXT_ONLY, "Neha and I start our first startup", "Two laptops, one table at the library.", Dates.year(2018), coverStyle = "plain_amber"),
            listOf(rahul, neha), null, 0,
        ),
    )

    val detail = MemoryDetail(
        memory = cards[0].memory,
        people = cards[0].people,
        media = listOf(media(1, 1), media(2, 1), media(3, 1, MediaKind.VIDEO), media(4, 1), media(5, 1)),
    )

    val story = PersonStory(
        person = rahul,
        memoryCount = 18,
        mediaCount = 64,
        firstMemory = Memory(9, MemoryType.TEXT_ONLY, "Cricket on the terrace", "", Dates.year(2008)),
        lastMemory = cards[0].memory,
        groups = listOf(com.yaadein.domain.model.Group(1, "Family", "👨‍👩‍👧‍👦"), com.yaadein.domain.model.Group(2, "College friends", "🎓")),
        daysToBirthday = 12,
    )

    private val palettes = listOf(
        Color(0xFF7C3AED) to Color(0xFFF472B6),
        Color(0xFF0EA5E9) to Color(0xFFFDE68A),
        Color(0xFF9A3412) to Color(0xFFFBBF24),
        Color(0xFF0D9488) to Color(0xFFFBBF24),
        Color(0xFF9F1239) to Color(0xFFFDA4AF),
    )

    fun previewPainter(id: Long): Painter {
        val (a, b) = palettes[(id % palettes.size).toInt()]
        return BrushPainter(Brush.linearGradient(listOf(a, b), start = Offset.Zero, end = Offset(900f, 700f)))
    }
}

@Composable
fun ScreenshotTheme(theme: AppTheme = AppTheme.WARM_ALBUM, dark: Boolean = false, easy: Boolean = false, content: @Composable () -> Unit) {
    YaadeinTheme(theme = theme, darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT, easyMode = easy) {
        ProvidePreviewImages(painter = Fixtures::previewPainter, content = content)
    }
}
