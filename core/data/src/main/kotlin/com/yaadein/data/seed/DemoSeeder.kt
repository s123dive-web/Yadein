package com.yaadein.data.seed

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.room.withTransaction
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.GroupEntity
import com.yaadein.data.db.entity.GroupMemberEntity
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.db.entity.MemoryEntity
import com.yaadein.data.db.entity.MemoryPersonEntity
import com.yaadein.data.db.entity.PersonEntity
import com.yaadein.data.media.MediaStore
import com.yaadein.data.repository.MemoryRepositoryImpl
import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.people.KinTerm
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Realistic Indian-family demo data for screenshots and first-run exploration. Photos are
 * generated on device (soft gradients with simple shapes) so the demo needs no bundled images and
 * still exercises the full encrypted media path.
 */
@Singleton
class DemoSeeder @Inject constructor(
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    private val memoryRepository: MemoryRepositoryImpl,
) {
    private data class P(val name: String, val nick: String?, val relation: String, val birthday: LocalDate?, val color: Int, val kin: KinTerm? = null, val relationText: String? = null)
    private data class M(
        val title: String,
        val description: String,
        val date: LocalDate,
        val kind: DateKind = DateKind.EXACT,
        val endDate: LocalDate? = null,
        val fuzzyLabel: String? = null,
        val place: String = "",
        val mood: String? = null,
        val people: List<Int>,
        val photos: Int = 0,
        val cover: String = "plain_plum",
        val type: MemoryType = if (photos > 0) MemoryType.EVENT else MemoryType.TEXT_ONLY,
    )

    private val people = listOf(
        P("Sunita Deshmukh", "Aai", "FAMILY", LocalDate.of(1966, 8, 14), 0, kin = KinTerm.AAI),
        P("Vijay Deshmukh", "Baba", "FAMILY", LocalDate.of(1962, 1, 26), 1, kin = KinTerm.BABA),
        P("Rahul Deshmukh", "Rahul", "FAMILY", LocalDate.of(1994, 3, 10), 2, kin = KinTerm.DADA),
        P("Priya Deshmukh", "Priya", "FAMILY", LocalDate.of(1998, 11, 2), 3, kin = KinTerm.SISTER),
        P("Shalini Joshi", "Aaji", "FAMILY", LocalDate.of(1942, 5, 5), 4, kin = KinTerm.AAJI),
        P("Neha Kulkarni", "Neha", "FRIEND", LocalDate.of(1995, 7, 21), 5, kin = KinTerm.FRIEND),
        P("Arjun Mehta", null, "FRIEND", LocalDate.of(1994, 12, 30), 6, kin = KinTerm.FRIEND),
        P("Meera Joshi", "Meera tai", "FAMILY", LocalDate.of(1990, 2, 17), 7, kin = KinTerm.TAI),
        P("Sameer Rao", null, "COLLEAGUE", null, 1, kin = KinTerm.COLLEAGUE),
        P("Kabir Deshmukh", "Kabir", "FAMILY", LocalDate.of(2023, 9, 9), 2, relationText = "Nephew"),
    )

    private val memories = listOf(
        M("Kabir's first birthday", "Balloons everywhere, cake on his nose, and Aaji singing the aarti louder than anyone.", LocalDate.of(2024, 9, 9), place = "Home, Pune", mood = "CELEBRATION", people = listOf(0, 1, 2, 3, 4, 9), photos = 6, cover = "birthday"),
        M("Goa Getaway, December 2024", "Three days of beach shacks, sunburn and Rahul losing every round of cards.", LocalDate.of(2024, 12, 20), endDate = LocalDate.of(2024, 12, 23), place = "Palolem, Goa", mood = "JOYFUL", people = listOf(2, 3, 5, 6), photos = 5, cover = "beach"),
        M("Diwali at home", "Aai's faral, Baba on the ladder with the lights, Priya doing the rangoli at 6 am.", LocalDate.of(2024, 10, 31), place = "Pune", mood = "CELEBRATION", people = listOf(0, 1, 2, 3, 4, 7), photos = 4, cover = "diwali"),
        M("Kabir arrives", "7:42 am. Tiny fingers. Everyone cried, Baba pretended he didn't.", LocalDate.of(2023, 9, 9), place = "Sahyadri Hospital", mood = "PROUD", people = listOf(0, 1, 2, 3, 9), photos = 3, cover = "baby"),
        M("Monsoon trek to Rajmachi", "Slipped twice, laughed the whole way. Neha's chai at the top was the best chai ever.", LocalDate.of(2023, 7, 15), place = "Lonavala", mood = "JOYFUL", people = listOf(2, 5, 6), photos = 4, cover = "rain"),
        M("Meera tai's wedding", "Three days of haldi, sangeet and Baba dancing like nobody was watching.", LocalDate.of(2022, 11, 25), endDate = LocalDate.of(2022, 11, 27), place = "Nashik", mood = "CELEBRATION", people = listOf(0, 1, 2, 3, 4, 7), photos = 6, cover = "wedding"),
        M("Aaji turns 80", "A surprise party she definitely knew about. She still acted surprised for us.", LocalDate.of(2022, 5, 5), place = "Home", mood = "JOYFUL", people = listOf(0, 1, 2, 3, 4, 7), photos = 3, cover = "birthday"),
        M("Rahul's first day at the new job", "Shirt ironed by Aai. Nervous. Came home with a cake from the team.", LocalDate.of(2022, 2, 14), place = "Hinjewadi", mood = "PROUD", people = listOf(2, 8), cover = "plain_teal"),
        M("Priya graduates", "Topper of her batch. Aai's saree, Baba's camera, and me holding the flowers.", LocalDate.of(2021, 6, 12), place = "COEP, Pune", mood = "PROUD", people = listOf(0, 1, 2, 3), photos = 4, cover = "college"),
        M("Lockdown terrace evenings", "Chai, Ludo, and sunsets. Strange year, warm memories.", LocalDate.of(2020, 5, 1), kind = DateKind.MONTH, place = "Home", mood = "CALM", people = listOf(0, 1, 2, 3), cover = "home"),
        M("Holi with the cousins", "Nobody recognised anybody by noon. Meera tai's gujiya saved the day.", LocalDate.of(2020, 3, 10), place = "Nashik", mood = "FUNNY", people = listOf(2, 3, 7), photos = 3, cover = "holi"),
        M("Arjun's farewell", "He left for Bangalore. We promised a trip every year. Kept it, mostly.", LocalDate.of(2019, 8, 24), place = "Koregaon Park", mood = "NOSTALGIC", people = listOf(2, 5, 6), photos = 2, cover = "plain_indigo"),
        M("Ganpati at home", "Baba's modak count: 11. Aai's patience count: 0.", LocalDate.of(2019, 9, 2), place = "Home", mood = "CELEBRATION", people = listOf(0, 1, 2, 3, 4), photos = 3, cover = "festival"),
        M("Summer 2018 in Mahabaleshwar", "Strawberries with cream, foggy points, and Baba's old Maruti making it up the ghat.", LocalDate.of(2018, 5, 1), kind = DateKind.FUZZY, fuzzyLabel = "Summer 2018", place = "Mahabaleshwar", mood = "JOYFUL", people = listOf(0, 1, 2, 3), photos = 4, cover = "mountain"),
        M("Neha and I start our first startup", "Two laptops, one table at the library. It lasted eight months and taught us everything.", LocalDate.of(2018, 1, 1), kind = DateKind.YEAR, mood = "PROUD", people = listOf(2, 5), cover = "plain_amber"),
        M("College farewell", "Formal shirts, borrowed ties, and a DJ who only knew five songs.", LocalDate.of(2016, 4, 20), place = "Fergusson College", mood = "NOSTALGIC", people = listOf(2, 5, 6), photos = 5, cover = "college"),
        M("Priya's first school concert", "She forgot one line and improvised a better one.", LocalDate.of(2010, 12, 15), place = "Vidya Bhavan", mood = "FUNNY", people = listOf(0, 1, 3), cover = "music"),
        M("Cricket on the terrace", "Rahul's ball went into Patil kaka's balcony for the hundredth time.", LocalDate.of(2008, 1, 1), kind = DateKind.YEAR, mood = "FUNNY", people = listOf(2, 3, 6), cover = "sports"),
        M("Our first family trip to Konkan", "Aai made 40 puran polis for the journey. We finished them before Satara.", LocalDate.of(2006, 5, 1), kind = DateKind.FUZZY, fuzzyLabel = "Summer 2006", place = "Ganpatipule", mood = "NOSTALGIC", people = listOf(0, 1, 2, 3, 4), cover = "travel"),
        M("Aaji teaches me to make modak", "Sunday mornings in her kitchen, flour on everything. She said the secret is patience.", LocalDate.of(2012, 9, 1), kind = DateKind.MONTH, place = "Aaji's house, Nashik", mood = "CALM", people = listOf(2, 4), cover = "food"),
        M("Sameer's send-off lunch", "Team lunch at Vaishali. He's moving to Mumbai; we'll miss his terrible puns.", LocalDate.of(2025, 3, 7), place = "Vaishali, FC Road", mood = "NOSTALGIC", people = listOf(2, 8), photos = 2, cover = "food"),
        M("Kabir says 'Aai' for the first time", "In the middle of dinner. Everyone stopped eating.", LocalDate.of(2025, 1, 18), place = "Home", mood = "JOYFUL", people = listOf(0, 3, 9), cover = "baby"),
        M("Baba's retirement party", "34 years at the bank. His colleagues gave him a watch; he gave them a 20-minute speech.", LocalDate.of(2025, 1, 31), place = "Bank of Maharashtra, Pune", mood = "PROUD", people = listOf(0, 1, 2, 3, 9), photos = 4, cover = "plain_plum"),
        M("Rainy evening, bhajiyas and board games", "Power cut, candles, Neha won Scrabble with 'quixotic'.", LocalDate.of(2025, 7, 12), place = "Home", mood = "CALM", people = listOf(2, 3, 5), cover = "rain"),
        M("Aaji's 83rd birthday", "Small lunch, big laughs. She asked for a smartphone this year.", LocalDate.of(2025, 5, 5), place = "Nashik", mood = "CELEBRATION", people = listOf(0, 1, 2, 3, 4, 7), photos = 3, cover = "birthday"),
        M("Weekend at Alibaug", "Kabir met the sea. The sea won.", LocalDate.of(2025, 10, 4), endDate = LocalDate.of(2025, 10, 5), place = "Alibaug", mood = "JOYFUL", people = listOf(0, 2, 3, 9), photos = 5, cover = "beach"),
        M("Diwali 2025", "First Diwali where Kabir lit a phuljhadi, with four adults hovering.", LocalDate.of(2025, 10, 20), place = "Home", mood = "CELEBRATION", people = listOf(0, 1, 2, 3, 4, 9), photos = 4, cover = "diwali"),
        M("New year on the terrace", "Fairy lights, Baba's playlist from 1985, and a resolution to take more photos.", LocalDate.of(2026, 1, 1), place = "Home", mood = "JOYFUL", people = listOf(0, 1, 2, 3, 5), photos = 2, cover = "music"),
        M("Priya's engagement", "She said yes before he finished the question.", LocalDate.of(2026, 2, 14), place = "Pune", mood = "CELEBRATION", people = listOf(0, 1, 2, 3, 4, 7), photos = 5, cover = "wedding"),
        M("Trek to Sinhagad with Arjun", "He flew in for two days just for this. Pithla bhakri at the top.", LocalDate.of(2026, 8, 15), place = "Sinhagad", mood = "JOYFUL", people = listOf(2, 6), photos = 3, cover = "mountain"),
        M("Kabir's third birthday", "Dinosaur cake. He roared at everyone, including the cake.", LocalDate.of(2026, 9, 9), place = "Home", mood = "CELEBRATION", people = listOf(0, 1, 2, 3, 9), photos = 4, cover = "birthday"),
        M("Just a quiet Sunday", "Aai hummed while cooking, Baba read the paper twice. Nothing happened and it was perfect.", LocalDate.of(2026, 10, 25), place = "Home", mood = "CALM", people = listOf(0, 1), cover = "home", type = MemoryType.QUICK),
    )

    private val random = Random(7)

    suspend fun isSeeded(): Boolean = db.memoryDao().count() > 0

    /** Seeds the demo family. Safe to call once; subsequent calls are no-ops. */
    suspend fun seed() {
        if (isSeeded()) return
        val now = System.currentTimeMillis()
        val personIds = people.mapIndexed { i, p ->
            db.personDao().upsert(
                PersonEntity(
                    name = p.name, nickname = p.nick, relation = p.relation,
                    birthdayEpochDay = p.birthday?.toEpochDay(), profileMediaId = null, notes = "",
                    avatarColorIndex = p.color, createdAt = now - (people.size - i) * 86_400_000L,
                    kinTerm = p.kin?.name, relationText = p.relationText,
                ),
            )
        }
        val groups = listOf(
            "Family" to listOf(0, 1, 2, 3, 4, 7, 9),
            "College friends" to listOf(5, 6),
            "Aai's side" to listOf(4, 7),
        )
        val groupEmoji = listOf("👨‍👩‍👧‍👦", "🎓", "🏡")
        groups.forEachIndexed { gi, (name, members) ->
            val gid = db.groupDao().upsert(GroupEntity(name = name, emoji = groupEmoji[gi], createdAt = now))
            db.groupDao().insertMembers(members.map { GroupMemberEntity(gid, personIds[it]) })
        }
        memories.forEachIndexed { index, m ->
            db.withTransaction {
                val createdAt = now - (memories.size - index) * 3_600_000L
                val memoryId = db.memoryDao().insert(
                    MemoryEntity(
                        type = m.type.name, title = m.title, description = m.description,
                        dateKind = m.kind.name, startEpochDay = m.date.toEpochDay(), endEpochDay = m.endDate?.toEpochDay(),
                        fuzzyLabel = m.fuzzyLabel, place = m.place, mood = m.mood, coverMediaId = null, coverStyle = m.cover,
                        voiceNotePath = null, createdAt = createdAt, updatedAt = createdAt,
                    ),
                )
                db.memoryDao().insertPeople(m.people.map { MemoryPersonEntity(memoryId, personIds[it]) })
                var coverId: Long? = null
                repeat(m.photos) { p ->
                    val id = insertGeneratedPhoto(memoryId, m.title, m.cover, index * 10 + p, m.date, p, createdAt)
                    if (coverId == null) coverId = id
                }
                if (coverId != null) db.memoryDao().setCover(memoryId, coverId, createdAt)
                memoryRepository.reindex(memoryId)
            }
        }
    }

    /** Adds [count] text-only memories spread over 15 years; used to verify timeline performance. */
    suspend fun seedLarge(count: Int) {
        val personIds = db.personDao().getAll().map { it.id }
        val now = System.currentTimeMillis()
        val titles = listOf("Chai with", "Evening walk with", "Phone call with", "Lunch with", "Movie night with", "Birthday wishes for", "Train ride with", "Weekend plans with")
        val places = listOf("Home", "Pune", "Mumbai", "Nashik", "Office", "FC Road", "Koregaon Park", "")
        val covers = com.yaadein.domain.model.CoverStyle.ALL
        var i = 0
        while (i < count) {
            db.withTransaction {
                val end = minOf(i + 200, count)
                while (i < end) {
                    val day = LocalDate.of(2026, 11, 1).minusDays(random.nextInt(15 * 365).toLong())
                    val who = if (personIds.isEmpty()) emptyList() else listOf(personIds[random.nextInt(personIds.size)])
                    val whoName = who.firstOrNull()?.let { id -> people.getOrNull(personIds.indexOf(id))?.nick ?: "family" } ?: "family"
                    val memoryId = db.memoryDao().insert(
                        MemoryEntity(
                            type = MemoryType.QUICK.name, title = "${titles[random.nextInt(titles.size)]} $whoName",
                            description = "A small moment worth keeping.", dateKind = DateKind.EXACT.name,
                            startEpochDay = day.toEpochDay(), endEpochDay = null, fuzzyLabel = null,
                            place = places[random.nextInt(places.size)], mood = null, coverMediaId = null,
                            coverStyle = covers[random.nextInt(covers.size)], voiceNotePath = null, createdAt = now - i, updatedAt = now - i,
                        ),
                    )
                    db.memoryDao().insertPeople(who.map { MemoryPersonEntity(memoryId, it) })
                    memoryRepository.reindex(memoryId)
                    i++
                }
            }
        }
    }

    private suspend fun insertGeneratedPhoto(memoryId: Long, title: String, style: String, seed: Int, date: LocalDate, position: Int, createdAt: Long): Long {
        val full = renderPhoto(1200, 900, style, seed, title)
        val thumb = Bitmap.createScaledBitmap(full, 400, 300, true)
        val fullBytes = ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        val thumbBytes = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        full.recycle(); thumb.recycle()
        val mediaFile = mediaStore.newMediaFile("jpg")
        val thumbFile = mediaStore.newThumbFile()
        val sha = mediaStore.crypto.encryptBytes(fullBytes, mediaFile)
        mediaStore.crypto.encryptBytes(thumbBytes, thumbFile)
        return db.mediaDao().insert(
            MediaEntity(
                memoryId = memoryId, kind = "PHOTO", encryptedPath = mediaStore.relativize(mediaFile), thumbPath = mediaStore.relativize(thumbFile),
                takenAt = date.atTime(10 + position, 15).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                durationMs = null, sizeBytes = fullBytes.size.toLong(), caption = "", width = 1200, height = 900, sha256 = sha,
                position = position, createdAt = createdAt,
            ),
        )
    }

    private fun renderPhoto(w: Int, h: Int, style: String, seed: Int, title: String): Bitmap {
        val (c1, c2) = palette(style, seed)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        val rnd = Random(seed.toLong())
        // soft circles like bokeh
        repeat(14) {
            paint.color = Color.argb(40 + rnd.nextInt(60), 255, 255, 255)
            val r = (h * (0.05f + rnd.nextFloat() * 0.18f))
            canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r, paint)
        }
        // horizon / hills silhouette
        paint.color = Color.argb(70, 0, 0, 0)
        val path = android.graphics.Path()
        path.moveTo(0f, h * 0.82f)
        var x = 0f
        while (x < w) {
            path.quadTo(x + w * 0.1f, h * (0.62f + rnd.nextFloat() * 0.18f), x + w * 0.2f, h * 0.82f)
            x += w * 0.2f
        }
        path.lineTo(w.toFloat(), h.toFloat()); path.lineTo(0f, h.toFloat()); path.close()
        canvas.drawPath(path, paint)
        // small caption so each photo is distinguishable
        paint.color = Color.argb(160, 255, 255, 255)
        paint.textSize = h * 0.05f
        canvas.drawText(title.take(28), w * 0.05f, h * 0.93f, paint)
        return bmp
    }

    private fun palette(style: String, seed: Int): Pair<Int, Int> {
        val base = when (style) {
            "beach" -> 0xFF0EA5E9.toInt() to 0xFFFDE68A.toInt()
            "mountain" -> 0xFF1E3A5F.toInt() to 0xFF93C5FD.toInt()
            "travel" -> 0xFF0D9488.toInt() to 0xFFFBBF24.toInt()
            "wedding" -> 0xFF9F1239.toInt() to 0xFFFDA4AF.toInt()
            "birthday" -> 0xFF7C3AED.toInt() to 0xFFF472B6.toInt()
            "diwali" -> 0xFF9A3412.toInt() to 0xFFFBBF24.toInt()
            "holi" -> 0xFFDB2777.toInt() to 0xFF34D399.toInt()
            "festival" -> 0xFFB91C1C.toInt() to 0xFFF59E0B.toInt()
            "college" -> 0xFF4338CA.toInt() to 0xFFA5B4FC.toInt()
            "baby" -> 0xFFF9A8D4.toInt() to 0xFFBFDBFE.toInt()
            "rain" -> 0xFF334155.toInt() to 0xFF7DD3FC.toInt()
            "food" -> 0xFFC2410C.toInt() to 0xFFFDBA74.toInt()
            "music" -> 0xFF6D28D9.toInt() to 0xFF67E8F9.toInt()
            else -> 0xFF5B2A86.toInt() to 0xFFE8A33D.toInt()
        }
        // vary slightly per photo
        val shift = (seed % 5) * 12
        return Color.argb(255, (Color.red(base.first) + shift).coerceAtMost(255), Color.green(base.first), Color.blue(base.first)) to
            Color.argb(255, Color.red(base.second), (Color.green(base.second) + shift).coerceAtMost(255), Color.blue(base.second))
    }
}
