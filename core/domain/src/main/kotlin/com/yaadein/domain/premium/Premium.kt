package com.yaadein.domain.premium

/**
 * Features planned for the one-time "Yaadein Plus" unlock (docs/PREMIUM.md). Nothing is locked yet: [AllUnlocked] is
 * bound everywhere, and the GitHub (sideload) build will always stay fully unlocked. Screens ask the gate instead of
 * hard-coding, so turning the unlock on later is a binding change in the Play build only.
 */
enum class PremiumFeature { EXTRA_THEMES, EXTRA_MUSIC, LONG_REELS, PDF_BOOK, FAMILY_PACKS }

fun interface PremiumGate {
    fun isUnlocked(feature: PremiumFeature): Boolean
}

object AllUnlocked : PremiumGate {
    override fun isUnlocked(feature: PremiumFeature): Boolean = true
}
