package com.violinjourney.app.feature.sound

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the list of «Слушать на…» opens (spec 3.36.5): the items of the header of the day of the recording listened on and of its
 * card — each day is its header and then its cards.
 */
class ListenOnPlaceTest {
    private val days = listOf(listOf(12L, 11L), listOf(10L, 9L, 8L, 7L), listOf(6L))

    @Test
    fun `the newest is the first card under the first header`() {
        assertEquals(ListenOnPlace(header = 0, card = 1), ListenOnPlace.of(days, 12))
    }

    @Test
    fun `a card deep in a later day is counted after the headers and cards before it`() {
        // the first day — its header and two cards; then the header of the second day at 3, its fourth card at 3 + 1 + 3
        assertEquals(ListenOnPlace(header = 3, card = 7), ListenOnPlace.of(days, 7))
        assertEquals(ListenOnPlace(header = 8, card = 9), ListenOnPlace.of(days, 6))
    }

    @Test
    fun `no recording listened on opens the list at its top`() {
        assertEquals(ListenOnPlace(header = 0, card = 0), ListenOnPlace.of(days, null))
        assertEquals(ListenOnPlace(header = 0, card = 0), ListenOnPlace.of(days, 99))
    }
}
