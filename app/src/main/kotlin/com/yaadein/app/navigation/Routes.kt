package com.yaadein.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Keep in sync with the navigation map in CLAUDE.md §6. */
sealed interface Route {
    @Serializable data object Onboarding : Route
    @Serializable data object LockSetup : Route
    @Serializable data object Home : Route
    @Serializable data class MemoryDetail(val memoryId: Long) : Route
    @Serializable data class MediaViewer(val memoryId: Long, val index: Int) : Route
    @Serializable data class PersonDetail(val personId: Long) : Route
    @Serializable data class PersonEdit(val personId: Long = 0L, val fromOnboarding: Boolean = false) : Route
    @Serializable data class GroupEdit(val groupId: Long = 0L) : Route
    @Serializable data class Together(val personId: Long = 0L) : Route
    @Serializable data object RelationshipsMap : Route
    @Serializable data class YearlySummary(val personId: Long, val year: Int) : Route
    @Serializable data object CaptureHub : Route
    /** [fromShare]: attach the photos/videos waiting in SharedMediaInbox (Share → Yaadein). */
    @Serializable data class EventCapture(val memoryId: Long = 0L, val fromShare: Boolean = false) : Route
    @Serializable data object PhotoFirst : Route
    @Serializable data object TextOnly : Route
    @Serializable data object QuickCapture : Route
    @Serializable data object PastWizard : Route
    @Serializable data class RecapSetup(val personId: Long = 0L, val memoryId: Long = 0L, val year: Int = 0) : Route
    /** Reels kept in the app: three made automatically plus the user's last ten. */
    @Serializable data object Reels : Route
    @Serializable data class ReelPlayer(val reelId: String) : Route
    @Serializable data class Settings(val page: String) : Route
}

enum class HomeTab { TIMELINE, PEOPLE, CREATE, RELIVE, SETTINGS }
