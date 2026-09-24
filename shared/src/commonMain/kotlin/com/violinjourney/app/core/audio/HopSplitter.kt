package com.violinjourney.app.core.audio

import kotlin.math.roundToInt

/**
 * Blocks of any length in, hops of [hopSize] 16-bit samples out — the shape the analysis reads on Android, where
 * AudioRecord gives hops itself. The iOS input hands over float blocks of whatever size the system likes (about
 * a tenth of a second); cut into the same hops, they go through the same FrameAnalyzer and watchdog, with the same
 * numbers. Samples are clipped to −1..1 and scaled as PCM16. One per stream; not thread-safe.
 */
class HopSplitter(hopSize: Int) {
    private val hop = ShortArray(hopSize)
    private var filled = 0

    /** Takes [count] samples of [block]; [onHop] gets every hop completed by them — the same array, refilled after. */
    fun push(block: FloatArray, count: Int = block.size, onHop: (ShortArray) -> Unit) {
        for (i in 0 until count) {
            hop[filled++] = toPcm16(block[i])
            if (filled == hop.size) {
                onHop(hop)
                filled = 0
            }
        }
    }

    private fun toPcm16(sample: Float): Short = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
}
