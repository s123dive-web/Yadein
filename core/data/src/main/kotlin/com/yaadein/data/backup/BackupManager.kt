package com.yaadein.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.GroupEntity
import com.yaadein.data.db.entity.GroupMemberEntity
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.db.entity.MediaPersonEntity
import com.yaadein.data.db.entity.MemoryEntity
import com.yaadein.data.db.entity.MemoryPersonEntity
import com.yaadein.data.db.entity.PersonEntity
import com.yaadein.data.media.MediaStore
import com.yaadein.data.repository.MemoryRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Single-file, password-protected backup (`.yaadein`).
 *
 * Layout (format 2): [magic "YDNB"][version 2][16-byte salt][7-byte nonce prefix][chunked AES-256-GCM(zip)]
 * where the key is Argon2id(password, salt) and the zip is sealed in 64 KiB chunks ([ChunkedAead]).
 * Format 1 (one GCM pass over the whole zip) is still readable, but it could not be written for real
 * libraries on Android because the platform buffers a whole GCM operation in memory. The zip holds `manifest.json`, `data.json` (all tables) and every media file
 * as plaintext (they are re-encrypted with the device key on restore). Restore verifies the manifest
 * and decrypts fully into a staging area before anything in the live vault is touched.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    private val memoryRepository: MemoryRepositoryImpl,
    private val keyDeriver: KeyDeriver = Argon2KeyDeriver(),
) {
    fun interface KeyDeriver { fun derive(password: CharArray, salt: ByteArray): ByteArray }

    class Argon2KeyDeriver : KeyDeriver {
        private val argon2 by lazy { Argon2Kt() }
        override fun derive(password: CharArray, salt: ByteArray): ByteArray =
            argon2.hash(Argon2Mode.ARGON2_ID, String(password).toByteArray(Charsets.UTF_8), salt, tCostInIterations = 3, mCostInKibibyte = 65_536, parallelism = 2, hashLengthInBytes = 32).rawHashAsByteArray()
    }

    @Serializable
    data class Manifest(val app: String = "yaadein", val format: Int = FORMAT, val createdAt: Long, val persons: Int, val memories: Int, val media: Int, val pack: Boolean = false)

    @Serializable
    data class Dump(
        val persons: List<PersonRow>, val groups: List<GroupRow>, val groupMembers: List<Pair<Long, Long>>,
        val memories: List<MemoryRow>, val media: List<MediaRow>, val mediaPersons: List<Pair<Long, Long>>, val memoryPersons: List<Pair<Long, Long>>,
    )

    @Serializable data class PersonRow(val id: Long, val name: String, val nickname: String?, val relation: String, val birthdayEpochDay: Long?, val profileMediaId: Long?, val notes: String, val avatarColorIndex: Int, val createdAt: Long)
    @Serializable data class GroupRow(val id: Long, val name: String, val emoji: String, val createdAt: Long)
    @Serializable data class MemoryRow(val id: Long, val type: String, val title: String, val description: String, val dateKind: String, val startEpochDay: Long, val endEpochDay: Long?, val fuzzyLabel: String?, val place: String, val mood: String?, val coverMediaId: Long?, val coverStyle: String, val createdAt: Long, val updatedAt: Long, val voice: Boolean = false)
    @Serializable data class MediaRow(val id: Long, val memoryId: Long?, val kind: String, val file: String, val thumb: String, val takenAt: Long?, val durationMs: Long?, val sizeBytes: Long, val caption: String, val width: Int, val height: Int, val sha256: String, val position: Int, val createdAt: Long, val latitude: Double? = null, val longitude: Double? = null)

    sealed interface Progress { data class Working(val done: Int, val total: Int) : Progress; data object Done : Progress }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Argon2id key for [password] (used once by automatic backup, which then reuses the key and salt). */
    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray = keyDeriver.derive(password, salt)

    suspend fun export(target: Uri, password: CharArray, onProgress: (Progress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        exportWithKey(target, salt, keyDeriver.derive(password, salt), onProgress)
    }

    /** Same file format as [export]; [keyBytes] must be Argon2id(password, [salt]) so a normal restore opens it. */
    suspend fun exportWithKey(target: Uri, salt: ByteArray, keyBytes: ByteArray, onProgress: (Progress) -> Unit = {}, onlyMemoryIds: Set<Long>? = null) = withContext(Dispatchers.IO) {
        val dump = dump(onlyMemoryIds)
        val manifest = Manifest(createdAt = System.currentTimeMillis(), persons = dump.persons.size, memories = dump.memories.size, media = dump.media.size, pack = onlyMemoryIds != null)
        val prefix = ByteArray(ChunkedAead.PREFIX_BYTES).also { SecureRandom().nextBytes(it) }
        val key = SecretKeySpec(keyBytes, "AES")
        val header = MAGIC + byteArrayOf(FORMAT.toByte()) + salt + prefix
        val raw = context.contentResolver.openOutputStream(target, "wt") ?: error("cannot open target")
        BufferedOutputStream(raw).use { out ->
            out.write(header)
            ZipOutputStream(ChunkedAeadOutputStream(out, key, prefix, header)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(json.encodeToString(Manifest.serializer(), manifest).toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("data.json")); zip.write(json.encodeToString(Dump.serializer(), dump).toByteArray()); zip.closeEntry()
                val wanted = dump.media.mapTo(HashSet()) { it.id }
                val media = db.mediaDao().all().filter { it.id in wanted }
                media.forEachIndexed { i, m ->
                    onProgress(Progress.Working(i, media.size))
                    copyEntry(zip, "media/${m.id}.bin", mediaStore.file(m.encryptedPath))
                    copyEntry(zip, "thumbs/${m.id}.jpg", mediaStore.file(m.thumbPath))
                }
                // Voice memories travel with the backup (MemoryRow.voice marks which memories have one).
                dump.memories.filter { it.voice }.forEach { row ->
                    db.memoryDao().get(row.id)?.voiceNotePath?.let { copyEntry(zip, "voice/${row.id}.m4a", mediaStore.file(it)) }
                }
            }
        }
        onProgress(Progress.Done)
    }

    private fun copyEntry(zip: ZipOutputStream, name: String, encrypted: File) {
        if (!encrypted.exists()) return
        zip.putNextEntry(ZipEntry(name))
        mediaStore.crypto.decryptingStream(encrypted.inputStream()).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Reads only the manifest (fast) so the UI can confirm before restoring. */
    suspend fun peek(source: Uri, password: CharArray): Manifest = withContext(Dispatchers.IO) {
        openZip(source, password).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") return@withContext json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                entry = zip.nextEntry
            }
            error("manifest missing")
        }
    }

    class WrongPasswordException : Exception("wrong password or corrupt backup")

    /** The file is a family pack (some memories), not a full backup: it can only be added, not restored. */
    class PackNotBackupException : Exception("this file is a family pack")

    private fun readArchive(source: Uri, password: CharArray, staging: File, onProgress: (Progress) -> Unit): Triple<Manifest, Dump, Map<String, File>> {
        var manifest: Manifest? = null
        var dump: Dump? = null
        val files = HashMap<String, File>()
        openZip(source, password).use { zip ->
            var entry = zip.nextEntry
            var n = 0
            while (entry != null) {
                when {
                    entry.name == "manifest.json" -> manifest = json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                    entry.name == "data.json" -> dump = json.decodeFromString(Dump.serializer(), zip.readBytes().decodeToString())
                    entry.name.startsWith("media/") || entry.name.startsWith("thumbs/") || entry.name.startsWith("voice/") -> {
                        // Entry names come from the file: keep only the expected "dir/<id>.<ext>" shape (no path tricks).
                        val safe = entry.name.replace('/', '_').takeIf { Regex("[a-z]+_[0-9]+\\.[a-z0-9]+").matches(it) }
                        if (safe != null) {
                            val f = File(staging, safe)
                            f.outputStream().use { zip.copyTo(it) }
                            files[entry.name] = f
                        }
                        onProgress(Progress.Working(++n, manifest?.media?.times(2) ?: 0))
                    }
                }
                entry = zip.nextEntry
            }
        }
        val m = manifest ?: throw WrongPasswordException()
        val d = dump ?: throw WrongPasswordException()
        check(m.app == "yaadein" && m.format <= FORMAT) { "unsupported backup" }
        return Triple(m, d, files)
    }

    /**
     * Writes a family pack: the given memories with their photos, videos, voice notes and the people in them, in the
     * backup format, sealed with [password]. Another Yaadein adds it with [importPack] without touching its own memories.
     */
    suspend fun exportPack(target: Uri, password: CharArray, memoryIds: Set<Long>, onProgress: (Progress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        exportWithKey(target, salt, keyDeriver.derive(password, salt), onProgress, memoryIds)
    }

    /** Result of [importPack]: how many memories were added and how many people were new. */
    data class PackResult(val memories: Int, val newPeople: Int, val firstMemoryId: Long?)

    /**
     * Adds a family pack to this vault. People are matched by name (case and spaces ignored) so "Aaji" in the pack and
     * "Aaji" here become one person; everything else is added alongside the existing memories.
     */
    suspend fun importPack(source: Uri, password: CharArray, onProgress: (Progress) -> Unit = {}): PackResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "pack-${System.nanoTime()}").apply { mkdirs() }
        try {
            val (_, d, files) = readArchive(source, password, staging, onProgress)
            val newMedia = HashMap<Long, Pair<String, String>>()
            d.media.forEach { row ->
                val plain = files["media/${row.id}.bin"] ?: return@forEach
                val target = mediaStore.newMediaFile(if (row.kind == "VIDEO") "mp4" else "jpg")
                val thumbTarget = mediaStore.newThumbFile()
                plain.inputStream().use { mediaStore.crypto.encryptFile(it, target) }
                files["thumbs/${row.id}.jpg"]?.inputStream()?.use { mediaStore.crypto.encryptFile(it, thumbTarget) } ?: mediaStore.crypto.encryptBytes(ByteArray(0), thumbTarget)
                newMedia[row.id] = mediaStore.relativize(target) to mediaStore.relativize(thumbTarget)
            }
            val newVoice = HashMap<Long, String>()
            d.memories.filter { it.voice }.forEach { row ->
                val plain = files["voice/${row.id}.m4a"] ?: return@forEach
                val target = mediaStore.newVoiceFile()
                plain.inputStream().use { mediaStore.crypto.encryptFile(it, target) }
                newVoice[row.id] = mediaStore.relativize(target)
            }
            var newPeople = 0
            var first: Long? = null
            db.withTransaction {
                fun norm(n: String) = n.trim().lowercase().replace(Regex("\\s+"), " ")
                val existing = db.personDao().getAll().associateBy { norm(it.name) }
                val personIds = HashMap<Long, Long>()
                d.persons.forEach { p ->
                    personIds[p.id] = existing[norm(p.name)]?.id ?: run {
                        newPeople++
                        db.personDao().upsert(PersonEntity(0, p.name, p.nickname, p.relation, p.birthdayEpochDay, null, p.notes, p.avatarColorIndex, p.createdAt))
                    }
                }
                val memoryIds = HashMap<Long, Long>()
                d.memories.forEach { mm -> memoryIds[mm.id] = db.memoryDao().insert(MemoryEntity(0, mm.type, mm.title, mm.description, mm.dateKind, mm.startEpochDay, mm.endEpochDay, mm.fuzzyLabel, mm.place, mm.mood, null, mm.coverStyle, newVoice[mm.id], mm.createdAt, mm.updatedAt)) }
                val mediaIds = HashMap<Long, Long>()
                d.media.forEach { r ->
                    val paths = newMedia[r.id] ?: return@forEach
                    mediaIds[r.id] = db.mediaDao().insert(MediaEntity(0, r.memoryId?.let { memoryIds[it] }, r.kind, paths.first, paths.second, r.takenAt, r.durationMs, r.sizeBytes, r.caption, r.width, r.height, r.sha256, r.position, r.createdAt, r.latitude, r.longitude))
                }
                d.memories.forEach { mm ->
                    val newId = memoryIds[mm.id] ?: return@forEach
                    mm.coverMediaId?.let { mediaIds[it] }?.let { db.memoryDao().setCover(newId, it, mm.updatedAt) }
                }
                db.memoryDao().insertPeople(d.memoryPersons.mapNotNull { (mId, pId) -> val a = memoryIds[mId]; val b = personIds[pId]; if (a != null && b != null) MemoryPersonEntity(a, b) else null }.distinct())
                db.mediaDao().insertPeople(d.mediaPersons.mapNotNull { (mId, pId) -> val a = mediaIds[mId]; val b = personIds[pId]; if (a != null && b != null) MediaPersonEntity(a, b) else null }.distinct())
                memoryIds.values.forEach { memoryRepository.reindex(it) }
                first = memoryIds.values.firstOrNull()
            }
            onProgress(Progress.Done)
            PackResult(d.memories.size, newPeople, first)
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Replaces the vault with the backup. Everything is decrypted into a staging folder and the
     * manifest counts are checked before the live database and media are swapped in one transaction.
     */
    suspend fun restore(source: Uri, password: CharArray, onProgress: (Progress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            val (m, d, files) = readArchive(source, password, staging, onProgress)
            // A family pack holds only some memories; restoring it would replace the whole vault with them.
            if (m.pack) throw PackNotBackupException()
            check(d.media.size == m.media && d.memories.size == m.memories) { "backup manifest mismatch" }

            // Re-encrypt media with this device's key into fresh vault files.
            val newPaths = HashMap<Long, Pair<String, String>>()
            d.media.forEachIndexed { i, row ->
                val plain = files["media/${row.id}.bin"] ?: return@forEachIndexed
                val thumb = files["thumbs/${row.id}.jpg"]
                val target = mediaStore.newMediaFile(if (row.kind == "VIDEO") "mp4" else "jpg")
                val thumbTarget = mediaStore.newThumbFile()
                plain.inputStream().use { mediaStore.crypto.encryptFile(it, target) }
                if (thumb != null) thumb.inputStream().use { mediaStore.crypto.encryptFile(it, thumbTarget) } else mediaStore.crypto.encryptBytes(ByteArray(0), thumbTarget)
                newPaths[row.id] = mediaStore.relativize(target) to mediaStore.relativize(thumbTarget)
                onProgress(Progress.Working(i, d.media.size))
            }
            val voicePaths = HashMap<Long, String>()
            d.memories.filter { it.voice }.forEach { row ->
                val plain = files["voice/${row.id}.m4a"] ?: return@forEach
                val target = mediaStore.newVoiceFile()
                plain.inputStream().use { mediaStore.crypto.encryptFile(it, target) }
                voicePaths[row.id] = mediaStore.relativize(target)
            }
            val oldPaths = db.mediaDao().allPaths() + db.memoryDao().voicePaths()
            db.withTransaction {
                db.clearAllTables()
                val personIds = HashMap<Long, Long>()
                d.persons.forEach { p -> personIds[p.id] = db.personDao().upsert(PersonEntity(0, p.name, p.nickname, p.relation, p.birthdayEpochDay, null, p.notes, p.avatarColorIndex, p.createdAt)) }
                val groupIds = HashMap<Long, Long>()
                d.groups.forEach { g -> groupIds[g.id] = db.groupDao().upsert(GroupEntity(0, g.name, g.emoji, g.createdAt)) }
                db.groupDao().insertMembers(d.groupMembers.mapNotNull { (g, p) -> val gi = groupIds[g]; val pi = personIds[p]; if (gi != null && pi != null) GroupMemberEntity(gi, pi) else null })
                val memoryIds = HashMap<Long, Long>()
                d.memories.forEach { mm -> memoryIds[mm.id] = db.memoryDao().insert(MemoryEntity(0, mm.type, mm.title, mm.description, mm.dateKind, mm.startEpochDay, mm.endEpochDay, mm.fuzzyLabel, mm.place, mm.mood, null, mm.coverStyle, voicePaths[mm.id], mm.createdAt, mm.updatedAt)) }
                val mediaIds = HashMap<Long, Long>()
                d.media.forEach { r ->
                    val paths = newPaths[r.id] ?: return@forEach
                    mediaIds[r.id] = db.mediaDao().insert(MediaEntity(0, r.memoryId?.let { memoryIds[it] }, r.kind, paths.first, paths.second, r.takenAt, r.durationMs, r.sizeBytes, r.caption, r.width, r.height, r.sha256, r.position, r.createdAt, r.latitude, r.longitude))
                }
                d.memories.forEach { mm ->
                    val newId = memoryIds[mm.id] ?: return@forEach
                    val cover = mm.coverMediaId?.let { mediaIds[it] }
                    if (cover != null) db.memoryDao().setCover(newId, cover, mm.updatedAt)
                }
                d.persons.forEach { p ->
                    val newId = personIds[p.id] ?: return@forEach
                    val profile = p.profileMediaId?.let { mediaIds[it] }
                    if (profile != null) db.personDao().get(newId)?.let { db.personDao().upsert(it.copy(profileMediaId = profile)) }
                }
                db.memoryDao().insertPeople(d.memoryPersons.mapNotNull { (mId, pId) -> val a = memoryIds[mId]; val b = personIds[pId]; if (a != null && b != null) MemoryPersonEntity(a, b) else null })
                db.mediaDao().insertPeople(d.mediaPersons.mapNotNull { (mId, pId) -> val a = mediaIds[mId]; val b = personIds[pId]; if (a != null && b != null) MediaPersonEntity(a, b) else null })
                memoryRepository.reindexAll()
            }
            oldPaths.forEach { runCatching { mediaStore.file(it).delete() } }
            onProgress(Progress.Done)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun openZip(source: Uri, password: CharArray): ZipInputStream {
        val input = BufferedInputStream(context.contentResolver.openInputStream(source) ?: error("cannot open backup"))
        val magic = ByteArray(4); readFully(input, magic)
        if (!magic.contentEquals(MAGIC)) throw WrongPasswordException()
        val format = input.read()
        if (format > FORMAT) error("backup from a newer Yaadein")
        val salt = ByteArray(16); readFully(input, salt)
        return try {
            when (format) {
                FORMAT_SINGLE_GCM -> {
                    val nonce = ByteArray(12); readFully(input, nonce)
                    val key = SecretKeySpec(keyDeriver.derive(password, salt), "AES")
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
                    ZipInputStream(CipherInputStream(input, cipher))
                }
                else -> {
                    val prefix = ByteArray(ChunkedAead.PREFIX_BYTES); readFully(input, prefix)
                    val key = SecretKeySpec(keyDeriver.derive(password, salt), "AES")
                    val header = MAGIC + byteArrayOf(format.toByte()) + salt + prefix
                    ZipInputStream(ChunkedAeadInputStream(input, key, prefix, header))
                }
            }
        } catch (e: Exception) {
            input.close()
            throw e
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) { val n = input.read(buf, off, buf.size - off); if (n < 0) throw WrongPasswordException(); off += n }
    }

    /** Everything, or (for a family pack) only [onlyMemoryIds], their media and the people in them. */
    private suspend fun dump(onlyMemoryIds: Set<Long>? = null): Dump {
        val memories = db.memoryDao().getByIds(onlyMemoryIds?.toList() ?: db.memoryDao().allIds())
        val media = db.mediaDao().all().filter { onlyMemoryIds == null || it.memoryId in onlyMemoryIds }
        val packPeople = onlyMemoryIds?.let { memories.flatMapTo(HashSet()) { r -> r.people.map { it.id } } + media.flatMap { db.mediaDao().personIds(it.id) } }
        val persons = db.personDao().getAll().filter { packPeople == null || it.id in packPeople }
            .map { PersonRow(it.id, it.name, it.nickname, it.relation, it.birthdayEpochDay, it.profileMediaId?.takeIf { onlyMemoryIds == null }, it.notes, it.avatarColorIndex, it.createdAt) }
        val groups = if (onlyMemoryIds == null) db.groupDao().observeAll().first() else emptyList()
        val mediaPersons = ArrayList<Pair<Long, Long>>()
        media.forEach { m -> db.mediaDao().personIds(m.id).forEach { mediaPersons += m.id to it } }
        return Dump(
            persons = persons,
            groups = groups.map { GroupRow(it.group.id, it.group.name, it.group.emoji, it.group.createdAt) },
            groupMembers = groups.flatMap { g -> g.members.map { g.group.id to it.id } },
            memories = memories.map { r -> r.memory.let { MemoryRow(it.id, it.type, it.title, it.description, it.dateKind, it.startEpochDay, it.endEpochDay, it.fuzzyLabel, it.place, it.mood, it.coverMediaId, it.coverStyle, it.createdAt, it.updatedAt, voice = it.voiceNotePath != null) } },
            media = media.map { MediaRow(it.id, it.memoryId, it.kind, it.encryptedPath, it.thumbPath, it.takenAt, it.durationMs, it.sizeBytes, it.caption, it.width, it.height, it.sha256, it.position, it.createdAt, it.latitude, it.longitude) },
            mediaPersons = mediaPersons,
            memoryPersons = memories.flatMap { r -> r.people.map { r.memory.id to it.id } },
        )
    }

    companion object {
        val MAGIC = byteArrayOf('Y'.code.toByte(), 'D'.code.toByte(), 'N'.code.toByte(), 'B'.code.toByte())
        const val FORMAT = 2
        const val MIN_PASSWORD_LENGTH = 6
        private const val FORMAT_SINGLE_GCM = 1
        const val EXTENSION = "yaadein"
        const val MIME = "application/octet-stream"
    }
}
