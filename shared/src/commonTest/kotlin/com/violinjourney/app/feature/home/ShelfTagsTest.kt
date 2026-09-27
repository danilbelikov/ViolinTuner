package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ShelfTagsTest {
    private val loaded = HomeState.EMPTY.copy(loaded = true)
    private val october = LocalDate(2026, 10, 1)
    private val progress = JourneyProgress(earned = 10_000, spent = 0, arrivals = listOf(Arrival("home", 1), Arrival("vienna", 2)), extras = emptySet())
    private fun item(id: String) = HomeCatalog.byId.getValue(id)

    @Test
    fun `in the room means seen in the home lived in - a thing whose place this home lacks is only bought`() {
        val rent = loaded.copy(purchased = setOf("piano", "wp_damask"), choices = mapOf("floorL" to "piano", "wallpaper" to "wp_damask"))
        val inRent = ShelfTags(rent, progress, "rent", october)
        assertEquals(ShelfTag.STANDING, inRent.of(item("piano")))
        assertEquals(ShelfTag.STANDING, inRent.of(item("wp_damask")))
        // the same things after moving to the wooden house: it has no place by the left wall and no wallpaper
        val wood = rent.copy(houses = setOf("wood"), choices = rent.choices + (HomeState.HOUSE_KEY to "wood"))
        val inWood = ShelfTags(wood, progress, "wood", october)
        assertEquals(ShelfTag.OWNED, inWood.of(item("piano")))
        assertEquals(ShelfTag.OWNED, inWood.of(item("wp_damask")))
        // still the choice of their places: nothing to put there, they wait for a home that has them
        assertTrue(inWood.chosen(item("piano")) && inWood.chosen(item("wp_damask")))
    }

    @Test
    fun `the tree is in the room only in its season`() {
        val state = loaded.copy(purchased = setOf("xmas"), choices = mapOf("floorR" to "xmas"))
        assertEquals(ShelfTag.OWNED, ShelfTags(state, progress, "rent", october).of(item("xmas")))
        assertEquals(ShelfTag.STANDING, ShelfTags(state, progress, "rent", LocalDate(2026, 12, 10)).of(item("xmas")))
        assertTrue(ShelfTags(state, progress, "rent", october).chosen(item("xmas")))
    }

    @Test
    fun `a thing outside is at the house - on the shelf before it is bought it says its price - a gift - or that it comes later`() {
        val state = loaded.copy(purchased = setOf("mailbox", "ficus"), choices = mapOf("oL" to "mailbox"))
        val tags = ShelfTags(state, progress, "rent", october)
        assertEquals(ShelfTag.STANDING, tags.of(item("mailbox")))
        // bought, but its place holds another thing: bought and put away
        assertEquals(ShelfTag.OWNED, tags.of(item("ficus")))
        assertFalse(tags.chosen(item("ficus")))
        assertEquals(ShelfTag.LOCKED, tags.of(item("clock")), "Prague is not reached")
        assertEquals(ShelfTag.GIFT, tags.of(item(HomeCatalog.GIFT)))
        assertEquals(ShelfTag.PRICE, tags.of(item("floorlamp")))
        assertTrue(tags.owned(item("mailbox")) && tags.owned(item("desk_simple")) && !tags.owned(item("floorlamp")))
    }
}
