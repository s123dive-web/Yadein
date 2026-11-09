package com.yaadein.data.recap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoFramerTest {
    private val framer = PhotoFramer(1080, 1920)

    /** A test photo: coloured quadrants with a white circle "face" near each side, so cropping would be visible. */
    private fun photo(w: Int, h: Int): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(200, 60, 60); c.drawRect(0f, 0f, w / 2f, h.toFloat(), p)
        p.color = Color.rgb(60, 120, 200); c.drawRect(w / 2f, 0f, w.toFloat(), h.toFloat(), p)
        p.color = Color.WHITE
        c.drawCircle(w * 0.1f, h * 0.5f, h * 0.08f, p); c.drawCircle(w * 0.9f, h * 0.5f, h * 0.08f, p)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    private fun frame(w: Int, h: Int, name: String): Bitmap {
        val out = File(System.getProperty("framer.out") ?: System.getProperty("java.io.tmpdir"), name)
        framer.frame(photo(w, h), out)
        return BitmapFactory.decodeFile(out.path)
    }

    @Test
    fun `wide photo is shown whole over a blurred backdrop at the supersampled reel size`() {
        val f = frame(4000, 3000, "framed-landscape.jpg")
        assertEquals(1350, f.width); assertEquals(2400, f.height)
        // Both "faces" near the photo's left and right edges are still in the frame.
        val y = f.height / 2
        assertTrue("left face kept", Color.red(f.getPixel((f.width * 0.1f).toInt(), y)) > 220)
        assertTrue("right face kept", Color.blue(f.getPixel((f.width * 0.9f).toInt(), y)) > 220)
        // Above the photo is the dimmed, blurred copy, not black and not the sharp photo.
        val top = f.getPixel(f.width / 2, f.height / 12)
        assertNotEquals(Color.BLACK, top)
    }

    @Test
    fun `portrait photo close to 9 by 16 fills the frame`() {
        val f = frame(1200, 2000, "framed-portrait.jpg")
        assertEquals(1350, f.width); assertEquals(2400, f.height)
        val corner = f.getPixel(5, 5)
        assertTrue("photo reaches the corner, no backdrop", Color.red(corner) > 150 && Color.blue(corner) < 110)
    }
}
