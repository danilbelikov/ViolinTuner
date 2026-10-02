package com.violinjourney.app.feature.events.screen

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The left column of the screen of an event lying (spec 3.36.9, 5.29 R9): 372, but no wider than 45 % of the window. */
class EventLayoutTest {
    @Test
    fun `the left column is 372 wide in a wide window and 45 percent of a narrow one`() {
        assertEquals(372.dp, EventLayout.leftColumn(892.dp))
        assertEquals(288.dp, EventLayout.leftColumn(640.dp))
        assertEquals(271.35.dp, EventLayout.leftColumn(603.dp), "the window of the emulator lying, 603 × 308")
    }
}
