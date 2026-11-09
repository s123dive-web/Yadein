package com.yaadein.domain.faces

import kotlin.math.sqrt

/**
 * Face descriptor: uniform local binary patterns (LBP 8,1) on a histogram-equalised, eye-aligned grey crop, pooled
 * over a 4×4 grid. LBP compares each pixel with its neighbours, so it ignores overall brightness and copes far
 * better with indoor/outdoor lighting than raw pixels did. Square-rooted histograms make cosine distance behave like
 * the Hellinger distance, the usual choice for comparing histograms.
 */
object FaceFeatures {
    const val SIZE = 64
    const val GRID = 4
    const val BINS = 59
    const val DIMENSIONS = GRID * GRID * BINS
    /** Cosine distance below which two faces join the same suggestion. Conservative: the user confirms everything. */
    const val THRESHOLD = 0.24f

    /** Index of each 8-bit pattern among the 58 uniform patterns (at most two 0/1 transitions); 58 = everything else. */
    private val uniformIndex: IntArray = IntArray(256).also { table ->
        var next = 0
        for (p in 0 until 256) {
            var transitions = 0
            for (i in 0 until 8) if (((p shr i) and 1) != ((p shr ((i + 1) % 8)) and 1)) transitions++
            table[p] = if (transitions <= 2) next++ else 58
        }
    }

    /** [grey]: SIZE×SIZE values 0..255, row-major. */
    fun embed(grey: IntArray): FloatArray {
        require(grey.size == SIZE * SIZE) { "expected ${SIZE}x$SIZE" }
        val eq = equalise(grey)
        val hist = FloatArray(DIMENSIONS)
        val cell = SIZE / GRID
        val dx = intArrayOf(-1, 0, 1, 1, 1, 0, -1, -1)
        val dy = intArrayOf(-1, -1, -1, 0, 1, 1, 1, 0)
        for (y in 1 until SIZE - 1) for (x in 1 until SIZE - 1) {
            val c = eq[y * SIZE + x]
            var code = 0
            for (k in 0 until 8) if (eq[(y + dy[k]) * SIZE + x + dx[k]] >= c) code = code or (1 shl k)
            val cellIndex = (y / cell).coerceAtMost(GRID - 1) * GRID + (x / cell).coerceAtMost(GRID - 1)
            hist[cellIndex * BINS + uniformIndex[code]] += 1f
        }
        for (i in hist.indices) hist[i] = sqrt(hist[i])
        return FaceClustering.normalise(hist)
    }

    /** Histogram equalisation: spreads the grey levels so dark and bright photos look alike. */
    fun equalise(grey: IntArray): IntArray {
        val counts = IntArray(256)
        for (g in grey) counts[g.coerceIn(0, 255)]++
        val cdf = IntArray(256)
        var run = 0
        for (i in 0 until 256) { run += counts[i]; cdf[i] = run }
        val min = cdf.first { it > 0 }
        val range = (grey.size - min).coerceAtLeast(1)
        return IntArray(grey.size) { i -> ((cdf[grey[i].coerceIn(0, 255)] - min) * 255L / range).toInt() }
    }
}
