package com.yaadein.domain.faces

import kotlin.math.sqrt

/** The bundled face-embedding model, as the database records it (`faces.model_version`, `persons.centroid_model`). */
object FaceModel {
    /** 1 was the LBP descriptor (dropped in schema v3); 2 is GhostFaceNetV1 W1.3-S1, int8, 112×112 → 512-d. */
    const val VERSION = 2
    const val INPUT_SIZE = 112
    const val EMBEDDING_SIZE = 512
    /** Faces smaller than this (longest box side, in source pixels) are too blurry to embed. */
    const val MIN_FACE_PX = 40
}

/** A person's running mean of confirmed face embeddings (the plain mean; compare with cosine, which ignores length). */
data class PersonCentroid(val personId: Long, val centroid: FloatArray, val count: Int)

/** What a new face looks like against the known people. */
sealed interface FaceMatch {
    val personId: Long?

    /** Similar enough to tag with a visible "suggested" badge and one-tap undo. */
    data class Auto(override val personId: Long, val similarity: Float) : FaceMatch

    /** Shown as a suggestion chip; the user confirms. */
    data class Suggest(override val personId: Long, val similarity: Float) : FaceMatch

    data object Unknown : FaceMatch { override val personId: Long? get() = null }
}

/**
 * Pure matching rules for face embeddings (cosine similarity on unit vectors). All thresholds live here so they can
 * be tuned in one place; the tests pin the behaviour at the boundaries.
 */
object FaceMatcher {
    object Thresholds {
        /** At or above: offered as a suggestion chip. */
        const val SUGGEST = 0.60f
        /** At or above: tagged automatically, badged "suggested", one tap undoes it. */
        const val AUTO = 0.75f
        /** Unknown faces at least this similar are grouped for bulk naming ("Who is this?"). */
        const val CLUSTER = 0.65f
    }

    fun normalise(v: FloatArray): FloatArray {
        var n = 0f
        for (x in v) n += x * x
        if (n == 0f) return v.copyOf()
        val s = sqrt(n)
        return FloatArray(v.size) { v[it] / s }
    }

    /** Cosine similarity in −1..1; vectors of different lengths (another model) are never similar. */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0f || nb == 0f) return -1f
        return (dot / (sqrt(na) * sqrt(nb))).coerceIn(-1f, 1f)
    }

    /** The best person for [embedding], skipping people the user said this face is not. */
    fun match(
        embedding: FloatArray,
        centroids: List<PersonCentroid>,
        rejectedPersonIds: Set<Long> = emptySet(),
        suggest: Float = Thresholds.SUGGEST,
        auto: Float = Thresholds.AUTO,
    ): FaceMatch {
        var best: PersonCentroid? = null
        var bestSim = -1f
        for (c in centroids) {
            if (c.personId in rejectedPersonIds) continue
            val s = similarity(embedding, c.centroid)
            if (s > bestSim) { bestSim = s; best = c }
        }
        val person = best ?: return FaceMatch.Unknown
        return when {
            bestSim >= auto -> FaceMatch.Auto(person.personId, bestSim)
            bestSim >= suggest -> FaceMatch.Suggest(person.personId, bestSim)
            else -> FaceMatch.Unknown
        }
    }

    /** Folds a confirmed face into a person's centroid (running mean of unit vectors; exact, so it can be undone). */
    fun addToCentroid(current: PersonCentroid?, personId: Long, embedding: FloatArray): PersonCentroid {
        val e = normalise(embedding)
        if (current == null || current.count <= 0 || current.centroid.size != e.size) return PersonCentroid(personId, e, 1)
        val n = current.count + 1
        val mean = FloatArray(e.size) { i -> (current.centroid[i] * current.count + e[i]) / n }
        return PersonCentroid(personId, mean, n)
    }

    /** Takes a face back out (the user undid a tag); null when nothing confirmed remains. */
    fun removeFromCentroid(current: PersonCentroid, embedding: FloatArray): PersonCentroid? {
        if (current.count <= 1) return null
        val e = normalise(embedding)
        val n = current.count - 1
        val mean = FloatArray(e.size) { i -> (current.centroid[i] * current.count - e[i]) / n }
        return PersonCentroid(current.personId, mean, n)
    }

    /**
     * Greedy agglomerative clustering (average linkage) of unknown faces: each face joins the existing group whose mean
     * it resembles at least [threshold], else starts a new group. Deterministic for a given order; groups are returned
     * largest first so "Who is this?" starts with the person who appears most.
     */
    fun cluster(faces: List<Pair<Long, FloatArray>>, threshold: Float = Thresholds.CLUSTER): List<List<Long>> {
        class Group(var sum: FloatArray, val ids: MutableList<Long>)
        val groups = ArrayList<Group>()
        for ((id, raw) in faces) {
            val e = normalise(raw)
            var best: Group? = null
            var bestSim = -1f
            for (g in groups) {
                val s = similarity(e, g.sum)
                if (s > bestSim) { bestSim = s; best = g }
            }
            if (best != null && bestSim >= threshold) {
                for (i in e.indices) best.sum[i] += e[i]
                best.ids += id
            } else {
                groups += Group(e.copyOf(), mutableListOf(id))
            }
        }
        return groups.sortedWith(compareByDescending<Group> { it.ids.size }.thenBy { it.ids.first() }).map { it.ids.toList() }
    }

    /** After a centroid changed: which pending faces now match it, and how. */
    fun rescore(pending: List<Pair<Long, FloatArray>>, centroid: PersonCentroid, rejected: Map<Long, Set<Long>> = emptyMap()): Map<Long, FaceMatch> =
        pending.associate { (faceId, e) ->
            faceId to match(e, listOf(centroid), rejected[faceId].orEmpty())
        }.filterValues { it !is FaceMatch.Unknown }
}
