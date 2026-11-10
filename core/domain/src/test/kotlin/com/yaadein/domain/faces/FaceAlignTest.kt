package com.yaadein.domain.faces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FaceAlignTest {
    @Test
    fun `a rotated, scaled and shifted template is recovered exactly`() {
        val truth = FaceAlign.make(scale = 2.5f, degrees = 17f, tx = 40f, ty = -12f)
        // Source landmarks = template mapped through the inverse of truth; solving must give truth back.
        val inv = run {
            val det = truth.a * truth.d - truth.b * truth.c
            val ia = truth.d / det; val ib = -truth.b / det; val ic = -truth.c / det; val id = truth.a / det
            Affine(ia, ib, -(ia * truth.tx + ib * truth.ty), ic, id, -(ic * truth.tx + id * truth.ty))
        }
        val source = FaceAlign.TEMPLATE.map { (x, y) -> inv.apply(x, y) }
        val solved = FaceAlign.similarity(source)!!
        assertEquals(2.5f, FaceAlign.scaleOf(solved), 1e-3f)
        assertEquals(17f, FaceAlign.rotationDegrees(solved), 1e-2f)
        FaceAlign.TEMPLATE.forEachIndexed { i, (tx, ty) ->
            val (x, y) = solved.apply(source[i].first, source[i].second)
            assertEquals(tx, x, 1e-2f); assertEquals(ty, y, 1e-2f)
        }
    }

    @Test
    fun `eyes alone land on the template eye positions`() {
        val m = FaceAlign.fromEyes(100f, 300f, 300f, 300f)!!
        val (lx, ly) = m.apply(100f, 300f)
        val (rx, ry) = m.apply(300f, 300f)
        assertEquals(FaceAlign.TEMPLATE[0].first, lx, 1e-2f); assertEquals(FaceAlign.TEMPLATE[0].second, ly, 1e-2f)
        assertEquals(FaceAlign.TEMPLATE[1].first, rx, 1e-2f); assertEquals(FaceAlign.TEMPLATE[1].second, ry, 1e-2f)
        // A tilted face is rotated level.
        val tilted = FaceAlign.fromEyes(100f, 300f, 300f, 400f)!!
        assertEquals(-26.9f, FaceAlign.rotationDegrees(tilted), 0.1f)
    }

    @Test
    fun `degenerate input returns null and the matrix values are row-major`() {
        assertNull(FaceAlign.similarity(listOf(1f to 1f)))
        assertNull(FaceAlign.similarity(listOf(5f to 5f, 5f to 5f)))
        val m = FaceAlign.make(1f, 0f, 3f, 4f)
        assertNotNull(m)
        assertEquals(listOf(1f, -0f, 3f, 0f, 1f, 4f, 0f, 0f, 1f).map { it + 0f }, m.values().toList().map { it + 0f })
    }
}
