package com.violinjourney.app.feature.repertoire.form

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What of the field in focus the column shows once the keyboard stands (spec 3.36.4 «Клавиатура», 5.29 R4): a field the window holds
 * with its air comes to the top of the column; a higher one gives the window to the line being typed — the line of the cursor with
 * the frame's bottom padding stands on the keyboard, the caption goes up out of sight (the lead's finding on the emulator in stage 110:
 * 640 × 360, 55 dp between the bar and the keyboard — the caption of the title stood whole, and the line typed was cut in half).
 *
 * In dp of a field at the font of 1.0: the title — the caption 20, 6, the frame 26…82 with its line 42…66, so the line with the
 * paddings of the frame is 26…82; the notes — the frame of three lines 26…130 and the counter under it, 154 in all.
 */
class FieldRiseTest {
    @Test
    fun `a field the window holds with its air comes to the top of the column`() {
        assertEquals(Rect(0f, -AIR, 1f, 94f), FieldRise.target(field = TITLE, window = 102f, air = AIR, typedLine = TITLE_LINE))
        // exactly the field and its air: still from the top
        assertEquals(Rect(0f, -AIR, 1f, TITLE), FieldRise.target(field = TITLE, window = TITLE + AIR, air = AIR, typedLine = TITLE_LINE))
    }

    @Test
    fun `640 x 360 - the line being typed stands on the keyboard and the caption goes up`() {
        val shown = FieldRise.target(field = TITLE, window = 55f, air = AIR, typedLine = TITLE_LINE)
        assertEquals(Rect(0f, 27f, 1f, 82f), shown)
        assertTrue(shown.top <= TEXT_TOP && TEXT_BOTTOM <= shown.bottom, "the line typed whole: $shown")
        assertEquals(TITLE_LINE.bottom, shown.bottom, "the frame's bottom padding on the keyboard")
        assertTrue(shown.top >= CAPTION_BOTTOM, "the caption out of sight: $shown")
    }

    @Test
    fun `lying on the 892 x 412 of the emulator - 73 over the keyboard - the line wins over the caption too`() {
        val shown = FieldRise.target(field = TITLE, window = 73f, air = AIR, typedLine = TITLE_LINE)
        assertEquals(Rect(0f, 9f, 1f, 82f), shown)
        assertTrue(shown.top <= TEXT_TOP && TEXT_BOTTOM <= shown.bottom, "the line typed whole: $shown")
    }

    @Test
    fun `the notes show the line of the cursor - not the bottom of the field`() {
        // the cursor on the first line of three: its line with the paddings is 26…82, the field ends at 154
        val shown = FieldRise.target(field = NOTES, window = 55f, air = AIR, typedLine = Rect(0f, 26f, 1f, 82f))
        assertEquals(Rect(0f, 27f, 1f, 82f), shown)
        // the cursor on the third line: 74…130
        assertEquals(Rect(0f, 75f, 1f, 130f), FieldRise.target(field = NOTES, window = 55f, air = AIR, typedLine = Rect(0f, 74f, 1f, 130f)))
    }

    @Test
    fun `a line not laid out yet leaves the bottom of the field on the keyboard`() {
        assertEquals(Rect(0f, 27f, 1f, TITLE), FieldRise.target(field = TITLE, window = 55f, air = AIR, typedLine = null))
    }

    private companion object {
        const val AIR = 8f
        const val TITLE = 82f
        const val NOTES = 154f
        const val CAPTION_BOTTOM = 20f
        const val TEXT_TOP = 42f
        const val TEXT_BOTTOM = 66f
        val TITLE_LINE = Rect(0f, 26f, 1f, 82f)
    }
}
