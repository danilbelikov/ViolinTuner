package com.violinjourney.app.feature.practice.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which card of the reminder stands (spec 3.36.9, 5.29 R9; plan D10): the compact one lying and upright in a window lower than 700 dp —
 * by its height — and the full one in every other window. The screen asks it with the height of the window, as the bottom zone does.
 */
class ReminderFitTest {
    @Test
    fun `upright the card is compact in a window lower than 700 and full from 700`() {
        assertTrue(ReminderFit.compact(640.dp, lying = false), "360 × 640")
        assertTrue(ReminderFit.compact(699.dp, lying = false))
        assertTrue(ReminderFit.compact(699.9.dp, lying = false))
        assertFalse(ReminderFit.compact(700.dp, lying = false), "exactly 700: the full card")
        assertFalse(ReminderFit.compact(892.dp, lying = false), "412 × 892")
    }

    @Test
    fun `lying the card is compact in any window`() {
        assertTrue(ReminderFit.compact(412.dp, lying = true), "892 × 412")
        assertTrue(ReminderFit.compact(360.dp, lying = true), "640 × 360")
        assertTrue(ReminderFit.compact(800.dp, lying = true), "a tablet of 1280 × 800 lying")
    }
}
