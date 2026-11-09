package com.yaadein.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design tokens for Yaadein. Nothing in the UI hardcodes a size, radius, elevation or duration;
 * everything references these objects so Easy mode and future tuning stay in one place.
 */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Horizontal page gutter. */
    val gutter: Dp = 20.dp,
)

@Immutable
data class Radius(
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val pill: Dp = 999.dp,
)

@Immutable
data class Elevation(
    val none: Dp = 0.dp,
    val low: Dp = 1.dp,
    val card: Dp = 2.dp,
    val raised: Dp = 4.dp,
    val modal: Dp = 8.dp,
)

@Immutable
data class Motion(
    val fastMs: Int = 120,
    val normalMs: Int = 220,
    val slowMs: Int = 360,
    val emphasizedMs: Int = 480,
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val emphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val exitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
)

@Immutable
data class Sizes(
    /** Minimum touch target (Material + accessibility). */
    val touchTarget: Dp = 48.dp,
    val avatarSm: Dp = 28.dp,
    val avatarMd: Dp = 40.dp,
    val avatarLg: Dp = 64.dp,
    val avatarXl: Dp = 96.dp,
    val fabSize: Dp = 64.dp,
    val cardCoverHeight: Dp = 200.dp,
    val cardCoverHeightCompact: Dp = 150.dp,
    val timelineRail: Dp = 2.dp,
    val timelineDot: Dp = 10.dp,
    val iconSm: Dp = 18.dp,
    val icon: Dp = 24.dp,
    val iconLg: Dp = 32.dp,
    val illustration: Dp = 200.dp,
    val bottomBarHeight: Dp = 80.dp,
    val twoPaneSidebarWidth: Dp = 360.dp,
)

/** Sizes when Easy mode is on: bigger targets and simpler layouts for all ages. */
val EasyModeSizes = Sizes(
    touchTarget = 56.dp,
    avatarSm = 32.dp,
    avatarMd = 48.dp,
    avatarLg = 72.dp,
    avatarXl = 112.dp,
    fabSize = 72.dp,
    cardCoverHeight = 220.dp,
    cardCoverHeightCompact = 170.dp,
)

/** Text scale multiplier applied on top of the system font scale in Easy mode. */
const val EASY_MODE_TEXT_SCALE = 1.3f

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadius = staticCompositionLocalOf { Radius() }
val LocalElevation = staticCompositionLocalOf { Elevation() }
val LocalMotion = staticCompositionLocalOf { Motion() }
val LocalSizes = staticCompositionLocalOf { Sizes() }
val LocalEasyMode = staticCompositionLocalOf { false }
