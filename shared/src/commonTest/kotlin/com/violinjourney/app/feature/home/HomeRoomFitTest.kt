package com.violinjourney.app.feature.home

import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.feature.journey.PictureFit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What lies on the room of the home and over its zone (spec 3.36.7, 5.29 R7; the review of stage 118): the square «на весь экран» at
 * the bottom end of the room only where it clears «Комната | Снаружи» at the top end — 4 + 48 + 8 + 48 + 8 = 116; and the fade over the
 * zone — 28 upright, none lying, where the room does not scroll. The emulator's 640 × 360 lying (603 × 308 under its bars): the room is
 * 134 at the font 1 (the halves in one row), some 66 to 71 at 1.15 and 1.3 (the halves one under the other). The home and «Обставить»
 * stand lying in a window wider than high where the room keeps 200 beside the column of 320 (the review of stage 119).
 */
class HomeRoomFitTest {
    @Test
    fun `the square stands on a room that holds it clear of the switch - 116 and more`() {
        assertEquals(116.dp, HomeRoomFit.SquareFrom)
        assertTrue(HomeRoomFit.squareFits(116.dp), "116 holds the switch, 8 and the square")
        assertFalse(HomeRoomFit.squareFits(115.dp), "under 116 the square would touch the switch")
    }

    @Test
    fun `lying in the low window the square stands at the font 1 and not at a large font`() {
        assertTrue(HomeRoomFit.squareFits(134.dp), "603 × 308 at the font 1")
        assertFalse(HomeRoomFit.squareFits(71.dp), "at 1.15")
        assertFalse(HomeRoomFit.squareFits(66.dp), "at 1.3")
        assertTrue(HomeRoomFit.squareFits(PictureFit.RoomMin), "upright the room is 200 at least: the square always stands")
    }

    @Test
    fun `upright the zone fades over the words and lying there is no fade`() {
        assertEquals(DockDefaults.Fade, HomeRoomFit.fade(lying = false))
        assertEquals(28.dp, HomeRoomFit.fade(lying = false))
        assertEquals(0.dp, HomeRoomFit.fade(lying = true))
    }

    @Test
    fun `lying is a window wider than high where the room keeps 200 beside the column of 320`() {
        assertTrue(HomeRoomFit.lying(windowWide = true, width = 603.dp), "640 × 360 lying: the room 235")
        assertTrue(HomeRoomFit.lying(windowWide = true, width = 862.dp), "a phone lying beside its cutout")
        assertTrue(HomeRoomFit.lying(windowWide = true, width = 568.dp), "the room 200 — enough")
        assertFalse(HomeRoomFit.lying(windowWide = true, width = 567.dp), "the room 199 — upright")
        assertFalse(HomeRoomFit.lying(windowWide = true, width = 456.dp), "the half of a phone lying: the room would be 88")
        assertFalse(HomeRoomFit.lying(windowWide = false, width = 412.dp), "the half of a split screen upright: its box under the bar is wider than high, the window is not")
        assertFalse(HomeRoomFit.lying(windowWide = false, width = 900.dp), "a window higher than wide is upright, however wide")
        assertEquals(235.dp, HomeRoomFit.roomBeside(603.dp))
    }
}
