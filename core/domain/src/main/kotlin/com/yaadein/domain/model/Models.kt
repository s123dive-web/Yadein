package com.yaadein.domain.model

import kotlinx.serialization.Serializable

/** How a person relates to the user. */
enum class Relation { FAMILY, FRIEND, COLLEAGUE, OTHER }

enum class MemoryType { EVENT, QUICK, TEXT_ONLY }

/** How precisely the user knows when a memory happened. */
enum class DateKind { EXACT, MONTH, YEAR, FUZZY }

enum class Mood { JOYFUL, CALM, NOSTALGIC, PROUD, FUNNY, SAD, CELEBRATION }

enum class MediaKind { PHOTO, VIDEO }

@Serializable
data class Person(
    val id: Long = 0,
    val name: String,
    val nickname: String? = null,
    val relation: Relation = Relation.FAMILY,
    /** Birthday as epoch day (LocalDate.toEpochDay()). Null when unknown. */
    val birthdayEpochDay: Long? = null,
    val profileMediaId: Long? = null,
    val notes: String = "",
    /** Index into the avatar palette so a person's colour is stable everywhere. */
    val avatarColorIndex: Int = 0,
    val createdAt: Long = 0,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: name
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercaseChar().toString() }
}

@Serializable
data class Group(
    val id: Long = 0,
    val name: String,
    val emoji: String = "👨‍👩‍👧",
    val memberIds: List<Long> = emptyList(),
    val createdAt: Long = 0,
)

/**
 * When the memory happened.
 * - EXACT: [startEpochDay] (and optional [endEpochDay] for multi-day events)
 * - MONTH: [startEpochDay] is the first of the month
 * - YEAR:  [startEpochDay] is 1 January
 * - FUZZY: [startEpochDay] is an anchor day and [label] describes it ("Summer 2015")
 */
@Serializable
data class MemoryDate(
    val kind: DateKind,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val label: String? = null,
)

@Serializable
data class Memory(
    val id: Long = 0,
    val type: MemoryType,
    val title: String,
    val description: String = "",
    val date: MemoryDate,
    val place: String = "",
    val mood: Mood? = null,
    val coverMediaId: Long? = null,
    /** Gradient / illustration key used when there is no cover photo (see CoverStyle). */
    val coverStyle: String = CoverStyle.DEFAULT,
    val voiceNotePath: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class Media(
    val id: Long = 0,
    val memoryId: Long? = null,
    val kind: MediaKind,
    val encryptedPath: String,
    val thumbPath: String,
    val takenAt: Long? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long,
    val caption: String = "",
    val width: Int,
    val height: Int,
    val sha256: String,
    val position: Int = 0,
    val createdAt: Long = 0,
    /** EXIF GPS position when the photo had one. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** A memory together with everything the timeline card needs. */
data class MemoryCard(
    val memory: Memory,
    val people: List<Person>,
    val cover: Media?,
    val mediaCount: Int,
)

/** A memory with all of its related data for the detail screen. */
data class MemoryDetail(
    val memory: Memory,
    val people: List<Person>,
    val media: List<Media>,
)

data class PersonStory(
    val person: Person,
    val memoryCount: Int,
    val mediaCount: Int,
    val firstMemory: Memory?,
    val lastMemory: Memory?,
    val groups: List<Group>,
    /** Days until the next birthday, null when unknown. */
    val daysToBirthday: Int?,
)

/** Named cover styles for text-only memories. Keys are stable (stored in the DB). */
object CoverStyle {
    const val DEFAULT = "plain_plum"
    val ALL = listOf(
        "plain_plum", "plain_amber", "plain_teal", "plain_rose", "plain_indigo",
        "beach", "mountain", "travel", "wedding", "birthday", "diwali", "holi", "festival",
        "school", "college", "home", "food", "baby", "music", "sports", "rain", "winter",
    )
}
