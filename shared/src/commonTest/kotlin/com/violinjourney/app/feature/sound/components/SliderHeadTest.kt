package com.violinjourney.app.feature.sound.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The line over a slider of «Звук» (spec 5.29 R5): the value keeps its room at the right and is never cut — the name takes what it
 * leaves; the hint follows a name of one line where at least its widest word stands. Pixels.
 */
class SliderHeadTest {
    @Test
    fun `the name takes what the room of the value and the gap leave`() {
        // 603 × 360 behind a cutout at the font 1.3: a card of 270, «−23,5 дБ» 81 and the gap of 8
        assertEquals(181, SliderHead.labelRoom(width = 270, valueRoom = 81, gap = 8))
    }

    @Test
    fun `a value wider than the line leaves the name no room rather than a negative one`() {
        assertEquals(0, SliderHead.labelRoom(width = 60, valueRoom = 81, gap = 8))
    }

    @Test
    fun `the hint follows a name of one line where its widest word stands`() {
        // «Сдвиг» 50 in 200: 142 after it and the gap — «отстаёт — влево» of 60 at its widest word
        assertEquals(142, SliderHead.hintRoom(labelRoom = 200, label = 50, gap = 8, hintLeast = 60, oneLine = true))
        // exactly its widest word: it stands
        assertEquals(60, SliderHead.hintRoom(labelRoom = 118, label = 50, gap = 8, hintLeast = 60, oneLine = true))
    }

    @Test
    fun `no hint after a name of two lines or where its widest word would not stand`() {
        // a name of two lines, whatever width its last line leaves
        assertNull(SliderHead.hintRoom(labelRoom = 200, label = 50, gap = 8, hintLeast = 60, oneLine = false))
        assertNull(SliderHead.hintRoom(labelRoom = 117, label = 50, gap = 8, hintLeast = 60, oneLine = true))
    }
}
