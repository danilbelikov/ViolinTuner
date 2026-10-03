package com.violinjourney.app.core.recording.overlay

import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Makes «Видео с нотами» (spec 3.37, 5.30): the picture of a recording encoded again with the overlay drawn over every frame and
 * the summary three seconds after its end, beside the sound of the chosen variant.
 */
interface NotesVideoRenderer {
    /**
     * [picture] — the video of the recording; [sound] — the file whose sound track goes into [target] as it is (the rendered
     * `.m4a` of the variant, or the video itself when its own sound goes); [overlay] and [words] — what is drawn. True when
     * [target] is whole; false when it could not be made — [target] is gone then. Cancellable: a cancelled render leaves no
     * file either. [onProgress] gets 0…1 of this work, from whatever thread renders.
     */
    suspend fun render(
        picture: PlatformFile,
        sound: PlatformFile,
        overlay: NotesOverlay,
        words: OverlayWords,
        target: PlatformFile,
        onProgress: (Float) -> Unit,
    ): Boolean
}

/** The picture of a «Видео с нотами» file (spec 5.30): its size as the player shows it, frames a second and bit rate. */
data class NotesVideoFormat(val width: Int, val height: Int, val frameRate: Int, val bitrate: Int) {
    /** «1080p» of the line of the file. */
    val shortSide: Int get() = min(width, height)

    companion object {
        /**
         * For a video shown [width] × [height] at [frameRate] frames a second (0 when the file does not say): no larger than
         * [NotesVideoConfig.maxShortSidePx] on the short side — a smaller one is not enlarged — with even sides, no more than
         * [NotesVideoConfig.maxFrameRate] frames, [NotesVideoConfig.bitsPerPixel] of every pixel of every frame.
         */
        fun of(width: Int, height: Int, frameRate: Float, config: NotesVideoConfig): NotesVideoFormat {
            val short = min(width, height)
            val scale = if (short > config.maxShortSidePx) config.maxShortSidePx.toDouble() / short else 1.0
            val outWidth = even(width * scale)
            val outHeight = even(height * scale)
            val rate = if (frameRate > 0f) min(frameRate.roundToInt().coerceAtLeast(1), config.maxFrameRate) else config.maxFrameRate
            val bitrate = (config.bitsPerPixel * outWidth * outHeight * rate).roundToInt().coerceIn(config.minBitrate, config.maxBitrate)
            return NotesVideoFormat(outWidth, outHeight, rate, bitrate)
        }

        /** About how much the file weighs: the picture and [soundBitrate] of sound over the video and the summary after it. */
        fun bytes(format: NotesVideoFormat, soundBitrate: Int, durationMs: Long, config: NotesVideoConfig): Long =
            (format.bitrate.toLong() + soundBitrate) * (durationMs + config.summaryMs) / BITS_PER_BYTE / MS_PER_SECOND

        /** Rounded down to an even number, two at the least: what every encoder takes. */
        private fun even(value: Double): Int = (value.toInt() / 2 * 2).coerceAtLeast(2)

        private const val BITS_PER_BYTE = 8
        private const val MS_PER_SECOND = 1_000
    }
}
