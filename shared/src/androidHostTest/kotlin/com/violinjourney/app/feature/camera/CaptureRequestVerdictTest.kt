package com.violinjourney.app.feature.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What Android's answer to the request of the own camera says of each permission (spec 3.36.4): by the rule of the microphone of
 * Live, each with its own mark of a refusal seen in a shown dialog.
 */
class CaptureRequestVerdictTest {
    private fun access(granted: Boolean = false, before: Boolean = false, after: Boolean = false, ms: Long, refused: Boolean = false) =
        CaptureRequestVerdict.accessOf(granted, rationaleBefore = before, rationaleAfter = after, answeredInMs = ms, refusedBefore = refused)

    @Test
    fun `allowed is allowed whatever else is told`() {
        assertEquals(CaptureAccess.GRANTED, access(granted = true, ms = 30, refused = true))
    }

    @Test
    fun `a refusal in a shown dialog may be asked again, and is remembered`() {
        assertEquals(CaptureAccess.ASKABLE, access(after = true, ms = 900))
        assertEquals(CaptureAccess.ASKABLE, access(before = true, ms = 1_800, refused = true))
        assertTrue(CaptureRequestVerdict.isSeenRefusal(granted = false, rationaleBefore = false, rationaleAfter = true))
        assertTrue(CaptureRequestVerdict.isSeenRefusal(granted = false, rationaleBefore = true, rationaleAfter = false))
    }

    @Test
    fun `after a refusal seen no rationale means refused for good however long both dialogs took`() {
        // the camera's dialog shown and answered slowly, the microphone's not shown at all: the whole request took long
        assertEquals(CaptureAccess.BLOCKED, access(ms = 2_600, refused = true))
    }

    @Test
    fun `without a mark an answer at once came without a dialog, a slow one is a dialog closed`() {
        assertEquals(CaptureAccess.BLOCKED, access(ms = 90))
        assertEquals(CaptureAccess.ASKABLE, access(ms = 2_000))
        assertFalse(CaptureRequestVerdict.isSeenRefusal(granted = false, rationaleBefore = false, rationaleAfter = false))
    }

    @Test
    fun `the two permissions are judged apart`() {
        // one request: the camera refused in its dialog (rationale after), the microphone refused for good long ago (its mark)
        val camera = access(after = true, ms = 1_500)
        val mic = access(ms = 1_500, refused = true)
        assertEquals(CaptureAccess.ASKABLE, camera)
        assertEquals(CaptureAccess.BLOCKED, mic)
    }
}
