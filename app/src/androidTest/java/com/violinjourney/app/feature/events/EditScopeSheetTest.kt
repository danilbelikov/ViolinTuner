package com.violinjourney.app.feature.events

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.AffectedDates
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.ChangedField
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.ScopeQuestion
import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.form.ChangeLine
import com.violinjourney.app.feature.events.form.EventFormIntent
import com.violinjourney.app.feature.events.form.EventFormReducer
import com.violinjourney.app.feature.events.form.EventFormSheetCard
import com.violinjourney.app.feature.events.form.FormDraft
import com.violinjourney.app.feature.events.form.FormSheet
import com.violinjourney.app.feature.events.form.Moved
import com.violinjourney.app.feature.events.form.ScopeAsk
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_date_time
import com.violinjourney.app.shared.resources.event_dates_and_on
import com.violinjourney.app.shared.resources.event_field_teacher
import com.violinjourney.app.shared.resources.event_new_from_on_at_2
import com.violinjourney.app.shared.resources.event_rest_on_at_1
import com.violinjourney.app.shared.resources.event_series_edit_q_lesson
import com.violinjourney.app.shared.resources.event_series_following_lesson
import com.violinjourney.app.shared.resources.event_series_label_lesson
import com.violinjourney.app.shared.resources.event_series_move_q_lesson
import com.violinjourney.app.shared.resources.event_series_only_date
import com.violinjourney.app.shared.resources.event_series_this_lesson
import com.violinjourney.app.shared.resources.practice_no_value
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.assertWordsWhole
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Урок повторяется» — the sheet an edit of an event of a repeat asks before it is saved (spec 3.36.9, 5.29 R9; plan D6, D31): the label,
 * the question with the date, the plate of what changes; a new time — «Этот и следующие» filled and first, «19, 26 окт. и дальше» under it,
 * «Только этот урок» an outline under it; a new date — «Перенести урок …», «Только этот урок» filled and first with what becomes of the rest,
 * «Этот и следующие» saying the new repeat; a new step — «Этот и следующие» alone. Each answer at least 60, its words whole — in German on
 * 360 at the font 1.3 too. Laid out in a window of its own size ([TestWindow]); the words read in the composition, in the language of the
 * test and its formats.
 */
@RunWith(AndroidJUnit4::class)
class EditScopeSheetTest {
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

    private val config = EventsConfig()
    private val kinds = KindRules.all(emptyList(), config)
    private val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    private val lessonKind = KindRules.kindOf(lesson, kinds, config)
    private val intents = mutableListOf<EventFormIntent>()
    private val words = mutableMapOf<String, String>()

    private fun word(key: String): String = words.getValue(key)

    /** The lesson of Monday 19 October at 17:00 of a weekly repeat, as it is stored. */
    private val before = EventDraft(lesson, DAY, startMinutes = 17 * 60, durationMinutes = 45, place = "Анна Сергеевна")
    private val following = AffectedDates(listOf(DAY, LocalDate(2026, 10, 26)), andOn = true)

    /** 17:00 → 17:30: «Этот и следующие» filled. */
    private val newTime = ScopeAsk(
        question = ScopeQuestion.Both(EditScope.FOLLOWING), word = SeriesWord.LESSON, date = DAY, moved = null,
        change = ChangeLine(ChangedField.TIME, before, before.copy(startMinutes = 17 * 60 + 30), Repeat.WEEKLY, Repeat.WEEKLY, lessonKind, lessonKind),
        following = following,
    )

    /** To Tuesday 20 October at 18:00: «Только этот урок» filled, the rest stays on Mondays at 17:00. */
    private val newDate = ScopeAsk(
        question = ScopeQuestion.Both(EditScope.ONLY_THIS), word = SeriesWord.LESSON, date = DAY,
        moved = Moved(MOVED_TO, 18 * 60, restWeekday = DayOfWeek.MONDAY, restStartMinutes = 17 * 60),
        change = ChangeLine(
            ChangedField.DATE, before, before.copy(date = MOVED_TO, startMinutes = 18 * 60), Repeat.WEEKLY, Repeat.WEEKLY, lessonKind, lessonKind,
        ),
        following = following,
    )

    /** Every other week from now: «Этот и следующие» alone. */
    private val newStep = newTime.copy(
        question = ScopeQuestion.FollowingOnly,
        change = ChangeLine(ChangedField.REPEAT, before, before, Repeat.WEEKLY, Repeat.BIWEEKLY, lessonKind, lessonKind),
    )

    private fun show(ask: ScopeAsk, size: DpSize = DpSize(412.dp, 892.dp), fontScale: Float = 1f) {
        val draft = FormDraft(lesson, DAY, 17 * 60, 45, Repeat.WEEKLY, null, "Анна Сергеевна", "", "")
        val state = EventFormReducer.stateOf(
            draft = draft, sheet = FormSheet.Scope(ask), dialog = null, loading = false, isNew = false, edited = null, editedSeries = null,
            initialDate = DAY, events = emptyList(), kinds = kinds, today = LocalDate(2026, 10, 12), focusNotes = false, config = config,
        )
        compose.setContent {
            val day = Formats.dayAndMonth(DAY)
            // drawn by SectionLabel, which shows it in capitals
            words[LABEL] = stringResource(Res.string.event_series_label_lesson).uppercase()
            words[EDIT_Q] = stringResource(Res.string.event_series_edit_q_lesson, day)
            words[MOVE_Q] = stringResource(Res.string.event_series_move_q_lesson, day, stringResource(Res.string.event_date_time, Formats.weekdayDate(MOVED_TO), "18:00"))
            words[THIS] = stringResource(Res.string.event_series_this_lesson)
            words[FOLLOWING] = stringResource(Res.string.event_series_following_lesson)
            words[ONLY_DATE] = stringResource(Res.string.event_series_only_date, day)
            words[DATES] = stringResource(Res.string.event_dates_and_on, Formats.dateList(following.dates))
            words[REST] = stringResource(Res.string.event_rest_on_at_1, "17:00")
            words[NEW_REPEAT] = stringResource(Res.string.event_new_from_on_at_2, Formats.shortDate(MOVED_TO), "18:00")
            words[TEACHER] = stringResource(Res.string.event_field_teacher)
            words[NO_VALUE] = stringResource(Res.string.practice_no_value)
            ViolinTheme { TestWindow(size, fontScale = fontScale) { EventFormSheetCard(state, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
    }

    /** An answer: its words and its caption are one button. */
    private fun answer(key: String) = compose.onNode(hasText(word(key), substring = true) and hasClickAction())

    private fun SemanticsNodeInteraction.isAbove(other: SemanticsNodeInteraction): Boolean =
        getUnclippedBoundsInRoot().bottom <= other.getUnclippedBoundsInRoot().top

    @Test
    fun aNewTimeAsksWithThePlateAndFillsThisAndTheFollowing() {
        show(newTime)
        compose.onNodeWithText(word(LABEL)).assertExists()
        compose.onNodeWithText(word(EDIT_Q)).assertExists()
        compose.onNodeWithText("17:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("17:30", useUnmergedTree = true).assertExists()
        val following = answer(FOLLOWING).assertHeightIsAtLeast(60.dp)
        val only = answer(THIS).assertHeightIsAtLeast(60.dp)
        assertTrue("«${word(FOLLOWING)}» first", following.isAbove(only))
        compose.onNodeWithText(word(DATES), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(word(ONLY_DATE), useUnmergedTree = true).assertExists()
        following.performClick()
        compose.runOnIdle { assertEquals(EventFormIntent.ScopeAnswered(EditScope.FOLLOWING), intents.last()) }
    }

    @Test
    fun aNewDateAsksToMoveAndFillsOnlyThisWithWhatBecomesOfTheRest() {
        show(newDate)
        compose.onNodeWithText(word(MOVE_Q)).assertExists()
        val only = answer(THIS)
        val following = answer(FOLLOWING)
        assertTrue("«${word(THIS)}» first", only.isAbove(following))
        compose.onNodeWithText(word(REST), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(word(NEW_REPEAT), useUnmergedTree = true).assertExists()
        only.performClick()
        compose.runOnIdle { assertEquals(EventFormIntent.ScopeAnswered(EditScope.ONLY_THIS), intents.last()) }
    }

    @Test
    fun aNewStepHasOneAnswer() {
        show(newStep)
        answer(FOLLOWING).assertHeightIsAtLeast(60.dp)
        assertTrue("no «${word(THIS)}»", compose.onAllNodesWithText(word(THIS), substring = true).fetchSemanticsNodes().isEmpty())
    }

    /** A teacher given to a lesson of a repeat that had none: «Преподаватель — → Анна Сергеевна». */
    private val newTeacher = newTime.copy(
        change = ChangeLine(ChangedField.PLACE, before.copy(place = ""), before, Repeat.WEEKLY, Repeat.WEEKLY, lessonKind, lessonKind),
    )

    /**
     * The plate on 412: «Анна Сергеевна» whole on one line beside «—», nothing cut (review of stage 98б). On the code before, the two values
     * shared what the caption and the arrow left by halves — 96 dp each — and the new one was cut with an ellipsis at «Анна Серге…».
     */
    @Test
    fun aNewTeacherStandsWholeOnThePlateBesideAShortOldValue() {
        show(newTeacher)
        assertWholeOnOneLine(compose.onNodeWithText(TEACHER_NAME, useUnmergedTree = true), TEACHER_NAME)
        assertWholeOnOneLine(compose.onNodeWithText(word(NO_VALUE), useUnmergedTree = true), word(NO_VALUE))
        assertWholeOnOneLine(compose.onNodeWithText(word(TEACHER), useUnmergedTree = true), word(TEACHER))
    }

    /** On 360 at 1.3 the row cannot hold «Преподаватель» and the values by their words: the caption stands over them, nothing breaks. */
    @Test
    fun onASmallPhoneAtALargeFontNothingOfThePlateBreaks() {
        show(newTeacher, DpSize(360.dp, 640.dp), fontScale = 1.3f)
        assertWordsWhole(compose.onNodeWithText(TEACHER_NAME, useUnmergedTree = true), TEACHER_NAME)
        assertWholeOnOneLine(compose.onNodeWithText(word(TEACHER), useUnmergedTree = true), word(TEACHER))
        assertWholeOnOneLine(compose.onNodeWithText(word(NO_VALUE), useUnmergedTree = true), word(NO_VALUE))
    }

    /** In German on 360 at 1.3 no word of the label, the question or an answer breaks, and nothing is cut. */
    @Test
    fun inGermanOnASmallPhoneAtALargeFontNoWordBreaks() {
        Locale.setDefault(Locale.forLanguageTag("de"))
        Formats.use("de")
        show(newDate, DpSize(360.dp, 640.dp), fontScale = 1.3f)
        listOf(LABEL, MOVE_Q, THIS, REST, FOLLOWING, NEW_REPEAT).forEach { key ->
            assertWordsWhole(compose.onNodeWithText(word(key), useUnmergedTree = true), "de: ${word(key)}")
        }
        answer(THIS).assertHeightIsAtLeast(60.dp)
        answer(FOLLOWING).assertHeightIsAtLeast(60.dp)
    }

    private companion object {
        val DAY = LocalDate(2026, 10, 19)
        val MOVED_TO = LocalDate(2026, 10, 20)
        const val LABEL = "label"
        const val EDIT_Q = "editQuestion"
        const val MOVE_Q = "moveQuestion"
        const val THIS = "this"
        const val FOLLOWING = "following"
        const val ONLY_DATE = "onlyDate"
        const val DATES = "dates"
        const val REST = "rest"
        const val NEW_REPEAT = "newRepeat"
        const val TEACHER = "teacher"
        const val NO_VALUE = "noValue"
        const val TEACHER_NAME = "Анна Сергеевна"
    }
}
