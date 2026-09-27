package com.violinjourney.app.core.audio.recording

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.assertTrue

/** A stretch of a test sound: a sine of [hz] — silence when null — lasting [seconds]. */
internal class Tone(val hz: Double?, val seconds: Double)

/**
 * An `.m4a` of [tones], mono at [rate], written straight through [AacFile] as fast as it encodes. Not through the take's
 * encoder: [IosAacEncoder] is fed by the microphone in real time and gives up on a queue of about two seconds of hops —
 * a test that offers it three seconds at once fails whenever the machine is too busy to encode as fast (it did, once).
 */
internal fun writeAacTones(path: String, rate: Int, tones: List<Tone>, amplitude: Double = 10_000.0) {
    val aac = AacFile(path, rate, channels = 1)
    val hop = ShortArray(HOP)
    var n = 0L
    tones.forEach { tone ->
        repeat((tone.seconds * rate).roundToInt() / HOP) {
            for (i in hop.indices) {
                hop[i] = if (tone.hz == null) 0 else (sin(2 * PI * tone.hz * n / rate) * amplitude).roundToInt().toShort()
                n++
            }
            assertTrue(aac.write(hop, hop.size), "the encoder took the sound")
        }
    }
    assertTrue(aac.close(), "the file was closed whole")
}

private const val HOP = 512
