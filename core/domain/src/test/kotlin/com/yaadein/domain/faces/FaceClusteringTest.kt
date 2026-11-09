package com.yaadein.domain.faces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaceClusteringTest {
    private fun sample(mediaId: Long, vararg v: Float) = FaceSample(mediaId, FaceClustering.normalise(v), 0f, 0f, 1f, 1f)

    @Test
    fun `similar faces join one cluster, different faces start another`() {
        var clusters = emptyList<Cluster>()
        var next = 1L
        val (c1, id1) = FaceClustering.assign(sample(1, 1f, 0.1f, 0f), clusters, next); clusters = c1; next = maxOf(next, id1 + 1)
        val (c2, id2) = FaceClustering.assign(sample(2, 0.95f, 0.15f, 0.02f), clusters, next); clusters = c2; next = maxOf(next, id2 + 1)
        val (c3, id3) = FaceClustering.assign(sample(3, 0f, 0f, 1f), clusters, next); clusters = c3
        assertEquals(id1, id2)
        assertTrue(id3 != id1)
        assertEquals(2, clusters.size)
        assertEquals(2, clusters.first { it.id == id1 }.count)
    }

    @Test
    fun `cosine distance is zero for identical and one for orthogonal`() {
        assertEquals(0f, FaceClustering.cosineDistance(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-5f)
        assertEquals(1f, FaceClustering.cosineDistance(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-5f)
    }
}
