package com.example.violintuner.core.domain.backing

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

    /** What the headphones add: the number set for them, or — never set — a guess for wireless ones and nothing for wired ones. */
    fun latencyMs(route: AudioRoute, latencies: HeadphoneLatencies, config: BackingConfig): Int =
        route.latencyKey?.let { latencies.of(it) } ?: if (route.output.isWireless) config.defaultWirelessLatencyMs else 0

    /** The headphones' latency as its slider moves it: whole steps, from none to [BackingConfig.maxLatencyMs]. */
    fun snapLatency(latencyMs: Int, config: BackingConfig): Int =
        ((latencyMs.toDouble() / config.offsetStepMs).roundToInt() * config.offsetStepMs).coerceIn(0, config.maxLatencyMs)

    fun clamp(offsetMs: Int, config: BackingConfig): Int = offsetMs.coerceIn(config.minOffsetMs, config.maxOffsetMs)

    /** A shift as the slider moves it: whole steps. */
    fun snap(offsetMs: Int, config: BackingConfig): Int =
        clamp((offsetMs.toDouble() / config.offsetStepMs).roundToInt() * config.offsetStepMs, config)

    fun snapGain(gainDb: Float, config: BackingConfig): Float =
        ((gainDb / config.gainStepDb).roundToInt() * config.gainStepDb).coerceIn(config.minGainDb, config.maxGainDb)

    /**
     * «Запомнить для …» (spec 3.32): the player moved the shift of a take by ear; the difference is what the
     * headphones' latency was wrong by, and the next takes should start from the corrected one.
     */
    fun correctedLatencyMs(take: TakeBacking, config: BackingConfig): Int =
        (take.latencyMs + (take.offsetMs - take.recordedOffsetMs)).coerceIn(0, config.maxLatencyMs)

    /** Where in the backing, in samples at [sampleRate], the violin's sample [violinSample] falls: negative — the backing has not begun. */
    fun backingSampleAt(violinSample: Long, offsetMs: Int, sampleRate: Int): Long =
        violinSample - (offsetMs.toLong() * sampleRate / MS_PER_SECOND.toDouble()).roundToLong()

    private const val NANOS_PER_MS = 1_000_000L
    private const val MS_PER_SECOND = 1_000L
}

/**
 * The latency of every pair of headphones, set by ear on the piece screen, by the name they give themselves (spec 5.25). At most
 * [BackingConfig.maxRememberedHeadphones]; the one used last goes to the front, the oldest falls off.
 */
data class HeadphoneLatencies(val entries: List<Pair<String, Int>> = emptyList()) {
    fun of(name: String): Int? = entries.firstOrNull { it.first == name }?.second

    fun with(name: String, latencyMs: Int, config: BackingConfig): HeadphoneLatencies =
        HeadphoneLatencies((listOf(name to latencyMs) + entries.filter { it.first != name }).take(config.maxRememberedHeadphones))

    companion object {
        val EMPTY = HeadphoneLatencies()
    }
}

/** `name=ms` lines; the name may hold anything but a line break. Unreadable — nothing, not a crash. */
object HeadphoneLatencyCodec {
    fun encode(latencies: HeadphoneLatencies): String =
        latencies.entries.joinToString("\n") { (name, ms) -> "${name.replace('\n', ' ')}$SEPARATOR$ms" }

    fun decode(text: String?): HeadphoneLatencies {
        if (text.isNullOrBlank()) return HeadphoneLatencies.EMPTY
        return HeadphoneLatencies(
            text.lines().mapNotNull { line ->
                val at = line.lastIndexOf(SEPARATOR)
                if (at <= 0) return@mapNotNull null
                val ms = line.substring(at + 1).toIntOrNull() ?: return@mapNotNull null
                line.substring(0, at) to ms
            },
        )
    }

    private const val SEPARATOR = '='
}

interface HeadphoneLatencyStore {
    val latencies: kotlinx.coroutines.flow.Flow<HeadphoneLatencies>

    suspend fun set(name: String, latencyMs: Int)
}
