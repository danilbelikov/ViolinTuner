package com.violinjourney.app.core.audio

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * Debug builds only: one line per second with what the detector saw, for tuning the silence and clarity thresholds
 * on a real instrument — `adb logcat -s MicPitchSource` on Android, the log of the app on iOS. [header] names the
 * input (its source and rate); [log] writes the line where the platform keeps such lines.
 */
class FrameStats(
    private val config: IntonationConfig,
    private val header: String,
    private val log: (String) -> Unit,
) {
    private var windowStartMs = -1L
    private var frames = 0
    private var confident = 0
    private var peakRms = 0.0
    private var peakClarity = 0.0
    private var lastConfidentHz = 0.0
    private val notes = HashMap<Int, Int>()
    private var maxGapMs = 0L
    private var lastFrameMs = -1L

    fun add(frame: PitchFrame) {
        if (windowStartMs < 0) windowStartMs = frame.tMs
        if (lastFrameMs >= 0) maxGapMs = maxOf(maxGapMs, frame.tMs - lastFrameMs)
        lastFrameMs = frame.tMs
        frames++
        peakRms = maxOf(peakRms, frame.rms)
        peakClarity = maxOf(peakClarity, frame.clarity)
        val freq = frame.freqHz
        if (freq != null && frame.clarity >= config.clarityThreshold && frame.rms >= config.silenceRms) {
            confident++
            lastConfidentHz = freq
            frame.midi?.let { notes[it] = (notes[it] ?: 0) + 1 }
        }
        if (frame.tMs - windowStartMs < LOG_PERIOD_MS) return
        log(line(frame.tMs))
        notes.clear()
        maxGapMs = 0
        windowStartMs = frame.tMs
        frames = 0
        confident = 0
        peakRms = 0.0
        peakClarity = 0.0
    }

    private fun line(tMs: Long): String {
        val peakDbfs = if (peakRms > 0) DB_PER_DECADE * log10(peakRms) else Double.NEGATIVE_INFINITY
        val midi = notes.keys.sorted().joinToString(", ", "{", "}") { "$it=${notes.getValue(it)}" }
        return "$header t=${tMs / LOG_PERIOD_MS}s frames=$frames confident=$confident " +
            "peakRms=${fixed(peakDbfs, 1)} dBFS peakClarity=${fixed(peakClarity, 2)} lastHz=${fixed(lastConfidentHz, 1)}" +
            " midi=$midi maxStepMs=$maxGapMs"
    }

    internal companion object {
        const val LOG_PERIOD_MS = 1_000L
        const val DB_PER_DECADE = 20.0

        /** [value] with [decimals] digits after a point, as `%.Nf` writes it in the root locale. */
        fun fixed(value: Double, decimals: Int): String {
            if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
            if (value.isNaN()) return "NaN"
            var scale = 1L
            repeat(decimals) { scale *= 10 }
            val scaled = (abs(value) * scale).roundToLong()
            val whole = scaled / scale
            val fraction = (scaled % scale).toString().padStart(decimals, '0')
            val sign = if (value < 0 && scaled != 0L) "-" else ""
            return if (decimals == 0) "$sign$whole" else "$sign$whole.$fraction"
        }
    }
}
