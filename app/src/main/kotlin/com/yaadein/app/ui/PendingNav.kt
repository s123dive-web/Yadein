package com.yaadein.app.ui

import android.content.Intent
import com.yaadein.feature.relive.notify.OnThisDayWorker

/** A destination requested by a reminder notification, opened once the vault is unlocked. */
sealed interface PendingNav {
    data class Memory(val id: Long) : PendingNav
    /** Birthday reminder: "Create a reel" for this person. */
    data class Reel(val personId: Long) : PendingNav
    /** Weekly story prompt: add a memory answering [text], about [personId] (0 = the user). */
    data class Prompt(val text: String, val personId: Long) : PendingNav
    /** Widget + button: "Today, in one line". */
    data object QuickAdd : PendingNav

    companion object {
        fun from(intent: Intent?): PendingNav? {
            intent ?: return null
            intent.getLongExtra(OnThisDayWorker.EXTRA_MEMORY_ID, -1L).takeIf { it > 0 }?.let { return Memory(it) }
            intent.getLongExtra(OnThisDayWorker.EXTRA_REEL_PERSON_ID, -1L).takeIf { it > 0 }?.let { return Reel(it) }
            if (intent.getBooleanExtra(com.yaadein.app.widget.RemindersWidget.EXTRA_QUICK_ADD, false)) return QuickAdd
            intent.getStringExtra(OnThisDayWorker.EXTRA_PROMPT)?.let { return Prompt(it, intent.getLongExtra(OnThisDayWorker.EXTRA_PROMPT_PERSON_ID, 0L)) }
            return null
        }
    }
}
