package com.violinjourney.app.feature.journey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.BoughtExtra
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyExtra
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.journey_card_description
import com.violinjourney.app.shared.resources.journey_extra_bought
import com.violinjourney.app.shared.resources.journey_extra_souvenir
import com.violinjourney.app.shared.resources.journey_extra_souvenir_note
import com.violinjourney.app.shared.resources.journey_extra_time
import com.violinjourney.app.shared.resources.journey_extra_time_note
import com.violinjourney.app.shared.resources.journey_extra_view
import com.violinjourney.app.shared.resources.journey_extra_view_inside
import com.violinjourney.app.shared.resources.journey_extra_view_outside
import com.violinjourney.app.shared.resources.journey_extras
import com.violinjourney.app.shared.resources.journey_extras_hint
import com.violinjourney.app.shared.resources.journey_fullscreen
import com.violinjourney.app.shared.resources.journey_go_home
import com.violinjourney.app.shared.resources.journey_here
import com.violinjourney.app.shared.resources.journey_here_end
import com.violinjourney.app.shared.resources.journey_here_enough
import com.violinjourney.app.shared.resources.journey_map_description
import com.violinjourney.app.shared.resources.journey_missing
import com.violinjourney.app.shared.resources.journey_mode_evening
import com.violinjourney.app.shared.resources.journey_mode_inside
import com.violinjourney.app.shared.resources.journey_mode_outside
import com.violinjourney.app.shared.resources.journey_next_short
import com.violinjourney.app.shared.resources.journey_passport_next
import com.violinjourney.app.shared.resources.journey_stamp_description
import com.violinjourney.app.shared.resources.journey_stamp_locked_description
import com.violinjourney.app.shared.resources.journey_stop_meta
import com.violinjourney.app.shared.resources.venue_play_here
import com.violinjourney.app.shared.resources.venue_play_here_caption
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The stop, the map and the passport of R7 (spec 3.36.7, 5.29 R7) by what an eye, a finger and a reader meet. The stop: its zone holds
 * «Играть здесь» with «Live · Золотой зал» and «Домой» under it — or beside it lying, «Домой» 120 at least and its words whole, in
 * German too — in every window, the low ones of the emulator too; the plates of the views lie on the postcard, 8 from its edges, in one
 * row where they fit beside the square «на весь экран», the second over the first where they do not (on 360 in Russian and German);
 * an extra is one phrase for a reader and a button only while it buys — the pill is the finger's —, every row 60, and no word of its
 * name or note breaks: lying in the low window what stands at its end goes under them; «маленькие цели в дороге» goes under its
 * heading where it does not fit beside it; the line of the place is one text. The map: the card «вы здесь» stands under the scheme,
 * never over it, and opens the stop the road stands at — home too; lying 360 wide at the bottom start. The passport: three stamps of
 * 96 in a row, lying too, and the next one says so; a caption stays on one line, smaller; the sticker of a souvenir is not cut by its
 * cell.
 *
 * Laid out in a window of its own size ([TestWindow]); the words are read in the composition, in the language of the process.
 */
@RunWith(AndroidJUnit4::class)
class JourneyPlacesTest {
    @get:Rule
    val compose = createComposeRule()

    private val stopIntents = mutableListOf<StopIntent>()
    private val intents = mutableListOf<JourneyIntent>()
    private val words = mutableMapOf<String, String>()
    private var stop by mutableStateOf(stopAt(VIENNA, PURSE))
    private var journey by mutableStateOf(stateAt(VIENNA, PURSE))
    private var windowSize by mutableStateOf(DpSize(412.dp, 800.dp))
    private var told by mutableStateOf<DpSize?>(null)
    private var fontScale by mutableFloatStateOf(1f)

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun show(screen: @Composable () -> Unit) {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                TestWindow(windowSize, told ?: windowSize, fontScale) {
                    CompositionLocalProvider(LocalHomeLook provides HOME_LOOK) { screen() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showStop() = show { StopScreen(stop, onIntent = { stopIntents += it }) }

    private fun showMap() = show { MapScreen(journey, onIntent = { intents += it }) }

    private fun showPassport() = show { PassportScreen(journey, onIntent = { intents += it }) }

    /** The words of the screens of the tests, read where the screens read them. */
    @Composable
    private fun ReadWords() {
        val dot = stringResource(Res.string.dot_separator)
        fun extra(title: String, note: String, vararg tail: String) = (listOf(title, note) + tail).joinToString(", ")
        val time = stringResource(Res.string.journey_extra_time)
        val view = stringResource(Res.string.journey_extra_view)
        val souvenir = stringResource(Res.string.journey_extra_souvenir)
        val timeNote = stringResource(Res.string.journey_extra_time_note)
        val inside = stringResource(Res.string.journey_extra_view_inside)
        val outside = stringResource(Res.string.journey_extra_view_outside)
        val souvenirNote = stringResource(Res.string.journey_extra_souvenir_note)
        words[PLAY] = stringResource(Res.string.venue_play_here)
        words[PLAY_VIENNA] = stringResource(Res.string.venue_play_here_caption, hallOf(VIENNA))
        words[PLAY_CREMONA] = stringResource(Res.string.venue_play_here_caption, hallOf(CREMONA))
        words[HOME] = stringResource(Res.string.journey_go_home)
        words[POSTCARD] = stringResource(Res.string.journey_card_description, cityOf(VIENNA))
        words[WHOLE] = stringResource(Res.string.journey_fullscreen)
        words[EVENING] = stringResource(Res.string.journey_mode_evening)
        words[OUTSIDE] = stringResource(Res.string.journey_mode_outside)
        words[INSIDE] = stringResource(Res.string.journey_mode_inside)
        words[PLACE_LINE] = listOf(
            placeOf(VIENNA), countryOf(VIENNA), stringResource(Res.string.journey_stop_meta, VIENNA, TOTAL, Formats.dayAndMonth(dayMs(VIENNA), TimeZone.currentSystemDefault())),
        ).joinToString(dot)
        words[EXTRAS] = stringResource(Res.string.journey_extras)
        words[EXTRAS_HINT] = stringResource(Res.string.journey_extras_hint)
        words[TIME_TITLE] = time
        words[TIME_NOTE] = timeNote
        words[VIEW_TITLE] = view
        words[VIEW_NOTE] = inside
        words[SOUVENIR_TITLE] = souvenir
        words[SOUVENIR_NOTE] = souvenirNote
        words[OPENED] = stringResource(Res.string.journey_extra_bought)
        words[SPB_CITY] = cityOf(SPB)
        words[TIME_TO_BUY] = extra(time, timeNote, taktsInWords(TIME_PRICE))
        words[TIME_OPEN] = extra(time, timeNote, stringResource(Res.string.journey_extra_bought))
        words[VIEW_SHORT] = extra(view, inside, taktsInWords(VIEW_PRICE), stringResource(Res.string.journey_missing, Formats.takts(VIEW_PRICE - SMALL_PURSE)))
        words[VIEW_OUTSIDE] = extra(view, outside, taktsInWords(VIEW_PRICE))
        words[SOUVENIR_TO_BUY] = extra(souvenir, souvenirNote, taktsInWords(SOUVENIR_PRICE))
        val here = stringResource(Res.string.journey_here)
        words[HERE_ENOUGH] = listOf(cityOf(VIENNA), here, stringResource(Res.string.journey_here_enough, cityToOf(PRAGUE))).joinToString(", ")
        words[HERE_SHORT] = listOf(cityOf(VIENNA), here, stringResource(Res.string.journey_next_short, cityToOf(PRAGUE), Formats.takts(PRAGUE_PRICE - SMALL_PURSE))).joinToString(", ")
        words[HERE_HOME] = listOf(cityOf(0), here, stringResource(Res.string.journey_here_enough, cityToOf(1))).joinToString(", ")
        words[HERE_END] = listOf(cityOf(SYDNEY), here, stringResource(Res.string.journey_here_end)).joinToString(", ")
        words[MAP] = stringResource(Res.string.journey_map_description, VIENNA, TOTAL)
        (1..VIENNA).forEach { words[STAMP + it] = stringResource(Res.string.journey_stamp_description, cityOf(it)) }
        words[PRAGUE_NEXT] = stringResource(Res.string.journey_stamp_locked_description, cityOf(PRAGUE)) + ", " + stringResource(Res.string.journey_passport_next)
        words[LEIPZIG_AHEAD] = stringResource(Res.string.journey_stamp_locked_description, cityOf(PRAGUE + 1))
    }

    private fun word(key: String): String = words.getValue(key)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun window(): DpRect = compose.onNodeWithTag(TEST_WINDOW).bounds()

    /** The button of [text] — its capsule, which is what is pressed: the widening of touch targets is off. */
    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    /** A tab of a two-way switch by its word. */
    private fun tab(text: String) = compose.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    /** A card by the one phrase a reader hears of it. */
    private fun said(description: String) = compose.onNodeWithContentDescription(description)

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

    // ---- the stop: the zone

    @Test
    fun theZoneOfTheStopHoldsPlayHereWithItsHallAndHomeUnderIt() {
        showStop()
        val play = button(word(PLAY))
        assertEquals(
            "«Играть здесь» says where Live opens",
            listOf(word(PLAY), word(PLAY_VIENNA)),
            play.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text },
        )
        val key = play.bounds()
        val home = button(word(HOME)).bounds()
        assertInside("«Играть здесь»", key, window())
        assertInside("«Домой»", home, window())
        assertEquals("the main one of 56", 56f, key.height.value, 0.5f)
        assertEquals("«Домой» an outline of 48", 48f, home.height.value, 0.5f)
        assertEquals("«Домой» 10 under it", 10f, (home.top - key.bottom).value, 0.5f)
        assertEquals("«Домой» on the whole width", key.width.value, home.width.value, 0.5f)
        play.performClick()
        button(word(HOME)).performClick()
        assertEquals(listOf(StopIntent.PlayHereClicked, StopIntent.HomeClicked), stopIntents)
    }

    /**
     * Every window of the stop keeps its zone in it (spec 3.36.7, 5.29 R7): upright «Домой» under «Играть здесь»; lying the two in one
     * row of one height in the left column of 360, «Домой» 120 wide at least; in a window no higher than 360 the buttons are 48. The
     * emulator's 640 × 360 lying is 603 × 308 under its bars (603 × 336 with a thin status bar); 892 × 412 lying is some 360 high and
     * keeps the zone of a window of 412.
     */
    @Test
    fun inEveryWindowTheZoneOfTheStopStandsInIt() = zoneInEveryWindow()

    /** «Nach Hause» with its house does not fit 120 (D5): «Домой» is wider lying, and its words stay on one line. */
    @Test
    fun inEveryWindowTheZoneOfTheStopStandsInItInGerman() {
        speaking("de")
        zoneInEveryWindow()
    }

    private fun zoneInEveryWindow() {
        showStop()
        for ((box, window) in WINDOWS) {
            inWindow(box, window)
            val where = "${box.width} × ${box.height}"
            val key = button(word(PLAY)).bounds()
            val home = button(word(HOME)).bounds()
            assertInside("$where: «Играть здесь»", key, window())
            assertInside("$where: «Домой»", home, window())
            if (window.height <= DockMetrics.TinyUpTo) assertEquals("$where: the main one of 48", 48f, key.height.value, 0.5f)
            if (box.width > box.height) {
                val column = DpRect(window().left + SIDE, window().top, window().left + SIDE + LEFT_COLUMN, window().bottom)
                assertInside("$where: lying, «Играть здесь» in the left column", key, column)
                assertInside("$where: lying, «Домой» in the left column", home, column)
                assertEquals("$where: lying, one row", key.top.value, home.top.value, 0.5f)
                assertEquals("$where: lying, of one height", key.height.value, home.height.value, 0.5f)
                assertTrue("$where: lying, «Домой» 120 at least: ${home.width}", home.width >= HOME_LYING_MIN - 0.5.dp)
                // wider where its words ask for it (D5): a button of exactly 120 would put «Nach Hause» on two lines
                assertWholeOnOneLine(compose.onNodeWithText(word(HOME), useUnmergedTree = true), "$where: lying, ${word(HOME)}")
                assertTrue("$where: lying, «Домой» after «Играть здесь»", home.left >= key.right)
            } else {
                assertTrue("$where: upright, «Домой» under «Играть здесь»", home.top >= key.bottom)
            }
        }
    }

    @Test
    fun aStopNotReachedHasNoZone() {
        stop = stopAt(VIENNA, PURSE).copy(arrivedAtEpochMs = null)
        showStop()
        compose.onAllNodes(hasText(word(PLAY))).assertCountEquals(0)
        compose.onAllNodes(hasText(word(HOME))).assertCountEquals(0)
    }

    /**
     * Lying the postcard of the stop never stands in the fade of its zone (5.29 R7: the pictures give way first): 180 where there is
     * room — 892 × 412 — and lower in the emulator's 603 × 308, so its plates and its square stand over the fade.
     */
    @Test
    fun lyingThePostcardStandsOverTheFadeOfTheZone() {
        stop = stopAt(VIENNA, PURSE, BOTH_VIEWS)
        showStop()
        for ((box, window) in WINDOWS.filter { (box, _) -> box.width > box.height }) {
            inWindow(box, window)
            val where = "${box.width} × ${box.height}"
            val card = said(word(POSTCARD)).bounds()
            val fade = button(word(PLAY)).bounds().top - DockMetrics.of(window.height).top - DockDefaults.Fade
            assertTrue("$where: the postcard at $card ends over the fade at $fade", card.bottom <= fade + 0.5.dp)
            assertTrue("$where: 180 at most: ${card.height}", card.height <= POSTCARD_LYING + 0.5.dp)
            if (box.height >= 360.dp) assertEquals("$where: 180 where there is room", POSTCARD_LYING.value, card.height.value, 0.5f)
        }
    }

    // ---- the stop: the postcard and its plates

    /** The two plates of [where] lie on the postcard 8 from its start and its bottom: the 48 of a plate stands 4 under its capsule. */
    private fun assertOnThePostcard(where: String, first: DpRect, card: DpRect) {
        assertEquals("$where: the plate 8 over the bottom of the postcard", (card.bottom - PLATE_AIR).value, first.bottom.value, 0.5f)
        assertEquals("$where: the plate 8 from its start (its first word in the field of 3)", (card.left + PICTURE_INSET + PLATE_FIELD).value, first.left.value, 0.5f)
    }

    @Test
    fun thePlatesLieOnThePostcardInOneRowWhereTheyFit() {
        speaking("de")
        stop = stopAt(VIENNA, PURSE, BOTH_VIEWS)
        showStop()
        val card = said(word(POSTCARD)).bounds()
        val evening = tab(word(EVENING)).bounds()
        val outside = tab(word(OUTSIDE)).bounds()
        assertOnThePostcard("de 412", evening, card)
        assertEquals("de 412: one row", evening.top.value, outside.top.value, 0.5f)
        assertTrue("de 412: the second after the first", outside.left >= evening.right)
        // the end of the second plate — its last word and its field of 3 — leaves the square its room
        val inside = tab(word(INSIDE)).bounds()
        assertTrue("de 412: the plates leave the square its room: $inside, $card", inside.right + PLATE_FIELD <= card.right - PLATES_RESERVE + 0.5.dp)
    }

    @Test
    fun theSecondPlateStandsOverTheFirstWhereTheyDoNotFitARow() {
        speaking("fr")
        fontScale = LARGE_FONT
        windowSize = DpSize(360.dp, 640.dp)
        stop = stopAt(VIENNA, PURSE, BOTH_VIEWS)
        showStop()
        val card = said(word(POSTCARD)).bounds()
        val evening = tab(word(EVENING)).bounds()
        val outside = tab(word(OUTSIDE)).bounds()
        assertOnThePostcard("fr 360 at 1.3", evening, card)
        assertTrue("fr 360 at 1.3: the second over the first: $outside, $evening", outside.bottom <= evening.top + 0.5.dp)
        assertEquals("fr 360 at 1.3: one start", evening.left.value, outside.left.value, 0.5f)
    }

    /**
     * On a phone of 360 the postcard is 328 wide and the row of the plates may take 256 of it beside the square «на весь экран» (8,
     * 48 and its 8): the row of the two is some 298 dp in Russian and 264 in German at the font 1 — it would fit the postcard without
     * the room of the square (320), and the square would lie on «внутри» / «innen» and take its touches. The second stands over the
     * first (5.29 R7; the review of stage 118: the rule of the room of the square was held by no test).
     */
    private fun secondPlateOverTheFirstOn360(language: String) {
        speaking(language)
        windowSize = DpSize(360.dp, 640.dp)
        stop = stopAt(VIENNA, PURSE, BOTH_VIEWS)
        showStop()
        val card = said(word(POSTCARD)).bounds()
        val evening = tab(word(EVENING)).bounds()
        val outside = tab(word(OUTSIDE)).bounds()
        assertOnThePostcard("$language 360", evening, card)
        assertTrue("$language 360: the second over the first: $outside, $evening", outside.bottom <= evening.top + 0.5.dp)
        val square = said(word(WHOLE)).bounds()
        val inside = tab(word(INSIDE)).bounds()
        assertTrue("$language 360: the plates off the square: $inside, $square", inside.right <= square.left || inside.bottom <= square.top)
    }

    @Test
    fun onAPhoneTheSecondPlateStandsOverTheFirstInRussian() = secondPlateOverTheFirstOn360("ru")

    @Test
    fun onAPhoneTheSecondPlateStandsOverTheFirstInGerman() = secondPlateOverTheFirstOn360("de")

    @Test
    fun theSquareOfTheWholeScreenIs48In8FromTheCornerOfThePostcard() {
        showStop()
        val card = said(word(POSTCARD)).bounds()
        val square = said(word(WHOLE))
        val bounds = square.bounds()
        assertEquals(48f, bounds.width.value, 0.5f)
        assertEquals(48f, bounds.height.value, 0.5f)
        assertEquals("8 from the end", (card.right - PICTURE_INSET).value, bounds.right.value, 0.5f)
        assertEquals("8 from the bottom", (card.bottom - PICTURE_INSET).value, bounds.bottom.value, 0.5f)
        square.performClick()
        assertEquals(listOf<StopIntent>(StopIntent.PostcardClicked), stopIntents)
    }

    @Test
    fun theLineOfThePlaceIsOneText() {
        showStop()
        compose.onNodeWithText(word(PLACE_LINE)).assertExists()
    }

    // ---- the stop: the extras

    /**
     * An extra is one phrase for a reader (spec 3.36.7): «Второе время суток, день вместо вечера, 200 тактов» — a button, its click buys;
     * the takts short: «Второй вид, вид изнутри, 400 тактов, не хватает 100» — no role, no click, never «disabled»; bought: «…,
     * открыто». The pill at its end is the finger's: a touch on it buys, a touch on the words does nothing.
     */
    @Test
    fun anExtraIsOnePhraseAndAButtonOnlyWhileItBuys() {
        // tall enough for the extras to stand over the zone: a touch on a row under it would land on the zone
        windowSize = DpSize(412.dp, 1000.dp)
        stop = stopAt(VIENNA, SMALL_PURSE)
        showStop()
        compose.onNode(hasText(word(EXTRAS)) and isHeading()).assertExists()
        val time = said(word(TIME_TO_BUY))
        val node = time.fetchSemanticsNode()
        assertEquals("a button", Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        time.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf<StopIntent>(StopIntent.BuyClicked(JourneyExtra.SECOND_TIME)), stopIntents)
        val short = said(word(VIEW_SHORT)).fetchSemanticsNode()
        assertEquals("no role", null, short.config.getOrNull(SemanticsProperties.Role))
        assertFalse("no click", SemanticsActions.OnClick in short.config)
        assertFalse("never «disabled»", SemanticsProperties.Disabled in short.config)
        said(word(SOUVENIR_TO_BUY)).assertExists()
        // the finger: the pill at the end of the row buys, the words of the row do not
        stopIntents.clear()
        val row = said(word(SOUVENIR_TO_BUY))
        row.performTouchInput { click(Offset(WORDS_X.toPx(), centerY)) }
        compose.runOnIdle { assertEquals("the words of the row are not a button", emptyList<StopIntent>(), stopIntents) }
        row.performTouchInput { click(Offset(width - PILL_X.toPx(), centerY)) }
        compose.runOnIdle { assertEquals(listOf<StopIntent>(StopIntent.BuyClicked(JourneyExtra.SOUVENIR)), stopIntents) }
    }

    @Test
    fun aBoughtExtraSaysItIsOpen() {
        stop = stopAt(VIENNA, PURSE, setOf(JourneyExtra.SECOND_TIME))
        showStop()
        val node = said(word(TIME_OPEN)).fetchSemanticsNode()
        assertEquals("no role", null, node.config.getOrNull(SemanticsProperties.Role))
        assertFalse("no click", SemanticsActions.OnClick in node.config)
    }

    /**
     * Every row of the extras is 60 at the font 1 (spec 5.29 R7: fields of 8, the capsule of a price 40 — the mockup's `.addon`), whatever
     * stands at its end: «открыто», the price in words, the price in its pill — whose touch of 48 lies in the fields of the row, not
     * under them (the review of stage 118: 8 + 48 + 8 made the row with a pill 64).
     */
    @Test
    fun everyRowOfTheExtrasIs60() {
        windowSize = DpSize(412.dp, 1000.dp)
        // the second time bought, the second view 100 short, the souvenir to buy
        stop = stopAt(VIENNA, SMALL_PURSE, setOf(JourneyExtra.SECOND_TIME))
        showStop()
        listOf(TIME_OPEN, VIEW_SHORT, SOUVENIR_TO_BUY).forEach { key ->
            assertEquals("«${word(key)}»: a row of 60", ROW_HEIGHT.value, said(word(key)).bounds().height.value, 0.5f)
        }
    }

    /**
     * Lying in the emulator's 640 × 360 (603 × 308 under its bars) the words of the extras have a column of 195: beside the chip
     * «✓ sbloccato» «momento» of «Un altro momento del giorno» had 62 dp for its 70, and broke by the letter; beside «✓ geöffnet»
     * «Tageszeit» had 70 for its 71 (the review of stage 118). No word of a name or a note breaks: what stands at the end of a row goes
     * under its words, at the end of the row, where they do not stand beside it — in Italian the chip by 8 dp ([chipUnder]); in German by
     * a dp of a measure, so there only the words are checked.
     */
    private fun extrasWholeLying(language: String, chipUnder: Boolean) {
        speaking(language)
        inWindow(DpSize(603.dp, 308.dp))
        stop = stopAt(VIENNA, PURSE, setOf(JourneyExtra.SECOND_TIME))
        showStop()
        val where = "$language 603 × 308"
        listOf(TIME_TITLE, TIME_NOTE, VIEW_TITLE, VIEW_NOTE, SOUVENIR_TITLE, SOUVENIR_NOTE).forEach { key ->
            assertWordsWhole(compose.onNodeWithText(word(key), useUnmergedTree = true), "$where: ${word(key)}")
        }
        val row = said(word(TIME_OPEN)).bounds()
        val chip = compose.onNodeWithText(word(OPENED), useUnmergedTree = true).bounds()
        assertInside("$where: «${word(OPENED)}» in its row", chip, row)
        if (chipUnder) {
            val note = compose.onNodeWithText(word(TIME_NOTE), useUnmergedTree = true).bounds()
            assertTrue("$where: «${word(OPENED)}» $chip under the words $note", chip.top >= note.bottom - 0.5.dp)
        }
    }

    @Test
    fun lyingInTheLowWindowNoWordOfTheExtrasBreaksInItalian() = extrasWholeLying("it", chipUnder = true)

    @Test
    fun lyingInTheLowWindowNoWordOfTheExtrasBreaksInGerman() = extrasWholeLying("de", chipUnder = false)

    /**
     * «маленькие цели в дороге» stands at the end of the line of «Дополнения» where the two fit it — 412 at the font 1 — and under it,
     * from the start of the line, where they do not (5.29 R7): on 360 at 1.3 in Russian and French the two take some 360 dp of 328.
     */
    private fun hintOfTheExtras(language: String) {
        speaking(language)
        windowSize = DpSize(412.dp, 1000.dp)
        stop = stopAt(VIENNA, PURSE)
        showStop()
        val heading = compose.onNode(hasText(word(EXTRAS)) and isHeading()).bounds()
        val hint = compose.onNodeWithText(word(EXTRAS_HINT)).bounds()
        assertTrue("$language 412: the hint beside the heading: $hint, $heading", hint.left >= heading.right && hint.top < heading.bottom)
        assertEquals("$language 412: at the end of the line", (window().right - SIDE).value, hint.right.value, 0.5f)
        fontScale = LARGE_FONT
        windowSize = DpSize(360.dp, 1000.dp)
        compose.waitForIdle()
        val under = compose.onNode(hasText(word(EXTRAS)) and isHeading()).bounds()
        val note = compose.onNodeWithText(word(EXTRAS_HINT)).bounds()
        assertTrue("$language 360 at 1.3: the hint under the heading: $note, $under", note.top >= under.bottom - 0.5.dp)
        assertEquals("$language 360 at 1.3: from the start of the line", under.left.value, note.left.value, 0.5f)
    }

    @Test
    fun theHintOfTheExtrasGoesUnderTheirHeadingWhereTheyDoNotFitALineInRussian() = hintOfTheExtras("ru")

    @Test
    fun theHintOfTheExtrasGoesUnderTheirHeadingWhereTheyDoNotFitALineInFrench() = hintOfTheExtras("fr")

    /** At Cremona the main view is inside: the second one is the view from outside; Live opens in the workshop. */
    @Test
    fun atCremonaTheSecondViewIsFromOutsideAndLiveIsInTheWorkshop() {
        stop = stopAt(CREMONA, PURSE)
        showStop()
        said(word(VIEW_OUTSIDE)).assertExists()
        assertEquals(
            listOf(word(PLAY), word(PLAY_CREMONA)),
            button(word(PLAY)).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text },
        )
    }

    // ---- the map

    /**
     * The card «вы здесь» stands under the scheme on a phone of 360 × 640, 16 from the edges of the screen — over the bottom of the scheme
     * it would hide Buenos Aires and Sydney — and the scheme fits the room over it; for a reader one phrase, a button; a tap opens the
     * stop the road stands at.
     */
    @Test
    fun theCardYouAreHereStandsUnderTheSchemeAndOpensItsStop() {
        windowSize = PHONE
        told = DpSize(360.dp, 640.dp)
        showMap()
        val card = said(word(HERE_ENOUGH))
        val bounds = card.bounds()
        val box = window()
        assertEquals("a button", Role.Button, card.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertTrue("64 at least: ${bounds.height}", bounds.height >= 64.dp - 0.5.dp)
        assertEquals("16 from the bottom", (box.bottom - MARGIN).value, bounds.bottom.value, 0.5f)
        assertEquals("16 from the start", (box.left + MARGIN).value, bounds.left.value, 0.5f)
        assertEquals("16 from the end", (box.right - MARGIN).value, bounds.right.value, 0.5f)
        val scheme = said(word(MAP)).bounds()
        assertTrue("the scheme ends over the card: $scheme, $bounds", scheme.bottom <= bounds.top + 0.5.dp)
        card.performClick()
        assertEquals(listOf<JourneyIntent>(JourneyIntent.StopClicked("vienna")), intents)
    }

    @Test
    fun theCardYouAreHereSaysWhatIsMissingAtHomeAndAtTheEnd() {
        journey = stateAt(VIENNA, SMALL_PURSE)
        showMap()
        said(word(HERE_SHORT)).assertExists()
        journey = stateAt(0, HOME_PURSE)
        compose.waitForIdle()
        said(word(HERE_HOME)).performClick()
        // at home it is a tap on the point of home: the model takes the player home, through the title card
        assertEquals(listOf<JourneyIntent>(JourneyIntent.StopClicked(JourneyRoute.HOME)), intents)
        journey = stateAt(SYDNEY, PURSE)
        compose.waitForIdle()
        said(word(HERE_END)).assertExists()
    }

    @Test
    fun lyingTheCardStands360WideAtTheBottomStartAndTheSchemeOnItsRight() {
        windowSize = DpSize(892.dp, 360.dp)
        told = DpSize(892.dp, 412.dp)
        showMap()
        val card = said(word(HERE_ENOUGH)).bounds()
        val box = window()
        assertEquals("360 wide", HERE_LYING.value, card.width.value, 0.5f)
        assertEquals("16 from the start", (box.left + MARGIN).value, card.left.value, 0.5f)
        assertEquals("16 from the bottom", (box.bottom - MARGIN).value, card.bottom.value, 0.5f)
        val scheme = said(word(MAP)).bounds()
        assertTrue("the scheme on the right of the card: $scheme, $card", scheme.left >= card.right + MARGIN - 0.5.dp)
    }

    // ---- the passport

    /** Three stamps of 96 in a row (spec 3.36.7): Cremona, Milan and Salzburg side by side, Vienna under them; lying too. */
    @Test
    fun theStampsStandThreeInARowUprightAndLying() {
        showPassport()
        for ((box, window) in listOf(DpSize(412.dp, 800.dp) to DpSize(412.dp, 800.dp), DpSize(892.dp, 360.dp) to DpSize(892.dp, 412.dp))) {
            inWindow(box, window)
            val where = "${box.width} × ${box.height}"
            val tops = (1..3).map { said(word(STAMP + it)).bounds().top.value }
            assertEquals("$where: Cremona and Milan in one row", tops[0], tops[1], 0.5f)
            assertEquals("$where: Milan and Salzburg in one row", tops[1], tops[2], 0.5f)
            assertTrue("$where: Vienna in the next one", said(word(STAMP + 4)).bounds().top.value > tops[0] + 0.5f)
            val stamp = compose.onNode(hasContentDescription(word(STAMP + 1)), useUnmergedTree = true).bounds()
            assertEquals("$where: a stamp of 96", STAMP_SIZE.value, stamp.width.value, 0.5f)
        }
    }

    /** The stop next on the road says so under its name — «Прага — ещё впереди, следующая» —, the ones after it only that they are ahead. */
    @Test
    fun theNextStopSaysItIsNext() {
        showPassport()
        said(word(PRAGUE_NEXT)).assertExists()
        said(word(LEIPZIG_AHEAD)).assertExists()
    }

    /**
     * How wide [layout] is on one line in its own style at [size], in px. The style of a layout read from the semantics is that of the
     * node — the asked size, whatever size its `autoSize` found (the lesson of stage 117) — and it is the style measured here.
     */
    private fun widthAt(layout: TextLayoutResult, size: TextUnit): Float = compose.runOnIdle {
        val input = layout.layoutInput
        TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
            .measure(input.text, input.style.copy(fontSize = size), softWrap = false, maxLines = 1)
            .multiParagraph.maxIntrinsicWidth
    }

    /** The size [layout] is drawn at, from its width: the step of 0.5 sp from [from] down to [least] at which it is as wide on one line. */
    private fun drawnSize(what: String, layout: TextLayoutResult, from: TextUnit, least: TextUnit): TextUnit {
        val drawn = layout.multiParagraph.maxIntrinsicWidth
        val steps = generateSequence(from.value) { it - SIZE_STEP }.takeWhile { it >= least.value }.map { it.sp }
        return steps.firstOrNull { abs(widthAt(layout, it) - drawn) < WIDTH_SLACK_PX }
            ?: run { fail("$what: $drawn px wide, at no size from $from to $least"); from }
    }

    /**
     * The caption under a stamp ahead stays on one line (5.29 R7, «Паспорт»): «Санкт-Петербург» does not fit a column of 105 on 360 at
     * 12 sp — some 111 dp at the font 1 — and steps down rather than go on to a second line or break; at 1.3 it reaches the least,
     * 10 sp, and only then is it cut with an ellipsis, on one line still.
     */
    @Test
    fun aCaptionOfThePassportStaysOnOneLineSmaller() {
        speaking("ru")
        windowSize = DpSize(360.dp, 640.dp)
        showPassport()
        for (font in listOf(1f, LARGE_FONT)) {
            fontScale = font
            compose.waitForIdle()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(SPB - 1)
            compose.waitForIdle()
            val where = "ru 360 at $font"
            val caption = compose.onNodeWithText(word(SPB_CITY), useUnmergedTree = true)
            val layout = caption.textLayout()
            assertEquals("$where: «${word(SPB_CITY)}» on one line", 1, layout.lineCount)
            val size = drawnSize(where, layout, CAPTION_SIZE, CAPTION_LEAST)
            assertTrue("$where: smaller than $CAPTION_SIZE: $size", size < CAPTION_SIZE)
            if (font == 1f) assertWholeOnOneLine(caption, "$where: ${word(SPB_CITY)}")
        }
    }

    /**
     * The sticker of a souvenir stands out of its stamp, as it always did (spec 3.36.7): on 360 a column of 105 holds the stamp of 96
     * with 4.7 at its sides, and the corner of the sticker, turned by 10°, reaches some 3.4 dp past the cell — the rounded cut of the
     * cell, there for its ripple, cut it off (the review of stage 118). Its paper (F1EEE6, light on the dark ground) is seen right of
     * the cell of Cremona, in the gap between the columns.
     */
    @Test
    fun theStickerOfASouvenirStandsOutOfItsCellUncut() {
        windowSize = DpSize(360.dp, 640.dp)
        journey = JourneyReducer.stateOf(progressAt(VIENNA, PURSE, setOf(BoughtExtra(CREMONA_ID, JourneyExtra.SOUVENIR))), null, JourneyConfig())
        showPassport()
        val cell = said(word(STAMP + 1)).bounds()
        val box = window()
        val image = compose.onNodeWithTag(TEST_WINDOW).captureToImage().toPixelMap()
        val px = { dp: Dp -> with(compose.density) { dp.toPx() }.toInt() }
        val xs = px(cell.right - box.left + STRIP_FROM)..px(cell.right - box.left + STRIP_TO)
        val ys = px(cell.top - box.top + STICKER_TOP)..px(cell.top - box.top + STICKER_BOTTOM)
        val paper = xs.sumOf { x -> ys.count { y -> image[x, y].let { it.red > PAPER && it.green > PAPER && it.blue > PAPER_BLUE } } }
        assertTrue("the paper of the sticker past the cell at $cell: $paper px", paper > 0)
    }

    private companion object {
        val VIENNA = JourneyRoute.indexOf("vienna")
        val PRAGUE = JourneyRoute.indexOf("prague")
        val CREMONA = JourneyRoute.indexOf("cremona")
        val SYDNEY = JourneyRoute.stops.lastIndex
        val TOTAL = JourneyRoute.stops.size - 1

        /** The purse of the mockups; a purse short of the second view and of Prague; the purse of home of the mockups. */
        const val PURSE = 47_884L
        const val SMALL_PURSE = 300L
        const val HOME_PURSE = 340L
        val PRAGUE_PRICE = JourneyRoute.stops[PRAGUE].price.toLong()
        val TIME_PRICE = JourneyConfig().secondTimePrice.toLong()
        val VIEW_PRICE = JourneyConfig().secondViewPrice.toLong()
        val SOUVENIR_PRICE = JourneyConfig().souvenirPrice.toLong()

        val BOTH_VIEWS = setOf(JourneyExtra.SECOND_TIME, JourneyExtra.SECOND_VIEW)
        val HOME_LOOK = HomeState(loaded = true, purchased = emptySet(), houses = emptySet(), choices = emptyMap())

        /** Home on 5 September, Cremona on the 7th, Milan on the 12th, Salzburg on the 18th, Vienna on the 20th… */
        private val DAYS = listOf(5, 7, 12, 18, 20, 22, 23, 24, 25, 26, 27, 28, 28, 29, 29, 30, 30)

        fun dayMs(index: Int): Long = LocalDate(2026, 9, DAYS[index]).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        fun progressAt(index: Int, balance: Long, extras: Set<BoughtExtra> = emptySet()): JourneyProgress {
            val spent = JourneyRoute.stops.take(index + 1).sumOf { it.price.toLong() }
            return JourneyProgress(earned = spent + balance, spent = spent, arrivals = (0..index).map { Arrival(JourneyRoute.stops[it].id, dayMs(it)) }, extras = extras)
        }

        /** The journey stood at the stop [index] with [balance] in the purse: every leg to it paid. */
        fun stateAt(index: Int, balance: Long): JourneyState = JourneyReducer.stateOf(progressAt(index, balance), null, JourneyConfig())

        /** The stop [index] as [StopViewModel] reads it, with [balance] in the purse and the extras [bought]. */
        fun stopAt(index: Int, balance: Long, bought: Set<JourneyExtra> = emptySet()): StopState {
            val stop = JourneyRoute.stops[index]
            val config = JourneyConfig()
            val progress = progressAt(index, balance, bought.map { BoughtExtra(stop.id, it) }.toSet())
            return StopState(
                loading = false,
                stop = stop,
                index = index,
                totalStops = TOTAL,
                arrivedAtEpochMs = dayMs(index),
                balance = balance,
                offers = JourneyRules.offers(stop, progress).map { extra ->
                    val owned = extra in bought
                    ExtraOffer(extra, JourneyRules.priceOf(extra, config), owned, affordable = !owned && JourneyRules.canBuy(stop, extra, progress, config))
                },
                day = false,
                inside = stop.views.firstOrNull()?.inside ?: false,
                dayUnlocked = JourneyExtra.SECOND_TIME in bought,
                secondViewUnlocked = JourneyExtra.SECOND_VIEW in bought,
            )
        }

        const val PLAY = "play"
        const val PLAY_VIENNA = "playVienna"
        const val PLAY_CREMONA = "playCremona"
        const val HOME = "home"
        const val POSTCARD = "postcard"
        const val WHOLE = "whole"
        const val EVENING = "evening"
        const val OUTSIDE = "outside"
        const val INSIDE = "inside"
        const val PLACE_LINE = "placeLine"
        const val EXTRAS = "extras"
        const val TIME_TO_BUY = "timeToBuy"
        const val TIME_OPEN = "timeOpen"
        const val VIEW_SHORT = "viewShort"
        const val VIEW_OUTSIDE = "viewOutside"
        const val SOUVENIR_TO_BUY = "souvenirToBuy"
        const val HERE_ENOUGH = "hereEnough"
        const val HERE_SHORT = "hereShort"
        const val HERE_HOME = "hereHome"
        const val HERE_END = "hereEnd"
        const val MAP = "map"
        const val STAMP = "stamp"
        const val PRAGUE_NEXT = "pragueNext"
        const val LEIPZIG_AHEAD = "leipzigAhead"
        const val EXTRAS_HINT = "extrasHint"
        const val TIME_TITLE = "timeTitle"
        const val TIME_NOTE = "timeNote"
        const val VIEW_TITLE = "viewTitle"
        const val VIEW_NOTE = "viewNote"
        const val SOUVENIR_TITLE = "souvenirTitle"
        const val SOUVENIR_NOTE = "souvenirNote"
        const val OPENED = "opened"
        const val SPB_CITY = "spbCity"

        /** A phone of 360 × 640 under its bars (24 and a gesture bar of 24). */
        val PHONE = DpSize(360.dp, 592.dp)

        /**
         * The windows of the stop and the window each is told it is in: upright 412 × 800 and a phone of 360 × 640; 892 × 412 lying is
         * some 360 high under its bars; the emulator's 640 × 360 lying is 603 × 308 under its bars and cutout, 603 × 336 with a thin bar.
         */
        val WINDOWS = listOf(
            DpSize(412.dp, 800.dp) to DpSize(412.dp, 800.dp),
            PHONE to DpSize(360.dp, 640.dp),
            DpSize(892.dp, 360.dp) to DpSize(892.dp, 412.dp),
            DpSize(603.dp, 308.dp) to DpSize(603.dp, 308.dp),
            DpSize(603.dp, 336.dp) to DpSize(603.dp, 336.dp),
        )

        /** The sides of the screen, the left column lying, «Домой» lying at the least (D5), the postcard lying at the most (5.29 R7). */
        val SIDE = 16.dp
        val LEFT_COLUMN = 360.dp
        val HOME_LYING_MIN = 120.dp
        val POSTCARD_LYING = 180.dp

        /** What lies on a picture stands 8 from its edges; a plate's 48 stands 4 over and under its capsule, its words in a field of 3. */
        val PICTURE_INSET = 8.dp
        val PLATE_AIR = 4.dp
        val PLATE_FIELD = 3.dp

        /** What the plates leave at the end of their row: the gap to the square, the square, its corner. */
        val PLATES_RESERVE = 8.dp + 48.dp + 8.dp

        /** A touch on the words of a row, from its start; a touch on its pill, from its end (the pill ends 8 from it). */
        val WORDS_X = 30.dp
        val PILL_X = 18.dp

        /** The card «вы здесь»: 16 from the edges; 360 wide lying. */
        val MARGIN = 16.dp
        val HERE_LYING = 360.dp

        val STAMP_SIZE = 96.dp
        const val LARGE_FONT = 1.3f

        /** A row of the extras at the font 1 (5.29 R7). */
        val ROW_HEIGHT = 60.dp

        /** The caption under a stamp and its least (5.29 R7); the steps a one-line text takes down, and how near widths count as one. */
        val CAPTION_SIZE = 12.sp
        val CAPTION_LEAST = 10.sp
        const val SIZE_STEP = 0.5f
        const val WIDTH_SLACK_PX = 0.5f

        val SPB = JourneyRoute.indexOf("spb")
        const val CREMONA_ID = "cremona"

        /**
         * Where the paper of the sticker of Cremona's stamp is looked for, from the end and the top of its cell on 360: past the cell by
         * half a dp to 2.5 (it reaches 3.4 there), from 76 to 88 under its top (the corner turned out of the cell stands at about 78).
         */
        val STRIP_FROM = 0.5.dp
        val STRIP_TO = 2.5.dp
        val STICKER_TOP = 76.dp
        val STICKER_BOTTOM = 88.dp

        /** The paper of the sticker, F1EEE6 — no ink of a stamp is that light in all three. */
        const val PAPER = 0.8f
        const val PAPER_BLUE = 0.75f
    }
}
