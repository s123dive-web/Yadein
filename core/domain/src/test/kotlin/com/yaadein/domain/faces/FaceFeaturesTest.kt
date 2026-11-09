package com.yaadein.domain.faces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sin

class FaceFeaturesTest {
    private val n = FaceFeatures.SIZE

    /** A synthetic "face": a few blobs whose layout is the identity; [gain]/[offset] mimic lighting. */
    private fun face(seed: Int, gain: Float = 1f, offset: Int = 0): IntArray = IntArray(n * n) { i ->
        val x = i % n; val y = i / n
        val v = 120 + 60 * sin((x + seed * 3) / (4.0 + seed % 3)) * sin((y + seed * 5) / (5.0 + seed % 4))
        ((v * gain) + offset).toInt().coerceIn(0, 255)
    }

    @Test
    fun `descriptor has the expected size and unit length`() {
        val e = FaceFeatures.embed(face(1))
        assertEquals(FaceFeatures.DIMENSIONS, e.size)
        assertEquals(1f, e.sumOf { (it * it).toDouble() }.toFloat(), 1e-3f)
    }

    @Test
    fun `brighter or darker photos of the same face stay within the threshold`() {
        val base = FaceFeatures.embed(face(2))
        val darker = FaceFeatures.embed(face(2, gain = 0.6f, offset = -10))
        val brighter = FaceFeatures.embed(face(2, gain = 1.2f, offset = 25))
        assertTrue(FaceClustering.cosineDistance(base, darker) < FaceFeatures.THRESHOLD)
        assertTrue(FaceClustering.cosineDistance(base, brighter) < FaceFeatures.THRESHOLD)
    }

    @Test
    fun `different faces are further apart than the same face in other light`() {
        val a = FaceFeatures.embed(face(2))
        val aDark = FaceFeatures.embed(face(2, gain = 0.6f))
        val b = FaceFeatures.embed(face(7))
        assertTrue(FaceClustering.cosineDistance(a, b) > FaceClustering.cosineDistance(a, aDark))
    }

    @Test
    fun `old descriptors of another length never match`() {
        assertEquals(1f, FaceClustering.cosineDistance(FloatArray(10) { 1f }, FloatArray(12) { 1f }))
    }

    @Test
    fun `equalisation spreads a narrow range to the full scale`() {
        val eq = FaceFeatures.equalise(IntArray(100) { 100 + it % 10 })
        assertEquals(0, eq.minOrNull())
        assertTrue(eq.maxOrNull()!! >= 229)
    }
}
