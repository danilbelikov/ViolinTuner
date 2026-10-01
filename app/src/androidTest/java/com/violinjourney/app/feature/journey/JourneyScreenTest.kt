package com.violinjourney.app.feature.journey

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockMetrics
import com.violinjourney.app.core.ui.components.WordsAndNumber
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.journey_card_description
import com.violinjourney.app.shared.resources.journey_depart
import com.violinjourney.app.shared.resources.journey_depart_spend
import com.violinjourney.app.shared.resources.journey_done
import com.violinjourney.app.shared.resources.journey_enter_home
import com.violinjourney.app.shared.resources.journey_enter_home_at
import com.violinjourney.app.shared.resources.journey_have
import com.violinjourney.app.shared.resources.journey_have_description
import com.violinjourney.app.shared.resources.journey_intro_start
import com.violinjourney.app.shared.resources.journey_intro_title
import com.violinjourney.app.shared.resources.journey_missing
import com.violinjourney.app.shared.resources.journey_next
import com.violinjourney.app.shared.resources.journey_no_cards
import com.violinjourney.app.shared.resources.journey_passed
import com.violinjourney.app.shared.resources.journey_stamp
import com.violinjourney.app.shared.resources.journey_stamp_description
import com.violinjourney.app.shared.resources.journey_stamp_last
import com.violinjourney.app.shared.resources.journey_stamp_leg
import com.violinjourney.app.shared.resources.journey_stamp_number
import com.violinjourney.app.shared.resources.journey_stop_of
import com.violinjourney.app.shared.resources.journey_title
import com.violinjourney.app.shared.resources.journey_tour_done
import com.violinjourney.app.shared.resources.journey_tour_more
import com.violinjourney.app.shared.resources.venue_play_here
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
 * The journey of R7 (spec 3.36.7, 5.29 R7) by what an eye, a finger and a reader meet: the bottom zone holds the card of the path and
 * «В путь · Прага» that says what it spends, or the plate of what is missing — words, not a sleeping button — or «Мировое турне
 * пройдено»; it stays put while the postcard, the city and the ribbon scroll, and nothing ends under it; lying down it is the left
 * column of 360 and only the ribbon is on the right; in a window no higher than 360 its button is 48 and the card of the path stays;
 * the bar has no purse, 56 upright and 48 lying; the ribbon says «город, день» and its oldest leads home; the intro, the arrival and
 * the page of the stamp keep their buttons in the zone even in 603 × 308 (640 × 360 on the emulator), and their words stand over
 * the fade of the zone in every window — lying beside their picture; a double tap across a moment spends nothing and skips no page;
 * at the font 1.3 on 360 the numbers of «спишется …» stand whole in every language and the words around them give way where they
 * must; the city steps down rather than break a word, and the line of the path breaks none.
 *
 * The screen is laid out in a window of its own size, whatever the device's ([WINDOW], and a fake [LocalWindowInfo] of that size —
 * the zone reads it — or of the size of the whole window, where the box is what its bars leave: [windowInfo]), without the widening
 * of touch targets under 48, the pictures standing still. The box is the room the root of the app leaves to a screen under the
 * system bars, and like the root it takes them away (MainActivity: `padding(innerPadding).consumeWindowInsets(innerPadding)`): the
 * zone pads no bar of the device the test runs on on top of it. The words are read in the composition, in the language of the
 * process ([speaking]).
 */
@RunWith(AndroidJUnit4::class)
class JourneyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<JourneyIntent>()
    private val words = mutableMapOf<String, String>()
    private var state by mutableStateOf(stateAt(VIENNA, PURSE))
    private var windowSize by mutableStateOf(DpSize(412.dp, 800.dp))

    /**
     * The window the screen is told it is in ([LocalWindowInfo]: the zone reads its height), where it is not the box it is laid out in:
     * 892 × 412 lying is some 360 high under its bars, and its zone is still that of a window of 412 (5.29, «Нижняя зона»).
     */
    private var windowInfo by mutableStateOf<DpSize?>(null)
    private var fontScale by mutableFloatStateOf(1f)

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val base = LocalViewConfiguration.current
        val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = androidx.compose.ui.unit.DpSize.Zero } }
        val size = windowSize
        val told = windowInfo ?: size
        val info = with(density) { Window(IntSize(told.width.roundToPx(), told.height.roundToPx())) }
        CompositionLocalProvider(
            LocalWindowInfo provides info,
            LocalViewConfiguration provides noWidening,
            LocalDensity provides Density(density.density, fontScale),
            // the pictures stand still: a living one would ask for frames all the time
            LocalReduceMotion provides true,
            LocalHomeLook provides HOME_LOOK,
        ) {
            // the room under the bars, the bars consumed as the root of the app consumes them (safeDrawing less the keyboard): else
            // the zone would pad the gesture bar of the emulator (24) inside a box that already stands for what the bars leave, and
            // 603 × 308 — the emulator's 640 × 360 lying — would be 603 × 284 (stage 117)
            Box(
                Modifier
                    .requiredSize(size.width, size.height)
                    .consumeWindowInsets(WindowInsets.safeDrawing.exclude(WindowInsets.ime))
                    .testTag(WINDOW),
            ) { content() }
        }
    }

    private fun show() {
        compose.setContent {
            ReadWords()
            ViolinTheme { InWindow { JourneyScreen(state, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
    }

    /** The words of the screens of the tests, read where the screen reads them. */
    @Composable
    private fun ReadWords() {
        val separator = stringResource(Res.string.dot_separator)
        @Composable
        fun path(next: Int, amount: String) =
            stringResource(Res.string.journey_next, cityToOf(next), roadOf(next)).replace(separator, ", ") + ", " + amount
        words[TITLE] = stringResource(Res.string.journey_title)
        words[DEPART_PRAGUE] = stringResource(Res.string.journey_depart, cityOf(PRAGUE))
        words[SPEND_PRAGUE] = stringResource(Res.string.journey_depart_spend, Formats.takts(PRAGUE_PRICE), Formats.takts(PURSE))
        words[PATH_ENOUGH] = path(PRAGUE, taktsInWords(PRAGUE_PRICE))
        words[PATH_SHORT] = path(PRAGUE, stringResource(Res.string.journey_have_description, Formats.takts(SHORT_PURSE), taktsInWords(PRAGUE_PRICE)))
        words[HAVE_SHORT] = stringResource(Res.string.journey_have, Formats.takts(SHORT_PURSE), Formats.takts(PRAGUE_PRICE))
        words[PLATE_SAID] = stringResource(Res.string.journey_missing, taktsInWords(PRAGUE_PRICE - SHORT_PURSE)) + ", " + sessionsInWords(4)
        words[TOUR_SAID] = listOf(
            stringResource(Res.string.journey_tour_done), taktsInWords(TOUR_PURSE), stringResource(Res.string.journey_tour_more),
        ).joinToString(", ")
        words[DEPART_ANY] = stringResource(Res.string.journey_depart, "").trimEnd()
        words[POSTCARD_VIENNA] = stringResource(Res.string.journey_card_description, cityOf(VIENNA)) + ", " +
            stringResource(Res.string.journey_stop_of, VIENNA, JourneyRoute.stops.size - 1)
        words[DOOR_HOME] = stringResource(Res.string.journey_enter_home) + ", " + stringResource(Res.string.journey_enter_home_at)
        words[NO_CARDS] = stringResource(Res.string.journey_no_cards)
        words[PASSED_VIENNA] = stringResource(Res.string.journey_passed, VIENNA, JourneyRoute.stops.size - 1)
        words[DEPART_CREMONA] = stringResource(Res.string.journey_depart, cityOf(1))
        words[SPEND_CREMONA] = stringResource(Res.string.journey_depart_spend, Formats.takts(JourneyRoute.stops[1].price.toLong()), Formats.takts(HOME_PURSE))
        (0..VIENNA).forEach { index -> words[STRIP + index] = cityOf(index) + ", " + Formats.dayAndMonth(dayMs(index), TimeZone.currentSystemDefault()) }
        words[INTRO_START] = stringResource(Res.string.journey_intro_start)
        words[STAMP] = stringResource(Res.string.journey_stamp)
        words[DONE] = stringResource(Res.string.journey_done)
        words[PLAY_HERE] = stringResource(Res.string.venue_play_here)
        words[STAMP_NUMBER] = stringResource(Res.string.journey_stamp_number, PRAGUE)
        words[STAMP_LEG] = stringResource(Res.string.journey_stamp_leg, cityToOf(PRAGUE + 1), taktsInWords(JourneyRoute.stops[PRAGUE + 1].price.toLong()))
        words[STAMP_LAST] = stringResource(Res.string.journey_stamp_last)
        words[DEPART_SPB] = stringResource(Res.string.journey_depart, cityOf(SPB))
        words[SPEND_SPB] = stringResource(Res.string.journey_depart_spend, Formats.takts(SPB_PRICE), Formats.takts(BIG_PURSE))
        words[CITY_SPB] = cityOf(SPB)
        words[PATH_LINE_SPB] = stringResource(Res.string.journey_next, cityToOf(SPB), roadOf(SPB))
        words[HAVE_SPB] = stringResource(Res.string.journey_have, Formats.takts(LONDON_SHORT), Formats.takts(SPB_PRICE))
        words[CITY_PRAGUE] = cityOf(PRAGUE)
        words[PLACE_PRAGUE] = listOf(placeOf(PRAGUE), countryOf(PRAGUE)).filter { it.isNotEmpty() }.joinToString(separator)
        words[POSTCARD_PRAGUE] = stringResource(Res.string.journey_card_description, cityOf(PRAGUE))
        words[STAMP_PRAGUE] = stringResource(Res.string.journey_stamp_description, cityOf(PRAGUE))
        words[INTRO_TITLE] = stringResource(Res.string.journey_intro_title)
        words[HOME_POSTCARD] = cityOf(0)
        // the words of «не хватает …» whatever the number: what any plate says starts or ends with them
        words[MISSING_WORDS] = stringResource(Res.string.journey_missing, MARK).split(MARK).map { it.trim() }.maxBy { it.length }
    }

    private fun word(key: String): String = words.getValue(key)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun window(): DpRect = compose.onNodeWithTag(WINDOW).bounds()

    /** The button of [text] — its capsule, which is what is pressed: the widening of touch targets is off. */
    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    /** A card by the one phrase a reader hears of it. */
    private fun said(description: String) = compose.onNodeWithContentDescription(description)

    private fun assertInside(what: String, inner: DpRect, outer: DpRect) {
        assertTrue(
            "$what at $inner stands inside $outer",
            inner.left >= outer.left - 0.5.dp && inner.right <= outer.right + 0.5.dp && inner.top >= outer.top - 0.5.dp && inner.bottom <= outer.bottom + 0.5.dp,
        )
    }

    // ---- the zone of the calm screen

    @Test
    fun enoughTheZoneHoldsThePathAndTheButtonThatSaysWhatItSpends() {
        show()
        val box = window()
        val card = said(word(PATH_ENOUGH)).bounds()
        val depart = button(word(DEPART_PRAGUE))
        val key = depart.bounds()
        // the button says what it spends and what there was: «спишется 1 600 из 47 884» is in its words
        val spoken = depart.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf(word(DEPART_PRAGUE), word(SPEND_PRAGUE)), spoken)
        assertEquals("a button", Role.Button, depart.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assertInside("«В путь»", key, box)
        assertTrue("the card of the path over the button: $card, $key", card.bottom <= key.top)
        assertEquals("10 between the card and the button", 10f, (key.top - card.bottom).value, 1f)
        assertEquals("the main button of the zone is 56", 56f, key.height.value, 0.5f)
        // no purse in the bar and nowhere on its own: it is in the words of the button only
        compose.onAllNodesWithText(Formats.takts(PURSE)).assertCountEquals(0)
        depart.performClick()
        assertEquals(listOf<JourneyIntent>(JourneyIntent.DepartClicked), intents)
    }

    @Test
    fun shortThePlateTellsWhatIsMissingAndIsNotAButton() {
        state = stateAt(VIENNA, SHORT_PURSE)
        show()
        val box = window()
        val plate = said(word(PLATE_SAID))
        val node = plate.fetchSemanticsNode()
        assertEquals("no role", null, node.config.getOrNull(SemanticsProperties.Role))
        assertFalse("no touch", SemanticsActions.OnClick in node.config)
        assertFalse("never «disabled»", SemanticsProperties.Disabled in node.config)
        val bounds = plate.bounds()
        assertInside("the plate", bounds, box)
        assertTrue("as high as the button it stands for: ${bounds.height}", bounds.height >= 56.dp - 0.5.dp)
        // the card says «472 из 1 600 тактов»; there is no «В путь»
        val card = said(word(PATH_SHORT)).bounds()
        assertTrue("the card over the plate", card.bottom <= bounds.top)
        compose.onAllNodes(hasText(word(DEPART_ANY), substring = true)).assertCountEquals(0)
    }

    @Test
    fun atTheEndOfTheRouteTheCardSaysTheTourIsDoneWithThePurseAndThereIsNoButton() {
        state = stateAt(SYDNEY, TOUR_PURSE)
        show()
        assertInside("«Мировое турне пройдено»", said(word(TOUR_SAID)).bounds(), window())
        compose.onAllNodes(hasText(word(DEPART_ANY), substring = true)).assertCountEquals(0)
        // no plate, whatever it would say — «не хватает 0 тактов» after Sydney as much as any other
        compose.onAllNodes(hasContentDescription(word(MISSING_WORDS), substring = true)).assertCountEquals(0)
    }

    @Test
    fun theZoneStaysPutWhileTheWordsScrollAndNothingEndsUnderIt() {
        windowSize = DpSize(360.dp, 640.dp)
        show()
        val before = button(word(DEPART_PRAGUE)).bounds()
        val card = said(word(PATH_ENOUGH)).bounds()
        // to the very end of what scrolls: the ribbon comes up over the zone, not under it
        scrollToTheEnd()
        assertEquals("the button did not move", before, button(word(DEPART_PRAGUE)).bounds())
        val salzburg = said(word(STRIP + 3)).bounds()
        // the zone starts its top field (8 in a window of 640) over the card; what scrolls ends 24 over the zone (plan 2.4:
        // LocalDockInset + 24) — out of the fade, not in the zone
        val end = card.top - DockMetrics.of(640.dp).top - SCROLL_END
        assertEquals("the ribbon at the end ends $SCROLL_END over the zone: $salzburg, the card at $card", end.value, salzburg.bottom.value, 1f)
    }

    @Test
    fun uprightTheBarIs56WithoutAPurseAndItsTitleIsAHeading() {
        show()
        val box = window()
        val postcard = said(word(POSTCARD_VIENNA)).bounds()
        assertEquals("the bar of 56 and 4 over the postcard", 60f, (postcard.top - box.top).value, 0.5f)
        compose.onNode(hasText(word(TITLE)) and isHeading()).assertExists()
        assertEquals("the postcard is 240 on a phone", 240f, postcard.height.value, 0.5f)
    }

    @Test
    fun lyingDownTheZoneIsTheLeftColumnAndOnlyTheRibbonIsOnTheRight() {
        windowSize = DpSize(892.dp, 412.dp)
        show()
        val box = window()
        val column = DpRect(box.left + 16.dp, box.top, box.left + 16.dp + 360.dp, box.bottom)
        val key = button(word(DEPART_PRAGUE)).bounds()
        val card = said(word(PATH_ENOUGH)).bounds()
        assertInside("«В путь» in the left column", key, column)
        assertInside("the card of the path in the left column", card, column)
        assertEquals("the zone as wide as the column", 360f, key.width.value, 0.5f)
        val postcard = said(word(POSTCARD_VIENNA)).bounds()
        assertEquals("the bar of 48 lying, 4 over the postcard", 52f, (postcard.top - box.top).value, 0.5f)
        assertEquals("the postcard of 180 lying", 180f, postcard.height.value, 0.5f)
        val salzburg = said(word(STRIP + 3)).bounds()
        assertTrue("the ribbon on the right of the column: $salzburg", salzburg.left >= column.right + 16.dp - 0.5.dp)
    }

    @Test
    fun inALowWindowTheButtonIs48AndTheZoneKeepsTheCardOfThePath() {
        show()
        // 640 × 360 lying on the emulator — 603 × 308 under its bars — and on a phone with a thin status bar; 640 × 360 without a cutout
        for (size in listOf(DpSize(603.dp, 308.dp), DpSize(603.dp, 336.dp), DpSize(640.dp, 360.dp))) {
            windowSize = size
            compose.waitForIdle()
            val box = window()
            val key = button(word(DEPART_PRAGUE)).bounds()
            val card = said(word(PATH_ENOUGH)).bounds()
            assertEquals("${size.width} × ${size.height}: the button of 48", 48f, key.height.value, 0.5f)
            assertInside("${size.width} × ${size.height}: «В путь»", key, box)
            assertInside("${size.width} × ${size.height}: the card of the path", card, box)
            assertTrue("${size.width} × ${size.height}: the card over the button", card.bottom <= key.top)
        }
    }

    // ---- the ribbon, home

    /** To the very end of what scrolls over the zone: the ribbon stands over it, where a finger reaches it. */
    private fun scrollToTheEnd() {
        compose.onNode(hasScrollAction() and !hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        compose.waitForIdle()
    }

    @Test
    fun theRibbonSaysCityAndDayAndItsOldestLeadsHome() {
        show()
        compose.onNode(hasText(word(PASSED_VIENNA)) and isHeading()).assertExists()
        scrollToTheEnd()
        said(word(STRIP + 3)).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(VIENNA - 1)
        said(word(STRIP + 0)).performClick()
        assertEquals(listOf(JourneyIntent.StopClicked("salzburg"), JourneyIntent.StopClicked(JourneyRoute.HOME)), intents)
    }

    @Test
    fun atHomeTheDoorSaysYouAreHomeAndTheButtonGoesToCremona() {
        state = stateAt(0, HOME_PURSE)
        show()
        said(word(DOOR_HOME)).performClick()
        compose.onNodeWithText(word(NO_CARDS)).assertExists()
        val depart = button(word(DEPART_CREMONA))
        assertEquals(listOf(word(DEPART_CREMONA), word(SPEND_CREMONA)), depart.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
        assertEquals(listOf<JourneyIntent>(JourneyIntent.StopClicked(JourneyRoute.HOME)), intents)
    }

    // ---- the moments of the road

    @Test
    fun theIntroTheArrivalAndTheStampKeepTheirButtonsInTheZoneOfALowWindow() {
        val intro = JourneyReducer.stateOf(JourneyProgress(earned = 180, spent = 0, arrivals = emptyList(), extras = emptySet()), null, JourneyConfig())
        state = intro
        show()
        for (size in listOf(DpSize(412.dp, 800.dp), DpSize(603.dp, 308.dp))) {
            windowSize = size
            state = intro
            compose.waitForIdle()
            intents.clear()
            val where = "${size.width} × ${size.height}"
            assertInside("$where: «Собрать футляр»", button(word(INTRO_START)).bounds(), window())
            button(word(INTRO_START)).performClick()
            state = stateAt(PRAGUE, PURSE - PRAGUE_PRICE, JourneyPhase.Arrival(JourneyRoute.stops[PRAGUE], PRAGUE))
            compose.waitForIdle()
            assertInside("$where: «Поставить штамп»", button(word(STAMP)).bounds(), window())
            button(word(STAMP)).performClick()
            state = stateAt(PRAGUE, PURSE - PRAGUE_PRICE, JourneyPhase.Stamp(JourneyRoute.stops[PRAGUE], PRAGUE))
            compose.waitForIdle()
            compose.onNodeWithText(word(STAMP_NUMBER)).assertExists()
            compose.onNodeWithText(word(STAMP_LEG)).assertExists()
            val done = button(word(DONE)).bounds()
            val play = button(word(PLAY_HERE)).bounds()
            assertInside("$where: «Готово»", done, window())
            assertInside("$where: «Играть здесь»", play, window())
            assertTrue("$where: «Играть здесь» under «Готово»", play.top >= done.bottom)
            button(word(DONE)).performClick()
            button(word(PLAY_HERE)).performClick()
            assertEquals(
                where,
                listOf(JourneyIntent.IntroConfirmed, JourneyIntent.StampClicked, JourneyIntent.StampDone, JourneyIntent.PlayHereClicked),
                intents,
            )
        }
    }

    @Test
    fun theStampOfSydneySaysTheRoadIsStillBeingDrawn() {
        state = stateAt(SYDNEY, TOUR_PURSE, JourneyPhase.Stamp(JourneyRoute.stops[SYDNEY], SYDNEY))
        show()
        compose.onNodeWithText(word(STAMP_LAST)).assertExists()
    }

    // ---- a double tap across a moment of the road

    private fun center(rect: DpRect): DpOffset = DpOffset((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)

    private fun DpRect.holds(point: DpOffset): Boolean = point.x in left..right && point.y in top..bottom

    /** A finger down and up again at [point] of the root — where it was a moment ago, whatever stands there now. */
    private fun tapAt(point: DpOffset) {
        val box = window()
        compose.onNodeWithTag(WINDOW).performTouchInput { click(Offset((point.x - box.left).toPx(), (point.y - box.top).toPx())) }
    }

    /**
     * A double tap on «Собрать футляр» (spec 5.29 R7, as a face of a sheet holds its main button, R3): the case packed, the calm screen
     * takes the place of the intro and «В путь · Кремона» stands where «Собрать футляр» stood — the second tap, a frame or two later,
     * spends nothing unseen (the spec's own purse: 340, «спишется 300 из 340»). Once the time of a double tap has passed, «В путь»
     * answers. The clock stands between the two taps, as the frames of a phone would go: the incoming moment takes touches from its
     * first frame, whatever its alpha.
     */
    @Test
    fun aSecondTapOfPackTheCaseSpendsNothing() {
        state = JourneyReducer.stateOf(JourneyProgress(earned = HOME_PURSE, spent = 0, arrivals = emptyList(), extras = emptySet()), null, JourneyConfig())
        show()
        val finger = center(button(word(INTRO_START)).bounds())
        compose.mainClock.autoAdvance = false
        tapAt(finger)
        // the model's answer: the case packed at home, enough for Cremona
        state = stateAt(0, HOME_PURSE)
        repeat(TAP_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val depart = button(word(DEPART_CREMONA)).bounds()
        assertTrue("«В путь» is under the finger that pressed «Собрать футляр»: $finger in $depart", depart.holds(finger))
        tapAt(finger)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("the second tap spent nothing", listOf<JourneyIntent>(JourneyIntent.IntroConfirmed), intents)
        tapAt(finger)
        compose.waitForIdle()
        assertEquals(listOf(JourneyIntent.IntroConfirmed, JourneyIntent.DepartClicked), intents)
    }

    /**
     * A double tap on «Поставить штамп»: the page of the stamp takes the place of the arrival at once, and its quiet «Играть здесь»
     * stands where «Поставить штамп» stood — the second tap does not skip the page to Live unseen. After the time of a double tap it
     * answers.
     */
    @Test
    fun aSecondTapOfPutTheStampDoesNotSkipThePage() {
        state = stateAt(PRAGUE, PURSE - PRAGUE_PRICE, JourneyPhase.Arrival(JourneyRoute.stops[PRAGUE], PRAGUE))
        show()
        val finger = center(button(word(STAMP)).bounds())
        compose.mainClock.autoAdvance = false
        tapAt(finger)
        state = stateAt(PRAGUE, PURSE - PRAGUE_PRICE, JourneyPhase.Stamp(JourneyRoute.stops[PRAGUE], PRAGUE))
        repeat(TAP_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val play = button(word(PLAY_HERE)).bounds()
        assertTrue("«Играть здесь» is under the finger that pressed «Поставить штамп»: $finger in $play", play.holds(finger))
        tapAt(finger)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("the second tap did not skip the page", listOf<JourneyIntent>(JourneyIntent.StampClicked), intents)
        tapAt(finger)
        compose.waitForIdle()
        assertEquals(listOf(JourneyIntent.StampClicked, JourneyIntent.PlayHereClicked), intents)
    }

    // ---- the moments over their zone

    /** Where the fade of the bottom zone starts: its first [button] less the top field of the zone of a window [told] high and the fade. */
    private fun fadeTop(button: DpRect, told: DpSize): Dp = button.top - DockMetrics.of(told.height).top - DockDefaults.Fade

    /** [rect] ends over [fade]: a picture that gives way to the room stands right at it, give or take the pixels of its fields. */
    private fun assertOver(where: String, what: String, rect: DpRect, fade: Dp) =
        assertTrue("$where: $what at $rect ends over the fade of the zone at $fade", rect.bottom <= fade + 1.dp)

    /** Lays the screen out in [box], told the window is [told]. */
    private fun inWindow(box: DpSize, told: DpSize) {
        windowSize = box
        windowInfo = told
        compose.waitForIdle()
    }

    /**
     * The page of the stamp stands over the fade of its zone in every window (spec 3.36.7, 5.29 R7): the stamp, «Штамп № 5» and «До
     * Лейпцига — 2 000 тактов» never wait under the zone. Lying the words stand beside the frame and the frame gives way to the room —
     * 892 × 412 is some 360 high under its bars and keeps the zone of 412; the emulator's 640 × 360 is 603 × 308 (the frame 148);
     * upright they stand under it, the frame 220. The bounds of the stamp are its own square placed at its corner as the turn of 8°
     * takes it (`getUnclippedBoundsInRoot`: the position of the node and its size — the turn moves the corner, not the size): 168
     * wide in a frame of 220, and their bottom a little lower than the lowest point of the turned stamp — over the fade, it is too.
     */
    @Test
    fun theWordsOfTheStampStandOverTheZoneInEveryWindow() {
        state = stateAt(PRAGUE, PURSE - PRAGUE_PRICE, JourneyPhase.Stamp(JourneyRoute.stops[PRAGUE], PRAGUE))
        show()
        for ((box, told) in PHASE_WINDOWS) {
            inWindow(box, told)
            val where = "${box.width} × ${box.height}"
            val fade = fadeTop(button(word(DONE)).bounds(), told)
            val stamp = said(word(STAMP_PRAGUE)).bounds()
            val number = compose.onNodeWithText(word(STAMP_NUMBER)).bounds()
            val leg = compose.onNodeWithText(word(STAMP_LEG)).bounds()
            assertOver(where, "the stamp", stamp, fade)
            assertOver(where, "«Штамп № 5»", number, fade)
            assertOver(where, "the leg", leg, fade)
            if (box.width > box.height) {
                assertTrue("$where: lying, «Штамп № 5» beside the stamp: $number, $stamp", number.left >= stamp.right)
            } else {
                assertTrue("$where: upright, «Штамп № 5» under the stamp: $number, $stamp", number.top >= stamp.bottom)
                // the frame of 220 and the stamp of 168 in it: the bounds are the stamp's own square, the turn does not widen them
                assertEquals("$where: upright, the stamp of 168", UPRIGHT_STAMP.value, stamp.width.value, 1f)
            }
        }
    }

    /**
     * The words of the arrival and of the intro stand over the fade of their zone in every window: the city of the arrival and its
     * «место · страна», the title of the intro. Lying they stand beside the postcard of 240 × 180 (the intro's gives way to what its
     * bar and its zone leave: 164 on 603 × 308); upright under it.
     */
    @Test
    fun theCityOfTheArrivalAndTheTitleOfTheIntroStandOverTheZoneInEveryWindow() {
        val arrival = stateAt(PRAGUE, PURSE - PRAGUE_PRICE, JourneyPhase.Arrival(JourneyRoute.stops[PRAGUE], PRAGUE))
        val intro = JourneyReducer.stateOf(JourneyProgress(earned = HOME_PURSE, spent = 0, arrivals = emptyList(), extras = emptySet()), null, JourneyConfig())
        state = arrival
        show()
        for ((box, told) in PHASE_WINDOWS) {
            val where = "${box.width} × ${box.height}"
            val lying = box.width > box.height
            state = arrival
            inWindow(box, told)
            val fade = fadeTop(button(word(STAMP)).bounds(), told)
            val postcard = said(word(POSTCARD_PRAGUE)).bounds()
            val city = compose.onNodeWithText(word(CITY_PRAGUE)).bounds()
            assertOver(where, "the postcard of the arrival", postcard, fade)
            assertOver(where, "the city", city, fade)
            assertOver(where, "«место · страна»", compose.onNodeWithText(word(PLACE_PRAGUE)).bounds(), fade)
            if (lying) {
                assertTrue("$where: lying, the city beside the postcard: $city, $postcard", city.left >= postcard.right)
                assertEquals("$where: lying, the postcard of 180", 180f, postcard.height.value, 0.5f)
            } else {
                assertTrue("$where: upright, the city under the postcard: $city, $postcard", city.top >= postcard.bottom)
            }
            state = intro
            compose.waitForIdle()
            val introFade = fadeTop(button(word(INTRO_START)).bounds(), told)
            val home = said(word(HOME_POSTCARD)).bounds()
            val title = compose.onNodeWithText(word(INTRO_TITLE)).bounds()
            assertOver(where, "the title of the intro", title, introFade)
            if (lying) {
                assertOver(where, "the postcard of the intro", home, introFade)
                assertTrue("$where: lying, the title beside the postcard: $title, $home", title.left >= home.right)
            } else {
                assertTrue("$where: upright, the title under the postcard: $title, $home", title.top >= home.bottom)
            }
        }
    }

    // ---- words at a large font

    /**
     * The numbers of [caption] stand whole: its part that holds them is drawn whole; the parts drawn side by side are no wider than the
     * line — a part laid out wider than its room (the whole caption kept, a side not cut) would be drawn past the line and past the
     * button, and the capsule would cut it without an ellipsis — and the line stands in [button]. [cut] — the words around the numbers
     * had to give way (de and fr at 1.3 on 360: «6 000 von 147 884 werden abgebucht» is some 280 dp at 12 sp, the room 260), each
     * with its ellipsis, not left out.
     */
    private fun assertNumbersWhole(what: String, caption: String, numbers: List<String>, button: DpRect, cut: Boolean) {
        val node = compose.onNodeWithText(caption, useUnmergedTree = true)
        val line = node.bounds()
        assertInside("$what: «$caption»", line, button)
        val parts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(parts)
        val drawn = parts.sumOf { it.size.width }
        val room = node.fetchSemanticsNode().size.width
        assertTrue("$what: the parts of «$caption» take $drawn px side by side, the line is $room px", drawn <= room)
        if (cut) {
            assertTrue(
                "$what: the words around the numbers give way with an ellipsis: ${parts.map { it.layoutInput.text }}",
                parts.any { it.isLineEllipsized(0) },
            )
        }
        numbers.forEach { number ->
            val kept = parts.single { number in it.layoutInput.text.text }
            assertFalse("$what: «$number» is not cut: «${kept.layoutInput.text}»", kept.isLineEllipsized(0))
            assertTrue(
                "$what: «$number» stands whole: it needs ${kept.multiParagraph.maxIntrinsicWidth} px, it has ${kept.size.width}",
                kept.multiParagraph.maxIntrinsicWidth <= kept.size.width + 0.5f,
            )
        }
    }

    private fun numbersWholeAtALargeFont(language: String) {
        speaking(language)
        windowSize = DpSize(360.dp, 640.dp)
        fontScale = 1.3f
        state = stateAt(LONDON, BIG_PURSE)
        show()
        val key = button(word(DEPART_SPB))
        val bounds = key.bounds()
        assertInside("$language: «В путь»", bounds, window())
        assertTrue("$language: the button of 56 at least: ${bounds.height}", bounds.height >= 56.dp - 0.5.dp)
        // the words go on one line, cut at their end if they must (the city), never on two
        val words = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(word(DEPART_SPB), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(words)
        assertEquals("$language: the words of «В путь» on one line", 1, words.single().lineCount)
        assertNumbersWhole(language, word(SPEND_SPB), listOf(Formats.takts(SPB_PRICE), Formats.takts(BIG_PURSE)), bounds, cut = language in CUT_AT_1_3)
    }

    @Test
    fun atTheFont1_3On360TheNumbersOfTheButtonStandWholeInRussian() = numbersWholeAtALargeFont("ru")

    @Test
    fun atTheFont1_3On360TheNumbersOfTheButtonStandWholeInGerman() = numbersWholeAtALargeFont("de")

    @Test
    fun atTheFont1_3On360TheNumbersOfTheButtonStandWholeInFrench() = numbersWholeAtALargeFont("fr")

    @Test
    fun atTheFont1_3On360TheNumbersOfTheButtonStandWholeInPortuguese() = numbersWholeAtALargeFont("pt")

    @Test
    fun atTheFont1_3On360TheNumbersOfTheButtonStandWholeInEnglish() = numbersWholeAtALargeFont("en")

    /** [text] goes on to a next line only at a space, and is not cut: no word of it breaks by the letter or at a hyphen. */
    private fun assertWordsWhole(what: String, text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("$what: «$text» breaks inside a word after «${text.substring(0, end)}»", text[end - 1].isWhitespace())
        }
        assertFalse("$what: «$text» is cut", layout.isLineEllipsized(layout.lineCount - 1))
    }

    /** The one layout of the name of the city on the calm screen. */
    private fun cityLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(word(CITY_SPB), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /**
     * How wide the name of [layout] is on one line in its own style at [size], in px. The style of a layout read from the semantics is
     * that of the node — the name's 26 sp, whatever size its `autoSize` found — and it is the style the name is measured in here.
     */
    private fun widthAt(layout: TextLayoutResult, size: TextUnit): Float = compose.runOnIdle {
        val input = layout.layoutInput
        TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
            .measure(input.text, input.style.copy(fontSize = size), softWrap = false, maxLines = 1)
            .multiParagraph.maxIntrinsicWidth
    }

    /**
     * The size the name of [layout] is drawn at. Not the font size of its style: the semantics of a text give its layout with the
     * style of the node (`TextAnnotatedStringNode`: `style = this.style`), so a name stepped down by its `autoSize` still says 26 sp.
     * Its width does tell it: the step of 0.5 sp from 26 down to 20 at which the name, measured in the same style, is as wide on one
     * line as the layout says it is — the steps are some 4 px apart there.
     */
    private fun drawnSize(what: String, layout: TextLayoutResult): TextUnit {
        val drawn = layout.multiParagraph.maxIntrinsicWidth
        val steps = generateSequence(CITY_SIZE.value) { it - SIZE_STEP }.takeWhile { it >= CITY_LEAST.value }.map { it.sp }
        return steps.firstOrNull { abs(widthAt(layout, it) - drawn) < WIDTH_SLACK_PX }
            ?: run { fail("$what: the name is $drawn px wide, at no size from $CITY_SIZE to $CITY_LEAST"); CITY_SIZE }
    }

    /**
     * The name of the city in a column it does not fit at 26 sp (spec 5.29 R7): «Санкт-Петербург» and «Saint-Pétersbourg» — one word
     * each — step down, and no word breaks; «Sankt Petersburg» goes on to a second line at its space and keeps its size. At the font
     * 2.0, the largest of the system, where 26 sp are 36.7 dp and 20 sp — the least — 34: the name stands at 26 sp on one line in a
     * window of 412 first, and the column is made from its own widths at 26 and at 20 sp then, halfway between them — 26 sp does not
     * fit there, 20 does, whatever the font of the device. Some 333 dp in Russian: a phone's. (At 1.3 the name fits a column of 328 at
     * 26 sp in every language: a test there could not fail.) The size is the one the name is drawn at ([drawnSize]).
     */
    private fun cityWhole(language: String, stepsDown: Boolean) {
        speaking(language)
        fontScale = LARGEST_FONT
        state = stateAt(SPB, 0)
        show()
        val whole = cityLayout()
        assertEquals("$language: the name on one line in 412", 1, whole.lineCount)
        assertEquals("$language: at 26 sp in 412", CITY_SIZE, drawnSize(language, whole))
        val column = with(compose.density) { ((widthAt(whole, CITY_SIZE) + widthAt(whole, CITY_LEAST)) / 2).toDp() }
        windowSize = DpSize(column + COLUMN_SIDES, 800.dp)
        compose.waitForIdle()
        val laid = cityLayout()
        val size = drawnSize(language, laid)
        if (stepsDown) {
            assertTrue("$language: the name steps down, not under the least: $size", size < CITY_SIZE && size >= CITY_LEAST)
            assertEquals("$language: on one line", 1, laid.lineCount)
        } else {
            assertEquals("$language: the name keeps its size", CITY_SIZE, size)
            assertEquals("$language: on two lines, at its space", 2, laid.lineCount)
        }
        assertWordsWhole(language, word(CITY_SPB))
    }

    @Test
    fun theNameOfTheCityStepsDownRatherThanBreakAWordInRussian() = cityWhole("ru", stepsDown = true)

    @Test
    fun theNameOfTheCityStepsDownRatherThanBreakAWordInFrench() = cityWhole("fr", stepsDown = true)

    @Test
    fun theNameOfTheCityGoesOnAtItsSpaceAndKeepsItsSizeInGerman() = cityWhole("de", stepsDown = false)

    /**
     * The line of the path in the card of 300 (360 less the sides of the zone and of the card) with its number, at the fonts 1 and
     * 1.3: no word broken — at 1.3 «Санкт-Петербурга» and «Saint-Pétersbourg» do not fit beside «1 472 / 6 000», and the number goes
     * under the line — and the number inside the card, beside the words or under them, never over them.
     */
    private fun pathLineWhole(language: String) {
        speaking(language)
        windowSize = DpSize(300.dp, 200.dp)
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow {
                    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
                    WordsAndNumber(word(PATH_LINE_SPB), style, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.width(300.dp)) {
                        TaktAmount(
                            word(HAVE_SPB),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                            icon = 14.dp,
                            merge = false,
                        )
                    }
                }
            }
        }
        for (font in listOf(1f, 1.3f)) {
            fontScale = font
            compose.waitForIdle()
            val where = "$language at $font"
            assertWordsWhole(where, word(PATH_LINE_SPB))
            val line = compose.onNodeWithText(word(PATH_LINE_SPB), useUnmergedTree = true).bounds()
            val number = compose.onNodeWithText(word(HAVE_SPB), useUnmergedTree = true).bounds()
            assertTrue("$where: the number inside the card: $number", number.right <= window().left + 300.dp + 0.5.dp)
            val beside = number.top < line.bottom - 0.5.dp
            if (beside) {
                assertTrue("$where: beside, the number stands off the words: $line, $number", number.left >= line.right)
            } else {
                assertTrue("$where: under, the number stands under the words: $line, $number", number.top >= line.bottom)
            }
            if (language == "ru" && font == 1f) assertTrue("$where: on 360 at the font 1 the number stands beside", beside)
            if (language == "ru" && font == 1.3f) assertFalse("$where: «Санкт-Петербурга» does not fit beside at 1.3: the number goes under", beside)
        }
    }

    @Test
    fun theLineOfThePathBreaksNoWordAndItsNumberGoesUnderItWhereItMustInRussian() = pathLineWhole("ru")

    @Test
    fun theLineOfThePathBreaksNoWordAndItsNumberGoesUnderItWhereItMustInFrench() = pathLineWhole("fr")

    @Test
    fun theLineOfThePathBreaksNoWordAndItsNumberGoesUnderItWhereItMustInGerman() = pathLineWhole("de")

    private companion object {
        const val WINDOW = "window"

        val VIENNA = JourneyRoute.indexOf("vienna")
        val PRAGUE = JourneyRoute.indexOf("prague")
        val LONDON = JourneyRoute.indexOf("london")
        val SPB = JourneyRoute.indexOf("spb")
        val SYDNEY = JourneyRoute.stops.lastIndex

        /** The purse of the mockups: enough for Prague; and short of it. */
        const val PURSE = 47_884L
        const val SHORT_PURSE = 472L
        const val TOUR_PURSE = 2_516L
        const val HOME_PURSE = 340L
        const val BIG_PURSE = 147_884L
        const val LONDON_SHORT = 1_472L
        val PRAGUE_PRICE = JourneyRoute.stops[PRAGUE].price.toLong()
        val SPB_PRICE = JourneyRoute.stops[SPB].price.toLong()

        val HOME_LOOK = HomeState(loaded = true, purchased = emptySet(), houses = emptySet(), choices = emptyMap())

        /** Home on 5 September, Cremona on the 7th, Milan on the 12th, Salzburg on the 18th, Vienna on the 20th… */
        private val DAYS = listOf(5, 7, 12, 18, 20, 22, 23, 24, 25, 26, 27, 28, 28, 29, 29, 30, 30)

        fun dayMs(index: Int): Long = LocalDate(2026, 9, DAYS[index]).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        /** The journey stood at the stop [index] with [balance] in the purse: every leg to it paid. */
        fun stateAt(index: Int, balance: Long, phase: JourneyPhase? = null): JourneyState {
            val spent = JourneyRoute.stops.take(index + 1).sumOf { it.price.toLong() }
            val progress = JourneyProgress(
                earned = spent + balance, spent = spent, arrivals = (0..index).map { Arrival(JourneyRoute.stops[it].id, dayMs(it)) }, extras = emptySet(),
            )
            return JourneyReducer.stateOf(progress, phase, JourneyConfig())
        }

        const val TITLE = "title"
        const val DEPART_PRAGUE = "departPrague"
        const val SPEND_PRAGUE = "spendPrague"
        const val PATH_ENOUGH = "pathEnough"
        const val PATH_SHORT = "pathShort"
        const val HAVE_SHORT = "haveShort"
        const val PLATE_SAID = "plateSaid"
        const val TOUR_SAID = "tourSaid"
        const val DEPART_ANY = "departAny"
        const val POSTCARD_VIENNA = "postcardVienna"
        const val DOOR_HOME = "doorHome"
        const val NO_CARDS = "noCards"
        const val PASSED_VIENNA = "passedVienna"
        const val DEPART_CREMONA = "departCremona"
        const val SPEND_CREMONA = "spendCremona"
        const val STRIP = "strip"
        const val INTRO_START = "introStart"
        const val STAMP = "stamp"
        const val DONE = "done"
        const val PLAY_HERE = "playHere"
        const val STAMP_NUMBER = "stampNumber"
        const val STAMP_LEG = "stampLeg"
        const val STAMP_LAST = "stampLast"
        const val DEPART_SPB = "departSpb"
        const val SPEND_SPB = "spendSpb"
        const val CITY_SPB = "citySpb"
        const val PATH_LINE_SPB = "pathLineSpb"
        const val HAVE_SPB = "haveSpb"
        const val CITY_PRAGUE = "cityPrague"
        const val PLACE_PRAGUE = "placePrague"
        const val POSTCARD_PRAGUE = "postcardPrague"
        const val STAMP_PRAGUE = "stampPrague"
        const val INTRO_TITLE = "introTitle"
        const val HOME_POSTCARD = "homePostcard"
        const val MISSING_WORDS = "missingWords"

        /**
         * The largest font of the system; the size of the name of a city, the least it steps down to and its step (5.29 R7); the sides
         * of the column; how near the width of a layout is to the width of the name measured at its size.
         */
        const val LARGEST_FONT = 2f
        val CITY_SIZE = 26.sp
        val CITY_LEAST = 20.sp
        const val SIZE_STEP = 0.5f
        val COLUMN_SIDES = 32.dp
        const val WIDTH_SLACK_PX = 0.5f

        /** What scrolls over the zone ends that far over it (plan 2.4: LocalDockInset + 24). */
        val SCROLL_END = 24.dp

        /** Frames between the two taps of a double tap: the moment that answered the first has come in a frame or two (stage 107). */
        const val TAP_FRAMES = 2

        /** The stamp in the frame of 220 (5.29 R7: 168 of 220). */
        val UPRIGHT_STAMP = 168.dp

        /**
         * The windows of the moments, and the window each is told it is in: 892 × 412 lying is some 360 high under its bars; the
         * emulator's 640 × 360 lying is 603 × 308 under its bars and cutout (603 × 336 with a thin status bar); upright 412 × 800 and
         * 360 × 640.
         */
        val PHASE_WINDOWS = listOf(
            DpSize(892.dp, 360.dp) to DpSize(892.dp, 412.dp),
            DpSize(603.dp, 308.dp) to DpSize(603.dp, 308.dp),
            DpSize(603.dp, 336.dp) to DpSize(603.dp, 336.dp),
            DpSize(412.dp, 800.dp) to DpSize(412.dp, 800.dp),
            DpSize(360.dp, 640.dp) to DpSize(360.dp, 640.dp),
        )

        /** The languages whose «спишется …» does not stand whole on 360 at the font 1.3: the words around the numbers give way. */
        val CUT_AT_1_3 = setOf("de", "fr")

        /** Stands for the number in a phrase whose words are wanted without it. */
        const val MARK = "\u0001"
    }
}
