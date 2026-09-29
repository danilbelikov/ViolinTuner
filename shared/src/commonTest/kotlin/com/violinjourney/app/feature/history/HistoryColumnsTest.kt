package com.violinjourney.app.feature.history

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The left column of «Записи» lying (spec 3.36.5): 360 as before, but never wider than the list beside it. */
class HistoryColumnsTest {
    @Test
    fun `on 892 the left column is 360 and the list has the rest`() {
        assertEquals(360.dp, HistoryColumns.left(892.dp))
        assertEquals(360.dp, HistoryColumns.left(768.dp))
    }

    @Test
    fun `on 640 and with a cutout at the side the column gives way to the list`() {
        // 640 − 3 × 16 = 592: 296 each — the list keeps what a card needs to show its time and its length
        assertEquals(296.dp, HistoryColumns.left(640.dp))
        // Pixel 7 at 720 × 1280 / 320 on its side: 603.5 without its cutout of 36.5
        assertEquals(277.75.dp, HistoryColumns.left(603.5.dp))
        val list = 603.5.dp - 16.dp * 3 - HistoryColumns.left(603.5.dp)
        assertEquals(HistoryColumns.left(603.5.dp), list)
    }

    @Test
    fun `a window too narrow for two columns gives no negative width`() {
        assertEquals(0.dp, HistoryColumns.left(40.dp))
    }
}
