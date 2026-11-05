package com.yaadein.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.CoverStyle
import kotlin.math.min

/**
 * Procedural cover for text-only memories: a themed gradient plus a soft illustration chosen by the
 * cover-style key (beach, wedding, diwali, ...). Deterministic, no assets, looks good at any size.
 */
@Composable
fun CoverArt(style: String, modifier: Modifier = Modifier, seed: Long = 0L) {
    val gradients = Yaadein.colors.gradients
    val (c1, c2) = gradients[style] ?: gradients[CoverStyle.DEFAULT] ?: (Color(0xFF5B2A86) to Color(0xFF8B5CF6))
    val brush = Brush.linearGradient(listOf(c1, c2), start = Offset.Zero, end = Offset.Infinite)
    Box(modifier = modifier.background(brush)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIllustration(style, seed)
        }
    }
}

private fun DrawScope.drawIllustration(style: String, seed: Long) {
    val w = size.width
    val h = size.height
    val ink = Color.White.copy(alpha = 0.22f)
    val inkSoft = Color.White.copy(alpha = 0.12f)
    when (style) {
        "beach" -> {
            // sun + waves
            drawCircle(ink, radius = min(w, h) * 0.16f, center = Offset(w * 0.78f, h * 0.3f))
            repeat(3) { i ->
                val y = h * (0.62f + i * 0.12f)
                val path = Path()
                path.moveTo(0f, y)
                var x = 0f
                var up = true
                while (x < w) {
                    path.quadraticTo(x + w * 0.08f, if (up) y - h * 0.05f else y + h * 0.05f, x + w * 0.16f, y)
                    x += w * 0.16f
                    up = !up
                }
                drawPath(path, inkSoft, style = Stroke(width = h * 0.02f))
            }
        }
        "mountain", "winter" -> {
            val path = Path().apply {
                moveTo(0f, h); lineTo(w * 0.3f, h * 0.35f); lineTo(w * 0.5f, h * 0.7f)
                lineTo(w * 0.68f, h * 0.25f); lineTo(w, h); close()
            }
            drawPath(path, inkSoft)
            drawCircle(ink, radius = min(w, h) * 0.1f, center = Offset(w * 0.2f, h * 0.25f))
        }
        "travel" -> {
            val path = Path().apply {
                moveTo(w * 0.1f, h * 0.8f)
                cubicTo(w * 0.3f, h * 0.2f, w * 0.6f, h * 0.9f, w * 0.9f, h * 0.3f)
            }
            drawPath(path, ink, style = Stroke(width = h * 0.025f))
            listOf(0.1f to 0.8f, 0.5f to 0.55f, 0.9f to 0.3f).forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.5f), radius = h * 0.04f, center = Offset(w * x, h * y))
            }
        }
        "wedding", "baby" -> {
            drawHeart(Offset(w * 0.72f, h * 0.42f), min(w, h) * 0.3f, ink)
            drawHeart(Offset(w * 0.28f, h * 0.6f), min(w, h) * 0.18f, inkSoft)
        }
        "birthday", "festival", "holi" -> {
            // confetti
            var s = seed * 31 + 7
            repeat(28) {
                s = (s * 1103515245 + 12345) and 0x7fffffff
                val x = (s % 1000) / 1000f * w
                s = (s * 1103515245 + 12345) and 0x7fffffff
                val y = (s % 1000) / 1000f * h
                s = (s * 1103515245 + 12345) and 0x7fffffff
                val r = h * (0.015f + (s % 100) / 100f * 0.03f)
                drawCircle(Color.White.copy(alpha = 0.2f + (s % 40) / 100f), radius = r, center = Offset(x, y))
            }
        }
        "diwali" -> {
            // diya flames along the bottom
            repeat(5) { i ->
                val x = w * (0.15f + i * 0.175f)
                val base = h * 0.78f
                drawArc(inkSoft, 0f, 180f, true, topLeft = Offset(x - h * 0.08f, base - h * 0.08f), size = androidx.compose.ui.geometry.Size(h * 0.16f, h * 0.16f))
                val flame = Path().apply {
                    moveTo(x, base - h * 0.28f)
                    quadraticTo(x + h * 0.07f, base - h * 0.14f, x, base - h * 0.06f)
                    quadraticTo(x - h * 0.07f, base - h * 0.14f, x, base - h * 0.28f)
                }
                drawPath(flame, Color.White.copy(alpha = 0.45f))
            }
        }
        "school", "college" -> {
            // open book
            val path = Path().apply {
                moveTo(w * 0.25f, h * 0.35f); quadraticTo(w * 0.5f, h * 0.45f, w * 0.75f, h * 0.35f)
                lineTo(w * 0.75f, h * 0.75f); quadraticTo(w * 0.5f, h * 0.85f, w * 0.25f, h * 0.75f); close()
            }
            drawPath(path, inkSoft)
            drawLine(ink, Offset(w * 0.5f, h * 0.43f), Offset(w * 0.5f, h * 0.83f), strokeWidth = h * 0.015f)
        }
        "home" -> {
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.2f); lineTo(w * 0.8f, h * 0.5f); lineTo(w * 0.72f, h * 0.5f)
                lineTo(w * 0.72f, h * 0.82f); lineTo(w * 0.28f, h * 0.82f); lineTo(w * 0.28f, h * 0.5f); lineTo(w * 0.2f, h * 0.5f); close()
            }
            drawPath(path, inkSoft)
        }
        "food" -> {
            drawCircle(inkSoft, radius = min(w, h) * 0.3f, center = Offset(w * 0.5f, h * 0.55f))
            drawCircle(ink, radius = min(w, h) * 0.2f, center = Offset(w * 0.5f, h * 0.55f))
        }
        "music" -> {
            repeat(4) { i ->
                val x = w * (0.2f + i * 0.2f)
                drawCircle(ink, radius = h * 0.05f, center = Offset(x, h * (0.6f + (i % 2) * 0.1f)))
                drawLine(ink, Offset(x + h * 0.045f, h * (0.6f + (i % 2) * 0.1f)), Offset(x + h * 0.045f, h * 0.3f), strokeWidth = h * 0.015f)
            }
        }
        "sports" -> {
            drawCircle(ink, radius = min(w, h) * 0.22f, center = Offset(w * 0.6f, h * 0.5f), style = Stroke(width = h * 0.02f))
            drawLine(ink, Offset(w * 0.15f, h * 0.8f), Offset(w * 0.9f, h * 0.8f), strokeWidth = h * 0.02f)
        }
        "rain" -> {
            var s = seed * 17 + 3
            repeat(24) {
                s = (s * 1103515245 + 12345) and 0x7fffffff
                val x = (s % 1000) / 1000f * w
                s = (s * 1103515245 + 12345) and 0x7fffffff
                val y = (s % 1000) / 1000f * h
                drawLine(inkSoft, Offset(x, y), Offset(x - h * 0.04f, y + h * 0.1f), strokeWidth = h * 0.012f)
            }
        }
        else -> {
            // soft orbs
            drawCircle(inkSoft, radius = min(w, h) * 0.45f, center = Offset(w * 0.85f, h * 0.2f))
            drawCircle(inkSoft, radius = min(w, h) * 0.3f, center = Offset(w * 0.15f, h * 0.9f))
        }
    }
}

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.45f)
        cubicTo(center.x - size * 0.9f, center.y - size * 0.2f, center.x - size * 0.4f, center.y - size * 0.8f, center.x, center.y - size * 0.3f)
        cubicTo(center.x + size * 0.4f, center.y - size * 0.8f, center.x + size * 0.9f, center.y - size * 0.2f, center.x, center.y + size * 0.45f)
        close()
    }
    drawPath(path, color)
}
