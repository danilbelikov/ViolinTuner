package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens of the «Звук» screen (handoff `Звук.dc.html`, `tokens`). None of them is a zone color:
 * green, amber and red mean intonation everywhere in this app, and a level meter is not a verdict.
 */
@Immutable
data class SoundColors(
    /** The output level: a steel blue. */
    val meterLevel: Color,
    /** «Сейчас сжимает», the "after" of the compressor picture, the decay picture of the hall. */
    val meterReduce: Color,
    /** The limiter is working: the one warning color of the screen. */
    val meterLimit: Color,
    /** The part of the waveform not played yet; the played part is primary. */
    val waveRest: Color,
    /** Between the equalizer curve and 0 dB. */
    val eqFill: Color,
)

internal val DarkSoundColors = SoundColors(
    meterLevel = MeterLevel,
    meterReduce = MeterReduce,
    meterLimit = MeterLimit,
    waveRest = WaveRest,
    eqFill = EqFill,
)

internal val LocalSoundColors = staticCompositionLocalOf<SoundColors> {
    error("SoundColors not provided: wrap content in ViolinTheme")
}
