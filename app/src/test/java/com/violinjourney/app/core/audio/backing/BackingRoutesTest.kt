package com.violinjourney.app.core.audio.backing

import android.media.AudioDeviceInfo
import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import org.junit.Assert.assertEquals
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
    fun `the device media go to is the route`() {
        assertEquals(AudioRoute(BackingOutput.WIRED, "Jack"), AudioRouteRules.routeOf(listOf(device(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "Jack"))))
        // the output switcher put media on «This phone» while the headphones stay connected
        assertEquals(AudioRoute(BackingOutput.SPEAKER, null), AudioRouteRules.routeOf(listOf(device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel"))))
    }

    @Test
    fun `below Android 13 a Bluetooth device only on its call profile is no headphones for music`() {
        val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel")
        assertEquals(AudioRoute(BackingOutput.SPEAKER, null), AudioRouteRules.likelyRouteOf(listOf(speaker, device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Car kit"))))
    }

    @Test
    fun `below Android 13 a headset on both profiles counts as wireless`() {
        val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel")
        assertEquals(
            AudioRoute(BackingOutput.BLUETOOTH, "Buds"),
            AudioRouteRules.likelyRouteOf(listOf(speaker, device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Buds"), device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Buds"))),
        )
        assertEquals(
            AudioRoute(BackingOutput.WIRED, "Jack"),
            AudioRouteRules.likelyRouteOf(listOf(speaker, device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Car kit"), device(AudioDeviceInfo.TYPE_WIRED_HEADSET, "Jack"))),
        )
    }
}
