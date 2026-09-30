package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.ParamRange
import com.violinjourney.app.core.domain.sound.SoundParam
import com.violinjourney.app.core.domain.sound.SoundParams
import com.violinjourney.app.core.domain.sound.SoundUnit

/**
 * The widest texts the value of a slider of «Звук» can take (spec 5.29 R5): the slider keeps their room at the right of its name,
 * so the name does not move — nor the track under the finger — while the value changes. With tabular figures the widest text of a
 * range is at one of its ends or a step within one, where a decimal comes in: «−11,5 дБ» is wider than «−12 дБ», «9,5:1» than
 * «10:1»; a range that reaches a kilohertz is written there with a decimal — «1,1 кГц» is wider than «950 Гц». A text a little wider
 * than any the range can take costs the name a few dp; one narrower would let it move. Pure.
 */
object SliderValues {
    /** Of a parameter of the chain within [range]: its ends and a step within each ([SoundParams.stepped]). */
    fun of(param: SoundParam, range: ParamRange): List<String> {
        val ends = listOf(
            range.min,
            range.max,
            SoundParams.stepped(param, range.min, up = true, range),
            SoundParams.stepped(param, range.max, up = false, range),
        )
        val kilohertz = if (param.unit == SoundUnit.HERTZ && range.max >= KILOHERTZ) listOf(KILOHERTZ_WITH_DECIMAL) else emptyList()
        return (ends + kilohertz).map { SoundFormats.value(param.unit, it) }.distinct()
    }

    /** «Громкость минусовки» (spec 3.32): its level at the ends and half a step within each. */
    fun ofBackingGain(config: BackingConfig): List<String> = listOf(
        config.minGainDb,
        config.maxGainDb,
        config.minGainDb + config.gainStepDb,
        config.maxGainDb - config.gainStepDb,
    ).map { SoundFormats.decibels(it.toDouble(), signed = true) }.distinct()

    /** «Сдвиг» of the backing: the shift at its ends, «−2000 мс» and «+2000 мс» — whole milliseconds, no decimal. */
    fun ofBackingOffset(config: BackingConfig): List<String> = listOf(config.minOffsetMs, config.maxOffsetMs).map(SoundFormats::signedMs).distinct()

    private const val KILOHERTZ = 1_000.0

    /** «1,1 кГц»: a frequency in kilohertz with a decimal, as wide as any of them — the figures are tabular. */
    private const val KILOHERTZ_WITH_DECIMAL = 1_100.0
}
