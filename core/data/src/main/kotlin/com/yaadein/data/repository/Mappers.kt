package com.yaadein.data.repository

import com.yaadein.data.db.entity.GroupEntity
import com.yaadein.data.db.entity.GroupWithMembers
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.db.entity.MemoryEntity
import com.yaadein.data.db.entity.MemoryWithRelations
import com.yaadein.data.db.entity.PersonEntity
import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Mood
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.Relation

internal fun PersonEntity.toDomain() = Person(
    id = id, name = name, nickname = nickname,
    relation = Relation.entries.firstOrNull { it.name == relation } ?: Relation.OTHER,
    birthdayEpochDay = birthdayEpochDay, profileMediaId = profileMediaId, notes = notes,
    avatarColorIndex = avatarColorIndex, createdAt = createdAt, kinTerm = kinTerm, relationText = relationText,
)

internal fun Person.toEntity() = PersonEntity(
    id = id, name = name.trim(), nickname = nickname?.trim()?.takeIf { it.isNotEmpty() }, relation = relation.name,
    birthdayEpochDay = birthdayEpochDay, profileMediaId = profileMediaId, notes = notes,
    avatarColorIndex = avatarColorIndex, createdAt = createdAt, kinTerm = kinTerm, relationText = relationText?.trim()?.takeIf { it.isNotEmpty() },
)

internal fun GroupWithMembers.toDomain() = Group(
    id = group.id, name = group.name, emoji = group.emoji,
    memberIds = members.map { it.id }, createdAt = group.createdAt,
)

internal fun Group.toEntity() = GroupEntity(id = id, name = name.trim(), emoji = emoji, createdAt = createdAt)

internal fun MemoryEntity.toDomain() = Memory(
    id = id,
    type = MemoryType.entries.firstOrNull { it.name == type } ?: MemoryType.EVENT,
    title = title, description = description,
    date = MemoryDate(
        kind = DateKind.entries.firstOrNull { it.name == dateKind } ?: DateKind.EXACT,
        startEpochDay = startEpochDay, endEpochDay = endEpochDay, label = fuzzyLabel,
    ),
    place = place, mood = mood?.let { m -> Mood.entries.firstOrNull { it.name == m } },
    coverMediaId = coverMediaId, coverStyle = coverStyle, voiceNotePath = voiceNotePath,
    createdAt = createdAt, updatedAt = updatedAt,
    favorite = favorite, lastOpenedAt = lastOpenedAt,
)

internal fun Memory.toEntity() = MemoryEntity(
    id = id, type = type.name, title = title.trim(), description = description.trim(),
    dateKind = date.kind.name, startEpochDay = date.startEpochDay, endEpochDay = date.endEpochDay,
    fuzzyLabel = date.label, place = place.trim(), mood = mood?.name, coverMediaId = coverMediaId,
    coverStyle = coverStyle, voiceNotePath = voiceNotePath, createdAt = createdAt, updatedAt = updatedAt,
    favorite = favorite, lastOpenedAt = lastOpenedAt,
)

internal fun MediaEntity.toDomain() = Media(
    id = id, memoryId = memoryId,
    kind = MediaKind.entries.firstOrNull { it.name == kind } ?: MediaKind.PHOTO,
    encryptedPath = encryptedPath, thumbPath = thumbPath, takenAt = takenAt, durationMs = durationMs,
    sizeBytes = sizeBytes, caption = caption, width = width, height = height, sha256 = sha256,
    position = position, createdAt = createdAt, latitude = latitude, longitude = longitude,
)

internal fun Media.toEntity() = MediaEntity(
    id = id, memoryId = memoryId, kind = kind.name, encryptedPath = encryptedPath, thumbPath = thumbPath,
    takenAt = takenAt, durationMs = durationMs, sizeBytes = sizeBytes, caption = caption, width = width,
    height = height, sha256 = sha256, position = position, createdAt = createdAt, latitude = latitude, longitude = longitude,
)

internal fun MemoryWithRelations.toCard(): MemoryCard {
    val m = memory.toDomain()
    val sortedMedia = media.sortedWith(compareBy({ it.position }, { it.takenAt ?: Long.MAX_VALUE }, { it.id }))
    val cover = sortedMedia.firstOrNull { it.id == m.coverMediaId } ?: sortedMedia.firstOrNull()
    return MemoryCard(
        memory = m,
        people = people.map { it.toDomain() }.sortedBy { it.name.lowercase() },
        cover = cover?.toDomain(),
        mediaCount = media.size,
    )
}

internal fun MemoryWithRelations.toDetail(): MemoryDetail = MemoryDetail(
    memory = memory.toDomain(),
    people = people.map { it.toDomain() }.sortedBy { it.name.lowercase() },
    media = media.sortedWith(compareBy({ it.position }, { it.takenAt ?: Long.MAX_VALUE }, { it.id })).map { it.toDomain() },
)
