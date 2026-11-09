package com.yaadein.data.recap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pre-draws one photo into a reel frame, so the renderer gets an image that already has the right shape.
 *
 * - Upright (EXIF orientation applied), decoded at just enough resolution, scaled with filtering.
 * - Photos close to the frame's shape fill it (light centre crop). Others are shown whole on top of a blurred,
 *   darkened, zoomed copy of themselves, the usual reels look, instead of cutting people off at the sides.
 * - Drawn at [SUPERSAMPLE]× the output size so the Ken Burns zoom (up to ~1.18×) still samples down, not up.
 */
class PhotoFramer(private val frameWidth: Int, private val frameHeight: Int) {

    val canvasWidth = (frameWidth * SUPERSAMPLE).toInt()
    val canvasHeight = (frameHeight * SUPERSAMPLE).toInt()

    /** Writes the framed photo as a high-quality JPEG to [out]. */
    fun frame(photoBytes: ByteArray, out: File) {
        val photo = decodeUpright(photoBytes)
        val canvasBmp = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val frameAspect = canvasWidth.toFloat() / canvasHeight
        val photoAspect = photo.width.toFloat() / photo.height
        if (abs(photoAspect - frameAspect) / frameAspect <= FILL_TOLERANCE) {
            canvas.drawBitmap(photo, null, cover(photo), paint)
        } else {
            drawBlurredBackdrop(canvas, photo)
            canvas.drawBitmap(photo, null, fit(photo), paint)
        }
        photo.recycle()
        FileOutputStream(out).use { canvasBmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        canvasBmp.recycle()
    }

    /**
     * For a video clip whose shape is far from the frame: a frame-sized (not supersampled) overlay made from the clip's
     * thumbnail, blurred and dimmed, with a transparent window where the whole clip will play (the clip itself is laid
     * out with Presentation LAYOUT_SCALE_TO_FIT). Null when the clip is close enough to the frame shape to simply fill it.
     */
    fun videoBackdrop(thumbBytes: ByteArray): Bitmap? {
        val thumb = decodeUpright(thumbBytes)
        val frameAspect = frameWidth.toFloat() / frameHeight
        val clipAspect = thumb.width.toFloat() / thumb.height
        if (abs(clipAspect - frameAspect) / frameAspect <= FILL_TOLERANCE) { thumb.recycle(); return null }
        val out = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawBlurredBackdrop(canvas, thumb, frameWidth, frameHeight)
        val s = min(frameWidth.toFloat() / thumb.width, frameHeight.toFloat() / thumb.height)
        val w = thumb.width * s; val h = thumb.height * s
        val window = RectF((frameWidth - w) / 2, (frameHeight - h) / 2, (frameWidth + w) / 2, (frameHeight + h) / 2)
        canvas.drawRect(window, Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
        thumb.recycle()
        return out
    }

    private fun decodeUpright(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not a decodable photo" }
        // Decode no smaller than needed to cover the canvas, but skip resolution far beyond it.
        val needed = max(canvasWidth, canvasHeight)
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= min(canvasWidth, canvasHeight) &&
            max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= needed
        ) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: error("photo decode failed")
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return decoded
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also { if (it != decoded) decoded.recycle() }
    }

    /** Rectangle that covers a [cw]×[ch] canvas, centred (crops the overflow). */
    private fun cover(b: Bitmap, cw: Int = canvasWidth, ch: Int = canvasHeight): RectF {
        val s = max(cw.toFloat() / b.width, ch.toFloat() / b.height)
        val w = b.width * s; val h = b.height * s
        return RectF((cw - w) / 2, (ch - h) / 2, (cw + w) / 2, (ch + h) / 2)
    }

    /** Rectangle that shows the whole photo inside the canvas, centred. */
    private fun fit(b: Bitmap): RectF {
        val s = min(canvasWidth.toFloat() / b.width, canvasHeight.toFloat() / b.height)
        val w = b.width * s; val h = b.height * s
        return RectF((canvasWidth - w) / 2, (canvasHeight - h) / 2, (canvasWidth + w) / 2, (canvasHeight + h) / 2)
    }

    /** Cheap, smooth blur: shrink a lot, then scale back up with filtering, twice. */
    private fun drawBlurredBackdrop(canvas: Canvas, photo: Bitmap, cw: Int = canvasWidth, ch: Int = canvasHeight) {
        val target = cover(photo, cw, ch)
        // Small sources (video thumbnails) get a gentler first step so the blur still has some shape.
        val step1 = if (photo.width < 1000) BLUR_STEP_1 / 4 else BLUR_STEP_1
        val tiny1 = Bitmap.createScaledBitmap(photo, max(1, photo.width / step1), max(1, photo.height / step1), true)
        val tiny2 = Bitmap.createScaledBitmap(tiny1, max(1, tiny1.width / BLUR_STEP_2), max(1, tiny1.height / BLUR_STEP_2), true)
        val back = Bitmap.createScaledBitmap(tiny2, max(1, tiny1.width), max(1, tiny1.height), true)
        canvas.drawBitmap(back, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawColor(Color.argb(BACKDROP_DIM_ALPHA, 0, 0, 0))
        tiny1.recycle(); tiny2.recycle(); back.recycle()
    }

    companion object {
        const val SUPERSAMPLE = 1.25f
        /** Photos whose shape is within 12 % of the frame's fill it; others get the blurred backdrop. */
        const val FILL_TOLERANCE = 0.12f
        const val JPEG_QUALITY = 95
        private const val BLUR_STEP_1 = 16
        private const val BLUR_STEP_2 = 4
        private const val BACKDROP_DIM_ALPHA = 90
    }
}
