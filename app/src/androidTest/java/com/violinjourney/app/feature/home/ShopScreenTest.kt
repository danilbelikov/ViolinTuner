package com.violinjourney.app.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.cityOf
import com.violinjourney.app.feature.journey.cityToOf
import com.violinjourney.app.feature.journey.sessionsInWords
import com.violinjourney.app.feature.journey.taktsInWords
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.arrange_in_shop
import com.violinjourney.app.shared.resources.home_evening
import com.violinjourney.app.shared.resources.shop_all
import com.violinjourney.app.shared.resources.shop_buy
import com.violinjourney.app.shared.resources.shop_left_after
import com.violinjourney.app.shared.resources.shop_missing
import com.violinjourney.app.shared.resources.shop_no_place
import com.violinjourney.app.shared.resources.shop_owned
import com.violinjourney.app.shared.resources.shop_place
import com.violinjourney.app.shared.resources.shop_place_clear
import com.violinjourney.app.shared.resources.shop_put
import com.violinjourney.app.shared.resources.shop_row_after
import com.violinjourney.app.shared.resources.shop_row_balance
import com.violinjourney.app.shared.resources.shop_row_place
import com.violinjourney.app.shared.resources.shop_row_to
import com.violinjourney.app.shared.resources.shop_shelf_yours
import com.violinjourney.app.shared.resources.shop_standing
import com.violinjourney.app.shared.resources.shop_still_enough
import com.violinjourney.app.shared.resources.shop_still_more
import com.violinjourney.app.shared.resources.shop_take
import com.violinjourney.app.shared.resources.shop_try
import com.violinjourney.app.shared.resources.shop_try_remove
import com.violinjourney.app.shared.resources.shop_try_title
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shop, the card of a thing, the try-on and «Обставить» of R7 (spec 3.36.7, 5.29 R7) by what an eye, a finger and a reader meet.
 * The shop by place: the chip of the place stands first, chosen, and its press takes the place off; only the things of the place are on
 * the shelves. The card: its rows are phrases — «После покупки, останется …», «До Праги, всё ещё хватает», «Баланс, 472» — and its
 * buttons stand in one row 1 : 1.4, or one under the other where a word would break; buying is the button's, a swipe only hides the
 * sheet. The try-on: its arrow is «Убрать»; «Убрать» by its word at the bottom. «Обставить»: a place can be reached in every window —
 * lying the room stands beside the places —, a tile is a radio button that puts its thing, «в лавке N →» opens the shop by place.
 *
 * Laid out in a window of its own size ([TestWindow]); the card and its buttons are laid out right in it, as wide as a sheet of the
 * phone holds them (the sheet itself is a window of the device's size); the words are read in the composition, in the language of the
 * process; the date of the cards is fixed.
 */
@RunWith(AndroidJUnit4::class)
class ShopScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<HomeIntent>()
    private val words = mutableMapOf<String, String>()
    private var ui by mutableStateOf(homeUi())
    private var windowSize by mutableStateOf(DpSize(412.dp, 800.dp))
    private var told by mutableStateOf<DpSize?>(null)
    private var fontScale by mutableFloatStateOf(1f)
    private var cardItem by mutableStateOf("chandelier")

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    /** The model of the tests: what the screen says; the card dropped when it is hidden, as [HomeViewModel] drops it. */
    private fun onIntent(intent: HomeIntent) {
        intents += intent
        if (intent == HomeIntent.CardClosed) ui = ui.copy(card = null)
    }

    private fun show(screen: @Composable () -> Unit) {
        compose.setContent {
            ReadWords()
            ViolinTheme { TestWindow(windowSize, told ?: windowSize, fontScale) { screen() } }
        }
        compose.waitForIdle()
    }

    private fun showShop() = show { ShopScreen(ui, ::onIntent) }

    private fun showArrange() = show { ArrangeScreen(ui, ::onIntent) }

    /**
     * The card of [cardItem] and its buttons as a sheet as wide as the window holds them: its fields of 20 at the sides. Laid out right in
     * the window of the test — a sheet is a window of the device's own size, whatever the test asks.
     */
    private fun showCard() = show {
        val item = HomeCatalog.byId.getValue(cardItem)
        Column(Modifier.fillMaxWidth().padding(horizontal = SHEET_SIDE)) {
            ItemCardContent(item, ui, today = OCTOBER)
            ItemCardButtons(item, ui, ::onIntent, today = OCTOBER)
        }
    }

    /** Another card in the same composition: a test sets its content once. */
    private fun card(item: String, home: HomeUi = ui) {
        ui = home
        cardItem = item
        compose.waitForIdle()
    }

    /** The words of the screens of the tests, read where the screens read them. */
    @Composable
    private fun ReadWords() {
        val deskR = slotName(DESK_RIGHT)
        words[PLACE_SAID] = stringResource(Res.string.shop_place, deskR)
        words[CLEAR] = stringResource(Res.string.shop_place_clear)
        words[ALL] = stringResource(Res.string.shop_all)
        words[YOURS] = stringResource(Res.string.shop_shelf_yours)
        words[NOTES] = itemName("notes")
        words[METRONOME] = itemName("metronome")
        words[TRY] = stringResource(Res.string.shop_try)
        words[BUY_CHANDELIER] = stringResource(Res.string.shop_buy, Formats.takts(CHANDELIER_PRICE))
        words[BUY_PIANO] = stringResource(Res.string.shop_buy, Formats.takts(PIANO_PRICE))
        words[TAKE] = stringResource(Res.string.shop_take)
        words[PUT] = stringResource(Res.string.shop_put)
        words[OWNED] = stringResource(Res.string.shop_owned)
        words[STANDING] = stringResource(Res.string.shop_standing)
        words[ROW_PLACE] = listOf(stringResource(Res.string.shop_row_place), slotName("ceiling")).joinToString(", ")
        words[ROW_AFTER] = listOf(stringResource(Res.string.shop_row_after), stringResource(Res.string.shop_left_after, Formats.takts(PURSE - CHANDELIER_PRICE))).joinToString(", ")
        words[ROW_TO] = listOf(stringResource(Res.string.shop_row_to, cityToOf(PRAGUE)), stringResource(Res.string.shop_still_enough)).joinToString(", ")
        words[ROW_BALANCE] = listOf(stringResource(Res.string.shop_row_balance), Formats.takts(SHORT_PURSE)).joinToString(", ")
        words[ROW_AFTER_KEY] = stringResource(Res.string.shop_row_after)
        words[ROW_PIANO_PLACE] = listOf(stringResource(Res.string.shop_row_place), slotName("floorL"), stringResource(Res.string.shop_no_place)).joinToString(", ")
        words[MISSING] = stringResource(Res.string.shop_missing, taktsInWords(CHANDELIER_PRICE - SHORT_PURSE))
        words[SESSIONS] = sessionsInWords(2)
        words[REMOVE] = stringResource(Res.string.shop_try_remove)
        words[TRY_TITLE] = stringResource(Res.string.shop_try_title, itemName("chandelier"))
        words[FLOOR] = slotName("floor")
        words[FLOOR_PLANK] = itemName("floor_plank")
        words[FLOOR_LINK] = stringResource(Res.string.arrange_in_shop, FLOOR_MORE)
        words[ROOM_PICTURE] = houseName(WOOD)
        words[ROW_TO_KEY] = stringResource(Res.string.shop_row_to, cityToOf(PRAGUE))
        words[MISSING_WORDS] = stringResource(Res.string.shop_missing, Formats.takts(CHANDELIER_PRICE - SHORT_PURSE))
        words[ROW_SPB_KEY] = stringResource(Res.string.shop_row_to, cityToOf(SPB))
        words[ROW_SPB_VALUE] = stringResource(Res.string.shop_still_more, Formats.takts(SPB_PRICE - (LONDON_PURSE - CHANDELIER_PRICE)))
        words[ROW_LONDON_LEFT] = stringResource(Res.string.shop_left_after, Formats.takts(LONDON_PURSE - CHANDELIER_PRICE))
        words[ROW_PLACE_KEY] = stringResource(Res.string.shop_row_place)
        words[ROW_PLACE_VALUE] = slotName("ceiling")
        words[EVENING] = stringResource(Res.string.home_evening)
        words[CAPTION] = houseName(WOOD)
        words[LAST_PLACE] = slotName(WOODEN_PLACES.last())
        val cremona = cityOf(JourneyRoute.indexOf("cremona"))
        words[CREMONA] = cremona
        words[MASTERS_VIOLIN] = listOf(itemName("vln_master"), cremona).joinToString(", ")
    }

    private fun word(key: String): String = words.getValue(key)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun window(): DpRect = compose.onNodeWithTag(TEST_WINDOW).bounds()

    /** The button of [text] — its capsule, which is what is pressed: the widening of touch targets is off. */
    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    /** A row, a chip or a picture by the one phrase a reader hears of it. */
    private fun said(description: String) = compose.onNode(hasContentDescription(description))

    private fun assertInside(what: String, inner: DpRect, outer: DpRect) {
        assertTrue(
            "$what at $inner stands inside $outer",
            inner.left >= outer.left - 0.5.dp && inner.right <= outer.right + 0.5.dp && inner.top >= outer.top - 0.5.dp && inner.bottom <= outer.bottom + 0.5.dp,
        )
    }

    private fun inWindow(box: DpSize, window: DpSize = box) {
        windowSize = box
        told = window
        compose.waitForIdle()
    }

    // ---- the shop by place

    /**
     * The shop by place (spec 3.36.7): the chip of the place first in the ribbon — chosen, «Место: На столе, справа» for a reader, a
     * button whose press is said as «Снять фильтр» —, «Всё» not chosen; on the shelves the things of the place and nothing else. A press
     * on the chip takes the place off; «Всё» is a filter of its own. What Android hands TalkBack of the chip — `ControlsTouchTest`.
     */
    @Test
    fun theChipOfThePlaceStandsFirstChosenAndItsPressTakesThePlaceOff() {
        ui = homeUi().copy(slot = DESK_RIGHT)
        showShop()
        val place = said(word(PLACE_SAID))
        // a button that says it is chosen: a radio button chosen already would lose its press, and the label with it (AppChip)
        place.assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("its press says what it does") { it.config.getOrNull(SemanticsActions.OnClick)?.label == word(CLEAR) })
        val all = button(word(ALL))
        all.assertIsNotSelected()
        assertTrue("the place first in the ribbon", place.bounds().right <= all.bounds().left)
        compose.onNode(hasContentDescription(word(NOTES), substring = true)).assertExists()
        compose.onAllNodes(hasContentDescription(word(METRONOME), substring = true)).assertCountEquals(0)
        place.performClick()
        all.performClick()
        assertEquals(listOf(HomeIntent.SlotFilterCleared, HomeIntent.CategorySelected(null)), intents)
    }

    /** «Полка ваша — всё куплено» is about a whole shelf: a shelf of one place does not say it, even all bought. */
    @Test
    fun byPlaceTheShelfIsNotSaidToBeYours() {
        val deskRight = HomeCatalog.items.filter { it.slot == DESK_RIGHT }.map { it.id }.toSet()
        ui = homeUi(InTheWoodenHouse.copy(purchased = BOUGHT + deskRight)).copy(slot = DESK_RIGHT)
        showShop()
        compose.onAllNodes(hasText(word(YOURS))).assertCountEquals(0)
    }

    // ---- the card of a thing

    /**
     * The chandelier of 900 out of 47 884 (spec 3.36.7): «из Вены», the rows «Место, Потолок», «После покупки, останется 46 984» and
     * «До Праги, всё ещё хватает», each one phrase; «Примерить» and «Купить · 900» in one row 10 apart, 1 : 1.4; each button does its own.
     */
    @Test
    fun theCardSaysWhatThePurchaseLeavesAndItsButtonsStandOneToOnePointFour() {
        showCard()
        said(word(ROW_PLACE)).assertExists()
        said(word(ROW_AFTER)).assertExists()
        said(word(ROW_TO)).assertExists()
        val tryOn = button(word(TRY)).bounds()
        val buy = button(word(BUY_CHANDELIER)).bounds()
        assertEquals("one row", tryOn.top.value, buy.top.value, 0.5f)
        assertEquals("10 apart", 10f, (buy.left - tryOn.right).value, 0.5f)
        assertEquals("1 : 1.4", 1.4f, buy.width / tryOn.width, 0.02f)
        assertEquals("the main of 56", 56f, buy.height.value, 0.5f)
        button(word(TRY)).performClick()
        button(word(BUY_CHANDELIER)).performClick()
        assertEquals(listOf(HomeIntent.TryClicked, HomeIntent.BuyClicked), intents)
    }

    /**
     * The chandelier of 900 out of 472: «Баланс, 472» and no «После покупки», nor «До Праги» (which would read two ways); the plate «не
     * хватает 428 тактов» — words without a role or a press — beside «Примерить» in one row, 1 : 1.4, 10 apart; under the row «примерно
     * 2 занятия».
     */
    @Test
    fun aShortCardSaysThePurseThePlateAndThePractices() {
        ui = homeUi(balance = SHORT_PURSE)
        showCard()
        said(word(ROW_BALANCE)).assertExists()
        compose.onAllNodes(hasContentDescription(word(ROW_AFTER_KEY), substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription(word(ROW_TO_KEY), substring = true)).assertCountEquals(0)
        val plateNode = said(word(MISSING))
        val plate = plateNode.fetchSemanticsNode()
        assertEquals("no role", null, plate.config.getOrNull(SemanticsProperties.Role))
        assertFalse("no press", SemanticsActions.OnClick in plate.config)
        val tryOn = button(word(TRY)).bounds()
        val beside = plateNode.bounds()
        assertEquals("the plate in the row of «Примерить»", tryOn.top.value, beside.top.value, 0.5f)
        assertEquals("of its height", tryOn.height.value, beside.height.value, 0.5f)
        assertEquals("10 apart", 10f, (beside.left - tryOn.right).value, 0.5f)
        assertEquals("1 : 1.4", 1.4f, beside.width / tryOn.width, 0.02f)
        compose.onNodeWithText(word(SESSIONS)).assertExists()
        assertTrue("the practices under the row", compose.onNodeWithText(word(SESSIONS)).bounds().top >= tryOn.bottom)
    }

    /** The piano in the wooden house, which has no place by the left wall: «нет места в этом доме», no «Примерить», «Купить · 5 000» alone. */
    @Test
    fun aThingWhosePlaceThisHomeLacksIsBoughtAloneOnTheWholeWidth() {
        cardItem = "piano"
        showCard()
        said(word(ROW_PIANO_PLACE)).assertExists()
        compose.onAllNodes(hasText(word(TRY)) and hasClickAction()).assertCountEquals(0)
        val buy = button(word(BUY_PIANO)).bounds()
        assertEquals("on the whole width of the sheet", (windowSize.width - SHEET_SIDE * 2).value, buy.width.value, 0.5f)
    }

    /** A thing bought whose place holds another is put by the main button — and the card closes; one that cannot be put has the chip. */
    @Test
    fun aBoughtThingIsPutByTheMainButtonAndOneThatCannotBeHasTheChipAndNoButtons() {
        cardItem = "case_velvet"
        showCard()
        button(word(PUT)).performClick()
        assertEquals(listOf(HomeIntent.Placed("case", "case_velvet"), HomeIntent.CardClosed), intents)
        card("piano", homeUi(InTheWoodenHouse.copy(purchased = BOUGHT + "piano", choices = InTheWoodenHouse.choices + ("floorL" to "piano"))))
        compose.onNodeWithText(word(OWNED)).assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun aStandingThingHasItsChipAndNothingToPress() {
        ui = homeUi(InTheWoodenHouse.copy(choices = InTheWoodenHouse.choices + ("deskM" to "metronome")))
        cardItem = "metronome"
        showCard()
        compose.onNodeWithText(word(STANDING)).assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    /** The gift: «Примерить» and «Забрать» in one row, no rows about takts. */
    @Test
    fun theGiftIsTriedOnAndTakenWithoutRowsOfTakts() {
        ui = homeUi(InTheWoodenHouse.copy(purchased = BOUGHT - HomeCatalog.GIFT))
        cardItem = HomeCatalog.GIFT
        showCard()
        val tryOn = button(word(TRY)).bounds()
        val take = button(word(TAKE)).bounds()
        assertEquals("one row", tryOn.top.value, take.top.value, 0.5f)
        compose.onAllNodes(hasContentDescription(word(ROW_AFTER_KEY), substring = true)).assertCountEquals(0)
        button(word(TAKE)).performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.BuyClicked), intents)
    }

    /**
     * No word of the buttons breaks, and no number is cut (5.29 R7): in the share of 1 : 1.4 «Примерить» steps down, and where it does
     * not stand even at its least — or «Купить · 900» does not beside it — the two stand one under the other, what buys first.
     */
    private fun buttonsWhole(language: String, font: Float, stacked: Boolean?) {
        speaking(language)
        fontScale = font
        inWindow(PHONE)
        showCard()
        val tryOn = button(word(TRY))
        val buy = button(word(BUY_CHANDELIER))
        assertWholeOnOneLine(compose.onNodeWithText(word(TRY), useUnmergedTree = true), "$language at $font: ${word(TRY)}")
        assertWholeOnOneLine(compose.onNodeWithText(word(BUY_CHANDELIER), useUnmergedTree = true), "$language at $font: ${word(BUY_CHANDELIER)}")
        val one = tryOn.bounds()
        val other = buy.bounds()
        val inARow = kotlin.math.abs((one.top - other.top).value) < 0.5f
        if (stacked != null) assertEquals("$language at $font: one under the other", stacked, !inARow)
        if (!inARow) {
            assertTrue("$language at $font: what buys first", other.bottom <= one.top + 0.5.dp)
            assertEquals("$language at $font: on the whole width", other.width.value, one.width.value, 0.5f)
        }
    }

    @Test
    fun onAPhoneTheButtonsOfTheCardStandWholeInRussian() = buttonsWhole("ru", 1f, stacked = false)

    @Test
    fun onAPhoneAtALargeFontTheButtonsOfTheCardStandOneUnderTheOtherInRussian() = buttonsWhole("ru", LARGE_FONT, stacked = true)

    /** «Anprobieren» at 13 sp needs 80.8 dp of the 80.2 its share of 129 leaves it beside its fields (CoreText; spec 5.29 R7: under). */
    @Test
    fun onAPhoneTheButtonsOfTheCardStandOneUnderTheOtherInGerman() = buttonsWhole("de", 1f, stacked = true)

    @Test
    fun onAPhoneAtALargeFontTheButtonsOfTheCardStandOneUnderTheOtherInGerman() = buttonsWhole("de", LARGE_FONT, stacked = true)

    @Test
    fun onAPhoneTheButtonsOfTheCardStandOneUnderTheOtherInPortuguese() = buttonsWhole("pt", 1f, stacked = true)

    @Test
    fun onAPhoneAtALargeFontTheButtonsOfTheCardStandWholeInFrench() = buttonsWhole("fr", LARGE_FONT, stacked = null)

    /**
     * «Примерить» and the plate «не хватает 428» on a phone (5.29 R7): side by side only where the words of the plate stand whole in
     * its share of 1.4 at 14 sp — its number never cut, nor the words round it —, else one under the other, the plate first, both on the
     * whole width. The words of the plate are one part where they stand whole ([assertPlateWhole]).
     */
    private fun plateWhole(language: String, font: Float, stacked: Boolean) {
        speaking(language)
        fontScale = font
        inWindow(PHONE)
        ui = homeUi(balance = SHORT_PURSE)
        showCard()
        val tryOn = button(word(TRY)).bounds()
        val plate = said(word(MISSING)).bounds()
        assertWholeOnOneLine(compose.onNodeWithText(word(TRY), useUnmergedTree = true), "$language at $font: ${word(TRY)}")
        assertPlateWhole("$language at $font")
        val inARow = kotlin.math.abs((tryOn.top - plate.top).value) < 0.5f
        assertEquals("$language at $font: one under the other", stacked, !inARow)
        if (!inARow) {
            assertTrue("$language at $font: the plate first", plate.bottom <= tryOn.top + 0.5.dp)
            assertEquals("$language at $font: on the whole width", plate.width.value, tryOn.width.value, 0.5f)
        }
    }

    /** The words of the plate stand whole: its line is laid out as one part — cut, it would be the words round the number, each cut. */
    private fun assertPlateWhole(where: String) {
        val node = compose.onNode(hasText(word(MISSING_WORDS)), useUnmergedTree = true).fetchSemanticsNode()
        val parts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(parts)
        assertEquals("$where: «${word(MISSING_WORDS)}» whole, one part", 1, parts.size)
        assertFalse("$where: «${word(MISSING_WORDS)}» not cut", parts.single().isLineEllipsized(0))
    }

    /** By CoreText at 14 sp «не хватает 428» needs 105 dp of the 114 the plate of 181 leaves it beside its sign. */
    @Test
    fun onAPhoneThePlateStandsBesideTryOnInRussian() = plateWhole("ru", 1f, stacked = false)

    /** «il manque 428» at 14 sp × 1.3 needs some 122 dp of the 114 the plate leaves it beside «Essayer»: one under the other. */
    @Test
    fun onAPhoneAtALargeFontThePlateStandsOverTryOnInFrench() = plateWhole("fr", LARGE_FONT, stacked = true)

    /**
     * The rows of the card on a phone at a large font with Saint Petersburg next (5.29 R7): no word of a key or a value is broken and
     * no number cut — «Per San Pietroburgo» goes on at its space, «mancano 1 900» stands whole: beside a key on one line the number had
     * 134 dp of the 138 it needs and broke inside.
     */
    private fun rowsWhole(language: String) {
        speaking(language)
        fontScale = LARGE_FONT
        inWindow(PHONE)
        ui = homeUi(balance = LONDON_PURSE, at = LONDON)
        showCard()
        listOf(ROW_SPB_KEY, ROW_SPB_VALUE, ROW_AFTER_KEY, ROW_LONDON_LEFT, ROW_PLACE_KEY, ROW_PLACE_VALUE).forEach { key ->
            assertWordsWhole(compose.onNodeWithText(word(key), useUnmergedTree = true), "$language at $LARGE_FONT: ${word(key)}")
        }
    }

    @Test
    fun onAPhoneAtALargeFontTheRowsBreakNoWordAndNoNumberInItalian() = rowsWhole("it")

    @Test
    fun onAPhoneAtALargeFontTheRowsBreakNoWordAndNoNumberInFrench() = rowsWhole("fr")

    @Test
    fun onAPhoneAtALargeFontTheRowsBreakNoWordAndNoNumberInRussian() = rowsWhole("ru")

    @Test
    fun onAPhoneAtALargeFontTheRowsBreakNoWordAndNoNumberInGerman() = rowsWhole("de")

    /** The sheet of the card: a swipe hides it, and nothing more — no purchase, no try-on. */
    @Test
    fun aSwipeOfTheCardOnlyHidesIt() {
        showShop()
        ui = ui.copy(card = HomeCatalog.byId.getValue("chandelier"))
        compose.waitForIdle()
        said(word(ROW_TO)).performTouchInput { swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS) }
        compose.waitForIdle()
        assertEquals("hidden, and nothing more", listOf<HomeIntent>(HomeIntent.CardClosed), intents)
        compose.onAllNodes(hasContentDescription(word(ROW_TO))).assertCountEquals(0)
    }

    /**
     * The card slides away as it was (the review of stage 119): the purchase drops the card, and the home that comes from the store
     * has the chandelier in its place — the sliding sheet keeps «После покупки», «Купить · 900» and no chip «в комнате» all the way
     * down, it does not turn into the card of a thing standing in the room and change its height on the way. The sheet slides here
     * (the window of the tests stills the motion; the screen gets it back), frame by frame.
     */
    @Test
    fun aCardBoughtSlidesAwayWithTheFaceItHad() {
        show { CompositionLocalProvider(LocalReduceMotion provides false) { ShopScreen(ui, ::onIntent) } }
        // the living thing of the card asks for frames all the time: they are given one by one
        compose.mainClock.autoAdvance = false
        ui = ui.copy(card = HomeCatalog.byId.getValue("chandelier"))
        repeat(RISE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        button(word(BUY_CHANDELIER)).assertExists()
        ui = homeUi(InTheWoodenHouse.copy(purchased = BOUGHT + "chandelier", choices = InTheWoodenHouse.choices + ("ceiling" to "chandelier")), balance = PURSE - CHANDELIER_PRICE)
        repeat(SOME_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            said(word(ROW_AFTER)).assertExists()
            button(word(BUY_CHANDELIER)).assertExists()
            compose.onAllNodesWithText(word(STANDING)).assertCountEquals(0)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription(word(ROW_AFTER))).assertCountEquals(0)
        assertTrue("nobody hid it: its owner dropped it", intents.isEmpty())
    }

    // ---- the try-on

    /**
     * The try-on (spec 3.36.7): its bar says «Примерка · Люстра» — a heading — and its arrow is «Убрать» for a reader and does what
     * «Убрать» does; at the bottom «Убрать» by its word and «Купить · 900» on the rest of the row.
     */
    @Test
    fun theArrowOfTheTryOnIsRemoveAndTheRowOfTheVeilKeepsRemoveByItsWord() {
        ui = homeUi().copy(tryOn = HomeCatalog.byId.getValue("chandelier"))
        showShop()
        compose.onNode(hasText(word(TRY_TITLE)) and isHeading()).assertExists()
        val arrow = compose.onNode(hasContentDescription(word(REMOVE)) and hasClickAction())
        arrow.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val remove = button(word(REMOVE)).bounds()
        val buy = button(word(BUY_CHANDELIER)).bounds()
        assertEquals("one row", remove.top.value, buy.top.value, 0.5f)
        assertTrue("«Убрать» by its word: narrower than what buys", remove.width < buy.width)
        assertEquals("the rest of the row", (window().right - TRY_SIDE).value, buy.right.value, 0.5f)
        arrow.performClick()
        button(word(REMOVE)).performClick()
        button(word(BUY_CHANDELIER)).performClick()
        assertEquals(listOf(HomeIntent.TryClosed, HomeIntent.TryClosed, HomeIntent.BuyClicked), intents)
    }

    /**
     * The bar of the try-on on a phone at a large font in German (spec 3.36.7: «Примерка · …» on one line, an ellipsis): «Anprobe ·
     * Kronleuchter» is wider than the room between the arrow and «Abend | Tag» — it keeps one line and that room, the arrow its 48.
     */
    @Test
    fun theTitleOfTheTryOnStaysOnOneLineBesideItsSwitchInGermanAtALargeFont() {
        speaking("de")
        fontScale = LARGE_FONT
        inWindow(PHONE)
        ui = homeUi().copy(tryOn = HomeCatalog.byId.getValue("chandelier"))
        showShop()
        val title = compose.onNode(hasText(word(TRY_TITLE)) and isHeading())
        assertEquals("«${word(TRY_TITLE)}» on one line", 1, title.textLayout().lineCount)
        val bounds = title.bounds()
        val evening = compose.onNode(hasText(word(EVENING)) and hasClickAction()).bounds()
        assertTrue("the title has its room: $bounds", bounds.width > 0.dp)
        assertTrue("the title ends before «${word(EVENING)}»", bounds.right <= evening.left + 0.5.dp)
        val arrow = compose.onNode(hasContentDescription(word(REMOVE)) and hasClickAction()).bounds()
        assertEquals("the arrow 48 wide", 48f, arrow.width.value, 0.5f)
        assertEquals("the arrow 48 high", 48f, arrow.height.value, 0.5f)
        assertTrue("the title after the arrow", bounds.left >= arrow.right - 0.5.dp)
    }

    // ---- «Обставить»

    /**
     * A place of «Обставить» is reached in every window (5.29 R7; open question 16): upright the room stands over the places — giving
     * way to the caption and one whole place —, lying it stands beside them, so the first place — «Пол» of the wooden house — and its
     * first tile are in the window without a scroll: on the emulator's 640 × 360 lying (603 × 308 under its bars) a room of 210 over
     * them left the places no room at all. Lying is a window wider than high where the room keeps 200 beside them (the review of stage
     * 119): the half of a split screen upright (412 × 450, its box under the bar wider than high) and the half of a phone lying (456 ×
     * 411) stand upright — beside the places the room was 44 and 88 there.
     */
    @Test
    fun inEveryWindowAPlaceOfArrangeIsReached() {
        listOf(
            Triple(DpSize(412.dp, 800.dp), DpSize(412.dp, 800.dp), false),
            Triple(PHONE, DpSize(360.dp, 640.dp), false),
            Triple(DpSize(892.dp, 360.dp), DpSize(892.dp, 412.dp), true),
            Triple(DpSize(603.dp, 308.dp), DpSize(603.dp, 308.dp), true),
            Triple(DpSize(603.dp, 336.dp), DpSize(603.dp, 336.dp), true),
            Triple(DpSize(412.dp, 402.dp), DpSize(412.dp, 450.dp), false),
            Triple(DpSize(456.dp, 387.dp), DpSize(456.dp, 411.dp), false),
        ).forEachIndexed { index, (box, told, lying) ->
            inWindow(box, told)
            if (index == 0) showArrange()
            val where = "${box.width} × ${box.height} in ${told.width} × ${told.height}"
            assertInside("$where: «${word(FLOOR)}»", compose.onNodeWithText(word(FLOOR)).bounds(), window())
            assertInside("$where: its first tile", said(word(FLOOR_PLANK)).bounds(), window())
            val room = said(word(ROOM_PICTURE)).bounds()
            val place = compose.onNodeWithText(word(FLOOR)).bounds()
            if (lying) {
                assertTrue("$where: lying the room beside the places", room.right <= place.left)
                assertTrue("$where: lying the room keeps 200: ${room.width}", room.width >= ROOM_BESIDE_LEAST - 0.5.dp)
            } else {
                assertTrue("$where: upright the room over the places", room.bottom <= place.top)
                assertEquals("$where: upright the room on the width of its column", (minOf(box.width, COLUMN) - SIDE * 2).value, room.width.value, 0.5f)
            }
        }
    }

    /**
     * A turn of the phone keeps where the places were scrolled to (the review of stage 119): the room and the places stand in one place
     * of the tree lying and upright, so what is saved of their scroll is found again — the activity is made anew lying, here as on a
     * phone ([StateRestorationTester]). Before, lying and upright were two places of the tree, and a turn brought the places back to
     * their top, «22 места, 18 занято».
     */
    @Test
    fun aTurnOfThePhoneKeepsWhereThePlacesWereScrolledTo() {
        val restoration = StateRestorationTester(compose)
        windowSize = DpSize(412.dp, 800.dp)
        told = windowSize
        restoration.setContent {
            ReadWords()
            ViolinTheme { TestWindow(windowSize, told ?: windowSize, fontScale) { ArrangeScreen(ui, ::onIntent) } }
        }
        compose.onNodeWithText(word(CAPTION), substring = true).assertIsDisplayed()
        compose.onNodeWithText(word(LAST_PLACE)).performScrollTo()
        compose.onNodeWithText(word(CAPTION), substring = true).assertIsNotDisplayed()
        // turned: lying, and the activity made anew with what was saved
        windowSize = DpSize(892.dp, 360.dp)
        told = DpSize(892.dp, 412.dp)
        restoration.emulateSavedInstanceStateRestore()
        assertTrue("lying now", said(word(ROOM_PICTURE)).bounds().right <= compose.onNodeWithText(word(LAST_PLACE)).bounds().left)
        compose.onNodeWithText(word(CAPTION), substring = true).assertIsNotDisplayed()
    }

    /**
     * The pill of the city of a chosen tile ends before its tick (the review of stage 119): the circle of 22 stands 6 out of the top end
     * corner of the tile of 72 and covered the end of «Кремона» — the pill ends 2 before it, with an ellipsis; the city is said whole
     * in the description of the tile.
     */
    @Test
    fun theCityOfAChosenTileEndsBeforeItsTick() {
        ui = homeUi(InTheWoodenHouse.copy(choices = InTheWoodenHouse.choices + ("violin" to "vln_master")))
        showArrange()
        val tile = said(word(MASTERS_VIOLIN))
        tile.performScrollTo().assertIsSelected()
        val box = tile.bounds()
        val city = compose.onNode(hasText(word(CREMONA)), useUnmergedTree = true)
        val words = city.bounds()
        // the circle of the tick: 22 wide, its end 6 out of the end of the tile
        val tickStart = box.right + TICK_OUT - TICK
        assertTrue("«${word(CREMONA)}» ends at ${words.right}, its field of 6 and 2 before the tick at $tickStart", words.right + CITY_SIDE + TICK_CLEAR <= tickStart + 0.5.dp)
        assertTrue("with an ellipsis", city.textLayout().isLineEllipsized(0))
    }

    /**
     * A tile is a radio button of its place that puts its thing; «в лавке 4 →» of «Пол» stands on the line of its name — a button to the
     * shop by that place (other places may have 4 in the shop too: the first of them is the floor's).
     */
    @Test
    fun aTilePutsItsThingAndTheLinkOpensTheShopByPlace() {
        showArrange()
        said(word(FLOOR_PLANK))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
            .performClick()
        val link = compose.onAllNodes(hasText(word(FLOOR_LINK)) and hasClickAction())[0]
        link.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val name = compose.onNodeWithText(word(FLOOR)).bounds()
        val box = link.bounds()
        assertEquals("on the line of the name", ((name.top + name.bottom) / 2).value, ((box.top + box.bottom) / 2).value, 1f)
        assertEquals("pressed over 48 of its own", 48f, box.height.value, 0.5f)
        assertTrue("the tiles start under its 48", said(word(FLOOR_PLANK)).bounds().top >= box.bottom - 0.5.dp)
        link.performClick()
        assertEquals(listOf(HomeIntent.Placed("floor", "floor_plank"), HomeIntent.ShopAtClicked("floor")), intents)
    }

    private companion object {
        val OCTOBER = LocalDate(2026, 10, 1)
        val PRAGUE = JourneyRoute.indexOf("prague")
        val VIENNA = JourneyRoute.indexOf("vienna")

        /** London reached: Saint Petersburg is next, 6 000 away — the longest name of a city in a key of the card. */
        val LONDON = JourneyRoute.indexOf("london")
        val SPB = JourneyRoute.indexOf("spb")
        val SPB_PRICE = JourneyRoute.stops[SPB].price.toLong()
        const val LONDON_PURSE = 5_000L
        const val WOOD = "wood"
        const val DESK_RIGHT = "deskR"

        /** The purse of the mockups; a purse short of the chandelier. */
        const val PURSE = 47_884L
        const val SHORT_PURSE = 472L
        val CHANDELIER_PRICE = HomeCatalog.byId.getValue("chandelier").price.toLong()
        val PIANO_PRICE = HomeCatalog.byId.getValue("piano").price.toLong()

        /** Seven things, three of them brought from the road; the gift taken. */
        val BOUGHT = setOf(HomeCatalog.GIFT, "vln_master", "case_velvet", "stand_wood", "metronome", "portrait", "notes")
        val InTheWoodenHouse = HomeState(loaded = true, purchased = BOUGHT, houses = setOf(WOOD), choices = mapOf(HomeState.HOUSE_KEY to WOOD))

        /** «в лавке 4» of «Пол»: the four floors of the shop — the plank floor is the room's own. */
        val FLOOR_MORE = HomeCatalog.items.count { it.slot == "floor" && it.id !in HomeRules.ownedItems(InTheWoodenHouse) }

        /** The journey stood in the city of index [at] with [balance] takts in the purse: every leg to it paid. */
        fun progressAt(balance: Long, at: Int = VIENNA): JourneyProgress {
            val spent = JourneyRoute.stops.take(at + 1).sumOf { it.price.toLong() }
            return JourneyProgress(earned = spent + balance, spent = spent, arrivals = (0..at).map { Arrival(JourneyRoute.stops[it].id, 0L) }, extras = emptySet())
        }

        fun homeUi(home: HomeState = InTheWoodenHouse, balance: Long = PURSE, at: Int = VIENNA) = HomeUi(
            loading = false,
            home = home,
            progress = progressAt(balance, at),
            house = home.choices[HomeState.HOUSE_KEY] ?: HomeCatalog.START_HOUSE,
        )

        /** The places of the wooden house «Обставить» lists, in their order: those with something bought for them. */
        val WOODEN_PLACES = HomeRules.places(WOOD, false).filter { HomeRules.wardrobe(it.id, InTheWoodenHouse).isNotEmpty() }.map { it.id }

        /** A phone of 360 × 640 under its bars (24 and a gesture bar of 24). */
        val PHONE = DpSize(360.dp, 592.dp)

        /** The fields of a sheet at its sides; the fields of the veil of the try-on. */
        val SHEET_SIDE = 20.dp
        val TRY_SIDE = 16.dp

        const val LARGE_FONT = 1.3f
        const val SWIPE = 700
        const val SWIPE_MS = 150L

        /** Frames for a sheet to rise (some 0.7 s); frames of its slide away, well inside the slide. */
        const val RISE_FRAMES = 40
        const val SOME_FRAMES = 5

        /** «Обставить»: the column of the home upright, its fields; lying the room keeps this beside the places (5.29 R7). */
        val COLUMN = 560.dp
        val SIDE = 16.dp
        val ROOM_BESIDE_LEAST = 200.dp

        /** The tick of a chosen tile: a circle of 22, 6 out of the end of the tile; the field of the pill of a city, 2 off the tick. */
        val TICK = 22.dp
        val TICK_OUT = 6.dp
        val CITY_SIDE = 6.dp
        val TICK_CLEAR = 2.dp

        const val PLACE_SAID = "placeSaid"
        const val CLEAR = "clear"
        const val ALL = "all"
        const val YOURS = "yours"
        const val NOTES = "notes"
        const val METRONOME = "metronome"
        const val TRY = "try"
        const val BUY_CHANDELIER = "buyChandelier"
        const val BUY_PIANO = "buyPiano"
        const val TAKE = "take"
        const val PUT = "put"
        const val OWNED = "owned"
        const val STANDING = "standing"
        const val ROW_PLACE = "rowPlace"
        const val ROW_AFTER = "rowAfter"
        const val ROW_TO = "rowTo"
        const val ROW_BALANCE = "rowBalance"
        const val ROW_AFTER_KEY = "rowAfterKey"
        const val ROW_PIANO_PLACE = "rowPianoPlace"
        const val MISSING = "missing"
        const val SESSIONS = "sessions"
        const val REMOVE = "remove"
        const val TRY_TITLE = "tryTitle"
        const val FLOOR = "floor"
        const val FLOOR_PLANK = "floorPlank"
        const val FLOOR_LINK = "floorLink"
        const val ROOM_PICTURE = "roomPicture"
        const val ROW_TO_KEY = "rowToKey"
        const val MISSING_WORDS = "missingWords"
        const val ROW_SPB_KEY = "rowSpbKey"
        const val ROW_SPB_VALUE = "rowSpbValue"
        const val ROW_LONDON_LEFT = "rowLondonLeft"
        const val ROW_PLACE_KEY = "rowPlaceKey"
        const val ROW_PLACE_VALUE = "rowPlaceValue"
        const val EVENING = "evening"
        const val CAPTION = "caption"
        const val LAST_PLACE = "lastPlace"
        const val CREMONA = "cremona"
        const val MASTERS_VIOLIN = "mastersViolin"
    }
}
