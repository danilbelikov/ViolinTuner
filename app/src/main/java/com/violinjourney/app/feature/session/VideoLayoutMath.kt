package com.violinjourney.app.feature.session

/**
 * Where the picture of a video take stands on the session screen (spec 3.19, 5.13; handoff 20d),
 * in dp. Pure. The frame keeps the proportions of the video inside the room it is given; scrolling
 * towards the note roll does not take it away — it shrinks into a row under the top bar, by the
 * offset of the scroll and nothing else: the mini frame is the same player grown smaller.
 */
object VideoLayoutMath {
    const val MAX_HEIGHT_SHARE = 0.4f
    const val STUCK_ROW_HEIGHT = 72f
    const val STUCK_FRAME_WIDTH = 128f

    /** The words of the stuck row fade in over the last part of the way. */
    const val WORDS_FROM = 0.6f

    /** What a video is taken for until it has said what it is. */
    const val DEFAULT_ASPECT = 16f / 9f

    data class Size(val width: Float, val height: Float)

    fun aspectOf(videoWidth: Int, videoHeight: Int): Float =
        if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight else DEFAULT_ASPECT

    /** The largest frame of [aspect] inside [maxWidth] × [maxHeight]. */
    fun fit(aspect: Float, maxWidth: Float, maxHeight: Float): Size {
        val byWidth = Size(maxWidth, maxWidth / aspect)
        return if (byWidth.height <= maxHeight) byWidth else Size(maxHeight * aspect, maxHeight)
    }

    /** The block the frame stands in when nothing is scrolled: a wide video fills the width, a tall one stands at 40 % of the screen between its margins. */
    fun blockHeight(aspect: Float, availableWidth: Float, screenHeight: Float): Float =
        fit(aspect, availableWidth, screenHeight * MAX_HEIGHT_SHARE).height

    /** 0 — as large as it gets, 1 — the row. */
    fun collapse(blockHeight: Float, scrolled: Float): Float {
        val way = blockHeight - STUCK_ROW_HEIGHT
        return if (way <= 0f) 1f else (scrolled / way).coerceIn(0f, 1f)
    }

    data class Frame(val x: Float, val width: Float, val height: Float, val blockHeight: Float, val wordsAlpha: Float)

    /** The frame at [collapse] of the way: from centred in its block to the left of the row. */
    fun frameAt(aspect: Float, availableWidth: Float, blockHeight: Float, collapse: Float): Frame {
        val full = fit(aspect, availableWidth, blockHeight)
        val small = fit(aspect, STUCK_FRAME_WIDTH, STUCK_ROW_HEIGHT)
        fun between(from: Float, to: Float) = from + (to - from) * collapse
        return Frame(
            x = between((availableWidth - full.width) / 2f, 0f),
            width = between(full.width, small.width),
            height = between(full.height, small.height),
            blockHeight = between(blockHeight, STUCK_ROW_HEIGHT),
            wordsAlpha = ((collapse - WORDS_FROM) / (1f - WORDS_FROM)).coerceIn(0f, 1f),
        )
    }

    /** «Смотреть это место» window of the note strip over a full-screen video: ± this much around the cursor. */
    const val STRIP_WINDOW_MS = 4_000L

    data class StripPiece(val from: Float, val to: Float, val index: Int)

    /** Segments as shares 0…1 of the strip whose centre is [cursorMs]; what lies outside the window is cut. */
    fun strip(segments: List<Pair<Long, Long>>, cursorMs: Long): List<StripPiece> {
        val start = cursorMs - STRIP_WINDOW_MS
        val span = (STRIP_WINDOW_MS * 2).toFloat()
        return segments.mapIndexedNotNull { index, (from, to) ->
            val a = ((from - start) / span).coerceIn(0f, 1f)
            val b = ((to - start) / span).coerceIn(0f, 1f)
            if (b > a) StripPiece(a, b, index) else null
        }
    }
}
