package com.violinjourney.app.core.audio

import kotlin.math.roundToInt

/**
 * A float sample as PCM16: clipped to full scale beyond −1..1, and a sample that is not a number — a broken input, a
 * decoder's glitch — is silence. `roundToInt` refuses NaN, and on the microphone's thread a throw ends the app; a run of
 * NaN reads as the digital silence `DigitalSilenceWatchdog` reopens the input after (spec 3.4).
 */
internal fun pcm16Of(sample: Float): Short =
    if (sample.isNaN()) 0 else (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
