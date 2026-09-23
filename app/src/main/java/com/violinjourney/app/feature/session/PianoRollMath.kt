package com.violinjourney.app.feature.session

import kotlin.math.ceil

/**
 * Geometry of the piano roll (spec 3.10). Pure: lengths are dp as Float, times are ms.
 * The x axis is time and scrolls; [x] values are content coordinates, before the scroll offset.
 */
class PianoRollMath(
    private val durationMs: Long,
    private val rowCount: Int,
    private val viewportWidth: Float,
) {
    /** A short session stretches to the viewport, a long one keeps the base scale and scrolls. */
    val dpPerMs: Float =
        maxOf(BASE_DP_PER_SECOND / MS_PER_SECOND, if (durationMs > 0) viewportWidth / durationMs else 0f)

    val contentWidth: Float = maxOf(viewportWidth, durationMs * dpPerMs)

    /** How far the content can be scrolled to the left. */
    val maxScroll: Float = contentWidth - viewportWidth

    val contentHeight: Float = rowCount * ROW_HEIGHT + ROWS_BOTTOM_PADDING

    /** Height on screen: at most [MAX_VISIBLE_ROWS], the rest scrolls inside the card. */
    val viewportHeight: Float = minOf(rowCount, MAX_VISIBLE_ROWS) * ROW_HEIGHT + ROWS_BOTTOM_PADDING

    fun x(timeMs: Long): Float = timeMs * dpPerMs

    fun barWidth(startMs: Long, endMs: Long): Float = maxOf(MIN_BAR_WIDTH, (endMs - startMs) * dpPerMs)

    fun rowLineY(row: Int): Float = row * ROW_HEIGHT + ROW_HEIGHT / 2

    fun barTop(row: Int): Float = row * ROW_HEIGHT + (ROW_HEIGHT - BAR_HEIGHT) / 2

    /** y inside a bar for a deviation: sharp goes up, clamped to the contour range. */
    fun contourY(cents: Double): Float {
        val clamped = cents.coerceIn(-CONTOUR_RANGE_CENTS, CONTOUR_RANGE_CENTS).toFloat()
        return BAR_HEIGHT / 2 - clamped / CONTOUR_RANGE_CENTS.toFloat() * CONTOUR_HALF_HEIGHT
    }

    /**
     * Scroll offset that keeps a playback cursor in view, or null when it already is: the cursor
     * may wander between [CURSOR_MIN] and [CURSOR_MAX] of the viewport, leaving that band puts
     * it back at [CURSOR_MIN] so that most of the view shows what comes next.
     */
    fun scrollToFollow(cursorMs: Long, scroll: Float): Float? {
        val position = x(cursorMs) - scroll
        if (position >= viewportWidth * CURSOR_MIN && position <= viewportWidth * CURSOR_MAX) return null
        return (x(cursorMs) - viewportWidth * CURSOR_MIN).coerceIn(0f, maxScroll)
    }

    /** Time labels: a round step that keeps them at least [MIN_TICK_SPACING] apart. */
    fun tickTimesMs(): List<Long> {
        val minStepMs = MIN_TICK_SPACING / dpPerMs
        val stepMs = TICK_STEPS_S.map { it * MS_PER_SECOND.toLong() }.firstOrNull { it >= minStepMs }
            ?: (ceil(minStepMs / TICK_LAST_STEP_MS).toLong() * TICK_LAST_STEP_MS)
        return (0..durationMs step stepMs).toList()
    }

    /**
     * Index of the bar under a tap at content coordinates, or null. Bars are given as
     * (row, startMs, endMs); the last hit wins, as the last drawn bar is on top.
     */
    fun hitTest(x: Float, y: Float, bars: List<Triple<Int, Long, Long>>): Int? =
        bars.indexOfLast { (row, startMs, endMs) ->
            val left = x(startMs)
            x >= left - TOUCH_SLOP && x <= left + barWidth(startMs, endMs) + TOUCH_SLOP &&
                y >= row * ROW_HEIGHT && y < (row + 1) * ROW_HEIGHT
        }.takeIf { it >= 0 }

    companion object {
        const val BASE_DP_PER_SECOND = 30f
        const val ROW_HEIGHT = 27f
        const val BAR_HEIGHT = 20f
        const val ROWS_BOTTOM_PADDING = 4f
        const val MAX_VISIBLE_ROWS = 12
        const val MIN_BAR_WIDTH = 6f
        const val CONTOUR_RANGE_CENTS = 40.0
        const val CONTOUR_HALF_HEIGHT = 8.8f
        const val MIN_TICK_SPACING = 80f
        const val TOUCH_SLOP = 6f
        const val CURSOR_MIN = 0.2f
        const val CURSOR_MAX = 0.8f
        private const val MS_PER_SECOND = 1_000f
        private const val TICK_LAST_STEP_MS = 600_000L
        private val TICK_STEPS_S = listOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600)
    }
}
