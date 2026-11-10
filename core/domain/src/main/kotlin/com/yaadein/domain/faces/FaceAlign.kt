package com.yaadein.domain.faces

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A 2×3 affine matrix `[a b tx; c d ty]` mapping source pixels to the 112×112 model input. */
data class Affine(val a: Float, val b: Float, val tx: Float, val c: Float, val d: Float, val ty: Float) {
    fun apply(x: Float, y: Float): Pair<Float, Float> = (a * x + b * y + tx) to (c * x + d * y + ty)

    /** Row-major 3×3 values for `android.graphics.Matrix.setValues`. */
    fun values(): FloatArray = floatArrayOf(a, b, tx, c, d, ty, 0f, 0f, 1f)
}

/**
 * Aligns a detected face to the ArcFace 112×112 template from landmarks. Pure maths so it is unit-tested on the JVM;
 * the Android side only draws the bitmap through the returned matrix.
 */
object FaceAlign {
    /** The standard ArcFace reference landmarks (left eye, right eye, nose tip, left mouth, right mouth) for 112×112. */
    val TEMPLATE: List<Pair<Float, Float>> = listOf(
        38.2946f to 51.6963f,
        73.5318f to 51.5014f,
        56.0252f to 71.7366f,
        41.5493f to 92.3655f,
        70.7299f to 92.2041f,
    )

    /**
     * Least-squares similarity transform (rotation, uniform scale, translation) from [source] points to [target].
     * Needs at least two points; with exactly two (the eyes) it is the exact eye-line alignment.
     */
    fun similarity(source: List<Pair<Float, Float>>, target: List<Pair<Float, Float>> = TEMPLATE): Affine? {
        val n = minOf(source.size, target.size)
        if (n < 2) return null
        var sx = 0.0; var sy = 0.0; var tx = 0.0; var ty = 0.0
        for (i in 0 until n) { sx += source[i].first; sy += source[i].second; tx += target[i].first; ty += target[i].second }
        sx /= n; sy /= n; tx /= n; ty /= n
        // Solve for complex scale*rotation z = (a + ib) minimising Σ|z·(s−s̄) − (t−t̄)|².
        var num = 0.0; var numI = 0.0; var den = 0.0
        for (i in 0 until n) {
            val px = source[i].first - sx; val py = source[i].second - sy
            val qx = target[i].first - tx; val qy = target[i].second - ty
            num += px * qx + py * qy
            numI += px * qy - py * qx
            den += px * px + py * py
        }
        if (den == 0.0) return null
        val a = (num / den).toFloat()
        val b = (numI / den).toFloat()
        // [x'] = [a -b][x] + t
        // [y']   [b  a][y]
        val txF = (tx - (a * sx - b * sy)).toFloat()
        val tyF = (ty - (b * sx + a * sy)).toFloat()
        return Affine(a = a, b = -b, tx = txF, c = b, d = a, ty = tyF)
    }

    /** Eye-only alignment: eyes level at the template eye positions. */
    fun fromEyes(leftX: Float, leftY: Float, rightX: Float, rightY: Float): Affine? =
        similarity(listOf(leftX to leftY, rightX to rightY), TEMPLATE.take(2))

    /** The scale factor of an affine (how many output pixels per source pixel). */
    fun scaleOf(m: Affine): Float = hypot(m.a, m.c)

    /** Rotation in degrees applied by [m]. */
    fun rotationDegrees(m: Affine): Float = Math.toDegrees(atan2(m.c.toDouble(), m.a.toDouble())).toFloat()

    /** For tests: a pure rotation+scale+translation. */
    fun make(scale: Float, degrees: Float, tx: Float, ty: Float): Affine {
        val r = Math.toRadians(degrees.toDouble())
        val a = (scale * cos(r)).toFloat(); val c = (scale * sin(r)).toFloat()
        return Affine(a, -c, tx, c, a, ty)
    }
}
