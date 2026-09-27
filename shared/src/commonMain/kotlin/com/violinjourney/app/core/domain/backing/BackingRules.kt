package com.violinjourney.app.core.domain.backing

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The shift of a take's backing (spec 5.25). Pure. */
object BackingOffset {
    /**
     * How much later the backing is to sound in the mix than the violin's first sample: the moment the
     * backing's first sample left the output minus the moment the take's first sample came in, both on
     * the same clock (`CLOCK_MONOTONIC`), plus what the headphones add on top of what the system reports.
     * The player heard the backing that much after the take began and played along to what they heard.
     */
    fun offsetMs(backingStartNanos: Long?, recordStartNanos: Long?, headphoneLatencyMs: Int, config: BackingConfig): Int {
        val clocks = if (backingStartNanos != null && recordStartNanos != null) ((backingStartNanos - recordStartNanos) / NANOS_PER_MS.toDouble()).roundToInt() else 0
        return clamp(clocks + headphoneLatencyMs, config)
    }

    /**
     * What the headphones lag in all: a guess for wireless ones, nothing for wired ones. Not remembered per headphones
     * (0.71, the owner's decision): each take's shift is set by ear on «Звук». What of it goes on top of the clocks —
     * [latencyAddedMs].
     */
    fun latencyMs(route: AudioRoute, config: BackingConfig): Int = if (route.output.isWireless) config.defaultWirelessLatencyMs else 0

    /**
     * What of the headphones' lag [headphoneLatencyMs] is added to the clocks of a take: the part the output's clock does
     * not already hold. The backing's start is when its first frame left the output as the platform reports it — on
     * Android without a Bluetooth link (`AudioTrack.getTimestamp`, [clockHoldsMs] 0), on iOS with the output latency the
     * session reports, which may hold the link. Whichever it is, the lag is not counted twice (spec 5.25). Without both
     * clocks nothing of the lag is in the shift, and the whole of it is added.
     */
    fun latencyAddedMs(backingStartNanos: Long?, recordStartNanos: Long?, headphoneLatencyMs: Int, clockHoldsMs: Int): Int =
        if (backingStartNanos != null && recordStartNanos != null) (headphoneLatencyMs - clockHoldsMs).coerceAtLeast(0) else headphoneLatencyMs

    fun clamp(offsetMs: Int, config: BackingConfig): Int = offsetMs.coerceIn(config.minOffsetMs, config.maxOffsetMs)

    /** A shift as the slider moves it: whole steps. */
    fun snap(offsetMs: Int, config: BackingConfig): Int =
        clamp((offsetMs.toDouble() / config.offsetStepMs).roundToInt() * config.offsetStepMs, config)

    fun snapGain(gainDb: Float, config: BackingConfig): Float =
        ((gainDb / config.gainStepDb).roundToInt() * config.gainStepDb).coerceIn(config.minGainDb, config.maxGainDb)

    /** Where in the backing, in samples at [sampleRate], the violin's sample [violinSample] falls: negative — the backing has not begun. */
    fun backingSampleAt(violinSample: Long, offsetMs: Int, sampleRate: Int): Long =
        violinSample - (offsetMs.toLong() * sampleRate / MS_PER_SECOND.toDouble()).roundToLong()

    private const val NANOS_PER_MS = 1_000_000L
    private const val MS_PER_SECOND = 1_000L
}
