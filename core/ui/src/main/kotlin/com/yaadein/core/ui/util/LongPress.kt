package com.yaadein.core.ui.util

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a Material `Card(onClick)` / `Surface(onClick)` needs to also answer a long press: see [rememberLongPress]. */
class LongPress(val interactionSource: MutableInteractionSource, val onClick: () -> Unit)

/**
 * Long press for components that only take `onClick` (Material's clickable Card and Surface). It watches the
 * component's own press state: a press held for the system's long-press time calls [onLongClick] (with a haptic), and
 * the click that follows when the finger lifts is swallowed. A scroll cancels the press, so it never fires mid-scroll.
 * Pass the returned `interactionSource` and `onClick` to the component. With [onLongClick] null it is a plain click.
 */
@Composable
fun rememberLongPress(onClick: () -> Unit, onLongClick: (() -> Unit)?): LongPress {
    val interactions = remember { MutableInteractionSource() }
    var fired by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val haptics = LocalHapticFeedback.current
    val enabled = onLongClick != null
    LaunchedEffect(interactions, enabled, timeout) {
        if (!enabled) return@LaunchedEffect
        var timer: Job? = null
        interactions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    fired = false
                    timer?.cancel()
                    timer = launch {
                        delay(timeout)
                        fired = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick?.invoke()
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> timer?.cancel()
            }
        }
    }
    return remember(interactions) { LongPress(interactions) { if (fired) fired = false else click() } }
}
