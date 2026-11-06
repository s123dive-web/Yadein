package com.yaadein.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateDecisionTest {
    private val key = setOf("aa11")
    private val installed = AppVersion("com.yaadein.app.debug", 30, "0.1.30", key)

    @Test
    fun `newer build with the same key installs`() {
        assertEquals(UpdateDecision.Install("0.1.31", 31), UpdateDecision.decide(installed, installed.copy(versionCode = 31, versionName = "0.1.31")))
    }

    @Test
    fun `same build is already up to date`() {
        assertEquals(UpdateDecision.AlreadyUpToDate("0.1.30", "0.1.30", fileIsOlder = false), UpdateDecision.decide(installed, installed))
    }

    @Test
    fun `older build says already up to date and that the file is older`() {
        assertEquals(
            UpdateDecision.AlreadyUpToDate("0.1.30", "0.1.12", fileIsOlder = true),
            UpdateDecision.decide(installed, installed.copy(versionCode = 12, versionName = "0.1.12")),
        )
    }

    @Test
    fun `another app or the release edition is refused`() {
        assertEquals(UpdateDecision.NotThisApp, UpdateDecision.decide(installed, installed.copy(packageName = "com.yaadein.app", versionCode = 99)))
    }

    @Test
    fun `a different signing key is refused so data is never put at risk`() {
        assertEquals(UpdateDecision.DifferentSignature, UpdateDecision.decide(installed, installed.copy(versionCode = 31, signers = setOf("bb22"))))
    }

    @Test
    fun `unknown signers on the file are left to the system installer`() {
        assertEquals(UpdateDecision.Install("0.1.31", 31), UpdateDecision.decide(installed, installed.copy(versionCode = 31, versionName = "0.1.31", signers = emptySet())))
    }

    @Test
    fun `an unreadable file is reported`() {
        assertEquals(UpdateDecision.Unreadable, UpdateDecision.decide(installed, null))
    }
}
