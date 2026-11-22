package com.yaadein.data.recap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.yaadein.domain.recap.JourneyMark
import com.yaadein.domain.recap.JourneyRail
import com.yaadein.domain.recap.RecapTheme

/** Fonts and colours the renderer uses for cards and captions; supplied by the feature layer. */
data class RenderStyle(val titleTypeface: Typeface, val bodyTypeface: Typeface)

/** Draws intro/outro/text cards and caption overlays for the reel (any frame size). */
class CardPainter(private val renderStyle: RenderStyle) {

    private data class Palette(val bg1: Int, val bg2: Int, val text: Int, val accent: Int)

    private fun palette(theme: RecapTheme) = when (theme) {
        RecapTheme.WARM -> Palette(0xFF3B1A5C.toInt(), 0xFF7A3E9D.toInt(), 0xFFFBF5EC.toInt(), 0xFFE8A33D.toInt())
        RecapTheme.MINIMAL -> Palette(0xFF111111.toInt(), 0xFF2A2A2A.toInt(), 0xFFFAFAFA.toInt(), 0xFFBDBDBD.toInt())
        RecapTheme.FESTIVE -> Palette(0xFF8A0F23.toInt(), 0xFFE07B00.toInt(), 0xFFFFF8E7.toInt(), 0xFFFFD166.toInt())
        RecapTheme.CLASSIC_FILM -> Palette(0xFF1C1A17.toInt(), 0xFF3A342C.toInt(), 0xFFEDE3D1.toInt(), 0xFFC9A66B.toInt())
    }

    fun titleCard(theme: RecapTheme, title: String, subtitle: String, width: Int, height: Int, outro: Boolean = false): Bitmap {
        val p = palette(theme)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), p.bg1, p.bg2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        if (theme == RecapTheme.CLASSIC_FILM) vignette(c, width, height)
        if (theme == RecapTheme.FESTIVE) confetti(c, width, height, p.accent)
        // Sizes follow the short side so text looks the same in a 9:16 reel and a 16:9 video.
        val s = minOf(width, height).toFloat()
        // accent rule
        paint.color = p.accent
        c.drawRoundRect(width * 0.1f, height * 0.56f, width * 0.1f + s * 0.14f, height * 0.56f + s * 0.012f, 8f, 8f, paint)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text; typeface = renderStyle.titleTypeface; textSize = s * (if (outro) 0.085f else 0.10f) }
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, (width * 0.8f).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save(); c.translate(width * 0.1f, height * 0.54f - titleLayout.height); titleLayout.draw(c); c.restore()
        if (subtitle.isNotBlank()) {
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text; alpha = 210; typeface = renderStyle.bodyTypeface; textSize = s * 0.05f }
            val subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, subPaint, (width * 0.8f).toInt()).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            c.save(); c.translate(width * 0.1f, height * 0.56f + s * 0.05f); subLayout.draw(c); c.restore()
        }
        return bmp
    }

    /**
     * Transparent caption strip (title + date) for the bottom of the frame. In a portrait reel the text
     * sits above the bottom ~18 % that Instagram/YouTube cover with their own buttons and captions.
     */
    fun caption(theme: RecapTheme, title: String, date: String?, width: Int, height: Int): Bitmap {
        val p = palette(theme)
        val s = minOf(width, height).toFloat()
        val safeBottom = if (height > width) height * REEL_SAFE_BOTTOM else 0f
        val textBlock = s * 0.2f
        val stripH = (textBlock + safeBottom).toInt()
        val bmp = Bitmap.createBitmap(width, stripH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, stripH.toFloat(), Color.TRANSPARENT, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), stripH.toFloat(), paint)
        paint.shader = null
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = renderStyle.titleTypeface; textSize = s * 0.065f; setShadowLayer(6f, 0f, 2f, 0x80000000.toInt()) }
        val t = android.text.TextUtils.ellipsize(title, titlePaint, width * 0.88f, android.text.TextUtils.TruncateAt.END).toString()
        c.drawText(t, width * 0.06f, textBlock * 0.62f, titlePaint)
        if (!date.isNullOrBlank()) {
            val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent; typeface = renderStyle.bodyTypeface; textSize = s * 0.04f; setShadowLayer(4f, 0f, 1f, 0x80000000.toInt()) }
            c.drawText(date, width * 0.06f, textBlock * 0.88f, datePaint)
        }
        return bmp
    }

    /**
     * Transparent strip for the top of a journey reel's frame: a line with one dot per memory, filled up to the memory
     * on screen ([JourneyRail]). Sits below the top ~8 % that reel apps cover with their own header.
     */
    fun journeyRail(theme: RecapTheme, mark: JourneyMark, width: Int, height: Int): Bitmap {
        val p = palette(theme)
        val s = minOf(width, height).toFloat()
        val railH = s * 0.035f
        val top = height * REEL_SAFE_TOP
        val bmp = Bitmap.createBitmap(width, (top + railH * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // A soft shade behind the rail so it reads on a bright sky as well as on a dark room.
        paint.shader = LinearGradient(0f, 0f, 0f, bmp.height.toFloat(), 0x66000000, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), bmp.height.toFloat(), paint)
        paint.shader = null
        val rail = JourneyRail.layout(mark.step, mark.count, width.toFloat(), railH)
        val y = top + rail.y
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = rail.line
        paint.color = 0x99FFFFFF.toInt()
        c.drawLine(rail.startX, y, rail.endX, y, paint)
        paint.color = p.accent
        c.drawLine(rail.startX, y, rail.currentX, y, paint)
        rail.dots.forEachIndexed { i, x ->
            paint.color = if (i <= mark.step) p.accent else 0xDDFFFFFF.toInt()
            c.drawCircle(x, y, rail.dotRadius, paint)
        }
        paint.color = Color.WHITE
        c.drawCircle(rail.currentX, y, rail.currentRadius, paint)
        paint.color = p.accent
        c.drawCircle(rail.currentX, y, rail.currentRadius * 0.7f, paint)
        return bmp
    }

    /**
     * The closing card of a journey reel: the first photo in the top half and the last in the bottom half, each
     * cropped to fill, with "Then · date" / "Now · date" labels and [title] on the line between them.
     */
    fun thenNowCard(theme: RecapTheme, title: String, then: Bitmap, now: Bitmap, thenLabel: String, nowLabel: String, width: Int, height: Int): Bitmap {
        val p = palette(theme)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = minOf(width, height).toFloat()
        val half = height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun cover(photo: Bitmap, top: Float) {
            val scale = maxOf(width.toFloat() / photo.width, half / photo.height)
            val w = photo.width * scale; val h = photo.height * scale
            c.save()
            c.clipRect(0f, top, width.toFloat(), top + half)
            // People are usually in the upper part of a photo: keep a little more of the top than of the bottom.
            val dy = top + (half - h) * 0.35f
            c.drawBitmap(photo, null, android.graphics.RectF((width - w) / 2f, dy, (width + w) / 2f, dy + h), paint)
            c.restore()
        }
        cover(then, 0f)
        cover(now, half)
        fun label(text: String, top: Float) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = renderStyle.bodyTypeface; textSize = s * 0.045f }
            val shown = android.text.TextUtils.ellipsize(text, textPaint, width * 0.8f, android.text.TextUtils.TruncateAt.END).toString()
            val padX = s * 0.03f; val boxH = s * 0.085f
            val left = width * 0.06f; val boxTop = top + s * 0.05f
            paint.color = 0x99000000.toInt()
            c.drawRoundRect(left, boxTop, left + textPaint.measureText(shown) + padX * 2, boxTop + boxH, boxH / 2, boxH / 2, paint)
            c.drawText(shown, left + padX, boxTop + boxH * 0.68f, textPaint)
        }
        // The top label clears the reel apps' header; the bottom one starts right under the dividing band.
        val bandH = if (title.isBlank()) s * 0.012f else s * 0.11f
        label(thenLabel, height * REEL_SAFE_TOP)
        label(nowLabel, half + bandH / 2)
        paint.color = p.bg1
        c.drawRect(0f, half - bandH / 2, width.toFloat(), half + bandH / 2, paint)
        if (title.isNotBlank()) {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text; typeface = renderStyle.titleTypeface; textSize = s * 0.055f; textAlign = Paint.Align.CENTER }
            val shown = android.text.TextUtils.ellipsize(title, titlePaint, width * 0.88f, android.text.TextUtils.TruncateAt.END).toString()
            c.drawText(shown, width / 2f, half + titlePaint.textSize * 0.35f, titlePaint)
        }
        return bmp
    }

    private companion object {
        /** Top share of a 9:16 frame that reel apps cover with their own header. */
        const val REEL_SAFE_TOP = 0.08f
        /** Bottom share of a 9:16 frame that reel apps cover with their own UI. */
        const val REEL_SAFE_BOTTOM = 0.18f
    }

    private fun vignette(c: Canvas, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.RadialGradient(w / 2f, h / 2f, w * 0.75f, intArrayOf(Color.TRANSPARENT, 0x99000000.toInt()), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun confetti(c: Canvas, w: Int, h: Int, accent: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rnd = java.util.Random(42)
        repeat(60) {
            paint.color = if (rnd.nextBoolean()) accent else Color.WHITE
            paint.alpha = 60 + rnd.nextInt(120)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, h * (0.004f + rnd.nextFloat() * 0.01f), paint)
        }
    }
}
