package com.yaadein.domain.faces

import kotlin.math.sqrt

/** A detected face: a compact embedding plus where it was found. */
data class FaceSample(val mediaId: Long, val embedding: FloatArray, val left: Float, val top: Float, val right: Float, val bottom: Float)

data class Cluster(val id: Long, val centroid: FloatArray, val count: Int, val sampleMediaId: Long, val sampleBox: FloatArray)

/**
 * Incremental, deterministic nearest-centroid clustering (cosine distance). Simple on purpose: the
 * user confirms every suggestion, so a conservative threshold and no automatic tagging is the point.
 */
object FaceClustering {
    const val DEFAULT_THRESHOLD = 0.32f

    fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0f || nb == 0f) return 1f
        return 1f - dot / (sqrt(na) * sqrt(nb))
    }

    /** Returns the updated cluster list; a new cluster gets id = nextId. */
    fun assign(sample: FaceSample, clusters: List<Cluster>, nextId: Long, threshold: Float = DEFAULT_THRESHOLD): Pair<List<Cluster>, Long> {
        val best = clusters.minByOrNull { cosineDistance(it.centroid, sample.embedding) }
        if (best != null && cosineDistance(best.centroid, sample.embedding) <= threshold) {
            val n = best.count + 1
            val centroid = FloatArray(best.centroid.size) { i -> (best.centroid[i] * best.count + sample.embedding[i]) / n }
            val updated = best.copy(centroid = centroid, count = n)
            return clusters.map { if (it.id == best.id) updated else it } to best.id
        }
        val fresh = Cluster(nextId, sample.embedding.copyOf(), 1, sample.mediaId, floatArrayOf(sample.left, sample.top, sample.right, sample.bottom))
        return (clusters + fresh) to nextId
    }

    /** L2-normalise so cosine distance is stable across lighting. */
    fun normalise(v: FloatArray): FloatArray {
        var n = 0f; for (x in v) n += x * x
        val s = if (n == 0f) 1f else sqrt(n)
        return FloatArray(v.size) { v[it] / s }
    }
}
