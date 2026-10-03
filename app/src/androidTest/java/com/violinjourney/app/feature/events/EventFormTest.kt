package com.violinjourney.app.feature.events

import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.form.EventFormIntent
import com.violinjourney.app.feature.events.form.EventFormReducer
import com.violinjourney.app.feature.events.form.EventFormScreen
import com.violinjourney.app.feature.events.form.EventFormSheetCard
import com.violinjourney.app.feature.events.form.EventFormState
import com.violinjourney.app.feature.events.form.FormDraft
import com.violinjourney.app.feature.events.form.FormSheet
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.event_duration_none
import com.violinjourney.app.shared.resources.event_duration_other
import com.violinjourney.app.shared.resources.event_end_next_day
import com.violinjourney.app.shared.resources.event_field_date
import com.violinjourney.app.shared.resources.event_field_duration
import com.violinjourney.app.shared.resources.event_field_teacher
import com.violinjourney.app.shared.resources.event_field_time
import com.violinjourney.app.shared.resources.event_field_title
import com.violinjourney.app.shared.resources.event_kind_lesson
import com.violinjourney.app.shared.resources.event_kind_performance
import com.violinjourney.app.shared.resources.event_kind_tile_said
import com.violinjourney.app.shared.resources.event_repeat_until_link
import com.violinjourney.app.shared.resources.event_repeat_weekly
import com.violinjourney.app.shared.resources.event_weekdays_on
import com.violinjourney.app.shared.resources.field_counter_said_many
import com.violinjourney.app.shared.resources.form_optional
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.practice_save
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The form of an event (spec 3.36.9, 5.29 R9) where its layout is the risk: the date and the time side by side on a phone, 8 apart, their
 * widths 1.2 : 1 — one under the other where a value would not stand whole in its share (320 at the font 1.3); the tiles of the kinds 80
 * wide, their names whole at 1.3 — fr «Prestation», de «Unterricht»; a tile a radio button that says its kind; the chips of the length
 * whole on 360 at 1.3, the pencil of «Другая…» among them; on 360 × 640 the title in focus and «Сохранить» over the keyboard; in the window
 * of the emulator lying, 603 × 308, «Сохранить» 48; the counters from 60 of the title and 45 of the place, said in words; the days of
 * «Повторять до» before the first event asleep. Laid out in a window of its own size ([TestWindow]); the words read in the composition,
 * in the language the screen speaks; the keyboard is the insets a real one hands the window, given to the view of the composition.
 */
@RunWith(AndroidJUnit4::class)
class EventFormTest {
    @get:Rule
    val compose = createComposeRule()

    /** The language of the device and of the formats, given back after every test. */
    private val deviceLanguage: Locale = Locale.getDefault()
    private val formatLanguage: FormatLanguage = Formats.language

    @After
    fun backToTheLanguageOfTheDevice() {
        Locale.setDefault(deviceLanguage)
        Formats.use(formatLanguage)
    }

    /** The words and the formats of [tag]: the dates of the rows speak it too. */
    private fun inLanguage(tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        Formats.use(tag)
    }

    private val config = EventsConfig()
    private val kinds = KindRules.all(emptyList(), config)
    private val today = LocalDate(2026, 9, 27)
    private val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    private val intents = mutableListOf<EventFormIntent>()
    private val words = mutableMapOf<String, String>()

    /** The view of the composition: the window hands its insets — a keyboard among them — to it. */
    private lateinit var view: View

    private fun word(key: String): String = words.getValue(key)

    /** A new weekly lesson on Monday 28 September at 17:00 for 45 minutes. */
    private fun draft(title: String = "", place: String = "") =
        FormDraft(lesson, MONDAY, 17 * 60, 45, Repeat.WEEKLY, until = null, place = place, title = title, notes = "")

    private fun state(draft: FormDraft = draft(), sheet: FormSheet? = null): EventFormState = EventFormReducer.stateOf(
        draft = draft, sheet = sheet, dialog = null, loading = false, isNew = true, edited = null, editedSeries = null,
        initialDate = draft.date, events = emptyList(), kinds = kinds, today = today, focusNotes = false, config = config,
    )

    /**
     * The form of [state] in a window of [size] at [fontScale]: a field may take the focus — no real keyboard comes up for it, the input
     * method is intercepted, and the keyboard is only what [keyboard] hands the window.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun show(state: EventFormState, size: DpSize, fontScale: Float = 1f) {
        compose.setContent {
            view = LocalView.current
            words[DATE] = stringResource(Res.string.practice_pair_description, stringResource(Res.string.event_field_date), Formats.weekdayFullDate(MONDAY))
            words[TIME] = stringResource(Res.string.practice_pair_description, stringResource(Res.string.event_field_time), Formats.clockOf(17 * 60))
            words[LESSON_TILE] = stringResource(Res.string.event_kind_tile_said, stringResource(Res.string.event_kind_lesson))
            words[PERFORMANCE_TILE] = stringResource(Res.string.event_kind_tile_said, stringResource(Res.string.event_kind_performance))
            words[SAVE] = stringResource(Res.string.practice_save)
            words[OTHER_LENGTH] = stringResource(Res.string.event_duration_other)
            words[NO_LENGTH] = stringResource(Res.string.event_duration_none)
            words[TITLE] = stringResource(Res.string.event_field_title) + ", " + stringResource(Res.string.form_optional)
            words[TEACHER] = stringResource(Res.string.event_field_teacher) + ", " + stringResource(Res.string.form_optional)
            words[TITLE_COUNTER] = stringResource(Res.string.field_counter_said_many, TITLE_FROM, config.maxTitleLength)
            words[TITLE_COUNTER_BEFORE] = stringResource(Res.string.field_counter_said_many, TITLE_FROM - 1, config.maxTitleLength)
            words[PLACE_COUNTER] = stringResource(Res.string.field_counter_said_many, PLACE_FROM, config.maxPlaceLength)
            words[PLACE_COUNTER_BEFORE] = stringResource(Res.string.field_counter_said_many, PLACE_FROM - 1, config.maxPlaceLength)
            ViolinTheme {
                TestWindow(size, fontScale = fontScale) {
                    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                        EventFormScreen(state = state, onIntent = { intents += it })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** What the window hands its views while the keyboard is up: the keyboard alone, [height] from the bottom; none at 0. */
    private fun keyboard(height: Dp) {
        val bottom = with(compose.density) { height.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    /** Past the 150 ms the keyboard stands still for, and the scroll that brings the field in focus up. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    /** A row of choice says its caption and its value as one button. */
    private fun row(key: String) = compose.onNode(hasContentDescription(word(key)) and hasClickAction())

    private fun saveButton() = compose.onNode(hasText(word(SAVE)) and hasClickAction())

    @Test
    fun onAPhoneTheDateAndTheTimeStandSideBySideTheDateWider() {
        show(state(), DpSize(412.dp, 892.dp))
        val date = row(DATE).getUnclippedBoundsInRoot()
        val time = row(TIME).getUnclippedBoundsInRoot()
        assertEquals("in one row", date.top.value, time.top.value, 0.5f)
        assertEquals("8 apart", 8f, (time.left - date.right).value, 1f)
        assertEquals("1.2 : 1", 1.2f, date.width / time.width, 0.02f)
        assertTrue("56 high: ${date.height}", date.height >= 56.dp - 0.5.dp)
    }

    /** 320 at 1.3: the share of the date (153 dp) does not hold «Пн, 28 сент.» of 21 sp with its calendar — one under the other. */
    @Test
    fun onANarrowPhoneAtALargeFontTheDateAndTheTimeStandOneUnderTheOther() {
        show(state(), DpSize(320.dp, 640.dp), fontScale = 1.3f)
        val date = row(DATE).getUnclippedBoundsInRoot()
        val time = row(TIME).getUnclippedBoundsInRoot()
        assertEquals("at the same edge", date.left.value, time.left.value, 0.5f)
        assertEquals("as wide", date.width.value, time.width.value, 0.5f)
        assertEquals("the time 8 under the date", 8f, (time.top - date.bottom).value, 1f)
    }

    /** fr «Prestation» and de «Unterricht» — the long names of the built-in kinds — whole on a tile of 80 at 1.3. */
    private fun assertTheTilesKeepTheirNamesWhole(language: String) {
        inLanguage(language)
        show(state(), DpSize(360.dp, 640.dp), fontScale = 1.3f)
        listOf(LESSON_TILE, PERFORMANCE_TILE).forEach { key ->
            val tile = compose.onNode(hasContentDescription(word(key)) and isSelectable()).getUnclippedBoundsInRoot()
            assertEquals("$language: ${word(key)} 80 wide", 80f, tile.width.value, 0.5f)
            assertTrue("$language: ${word(key)} at least 80 high: ${tile.height}", tile.height >= 80.dp - 0.5.dp)
            val name = compose.onNode(hasContentDescription(word(key)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            assertWordsWhole(name, "$language: ${word(key)}")
        }
    }

    @Test
    fun inFrenchAtALargeFontTheTilesKeepTheirNamesWhole() = assertTheTilesKeepTheirNamesWhole("fr")

    @Test
    fun inGermanAtALargeFontTheTilesKeepTheirNamesWhole() = assertTheTilesKeepTheirNamesWhole("de")

    @Test
    fun aTileIsARadioButtonThatSaysItsKindAndAPressChoosesIt() {
        show(state(), DpSize(412.dp, 892.dp))
        compose.onNode(hasContentDescription(word(LESSON_TILE)) and isSelectable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNode(hasContentDescription(word(PERFORMANCE_TILE)) and isSelectable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .performClick()
        compose.runOnIdle { assertEquals(EventFormIntent.KindPicked(KindRef.BuiltIn(BuiltInKind.PERFORMANCE)), intents.last()) }
    }

    /** The chips of the length on 360 at 1.3: each whole on its line — «Другая…» with its pencil too; the slot of 48 is ControlsTouchTest's. */
    @Test
    fun onASmallPhoneAtALargeFontTheChipsOfTheLengthStandWhole() {
        show(state(), DpSize(360.dp, 640.dp), fontScale = 1.3f)
        (config.quickDurationsMinutes.map { Formats.quickDuration(it) } + listOf(word(OTHER_LENGTH), word(NO_LENGTH))).forEach { text ->
            compose.onNode(hasText(text) and isSelectable()).performScrollTo()
            assertWholeOnOneLine(compose.onNode(hasText(text), useUnmergedTree = true), text)
        }
        compose.onNode(hasText(word(OTHER_LENGTH)) and isSelectable()).performClick()
        compose.runOnIdle { assertEquals(EventFormIntent.DurationOtherClicked, intents.last()) }
    }

    /**
     * 360 × 640 with a keyboard of 260 (spec 3.36.4 «Клавиатура», the frame of R4): the title in focus comes up whole over the bottom zone,
     * and «Сохранить» — 56, the room between the bar and the keyboard being more than 200 — stands right over the keyboard.
     */
    @Test
    fun onASmallPhoneTheTitleInFocusAndSaveStandOverTheKeyboard() {
        show(state(), DpSize(360.dp, 640.dp))
        val title = compose.onNode(hasSetTextAction() and hasText(word(TITLE)))
        // focus as a tap on the field gives it: a tap at its centre after performScrollTo, which scrolls it only into the viewport,
        // would land on the pinned zone of «Сохранить» over the end of that viewport
        title.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        title.assertIsFocused()
        keyboard(KEYBOARD)
        settle()
        val window = compose.onNodeWithTag(TEST_WINDOW).getUnclippedBoundsInRoot()
        val keyboardTop = window.bottom - KEYBOARD
        val save = saveButton().getUnclippedBoundsInRoot()
        assertTrue("«Сохранить» over the keyboard: ${save.bottom}, the keyboard at $keyboardTop", save.bottom <= keyboardTop + 1.dp)
        assertEquals("a button of 56", 56f, save.height.value, 1f)
        assertWholeOver(title, save.top, "the title in focus")
    }

    /** Nothing of [field] cut by the edges of the column it scrolls in, and all of it over [limit]. */
    private fun assertWholeOver(field: SemanticsNodeInteraction, limit: Dp, what: String) {
        val whole = field.getUnclippedBoundsInRoot()
        val seen = field.getBoundsInRoot()
        assertEquals("$what whole: ${seen.height} seen of ${whole.height}", whole.height.value, seen.height.value, 1f)
        assertTrue("$what over $limit: ${seen.bottom}", seen.bottom <= limit + 1.dp)
    }

    /** In the window of the emulator lying, 603 × 308: no higher than 360 — the button of the zone is 48 (3.36.1, item 5). */
    @Test
    fun inTheWindowOfTheEmulatorLyingSaveIs48() {
        show(state(), DpSize(603.dp, 308.dp))
        assertEquals("a button of 48", 48f, saveButton().getUnclippedBoundsInRoot().height.value, 1f)
    }

    /**
     * The counter of the title comes at 60 of its 80 characters, of the place — the teacher of a lesson — at 45 of its 60 (decision 36),
     * said in words: one character short of either, none; typed up to it, there. A field owns its text, so the characters are typed.
     */
    @Test
    fun theCountersComeNearTheirLimitsAndAreSaidInWords() {
        show(state(draft(title = "а".repeat(TITLE_FROM - 1), place = "б".repeat(PLACE_FROM - 1))), DpSize(412.dp, 892.dp))
        compose.onNode(hasText(word(TITLE_COUNTER_BEFORE)), useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText(word(PLACE_COUNTER_BEFORE)), useUnmergedTree = true).assertDoesNotExist()

        compose.onNode(hasSetTextAction() and hasText(word(TITLE))).performTextInput("а")
        compose.onNode(hasSetTextAction() and hasText(word(TEACHER))).performTextInput("б")
        compose.waitForIdle()
        compose.onNode(hasText(word(TITLE_COUNTER)), useUnmergedTree = true).assertExists()
        compose.onNode(hasText(word(PLACE_COUNTER)), useUnmergedTree = true).assertExists()
        compose.runOnIdle {
            assertEquals("а".repeat(TITLE_FROM), intents.filterIsInstance<EventFormIntent.TitleChanged>().last().text)
            assertEquals("б".repeat(PLACE_FROM), intents.filterIsInstance<EventFormIntent.PlaceChanged>().last().text)
        }
    }

    /** «Повторять до» (spec 3.36.9): the days before the first event of the repeat are 0.38 and do not answer; its first day does. */
    @Test
    fun theDaysBeforeTheFirstEventOfTheEndOfARepeatSleep() {
        val sheet = FormSheet.Until(YearMonth(2026, 9), null)
        compose.setContent {
            words[BEFORE] = Formats.weekdayFullDate(LocalDate(2026, 9, 21))
            words[FIRST] = Formats.weekdayFullDate(MONDAY)
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp)) { EventFormSheetCard(state(sheet = sheet), onIntent = { intents += it }) }
            }
        }
        compose.waitForIdle()
        val before = compose.onNode(hasContentDescription(word(BEFORE), substring = true) and isSelectable())
        before.assertIsNotEnabled()
        before.performClick()
        compose.runOnIdle { assertTrue("a sleeping day answers nothing: $intents", intents.none { it is EventFormIntent.UntilPicked }) }
        compose.onNode(hasContentDescription(word(FIRST), substring = true) and isSelectable()).performClick()
        compose.runOnIdle { assertEquals(EventFormIntent.UntilPicked(MONDAY), intents.last()) }
    }

    /**
     * A kind of one's own chosen — the fifth tile, past the edge of a phone of 360 (16 + 4 × 88 = 368): the row comes to it, the tile
     * stands whole in the window (review of stage 98б). On the code before, it stood at 368 … 448, out of sight.
     */
    @Test
    fun onAPhoneOf360AKindOfOnesOwnChosenStandsInSight() {
        val own = KindRules.all(listOf(StoredKind.Own(9, SOLFEGE, 1, KindSign.BOOK, 1)), config)
        val chosen = EventFormReducer.stateOf(
            draft = draft().copy(kind = KindRef.Custom(9)), sheet = null, dialog = null, loading = false, isNew = true, edited = null, editedSeries = null,
            initialDate = MONDAY, events = emptyList(), kinds = own, today = today, focusNotes = false, config = config,
        )
        compose.setContent {
            words[OWN_TILE] = stringResource(Res.string.event_kind_tile_said, SOLFEGE)
            ViolinTheme { TestWindow(DpSize(360.dp, 640.dp)) { EventFormScreen(state = chosen, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
        val window = compose.onNodeWithTag(TEST_WINDOW).getUnclippedBoundsInRoot()
        val tile = compose.onNode(hasContentDescription(word(OWN_TILE)) and isSelectable()).getUnclippedBoundsInRoot()
        assertTrue("the chosen tile in the window: ${tile.left} … ${tile.right} of ${window.left} … ${window.right}", tile.left >= window.left - 0.5.dp && tile.right <= window.right + 0.5.dp)
    }

    /**
     * The value of the row of the time is in the accent — «Весь день» too, as «17:00» (events-form.html 1, 3: the class `val` of both).
     * The row says itself as one button, its words are not nodes of their own: its picture is asked — the words drawn in the accent.
     * On the code before, «Весь день» was in the first level of text, and the row had no pixel of the accent.
     */
    @Test
    fun theRowOfTheTimeSaysTheWholeDayInTheAccent() {
        var accent = Color.Unspecified
        compose.setContent {
            words[ALL_DAY] = stringResource(Res.string.practice_pair_description, stringResource(Res.string.event_field_time), stringResource(Res.string.event_all_day))
            ViolinTheme {
                accent = MaterialTheme.colorScheme.primary
                TestWindow(DpSize(412.dp, 892.dp)) { EventFormScreen(state = state(draft().copy(startMinutes = null, durationMinutes = null)), onIntent = { intents += it }) }
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNode(hasContentDescription(word(ALL_DAY)) and hasClickAction()).captureToImage().toPixelMap()
        val inAccent = (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y -> pixels[x, y].toArgb() == accent.toArgb() } }
        assertTrue("the words of «${word(ALL_DAY)}» in the accent: $inAccent pixels", inAccent >= ACCENT_PIXELS)
    }

    /** An edit still being read has no kind yet: the bar says nothing rather than the placeholder's «Урок» (3.36.9 «Загрузка правки»). */
    @Test
    fun whileAnEditIsReadTheBarSaysNoKind() {
        val loading = EventFormReducer.stateOf(
            draft = draft(), sheet = null, dialog = null, loading = true, isNew = false, edited = null, editedSeries = null,
            initialDate = MONDAY, events = emptyList(), kinds = kinds, today = today, focusNotes = false, config = config,
        )
        compose.setContent {
            words[LESSON] = stringResource(Res.string.event_kind_lesson)
            ViolinTheme { TestWindow(DpSize(412.dp, 892.dp)) { EventFormScreen(state = loading, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
        compose.onNode(hasText(word(LESSON)), useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * 320 at 1.3, a lesson at 23:30 for 1 h 30 min: the end «до 01:00, вт 29 сент.» does not fit the row beside «Длительность» — the
     * caption stands whole, the end wraps at its right by its words. On the code before, the end took its room first and «Длительность»
     * broke inside the word (the review measured 115 dp left for its 123).
     */
    @Test
    fun onANarrowPhoneAtALargeFontTheCaptionOfTheLengthStandsWhole() {
        val late = state(draft().copy(startMinutes = 23 * 60 + 30, durationMinutes = 90, repeat = Repeat.NONE))
        compose.setContent {
            words[LENGTH] = stringResource(Res.string.event_field_duration)
            words[END] = stringResource(Res.string.event_end_next_day, Formats.clockOf(60), Formats.weekdayDate(LocalDate(2026, 9, 29)))
            ViolinTheme { TestWindow(DpSize(320.dp, 640.dp), fontScale = 1.3f) { EventFormScreen(state = late, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
        assertWholeOnOneLine(compose.onNode(hasText(word(LENGTH)), useUnmergedTree = true), word(LENGTH))
        assertWordsWhole(compose.onNode(hasText(word(END)), useUnmergedTree = true), word(END))
    }

    /**
     * de on 360 at 1.3, weekly up to 31 December: «montags · bis 31. Dez. · 14 Unterrichtsstunden» is one sentence that wraps by its
     * words. On the code before, three texts in a row left «montags · » some 35 dp, and the weekday broke by the letter.
     */
    @Test
    fun inGermanAtALargeFontTheLineOfARepeatWrapsByItsWords() {
        inLanguage("de")
        val until = state(draft().copy(until = LocalDate(2026, 12, 31)))
        compose.setContent {
            words[WEEKDAY] = stringArrayResource(Res.array.event_weekdays_on).first()
            ViolinTheme { TestWindow(DpSize(360.dp, 640.dp), fontScale = 1.3f) { EventFormScreen(state = until, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
        assertWordsWhole(compose.onNode(hasText(word(WEEKDAY), substring = true), useUnmergedTree = true), "de: the line of the repeat")
    }

    /**
     * The line of the repeat stands 8 under the switch with its link «до…» as without it (an event of a repeat): the link does not grow
     * the line — on the code before, its 48 put the line 15 lower. Its touch is still 48: a tap 5 dp over the words reaches it, the
     * widening of Compose given back to this window (the test window takes it away).
     */
    @Test
    fun theLineOfARepeatStandsUnderTheSwitchAsAnEditsDoesAndItsLinkIsTouchedFrom48() {
        val repeatOf = EventSeries(1, lesson, Repeat.WEEKLY, MONDAY, null, MONDAY, 17 * 60, 45, "", "")
        val event = CalendarEvent(1, lesson, MONDAY, 17 * 60, 45, "", "", "", seriesId = 1, detached = false, createdAtEpochMs = 0)
        val edit = EventFormReducer.stateOf(
            draft = draft(), sheet = null, dialog = null, loading = false, isNew = false, edited = event, editedSeries = repeatOf,
            initialDate = MONDAY, events = listOf(event), kinds = kinds, today = today, focusNotes = false, config = config,
        )
        var shown by mutableStateOf(state())
        compose.setContent {
            words[WEEKDAY] = stringArrayResource(Res.array.event_weekdays_on).first()
            words[WEEKLY] = stringResource(Res.string.event_repeat_weekly)
            words[LINK] = stringResource(Res.string.event_repeat_until_link)
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp)) {
                    val base = LocalViewConfiguration.current
                    val widened = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize(48.dp, 48.dp) } }
                    CompositionLocalProvider(LocalViewConfiguration provides widened) { EventFormScreen(state = shown, onIntent = { intents += it }) }
                }
            }
        }
        compose.waitForIdle()
        fun gap(): Float {
            val segment = compose.onNode(hasText(word(WEEKLY)) and isSelectable()).getUnclippedBoundsInRoot()
            val line = compose.onNode(hasText(word(WEEKDAY), substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
            return (line.top - segment.bottom).value
        }
        val withLink = gap()
        val line = compose.onNode(hasText(word(WEEKDAY), substring = true), useUnmergedTree = true)
        val layout = line.textLayout()
        val at = layout.layoutInput.text.text.indexOf(word(LINK))
        assertTrue("the link in the line", at >= 0)
        val first = layout.getBoundingBox(at)
        val last = layout.getBoundingBox(at + word(LINK).length - 1)
        line.performTouchInput { click(Offset((first.left + last.right) / 2, first.top - 5.dp.toPx())) }
        compose.runOnIdle { assertEquals("a tap 5 dp over «${word(LINK)}»", EventFormIntent.UntilClicked, intents.last()) }

        compose.runOnIdle { shown = edit }
        compose.waitForIdle()
        assertEquals("the line with its link where the line of an edit stands", gap(), withLink, 0.5f)
    }

    /** «Время» of a performance 18:30 for 1 h 30 min: under the wheels «до 20:00 · 1 ч 30 мин», the time in the first level, 700. */
    @Test
    fun theEndUnderTheWheelsSaysItsTimeInBold() {
        val sheet = FormSheet.Time(18 * 60 + 30, allDay = false)
        compose.setContent {
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp)) {
                    EventFormSheetCard(state(draft().copy(startMinutes = 18 * 60 + 30, durationMinutes = 90), sheet = sheet), onIntent = { intents += it })
                }
            }
        }
        compose.waitForIdle()
        val end = compose.onNode(hasText("20:00", substring = true), useUnmergedTree = true).fetchSemanticsNode()
        val text = end.config[SemanticsProperties.Text].first()
        val at = text.text.indexOf("20:00")
        assertTrue(
            "«20:00» of «${text.text}» in 700: ${text.spanStyles}",
            text.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start <= at && it.end >= at + 5 },
        )
    }

    private companion object {
        val MONDAY = LocalDate(2026, 9, 28)
        const val DATE = "date"
        const val TIME = "time"
        const val LESSON_TILE = "lessonTile"
        const val PERFORMANCE_TILE = "performanceTile"
        const val SAVE = "save"
        const val OTHER_LENGTH = "otherLength"
        const val NO_LENGTH = "noLength"
        const val TITLE = "title"
        const val TEACHER = "teacher"
        const val TITLE_COUNTER = "titleCounter"
        const val TITLE_COUNTER_BEFORE = "titleCounterBefore"
        const val PLACE_COUNTER = "placeCounter"
        const val PLACE_COUNTER_BEFORE = "placeCounterBefore"
        const val BEFORE = "before"
        const val FIRST = "first"
        const val OWN_TILE = "ownTile"
        const val ALL_DAY = "allDay"
        const val LESSON = "lesson"
        const val LENGTH = "length"
        const val END = "end"
        const val WEEKDAY = "weekday"
        const val WEEKLY = "weekly"
        const val LINK = "link"
        const val SOLFEGE = "Сольфеджио"

        /** The full pixels of a word of 16 sp / 800 drawn in one colour: far more than this. */
        const val ACCENT_PIXELS = 20
        const val TITLE_FROM = 60
        const val PLACE_FROM = 45

        /** Gboard upright on a phone of 360 × 640 is some 260 dp. */
        val KEYBOARD = 260.dp
        const val SETTLE_MS = 600L
    }
}
