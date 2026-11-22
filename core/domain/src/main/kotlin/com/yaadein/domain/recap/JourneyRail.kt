package com.yaadein.domain.recap

/**
 * Geometry of a journey reel's rail: a line across the frame with one dot per memory and a larger dot on the memory
 * being shown. Pure numbers, so the exported video and the in-app preview draw the same thing.
 */
object JourneyRail {
    /** Past this many memories the small dots would run into each other; only the line and the current dot are drawn. */
    const val MAX_DOTS = 24

    data class Layout(
        val startX: Float, val endX: Float, val y: Float,
        /** Thickness of the line. */
        val line: Float,
        val dotRadius: Float, val currentRadius: Float,
        /** Centre of each memory's dot, in order; empty when there are more than [MAX_DOTS]. */
        val dots: List<Float>,
        /** Centre of the current memory's dot; the line is filled up to here. */
        val currentX: Float,
    )

    fun layout(step: Int, count: Int, width: Float, height: Float): Layout {
        val startX = width * SIDE_MARGIN
        val endX = width - startX
        val steps = (count - 1).coerceAtLeast(1)
        fun x(i: Int) = startX + (endX - startX) * i.coerceIn(0, steps) / steps
        return Layout(
            startX = startX, endX = endX, y = height / 2f, line = height * 0.16f,
            dotRadius = height * 0.26f, currentRadius = height * 0.5f,
            dots = if (count in 2..MAX_DOTS) (0 until count).map { x(it) } else emptyList(),
            currentX = if (count <= 1) endX else x(step),
        )
    }

    private const val SIDE_MARGIN = 0.08f
}
