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

/** Draws intro/outro/text cards and caption overlays for the recap video. */
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
        // accent rule
        paint.color = p.accent
        c.drawRoundRect(width * 0.1f, height * 0.56f, width * 0.1f + width * 0.08f, height * 0.56f + height * 0.012f, 8f, 8f, paint)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text; typeface = renderStyle.titleTypeface; textSize = height * (if (outro) 0.075f else 0.09f) }
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, (width * 0.8f).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save(); c.translate(width * 0.1f, height * 0.54f - titleLayout.height); titleLayout.draw(c); c.restore()
        if (subtitle.isNotBlank()) {
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text; alpha = 210; typeface = renderStyle.bodyTypeface; textSize = height * 0.04f }
            val subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, subPaint, (width * 0.8f).toInt()).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            c.save(); c.translate(width * 0.1f, height * 0.60f); subLayout.draw(c); c.restore()
        }
        return bmp
    }

    /** Transparent caption strip (title + date) sized for the bottom-left of a 1080p frame. */
    fun caption(theme: RecapTheme, title: String, date: String?, width: Int, height: Int): Bitmap {
        val p = palette(theme)
        val stripH = (height * 0.18f).toInt()
        val bmp = Bitmap.createBitmap(width, stripH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, stripH.toFloat(), Color.TRANSPARENT, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), stripH.toFloat(), paint)
        paint.shader = null
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = renderStyle.titleTypeface; textSize = height * 0.05f; setShadowLayer(6f, 0f, 2f, 0x80000000.toInt()) }
        val t = android.text.TextUtils.ellipsize(title, titlePaint, width * 0.8f, android.text.TextUtils.TruncateAt.END).toString()
        c.drawText(t, width * 0.06f, stripH * 0.62f, titlePaint)
        if (!date.isNullOrBlank()) {
            val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent; typeface = renderStyle.bodyTypeface; textSize = height * 0.03f; setShadowLayer(4f, 0f, 1f, 0x80000000.toInt()) }
            c.drawText(date, width * 0.06f, stripH * 0.88f, datePaint)
        }
        return bmp
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
