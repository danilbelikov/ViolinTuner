package com.violinjourney.app.feature.repertoire.form

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.DockMetrics
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bottom zone of a form is one line — the reason and the button of 48 — only where less than 200 dp are left (spec 3.36.1 rule 3),
 * and it goes under the fields where the keyboard leaves less than that line and a field together (5.29 R4; the lead's finding on the
 * emulator in stage 110 — Pixel 7 lying, Gboard ≈ 262 dp: the line left the title in focus a strip of 16 dp).
 *
 * The numbers are those of the layout: the line — the paddings of the zone ([DockMetrics.of] the height of the window) and its button
 * of 48; the field — 8 of air over it at the top of the column and the title with its caption, 20 + 6 + 56 (`appFieldLeastHeight` at
 * the font of 1.0, which `AppFieldTest` holds to the field itself). The bar is 48 lying; the windows of the tests have no status bar.
 */
class FormDockModeTest {
    @Test
    fun `less than 200 between the bar and the keyboard makes one line`() {
        assertEquals(FormDockMode.ROW, FormDockMode.of(199.dp))
        assertEquals(FormDockMode.ROW, FormDockMode.of(170.dp), "landscape 412 with the keyboard (landscape.html 5)")
        assertEquals(FormDockMode.ROW, FormDockMode.of(0.dp))
    }

    @Test
    fun `200 and more keep the reason over the button`() {
        assertEquals(FormDockMode.COLUMN, FormDockMode.of(200.dp))
        assertEquals(FormDockMode.COLUMN, FormDockMode.of(812.dp))
    }

    @Test
    fun `a room not measured yet is no reason for a line`() {
        assertEquals(FormDockMode.COLUMN, FormDockMode.of(Dp.Unspecified))
        assertEquals(FormDockMode.COLUMN, FormDockMode.of(Dp.Unspecified, keyboardUp = true, line = LINE_412, field = FIELD))
    }

    @Test
    fun `892 x 412 - a keyboard of 262 sends the line under the fields and one of 200 keeps it`() {
        assertEquals(66.dp, LINE_412, "the line of 412: 8 + 48 + 10")
        // 412 − 48 − 262 = 102: the line would leave the title 36 of its 90
        assertEquals(FormDockMode.IN_CONTENT, lying(412.dp, keyboard = 262.dp))
        // 412 − 48 − 200 = 164: the line and the title, 156, both stand
        assertEquals(FormDockMode.ROW, lying(412.dp, keyboard = 200.dp))
    }

    @Test
    fun `640 x 360 - a keyboard of 230 or even 200 sends the line under the fields`() {
        assertEquals(64.dp, LINE_360, "the line of 360: 8 + 48 + 8")
        // 360 − 48 − 230 = 82: the line alone takes 64
        assertEquals(FormDockMode.IN_CONTENT, lying(360.dp, keyboard = 230.dp))
        // 360 − 48 − 200 = 112: still less than the line and the title, 154
        assertEquals(FormDockMode.IN_CONTENT, lying(360.dp, keyboard = 200.dp))
        // a keyboard of 150 leaves 162: the line holds
        assertEquals(FormDockMode.ROW, lying(360.dp, keyboard = 150.dp))
    }

    /** As the emulator had it: the bar ends 76 dp from the top of the window of 411 (1080 px at 420 dpi) under the status bar. */
    @Test
    fun `the room the emulator measured sends the line under the fields`() {
        val room = 411.43.dp - 76.dp - 262.dp
        assertEquals(FormDockMode.IN_CONTENT, FormDockMode.of(room, keyboardUp = true, line = LINE_412, field = FIELD))
    }

    @Test
    fun `the line holds exactly as long as the room holds both`() {
        val both = LINE_412 + FIELD
        assertEquals(FormDockMode.ROW, FormDockMode.of(both, keyboardUp = true, line = LINE_412, field = FIELD))
        assertEquals(FormDockMode.IN_CONTENT, FormDockMode.of(both - 0.5.dp, keyboardUp = true, line = LINE_412, field = FIELD))
    }

    @Test
    fun `a line higher than its button - a plate of two lines - needs more room`() {
        // «Такая гамма уже есть…» of two lines: 10 + 36 + 10 = 56 — the line of 412 is 74
        val line = DockMetrics.Low.top + 56.dp + DockMetrics.Low.bottom
        assertEquals(FormDockMode.ROW, lying(412.dp, keyboard = 200.dp, line = LINE_412))
        assertEquals(FormDockMode.ROW, lying(412.dp, keyboard = 200.dp, line = line), "164 holds 74 + 90")
        assertEquals(FormDockMode.IN_CONTENT, lying(412.dp, keyboard = 210.dp, line = line), "154 does not")
    }

    @Test
    fun `without a keyboard a low window keeps its line pinned - nothing is typed`() {
        assertEquals(FormDockMode.ROW, FormDockMode.of(100.dp, keyboardUp = false, line = LINE_412, field = FIELD))
        assertEquals(FormDockMode.ROW, FormDockMode.of(412.dp - 48.dp - 262.dp, line = LINE_412, field = FIELD))
    }

    @Test
    fun `portrait and a taller landscape keep the column over the keyboard`() {
        // 412 × 892 upright: the bar 56, a keyboard of 300
        assertEquals(FormDockMode.COLUMN, FormDockMode.of(892.dp - 56.dp - 300.dp, keyboardUp = true, line = LINE_412, field = FIELD))
        // 360 × 640 upright, a keyboard of 280
        assertEquals(FormDockMode.COLUMN, FormDockMode.of(640.dp - 56.dp - 280.dp, keyboardUp = true, line = LINE_360, field = FIELD))
        // a tablet lying, 1280 × 800, a keyboard of 350
        assertEquals(FormDockMode.COLUMN, FormDockMode.of(800.dp - 48.dp - 350.dp, keyboardUp = true, line = LINE_412, field = FIELD))
    }

    /** A form lying in a window [height] high, the bar of 48 over it and the [keyboard] under it. */
    private fun lying(height: Dp, keyboard: Dp, line: Dp = lineOf(height)): FormDockMode =
        FormDockMode.of(height - LYING_BAR - keyboard, keyboardUp = true, line = line, field = FIELD)

    private fun lineOf(window: Dp): Dp = DockMetrics.of(window).let { it.top + LINE_BUTTON + it.bottom }

    private companion object {
        val LYING_BAR = 48.dp
        val LINE_BUTTON = 48.dp
        val LINE_412 = DockMetrics.of(412.dp).let { it.top + LINE_BUTTON + it.bottom }
        val LINE_360 = DockMetrics.of(360.dp).let { it.top + LINE_BUTTON + it.bottom }

        /** 8 of air, the caption 20, 6, the frame 56. */
        val FIELD = 8.dp + 20.dp + 6.dp + 56.dp
    }
}
