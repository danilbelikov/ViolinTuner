package com.violinjourney.app.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.a4_option_description
import com.violinjourney.app.shared.resources.app_name
import com.violinjourney.app.shared.resources.backup_onboarding_link
import com.violinjourney.app.shared.resources.onboarding_a4_hint_lead
import com.violinjourney.app.shared.resources.onboarding_a4_text
import com.violinjourney.app.shared.resources.onboarding_a4_title
import com.violinjourney.app.shared.resources.onboarding_data_analytics_lead
import com.violinjourney.app.shared.resources.onboarding_data_copy_lead
import com.violinjourney.app.shared.resources.onboarding_data_cta
import com.violinjourney.app.shared.resources.onboarding_data_title
import com.violinjourney.app.shared.resources.onboarding_data_takes_lead
import com.violinjourney.app.shared.resources.onboarding_journey_title
import com.violinjourney.app.shared.resources.onboarding_live_foot
import com.violinjourney.app.shared.resources.onboarding_live_text
import com.violinjourney.app.shared.resources.onboarding_live_title
import com.violinjourney.app.shared.resources.onboarding_mic_cta
import com.violinjourney.app.shared.resources.onboarding_mic_hint_lead
import com.violinjourney.app.shared.resources.onboarding_mic_text
import com.violinjourney.app.shared.resources.onboarding_mic_title
import com.violinjourney.app.shared.resources.onboarding_next
import com.violinjourney.app.shared.resources.onboarding_part_intro
import com.violinjourney.app.shared.resources.onboarding_part_setup
import com.violinjourney.app.shared.resources.onboarding_progress_description
import com.violinjourney.app.shared.resources.onboarding_skip
import com.violinjourney.app.shared.resources.onboarding_tolerance_cta
import com.violinjourney.app.shared.resources.onboarding_tolerance_text
import com.violinjourney.app.shared.resources.onboarding_tolerance_title
import com.violinjourney.app.shared.resources.onboarding_welcome_cta
import com.violinjourney.app.shared.resources.onboarding_welcome_text
import com.violinjourney.app.shared.resources.tolerance_beginner_name
import com.violinjourney.app.shared.resources.tolerance_beginner_text
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_few
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_many
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_one
import com.violinjourney.app.shared.resources.tolerance_intermediate_name
import com.violinjourney.app.shared.resources.tolerance_intermediate_text
import com.violinjourney.app.shared.resources.tolerance_pro_name
import com.violinjourney.app.shared.resources.tolerance_pro_text
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The onboarding of R8 (spec 3.36.8, 5.29 R8) by what an eye, a finger and a reader meet: one strip of the way for the seven screens,
 * standing on the edge of the picture upright and in the top row of the words lying, as long and as high on all seven, its label
 * never broken and the title 12 under it; lying on the emulator's 640 × 360 (603 × 308 behind its cutout and bars, and 603 × 336 where
 * the status bar is hidden) the words of the first page stand whole with «У меня есть копия данных» on two lines beside «Начать», or
 * under it where beside it would take three lines; the buttons of the reference and the hint of «Микрофон» stand whole as their step
 * opens, the cards of the tolerance come into reach above the button; each step of the setup opens at its top, and a turn of the
 * phone keeps where its words were scrolled; the hint of «Микрофон» stands only while the microphone is not allowed and leaves with
 * its face; «Пока играете…» stands over the button; a second tap of a finger does not press what took the place of its button; the
 * words fade into the ground at an edge past which they go on. And from the review of stage 120: the strip under «Пропустить» in a low
 * window upright, the names of the cards whole on a small phone at a large font; from the lead's check: two taps before the next frame
 * move the way once, and the label of the strip is at most two of its lines on all seven screens; from the check of that fix: a finger
 * on a page the model moves neither sends it back nor refuses the jump of «Пропустить», and the stop of a slide cut short undoes neither
 * back nor a jump — back brings the first page back whole.
 *
 * The screen is laid out in a window of its own size ([TestWindow]: the bars consumed as the root of the app consumes them, the
 * widening of touch targets off, the pictures still). The words are read in the composition, in the language of the process
 * ([speaking]).
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(stateOf(OnboardingStep.WELCOME))
    private var windowSize by mutableStateOf(UPRIGHT)
    private var fontScale by mutableFloatStateOf(1f)
    private var micAllowed by mutableStateOf(false)

    /** The screen moves as on a phone: the window of the tests stills the motion, this gives it back ([TestWindow]). */
    private var living by mutableStateOf(false)
    private val words = mutableMapOf<String, String>()
    private val intents = mutableListOf<OnboardingIntent>()
    private var colors = ThemeColors()

    /** How many times a press made the model ask the system for the microphone, and finish ([OnboardingFlow.press]). */
    private var asks = 0
    private var finishes = 0

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun stateOf(step: OnboardingStep, tolerance: TolerancePreset = TolerancePreset.INTERMEDIATE) =
        OnboardingState(step, a4Hz = UserSettings.DEFAULT_A4_HZ, a4OptionsHz = UserSettings.A4_OPTIONS_HZ, tolerance = tolerance)

    private fun show() {
        compose.setContent { Screen() }
        compose.waitForIdle()
    }

    @Composable
    private fun Screen() {
        ReadWords()
        ViolinTheme {
            colors = ThemeColors(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, ViolinTheme.accentSoft, MaterialTheme.colorScheme.surfaceContainer)
            TestWindow(windowSize, fontScale = fontScale) {
                CompositionLocalProvider(LocalReduceMotion provides !living) {
                    OnboardingScreen(state, onIntent = ::onIntent, onHaveBackup = {}, micAllowed = micAllowed)
                }
            }
        }
    }

    /**
     * What the screen asks for — and, as the view model does, what a press, a swipe and back do ([OnboardingFlow.press],
     * [OnboardingFlow.skipped], [OnboardingFlow.swipedTo], [OnboardingFlow.back]): with the microphone allowed the system answers its
     * question at once and the answer moves on, as `rememberMicPermissionRequester` does on a phone; the rest only noted.
     */
    private fun onIntent(intent: OnboardingIntent) {
        intents += intent
        when (intent) {
            is OnboardingIntent.PrimaryClicked -> when (val press = OnboardingFlow.press(state.step, intent.from)) {
                is OnboardingPress.GoTo -> state = stateOf(press.step)
                OnboardingPress.AskMicrophone -> {
                    asks++
                    if (micAllowed) state = stateOf(OnboardingStep.REFERENCE_PITCH)
                }
                OnboardingPress.Finish -> finishes++
                OnboardingPress.Stay -> Unit
            }
            is OnboardingIntent.SkipClicked -> OnboardingFlow.skipped(state.step, intent.from)?.let { state = stateOf(it) }
            is OnboardingIntent.PageShown -> moveTo(OnboardingFlow.swipedTo(state.step, intent.page))
            OnboardingIntent.BackPressed -> OnboardingFlow.back(state.step)?.let(::moveTo)
            else -> Unit
        }
    }

    /** The model on [step], its choices kept. */
    private fun moveTo(step: OnboardingStep) {
        if (step != state.step) state = state.copy(step = step)
    }

    /** The colours of the theme the pixels are told by: the ground of the screen, the accent, the soft accent, a card. */
    private class ThemeColors(
        val ground: Color = Color.Unspecified,
        val accent: Color = Color.Unspecified,
        val accentSoft: Color = Color.Unspecified,
        val card: Color = Color.Unspecified,
    )

    /** The words of the tests, read where the screen reads them. */
    @Composable
    private fun ReadWords() {
        val intro = stringResource(Res.string.onboarding_part_intro)
        val setup = stringResource(Res.string.onboarding_part_setup)
        val total = OnboardingStep.entries.size
        OnboardingStep.entries.forEach { step ->
            words[STRIP + step.number] = stringResource(Res.string.onboarding_progress_description, if (step.part == OnboardingPart.INTRO) intro else setup, step.number, total)
        }
        words[LIVE_TITLE] = stringResource(Res.string.onboarding_live_title)
        words[JOURNEY_TITLE] = stringResource(Res.string.onboarding_journey_title)
        words[DATA_TITLE] = stringResource(Res.string.onboarding_data_title)
        words[APP_NAME] = stringResource(Res.string.app_name)
        words[WELCOME_TEXT] = stringResource(Res.string.onboarding_welcome_text)
        words[START] = stringResource(Res.string.onboarding_welcome_cta)
        words[LINK] = stringResource(Res.string.backup_onboarding_link)
        words[NEXT] = stringResource(Res.string.onboarding_next)
        words[LIVE_TEXT] = stringResource(Res.string.onboarding_live_text)
        words[LIVE_FOOT] = stringResource(Res.string.onboarding_live_foot)
        words[MIC_CTA] = stringResource(Res.string.onboarding_mic_cta)
        words[MIC_HINT] = stringResource(Res.string.onboarding_mic_hint_lead)
        words[A4_TITLE] = stringResource(Res.string.onboarding_a4_title)
        words[A4_HINT] = stringResource(Res.string.onboarding_a4_hint_lead)
        words[HZ_440] = stringResource(Res.string.a4_option_description, 440)
        words[HZ_443] = stringResource(Res.string.a4_option_description, 443)
        words[TOLERANCE_TITLE] = stringResource(Res.string.onboarding_tolerance_title)
        words[TOLERANCE_CAPTION] = stringResource(Res.string.onboarding_tolerance_text)
        words[TOLERANCE_CTA] = stringResource(Res.string.onboarding_tolerance_cta)
        words[CARD_BEGINNER] = card(Res.string.tolerance_beginner_name, Res.string.tolerance_beginner_text, TolerancePreset.BEGINNER.cents)
        words[CARD_INTERMEDIATE] = card(Res.string.tolerance_intermediate_name, Res.string.tolerance_intermediate_text, TolerancePreset.INTERMEDIATE.cents)
        words[CARD_PRO] = card(Res.string.tolerance_pro_name, Res.string.tolerance_pro_text, TolerancePreset.PRO.cents)
        words[SKIP] = stringResource(Res.string.onboarding_skip)
        words[GOT_IT] = stringResource(Res.string.onboarding_data_cta)
        words[MIC_TITLE] = stringResource(Res.string.onboarding_mic_title)
        words[MIC_TEXT] = stringResource(Res.string.onboarding_mic_text)
        words[A4_TEXT] = stringResource(Res.string.onboarding_a4_text)
        words[ROW_COPY] = stringResource(Res.string.onboarding_data_copy_lead)
        words[ROW_TAKES] = stringResource(Res.string.onboarding_data_takes_lead)
        words[ROW_ANALYTICS] = stringResource(Res.string.onboarding_data_analytics_lead)
    }

    @Composable
    private fun card(name: org.jetbrains.compose.resources.StringResource, caption: org.jetbrains.compose.resources.StringResource, cents: Int): String {
        val spoken = stringResource(Formats.plural(cents, Res.string.tolerance_cents_spoken_one, Res.string.tolerance_cents_spoken_few, Res.string.tolerance_cents_spoken_many), cents)
        return "${stringResource(name)}, ${stringResource(caption)}, $spoken"
    }

    private fun word(key: String): String = words.getValue(key)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun strip(step: OnboardingStep) = compose.onNodeWithContentDescription(word(STRIP + step.number))

    /**
     * The button of [text] in the window — its capsule, which is what is pressed: the widening of touch targets is off. The pages beside
     * the one in view are composed by the pager next to the window, with their own «Дальше»: the one inside the window is the one.
     */
    private fun button(text: String): SemanticsNodeInteraction {
        val window = compose.onNodeWithTag(TEST_WINDOW).bounds()
        val all = compose.onAllNodes(hasText(text) and hasClickAction())
        val inWindow = (0 until all.fetchSemanticsNodes().size).map { all[it] }.filter { node ->
            val box = node.bounds()
            box.left >= window.left - 0.5.dp && box.right <= window.right + 0.5.dp
        }
        assertEquals("one «$text» in the window", 1, inWindow.size)
        return inWindow.single()
    }

    private fun assertNear(what: String, expected: Dp, actual: Dp, tolerance: Dp = 1.dp) =
        assertTrue("$what: $actual, expected $expected ± $tolerance", actual in (expected - tolerance)..(expected + tolerance))

    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    private fun window(): DpRect = compose.onNodeWithTag(TEST_WINDOW).bounds()

    private fun center(rect: DpRect): DpOffset = DpOffset((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)

    private fun DpRect.holds(point: DpOffset): Boolean = point.x in left..right && point.y in top..bottom

    /** A finger down and up again at [point] of the root — where it was a moment ago, whatever stands there now. */
    private fun tapAt(point: DpOffset) {
        val box = window()
        compose.onNodeWithTag(TEST_WINDOW).performTouchInput { click(Offset((point.x - box.left).toPx(), (point.y - box.top).toPx())) }
    }

    /**
     * A finger down and up again at [point] with no time between, as `adb shell input tap` sends it: [tapAt]'s click moves the clock
     * 16 ms between its down and its up, and a frame run there would compose what the tap before brought before this one lands.
     */
    private fun tapInstantly(point: DpOffset) {
        val box = window()
        compose.onNodeWithTag(TEST_WINDOW).performTouchInput {
            down(Offset((point.x - box.left).toPx(), (point.y - box.top).toPx()))
            up()
        }
    }

    /** A gesture of a finger on the window, at points of the root ([at]). */
    private fun touch(gesture: TouchInjectionScope.(at: (DpOffset) -> Offset) -> Unit) {
        val box = window()
        compose.onNodeWithTag(TEST_WINDOW).performTouchInput {
            gesture { point -> Offset((point.x - box.left).toPx(), (point.y - box.top).toPx()) }
        }
    }

    /** The strip says [step]: «Знакомство, экран 2 из 7» — and no other step. */
    private fun assertStripSays(step: OnboardingStep, what: String) {
        OnboardingStep.entries.forEach { other ->
            val nodes = compose.onAllNodesWithContentDescription(word(STRIP + other.number)).fetchSemanticsNodes().size
            assertEquals("$what: the strip on ${step.number}, nodes saying ${other.number}", if (other == step) 1 else 0, nodes)
        }
    }

    /** How many nodes of the hint of «Микрофон» the screen has — the leaving face's too, whatever its alpha. */
    private fun micHints(): Int = compose.onAllNodes(hasText(word(MIC_HINT), substring = true)).fetchSemanticsNodes().size

    /**
     * The window of the words around [words]: the scroll that goes down and holds them across — the pager composes its neighbours beside
     * the page in view, lying the one before it inside the window, in the half of the picture.
     */
    private fun wordsScroll(words: DpRect): SemanticsNodeInteraction {
        val all = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        return (0 until all.fetchSemanticsNodes().size).map { all[it] }.single { node ->
            val box = node.bounds()
            box.left <= words.left + 0.5.dp && box.right >= words.right - 0.5.dp
        }
    }

    /**
     * The colour of the screen at [x], [y] of the root. The root is captured, not the window: a window wider than the phone (603 lying
     * on a phone upright) stands over its edges, and a node captures only what of it is on the screen.
     */
    private fun pixel(x: Dp, y: Dp): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return with(compose.density) {
            val px = x.roundToPx()
            val py = y.roundToPx()
            assertTrue("($x, $y) is on the screen of ${pixels.width} × ${pixels.height} px", px in 0 until pixels.width && py in 0 until pixels.height)
            pixels[px, py]
        }
    }

    private fun assertColorNear(what: String, expected: Color, actual: Color) {
        val near = abs(expected.red - actual.red) <= PIXEL_SLACK && abs(expected.green - actual.green) <= PIXEL_SLACK && abs(expected.blue - actual.blue) <= PIXEL_SLACK
        assertTrue("$what: $actual, expected $expected", near)
    }

    // ---- the strip of the way

    /**
     * Upright the strip stands outside the pager on the edge of the picture, where its unseen twin keeps its room on the page: the title
     * starts 12 under its label (spec 5.29 R8) — the living strip stands right on its twin.
     */
    @Test
    fun uprightTheStripStandsWhereItsTwinKeepsTheRoom() {
        windowSize = UPRIGHT
        show()
        val strip = strip(OnboardingStep.WELCOME).assertIsDisplayed().bounds()
        val title = compose.onNodeWithText(word(APP_NAME)).bounds()
        assertNear("the title under the label of the strip", strip.bottom + PROGRESS_TO_TITLE, title.top)
    }

    /** One strip, one phrase: its twin on the page is neither heard nor counted twice (spec 3.36.8). */
    @Test
    fun theStripIsOneNodeForAReaderUprightAndLying() {
        show()
        listOf(UPRIGHT, LOW_LYING).forEach { size ->
            windowSize = size
            state = stateOf(OnboardingStep.WELCOME)
            compose.waitForIdle()
            compose.onAllNodesWithContentDescription(word(STRIP + 1)).assertCountEquals(1)
            state = stateOf(OnboardingStep.TOLERANCE)
            compose.waitForIdle()
            compose.onAllNodesWithContentDescription(word(STRIP + 7)).assertCountEquals(1)
        }
    }

    /**
     * Lying the strip stands in the top row of the column of words and ends before «Пропустить» — as long on the pages without it and in
     * the setup, so it does not jump into the setup (spec 3.36.8). On the emulator's 640 × 360 and with its status bar hidden.
     */
    @Test
    fun lyingTheStripIsAsLongOnAllSevenScreens() {
        windowSize = LOW_LYING
        show()
        listOf(LOW_LYING, LOW_LYING_NO_STATUS_BAR, LYING).forEach { size ->
            windowSize = size
            state = stateOf(OnboardingStep.WELCOME)
            compose.waitForIdle()
            val first = strip(OnboardingStep.WELCOME).bounds()
            OnboardingStep.entries.drop(1).forEach { step ->
                state = stateOf(step)
                compose.waitForIdle()
                val other = strip(step).bounds()
                assertNear("the strip of ${step.number} as long as the first at $size", first.width, other.width, 0.5.dp)
                assertNear("the strip of ${step.number} where the first is at $size", first.left, other.left, 0.5.dp)
                assertNear("the strip of ${step.number} as high as the first at $size", first.top, other.top, 0.5.dp)
            }
        }
    }

    // ---- the first page lying

    /**
     * On the emulator's 640 × 360 lying the words of the first page stand whole, nothing cut at the edge of their scroll (the fault of
     * the «было» shots: «мира.» cut in half), and «У меня есть копия данных» stands beside «Начать» on two lines at most, every word whole
     * — before, it took three and pushed the words up.
     */
    @Test
    fun lyingTheFirstPageStandsWholeWithTheLinkBesideTheButton() {
        windowSize = LOW_LYING
        show()
        listOf(LOW_LYING, LOW_LYING_NO_STATUS_BAR).forEach { size ->
            windowSize = size
            compose.waitForIdle()
            val start = button(word(START)).bounds()
            val link = button(word(LINK)).bounds()
            val text = compose.onNodeWithText(word(WELCOME_TEXT)).bounds()
            assertTrue("the words end above the button at $size: $text, $start", text.bottom <= start.top - GAP_OVER_BUTTON + 0.5.dp)
            assertTrue("the link beside the button at $size: $link, $start", link.left >= start.right && link.top < start.bottom && link.bottom > start.top)
            val words = compose.onNode(hasText(word(LINK)), useUnmergedTree = true)
            assertTrue("the link on two lines at most", words.textLayout().lineCount <= 2)
            assertWordsWhole(words, word(LINK))
        }
    }

    /** Where beside the button the link would take three lines or break a word — German at the font 1.3 — it goes under the button. */
    @Test
    fun lyingTheLinkGoesUnderTheButtonWhereBesideItWouldNotStandWhole() {
        speaking("de")
        fontScale = 1.3f
        windowSize = LOW_LYING
        state = stateOf(OnboardingStep.WELCOME)
        show()
        val start = button(word(START)).bounds()
        val link = button(word(LINK)).bounds()
        assertTrue("the link under the button: $link, $start", link.top >= start.bottom - 0.5.dp)
        assertWordsWhole(compose.onNode(hasText(word(LINK)), useUnmergedTree = true), word(LINK))
    }

    /** «Пока играете, ничего нажимать не нужно» is out of the scroll, right over the button — lying too (spec 3.36.8). */
    @Test
    fun thePromiseOfLiveStandsOverTheButton() {
        state = stateOf(OnboardingStep.LIVE)
        show()
        listOf(UPRIGHT, LOW_LYING).forEach { size ->
            windowSize = size
            compose.waitForIdle()
            val foot = compose.onNodeWithText(word(LIVE_FOOT)).bounds()
            val next = button(word(NEXT)).bounds()
            assertNear("6 over the button at $size", next.top - FOOT_TO_BUTTON, foot.bottom)
            val text = compose.onNodeWithText(word(LIVE_TEXT)).bounds()
            assertTrue("the words of the page above it at $size", text.top < foot.top)
        }
    }

    // ---- the setup

    /** The hint of «Микрофон» stands while the microphone is not allowed and is gone once it is (spec 3.36.8). */
    @Test
    fun theHintOfTheMicrophoneStandsOnlyWhileItIsNotAllowed() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.MICROPHONE)
        micAllowed = false
        show()
        compose.onNode(hasText(word(MIC_HINT), substring = true)).assertIsDisplayed()
        button(word(MIC_CTA)).assertIsDisplayed()
        micAllowed = true
        compose.waitForIdle()
        compose.onAllNodes(hasText(word(MIC_HINT), substring = true)).assertCountEquals(0)
    }

    /**
     * Lying on 640 × 360 the four buttons of the reference and the three cards of the tolerance are reached by the scroll of the words,
     * whole above the button of the step, and so is the caption under the cards. What a step shows as it opens, before any scroll, is
     * [lyingTheButtonsOfTheReferenceStandWholeAsTheStepOpens].
     */
    @Test
    fun lyingTheReferenceAndTheCardsAreReachedByTheScroll() {
        windowSize = LOW_LYING
        show()
        listOf(LOW_LYING, LOW_LYING_NO_STATUS_BAR).forEach { size ->
            windowSize = size
            state = stateOf(OnboardingStep.REFERENCE_PITCH)
            compose.waitForIdle()
            val next = button(word(NEXT)).bounds()
            listOf(HZ_440, HZ_443).forEach { key ->
                val hz = compose.onNodeWithContentDescription(word(key)).performScrollTo().bounds()
                assertTrue("«${word(key)}» whole above «${word(NEXT)}» at $size: $hz, $next", hz.bottom <= next.top + 0.5.dp && hz.height >= A4_LEAST - 0.5.dp)
            }
            state = stateOf(OnboardingStep.TOLERANCE)
            compose.waitForIdle()
            val play = button(word(TOLERANCE_CTA)).bounds()
            val card = compose.onNodeWithContentDescription(word(CARD_BEGINNER)).performScrollTo().bounds()
            assertTrue("the first card whole above the button at $size: $card, $play", card.bottom <= play.top + 0.5.dp && card.height >= CARD_LEAST - 0.5.dp)
            val last = compose.onNodeWithContentDescription(word(CARD_PRO)).performScrollTo().bounds()
            assertTrue("the last card too at $size: $last", last.bottom <= play.top + 0.5.dp)
            compose.onNodeWithText(word(TOLERANCE_CAPTION)).performScrollTo().assertIsDisplayed()
        }
    }

    /** A step scrolled down never opens the next one scrolled: lying, «Допуск» opened from a scrolled «Эталон» starts at its title. */
    @Test
    fun eachStepOfTheSetupOpensAtItsTop() {
        windowSize = LOW_LYING
        state = stateOf(OnboardingStep.REFERENCE_PITCH)
        show()
        compose.onNode(hasText(word(A4_HINT), substring = true)).performScrollTo()
        compose.onNodeWithText(word(A4_TITLE)).assertIsNotDisplayed()
        state = stateOf(OnboardingStep.TOLERANCE)
        compose.waitForIdle()
        val strip = strip(OnboardingStep.TOLERANCE).bounds()
        val title = compose.onNodeWithText(word(TOLERANCE_TITLE)).assertIsDisplayed().bounds()
        assertTrue("«${word(TOLERANCE_TITLE)}» at the top of its scroll: $title under $strip", title.top >= strip.bottom - 0.5.dp)
    }

    /**
     * A turn of the phone keeps where the words of a step were scrolled: their scroll is made at one place of the tree lying and upright,
     * so what is saved of it is found again when the activity is made anew in the other shape ([StateRestorationTester]).
     */
    @Test
    fun aTurnOfThePhoneKeepsWhereTheWordsOfAStepWereScrolled() {
        val restoration = StateRestorationTester(compose)
        fontScale = 1.3f
        windowSize = LOW_LYING
        state = stateOf(OnboardingStep.REFERENCE_PITCH)
        restoration.setContent { Screen() }
        compose.onNode(hasText(word(A4_HINT), substring = true)).performScrollTo()
        compose.onNodeWithText(word(A4_TITLE)).assertIsNotDisplayed()
        // turned upright, the activity made anew with what was saved
        windowSize = SMALL_UPRIGHT
        restoration.emulateSavedInstanceStateRestore()
        val strip = strip(OnboardingStep.REFERENCE_PITCH).bounds()
        val title = compose.onNodeWithText(word(A4_TITLE)).bounds()
        assertTrue("still scrolled after the turn: the title at $title, the top of the words at ${strip.bottom + PROGRESS_TO_TITLE}", title.top < strip.bottom + PROGRESS_TO_TITLE - 1.dp)
    }

    /** The buttons of the reference read «440 герц» with their choice; «Гц» under the number is not a stop of its own (spec 3.36.8). */
    @Test
    fun theButtonsOfTheReferenceReadAsHertzWithTheirChoice() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.REFERENCE_PITCH)
        show()
        compose.onNodeWithContentDescription(word(HZ_440))
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithContentDescription(word(HZ_443)).assertIsNotSelected()
        compose.onAllNodesWithText(Formats.language.sound.hz).assertCountEquals(0)
        compose.onAllNodesWithText("440").assertCountEquals(0)
    }

    // ---- the review of stage 120

    /**
     * The leaving «Микрофон» keeps its face (the lesson of stage 119): «Разрешить» in the system dialog moves the step on and allows the
     * microphone in one frame — the hint fades with the words of the step and does not go from under them in the first frame of the
     * dissolve. The screen moves here (the window of the tests stills it), frame by frame.
     */
    @Test
    fun aLeavingMicrophoneKeepsItsHintWhileItsWordsFade() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.MICROPHONE)
        micAllowed = false
        show()
        // the living pictures ask for frames all the time: they are given one by one
        compose.mainClock.autoAdvance = false
        living = true
        frames(SOME_FRAMES)
        assertEquals("the hint of «Микрофон»", 1, micHints())
        // the answer of the system: the next step and the microphone allowed, in one frame
        state = stateOf(OnboardingStep.REFERENCE_PITCH)
        micAllowed = true
        frames(1)
        assertEquals("the leaving face keeps its hint in the first frame of the dissolve", 1, micHints())
        frames(SOME_FRAMES)
        assertEquals("…and on while it fades", 1, micHints())
        compose.mainClock.advanceTimeBy(DISSOLVE_MS)
        assertEquals("gone with its face", 0, micHints())
        living = false
        compose.mainClock.autoAdvance = true
    }

    /**
     * A double tap on «Дальше» of «Эталон» (5.29 R8, as R3 and R7): «Начать играть» takes its place under the finger at once — the second
     * tap, a frame or two later, does not finish the onboarding with «Допуск» unseen. Once the time of a double tap has passed, it answers.
     */
    @Test
    fun aSecondTapOfNextOnTheReferenceDoesNotSkipTheTolerance() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.REFERENCE_PITCH)
        show()
        val finger = center(button(word(NEXT)).bounds())
        compose.mainClock.autoAdvance = false
        tapAt(finger)
        frames(TAP_FRAMES)
        assertTrue("«${word(TOLERANCE_CTA)}» is under the finger that pressed «${word(NEXT)}»", button(word(TOLERANCE_CTA)).bounds().holds(finger))
        tapAt(finger)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("the second tap did not finish the onboarding", 1, intents.count { it is OnboardingIntent.PrimaryClicked })
        assertEquals(0, finishes)
        tapAt(finger)
        compose.waitForIdle()
        assertEquals("after the time of a double tap it answers", 2, intents.count { it is OnboardingIntent.PrimaryClicked })
        assertEquals("…and finishes", 1, finishes)
    }

    /**
     * A double tap on «Понятно»: «Разрешить микрофон» takes its place — the second tap does not ask the system before the hint «Любой
     * ответ ведёт дальше» is read (3.36.8).
     */
    @Test
    fun aSecondTapOfGotItDoesNotAskTheSystemBeforeTheHintIsRead() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.DATA)
        show()
        val finger = center(button(word(GOT_IT)).bounds())
        compose.mainClock.autoAdvance = false
        tapAt(finger)
        frames(TAP_FRAMES)
        assertTrue("«${word(MIC_CTA)}» is under the finger that pressed «${word(GOT_IT)}»", button(word(MIC_CTA)).bounds().holds(finger))
        tapAt(finger)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("one press — to the microphone, not to the system", 1, intents.count { it is OnboardingIntent.PrimaryClicked })
        assertEquals(0, asks)
        assertEquals(OnboardingStep.MICROPHONE, state.step)
    }

    /**
     * «Пропустить» and at once «Начать»: the first page dissolves into the page about the data for 225 ms, its «Начать» still under the
     * fade and still pressed — the tap does not take the model, already on the fourth page, past it unread (3.33: «Пропустить» leads to
     * it so it is read at least once). The screen moves here, frame by frame.
     */
    @Test
    fun aTapRightAfterSkipDoesNotLeaveThePageAboutTheDataUnread() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.WELCOME)
        show()
        val start = center(button(word(START)).bounds())
        val skip = center(button(word(SKIP)).bounds())
        compose.mainClock.autoAdvance = false
        living = true
        frames(SOME_FRAMES)
        tapAt(skip)
        frames(TAP_FRAMES)
        tapAt(start)
        living = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(
            "only «Пропустить», from the first page",
            listOf<OnboardingIntent>(OnboardingIntent.SkipClicked(OnboardingStep.WELCOME)),
            intents.filter { it is OnboardingIntent.SkipClicked || it is OnboardingIntent.PrimaryClicked },
        )
        assertEquals(OnboardingStep.DATA, state.step)
    }

    // ---- the lead's check of stage 120: two taps before the next frame

    /**
     * Two taps of «Понятно» that both land before the next frame (found by the lead on the emulator: `adb shell 'input tap X Y; input
     * tap X Y'`, 30–60 ms apart, the microphone allowed — the onboarding went past «Микрофон» to «Эталон»): the second reaches the same
     * button, which still shows its page, and the hold of [aSecondTapOfGotItDoesNotAskTheSystemBeforeTheHintIsRead] is not there yet —
     * it starts with the frame that composes the new step. The press carries the step of its button (5.29 R8): «Микрофон» comes, and
     * the system is not asked. With the screen moving and with the animations removed.
     */
    @Test
    fun twoTapsOfGotItBeforeTheNextFrameLandOnTheMicrophone() {
        micAllowed = true
        twoTapsBeforeTheNextFrame(OnboardingStep.DATA, GOT_IT, OnboardingStep.MICROPHONE)
    }

    /** The same on «Эталон»: «Допуск» comes, the onboarding does not finish with it unseen — the one button of the setup says its step. */
    @Test
    fun twoTapsOfNextOnTheReferenceBeforeTheNextFrameLandOnTheTolerance() =
        twoTapsBeforeTheNextFrame(OnboardingStep.REFERENCE_PITCH, NEXT, OnboardingStep.TOLERANCE)

    /** The same on the first page: the second page comes, not the third. */
    @Test
    fun twoTapsOfStartBeforeTheNextFrameLandOnTheSecondPage() =
        twoTapsBeforeTheNextFrame(OnboardingStep.WELCOME, START, OnboardingStep.LIVE)

    /**
     * «Пропустить» and «Начать» before the next frame: «Начать» still says the first page, and the page about the data stays in view —
     * it is read at least once (3.33). With the screen moving and with the animations removed.
     */
    @Test
    fun skipAndStartBeforeTheNextFrameLeaveThePageAboutTheDataInView() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.WELCOME)
        show()
        listOf(false, true).forEach { moving ->
            fresh(OnboardingStep.WELCOME)
            val skip = center(button(word(SKIP)).bounds())
            val start = center(button(word(START)).bounds())
            beforeTheNextFrame(moving) {
                tapInstantly(skip)
                tapInstantly(start)
            }
            val presses = intents.filter { it is OnboardingIntent.SkipClicked || it is OnboardingIntent.PrimaryClicked }
            assertEquals(
                "both taps reached their buttons (moving $moving)",
                listOf(OnboardingIntent.SkipClicked(OnboardingStep.WELCOME), OnboardingIntent.PrimaryClicked(OnboardingStep.WELCOME)),
                presses,
            )
            assertEquals("the page about the data (moving $moving)", OnboardingStep.DATA, state.step)
        }
    }

    /**
     * Two taps of [button] on [from], both before the next frame, with the screen still and moving: both reach the button — no hold is
     * there yet — and the step after is [next], never the one after it; the system is not asked, the onboarding does not finish.
     */
    private fun twoTapsBeforeTheNextFrame(from: OnboardingStep, button: String, next: OnboardingStep) {
        windowSize = UPRIGHT
        state = stateOf(from)
        show()
        listOf(false, true).forEach { moving ->
            fresh(from)
            val finger = center(button(word(button)).bounds())
            beforeTheNextFrame(moving) {
                tapInstantly(finger)
                tapInstantly(finger)
            }
            assertEquals("both taps reached «${word(button)}» (moving $moving)", 2, intents.count { it is OnboardingIntent.PrimaryClicked })
            assertEquals("the next step, not the one after it (moving $moving)", next, state.step)
            assertEquals("the system not asked (moving $moving)", 0, asks)
            assertEquals("not finished (moving $moving)", 0, finishes)
        }
    }

    /** [step] in view and settled — its hold let go — and nothing pressed yet. */
    private fun fresh(step: OnboardingStep) {
        state = stateOf(step)
        compose.waitForIdle()
        intents.clear()
        asks = 0
        finishes = 0
    }

    /** [taps] with the clock standing — no frame runs between them — the screen [moving] or still; then everything settles. */
    private fun beforeTheNextFrame(moving: Boolean, taps: () -> Unit) {
        compose.mainClock.autoAdvance = false
        living = moving
        frames(SOME_FRAMES)
        taps()
        frames(SOME_FRAMES)
        living = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    // ---- the verifier of the lead's fix: a finger, and back, while the model moves the pager

    /**
     * A second tap of «Начать» while the second page slides in — after the frame that started the slide, as the 30–60 ms of the lead's
     * taps on a phone (the verifier of the lead's fix): the pager took the tap — a pager on its way takes a finger before what is on it —
     * stopped the page an eighth of the way and sent it back to the first, while the model stood on the second: «Начать» then carried
     * the first page and moved nothing, dead until a swipe. While the model drives the pager it takes no finger (5.29 R8): the tap
     * reaches the button under it, the page slides on and stands with the model, and its «Дальше» answers once the time of a double tap
     * has passed. With the screen moving and with the animations removed.
     */
    @Test
    fun aSecondTapWhileThePageSlidesDoesNotSendItBack() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.WELCOME)
        show()
        listOf(false, true).forEach { moving ->
            fresh(OnboardingStep.WELCOME)
            val finger = center(button(word(START)).bounds())
            compose.mainClock.autoAdvance = false
            living = moving
            frames(SOME_FRAMES)
            tapInstantly(finger)
            frames(SLIDE_FRAMES)
            tapInstantly(finger)
            frames(SOME_FRAMES)
            living = false
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertStripSays(OnboardingStep.LIVE, "moving $moving, the model on ${state.step}")
            val next = button(word(NEXT)).bounds()
            assertEquals("the model on the second page (moving $moving)", OnboardingStep.LIVE, state.step)
            tapAt(center(next))
            compose.waitForIdle()
            assertEquals("its «${word(NEXT)}» answers (moving $moving)", OnboardingStep.JOURNEY, state.step)
        }
    }

    /**
     * A finger that lands on the first page while «Пропустить» dissolves it and drags it — past the moment of the jump, back, and up
     * (the verifier of the lead's fix): the pager took it, the jump was refused, and the first page stood in view with the model on the
     * fourth — «Начать» and «Пропустить» both dead. While the model drives the pager it takes no finger: the jump is made, the page about
     * the data stands with the model on it, and its «Понятно» answers. The screen moves here, frame by frame.
     */
    @Test
    fun aFingerOnThePageDoesNotRefuseTheJumpOfSkip() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.WELCOME)
        show()
        val skip = center(button(word(SKIP)).bounds())
        val words = center(compose.onNodeWithText(word(WELCOME_TEXT)).bounds())
        val drag = window().width / DRAG_SHARE
        compose.mainClock.autoAdvance = false
        living = true
        frames(SOME_FRAMES)
        tapInstantly(skip)
        frames(SOME_FRAMES)
        touch { at -> down(at(words)); moveBy(Offset(-drag.toPx(), 0f)) }
        // past the jump, which comes when the first page has dissolved (225 ms)
        compose.mainClock.advanceTimeBy(DISSOLVE_MS)
        touch { moveBy(Offset(drag.toPx(), 0f)); up() }
        living = false
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertStripSays(OnboardingStep.DATA, "after the jump, the model on ${state.step}")
        val gotIt = button(word(GOT_IT)).bounds()
        assertEquals("the model on the page about the data", OnboardingStep.DATA, state.step)
        tapAt(center(gotIt))
        compose.waitForIdle()
        assertEquals("its «${word(GOT_IT)}» answers", OnboardingStep.MICROPHONE, state.step)
    }

    /**
     * Back while the second page slides in (found with the verifier's finding; older than stage 120): the slide cut short before half-way
     * left the pager where it stood — the first page shifted by an eighth — and past half-way its stop was told to the model as a swipe,
     * which took the model to the second page again and undid the back. Back drives the pager to the first page from wherever the slide
     * stood, and a stop of a pager the model drives is not told. The screen moves here, frame by frame; the slide cut before and past
     * half-way.
     */
    @Test
    fun backWhileThePageSlidesBringsTheFirstPageBackWhole() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.WELCOME)
        show()
        listOf(SLIDE_FRAMES, PAST_HALF_FRAMES).forEach { slide ->
            fresh(OnboardingStep.WELCOME)
            val finger = center(button(word(START)).bounds())
            compose.mainClock.autoAdvance = false
            living = true
            frames(SOME_FRAMES)
            tapInstantly(finger)
            frames(slide)
            onIntent(OnboardingIntent.BackPressed)
            frames(SOME_FRAMES)
            living = false
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals("back stands, the slide cut $slide frames after the tap", OnboardingStep.WELCOME, state.step)
            assertStripSays(OnboardingStep.WELCOME, "the slide cut $slide frames after the tap")
            // whole in the window: the pager stands on the first page, not between two
            button(word(START))
        }
    }

    /**
     * The model jumps to the page about the data while the second page slides in — «Пропустить» pressed once the time of a double tap
     * has passed, while the spring of the slide still settles its last pixel (it runs some 370 ms; here the slide is cut earlier, before
     * and past half-way, so that it stops on the first page and on the second): the slide cut short stands still through the first half
     * of the dissolve, and its stop, told to the model as a swipe — past half-way the settled page was told so before — took the model
     * off the fourth page and the jump with it. A stop of a pager the model drives is not told: the jump is made.
     */
    @Test
    fun aJumpWhileThePageSlidesIsNotUndoneByTheStopOfTheSlide() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.WELCOME)
        show()
        listOf(SLIDE_FRAMES, PAST_HALF_FRAMES).forEach { slide ->
            fresh(OnboardingStep.WELCOME)
            val finger = center(button(word(START)).bounds())
            compose.mainClock.autoAdvance = false
            living = true
            frames(SOME_FRAMES)
            tapInstantly(finger)
            frames(slide)
            onIntent(OnboardingIntent.SkipClicked(OnboardingStep.LIVE))
            frames(SOME_FRAMES)
            living = false
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals("the jump stands, the slide cut $slide frames after the tap", OnboardingStep.DATA, state.step)
            assertStripSays(OnboardingStep.DATA, "the slide cut $slide frames after the tap")
            button(word(GOT_IT))
        }
    }

    /**
     * Upright in a window lower than 520 dp (320 × 544 less its bars, a phone split in two) the picture gives way to the words but keeps
     * the corner of «Пропустить»: the strip stands under the button, never under its letters — at the font 1 and 1.5 (the review of
     * stage 120: the strip stood at 16–31 dp, inside the 8–56 of the button).
     */
    @Test
    fun uprightInAWindowLowerThan520TheStripStandsUnderSkip() {
        windowSize = LOW_UPRIGHT
        state = stateOf(OnboardingStep.JOURNEY)
        show()
        listOf(1f, LARGE_FONT).forEach { scale ->
            fontScale = scale
            compose.waitForIdle()
            val skip = button(word(SKIP)).bounds()
            val strip = strip(OnboardingStep.JOURNEY).bounds()
            assertTrue("at $scale the strip $strip under «${word(SKIP)}» $skip", strip.top >= skip.bottom - 0.5.dp)
        }
    }

    /**
     * fr on 320 × 544 at the font 1.5 (the review of stage 120): «Intermédiaire» does not stand beside «±12 cts» even at 13 sp — the
     * numbers go under the captions, beside the bars, and the names stand whole on one line. The names and the numbers are silent; a test
     * finds them by their tags.
     */
    @Test
    fun onASmallPhoneAtALargeFontTheNamesOfTheCardsStayWholeInFrench() {
        speaking("fr")
        cardsOnASmallPhone(LARGE_FONT, numbersAtTheEnd = false)
    }

    /**
     * ru on the same phone at the font 1.3: «Средний» stands beside «±8 ц» at 17 sp, the bars under the captions, the numbers at the end
     * — where it fits, «±N ц» stays where 3.36.8 puts it.
     */
    @Test
    fun onASmallPhoneAtALargeFontTheNumbersOfTheCardsStayAtTheEndInRussian() {
        speaking("ru")
        cardsOnASmallPhone(1.3f, numbersAtTheEnd = true)
    }

    private fun cardsOnASmallPhone(scale: Float, numbersAtTheEnd: Boolean) {
        fontScale = scale
        windowSize = LOW_UPRIGHT
        state = stateOf(OnboardingStep.TOLERANCE)
        show()
        val names = compose.onAllNodesWithTag(NAME_TAG, useUnmergedTree = true)
        val numbers = compose.onAllNodesWithTag(NUMBER_TAG, useUnmergedTree = true)
        names.assertCountEquals(TolerancePreset.entries.size)
        numbers.assertCountEquals(TolerancePreset.entries.size)
        val line = NAME_LINE * scale
        TolerancePreset.entries.indices.forEach { index ->
            val name = names[index].bounds()
            val number = numbers[index].bounds()
            assertTrue("the name of card ${index + 1} on one line: $name, a line is $line", name.height <= line + 1.dp)
            if (numbersAtTheEnd) {
                assertTrue("the number of card ${index + 1} at the end, right of the name: $number, $name", number.left >= name.right)
            } else {
                assertTrue("the number of card ${index + 1} under the name: $number, $name", number.top >= name.bottom)
            }
        }
    }

    /**
     * Lying on 640 × 360 the label of the strip never breaks a word (the review of stage 120: «ЗНАКОМСТВ / О» at the font 1.5) and the
     * title stands 12 under it (5.29 R8; it stood 9 under, and against it where the label went on two lines), on all seven screens: at
     * the font 1 the label stands on one line beside «Пропустить», at 1.3 the count goes under the part, at 1.5 smaller, at 2.0 the
     * strip goes under the button. The label is at most two of its lines of 16 sp at 12 sp: a broken part word would make three.
     */
    @Test
    fun lyingTheLabelOfTheStripBreaksNoWordAndTheTitleStands12UnderItInRussian() {
        speaking("ru")
        labelLying(listOf(1f, 1.3f, LARGE_FONT, LARGEST_FONT))
    }

    /** The same in German: «EINRICHTUNG» beside «Überspringen» — at 1.5 the label steps down, at 2.0 the strip goes under. */
    @Test
    fun lyingTheLabelOfTheStripBreaksNoWordAndTheTitleStands12UnderItInGerman() {
        speaking("de")
        labelLying(listOf(1.3f, LARGE_FONT, LARGEST_FONT))
    }

    private fun labelLying(scales: List<Float>) {
        windowSize = LOW_LYING
        state = stateOf(OnboardingStep.WELCOME)
        show()
        scales.forEach { scale ->
            fontScale = scale
            OnboardingStep.entries.forEach { step ->
                state = stateOf(step)
                compose.waitForIdle()
                val strip = strip(step).bounds()
                val heading = compose.onNodeWithText(word(titleOf(step))).bounds()
                assertNear("at $scale on ${step.number} the title under the label", strip.bottom + PROGRESS_TO_TITLE, heading.top)
                val twoLines = STRIP_OVER_LABEL + LABEL_LINE * scale * 2
                assertTrue("at $scale on ${step.number} the label on two lines at most: $strip", strip.height <= twoLines + 1.dp)
                if (OnboardingFlow.canSkip(step)) {
                    val skip = button(word(SKIP)).bounds()
                    assertTrue("at $scale on ${step.number} the strip $strip beside «${word(SKIP)}» $skip or under it", strip.right <= skip.left + 0.5.dp || strip.top >= skip.bottom - 0.5.dp)
                }
            }
        }
    }

    /** The title of [step], the first words under the strip. */
    private fun titleOf(step: OnboardingStep): String = when (step) {
        OnboardingStep.WELCOME -> APP_NAME
        OnboardingStep.LIVE -> LIVE_TITLE
        OnboardingStep.JOURNEY -> JOURNEY_TITLE
        OnboardingStep.DATA -> DATA_TITLE
        OnboardingStep.MICROPHONE -> MIC_TITLE
        OnboardingStep.REFERENCE_PITCH -> A4_TITLE
        OnboardingStep.TOLERANCE -> TOLERANCE_TITLE
    }

    /**
     * Lying at the font 1.5 in English «INTRODUCTION · 4 OF 7» goes on two lines and «SETUP · 5 OF 7» would stand on one: the strip takes
     * the lines of the wider label on all seven screens, so it is as high and as placed on the page about the data and on «Микрофон», and
     * the title stands where it stood (3.36.8: «при переходе в настройку не прыгает»).
     */
    @Test
    fun lyingTheStripDoesNotJumpIntoTheSetupAtALargeFont() {
        speaking("en")
        fontScale = LARGE_FONT
        windowSize = LOW_LYING
        state = stateOf(OnboardingStep.DATA)
        show()
        val data = strip(OnboardingStep.DATA).bounds()
        state = stateOf(OnboardingStep.MICROPHONE)
        compose.waitForIdle()
        val microphone = strip(OnboardingStep.MICROPHONE).bounds()
        assertNear("as high", data.height, microphone.height, 0.5.dp)
        assertNear("where it was", data.top, microphone.top, 0.5.dp)
        assertNear("as long", data.width, microphone.width, 0.5.dp)
    }

    /**
     * As «Эталон» opens lying on 640 × 360 — 603 × 308 and 603 × 336 — its four buttons stand whole above the fade of the edge of the
     * words, before any scroll (the fault of the «было» shots: only the top rim of the selector showed; after stage 120 its bottom still
     * went under the edge): they stand right under the title, the words of the step under them.
     */
    @Test
    fun lyingTheButtonsOfTheReferenceStandWholeAsTheStepOpens() {
        windowSize = LOW_LYING
        state = stateOf(OnboardingStep.REFERENCE_PITCH)
        show()
        listOf(LOW_LYING, LOW_LYING_NO_STATUS_BAR).forEach { size ->
            windowSize = size
            compose.waitForIdle()
            val fade = button(word(NEXT)).bounds().top - GAP_OVER_BUTTON - SCROLL_FADE
            val title = compose.onNodeWithText(word(A4_TITLE)).bounds()
            listOf(HZ_440, HZ_443).forEach { key ->
                val hz = compose.onNodeWithContentDescription(word(key)).bounds()
                assertTrue("«${word(key)}» whole above the fade at $fade at $size: $hz", hz.bottom <= fade + 0.5.dp && hz.top >= title.bottom)
            }
        }
    }

    /** Upright the order of the spec stays: the title, «Частота ноты A4…», the buttons. */
    @Test
    fun uprightTheButtonsOfTheReferenceStandUnderItsWords() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.REFERENCE_PITCH)
        show()
        val text = compose.onNodeWithText(word(A4_TEXT)).bounds()
        val hz = compose.onNodeWithContentDescription(word(HZ_440)).bounds()
        assertNear("the buttons 18 under the words", text.bottom + A4_TOP, hz.top)
    }

    /**
     * As «Микрофон» opens lying on 640 × 360 the hint «Любой ответ ведёт дальше» stands whole over «Разрешить микрофон» — read before
     * the system asks (3.36.8); the words scroll over it. Before, it stood past the edge of the words.
     */
    @Test
    fun lyingTheHintOfTheMicrophoneStandsOverTheButtonAsTheStepOpens() {
        windowSize = LOW_LYING
        state = stateOf(OnboardingStep.MICROPHONE)
        micAllowed = false
        show()
        listOf(LOW_LYING, LOW_LYING_NO_STATUS_BAR).forEach { size ->
            windowSize = size
            compose.waitForIdle()
            val cta = button(word(MIC_CTA)).bounds()
            val strip = strip(OnboardingStep.MICROPHONE).bounds()
            val hint = compose.onNode(hasText(word(MIC_HINT), substring = true)).bounds()
            val title = compose.onNodeWithText(word(MIC_TITLE)).bounds()
            assertTrue("the hint $hint over the button $cta at $size", hint.bottom <= cta.top - GAP_OVER_BUTTON + 0.5.dp)
            assertTrue("the hint $hint under the title $title at $size", hint.top >= title.bottom)
            assertTrue("the title under the strip at $size", title.top >= strip.bottom - 0.5.dp)
        }
    }

    /** Upright, where all of it stands, the hint stays right under the words of the step (its card 14 under them, its words 12 inside). */
    @Test
    fun uprightTheHintOfTheMicrophoneStandsUnderItsWords() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.MICROPHONE)
        micAllowed = false
        show()
        val text = compose.onNodeWithText(word(MIC_TEXT)).bounds()
        val hint = compose.onNode(hasText(word(MIC_HINT), substring = true)).bounds()
        assertNear("the hint under the words", text.bottom + HINT_TOP + HINT_FIELD, hint.top)
    }

    /**
     * The strip of the introduction (decisions 3 and 10 of stage 120): it fills when the page has stopped — «Дальше» moves the step at
     * once, the strip says «1 из 7» while the page slides and «2 из 7» once it stands; «Пропустить» from the second page to the fourth is
     * at once «4 из 7», under the dissolve, while the second page is still the one in view. And it is heard first, before the words of
     * the page under it (`traversalIndex` −1). The screen moves here, frame by frame.
     */
    @Test
    fun theStripFillsWhenThePageHasStoppedAndAtOnceWhereSkipGoes() {
        windowSize = UPRIGHT
        state = stateOf(OnboardingStep.WELCOME)
        show()
        assertEquals("heard first", HEARD_FIRST, strip(OnboardingStep.WELCOME).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex))
        compose.mainClock.autoAdvance = false
        living = true
        frames(SOME_FRAMES)
        // «Дальше»: the view model moves the step at once; the page slides
        state = stateOf(OnboardingStep.LIVE)
        frames(SOME_FRAMES)
        compose.onAllNodesWithContentDescription(word(STRIP + 1)).assertCountEquals(1)
        compose.onAllNodesWithContentDescription(word(STRIP + 2)).assertCountEquals(0)
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onAllNodesWithContentDescription(word(STRIP + 2)).assertCountEquals(1)
        // «Пропустить» from the second page: the strip goes to the fourth at once, the page is still the second under the dissolve
        state = stateOf(OnboardingStep.DATA)
        frames(SOME_FRAMES)
        compose.onAllNodesWithContentDescription(word(STRIP + 4)).assertCountEquals(1)
        compose.onNodeWithText(word(LIVE_TEXT)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onAllNodesWithContentDescription(word(STRIP + 4)).assertCountEquals(1)
        living = false
        compose.mainClock.autoAdvance = true
    }

    /**
     * The words that go on past the edge of their scroll fade into the ground there (5.29 R8, decision 1 of stage 120), lying on
     * 640 × 360 — in the setup and in the introduction, each its own scroll: a card of «Допуск» and a plate of the fourth page are
     * brought 30 dp over the bottom edge — at the edge the pixel is the ground, 3 dp over the fade it is the card's or the plate's own;
     * scrolled to its end, the top edge fades over the card under it instead. The pixels of a card are taken in the gap between its
     * radio and its words, where nothing is drawn on it.
     */
    @Test
    fun lyingTheWordsFadeIntoTheGroundAtTheEdgePastWhichTheyGoOn() {
        windowSize = LOW_LYING
        state = stateOf(OnboardingStep.TOLERANCE, tolerance = TolerancePreset.BEGINNER)
        show()
        assertNotEquals("a card is not the ground", colors.card, colors.ground)
        // the setup: the first card whose top is not over the edge yet, brought to it — the chosen one is on the soft accent
        val cards = listOf(CARD_BEGINNER to colors.accentSoft, CARD_INTERMEDIATE to colors.card, CARD_PRO to colors.card)
        val scroll = wordsScroll(said(CARD_BEGINNER))
        var view = scroll.bounds()
        val (card, cardColor) = cards.first { (key, _) -> said(key).top >= view.bottom - OVER_EDGE }
        scrollBy(scroll, said(card).top - (view.bottom - OVER_EDGE))
        val brought = said(card)
        assertNear("the card 30 dp over the edge", view.bottom - OVER_EDGE, brought.top)
        val x = brought.left + CARD_GAP_X
        assertColorNear("the edge over the card", colors.ground, pixel(x, view.bottom - EDGE_PIXEL))
        assertColorNear("over the fade the card is its own", cardColor, pixel(x, view.bottom - SCROLL_FADE - ABOVE_FADE))
        // scrolled to its end: the top edge fades over the card under it
        compose.onNodeWithText(word(TOLERANCE_CAPTION)).performScrollTo()
        view = scroll.bounds()
        val under = cards.map { (key, _) -> said(key) }.first { it.top < view.top && it.bottom > view.top + EDGE_PIXEL * 2 }
        assertColorNear("the top edge over the card", colors.ground, pixel(under.left + CARD_GAP_X, view.top + EDGE_PIXEL))
        // the introduction: the first plate of a row not over the edge yet, brought to it — the plate of the copy is the accent
        state = stateOf(OnboardingStep.DATA)
        compose.waitForIdle()
        val page = wordsScroll(compose.onNode(hasText(word(ROW_COPY), substring = true)).bounds())
        view = page.bounds()
        val rows = listOf(ROW_COPY to colors.accent, ROW_TAKES to colors.accentSoft, ROW_ANALYTICS to colors.accentSoft)
        val (row, plateColor) = rows.first { (lead, _) -> plateTop(lead) >= view.bottom - OVER_EDGE }
        scrollBy(page, plateTop(row) - (view.bottom - OVER_EDGE))
        assertNear("the plate 30 dp over the edge", view.bottom - OVER_EDGE, plateTop(row))
        val plateX = compose.onNode(hasText(word(row), substring = true)).bounds().left - ROW_GAP - ROW_PLATE_LYING / 2
        assertColorNear("the edge over the plate", colors.ground, pixel(plateX, view.bottom - EDGE_PIXEL))
        assertColorNear("over the fade the plate is its own", plateColor, pixel(plateX, view.bottom - SCROLL_FADE - ABOVE_FADE))
    }

    /** Where the card that says [key] stands. */
    private fun said(key: String): DpRect = compose.onNodeWithContentDescription(word(key)).bounds()

    /** Where the plate of the row that starts with [lead] stands lying: its words start 7 under its top (5.29 R8). */
    private fun plateTop(lead: String): Dp = compose.onNode(hasText(word(lead), substring = true)).bounds().top - ROW_TEXT_TOP_LYING

    /** Scrolls [scroll] down by [distance], as a reader's scroll does, and lets it settle. */
    private fun scrollBy(scroll: SemanticsNodeInteraction, distance: Dp) {
        assertTrue("a scroll down, not up: $distance", distance >= 0.dp)
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { by -> with(compose.density) { by(0f, distance.toPx()) } }
        compose.waitForIdle()
    }

    private companion object {
        /** A phone upright: 412 × 892 less its status bar. */
        val UPRIGHT = DpSize(412.dp, 868.dp)

        /** 360 × 640 upright less its status bar and gesture bar. */
        val SMALL_UPRIGHT = DpSize(360.dp, 592.dp)

        /** 320 × 544 (`wm size 720x1224`, `wm density 360`) less its bars: lower than 520, narrower than 380. */
        val LOW_UPRIGHT = DpSize(320.dp, 492.dp)

        /** 892 × 412 lying: what its bars leave. */
        val LYING = DpSize(848.dp, 360.dp)

        /** The emulator's 640 × 360 lying: 603 × 308 behind its cutout, its status bar and its gesture bar. */
        val LOW_LYING = DpSize(603.dp, 308.dp)

        /** …and without its status bar. */
        val LOW_LYING_NO_STATUS_BAR = DpSize(603.dp, 336.dp)

        /** The title 12 under the label of the strip; «Пока играете…» 6 over the button; the scroll ends 12 over the button lying. */
        val PROGRESS_TO_TITLE = 12.dp
        val FOOT_TO_BUTTON = 6.dp
        val GAP_OVER_BUTTON = 12.dp

        /** The least height of a button of the reference and of a card of the tolerance (spec 5.29 R8). */
        val A4_LEAST = 64.dp
        val CARD_LEAST = 76.dp

        /** The buttons of the reference 18 under the words; the card of a hint 14 under what it explains, its words 12 inside it. */
        val A4_TOP = 18.dp
        val HINT_TOP = 14.dp
        val HINT_FIELD = 12.dp

        /** The words of a scroll fade into the ground over its last 24 dp (5.29 R8); the pixel of the edge, and one 3 dp over the fade. */
        val SCROLL_FADE = 24.dp
        val EDGE_PIXEL = 0.5.dp
        val ABOVE_FADE = 3.dp

        /** In a card of the tolerance the gap between the radio and the words: its field 16, the radio 22, half the gap of 14. */
        val CARD_GAP_X = 45.dp

        /** A row of the introduction lying: its plate 36, 14 to its words, which start 7 under its top. */
        val ROW_PLATE_LYING = 36.dp
        val ROW_GAP = 14.dp
        val ROW_TEXT_TOP_LYING = 7.dp

        /** A card or a plate is brought this far over the bottom edge: 3 dp of it over the fade, the rest under it and past the edge. */
        val OVER_EDGE = 30.dp

        /**
         * The strip: segments of 6 and the label 8 under them; a line of the label 16 sp at 12 sp — exactly: the label's lines are
         * `ExactLines` (on Android a line of material3's style is padded back to Manrope's own 1.37 em: 56 px for a line of 54.6 at 1.3,
         * the label of two lines 1.1 dp taller than its lines — the lead's check of stage 120); the name of a card in lines of 22.
         */
        val STRIP_OVER_LABEL = 14.dp
        val LABEL_LINE = 16.dp
        val NAME_LINE = 22.dp

        /** The tags of the silent name and «±8 ц» of a card of the tolerance (`SettingsControls.kt`). */
        const val NAME_TAG = "tolerance name"
        const val NUMBER_TAG = "tolerance number"

        /** The traversal index of the strip: heard before the words of the page under it. */
        const val HEARD_FIRST = -1f

        /**
         * A large font and the largest of Android 14. The window of the tests turns sp into dp as a phone does: Compose's
         * `Density(density, fontScale)` on Android is nonlinear from 1.05 (16 sp at 1.3 is 20.2 dp, not 20.8). Up to 12 sp the tables
         * of Android 14 are linear still (12 sp at 1.3 is 15.6 dp), and a line keeps its share of its size (16 sp of a text of 12 sp at
         * 1.3 — 20.8 dp): the line of the label at 12 sp is 16 sp × the scale.
         */
        const val LARGE_FONT = 1.5f
        const val LARGEST_FONT = 2f

        /** A few frames; the frames between two taps of a finger; long enough for a page or a dissolve to settle. */
        const val SOME_FRAMES = 3
        const val TAP_FRAMES = 2
        const val SETTLE_MS = 1_500L
        const val DISSOLVE_MS = 400L

        /**
         * The frames after a press by which the page is on its way, and by which it is past half-way: the frame that composes the step,
         * the first of the slide, then 16 ms each — the spring of the pager (stiffness 1500, no bounce) is an eighth of the way after one,
         * half after 43 ms, seven tenths after four.
         */
        const val SLIDE_FRAMES = 3
        const val PAST_HALF_FRAMES = 6

        /** A finger drags the page a third of the window: far past the slop of a drag. */
        const val DRAG_SHARE = 3

        /** How far a channel of a pixel may stand from the colour it is told by: the edge of a gradient is not exactly the ground. */
        const val PIXEL_SLACK = 0.035f

        const val STRIP = "strip"
        const val LIVE_TITLE = "live title"
        const val JOURNEY_TITLE = "journey title"
        const val DATA_TITLE = "data title"
        const val APP_NAME = "app name"
        const val WELCOME_TEXT = "welcome text"
        const val START = "start"
        const val LINK = "link"
        const val NEXT = "next"
        const val LIVE_TEXT = "live text"
        const val LIVE_FOOT = "live foot"
        const val MIC_CTA = "mic cta"
        const val MIC_HINT = "mic hint"
        const val A4_TITLE = "a4 title"
        const val A4_HINT = "a4 hint"
        const val HZ_440 = "440"
        const val HZ_443 = "443"
        const val TOLERANCE_TITLE = "tolerance title"
        const val TOLERANCE_CAPTION = "tolerance caption"
        const val TOLERANCE_CTA = "tolerance cta"
        const val CARD_BEGINNER = "card beginner"
        const val CARD_INTERMEDIATE = "card intermediate"
        const val CARD_PRO = "card pro"
        const val SKIP = "skip"
        const val GOT_IT = "got it"
        const val MIC_TITLE = "mic title"
        const val MIC_TEXT = "mic text"
        const val A4_TEXT = "a4 text"
        const val ROW_COPY = "row copy"
        const val ROW_TAKES = "row takes"
        const val ROW_ANALYTICS = "row analytics"
    }
}
