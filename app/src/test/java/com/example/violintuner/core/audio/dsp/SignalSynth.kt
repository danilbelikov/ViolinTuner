package com.example.violintuner.core.audio.dsp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Synthetic test signals, amplitude roughly within -1..1. */
internal object SignalSynth {
    val SINE = doubleArrayOf(1.0)

    /** Bright bowed-string-like spectrum. */
    val VIOLIN = doubleArrayOf(1.0, 0.8, 0.6, 0.5, 0.3, 0.25, 0.2, 0.1)
    val SAW = DoubleArray(8) { 1.0 / (it + 1) }

    // Octave traps for the G string: the detector must still answer with the fundamental.
    val WEAK_FUNDAMENTAL = doubleArrayOf(0.1, 1.0, 0.7, 0.5, 0.3)
    val MISSING_FUNDAMENTAL = doubleArrayOf(0.0, 1.0, 0.8, 0.6)
    val STRONG_EVEN_HARMONICS = doubleArrayOf(0.3, 1.0, 0.2, 0.8, 0.1, 0.5)

    fun hz(midi: Int, cents: Double = 0.0): Double = 440.0 * 2.0.pow((midi - 69 + cents / 100) / 12)

    /**
     * Harmonic tone of [length] samples. [centsAt] gives the pitch deviation over time in
     * seconds (vibrato); partials above Nyquist are dropped.
     */
    fun tone(
        freqHz: Double,
        sampleRateHz: Int,
        length: Int,
        partials: DoubleArray = SINE,
        phase: Double = 0.3,
        centsAt: (Double) -> Double = { 0.0 },
    ): FloatArray {
        val norm = partials.sum()
        val out = FloatArray(length)
        var angle = 0.0
        for (i in 0 until length) {
            val instantHz = freqHz * 2.0.pow(centsAt(i.toDouble() / sampleRateHz) / 1200)
            var sample = 0.0
            for (k in partials.indices) {
                val harmonic = k + 1
                if (instantHz * harmonic < sampleRateHz / 2.0) {
                    sample += partials[k] * sin(harmonic * (angle + phase))
                }
            }
            out[i] = (sample / norm).toFloat()
            angle += 2 * PI * instantHz / sampleRateHz
        }
        return out
    }

    fun whiteNoise(length: Int, seed: Int, amplitude: Double = 0.3): FloatArray {
        val random = Random(seed)
        return FloatArray(length) { (random.nextDouble(-1.0, 1.0) * amplitude).toFloat() }
    }

    /** [signal] plus white noise scaled to the given signal-to-noise ratio. */
    fun withNoise(signal: FloatArray, snrDb: Double, seed: Int): FloatArray {
        val noise = whiteNoise(signal.size, seed)
        val gain = rms(signal) / rms(noise) / 10.0.pow(snrDb / 20)
        return FloatArray(signal.size) { (signal[it] + noise[it] * gain).toFloat() }
    }

    fun rms(signal: FloatArray): Double = sqrt(signal.sumOf { it.toDouble() * it } / signal.size)

    fun toPcm16(signal: FloatArray): ShortArray =
        ShortArray(signal.size) { (signal[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
}
