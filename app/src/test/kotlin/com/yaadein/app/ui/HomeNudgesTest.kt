package com.yaadein.app.ui

import com.yaadein.app.navigation.Route
import com.yaadein.app.navigation.startRoute
import com.yaadein.domain.settings.UserSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What Home offers after a photos-first start, and that people who were already set up see none of it. */
class HomeNudgesTest {
    private val fresh = UserSettings(onboardingDone = true)
    private val existing = UserSettings(onboardingDone = true, pinConfigured = true)

    private fun decide(s: UserSettings, memories: Int = 3, photos: Int = 10, people: Int = 0, unknownFaces: Int = 0) =
        HomeNudges.decide(s, memories, photos, people, unknownFaces)

    @Test
    fun `an existing user with a lock and people sees nothing new`() {
        val state = decide(existing, memories = 120, photos = 900, people = 8, unknownFaces = 4)
        assertFalse(state.lockPrompt)
        assertNull(state.card)
        assertEquals(Route.Home, startRoute(existing.onboardingDone))
    }

    @Test
    fun `only a first run starts on the welcome screen, and nobody is sent to lock setup`() {
        assertEquals(Route.Onboarding, startRoute(onboardingDone = false))
        // Onboarded without a lock (new flow, or an install that stopped before choosing a PIN): Home, never forced setup.
        assertEquals(Route.Home, startRoute(onboardingDone = true))
    }

    @Test
    fun `nothing is offered before the first memory or before onboarding`() {
        assertEquals(HomeNudgesUiState(), decide(fresh, memories = 0, photos = 0))
        assertEquals(HomeNudgesUiState(), decide(UserSettings(), memories = 0, photos = 0))
        // Counts not known yet (-1) must not flash a prompt.
        assertEquals(HomeNudgesUiState(), decide(fresh, memories = -1, photos = 0))
    }

    @Test
    fun `the first real memory asks once to protect it, then leaves a card until a lock is set`() {
        val first = decide(fresh)
        assertTrue(first.lockPrompt)
        // The sheet is up: the card underneath is about people, not a second lock message.
        assertEquals(HomeCard.OfferFaces, first.card)

        val postponed = decide(fresh.copy(lockPromptPostponed = true))
        assertFalse(postponed.lockPrompt)
        assertEquals(HomeCard.Lock, postponed.card)

        val locked = decide(fresh.copy(lockPromptPostponed = true, pinConfigured = true))
        assertFalse(locked.lockPrompt)
        assertEquals(HomeCard.OfferFaces, locked.card)
    }

    @Test
    fun `the sample family is labelled and never asks for a lock or for people`() {
        val state = decide(fresh.copy(demoMode = true), memories = 32, photos = 60, people = 10)
        assertEquals(HomeCard.Sample, state.card)
        assertFalse(state.lockPrompt)
        // Even with no memories left the banner stays until the sample is cleared.
        assertEquals(HomeCard.Sample, decide(fresh.copy(demoMode = true), memories = 0, photos = 0).card)
    }

    @Test
    fun `people come from faces when there are photos, otherwise from a gentle add card`() {
        val locked = existing
        assertEquals(HomeCard.OfferFaces, decide(locked).card)
        assertEquals(HomeCard.AddSomeone, decide(locked, photos = 0).card)
        // Faces on: name the ones found; while none are found yet, the add card.
        assertEquals(HomeCard.NameFaces(4), decide(locked.copy(faceSuggestions = true), unknownFaces = 4).card)
        assertEquals(HomeCard.AddSomeone, decide(locked.copy(faceSuggestions = true), unknownFaces = 0).card)
        // Once someone exists, or the card was closed, it is gone.
        assertNull(decide(locked, people = 1).card)
        assertNull(decide(locked.copy(peopleNudgeDismissed = true)).card)
    }
}
