package com.example.violintuner.core.audio

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.PitchFrame
import com.example.violintuner.core.domain.PitchMath
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Scripted signals for previews, tests and development without a microphone (spec 6). */
enum class FakeScenario {
    IN_TUNE, DRIFT_SHARP, DRIFT_FLAT, VIBRATO, SILENCE, NOISE,

    /** Loops through all the scenarios above, a few seconds each; for running without a mic. */
    DEMO,
}

class FakePitchSource(
    private val scenario: FakeScenario,
    private val config: IntonationConfig = IntonationConfig(),
    /** Paces the flow; tests pass the virtual clock of their scheduler. */
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : PitchSource {

    // Frames are due at absolute times since collection started. Sleeping a fixed period per
    // frame would let delay() overhead pile up and the script would lag behind the wall clock.
    override val frames: Flow<PitchFrame> = flow {
        val start = timeSource.markNow()
        var index = 0L
        while (true) {
            emit(frameAt(index))
            index++
            delay((frameTimeMs(index) - start.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
        }
    }

    /** Deterministic frame number [index]; usable without coroutines. */
    fun frameAt(index: Long): PitchFrame {
        val tMs = frameTimeMs(index)
        if (scenario != FakeScenario.DEMO) return frame(scenario, tMs, tMs / MS_PER_SECOND)
        val segment = (tMs / DEMO_SEGMENT_MS % DEMO_SEQUENCE.size).toInt()
        return frame(DEMO_SEQUENCE[segment], tMs, tMs % DEMO_SEGMENT_MS / MS_PER_SECOND)
    }

    /** [seconds] is the scenario's own clock, so drifts restart in every DEMO segment. */
    private fun frame(scenario: FakeScenario, tMs: Long, seconds: Double): PitchFrame = when (scenario) {
        FakeScenario.IN_TUNE -> played(tMs, IN_TUNE_OFFSET_CENTS)
        FakeScenario.DRIFT_SHARP -> played(tMs, driftCents(seconds))
        FakeScenario.DRIFT_FLAT -> played(tMs, -driftCents(seconds))
        FakeScenario.VIBRATO ->
            played(tMs, VIBRATO_DEPTH_CENTS * sin(2 * PI * VIBRATO_RATE_HZ * seconds))
        FakeScenario.SILENCE -> PitchFrame.unpitched(tMs, clarity = 0.0, rms = SILENCE_RMS)
        FakeScenario.NOISE -> PitchFrame.unpitched(tMs, clarity = NOISE_CLARITY, rms = NOISE_RMS)
        FakeScenario.DEMO -> error("DEMO is a sequence, not a signal")
    }

    private fun frameTimeMs(index: Long): Long =
        index * config.hopSizeSamples * MS_PER_SECOND.toLong() / config.sampleRateHz

    private fun driftCents(seconds: Double): Double =
        (DRIFT_CENTS_PER_SECOND * seconds).coerceAtMost(DRIFT_LIMIT_CENTS)

    private fun played(tMs: Long, offsetCents: Double): PitchFrame = PitchFrame.pitched(
        tMs = tMs,
        freqHz = config.a4Hz * 2.0.pow(offsetCents / PitchMath.CENTS_PER_OCTAVE),
        clarity = PLAYED_CLARITY,
        rms = PLAYED_RMS,
        a4Hz = config.a4Hz,
    )

    // Script parameters of the fake, not domain rules, hence not in IntonationConfig.
    // Every scenario plays around A4.
    private companion object {
        const val MS_PER_SECOND = 1000.0
        const val IN_TUNE_OFFSET_CENTS = 2.0
        const val DRIFT_CENTS_PER_SECOND = 12.0
        const val DRIFT_LIMIT_CENTS = 35.0
        const val VIBRATO_DEPTH_CENTS = 10.0
        const val VIBRATO_RATE_HZ = 5.5
        const val PLAYED_CLARITY = 0.97
        const val PLAYED_RMS = 0.2
        const val SILENCE_RMS = 0.0005
        const val NOISE_CLARITY = 0.3
        const val NOISE_RMS = 0.1
        const val DEMO_SEGMENT_MS = 4_000L
        val DEMO_SEQUENCE = listOf(
            FakeScenario.IN_TUNE,
            FakeScenario.DRIFT_SHARP,
            FakeScenario.DRIFT_FLAT,
            FakeScenario.VIBRATO,
            FakeScenario.SILENCE,
            FakeScenario.NOISE,
        )
    }
}
