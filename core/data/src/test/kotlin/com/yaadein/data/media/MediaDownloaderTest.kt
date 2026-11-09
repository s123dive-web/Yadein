package com.yaadein.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloaderTest {
    private fun sniff(bytes: ByteArray, isVideo: Boolean = false) = MediaDownloader.sniff(bytes.inputStream(), isVideo)
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private fun ftyp(brand: String) = bytes(0, 0, 0, 0x18) + "ftyp$brand".toByteArray(Charsets.US_ASCII) + ByteArray(4)

    @Test
    fun `recognises the original photo format`() {
        assertEquals("image/jpeg", sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0)).mime)
        assertEquals("png", sniff(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A)).extension)
        assertEquals("image/webp", sniff("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)).mime)
        assertEquals("image/heic", sniff(ftyp("heic")).mime)
    }

    @Test
    fun `recognises video containers and falls back by kind`() {
        assertEquals("video/mp4", sniff(ftyp("isom"), isVideo = true).mime)
        assertEquals("mov", sniff(ftyp("qt  "), isVideo = true).extension)
        assertEquals("video/mp4", sniff(ByteArray(0), isVideo = true).mime)
        assertEquals("image/jpeg", sniff(ByteArray(3), isVideo = false).mime)
    }

    @Test
    fun `file names sort by date and are unique per media`() {
        val name = MediaDownloader.fileName(42, 0L, "jpg")
        assertTrue(name, name.matches(Regex("Yaadein_\\d{8}_\\d{6}_42\\.jpg")))
    }
}
