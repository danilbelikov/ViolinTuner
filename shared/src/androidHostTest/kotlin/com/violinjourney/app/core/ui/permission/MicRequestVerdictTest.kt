package com.violinjourney.app.core.ui.permission

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MicRequestVerdictTest {
    private fun answer(granted: Boolean = false, before: Boolean = false, after: Boolean = false, ms: Long, refused: Boolean = false) =
        MicRequestVerdict.answerOf(granted, rationaleBefore = before, rationaleAfter = after, answeredInMs = ms, refusedBefore = refused)

    @Test
    fun `allowed is allowed whatever else is told`() {
        assertEquals(MicPermissionAnswer.GRANTED, answer(granted = true, ms = 50, refused = true))
        assertEquals(MicPermissionAnswer.GRANTED, answer(granted = true, before = true, ms = 3_000))
    }

    @Test
    fun `the first refusal raises the rationale — a dialog was shown`() {
        assertEquals(MicPermissionAnswer.DENIED, answer(after = true, ms = 150))
        assertTrue(MicRequestVerdict.isSeenRefusal(granted = false, rationaleBefore = false, rationaleAfter = true))
    }

    @Test
    fun `the second refusal comes from a dialog shown with the rationale`() {
        assertEquals(MicPermissionAnswer.DENIED, answer(before = true, ms = 2_500, refused = true))
        assertTrue(MicRequestVerdict.isSeenRefusal(granted = false, rationaleBefore = true, rationaleAfter = false))
    }

    @Test
    fun `after a refusal seen no rationale means blocked however slow the answer`() {
        // a cold permission controller on a slow phone: this was «denied» by time alone, and the button did nothing
        assertEquals(MicPermissionAnswer.BLOCKED, answer(ms = 900, refused = true))
        assertEquals(MicPermissionAnswer.BLOCKED, answer(ms = 120, refused = true))
    }

    @Test
    fun `without a refusal seen an answer at once came without a dialog`() {
        assertEquals(MicPermissionAnswer.BLOCKED, answer(ms = 120))
    }

    @Test
    fun `without a refusal seen a slow answer is the first dialog closed by a tap beside it`() {
        assertEquals(MicPermissionAnswer.DENIED, answer(ms = 2_000))
        assertFalse(MicRequestVerdict.isSeenRefusal(granted = false, rationaleBefore = false, rationaleAfter = false))
    }

    @Test
    fun `an allowed answer is not a refusal to remember`() {
        assertFalse(MicRequestVerdict.isSeenRefusal(granted = true, rationaleBefore = true, rationaleAfter = true))
    }
}
