package com.yaadein.core.ui.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.yaadein.core.ui.theme.Yaadein

/**
 * Kid-friendly vector illustrations drawn in theme colours, used by empty states and onboarding.
 * Drawn procedurally so they recolour with every theme and need no bitmap assets.
 */
enum class IllustrationKind {
    PHOTO_STACK,      // a tilted stack of polaroids with a heart
    PEOPLE_CIRCLE,    // three smiling faces in a circle
    KITE,             // a kite in the sky (relive / on this day)
    SEARCH_GLASS,     // magnifying glass over a small photo
    FILM_REEL,        // recap video
    LOCK_HEART,       // security
    CALENDAR_STAR,    // fill in the past
    BOX_CLOSED,       // nothing here yet
}

@Composable
fun Illustration(kind: IllustrationKind, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val container = MaterialTheme.colorScheme.primaryContainer
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val avatars = Yaadein.colors.avatarPalette
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h)
        // soft backdrop blob
        drawCircle(container.copy(alpha = 0.6f), radius = s * 0.42f, center = Offset(w / 2, h / 2))
        when (kind) {
            IllustrationKind.PHOTO_STACK -> {
                polaroid(Offset(w * 0.32f, h * 0.36f), s * 0.42f, -12f, surface, avatars[2], onSurface)
                polaroid(Offset(w * 0.5f, h * 0.5f), s * 0.42f, 6f, surface, avatars[1], onSurface)
                heart(Offset(w * 0.72f, h * 0.3f), s * 0.11f, secondary)
            }
            IllustrationKind.PEOPLE_CIRCLE -> {
                face(Offset(w * 0.5f, h * 0.36f), s * 0.14f, avatars[0], surface)
                face(Offset(w * 0.34f, h * 0.6f), s * 0.14f, avatars[1], surface)
                face(Offset(w * 0.66f, h * 0.6f), s * 0.14f, avatars[2], surface)
                heart(Offset(w * 0.5f, h * 0.6f), s * 0.07f, secondary)
            }
            IllustrationKind.KITE -> {
                val kite = Path().apply {
                    moveTo(w * 0.55f, h * 0.2f); lineTo(w * 0.72f, h * 0.42f); lineTo(w * 0.55f, h * 0.7f); lineTo(w * 0.38f, h * 0.42f); close()
                }
                drawPath(kite, primary)
                drawLine(surface, Offset(w * 0.55f, h * 0.2f), Offset(w * 0.55f, h * 0.7f), strokeWidth = s * 0.012f)
                drawLine(surface, Offset(w * 0.38f, h * 0.42f), Offset(w * 0.72f, h * 0.42f), strokeWidth = s * 0.012f)
                val tail = Path().apply {
                    moveTo(w * 0.55f, h * 0.7f)
                    cubicTo(w * 0.45f, h * 0.8f, w * 0.5f, h * 0.9f, w * 0.38f, h * 0.95f)
                }
                drawPath(tail, secondary, style = Stroke(width = s * 0.015f))
                listOf(0.5f to 0.78f, 0.47f to 0.87f).forEach { (x, y) -> bow(Offset(w * x, h * y), s * 0.035f, tertiary) }
            }
            IllustrationKind.SEARCH_GLASS -> {
                polaroid(Offset(w * 0.42f, h * 0.5f), s * 0.4f, -6f, surface, avatars[3], onSurface)
                drawCircle(primary, radius = s * 0.14f, center = Offset(w * 0.62f, h * 0.42f), style = Stroke(width = s * 0.035f))
                drawLine(primary, Offset(w * 0.72f, h * 0.53f), Offset(w * 0.84f, h * 0.68f), strokeWidth = s * 0.05f)
            }
            IllustrationKind.FILM_REEL -> {
                drawCircle(primary, radius = s * 0.26f, center = Offset(w * 0.5f, h * 0.48f))
                drawCircle(surface, radius = s * 0.06f, center = Offset(w * 0.5f, h * 0.48f))
                repeat(5) { i ->
                    val a = Math.toRadians((i * 72 - 90).toDouble())
                    val c = Offset(w * 0.5f + (s * 0.16f * Math.cos(a)).toFloat(), h * 0.48f + (s * 0.16f * Math.sin(a)).toFloat())
                    drawCircle(surface, radius = s * 0.045f, center = c)
                }
                val strip = Path().apply {
                    moveTo(w * 0.6f, h * 0.7f); quadraticTo(w * 0.8f, h * 0.8f, w * 0.85f, h * 0.95f)
                }
                drawPath(strip, secondary, style = Stroke(width = s * 0.05f))
            }
            IllustrationKind.LOCK_HEART -> {
                drawRoundRect(primary, topLeft = Offset(w * 0.32f, h * 0.45f), size = Size(w * 0.36f, h * 0.32f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.06f))
                drawArc(primary, 180f, 180f, false, topLeft = Offset(w * 0.38f, h * 0.24f), size = Size(w * 0.24f, h * 0.36f), style = Stroke(width = s * 0.05f))
                heart(Offset(w * 0.5f, h * 0.61f), s * 0.07f, secondary)
            }
            IllustrationKind.CALENDAR_STAR -> {
                drawRoundRect(surface, topLeft = Offset(w * 0.28f, h * 0.3f), size = Size(w * 0.44f, h * 0.44f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.04f))
                drawRoundRect(primary, topLeft = Offset(w * 0.28f, h * 0.3f), size = Size(w * 0.44f, h * 0.12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.04f))
                for (r in 0 until 3) for (c in 0 until 4) {
                    drawCircle(onSurface.copy(alpha = 0.25f), radius = s * 0.015f, center = Offset(w * (0.34f + c * 0.107f), h * (0.5f + r * 0.08f)))
                }
                star(Offset(w * 0.66f, h * 0.66f), s * 0.09f, secondary)
            }
            IllustrationKind.BOX_CLOSED -> {
                drawRoundRect(primary.copy(alpha = 0.85f), topLeft = Offset(w * 0.3f, h * 0.45f), size = Size(w * 0.4f, h * 0.3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f))
                drawRoundRect(primary, topLeft = Offset(w * 0.26f, h * 0.38f), size = Size(w * 0.48f, h * 0.12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f))
                drawLine(surface, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.75f), strokeWidth = s * 0.02f)
                heart(Offset(w * 0.5f, h * 0.3f), s * 0.06f, secondary)
            }
        }
    }
}

private fun DrawScope.polaroid(center: Offset, size: Float, degrees: Float, paper: Color, photo: Color, ink: Color) {
    rotate(degrees, pivot = center) {
        val top = Offset(center.x - size / 2, center.y - size / 2)
        drawRoundRect(ink.copy(alpha = 0.12f), topLeft = top + Offset(size * 0.02f, size * 0.04f), size = Size(size, size * 1.15f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.04f))
        drawRoundRect(paper, topLeft = top, size = Size(size, size * 1.15f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.04f))
        drawRect(photo.copy(alpha = 0.85f), topLeft = top + Offset(size * 0.08f, size * 0.08f), size = Size(size * 0.84f, size * 0.78f))
        // tiny sun + hills in the photo
        drawCircle(paper.copy(alpha = 0.8f), radius = size * 0.08f, center = top + Offset(size * 0.7f, size * 0.3f))
        val hills = Path().apply {
            moveTo(top.x + size * 0.08f, top.y + size * 0.86f)
            quadraticTo(top.x + size * 0.3f, top.y + size * 0.5f, top.x + size * 0.5f, top.y + size * 0.78f)
            quadraticTo(top.x + size * 0.7f, top.y + size * 0.55f, top.x + size * 0.92f, top.y + size * 0.86f)
            close()
        }
        drawPath(hills, paper.copy(alpha = 0.55f))
    }
}

private fun DrawScope.face(center: Offset, r: Float, skin: Color, ink: Color) {
    drawCircle(skin, radius = r, center = center)
    drawCircle(ink, radius = r * 0.09f, center = center + Offset(-r * 0.3f, -r * 0.15f))
    drawCircle(ink, radius = r * 0.09f, center = center + Offset(r * 0.3f, -r * 0.15f))
    drawArc(ink, 20f, 140f, false, topLeft = center + Offset(-r * 0.4f, -r * 0.1f), size = Size(r * 0.8f, r * 0.7f), style = Stroke(width = r * 0.1f))
}

private fun DrawScope.heart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.9f)
        cubicTo(center.x - size * 1.8f, center.y - size * 0.4f, center.x - size * 0.8f, center.y - size * 1.6f, center.x, center.y - size * 0.6f)
        cubicTo(center.x + size * 0.8f, center.y - size * 1.6f, center.x + size * 1.8f, center.y - size * 0.4f, center.x, center.y + size * 0.9f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.bow(center: Offset, size: Float, color: Color) {
    translate(center.x, center.y) {
        val p = Path().apply {
            moveTo(0f, 0f); lineTo(-size, -size * 0.6f); lineTo(-size, size * 0.6f); close()
            moveTo(0f, 0f); lineTo(size, -size * 0.6f); lineTo(size, size * 0.6f); close()
        }
        drawPath(p, color)
    }
}

private fun DrawScope.star(center: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians((i * 36 - 90).toDouble())
        val pt = Offset(center.x + (radius * Math.cos(a)).toFloat(), center.y + (radius * Math.sin(a)).toFloat())
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    drawPath(p, color)
}
