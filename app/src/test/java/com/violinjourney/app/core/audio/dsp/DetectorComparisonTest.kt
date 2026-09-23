package com.violinjourney.app.core.audio.dsp

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchMath
import kotlin.math.abs
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Choice by tests" (spec 5.1): prints accuracy, noise robustness and cost of both detectors.
 * The numbers behind the default binding in AudioModule come from this report; rerun it with
 * `./gradlew :app:testDebugUnitTest --tests "*.DetectorComparisonTest" -i` after changing either.
 */
class DetectorComparisonTest {
    private val config = IntonationConfig()
    private val window = config.windowSizeSamples
    private val rate = 44_100

    private class NoiseScore(
        var correct: Int = 0,
        var rejected: Int = 0,
        var confidentlyWrong: Int = 0,
        var errorSum: Double = 0.0,
    )

    @Test
    fun `report and sanity check`() {
        val detectors = listOf("YIN" to YinDetector(config), "MPM" to MpmDetector(config))
        val report = StringBuilder("\n=== detector comparison, violin spectrum, $rate Hz ===\n")
        for ((name, detector) in detectors) {
            report.append("$name\n")
            report.append("  clean, max / mean |error| in cents per octave from G3: ")
            for (octave in 0 until 4) {
                val errors = (55 + octave * 12..minOf(100, 66 + octave * 12)).flatMap { midi ->
                    listOf(-30.0, 0.0, 17.0, 41.0).map { cents ->
                        val hz = SignalSynth.hz(midi, cents)
                        val freq = detector.detect(SignalSynth.tone(hz, rate, window, SignalSynth.VIOLIN), rate).freqHz
                        if (freq == null) Double.MAX_VALUE else abs(PitchMath.centsBetween(freq, hz))
                    }
                }
                report.append("%.2f / %.2f   ".format(errors.max(), errors.average()))
            }
            report.append('\n')
            for (snr in listOf(20.0, 10.0, 8.0, 7.0, 6.0, 0.0)) {
                val score = NoiseScore()
                for (midi in listOf(55, 62, 69, 76, 88)) for (seed in 1..40) {
                    val hz = SignalSynth.hz(midi, 9.0)
                    val noisy = SignalSynth.withNoise(SignalSynth.tone(hz, rate, window, SignalSynth.VIOLIN), snr, seed)
                    val estimate = detector.detect(noisy, rate)
                    val freq = estimate.freqHz
                    when {
                        freq == null || estimate.clarity < config.clarityThreshold -> score.rejected++
                        abs(PitchMath.centsBetween(freq, hz)) <= 50 -> {
                            score.correct++
                            score.errorSum += abs(PitchMath.centsBetween(freq, hz))
                        }
                        else -> score.confidentlyWrong++
                    }
                }
                report.append(
                    "  SNR %5.1f dB: correct %3d (mean |error| %.2f c), rejected %3d, confidently wrong %d\n".format(
                        snr, score.correct, score.errorSum / maxOf(1, score.correct),
                        score.rejected, score.confidentlyWrong,
                    ),
                )
                assertTrue("$name is confidently wrong at $snr dB", score.confidentlyWrong == 0)
            }
            // Note onset: amplitude grows across the window, as in a bow attack.
            for (startLevel in listOf(0.5, 0.2, 0.05)) {
                var accepted = 0
                var claritySum = 0.0
                var worst = 0.0
                val notes = listOf(55, 62, 69, 76, 88)
                for (midi in notes) {
                    val hz = SignalSynth.hz(midi, 9.0)
                    val tone = SignalSynth.tone(hz, rate, window, SignalSynth.VIOLIN)
                    val swell = FloatArray(window) {
                        (tone[it] * (startLevel + (1 - startLevel) * it / window)).toFloat()
                    }
                    val estimate = detector.detect(swell, rate)
                    claritySum += estimate.clarity
                    val freq = estimate.freqHz
                    if (freq != null && estimate.clarity >= config.clarityThreshold) {
                        accepted++
                        worst = maxOf(worst, abs(PitchMath.centsBetween(freq, hz)))
                    }
                }
                report.append(
                    "  swell from %.2f: accepted %d of %d, mean clarity %.3f, worst |error| %.2f c\n"
                        .format(startLevel, accepted, notes.size, claritySum / notes.size, worst),
                )
            }
            val signal = SignalSynth.tone(440.0, rate, window, SignalSynth.VIOLIN)
            repeat(WARM_UP) { detector.detect(signal, rate) }
            val nanos = measureNanoTime { repeat(TIMED) { detector.detect(signal, rate) } }
            report.append("  cost: %.3f ms per frame on this JVM\n".format(nanos / 1e6 / TIMED))
        }
        println(report)
    }

    private companion object {
        const val WARM_UP = 50
        const val TIMED = 200
    }
}
