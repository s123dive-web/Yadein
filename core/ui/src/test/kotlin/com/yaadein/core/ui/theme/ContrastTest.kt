package com.yaadein.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.1 contrast checks for every bundled palette, light and dark. */
class ContrastTest {
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double { val s = v.toDouble(); return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun ratio(a: Color, b: Color): Double { val la = luminance(a); val lb = luminance(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05) }

    private fun check(name: String, scheme: ColorScheme) {
        val pairs = listOf(
            "onSurface/surface" to (scheme.onSurface to scheme.surface),
            "onBackground/background" to (scheme.onBackground to scheme.background),
            "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
            "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
            "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
            "primary/surface" to (scheme.primary to scheme.surface),
            "onErrorContainer/errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
        )
        pairs.forEach { (label, p) ->
            val r = ratio(p.first, p.second)
            assertTrue(r >= 4.5, "$name $label contrast ${"%.2f".format(r)} < 4.5")
        }
    }

    @Test
    fun `all palettes meet 4_5 to 1 for text roles`() {
        listOf("WarmAlbum" to WarmAlbum, "Midnight" to Midnight, "Monsoon" to Monsoon, "Festive" to Festive, "MinimalMono" to MinimalMono).forEach { (n, p) ->
            check("$n light", p.light); check("$n dark", p.dark)
        }
    }
}
