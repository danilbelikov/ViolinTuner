package com.example.violintuner.core.audio.backing

import android.media.AudioDeviceInfo
import com.example.violintuner.core.domain.backing.AudioRoute
import com.example.violintuner.core.domain.backing.BackingOutput
import com.example.violintuner.core.domain.backing.CalibrationConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which headphones the backing goes to, and the clicks it measures them with (spec 3.32, 5.25). */
class BackingRoutesTest {
    private fun device(type: Int, name: String? = null) = AudioRouteRules.Device(type, name)

    @Test
    fun `wireless headphones come first, the speaker alone is no headphones`() {
        val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel")
        assertEquals(AudioRoute(BackingOutput.SPEAKER, null), AudioRouteRules.routeOf(listOf(speaker, device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))))
        assertEquals(
            AudioRoute(BackingOutput.BLUETOOTH, "Pixel Buds Pro"),
            AudioRouteRules.routeOf(listOf(speaker, device(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "Jack"), device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Pixel Buds Pro"))),
        )
        assertEquals(AudioRoute(BackingOutput.USB, "USB-C"), AudioRouteRules.routeOf(listOf(speaker, device(AudioDeviceInfo.TYPE_USB_HEADSET, "USB-C"))))
        assertEquals(AudioRoute(BackingOutput.WIRED, "Jack"), AudioRouteRules.routeOf(listOf(device(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Jack"), speaker)))
        // outputs that are no ears — a hearing aid, a telephony line — do not count
        assertEquals(AudioRoute(BackingOutput.SPEAKER, null), AudioRouteRules.routeOf(listOf(device(AudioDeviceInfo.TYPE_TELEPHONY))))
    }

    @Test
    fun `the clicks fall on the beat, the lead-in included, with silence between and after them`() {
        val config = CalibrationConfig()
        val rate = 48_000
        val bytes = ClickTrack.pcm(config, rate)
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val left = ShortArray(bytes.size / 4) { shorts.get(2 * it) }
        val beat = (config.beatMs * rate / 1_000).toInt()
        val clickFrames = config.clickMs * rate / 1_000
        repeat(config.leadInClicks + config.clicks) { k ->
            val start = k * beat
            assertTrue("click $k sounds", (start until start + clickFrames).any { abs(left[it].toInt()) > 1_000 })
            assertTrue("silence after click $k", (start + clickFrames until minOf(start + beat, left.size)).all { left[it].toInt() == 0 })
        }
        // no click louder than asked for, and a click without a click of its own at its edges
        assertTrue(left.all { abs(it.toInt()) <= Short.MAX_VALUE / 3 })
        assertEquals(0, left[0].toInt())
        // room after the last click for a late note
        assertTrue(left.size >= beat * (config.leadInClicks + config.clicks - 1) + (config.windowAfterMs * rate / 1_000))
    }
}
