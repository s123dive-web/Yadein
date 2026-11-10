package com.yaadein.data.media

import com.yaadein.domain.repository.ImportFailureKind
import com.yaadein.domain.repository.ImportProgressSource
import com.yaadein.domain.repository.MemoryImportProgress
import com.yaadein.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class ImportProgress(
    val total: Int = 0,
    val done: Int = 0,
    val failures: List<ImportFailure> = emptyList(),
    val currentMemoryId: Long? = null,
) {
    val running: Boolean get() = total > 0 && done < total
}

/** Something that looks at a photo once it is in the vault (face suggestions). Injected so JVM tests can pass a no-op. */
fun interface FaceIndexer {
    suspend fun onImported(mediaId: Long)
}

/**
 * Runs imports in an application-scoped coroutine so they continue while the user navigates.
 *
 * Two stages. First every picked file is copied into the app's temp dir right away (Photo Picker and share grants are
 * short-lived and die with the process); then, one at a time behind a mutex to keep memory bounded, each copy is
 * optimised, encrypted, verified and written to the database. A copied file that did not finish survives a restart
 * (see [MediaImporter.recoverPending]).
 *
 * Progress is published per memory ([observeAll]) so the timeline card can show a ring and a failure badge.
 */
@Singleton
class ImportCoordinator @Inject constructor(
    private val importer: MediaImporter,
    private val settings: SettingsRepository,
    private val faces: FaceIndexer,
) : ImportProgressSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _progress = MutableStateFlow(ImportProgress())
    /** Overall counters (kept for callers that only want "is anything importing"). */
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()
    private val _byMemory = MutableStateFlow<Map<Long, MemoryImportProgress>>(emptyMap())

    override fun observeAll(): Flow<Map<Long, MemoryImportProgress>> = _byMemory.asStateFlow()

    override fun dismissFailures(memoryId: Long) = _byMemory.update { map ->
        val p = map[memoryId] ?: return@update map
        if (p.running) map + (memoryId to p.copy(failures = emptyList())) else map - memoryId
    }

    /** Imports [items] into [memoryId]; returns immediately. [onDone] is called with the media ids created. */
    fun enqueue(memoryId: Long, items: List<PickedMedia>, optimize: Boolean, startPosition: Int = 0, onDone: suspend (List<Long>) -> Unit = {}) {
        if (items.isEmpty()) { scope.launch { onDone(emptyList()) }; return }
        _progress.update { it.copy(total = it.total + items.size, currentMemoryId = memoryId) }
        _byMemory.update { map ->
            val p = map[memoryId]
            map + (memoryId to (p?.copy(total = p.total + items.size) ?: MemoryImportProgress(total = items.size, done = 0)))
        }
        scope.launch {
            // Stage 1: copy everything while the grants are still valid.
            val staged = ArrayList<StagedMedia>()
            items.forEachIndexed { index, picked ->
                when (val s = importer.stage(picked, memoryId, optimize, startPosition + index)) {
                    is StageOutcome.Staged -> staged += s.media
                    is StageOutcome.Failed -> finishOne(memoryId, s.failure)
                }
            }
            // Stage 2: one at a time, heavy work.
            val ids = ArrayList<Long>()
            mutex.withLock {
                for (s in staged) {
                    when (val outcome = importer.finish(s)) {
                        is ImportOutcome.Success -> {
                            ids += outcome.mediaId
                            if (settings.current().faceSuggestions) runCatching { faces.onImported(outcome.mediaId) }
                            finishOne(memoryId, null)
                        }
                        is ImportOutcome.Failed -> finishOne(memoryId, outcome.failure)
                    }
                }
            }
            _progress.update { if (it.done >= it.total) ImportProgress(failures = it.failures) else it }
            onDone(ids)
        }
    }

    private fun finishOne(memoryId: Long, failure: ImportFailure?) {
        _progress.update { it.copy(done = it.done + 1, failures = if (failure != null) it.failures + failure else it.failures) }
        _byMemory.update { map ->
            val p = map[memoryId] ?: return@update map
            val next = p.copy(done = p.done + 1, failures = if (failure != null) p.failures + failure.kind else p.failures)
            // A finished import with nothing to report disappears; failures stay until the user sees them.
            if (!next.running && next.failures.isEmpty()) map - memoryId else map + (memoryId to next)
        }
    }

    fun clearFailures() = _progress.update { it.copy(failures = emptyList()) }

    /** Runs [block] while no import is encrypting (the face re-index worker shares the one heavy-work slot). */
    suspend fun <T> withImportLock(block: suspend () -> T): T = mutex.withLock { block() }

    /** True while photos are still being encrypted (the re-index worker steps aside). */
    val importing: Boolean get() = _progress.value.running

    companion object {
        val ImportFailure.kind: ImportFailureKind
            get() = when (this) {
                is ImportFailure.TooLarge -> ImportFailureKind.TOO_LARGE
                ImportFailure.Unsupported -> ImportFailureKind.UNSUPPORTED
                is ImportFailure.Unreadable -> ImportFailureKind.UNREADABLE
                ImportFailure.IntegrityCheckFailed -> ImportFailureKind.INTEGRITY_CHECK_FAILED
                is ImportFailure.NotEnoughSpace -> ImportFailureKind.NOT_ENOUGH_SPACE
            }
    }
}
