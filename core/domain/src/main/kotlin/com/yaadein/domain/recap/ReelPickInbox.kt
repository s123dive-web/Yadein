package com.yaadein.domain.recap

/**
 * Memories ticked on the Timeline, on their way to the reel maker. A route argument cannot carry a long list of ids,
 * so the Timeline leaves them here and "Create a reel" takes them once when it opens.
 */
object ReelPickInbox {
    @Volatile private var pending: List<Long> = emptyList()

    fun offer(memoryIds: List<Long>) { pending = memoryIds.distinct() }

    /** The waiting ids (empty when there are none); the inbox is empty afterwards. */
    fun take(): List<Long> = pending.also { pending = emptyList() }
}
