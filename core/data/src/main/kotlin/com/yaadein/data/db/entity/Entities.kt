package com.yaadein.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "persons",
    indices = [Index("name"), Index("profile_media_id")],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_media_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String?,
    val relation: String,
    @ColumnInfo(name = "birthday_epoch_day") val birthdayEpochDay: Long?,
    @ColumnInfo(name = "profile_media_id") val profileMediaId: Long?,
    val notes: String,
    @ColumnInfo(name = "avatar_color_index") val avatarColorIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["group_id", "person_id"],
    indices = [Index("person_id")],
    foreignKeys = [
        ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class GroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "person_id") val personId: Long,
)

@Entity(
    tableName = "memories",
    indices = [Index(value = ["start_epoch_day"], orders = [Index.Order.DESC]), Index("type"), Index("cover_media_id")],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["cover_media_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "date_kind") val dateKind: String,
    @ColumnInfo(name = "start_epoch_day") val startEpochDay: Long,
    @ColumnInfo(name = "end_epoch_day") val endEpochDay: Long?,
    @ColumnInfo(name = "fuzzy_label") val fuzzyLabel: String?,
    @ColumnInfo(defaultValue = "") val place: String,
    val mood: String?,
    @ColumnInfo(name = "cover_media_id") val coverMediaId: Long?,
    @ColumnInfo(name = "cover_style") val coverStyle: String,
    @ColumnInfo(name = "voice_note_path") val voiceNotePath: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "media",
    indices = [Index("memory_id")],
    foreignKeys = [
        ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["memory_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "memory_id") val memoryId: Long?,
    val kind: String,
    @ColumnInfo(name = "encrypted_path") val encryptedPath: String,
    @ColumnInfo(name = "thumb_path") val thumbPath: String,
    @ColumnInfo(name = "taken_at") val takenAt: Long?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(defaultValue = "") val caption: String,
    val width: Int,
    val height: Int,
    val sha256: String,
    @ColumnInfo(defaultValue = "0") val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "media_persons",
    primaryKeys = ["media_id", "person_id"],
    indices = [Index("person_id")],
    foreignKeys = [
        ForeignKey(entity = MediaEntity::class, parentColumns = ["id"], childColumns = ["media_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MediaPersonEntity(
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "person_id") val personId: Long,
)

@Entity(
    tableName = "memory_persons",
    primaryKeys = ["memory_id", "person_id"],
    indices = [Index("person_id")],
    foreignKeys = [
        ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["memory_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MemoryPersonEntity(
    @ColumnInfo(name = "memory_id") val memoryId: Long,
    @ColumnInfo(name = "person_id") val personId: Long,
)

@Entity(
    tableName = "face_clusters",
    indices = [Index("sample_media_id"), Index("suggested_person_id")],
    foreignKeys = [
        ForeignKey(entity = MediaEntity::class, parentColumns = ["id"], childColumns = ["sample_media_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["suggested_person_id"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class FaceClusterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val embedding: ByteArray,
    @ColumnInfo(name = "sample_media_id") val sampleMediaId: Long?,
    @ColumnInfo(name = "sample_left") val sampleLeft: Float,
    @ColumnInfo(name = "sample_top") val sampleTop: Float,
    @ColumnInfo(name = "sample_right") val sampleRight: Float,
    @ColumnInfo(name = "sample_bottom") val sampleBottom: Float,
    @ColumnInfo(name = "suggested_person_id") val suggestedPersonId: Long?,
    @ColumnInfo(defaultValue = "0") val confirmed: Boolean,
    @ColumnInfo(name = "face_count", defaultValue = "1") val faceCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean = other is FaceClusterEntity && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/** Denormalised full-text index: one row per memory. docid == memory id. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "memory_search")
data class MemorySearchEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val body: String,
)

@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_uri") val sourceUri: String,
    @ColumnInfo(name = "memory_id") val memoryId: Long?,
    val stage: String,
    @ColumnInfo(name = "temp_path") val tempPath: String?,
    @ColumnInfo(name = "target_path") val targetPath: String?,
    @ColumnInfo(name = "thumb_path") val thumbPath: String?,
    val sha256: String?,
    val optimize: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

// ---- Relations ----

data class MemoryWithRelations(
    @Embedded val memory: MemoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(value = MemoryPersonEntity::class, parentColumn = "memory_id", entityColumn = "person_id"),
    )
    val people: List<PersonEntity>,
    @Relation(parentColumn = "id", entityColumn = "memory_id")
    val media: List<MediaEntity>,
)

data class GroupWithMembers(
    @Embedded val group: GroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(value = GroupMemberEntity::class, parentColumn = "group_id", entityColumn = "person_id"),
    )
    val members: List<PersonEntity>,
)

data class PairCount(
    @ColumnInfo(name = "a") val a: Long,
    @ColumnInfo(name = "b") val b: Long,
    @ColumnInfo(name = "n") val n: Int,
)

data class IdCount(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "n") val n: Int,
)

data class YearCount(
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "n") val n: Int,
)
