package com.violinjourney.app.core.audio.playback

/**
 * How long the player's thread waits on a full `AudioTrack` before it writes more of a chunk: as long as the rest
 * takes to play, within bounds. A wish (pause, seek, release, a setting) wakes the wait at once.
 */
internal object TrackRoom {
    /** Not a busy loop, even for a sliver of a chunk. */
    const val MIN_WAIT_MS = 5L

    /**
     * Never longer than this, whatever is left: the track (about two chunks, ~85 ms at 48 kHz) runs down by no more
     * than this before it is topped up, so a thread that wakes late or a mixer that takes its sound in bursts still
     * finds ~60 ms queued. Waiting for the whole rest of a chunk let it run down to about half.
     */
    const val MAX_WAIT_MS = 20L

    private const val MS_PER_SECOND = 1_000L

    /** The time [samplesLeft] interleaved samples of [channels] take to play at [sampleRate], within the bounds. */
    fun waitMs(samplesLeft: Int, channels: Int, sampleRate: Int): Long {
        val frames = samplesLeft.toLong() / channels.coerceAtLeast(1)
        return (frames * MS_PER_SECOND / sampleRate.coerceAtLeast(1)).coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)
    }
}
