package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shelves of the shop (spec 3.24, 3.36.7): the whole shop by rows, one row, and the shop by place — only the things of that place,
 * the rows without them gone, the row chosen before not narrowing it; never what the rented room came with; «Полка ваша» only in the
 * whole shop.
 */
class ShopShelvesTest {
    private fun ids(shelves: List<ShopShelf>): List<String> = shelves.flatMap { shelf -> shelf.things.map { it.id } }

    @Test
    fun `the whole shop is every row with every thing for sale once - what the room came with is not sold`() {
        val shelves = ShopShelves.of(category = null, slot = null)
        assertEquals(HomeGroup.entries.toList(), shelves.map { it.group })
        val sold = ids(shelves)
        assertEquals(sold.toSet().size, sold.size, "each thing once")
        assertEquals(HomeCatalog.items.map { it.id }.filter { it !in HomeCatalog.startItems }.toSet(), sold.toSet())
        assertTrue(HomeCatalog.GIFT in sold, "the gift waits on its shelf")
        assertFalse("lamp_table" in sold || "desk_simple" in sold || "curtain_plum" in sold, "the room's own things are not for sale")
    }

    @Test
    fun `a row is that row alone`() {
        val shelves = ShopShelves.of(category = HomeGroup.LIGHT, slot = null)
        assertEquals(listOf(HomeGroup.LIGHT), shelves.map { it.group })
        assertEquals(HomeCatalog.items.filter { it.group == HomeGroup.LIGHT && it.id !in HomeCatalog.startItems }.map { it.id }, ids(shelves))
    }

    @Test
    fun `by place only the things of the place stand on their own shelves and the rows without them go`() {
        val shelves = ShopShelves.of(category = null, slot = "deskR")
        assertEquals(listOf(HomeGroup.MUSIC, HomeGroup.LIGHT, HomeGroup.LIFE), shelves.map { it.group })
        assertEquals(listOf("notes"), shelves[0].things.map { it.id })
        assertEquals(listOf("candles"), shelves[1].things.map { it.id })
        assertEquals(listOf("tea", "books", "passport"), shelves[2].things.map { it.id })
        assertTrue(shelves.all { shelf -> shelf.things.all { it.slot == "deskR" } })
    }

    @Test
    fun `by place the row chosen before does not narrow it - the place is the one filter`() {
        assertEquals(ShopShelves.of(category = null, slot = "deskR"), ShopShelves.of(category = HomeGroup.PET, slot = "deskR"))
    }

    @Test
    fun `by place what the room came with is not on the shelf either`() {
        val shelves = ShopShelves.of(category = null, slot = "deskTop")
        assertEquals(listOf("lamp_brass"), ids(shelves), "the table lamp of the rented room is not sold")
    }

    @Test
    fun `a place with nothing for sale shows no shelf`() {
        // the cat lies on the left sill, but its place is «pet»: no thing has the sill as its place
        assertTrue(ShopShelves.of(category = null, slot = "sillL").isEmpty())
    }

    @Test
    fun `the shelf is yours in the whole shop only - when every thing on it is owned - by place the line is not said`() {
        // every light owned; of music only the notes
        val whole = ShopShelves.of(category = null, slot = null, owned = { it.group == HomeGroup.LIGHT || it.id == "notes" })
        assertTrue(whole.single { it.group == HomeGroup.LIGHT }.yours, "every light is owned")
        assertFalse(whole.single { it.group == HomeGroup.MUSIC }.yours, "one thing of music owned is not the whole shelf")
        assertFalse(whole.single { it.group == HomeGroup.PET }.yours, "nothing of the pets is owned")
        val byPlace = ShopShelves.of(category = null, slot = "deskR", owned = { true })
        assertTrue(byPlace.none { it.yours }, "a shelf of one place is not the whole shelf")
    }

    /** Spec 3.36.7: by place the things bought stay on their shelves «со своим статусом» — a bought thing does not vanish from it. */
    @Test
    fun `by place the things bought stay on their shelves`() {
        val everything = ShopShelves.of(category = null, slot = "deskR", owned = { true })
        assertEquals(listOf("notes", "candles", "tea", "books", "passport"), ids(everything))
        assertEquals(listOf(HomeGroup.MUSIC, HomeGroup.LIGHT, HomeGroup.LIFE), everything.map { it.group })
    }
}
