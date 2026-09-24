package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.domain.sound.EqSettings
import com.violinjourney.app.core.domain.sound.SoundConfig
import kotlin.math.ln
import kotlin.math.exp

/** The filters the settings ask for, and the curve they make. Pure maths: the screen draws from here, the sound is made from here. */
object EqCurve {
    const val MIN_HZ = 20.0
    const val MAX_HZ = 20_000.0

    /** Five filters in the order of the signal; a band that does nothing — and every band of a switched-off equalizer — is the identity. */
    fun coefficients(eq: EqSettings, sampleRate: Int, config: SoundConfig): List<BiquadCoefficients> {
        if (!eq.enabled) return List(BANDS) { BiquadCoefficients.IDENTITY }
        return listOf(
            if (eq.lowCut.enabled) BiquadCoefficients.highPass(eq.lowCut.hz, config.fixedQ, sampleRate) else BiquadCoefficients.IDENTITY,
            BiquadCoefficients.lowShelf(eq.low.hz, eq.low.gainDb, config.fixedQ, sampleRate),
            BiquadCoefficients.bell(eq.body.hz, eq.body.gainDb, eq.body.q, sampleRate),
            BiquadCoefficients.bell(eq.presence.hz, eq.presence.gainDb, eq.presence.q, sampleRate),
            BiquadCoefficients.highShelf(eq.air.hz, eq.air.gainDb, config.fixedQ, sampleRate),
        )
    }

    /** [points] frequencies from 20 Hz to 20 kHz, evenly spread on the logarithmic axis of the screen. */
    fun logFrequencies(points: Int): DoubleArray {
        val from = ln(MIN_HZ)
        val to = ln(MAX_HZ)
        return DoubleArray(points) { exp(from + (to - from) * it / (points - 1).coerceAtLeast(1)) }
    }

    /** The response in decibels at each of [frequencies]: the sum of what every filter does there. Not clipped — the drawing clips, the number does not. */
    fun responseDb(eq: EqSettings, sampleRate: Int, frequencies: DoubleArray, config: SoundConfig): DoubleArray {
        val filters = coefficients(eq, sampleRate, config).filter { it != BiquadCoefficients.IDENTITY }
        return DoubleArray(frequencies.size) { index -> filters.sumOf { it.magnitudeDb(frequencies[index], sampleRate) } }
    }

    const val BANDS = 5
}

/** The five filters in the stream. */
internal class Equalizer(private val sampleRate: Int, private val config: SoundConfig, rampSamples: Int) {
    private val sections = List(EqCurve.BANDS) { BiquadSection(rampSamples) }

    fun set(eq: EqSettings, immediate: Boolean) {
        EqCurve.coefficients(eq, sampleRate, config).forEachIndexed { index, coefficients -> sections[index].set(coefficients, immediate) }
    }

    fun reset() = sections.forEach { it.reset() }

    fun process(sample: Double): Double {
        var value = sample
        for (section in sections) {
            if (!section.idle) value = section.process(value)
        }
        return value
    }
}
