package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyStop
import com.violinjourney.app.feature.home.ItemCardPlan.Buttons
import com.violinjourney.app.feature.home.ItemCardPlan.Chip
import com.violinjourney.app.feature.home.ItemCardPlan.Money
import com.violinjourney.app.feature.home.ItemCardPlan.PlaceNote
import com.violinjourney.app.feature.home.ItemCardPlan.ToNext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * The card of a thing (spec 3.36.7, «Карточка вещи»): «После покупки» and «До Праги — всё ещё хватает / ещё N» while the takts are
 * enough, «Баланс» and «примерно N занятий» while they are short (5.18: 300 a practice, up, at least 1); no second row after the last
 * city; the statuses — no place, out of season, bought («Поставить», or the chip «куплено» where it cannot be put), standing (the chip
 * and no buttons), the gift («Примерить» and «Забрать», no rows about takts).
 */
class ItemCardPlanTest {
    private fun item(id: String) = HomeCatalog.byId.getValue(id)
    private val prague = JourneyRoute.stops[JourneyRoute.indexOf("prague")]
    private val config = JourneyConfig()

    private fun plan(
        id: String,
        tag: ShelfTag = ShelfTag.PRICE,
        chosen: Boolean = false,
        here: Boolean = true,
        inSeason: Boolean = true,
        balance: Long = 47_884,
        next: JourneyStop? = prague,
        config: JourneyConfig = this.config,
    ) = ItemCardPlan.of(item(id), tag, chosen, here, inSeason, balance, next, config)

    @Test
    fun `the lamp of 300 out of 47 884 leaves 47 584 and the road to Prague still enough`() {
        val lamp = plan("lamp_brass")
        assertEquals(Money.After(47_584, ToNext.StillEnough("prague")), lamp.money)
        assertEquals(Buttons.TryAndBuy, lamp.buttons)
        assertNull(lamp.chip)
        assertNull(lamp.placeNote)
        assertEquals(0, lamp.sessions)
    }

    @Test
    fun `a purchase that eats the road says how much more the road needs after it`() {
        // 1 800 − 300 = 1 500: Prague is 1 600 — 100 more
        assertEquals(Money.After(1_500, ToNext.More("prague", 100)), plan("lamp_brass", balance = 1_800).money)
        // short of the road already: what is short after buying
        assertEquals(Money.After(700, ToNext.More("prague", 900)), plan("lamp_brass", balance = 1_000).money)
        // exactly enough is enough
        assertEquals(Money.After(1_600, ToNext.StillEnough("prague")), plan("lamp_brass", balance = 1_900).money)
    }

    /** Exactly enough for the thing is enough: the rows of what is left, not the purse — and «Купить · 900» stands bright then too. */
    @Test
    fun `exactly the price in the purse buys it - nothing left and the road says what it needs`() {
        val chandelier = plan("chandelier", balance = 900)
        assertEquals(Money.After(0, ToNext.More("prague", 1_600)), chandelier.money)
        assertEquals(0, chandelier.sessions)
        assertEquals(Buttons.TryAndBuy, chandelier.buttons)
    }

    @Test
    fun `the chandelier of 900 out of 472 says the purse and about two practices - no road`() {
        val chandelier = plan("chandelier", balance = 472)
        assertEquals(Money.Balance(472), chandelier.money)
        assertEquals(2, chandelier.sessions, "428 short — 300 a practice, up")
        assertEquals(Buttons.TryAndBuy, chandelier.buttons, "«Примерить» and the plate")
        assertEquals(5, plan("chandelier", balance = 472, config = JourneyConfig(taktsPerSessionHint = 100)).sessions, "by the numbers of the graph")
        assertEquals(1, plan("chandelier", balance = 899).sessions, "one takt short is a practice")
    }

    @Test
    fun `after the last city there is no row of the road`() {
        assertEquals(Money.After(47_584, null), plan("lamp_brass", next = null).money)
    }

    @Test
    fun `a home without the place of a thing - it is sold all the same and bought alone on the whole width`() {
        val fireplace = plan("fireplace", here = false)
        assertEquals(PlaceNote.NEEDS_CHIMNEY, fireplace.placeNote)
        assertEquals(Buttons.Buy, fireplace.buttons, "no «Примерить»: there is nowhere to try it")
        assertTrue(fireplace.money is Money.After)
        assertEquals(PlaceNote.NO_PLACE, plan("piano", here = false).placeNote)
        val short = plan("piano", here = false, balance = 1_000)
        assertEquals(Buttons.Buy, short.buttons)
        assertEquals(14, short.sessions, "4 000 short")
    }

    @Test
    fun `out of season the place says it waits for December`() {
        val tree = plan("xmas", inSeason = false)
        assertEquals(PlaceNote.WAITS_SEASON, tree.placeNote)
        assertEquals(Buttons.TryAndBuy, tree.buttons)
    }

    @Test
    fun `bought and not in its place - put it with the main button alone`() {
        val ficus = plan("ficus", tag = ShelfTag.OWNED)
        assertEquals(Buttons.Put, ficus.buttons)
        assertNull(ficus.chip)
        assertNull(ficus.money)
        assertEquals(0, ficus.sessions)
    }

    @Test
    fun `bought and it cannot be put - the chip bought and the reason at the place and no buttons`() {
        val piano = plan("piano", tag = ShelfTag.OWNED, chosen = true, here = false)
        assertEquals(Chip.OWNED, piano.chip)
        assertEquals(PlaceNote.NO_PLACE, piano.placeNote)
        assertEquals(Buttons.None, piano.buttons)
        assertNull(piano.money)
        // not even where it is not the choice of its place: the home has no place for it
        assertEquals(Buttons.None, plan("piano", tag = ShelfTag.OWNED, chosen = false, here = false).buttons)
        val tree = plan("xmas", tag = ShelfTag.OWNED, chosen = true, inSeason = false)
        assertEquals(Chip.OWNED, tree.chip)
        assertEquals(PlaceNote.WAITS_SEASON, tree.placeNote)
        assertEquals(Buttons.None, tree.buttons, "it is the choice of its place already: it waits for its season")
    }

    @Test
    fun `standing in the home - the chip of the room or of the house and nothing to press`() {
        val metronome = plan("metronome", tag = ShelfTag.STANDING, chosen = true)
        assertEquals(Chip.STANDING, metronome.chip)
        assertEquals(Buttons.None, metronome.buttons)
        assertNull(metronome.money)
        assertEquals(Chip.STANDING_OUTSIDE, plan("mailbox", tag = ShelfTag.STANDING, chosen = true).chip)
    }

    @Test
    fun `the gift is tried on and taken - no rows of takts`() {
        val gift = plan(HomeCatalog.GIFT, tag = ShelfTag.GIFT, balance = 0)
        assertEquals(Buttons.TryAndTake, gift.buttons)
        assertNull(gift.money)
        assertEquals(0, gift.sessions)
        assertNull(gift.chip)
        assertEquals(Buttons.Buy, plan(HomeCatalog.GIFT, tag = ShelfTag.GIFT, here = false).buttons)
    }

    @Test
    fun `a thing of a city not reached has nothing to offer`() {
        val clock = plan("clock", tag = ShelfTag.LOCKED)
        assertEquals(Buttons.None, clock.buttons)
        assertNull(clock.money)
    }

    @Test
    fun `in the shop the card reads the tags of the shelf and the home lived in`() {
        val progress = JourneyProgress(earned = 10_000, spent = 0, arrivals = listOf(Arrival("home", 1), Arrival("vienna", 2)), extras = emptySet())
        val october = LocalDate(2026, 10, 1)
        // the piano by the left wall, after moving to the wooden house: no such place there
        val home = HomeState.EMPTY.copy(loaded = true, purchased = setOf("piano"), houses = setOf("wood"), choices = mapOf("floorL" to "piano", HomeState.HOUSE_KEY to "wood"))
        val ui = HomeUi(loading = false, home = home, progress = progress, house = "wood")
        val piano = ItemCardPlan.of(item("piano"), ui, ShelfTags(home, progress, "wood", october), october)
        assertEquals(Chip.OWNED, piano.chip)
        assertEquals(PlaceNote.NO_PLACE, piano.placeNote)
        // the cat lies on the left sill: every home has it
        assertTrue(ItemCardPlan.placeIsHere(item("cat_ginger"), "wood"))
        assertFalse(ItemCardPlan.placeIsHere(item("fireplace"), "rent"))
        val cat = ItemCardPlan.of(item("cat_ginger"), ui, ShelfTags(home, progress, "wood", october), october)
        assertEquals(Money.After(9_200, ToNext.StillEnough("prague")), cat.money, "10 000 − 800; Prague is 1 600")
        assertEquals(Buttons.TryAndBuy, cat.buttons)
    }
}
