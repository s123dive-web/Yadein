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
    /** "Who is this?": name groups of unknown faces, one group at a time. */
    @Serializable data object WhoIsThis : Route
    @Serializable data class YearlySummary(val personId: Long, val year: Int) : Route
    @Serializable data object CaptureHub : Route
    /**
     * Quick Save: the + button's photos (handed over through QuickSaveInbox) or, with [fromShare], the files waiting in
     * SharedMediaInbox; [wordsOnly] when the picker came back empty ("No photos? Write it instead").
     */
    @Serializable data class QuickSave(val fromShare: Boolean = false, val wordsOnly: Boolean = false) : Route
    /** [fromShare]: attach the photos/videos waiting in SharedMediaInbox (Share → Yaadein). */
    @Serializable data class EventCapture(val memoryId: Long = 0L, val fromShare: Boolean = false) : Route
    @Serializable data object PhotoFirst : Route
    /** Bulk upload: one memory per year or month of the picked photos. */
    @Serializable data object BulkUpload : Route
    @Serializable data object TextOnly : Route
    @Serializable data object QuickCapture : Route
    @Serializable data object PastWizard : Route
    /** Record a voice memory: new ([memoryId] 0) or attached to an existing memory; [prompt] from the weekly story prompt. */
    /** Google Photos Takeout / WhatsApp chat export zip. */
    @Serializable data object ImportExport : Route
    @Serializable data class VoiceCapture(val memoryId: Long = 0L, val prompt: String = "", val personId: Long = 0L) : Route
    @Serializable data class RecapSetup(val personId: Long = 0L, val memoryId: Long = 0L, val year: Int = 0) : Route
    /** Reels kept in the app: three made automatically plus the user's last ten. */
    @Serializable data object Reels : Route
    @Serializable data class ReelPlayer(val reelId: String) : Route
    @Serializable data class Settings(val page: String) : Route
}

enum class HomeTab { TIMELINE, PEOPLE, CREATE, RELIVE, SETTINGS }
