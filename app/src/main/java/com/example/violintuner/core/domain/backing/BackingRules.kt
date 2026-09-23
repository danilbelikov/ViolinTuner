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

    /** What the headphones add: the calibrated number, or a guess for wireless ones never calibrated; nothing for wired ones. */
    fun latencyMs(route: AudioRoute, latencies: HeadphoneLatencies, config: BackingConfig): Int = when {
        !route.output.needsCalibration -> route.deviceName?.let { latencies.of(it) } ?: 0
        else -> route.deviceName?.let { latencies.of(it) } ?: config.uncalibratedBluetoothMs
    }

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
    fun correctedLatencyMs(take: TakeBacking, currentLatencyMs: Int): Int =
        (currentLatencyMs + (take.offsetMs - take.recordedOffsetMs)).coerceAtLeast(0)

    /** Where in the backing, in samples at [sampleRate], the violin's sample [violinSample] falls: negative — the backing has not begun. */
    fun backingSampleAt(violinSample: Long, offsetMs: Int, sampleRate: Int): Long =
        violinSample - (offsetMs.toLong() * sampleRate / MS_PER_SECOND.toDouble()).roundToLong()

    private const val NANOS_PER_MS = 1_000_000L
    private const val MS_PER_SECOND = 1_000L
}

/** The outcome of «Настроим наушники» (spec 3.32). */
sealed interface CalibrationResult {
    data class Measured(val latencyMs: Int, val hits: Int, val of: Int) : CalibrationResult

    /** Too few notes on the beat, or too scattered to trust: «Не получилось поймать такт». */
    data class Failed(val hits: Int, val of: Int) : CalibrationResult
}

/**
 * The latency of headphones measured on the player (spec 5.25): each working click is paired with the
 * first note that starts within its window; the latency is the median of note minus click. The player's
 * own ear and hand are in the number on purpose — they are exactly what a take under the backing has to undo.
 */
object Calibration {
    /** When each click leaves the output, on the output's clock, lead-in included; the first [CalibrationConfig.leadInClicks] are not measured. */
    fun clickTimesNanos(firstClickNanos: Long, config: CalibrationConfig): List<Long> =
        List(config.leadInClicks + config.clicks) { firstClickNanos + it * config.beatMs * NANOS_PER_MS }

    fun measure(clickNanos: List<Long>, onsetNanos: List<Long>, config: CalibrationConfig): CalibrationResult {
        val working = clickNanos.drop(config.leadInClicks)
        val onsets = onsetNanos.sorted()
        val used = HashSet<Int>()
        val deltasMs = working.mapNotNull { click ->
            val from = click - config.windowBeforeMs * NANOS_PER_MS
            val to = click + config.windowAfterMs * NANOS_PER_MS
            val index = onsets.indices.firstOrNull { it !in used && onsets[it] in from..to } ?: return@mapNotNull null
            used += index
            (onsets[index] - click) / NANOS_PER_MS.toDouble()
        }
        if (deltasMs.size < config.minHits || spread(deltasMs) > config.maxSpreadMs) return CalibrationResult.Failed(deltasMs.size, working.size)
        return CalibrationResult.Measured(median(deltasMs).roundToInt().coerceAtLeast(0), deltasMs.size, working.size)
    }

    /** The clicks answered so far — for the eight dots of the sheet. */
    fun answered(clickNanos: List<Long>, onsetNanos: List<Long>, config: CalibrationConfig): List<Boolean> {
        val onsets = onsetNanos.sorted()
        return clickNanos.drop(config.leadInClicks).map { click ->
            onsets.any { it in (click - config.windowBeforeMs * NANOS_PER_MS)..(click + config.windowAfterMs * NANOS_PER_MS) }
        }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    /** Interquartile range: one wild note does not spoil an otherwise steady hand. */
    private fun spread(values: List<Double>): Double {
        val sorted = values.sorted()
        fun quantile(q: Double): Double {
            val position = q * (sorted.size - 1)
            val low = position.toInt()
            val high = minOf(low + 1, sorted.lastIndex)
            return sorted[low] + (sorted[high] - sorted[low]) * (position - low)
        }
        return quantile(UPPER_QUARTILE) - quantile(LOWER_QUARTILE)
    }

    private const val NANOS_PER_MS = 1_000_000L
    private const val LOWER_QUARTILE = 0.25
    private const val UPPER_QUARTILE = 0.75
}

/** Onsets — starts of notes — from the level of the microphone, frame by frame (spec 5.25). Stateful, one per calibration. */
class OnsetDetector(private val config: CalibrationConfig) {
    private val window = ArrayDeque<Pair<Long, Double>>()
    private var armed = true

    /** [rmsDb] of a frame at [nanos]; true when a note starts here. */
    fun add(nanos: Long, rmsDb: Double): Boolean {
        window.addLast(nanos to rmsDb)
        while (window.first().first < nanos - config.onsetWindowMs * NANOS_PER_MS) window.removeFirst()
        val lowest = window.minOf { it.second }
        val rise = rmsDb - lowest
        if (armed && rise >= config.onsetRiseDb) {
            armed = false
            return true
        }
        // ready for the next note once the level has settled back from the attack
        if (!armed && rise < config.onsetRiseDb / 2) armed = true
        return false
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}

/**
 * The latency of every pair of headphones calibrated, by the name they give themselves (spec 5.25). At most
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
