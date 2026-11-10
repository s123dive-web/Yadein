package com.yaadein.domain.faces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceMatcherTest {
    /** A unit vector at [degrees] from the first axis, so cosine similarity to e1 is cos(degrees). */
    private fun at(degrees: Double, dims: Int = 8): FloatArray {
        val r = Math.toRadians(degrees)
        return FloatArray(dims) { i -> when (i) { 0 -> cos(r).toFloat(); 1 -> sin(r).toFloat(); else -> 0f } }
    }

    private val aaji = PersonCentroid(1, at(0.0), 5)
    private val rahul = PersonCentroid(2, at(90.0), 3)

    @Test
    fun `thresholds split unknown, suggest and auto at the boundaries`() {
        // cos(36°) ≈ 0.809 ≥ AUTO, cos(45°) ≈ 0.707 ≥ SUGGEST, cos(135°) < 0 for both people
        assertTrue(FaceMatcher.match(at(36.0), listOf(aaji, rahul)) is FaceMatch.Auto)
        val s = FaceMatcher.match(at(45.0), listOf(aaji, rahul))
        assertTrue(s is FaceMatch.Suggest && s.personId == 1L)
        assertEquals(FaceMatch.Unknown, FaceMatcher.match(at(225.0), listOf(aaji, rahul)))
        assertEquals(FaceMatch.Unknown, FaceMatcher.match(at(55.0), listOf(aaji), suggest = 0.60f))
        // Exactly at the thresholds counts.
        assertTrue(FaceMatcher.match(at(0.0), listOf(aaji), suggest = 1f, auto = 1f) is FaceMatch.Auto)
    }

    @Test
    fun `the nearest person wins and rejected people are skipped`() {
        val m = FaceMatcher.match(at(80.0), listOf(aaji, rahul))
        assertEquals(2L, m.personId)
        assertEquals(FaceMatch.Unknown, FaceMatcher.match(at(80.0), listOf(aaji, rahul), rejectedPersonIds = setOf(2)))
        assertEquals(FaceMatch.Unknown, FaceMatcher.match(at(10.0), emptyList()))
        assertEquals(-1f, FaceMatcher.similarity(at(0.0), FloatArray(4)))
    }

    @Test
    fun `centroid is a running mean that stays unit length and can be undone`() {
        val c1 = FaceMatcher.addToCentroid(null, 7, at(0.0))
        assertEquals(1, c1.count)
        val c2 = FaceMatcher.addToCentroid(c1, 7, at(60.0))
        assertEquals(2, c2.count)
        assertEquals(30f, Math.toDegrees(Math.atan2(c2.centroid[1].toDouble(), c2.centroid[0].toDouble())).toFloat(), 0.01f)
        assertTrue(FaceMatcher.similarity(c2.centroid, at(30.0)) > 0.9999f)
        val back = FaceMatcher.removeFromCentroid(c2, at(60.0))!!
        assertEquals(1, back.count)
        assertTrue(FaceMatcher.similarity(back.centroid, at(0.0)) > 0.999f)
        assertNull(FaceMatcher.removeFromCentroid(back, at(0.0)))
    }

    @Test
    fun `unknown faces cluster by similarity, largest group first`() {
        val faces = listOf(
            10L to at(0.0), 11L to at(5.0), 12L to at(100.0), 13L to at(-4.0), 14L to at(95.0), 15L to at(200.0), 16L to at(3.0),
        )
        val groups = FaceMatcher.cluster(faces)
        assertEquals(listOf(listOf(10L, 11L, 13L, 16L), listOf(12L, 14L), listOf(15L)), groups)
        assertEquals(faces.size, groups.flatten().size)
        assertEquals(7, FaceMatcher.cluster(faces, threshold = 1.01f).size)
    }

    @Test
    fun `rescore reports pending faces that now match a centroid`() {
        val pending = listOf(20L to at(30.0), 21L to at(50.0), 22L to at(85.0))
        val result = FaceMatcher.rescore(pending, aaji, rejected = mapOf(21L to setOf(1L)))
        assertTrue(result[20L] is FaceMatch.Auto)
        assertNull(result[21L]) // the user said face 21 is not Aaji
        assertNull(result[22L])
    }

    @Test
    fun `model constants are what the schema and the converter expect`() {
        assertEquals(2, FaceModel.VERSION)
        assertEquals(112, FaceModel.INPUT_SIZE)
        assertEquals(512, FaceModel.EMBEDDING_SIZE)
    }
}
