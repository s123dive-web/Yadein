package com.yaadein.feature.recap

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppShareTest {
    @Test
    fun `a 30 second reel under 16 MB fits a status`() {
        assertTrue(WhatsAppShare.fitsStatus(durationMs = 30_000, sizeBytes = 13_600_000))
        assertTrue(WhatsAppShare.fitsStatus(durationMs = 12_500, sizeBytes = WhatsAppShare.STATUS_MAX_BYTES))
    }

    @Test
    fun `longer or bigger reels do not`() {
        assertFalse(WhatsAppShare.fitsStatus(durationMs = 30_001, sizeBytes = 1_000_000))
        assertFalse(WhatsAppShare.fitsStatus(durationMs = 20_000, sizeBytes = WhatsAppShare.STATUS_MAX_BYTES + 1))
    }

    @Test
    fun `unknown size or length never counts as fitting`() {
        // Work queued by an older build reports neither; the done card then shows the ordinary buttons.
        assertFalse(WhatsAppShare.fitsStatus(durationMs = 0, sizeBytes = 0))
        assertFalse(WhatsAppShare.fitsStatus(durationMs = 30_000, sizeBytes = 0))
        assertFalse(WhatsAppShare.tooBigForStatus(0))
    }

    @Test
    fun `the too-big hint starts just past 16 MB`() {
        assertFalse(WhatsAppShare.tooBigForStatus(WhatsAppShare.STATUS_MAX_BYTES))
        assertTrue(WhatsAppShare.tooBigForStatus(WhatsAppShare.STATUS_MAX_BYTES + 1))
    }
}
