package com.violinjourney.app.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.ui.components.DockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.cityOf
import com.violinjourney.app.feature.journey.cityToOf
import com.violinjourney.app.feature.journey.sessionsInWords
import com.violinjourney.app.feature.journey.taktsInWords
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.home_all_houses
import com.violinjourney.app.shared.resources.home_arrange
import com.violinjourney.app.shared.resources.home_fullscreen
import com.violinjourney.app.shared.resources.home_gift_take
import com.violinjourney.app.shared.resources.home_gift_text
import com.violinjourney.app.shared.resources.home_gift_title
import com.violinjourney.app.shared.resources.home_have
import com.violinjourney.app.shared.resources.home_house_soon
import com.violinjourney.app.shared.resources.home_next_house
import com.violinjourney.app.shared.resources.home_outside
import com.violinjourney.app.shared.resources.home_picture_room
import com.violinjourney.app.shared.resources.home_shop
import com.violinjourney.app.shared.resources.home_travel
import com.violinjourney.app.shared.resources.home_travel_enough
import com.violinjourney.app.shared.resources.houses_enough
import com.violinjourney.app.shared.resources.houses_live
import com.violinjourney.app.shared.resources.houses_live_here
import com.violinjourney.app.shared.resources.houses_move
import com.violinjourney.app.shared.resources.journey_have_description
import com.violinjourney.app.shared.resources.shop_missing
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.assertWordsWhole
import java.util.Locale
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home and «Дома» of R7 (spec 3.36.7, 5.29 R7) by what an eye, a finger and a reader meet. The home: its zone holds «В дорогу» with
 * the line of the road and under it «Лавка» and «Обставить» by halves, in every window; on a phone of 360 × 640 the room gives way
 * (284 with gestures) and the zone stays whole; «Комната | Снаружи» stands at the top end of the room and the square «на весь экран»
 * at its bottom end, 8 from its edges — and never on the switch: in a low window at a large font the room has no square; lying the
 * three stand in one row under the room where its column is 440 wide, the two outlines with the fields of the zone, and the halves
 * keep their words whole where it is narrower (the emulator's 640 × 360), at a large font too; the gift is taken right on the home,
 * and its words stay whole lying at a large font; the next home is one row, one phrase, that opens «Дома». «Дома»: one phrase a row —
 * where one lives, what can be bought, what is soon and not pressed —, «Жить здесь» a button of its own; the name of a home keeps its
 * words whole, and what stands at the end of its row goes under them where it would break them; the sheet of a home says what is
 * missing and how many practices, by the numbers of the graph, and a swipe only hides it.
 *
 * Laid out in a window of its own size ([TestWindow]); the words are read in the composition, in the language of the process.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<HomeIntent>()
    private val words = mutableMapOf<String, String>()
    private var ui by mutableStateOf(homeUi())
    private var windowSize by mutableStateOf(DpSize(412.dp, 800.dp))
    private var told by mutableStateOf<DpSize?>(null)
    private var fontScale by mutableFloatStateOf(1f)

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    /** The model of the tests: what the screen says; the sheet of a home dropped when it is hidden, as [HomeViewModel] drops it. */
    private fun onIntent(intent: HomeIntent) {
        intents += intent
        if (intent == HomeIntent.HouseCardClosed) ui = ui.copy(houseCard = null)
    }

    private fun show(screen: @Composable () -> Unit) {
        compose.setContent {
            ReadWords()
            ViolinTheme { TestWindow(windowSize, told ?: windowSize, fontScale) { screen() } }
        }
        compose.waitForIdle()
    }

    private fun showHome() = show { HomeScreen(ui, ::onIntent) }

    private fun showHouses() = show { HousesScreen(ui, ::onIntent) }

    @Composable
    private fun note(id: String): String = HomeTexts.houseNotes[id]?.let { stringResource(it) }.orEmpty()

    /** The words of the screens of the tests, read where the screens read them. */
    @Composable
    private fun ReadWords() {
        val soon = stringResource(Res.string.home_house_soon)
        val have = stringResource(Res.string.journey_have_description, Formats.takts(SHORT_PURSE), taktsInWords(WOOD_PRICE))
        words[TRAVEL] = stringResource(Res.string.home_travel)
        words[TRAVEL_LINE] = stringResource(Res.string.home_travel_enough, cityOf(VIENNA), cityToOf(VIENNA + 1))
        words[SHOP] = stringResource(Res.string.home_shop)
        words[ARRANGE] = stringResource(Res.string.home_arrange)
        words[ROOM] = stringResource(Res.string.home_picture_room, houseName(WOOD))
        words[OUTSIDE] = stringResource(Res.string.home_outside)
        words[WHOLE] = stringResource(Res.string.home_fullscreen)
        words[TAKE] = stringResource(Res.string.home_gift_take)
        val nextHouse = stringResource(Res.string.home_next_house)
        words[NEXT_SOON] = listOf(nextHouse, houseName(FLAT), "$soon, ${taktsInWords(FLAT_PRICE)}").joinToString(", ")
        words[NEXT_SHORT] = listOf(nextHouse, houseName(WOOD), have).joinToString(", ")
        words[ALL_HOUSES] = stringResource(Res.string.home_all_houses)
        words[RENT_HERE] = listOf(houseName(RENT), stringResource(Res.string.houses_live_here), note(RENT)).joinToString(", ")
        words[RENT_OWNED] = listOf(houseName(RENT), note(RENT)).joinToString(", ")
        words[WOOD_SHORT] = listOf(houseName(WOOD), note(WOOD), have).joinToString(", ")
        words[FLAT_SOON] = listOf(houseName(FLAT), "$soon, ${taktsInWords(FLAT_PRICE)}").joinToString(", ")
        words[LIVE] = stringResource(Res.string.houses_live)
        words[MOVE] = stringResource(Res.string.houses_move, Formats.takts(WOOD_PRICE))
        words[MISSING] = stringResource(Res.string.shop_missing, taktsInWords(WOOD_PRICE - SHORT_PURSE))
        words[SESSIONS] = sessionsInWords(SESSIONS_AT_250)
        words[WOOD_NAME] = houseName(WOOD)
        words[WOOD_NOTE] = note(WOOD)
        words[WOOD_ENOUGH_LINE] = stringResource(Res.string.houses_enough, Formats.takts(WOOD_PRICE))
        words[WOOD_SHORT_LINE] = stringResource(Res.string.home_have, Formats.takts(SHORT_PURSE), Formats.takts(WOOD_PRICE))
        words[GIFT_TITLE] = stringResource(Res.string.home_gift_title)
        words[GIFT_TEXT] = stringResource(Res.string.home_gift_text)
    }

    private fun word(key: String): String = words.getValue(key)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun window(): DpRect = compose.onNodeWithTag(TEST_WINDOW).bounds()

    /** The button of [text] — its capsule, which is what is pressed: the widening of touch targets is off. */
    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    private fun tab(text: String) = compose.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    /** A row or a picture by the one phrase a reader hears of it. */
    private fun said(description: String) = compose.onNodeWithContentDescription(description)

    private fun assertInside(what: String, inner: DpRect, outer: DpRect) {
        assertTrue(
            "$what at $inner stands inside $outer",
            inner.left >= outer.left - 0.5.dp && inner.right <= outer.right + 0.5.dp && inner.top >= outer.top - 0.5.dp && inner.bottom <= outer.bottom + 0.5.dp,
        )
    }

    /** Whether two boxes share some of their room, more than the half dp of rounding. */
    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right - 0.5.dp && other.left < right - 0.5.dp && top < other.bottom - 0.5.dp && other.top < bottom - 0.5.dp

    private fun inWindow(box: DpSize, window: DpSize = box) {
        windowSize = box
        told = window
        compose.waitForIdle()
    }

    // ---- the home: the zone

    @Test
    fun theZoneOfTheHomeHoldsTheRoadAndUnderItTheShopAndTheWardrobe() {
        showHome()
        val road = button(word(TRAVEL))
        assertEquals(
            "«В дорогу» says how far the road is",
            listOf(word(TRAVEL), word(TRAVEL_LINE)),
            road.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text },
        )
        val key = road.bounds()
        val shop = button(word(SHOP)).bounds()
        val arrange = button(word(ARRANGE)).bounds()
        listOf("«В дорогу»" to key, "«Лавка»" to shop, "«Обставить»" to arrange).forEach { (what, bounds) -> assertInside(what, bounds, window()) }
        assertEquals("the main one of 56", 56f, key.height.value, 0.5f)
        assertEquals("«Лавка» an outline of 48", 48f, shop.height.value, 0.5f)
        assertEquals("the halves in one row", shop.top.value, arrange.top.value, 0.5f)
        assertEquals("the halves of one width", shop.width.value, arrange.width.value, 0.5f)
        assertEquals("the halves 10 under the road", 10f, (shop.top - key.bottom).value, 0.5f)
        road.performClick()
        button(word(SHOP)).performClick()
        button(word(ARRANGE)).performClick()
        assertEquals(listOf(HomeIntent.TravelClicked, HomeIntent.ShopClicked, HomeIntent.ArrangeClicked), intents)
    }

    /**
     * On a phone of 360 × 640 — 592 under its bars with gestures — the room gives way first (5.29 R7): what is left under the bar of 56
     * (536) less the zone (132) and the words under the room (120) — 284; the zone stays whole in the window.
     */
    @Test
    fun onAPhoneTheRoomGivesWayAndTheZoneStaysWhole() {
        inWindow(PHONE, DpSize(360.dp, 640.dp))
        showHome()
        val room = said(word(ROOM)).bounds()
        assertEquals("the room of 284", PHONE_ROOM.value, room.height.value, 1f)
        listOf(TRAVEL, SHOP, ARRANGE).forEach { assertInside(word(it), button(word(it)).bounds(), window()) }
    }

    /** «Комната | Снаружи» at the top end of the room and the square «на весь экран» at its bottom end, 8 from its edges (5.29 R7). */
    @Test
    fun theSwitchStandsAtTheTopEndOfTheRoomAndTheSquareAtItsBottomEnd() {
        showHome()
        val room = said(word(ROOM)).bounds()
        val outside = tab(word(OUTSIDE))
        val switch = outside.bounds()
        assertEquals("8 from the end of the room (its last word in the field of 3)", (room.right - PICTURE_INSET - TWO_WAY_FIELD).value, switch.right.value, 0.5f)
        assertEquals("8 from its top: the 48 of the switch stands 4 over its capsule", (room.top + PICTURE_INSET - TWO_WAY_AIR).value, switch.top.value, 0.5f)
        assertEquals("pressed over 48", 48f, switch.height.value, 0.5f)
        val square = said(word(WHOLE)).bounds()
        assertEquals((room.right - PICTURE_INSET).value, square.right.value, 0.5f)
        assertEquals((room.bottom - PICTURE_INSET).value, square.bottom.value, 0.5f)
        assertEquals(48f, square.width.value, 0.5f)
        outside.performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.SideSelected(true)), intents)
    }

    /**
     * Lying in 892 × 412 (some 360 under its bars) the left column is 524 wide: «В дорогу», «Лавка» and «Обставить» stand in one row
     * of one height under the room; the room does not scroll and ends right at the top of the zone. That no fade is drawn over it is
     * not seen here — the bounds of the room are the same with one — but held by [HomeRoomFit.fade] (`HomeRoomFitTest`), which the
     * screen asks, and by the eye of the lead on the emulator.
     */
    @Test
    fun lyingTheThreeStandInOneRowUnderTheRoom() {
        inWindow(DpSize(892.dp, 360.dp), DpSize(892.dp, 412.dp))
        showHome()
        val box = window()
        val column = DpRect(box.left + SIDE, box.top, box.right - SIDE - ABOUT - SIDE, box.bottom)
        val road = button(word(TRAVEL)).bounds()
        val shop = button(word(SHOP)).bounds()
        val arrange = button(word(ARRANGE)).bounds()
        listOf("«В дорогу»" to road, "«Лавка»" to shop, "«Обставить»" to arrange).forEach { (what, bounds) ->
            assertInside("lying, $what in the left column", bounds, column)
            assertEquals("lying, $what in the row", road.top.value, bounds.top.value, 0.5f)
            assertEquals("lying, $what of the height of the row", road.height.value, bounds.height.value, 0.5f)
        }
        assertTrue("«Лавка» after «В дорогу»", shop.left >= road.right)
        assertTrue("«Обставить» after «Лавка»", arrange.left >= shop.right)
        val room = said(word(ROOM)).bounds()
        assertEquals("the room ends at the top of the zone", (road.top - DockMetrics.of(412.dp).top).value, room.bottom.value, 0.5f)
    }

    /**
     * The emulator's 640 × 360 lying is 603 × 308 under its bars and its cutout: the left column is 235 — narrower than the row of three —
     * so «В дорогу» stands on top and «Лавка» and «Обставить» under it; all of it in the window, the buttons 48, and no word of the
     * halves broken or cut.
     */
    @Test
    fun inTheLowWindowOfTheEmulatorTheHalvesStandUnderTheRoadWithTheirWordsWhole() {
        inWindow(DpSize(603.dp, 308.dp))
        showHome()
        val road = button(word(TRAVEL)).bounds()
        val shop = button(word(SHOP)).bounds()
        val arrange = button(word(ARRANGE)).bounds()
        listOf("«В дорогу»" to road, "«Лавка»" to shop, "«Обставить»" to arrange).forEach { (what, bounds) ->
            assertInside(what, bounds, window())
            assertEquals("$what of 48", 48f, bounds.height.value, 0.5f)
        }
        assertTrue("the halves under the road", shop.top >= road.bottom && arrange.top >= road.bottom)
        assertWordsOfTheHalvesWhole("603 × 308")
    }

    private fun assertWordsOfTheHalvesWhole(where: String) {
        assertWholeOnOneLine(compose.onNodeWithText(word(SHOP), useUnmergedTree = true), "$where: ${word(SHOP)}")
        assertWholeOnOneLine(compose.onNodeWithText(word(ARRANGE), useUnmergedTree = true), "$where: ${word(ARRANGE)}")
    }

    private fun halvesWholeAtALargeFont(language: String) {
        speaking(language)
        fontScale = LARGE_FONT
        inWindow(PHONE, DpSize(360.dp, 640.dp))
        showHome()
        listOf(TRAVEL, SHOP, ARRANGE).forEach { assertInside("$language: ${word(it)}", button(word(it)).bounds(), window()) }
        assertWordsOfTheHalvesWhole("$language 360 at 1.3")
    }

    @Test
    fun atTheFont1_3On360TheHalvesKeepTheirWordsWholeInRussian() = halvesWholeAtALargeFont("ru")

    @Test
    fun atTheFont1_3On360TheHalvesKeepTheirWordsWholeInGerman() = halvesWholeAtALargeFont("de")

    @Test
    fun atTheFont1_3On360TheHalvesKeepTheirWordsWholeInFrench() = halvesWholeAtALargeFont("fr")

    /**
     * In the emulator's 640 × 360 lying (603 × 308 under its bars) the halves stand one under the other at a large font — at 1.3 for
     * sure, at 1.15 by a dp or two — and the room is some 66 to 71 high: the square «на весь экран» at its bottom end would lie on
     * «Снаружи» at its top end and take its touches (the review of stage 118). It stands only on a room that holds it clear of the
     * switch ([HomeRoomFit.SquareFrom], 116) — at the font 1 the room is 134 and it is there — and never on the switch.
     */
    @Test
    fun inTheLowWindowTheSquareNeverLiesOnTheSwitch() {
        speaking("ru")
        inWindow(DpSize(603.dp, 308.dp))
        showHome()
        for (font in listOf(1f, MIDDLE_FONT, LARGE_FONT)) {
            fontScale = font
            compose.waitForIdle()
            val where = "603 × 308 at $font"
            val room = said(word(ROOM)).bounds()
            val switch = tab(word(OUTSIDE)).bounds()
            val squares = compose.onAllNodesWithContentDescription(word(WHOLE)).fetchSemanticsNodes()
            assertEquals("$where: a square on a room of ${room.height} where it holds it, and only there", room.height >= HomeRoomFit.SquareFrom, squares.isNotEmpty())
            if (squares.isNotEmpty()) {
                val square = said(word(WHOLE)).bounds()
                assertFalse("$where: the square $square off the switch $switch", square.overlaps(switch))
            }
            if (font == 1f) assertTrue("$where: the room of ${room.height} holds the square at the font 1", squares.isNotEmpty())
            if (font == LARGE_FONT) assertTrue("$where: the halves one under the other leave the room ${room.height}", room.height < HomeRoomFit.SquareFrom)
        }
    }

    /**
     * Lying on a phone — the emulator's Pixel 7 is some 862 × 360 under its bars and its cutout, in a window of 914 × 411 — the left
     * column is 494: «Лавка» and «Обставить» are the zone's outlines by their words, with fields of 16 and of the height of the row
     * (5.29 R7, «Общее» and «Дом»), and leave «В дорогу» room for «Вена → хватает до Праги» whole on one line. With the fields of 24 of
     * an outline of 56 the line had some 119 dp for its 149 at 12 sp and was cut (the review of stage 118).
     */
    @Test
    fun lyingTheOutlinesOfTheRowOfThreeLeaveTheRoadItsLine() {
        speaking("ru")
        inWindow(DpSize(862.dp, 360.dp), DpSize(914.dp, 411.dp))
        showHome()
        val road = button(word(TRAVEL)).bounds()
        for (key in listOf(SHOP, ARRANGE)) {
            val outline = button(word(key)).bounds()
            val label = compose.onNodeWithText(word(key), useUnmergedTree = true).bounds()
            assertEquals("«${word(key)}»: the fields of 16 round the icon, its gap and the word", OUTLINE_AROUND.value, (outline.width - label.width).value, 1f)
            assertEquals("«${word(key)}» of the height of the row", road.height.value, outline.height.value, 0.5f)
            assertEquals("«${word(key)}» in the row", road.top.value, outline.top.value, 0.5f)
        }
        assertWholeOnOneLine(compose.onNodeWithText(word(TRAVEL_LINE), useUnmergedTree = true), "862 × 360 lying: ${word(TRAVEL_LINE)}")
    }

    // ---- the home: the gift and the next home

    @Test
    fun theGiftIsTakenRightOnTheHome() {
        ui = homeUi(InTheWoodenHouse.copy(purchased = BOUGHT - HomeCatalog.GIFT))
        showHome()
        val take = button(word(TAKE))
        assertEquals("the main one of 48", 48f, take.bounds().height.value, 0.5f)
        take.performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.GiftTaken), intents)
    }

    /**
     * Lying the gift stands in the column of 320 (spec 3.36.7); at the font 1.3 «Забрать» beside the words left them some 92 dp, and
     * «Бесплатно.» (≈ 97) and «Geschenk» (≈ 95) broke by the letter (the review of stage 118). No word of the gift breaks: where they do
     * not stand beside it, «Забрать» goes under the words, at the end of the card — in Russian by some 7 dp ([under]; in German by 2 dp
     * of a measure — there only the words are checked).
     */
    private fun giftWholeLying(language: String, under: Boolean) {
        speaking(language)
        fontScale = LARGE_FONT
        ui = homeUi(InTheWoodenHouse.copy(purchased = BOUGHT - HomeCatalog.GIFT))
        inWindow(DpSize(892.dp, 360.dp), DpSize(892.dp, 412.dp))
        showHome()
        val where = "$language lying at 1.3"
        assertWordsWhole(compose.onNodeWithText(word(GIFT_TITLE), useUnmergedTree = true), "$where: ${word(GIFT_TITLE)}")
        assertWordsWhole(compose.onNodeWithText(word(GIFT_TEXT), useUnmergedTree = true), "$where: ${word(GIFT_TEXT)}")
        val box = window()
        val about = DpRect(box.right - SIDE - ABOUT, box.top, box.right - SIDE, box.bottom)
        val take = button(word(TAKE)).bounds()
        assertTrue("$where: «${word(TAKE)}» at ${take.left}…${take.right} in the column of 320", take.left >= about.left - 0.5.dp && take.right <= about.right + 0.5.dp)
        if (under) {
            val text = compose.onNodeWithText(word(GIFT_TEXT), useUnmergedTree = true).bounds()
            assertTrue("$where: «${word(TAKE)}» $take under the words $text", take.top >= text.bottom - 0.5.dp)
        }
    }

    @Test
    fun lyingAtALargeFontTheGiftBreaksNoWordInRussian() = giftWholeLying("ru", under = true)

    @Test
    fun lyingAtALargeFontTheGiftBreaksNoWordInGerman() = giftWholeLying("de", under = false)

    /**
     * The next home is one row (spec 3.36.7), one phrase for a reader and a button that opens «Дома»: «Следующий дом, Квартира …, скоро,
     * 6 000 тактов» while it is not drawn; from the rented room — the wooden house, «1 200 из 3 000 тактов».
     */
    @Test
    fun theNextHomeIsOneRowThatOpensTheHomes() {
        showHome()
        val row = said(word(NEXT_SOON))
        assertEquals("a button", Role.Button, row.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertTrue("64 at least", row.bounds().height >= 64.dp - 0.5.dp)
        row.performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.HousesClicked), intents)
        ui = NothingBought
        compose.waitForIdle()
        said(word(NEXT_SHORT)).assertExists()
    }

    @Test
    fun withoutANextHomeTheRowSaysAllHomes() {
        var opened = 0
        show { NextHouseRow(next = null, balance = PURSE, onClick = { opened++ }) }
        said(word(ALL_HOUSES)).performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    // ---- «Дома»

    @Test
    fun theRowsOfTheHomesSayWhereOneLivesWhatCanBeBoughtAndWhatIsSoon() {
        ui = NothingBought
        showHouses()
        said(word(RENT_HERE)).assertExists()
        val wood = said(word(WOOD_SHORT))
        assertEquals("what can be bought is a button", Role.Button, wood.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        wood.performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.HouseClicked(WOOD)), intents)
        val flat = said(word(FLAT_SOON)).fetchSemanticsNode()
        assertFalse("what is soon is not pressed", SemanticsActions.OnClick in flat.config)
    }

    @Test
    fun aHomeBoughtAndLeftIsLivedInAgainByItsOwnButton() {
        showHouses()
        val rent = said(word(RENT_OWNED)).fetchSemanticsNode()
        assertFalse("the row itself is not pressed", SemanticsActions.OnClick in rent.config)
        button(word(LIVE)).performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.LiveHere(RENT)), intents)
    }

    /**
     * The name and the note of the wooden house keep their words whole, and [end] — what stands at the end of its row — stands under
     * them, at the end of the row: [endInset] in from the end of its words (the chevron after a line; nothing after a pill).
     */
    private fun assertTheWoodenHouseWhole(where: String, end: DpRect, endInset: Dp) {
        val note = compose.onNodeWithText(word(WOOD_NOTE), useUnmergedTree = true)
        assertWordsWhole(compose.onNodeWithText(word(WOOD_NAME), useUnmergedTree = true), "$where: ${word(WOOD_NAME)}")
        assertWordsWhole(note, "$where: ${word(WOOD_NOTE)}")
        val words = note.bounds()
        assertTrue("$where: the end $end under the words $words", end.top >= words.bottom - 0.5.dp)
        assertEquals("$where: the end at the end of the row", (window().right - SIDE - HOUSE_PADDING - endInset).value, end.right.value, 1f)
    }

    /**
     * On a phone of 360 what stands at the end of the row of the wooden house — «хватает · 3 000 ›», «1 200 / 3 000 ›», «Жить здесь» —
     * leaves its words 64 to 79 dp in Russian, where «Маленький деревянный домик» does not stand on two lines of whole words even at
     * 13 sp: a word broke by the letter (the review of stage 118). The end goes under the words then, and the name and the note stay
     * whole. On 412 beside «хватает · 3 000» the name had 116 — «Маленький / деревянный д…» — and the end goes under there too.
     */
    @Test
    fun theNameOfTheWoodenHouseBreaksNoWordAndWhatStandsAtTheEndOfItsRowGoesUnder() {
        speaking("ru")
        inWindow(PHONE, DpSize(360.dp, 640.dp))
        ui = NoWoodWithAPurse
        showHouses()
        assertTheWoodenHouseWhole("360, «хватает · 3 000»", compose.onNodeWithText(word(WOOD_ENOUGH_LINE), useUnmergedTree = true).bounds(), CHEVRON)
        ui = NothingBought
        compose.waitForIdle()
        assertTheWoodenHouseWhole("360, «1 200 / 3 000»", compose.onNodeWithText(word(WOOD_SHORT_LINE), useUnmergedTree = true).bounds(), CHEVRON)
        ui = WoodLeft
        compose.waitForIdle()
        assertTheWoodenHouseWhole("360, «Жить здесь»", button(word(LIVE)).bounds(), 0.dp)
        ui = NoWoodWithAPurse
        inWindow(DpSize(412.dp, 800.dp))
        assertTheWoodenHouseWhole("412, «хватает · 3 000»", compose.onNodeWithText(word(WOOD_ENOUGH_LINE), useUnmergedTree = true).bounds(), CHEVRON)
    }

    /**
     * The sheet of the wooden house from the rented room with 1 200 (spec 3.36.7): the plate «не хватает 1 800» — words, not a button —
     * and under it how many practices by the numbers of the graph ([HomeUi.config]: 250 takts a practice here, not the 300 of the
     * default — «примерно 8 занятий»); a swipe only hides the sheet: no move.
     */
    @Test
    fun theSheetOfAHomeSaysWhatIsMissingAndASwipeOnlyHidesIt() {
        ui = NothingBought.copy(config = JourneyConfig(taktsPerSessionHint = HINT_250))
        showHouses()
        ui = ui.copy(houseCard = HomeCatalog.houseById.getValue(WOOD))
        compose.waitForIdle()
        val plate = said(word(MISSING)).fetchSemanticsNode()
        assertEquals("no role", null, plate.config.getOrNull(SemanticsProperties.Role))
        assertFalse("no click", SemanticsActions.OnClick in plate.config)
        compose.onNodeWithText(word(SESSIONS)).assertExists()
        compose.onNode(hasText(word(WOOD_NAME)) and isHeading()).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
        assertEquals("hidden, and nothing more", listOf<HomeIntent>(HomeIntent.HouseCardClosed), intents)
        compose.onAllNodes(hasText(word(WOOD_NAME)) and isHeading()).assertCountEquals(0)
    }

    @Test
    fun theSheetOfAHomeMovesByItsButton() {
        ui = homeUi(HomeState(loaded = true, purchased = BOUGHT, houses = emptySet(), choices = emptyMap()))
        showHouses()
        ui = ui.copy(houseCard = HomeCatalog.houseById.getValue(WOOD))
        compose.waitForIdle()
        button(word(MOVE)).performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.MoveClicked), intents)
        compose.onAllNodes(hasContentDescription(word(MISSING))).assertCountEquals(0)
    }

    private companion object {
        val VIENNA = JourneyRoute.indexOf("vienna")
        const val RENT = HomeCatalog.START_HOUSE
        const val WOOD = "wood"
        const val FLAT = "flat"
        val WOOD_PRICE = HomeCatalog.houseById.getValue(WOOD).price.toLong()
        val FLAT_PRICE = HomeCatalog.houseById.getValue(FLAT).price.toLong()

        /** The purse of the mockups; a purse short of the wooden house. */
        const val PURSE = 47_884L
        const val SHORT_PURSE = 1_200L

        /** Seven things, three of them brought from the road; the gift taken. */
        val BOUGHT = setOf(HomeCatalog.GIFT, "vln_master", "case_velvet", "stand_wood", "metronome", "portrait", "notes")
        val InTheWoodenHouse = HomeState(loaded = true, purchased = BOUGHT, houses = setOf(WOOD), choices = mapOf(HomeState.HOUSE_KEY to WOOD))

        fun progressAt(balance: Long): JourneyProgress {
            val spent = JourneyRoute.stops.take(VIENNA + 1).sumOf { it.price.toLong() }
            return JourneyProgress(earned = spent + balance, spent = spent, arrivals = (0..VIENNA).map { Arrival(JourneyRoute.stops[it].id, 0L) }, extras = emptySet())
        }

        fun homeUi(home: HomeState = InTheWoodenHouse, balance: Long = PURSE) = HomeUi(
            loading = false,
            home = home,
            progress = progressAt(balance),
            house = home.choices[HomeState.HOUSE_KEY] ?: RENT,
        )

        /** Nothing bought, the rented room lived in: the gift waits, the wooden house is 1 200 / 3 000 away. */
        val NothingBought = homeUi(HomeState(loaded = true, purchased = emptySet(), houses = emptySet(), choices = emptyMap()), balance = SHORT_PURSE)

        /** The rented room lived in, the wooden house not bought, the purse of the mockups: «хватает · 3 000». */
        val NoWoodWithAPurse = homeUi(HomeState(loaded = true, purchased = emptySet(), houses = emptySet(), choices = emptyMap()))

        /** The wooden house bought and left for the rented room: «Жить здесь». */
        val WoodLeft = homeUi(HomeState(loaded = true, purchased = BOUGHT, houses = setOf(WOOD), choices = emptyMap()))

        /** A practice of 250 takts in the numbers of the graph: 1 800 missing is 8 practices (300 would say 6). */
        const val HINT_250 = 250
        const val SESSIONS_AT_250 = 8

        const val TRAVEL = "travel"
        const val TRAVEL_LINE = "travelLine"
        const val SHOP = "shop"
        const val ARRANGE = "arrange"
        const val ROOM = "room"
        const val OUTSIDE = "outside"
        const val WHOLE = "whole"
        const val TAKE = "take"
        const val NEXT_SOON = "nextSoon"
        const val NEXT_SHORT = "nextShort"
        const val ALL_HOUSES = "allHouses"
        const val RENT_HERE = "rentHere"
        const val RENT_OWNED = "rentOwned"
        const val WOOD_SHORT = "woodShort"
        const val FLAT_SOON = "flatSoon"
        const val LIVE = "live"
        const val MOVE = "move"
        const val MISSING = "missing"
        const val SESSIONS = "sessions"
        const val WOOD_NAME = "woodName"
        const val WOOD_NOTE = "woodNote"
        const val WOOD_ENOUGH_LINE = "woodEnoughLine"
        const val WOOD_SHORT_LINE = "woodShortLine"
        const val GIFT_TITLE = "giftTitle"
        const val GIFT_TEXT = "giftText"

        /** A phone of 360 × 640 under its bars (24 and a gesture bar of 24), and its room (5.29 R7: 536 − 132 − 120). */
        val PHONE = DpSize(360.dp, 592.dp)
        val PHONE_ROOM = 284.dp

        /** The sides of the screen and the column of the words lying. */
        val SIDE = 16.dp
        val ABOUT = 320.dp

        /** What lies on the room stands 8 from its edges; the 48 of a switch stands 4 over its capsule, its words in a field of 3. */
        val PICTURE_INSET = 8.dp
        val TWO_WAY_AIR = 4.dp
        val TWO_WAY_FIELD = 3.dp

        /** The two large fonts of Android under 2: at 1.15 the halves stand one under the other by a dp or two, at 1.3 for sure. */
        const val MIDDLE_FONT = 1.15f
        const val LARGE_FONT = 1.3f

        /** An outline of the zone: fields of 16 round its icon of 20 and the gap of 8 to its word (5.29 R7). */
        val OUTLINE_AROUND = 16.dp + 20.dp + 8.dp + 16.dp

        /** The fields of a row of «Дома» and the chevron at the end of its line. */
        val HOUSE_PADDING = 14.dp
        val CHEVRON = 24.dp
        const val SWIPE = 700
        const val SWIPE_MS = 150L
    }
}
