package com.violinjourney.app.feature.sound

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The subtitle of a recording's «Звук» (spec 3.36.5: «<название> · <режим>», the name gives way): the mode with its separator keeps
 * its room, the name takes the rest — and where not even its first sign with «…» would stand, the mode stands alone, not after a
 * separator that leads nothing. Pixels.
 */
class SubtitleFitTest {
    @Test
    fun `the name takes what the mode and its separator leave`() {
        // lying on 640: 188 for the subtitle, «· как у всех · Камерный зал» 150
        assertEquals(38, SubtitleFit.nameWidth(width = 188, ledMode = 150, leastName = 15))
    }

    @Test
    fun `a name left exactly its least still stands`() {
        assertEquals(15, SubtitleFit.nameWidth(width = 188, ledMode = 173, leastName = 15))
    }

    @Test
    fun `with less than the least of the name left the mode stands alone`() {
        assertNull(SubtitleFit.nameWidth(width = 188, ledMode = 174, leastName = 15))
        // «· свои настройки · сохраняются сами» (233) laid out in 188 fills the line: before, it began with «·»
        assertNull(SubtitleFit.nameWidth(width = 188, ledMode = 188, leastName = 15))
    }
}
