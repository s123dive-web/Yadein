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

    private companion object {
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
