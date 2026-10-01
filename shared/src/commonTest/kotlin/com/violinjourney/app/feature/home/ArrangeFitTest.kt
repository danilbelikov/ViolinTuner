package com.violinjourney.app.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the room and the places of «Обставить» stand (spec 3.36.7, 5.29 R7; open question 16, the review of stage 119): lying — the
 * room beside the places on the whole height of its column (when lying, [HomeRoomFitTest]); upright the room 210 over them, giving way
 * to the caption and one whole place, down to 120.
 */
class ArrangeFitTest {
    /** The caption and one whole place at the font 1: 12 + 18 + 8 + 12 + 20 + 14 + 72 + 3 + 12. */
    private val placesLeast = 171.dp
    private val column = 560.dp

    private fun DpRect.size(): Pair<Dp, Dp> = (right - left) to (bottom - top)

    @Test
    fun `lying the room stands beside the places on the whole height of its column`() {
        // the emulator's 640 × 360 lying: 603 × 308 under its bars, 260 under the bar of 48
        val frames = ArrangeFit.frames(603.dp, 260.dp, lying = true, column = column, placesLeast = placesLeast)
        assertEquals(DpRect(16.dp, 4.dp, 251.dp, 244.dp), frames.room, "the room 235 × 240, 4 over it and 16 under it")
        assertEquals(DpRect(267.dp, 0.dp, 587.dp, 260.dp), frames.places, "the places 320, 16 after the room, to the bottom")
    }

    @Test
    fun `upright on a phone the room is 210 over the places`() {
        val frames = ArrangeFit.frames(360.dp, 536.dp, lying = false, column = column, placesLeast = placesLeast)
        assertEquals(DpRect(16.dp, 0.dp, 344.dp, 210.dp), frames.room)
        assertEquals(DpRect(0.dp, 210.dp, 360.dp, 536.dp), frames.places)
        // a tablet upright: the column of 560 in the middle
        val wide = ArrangeFit.frames(800.dp, 1_200.dp, lying = false, column = column, placesLeast = placesLeast)
        assertEquals(DpRect(136.dp, 0.dp, 664.dp, 210.dp), wide.room)
        assertEquals(DpRect(120.dp, 210.dp, 680.dp, 1_200.dp), wide.places)
    }

    @Test
    fun `upright in a low box the room gives way to the caption and one whole place - down to 120`() {
        // the half of a split screen: 412 × 346 under the bar of 56 — a room of 210 left the places 136, the first place cut
        val frames = ArrangeFit.frames(412.dp, 346.dp, lying = false, column = column, placesLeast = placesLeast)
        assertEquals(175.dp, frames.room.bottom)
        assertEquals(placesLeast, frames.places.size().second, "the places keep the caption and one whole place")
        assertEquals(210.dp, ArrangeFit.roomHeight(381.dp, placesLeast), "from 381 on the room is 210")
        assertEquals(209.dp, ArrangeFit.roomHeight(380.dp, placesLeast))
        assertEquals(120.dp, ArrangeFit.roomHeight(250.dp, placesLeast), "never lower than 120: the places scroll in what is left")
    }
}
