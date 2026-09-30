package com.violinjourney.app.feature.sound

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The left column of «Звук» lying (spec 3.36.5): 340, but never wider than half of what the window leaves after its fields. */
class SoundColumnsTest {
    @Test
    fun `on 892 the left column is 340`() {
        assertEquals(340.dp, SoundColumns.left(892.dp))
        assertEquals(340.dp, SoundColumns.left(728.dp))
    }

    @Test
    fun `on 640 and with a cutout at the side the column gives way to the cards`() {
        // 640 − 3 × 16 = 592: 296 each — the cards keep room for «Компрессор» beside the switch
        assertEquals(296.dp, SoundColumns.left(640.dp))
        // Pixel 7 at 720 × 1280 / 320 on its side: 603.5 without its cutout of 36.5
        assertEquals(277.75.dp, SoundColumns.left(603.5.dp))
    }

    @Test
    fun `a window too narrow for two columns gives no negative width`() {
        assertEquals(0.dp, SoundColumns.left(40.dp))
    }
}
