package com.yaadein.data.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

/**
 * Runs imports in an application-scoped coroutine so they continue while the user navigates.
 * Imports are serialised to keep memory use bounded (one decoded photo or transcoding video at a time).
 */
@Singleton
class ImportCoordinator @Inject constructor(private val importer: MediaImporter) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    /** Imports [items] into [memoryId]; returns immediately. [onDone] is called with the media ids created. */
    fun enqueue(memoryId: Long, items: List<PickedMedia>, optimize: Boolean, startPosition: Int = 0, onDone: (List<Long>) -> Unit = {}) {
        if (items.isEmpty()) { onDone(emptyList()); return }
        _progress.update { it.copy(total = it.total + items.size, currentMemoryId = memoryId) }
        scope.launch {
            val ids = ArrayList<Long>()
            mutex.withLock {
                items.forEachIndexed { index, picked ->
                    when (val outcome = importer.import(picked, memoryId, optimize, startPosition + index)) {
                        is ImportOutcome.Success -> ids += outcome.mediaId
                        is ImportOutcome.Failed -> _progress.update { it.copy(failures = it.failures + outcome.failure) }
                    }
                    _progress.update { it.copy(done = it.done + 1) }
                }
            }
            _progress.update { if (it.done >= it.total) ImportProgress(failures = it.failures) else it }
            onDone(ids)
        }
    }

    fun clearFailures() = _progress.update { it.copy(failures = emptyList()) }
}
