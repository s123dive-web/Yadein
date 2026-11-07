package com.yaadein.data.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.yaadein.data.media.MediaStore
import com.yaadein.data.recap.RenderStyle
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.MemoryDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max

/**
 * Turns memories into things people can send without Yaadein: a picture card (photo, title, date, words) for WhatsApp,
 * and a printable PDF book. Everything is drawn on the phone; the photo is decrypted in memory only and the file lands
 * in `cache/exports/` (shared through the FileProvider) or a document the user picked.
 */
class MemoryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: MediaStore,
) {
    data class Labels(val dateText: String, val footer: String)

    /** Renders [detail] as a 1080×1350 card and returns a share-sheet intent for it. */
    suspend fun shareCard(detail: MemoryDetail, labels: Labels, style: RenderStyle, chooserTitle: String): Intent = withContext(Dispatchers.IO) {
        val bitmap = card(detail, labels, style)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("card-") }?.forEach { it.delete() }
        val file = File(dir, "card-${detail.memory.id}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, detail.memory.title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(send, chooserTitle)
    }

    fun card(detail: MemoryDetail, labels: Labels, style: RenderStyle): Bitmap {
        val w = CARD_W; val h = CARD_H
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(CREAM)
        val photoH = (h * 0.62f).toInt()
        val photo = coverPhoto(detail, w)
        if (photo != null) {
            drawCover(c, photo, Rect(0, 0, w, photoH))
            photo.recycle()
        } else {
            val p = Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), photoH.toFloat(), PLUM, AMBER, android.graphics.Shader.TileMode.CLAMP) }
            c.drawRect(0f, 0f, w.toFloat(), photoH.toFloat(), p)
        }
        val margin = 64f
        var y = photoH + 56f
        y = drawText(c, detail.memory.title, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; typeface = style.titleTypeface; textSize = 64f }, margin, y, w - 2 * margin, 2)
        val meta = listOf(labels.dateText, detail.memory.place).filter { it.isNotBlank() }.joinToString("  ·  ")
        y = drawText(c, meta, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = PLUM; typeface = style.bodyTypeface; textSize = 36f }, margin, y + 12f, w - 2 * margin, 1)
        if (detail.memory.description.isNotBlank()) {
            drawText(c, detail.memory.description, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; alpha = 220; typeface = style.bodyTypeface; textSize = 34f }, margin, y + 20f, w - 2 * margin, 3)
        }
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = PLUM; alpha = 170; typeface = style.bodyTypeface; textSize = 26f }
        c.drawText(labels.footer, margin, h - 40f, footer)
        return bmp
    }

    /** One page per memory (A4 portrait), with a title page. */
    data class BookEntry(val detail: MemoryDetail, val dateText: String)

    suspend fun writeBook(target: Uri, title: String, subtitle: String, entries: List<BookEntry>, style: RenderStyle, footer: String) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            var pageNo = 1
            doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create()).also { page ->
                val c = page.canvas
                c.drawColor(CREAM)
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = PLUM; typeface = style.titleTypeface; textSize = 40f; textAlign = Paint.Align.LEFT }
                var y = drawText(c, title, tp, 56f, PAGE_H * 0.38f, PAGE_W - 112f, 3)
                drawText(c, subtitle, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; typeface = style.bodyTypeface; textSize = 16f }, 56f, y + 16f, PAGE_W - 112f, 3)
                c.drawText(footer, 56f, PAGE_H - 40f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = PLUM; alpha = 160; typeface = style.bodyTypeface; textSize = 10f })
                doc.finishPage(page)
            }
            entries.forEach { entry ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                val c = page.canvas
                c.drawColor(Color.WHITE)
                val m = 40f
                val photos = entry.detail.media.filter { it.kind == MediaKind.PHOTO }.take(2)
                var y = m
                if (photos.isNotEmpty()) {
                    val slotH = if (photos.size == 1) PAGE_H * 0.5f else PAGE_H * 0.3f
                    photos.forEachIndexed { i, media ->
                        decode(media.encryptedPath, 1400)?.let { bmp ->
                            drawFit(c, bmp, RectF(m, y, PAGE_W - m, y + slotH))
                            bmp.recycle()
                        }
                        y += slotH + if (i < photos.lastIndex) 10f else 24f
                    }
                }
                y = drawText(c, entry.detail.memory.title, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; typeface = style.titleTypeface; textSize = 24f }, m, y, PAGE_W - 2 * m, 2)
                val meta = listOf(entry.dateText, entry.detail.memory.place).filter { it.isNotBlank() }.joinToString("  ·  ")
                y = drawText(c, meta, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = PLUM; typeface = style.bodyTypeface; textSize = 12f }, m, y + 6f, PAGE_W - 2 * m, 1)
                val people = entry.detail.people.joinToString(", ") { it.displayName }
                if (people.isNotBlank()) y = drawText(c, people, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; alpha = 170; typeface = style.bodyTypeface; textSize = 11f }, m, y + 4f, PAGE_W - 2 * m, 2)
                val remainingLines = ((PAGE_H - 60f - y) / 18f).toInt().coerceAtLeast(1)
                drawText(c, entry.detail.memory.description, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; typeface = style.bodyTypeface; textSize = 12f }, m, y + 12f, PAGE_W - 2 * m, remainingLines)
                c.drawText("$pageNo", PAGE_W / 2f, PAGE_H - 24f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; alpha = 120; textSize = 9f; textAlign = Paint.Align.CENTER })
                doc.finishPage(page)
                pageNo++
            }
            context.contentResolver.openOutputStream(target, "wt")?.use { doc.writeTo(it) } ?: error("cannot open target")
        } finally {
            doc.close()
        }
    }

    private fun coverPhoto(detail: MemoryDetail, width: Int): Bitmap? {
        val cover = detail.media.firstOrNull { it.id == detail.memory.coverMediaId && it.kind == MediaKind.PHOTO }
            ?: detail.media.firstOrNull { it.kind == MediaKind.PHOTO }
            ?: detail.media.firstOrNull()
            ?: return null
        // Videos: use the (encrypted) thumbnail frame.
        return decode(if (cover.kind == MediaKind.PHOTO) cover.encryptedPath else cover.thumbPath, width)
    }

    /** Decrypts in memory and decodes, sampled down to about [targetEdge] pixels on the long side. */
    private fun decode(path: String, targetEdge: Int): Bitmap? = runCatching {
        val bytes = store.crypto.decryptToBytes(store.file(path))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetEdge) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun drawCover(c: Canvas, bmp: Bitmap, dst: Rect) {
        val scale = max(dst.width().toFloat() / bmp.width, dst.height().toFloat() / bmp.height)
        val sw = (dst.width() / scale).toInt(); val sh = (dst.height() / scale).toInt()
        val sx = (bmp.width - sw) / 2; val sy = (bmp.height - sh) / 2
        c.drawBitmap(bmp, Rect(sx, sy, sx + sw, sy + sh), dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawFit(c: Canvas, bmp: Bitmap, dst: RectF) {
        val scale = minOf(dst.width() / bmp.width, dst.height() / bmp.height)
        val w = bmp.width * scale; val h = bmp.height * scale
        val left = dst.left + (dst.width() - w) / 2
        c.drawBitmap(bmp, null, RectF(left, dst.top, left + w, dst.top + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** Draws wrapped text at (x, y) and returns the y below it. */
    private fun drawText(c: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Float, maxLines: Int): Float {
        if (text.isBlank()) return y
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save(); c.translate(x, y); layout.draw(c); c.restore()
        return y + layout.height
    }

    companion object {
        const val CARD_W = 1080
        const val CARD_H = 1350
        /** A4 at 72 dpi (PDF points). */
        const val PAGE_W = 595
        const val PAGE_H = 842
        private val CREAM = Color.rgb(0xFB, 0xF5, 0xEC)
        private val PLUM = Color.rgb(0x5B, 0x2A, 0x86)
        private val AMBER = Color.rgb(0xE8, 0xA3, 0x3D)
        private val INK = Color.rgb(0x2B, 0x22, 0x30)
    }
}
