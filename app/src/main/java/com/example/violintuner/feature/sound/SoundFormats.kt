package com.example.violintuner.feature.sound

import com.example.violintuner.core.domain.sound.SoundUnit
import com.example.violintuner.core.ui.format.Formats
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Numbers of the «Звук» screen as text: always with their unit, a decimal comma, a true minus
 * (U+2212) and a non-breaking space before the unit — «2,4 кГц», «−18 дБ», «3,5:1», «1,8 с».
 * The locale is fixed, as everywhere in the app's formats: the interface is Russian.
 */
object SoundFormats {
    private val LOCALE get() = Formats.LOCALE
    private val units get() = Formats.language.sound
    private const val NBSP = ' '
    private const val MINUS = '−'

    fun value(unit: SoundUnit, value: Double): String = when (unit) {
        SoundUnit.HERTZ -> hertz(value)
        SoundUnit.DECIBEL -> decibels(value, signed = true)
        SoundUnit.MILLISECOND -> "${value.roundToInt()}${NBSP}${units.ms}"
        SoundUnit.SECOND -> "${oneDecimal(value)}${NBSP}${units.s}"
        SoundUnit.PERCENT -> "${(value * PERCENT).roundToInt()}${NBSP}%"
        SoundUnit.RATIO -> ratio(value)
        SoundUnit.WIDTH -> oneDecimal(value)
        SoundUnit.AMOUNT -> "${(value * PERCENT).roundToInt()}${NBSP}%"
    }

    /** «80 Гц», «950 Гц», «1 кГц», «2,4 кГц», «12 кГц». */
    fun hertz(hz: Double): String = when {
        hz < KILO -> "${hz.roundToInt()}${NBSP}${units.hz}"
        hz < TEN_KILO -> "${trimmed(hz / KILO)}${NBSP}${units.khz}"
        else -> "${(hz / KILO).roundToInt()}${NBSP}${units.khz}"
    }

    /** «+2,5 дБ», «−18 дБ», «0 дБ»; [signed] false leaves the plus out — for levels, which are all below zero anyway. */
    fun decibels(db: Double, signed: Boolean): String {
        val rounded = (db * 2).roundToInt() / 2.0
        val sign = when {
            rounded < 0 -> MINUS.toString()
            rounded > 0 && signed -> "+"
            else -> ""
        }
        return "$sign${trimmed(abs(rounded))}${NBSP}${units.db}"
    }

    /** «3:1», «3,5:1». */
    fun ratio(ratio: Double): String = "${trimmed(ratio)}:1"

    /** Short axis labels of the curve: «100», «1 к», «10 к». */
    fun axis(hz: Double): String = if (hz < KILO) hz.roundToInt().toString() else "${(hz / KILO).roundToInt()}${NBSP}${units.kilo}"

    private fun oneDecimal(value: Double) = "%.1f".format(LOCALE, value)

    /** One decimal, and none when it would be a zero. */
    private fun trimmed(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else "%.1f".format(LOCALE, tenths / 10.0)
    }

    private const val KILO = 1_000.0
    private const val TEN_KILO = 10_000.0
    private const val PERCENT = 100
}
