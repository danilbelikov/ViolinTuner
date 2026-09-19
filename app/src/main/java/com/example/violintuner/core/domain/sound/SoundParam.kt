package com.example.violintuner.core.domain.sound

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

/** The four blocks of the «Звук» screen, in the order the signal passes them. */
enum class SoundBlock { EQ, COMPRESSOR, REVERB, OUTPUT }

/** What a number is a number of: decides its step and how it is written. */
enum class SoundUnit { HERTZ, DECIBEL, MILLISECOND, SECOND, PERCENT, RATIO, WIDTH, AMOUNT }

/**
 * Every number of the «Звук» screen that has a slider (spec 3.17) — one description for all of
 * them, so that one control serves them all: where the number lives in the settings, its range,
 * its step, whether its track is logarithmic or grows from the middle.
 */
enum class SoundParam(val block: SoundBlock, val unit: SoundUnit) {
    LOW_CUT_HZ(SoundBlock.EQ, SoundUnit.HERTZ),
    LOW_HZ(SoundBlock.EQ, SoundUnit.HERTZ),
    LOW_GAIN(SoundBlock.EQ, SoundUnit.DECIBEL),
    BODY_HZ(SoundBlock.EQ, SoundUnit.HERTZ),
    BODY_GAIN(SoundBlock.EQ, SoundUnit.DECIBEL),
    BODY_Q(SoundBlock.EQ, SoundUnit.WIDTH),
    PRESENCE_HZ(SoundBlock.EQ, SoundUnit.HERTZ),
    PRESENCE_GAIN(SoundBlock.EQ, SoundUnit.DECIBEL),
    PRESENCE_Q(SoundBlock.EQ, SoundUnit.WIDTH),
    AIR_HZ(SoundBlock.EQ, SoundUnit.HERTZ),
    AIR_GAIN(SoundBlock.EQ, SoundUnit.DECIBEL),

    /** The one knob «Сколько»; the five below are its «Подробно». */
    COMP_AMOUNT(SoundBlock.COMPRESSOR, SoundUnit.AMOUNT),
    COMP_THRESHOLD(SoundBlock.COMPRESSOR, SoundUnit.DECIBEL),
    COMP_RATIO(SoundBlock.COMPRESSOR, SoundUnit.RATIO),
    COMP_ATTACK(SoundBlock.COMPRESSOR, SoundUnit.MILLISECOND),
    COMP_RELEASE(SoundBlock.COMPRESSOR, SoundUnit.MILLISECOND),
    COMP_MAKEUP(SoundBlock.COMPRESSOR, SoundUnit.DECIBEL),

    REVERB_DECAY(SoundBlock.REVERB, SoundUnit.SECOND),
    REVERB_PRE_DELAY(SoundBlock.REVERB, SoundUnit.MILLISECOND),
    REVERB_BRIGHTNESS(SoundBlock.REVERB, SoundUnit.PERCENT),
    REVERB_MIX(SoundBlock.REVERB, SoundUnit.PERCENT),

    OUTPUT_GAIN(SoundBlock.OUTPUT, SoundUnit.DECIBEL),
    ;

    /** Frequencies are heard in octaves, so their tracks are logarithmic. */
    val logarithmic: Boolean get() = unit == SoundUnit.HERTZ

    companion object {
        /** The sliders of one band of the equalizer, in the order they stand under the curve. */
        fun ofBand(band: EqBand): List<SoundParam> = when (band) {
            EqBand.LOW_CUT -> listOf(LOW_CUT_HZ)
            EqBand.LOW -> listOf(LOW_HZ, LOW_GAIN)
            EqBand.BODY -> listOf(BODY_HZ, BODY_GAIN, BODY_Q)
            EqBand.PRESENCE -> listOf(PRESENCE_HZ, PRESENCE_GAIN, PRESENCE_Q)
            EqBand.AIR -> listOf(AIR_HZ, AIR_GAIN)
        }
    }
}

/** Reading, writing and stepping a [SoundParam]. Pure: the screen and its view model both lean on it. */
object SoundParams {
    fun range(param: SoundParam, settings: SoundSettings, config: SoundConfig): ParamRange = when (param) {
        SoundParam.LOW_CUT_HZ -> config.lowCutHz
        SoundParam.LOW_HZ -> config.lowHz
        SoundParam.BODY_HZ -> config.bodyHz
        SoundParam.PRESENCE_HZ -> config.presenceHz
        SoundParam.AIR_HZ -> config.airHz
        SoundParam.LOW_GAIN, SoundParam.BODY_GAIN, SoundParam.PRESENCE_GAIN, SoundParam.AIR_GAIN -> config.eqGainDb
        SoundParam.BODY_Q, SoundParam.PRESENCE_Q -> config.bellQ
        SoundParam.COMP_AMOUNT -> ParamRange(0.0, 1.0, CompressorAmount.NOTICEABLY)
        SoundParam.COMP_THRESHOLD -> config.thresholdDb
        SoundParam.COMP_RATIO -> config.ratio
        SoundParam.COMP_ATTACK -> config.attackMs
        SoundParam.COMP_RELEASE -> config.releaseMs
        SoundParam.COMP_MAKEUP -> config.makeupDb
        // the tail is as long as its space allows, and starts where that space starts
        SoundParam.REVERB_DECAY -> config.decayRange(settings.reverb.space)
        SoundParam.REVERB_PRE_DELAY -> config.preDelayMs.copy(default = config.preDelayOf(settings.reverb.space))
        SoundParam.REVERB_BRIGHTNESS -> config.brightness
        SoundParam.REVERB_MIX -> config.mix
        SoundParam.OUTPUT_GAIN -> config.outputGainDb
    }

    /** Null only for «Сколько» once one of its five was set by hand: the knob then says «своё». */
    fun get(param: SoundParam, settings: SoundSettings): Double? = with(settings) {
        when (param) {
            SoundParam.LOW_CUT_HZ -> eq.lowCut.hz
            SoundParam.LOW_HZ -> eq.low.hz
            SoundParam.LOW_GAIN -> eq.low.gainDb
            SoundParam.BODY_HZ -> eq.body.hz
            SoundParam.BODY_GAIN -> eq.body.gainDb
            SoundParam.BODY_Q -> eq.body.q
            SoundParam.PRESENCE_HZ -> eq.presence.hz
            SoundParam.PRESENCE_GAIN -> eq.presence.gainDb
            SoundParam.PRESENCE_Q -> eq.presence.q
            SoundParam.AIR_HZ -> eq.air.hz
            SoundParam.AIR_GAIN -> eq.air.gainDb
            SoundParam.COMP_AMOUNT -> compressor.amount
            SoundParam.COMP_THRESHOLD -> compressor.thresholdDb
            SoundParam.COMP_RATIO -> compressor.ratio
            SoundParam.COMP_ATTACK -> compressor.attackMs
            SoundParam.COMP_RELEASE -> compressor.releaseMs
            SoundParam.COMP_MAKEUP -> compressor.makeupDb
            SoundParam.REVERB_DECAY -> reverb.decaySec
            SoundParam.REVERB_PRE_DELAY -> reverb.preDelayMs
            SoundParam.REVERB_BRIGHTNESS -> reverb.brightness
            SoundParam.REVERB_MIX -> reverb.mix
            SoundParam.OUTPUT_GAIN -> output.gainDb
        }
    }

    /**
     * The settings with [param] at [value], within its range. The rules come along: «Сколько»
     * leads its five, a hand on any of the five lets the knob go.
     */
    fun set(param: SoundParam, value: Double, settings: SoundSettings, config: SoundConfig): SoundSettings {
        val v = range(param, settings, config).clamp(value)
        return with(settings) {
            when (param) {
                SoundParam.LOW_CUT_HZ -> copy(eq = eq.copy(lowCut = eq.lowCut.copy(hz = v)))
                SoundParam.LOW_HZ -> copy(eq = eq.copy(low = eq.low.copy(hz = v)))
                SoundParam.LOW_GAIN -> copy(eq = eq.copy(low = eq.low.copy(gainDb = v)))
                SoundParam.BODY_HZ -> copy(eq = eq.copy(body = eq.body.copy(hz = v)))
                SoundParam.BODY_GAIN -> copy(eq = eq.copy(body = eq.body.copy(gainDb = v)))
                SoundParam.BODY_Q -> copy(eq = eq.copy(body = eq.body.copy(q = v)))
                SoundParam.PRESENCE_HZ -> copy(eq = eq.copy(presence = eq.presence.copy(hz = v)))
                SoundParam.PRESENCE_GAIN -> copy(eq = eq.copy(presence = eq.presence.copy(gainDb = v)))
                SoundParam.PRESENCE_Q -> copy(eq = eq.copy(presence = eq.presence.copy(q = v)))
                SoundParam.AIR_HZ -> copy(eq = eq.copy(air = eq.air.copy(hz = v)))
                SoundParam.AIR_GAIN -> copy(eq = eq.copy(air = eq.air.copy(gainDb = v)))
                SoundParam.COMP_AMOUNT -> copy(compressor = SoundRules.withAmount(compressor, v))
                SoundParam.COMP_THRESHOLD -> copy(compressor = SoundRules.byHand(compressor.copy(thresholdDb = v)))
                SoundParam.COMP_RATIO -> copy(compressor = SoundRules.byHand(compressor.copy(ratio = v)))
                SoundParam.COMP_ATTACK -> copy(compressor = SoundRules.byHand(compressor.copy(attackMs = v)))
                SoundParam.COMP_RELEASE -> copy(compressor = SoundRules.byHand(compressor.copy(releaseMs = v)))
                SoundParam.COMP_MAKEUP -> copy(compressor = SoundRules.byHand(compressor.copy(makeupDb = v)))
                SoundParam.REVERB_DECAY -> copy(reverb = reverb.copy(decaySec = v))
                SoundParam.REVERB_PRE_DELAY -> copy(reverb = reverb.copy(preDelayMs = v))
                SoundParam.REVERB_BRIGHTNESS -> copy(reverb = reverb.copy(brightness = v))
                SoundParam.REVERB_MIX -> copy(reverb = reverb.copy(mix = v))
                SoundParam.OUTPUT_GAIN -> copy(output = output.copy(gainDb = v))
            }
        }
    }

    /** One press of − or + (spec 5.11): a twelfth of an octave, half a decibel, a millisecond or five, a percent, a tenth of a second. */
    fun stepped(param: SoundParam, value: Double, up: Boolean, range: ParamRange): Double {
        val sign = if (up) 1 else -1
        val next = when (param.unit) {
            SoundUnit.HERTZ -> value * SEMITONE.pow(sign)
            SoundUnit.DECIBEL -> snap(value + sign * DB_STEP, DB_STEP)
            SoundUnit.MILLISECOND -> {
                // fine where a millisecond is heard, coarse where it is not
                val fine = if (up) value < FINE_MS_BELOW else value <= FINE_MS_BELOW
                if (fine) snap(value + sign, 1.0) else snap(value + sign * COARSE_MS_STEP, COARSE_MS_STEP)
            }
            SoundUnit.SECOND -> snap(value + sign * SECOND_STEP, SECOND_STEP)
            SoundUnit.PERCENT, SoundUnit.AMOUNT -> snap(value + sign * PERCENT_STEP, PERCENT_STEP)
            SoundUnit.RATIO -> snap(value + sign * RATIO_STEP, RATIO_STEP)
            SoundUnit.WIDTH -> snap(value + sign * WIDTH_STEP, WIDTH_STEP)
        }
        return range.clamp(next)
    }

    /**
     * A value that came from a finger — a dragged slider, a point of the curve — brought onto the
     * grid its number is shown on: what is heard is then what is written, not «−1,5 дБ» for −1.36.
     */
    fun snapped(param: SoundParam, value: Double): Double = when (param.unit) {
        SoundUnit.HERTZ -> round(value)
        SoundUnit.DECIBEL -> snap(value, DB_STEP)
        SoundUnit.MILLISECOND -> round(value)
        SoundUnit.SECOND -> snap(value, SECOND_STEP)
        SoundUnit.PERCENT, SoundUnit.AMOUNT -> snap(value, PERCENT_STEP)
        SoundUnit.RATIO, SoundUnit.WIDTH -> snap(value, WIDTH_STEP)
    }

    /** Where [value] stands on its track, 0…1. */
    fun fractionOf(param: SoundParam, value: Double, range: ParamRange): Float {
        val v = range.clamp(value)
        val fraction = if (param.logarithmic) ln(v / range.min) / ln(range.max / range.min) else (v - range.min) / (range.max - range.min)
        return fraction.toFloat().coerceIn(0f, 1f)
    }

    /** The value at [fraction] of the track; the inverse of [fractionOf]. */
    fun valueAt(param: SoundParam, fraction: Float, range: ParamRange): Double {
        val f = fraction.coerceIn(0f, 1f).toDouble()
        return if (param.logarithmic) range.min * exp(f * ln(range.max / range.min)) else range.min + f * (range.max - range.min)
    }

    private fun snap(value: Double, step: Double): Double = round(value / step) * step

    private val SEMITONE = 2.0.pow(1.0 / 12)
    private const val DB_STEP = 0.5
    private const val FINE_MS_BELOW = 20.0
    private const val COARSE_MS_STEP = 5.0
    private const val SECOND_STEP = 0.1
    private const val PERCENT_STEP = 0.01
    private const val RATIO_STEP = 0.5
    private const val WIDTH_STEP = 0.1
}
