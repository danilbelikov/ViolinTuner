package com.violinjourney.app.core.domain.home

import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class HomeRulesTest {
    private val loaded = HomeState.EMPTY.copy(loaded = true)
    private val september = LocalDate(2026, 9, 21)
    private fun item(id: String) = HomeCatalog.byId.getValue(id)
    private fun progress(balance: Long, vararg stops: String) = JourneyProgress(earned = balance, spent = 0, arrivals = stops.map { Arrival(it, 1) }, extras = emptySet())

    @Test
    fun `the catalogue is whole - every thing has a place that exists — every city is on the route — ids are unique`() {
        val items = HomeCatalog.items
        assertTrue(items.size >= 100)
        assertEquals(items.size, items.map { it.id }.toSet().size)
        items.forEach { item ->
            assertTrue(item.slot in HomeCatalog.slotById, "${item.id} stands in a place that does not exist")
            item.at?.let { assertTrue(it in HomeCatalog.slotById, "${item.id} lies in a place that does not exist") }
            item.from?.let { assertTrue(JourneyRoute.indexOf(it) > 0, "${item.id} comes from a city off the route") }
            assertEquals(item.outside, HomeCatalog.slotById.getValue(item.slot).outside, "${item.id}: outside things stand in outside places")
            assertTrue(item.price >= 0)
        }
        // colours are not things, things are not colours
        assertTrue(items.filter { HomeCatalog.slotById.getValue(it.slot).palette }.none { it.drawn })
        assertEquals(listOf("rent", "wood"), HomeCatalog.houses.filter { it.drawn }.map { it.id })
        assertEquals(HomeCatalog.houses.map { it.price }.sorted(), HomeCatalog.houses.map { it.price })
    }

    @Test
    fun `the rented room comes with what costs nothing - but the present waits in the shop`() {
        val placed = HomeRules.placed(loaded)
        assertEquals("wp_plum", placed["wallpaper"]?.id)
        assertEquals("desk_simple", placed["desk"]?.id)
        assertEquals("case_black", placed["case"]?.id)
        assertNull(placed["violin"])
        // the plum rug left the room for the shop (spec 3.25)
        assertNull(placed["rug"])
        assertEquals(100, item("rug_plum").price)
        assertTrue(HomeRules.giftWaiting(loaded))
        assertFalse(HomeRules.giftWaiting(HomeState.EMPTY))
        assertFalse(HomeRules.giftWaiting(loaded.copy(purchased = setOf(HomeCatalog.GIFT))))
        assertEquals("rent", HomeRules.house(loaded))
    }

    @Test
    fun `a thing is bought when it is on the shelf — not owned yet and can be paid for`() {
        val clock = item("clock") // 700, from Prague
        assertFalse(HomeRules.unlocked(clock, progress(5_000, "home", "cremona")))
        assertFalse(HomeRules.canBuy(clock, loaded, progress(5_000, "home", "cremona")))
        assertTrue(HomeRules.canBuy(clock, loaded, progress(700, "home", "prague")))
        assertFalse(HomeRules.canBuy(clock, loaded, progress(699, "home", "prague")))
        assertFalse(HomeRules.canBuy(clock, loaded.copy(purchased = setOf("clock")), progress(5_000, "home", "prague")))
        // what the room came with is owned
        assertFalse(HomeRules.canBuy(item("desk_simple"), loaded, progress(5_000, "home")))
    }

    @Test
    fun `one place holds one thing - a choice replaces — an empty choice leaves the place bare — a thing not owned is not placed`() {
        val state = loaded.copy(purchased = setOf("desk_oak", "cat_ginger"), choices = mapOf("desk" to "desk_oak", "pet" to "cat_ginger", "deskTop" to "", "chair" to "rocking"))
        val placed = HomeRules.placed(state)
        assertEquals("desk_oak", placed["desk"]?.id)
        assertEquals("cat_ginger", placed["pet"]?.id)
        assertNull(placed["deskTop"])
        assertEquals("chair_simple", placed["chair"]?.id)
        assertEquals(listOf("desk_simple", "desk_oak"), HomeRules.wardrobe("desk", state).map { it.id })
    }

    @Test
    fun `a desk and a chair are never bare - what stands on them never hangs in the air`() {
        // «пусто» saved for a desk, a chair or the window before it went from them reads as what the room came with
        val state = loaded.copy(purchased = setOf("plaid"), choices = mapOf("desk" to "", "chair" to "", "window" to "", "chairTop" to "plaid"))
        val placed = HomeRules.placed(state)
        assertEquals("desk_simple", placed["desk"]?.id)
        assertEquals("chair_simple", placed["chair"]?.id)
        assertEquals("window_simple", placed["window"]?.id)
        val standing = HomeRules.standing(state, "rent", false, september).map { it.id }
        assertTrue("lamp_table" in standing && "desk_simple" in standing, "the lamp stands on a desk")
        assertTrue("plaid" in standing && "chair_simple" in standing, "the plaid lies on a chair")
        // what stands on them may still be taken away: a desk without a lamp is a choice
        assertNull(HomeRules.placed(state.copy(choices = state.choices + ("deskTop" to "")))["deskTop"])
        assertTrue(HomeCatalog.NEVER_BARE.containsAll(listOf("window", "view", "desk", "chair")))
    }

    @Test
    fun `things move with their owner - those whose place the new home lacks wait in the wardrobe`() {
        val state = loaded.copy(
            purchased = setOf("bookshelf", "fireplace", "wp_damask", "mailbox", "vane"),
            houses = setOf("wood"),
            choices = mapOf("floorL" to "bookshelf", "fire" to "fireplace", "wallpaper" to "wp_damask", "oL" to "mailbox", "oRoof" to "vane", HomeState.HOUSE_KEY to "wood"),
        )
        assertEquals("wood", HomeRules.house(state))
        val inWood = HomeRules.standing(state, "wood", outside = false, september).map { it.id }
        val inRent = HomeRules.standing(state, "rent", outside = false, september).map { it.id }
        assertTrue("fireplace" in inWood && "bookshelf" !in inWood && "wp_damask" !in inWood)
        assertTrue("fireplace" !in inRent && "bookshelf" in inRent && "wp_damask" in inRent)
        assertEquals(listOf("mailbox", "vane"), HomeRules.standing(state, "wood", outside = true, september).map { it.id }.sorted())
        assertEquals(listOf("mailbox"), HomeRules.standing(state, "rent", outside = true, september).map { it.id })
        // back to front
        assertEquals(inWood.map { item(it).z }.sorted(), inWood.map { item(it).z })
        // a home that was not bought is not lived in
        assertEquals("rent", HomeRules.house(state.copy(houses = emptySet())))
    }

    @Test
    fun `the tree stands from December to the middle of January`() {
        val state = loaded.copy(purchased = setOf("xmas"), choices = mapOf("floorR" to "xmas"))
        fun stands(date: LocalDate) = "xmas" in HomeRules.standing(state, "rent", false, date).map { it.id }
        assertFalse(stands(september))
        assertTrue(stands(LocalDate(2026, 12, 1)))
        assertTrue(stands(LocalDate(2027, 1, 15)))
        assertFalse(stands(LocalDate(2027, 1, 16)))
    }

    @Test
    fun `the tree does not evict - out of its season what stood in its place stands there`() {
        val tree = loaded.copy(purchased = setOf("xmas", "cello"), choices = mapOf("floorR" to "xmas", HomeState.SEASON_KEY to "cello"))
        fun byTheRightWall(state: HomeState, date: LocalDate) = HomeRules.standing(state, "rent", false, date).filter { it.slot == "floorR" }.map { it.id }
        assertEquals(listOf("cello"), byTheRightWall(tree, september))
        assertEquals(listOf("xmas"), byTheRightWall(tree, LocalDate(2026, 12, 10)))
        assertEquals(listOf("cello"), byTheRightWall(tree, LocalDate(2027, 1, 20)))
        // a tree put in before what stood there was remembered: the room's own, which is nothing by the right wall
        assertEquals(emptyList(), byTheRightWall(tree.copy(choices = mapOf("floorR" to "xmas")), september))
        // the place was bare on purpose
        assertEquals(emptyList(), byTheRightWall(tree.copy(choices = mapOf("floorR" to "xmas", HomeState.SEASON_KEY to "")), september))
        // a thing remembered but not owned, or of another place, is not shown
        assertEquals(emptyList(), byTheRightWall(tree.copy(choices = mapOf("floorR" to "xmas", HomeState.SEASON_KEY to "ficus")), september))
        assertEquals(emptyList(), byTheRightWall(tree.copy(choices = mapOf("floorR" to "xmas", HomeState.SEASON_KEY to "desk_simple")), september))
        // the keys of the home are not places, whatever they hold
        assertTrue(HomeRules.placed(tree).keys.none { it.startsWith("@") })
        assertEquals(HomeState.SEASON_KEY, HomeRules.underKeyOf(item("xmas")))
        assertNull(HomeRules.underKeyOf(item("cello")))
    }

    @Test
    fun `the cat is on the porch of a home that has one - the next home is the cheapest not owned`() {
        val state = loaded.copy(purchased = setOf("cat_black"), choices = mapOf("pet" to "cat_black"))
        assertNull(HomeRules.catOnPorch(state, "rent"))
        assertEquals("cat_black", HomeRules.catOnPorch(state, "wood")?.id)
        assertEquals("wood", HomeRules.nextHouse(state)?.id)
        assertEquals("flat", HomeRules.nextHouse(state.copy(houses = setOf("wood")))?.id)
        assertEquals(1 to 0, HomeRules.counts(state))
    }

    @Test
    fun `«Обставить» counts the places a thing stands in - not the points where a pet sits`() {
        val rent = HomeRules.places("rent", outside = false).map { it.id }
        assertTrue("sillL" !in rent, "the cat's sill is no place of its own")
        assertTrue("pet" in rent)
        assertTrue("fire" !in rent, "the rented room has no stove")
        rent.forEach { place -> assertTrue(HomeCatalog.items.any { it.slot == place }, "$place holds no thing") }
        assertEquals(23, rent.size)

        assertTrue("fire" in HomeRules.places("wood", outside = false).map { it.id })
        val woodOutside = HomeRules.places("wood", outside = true).map { it.id }
        assertTrue("oRoof" in woodOutside)
        assertTrue("oPorch" !in woodOutside, "the porch is where the cat sits")
        assertEquals(5, woodOutside.size)
        assertEquals(4, HomeRules.places("rent", outside = true).size)
    }
}
