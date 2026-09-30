package com.violinjourney.app.feature.live.components

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.feature.live.LiveReducer
import com.violinjourney.app.feature.live.TuningState
import com.violinjourney.app.feature.live.venue.VenueLook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The row of strings in the dark (spec 3.36.6, «Свет и приглушение»; 5.29 R6): it dims button by button — the target, locked or the
 * nearest in auto, keeps its whole alpha while the others go down with the light to 0.38; without the permission all four are 0.4
 * times the light; a button that stops being the target fades to the light rather than dropping to it in a frame (spec 3.14).
 */
class StringLightTest {
    private val out = VenueLook.chromeAlpha(1f)
    private val stringHz = LiveReducer.stringHzOf(IntonationConfig())

    private fun tuning(locked: ViolinString? = null, target: ViolinString? = locked) =
        TuningState(lockedString = locked, targetString = target, stringHz = stringHz)

    /** The alpha of [string] in [tuning] once its fade is over, with [base] and the light of the room [light]. */
    private fun settled(string: ViolinString, tuning: TuningState, base: Float, light: Float): Float =
        StringLight.alpha(base, light, StringLight.wholeness(stringLookOf(string, tuning), base))

    @Test
    fun `the look of a string is the lock the nearest or the glass`() {
        assertEquals(StringLook.LOCKED, stringLookOf(ViolinString.D4, tuning(locked = ViolinString.D4)))
        assertEquals(StringLook.NEAREST, stringLookOf(ViolinString.A4, tuning(target = ViolinString.A4)))
        assertEquals(StringLook.PLAIN, stringLookOf(ViolinString.G3, tuning(target = ViolinString.A4)))
        // the locked string is the target too: it looks locked, not nearest
        assertEquals(StringLook.LOCKED, stringLookOf(ViolinString.D4, tuning(locked = ViolinString.D4, target = ViolinString.D4)))
        assertEquals(StringLook.PLAIN, stringLookOf(ViolinString.E5, tuning()))
    }

    @Test
    fun `in the dark the target stays whole and the others go down with the light`() {
        assertEquals(0.38f, out, 0.0001f)
        // auto, A sounds: A whole, the three others with the light
        assertEquals(1f, settled(ViolinString.A4, tuning(target = ViolinString.A4), base = 1f, light = out), 0.0001f)
        listOf(ViolinString.G3, ViolinString.D4, ViolinString.E5).forEach { string ->
            assertEquals(out, settled(string, tuning(target = ViolinString.A4), base = 1f, light = out), 0.0001f, "$string")
        }
        // D locked: D whole
        assertEquals(1f, settled(ViolinString.D4, tuning(locked = ViolinString.D4), base = 1f, light = out), 0.0001f)
        // the light on: all whole
        assertEquals(1f, settled(ViolinString.G3, tuning(target = ViolinString.A4), base = 1f, light = 1f), 0.0001f)
    }

    @Test
    fun `without the permission all four are dimmed the target too`() {
        val noMic = LiveDimens.CHROME_ALPHA_NO_MIC
        ViolinString.entries.forEach { string ->
            assertEquals(noMic * out, settled(string, tuning(locked = ViolinString.D4), base = noMic, light = out), 0.0001f, "$string in the dark")
            assertEquals(noMic, settled(string, tuning(locked = ViolinString.D4), base = noMic, light = 1f), 0.0001f, "$string in the light")
        }
    }

    @Test
    fun `a target that stops being one fades to the light and does not drop in a frame`() {
        // halfway through its fade the button that was the target stands between its whole alpha and the light, both ways
        val halfway = StringLight.alpha(base = 1f, light = out, whole = 0.5f)
        assertTrue(halfway > out && halfway < 1f, "halfway is $halfway")
        assertEquals((1f + out) / 2, halfway, 0.0001f)
        // the fade goes from the target to the light and no further
        assertEquals(1f, StringLight.alpha(base = 1f, light = out, whole = 1f), 0.0001f)
        assertEquals(out, StringLight.alpha(base = 1f, light = out, whole = 0f), 0.0001f)
    }
}
