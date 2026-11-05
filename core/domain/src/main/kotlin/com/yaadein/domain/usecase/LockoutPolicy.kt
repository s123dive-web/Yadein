package com.yaadein.domain.usecase

/**
 * Escalating delay after wrong PIN attempts.
 * Attempts 1–4: no delay. 5th wrong: 30 s, then 1 m, 5 m, 15 m, 1 h for every further failure.
 * If wipe-after-ten is enabled, the 10th failure wipes the vault.
 */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 4
    const val WIPE_AT = 10

    private val delaysMs = longArrayOf(30_000L, 60_000L, 300_000L, 900_000L, 3_600_000L)

    /** Delay to impose after the given number of consecutive failures (0 when none). */
    fun delayAfterFailures(failures: Int): Long {
        if (failures <= FREE_ATTEMPTS) return 0L
        val index = (failures - FREE_ATTEMPTS - 1).coerceIn(0, delaysMs.lastIndex)
        return delaysMs[index]
    }

    fun shouldWipe(failures: Int, wipeEnabled: Boolean): Boolean = wipeEnabled && failures >= WIPE_AT

    fun attemptsBeforeWipe(failures: Int): Int = (WIPE_AT - failures).coerceAtLeast(0)
}
