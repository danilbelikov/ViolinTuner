package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.DayEvent
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.SelectedDay
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.event_kind_lesson
import com.violinjourney.app.shared.resources.event_repeat_weekly_short
import com.violinjourney.app.shared.resources.event_time_range
import com.violinjourney.app.shared.resources.practice_day_add
import com.violinjourney.app.shared.resources.practice_day_edit
import com.violinjourney.app.shared.resources.practice_day_events
import com.violinjourney.app.shared.resources.practice_day_no_events
import com.violinjourney.app.shared.resources.practice_day_no_events_text
import com.violinjourney.app.shared.resources.practice_day_time_later
import com.violinjourney.app.shared.resources.practice_day_tomorrow
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.testing.TestWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «События» of the sheet of the day (spec 3.36.9 «Лист дня: события», 5.29 R9; the review of stage 97): a day gone by keeps its time and
 * «Изменить», and under them the heading «События» and a row an event in the order of the day — «весь день» first — each one
 * description of its parts in the order they are seen, the whole row of at least 64; a day to come has no time and nothing to edit —
 * «Время появится, когда день наступит.», the chip «завтра» for tomorrow, its rows right under it without a heading; a day to come
 * without events says «Событий нет» and what a day holds. Laid out in a window of its own size ([TestWindow]); the words read in the
 * composition.
 */
@RunWith(AndroidJUnit4::class)
class DaySheetEventsTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = TimeZone.of("Europe/Moscow")
    private val intents = mutableListOf<PracticeIntent>()

    /** Strings of the whole day, then the lesson at 17:00 with Анна Сергеевна every week — the order of the day (5.28). */
    private val events = listOf(
        DayEvent(4, KindLook(KindSign.OTHER, 7), EventName.Titled("Замена струн"), startMinutes = null, endMinutes = null, place = "", repeat = Repeat.NONE),
        DayEvent(
            1, KindLook(KindSign.LESSON, 0), EventName.OfKind(KindRef.BuiltIn(BuiltInKind.LESSON), null), startMinutes = 17 * 60, endMinutes = 17 * 60 + 45,
            place = "Анна Сергеевна", repeat = Repeat.WEEKLY,
        ),
    )

    /** Saturday 12 September, gone by — 45 minutes played; tomorrow, 18 September; a day to come further on, 21 November. */
    private val past = SelectedDay(LocalDate(2026, 9, 12), isToday = false, totalMs = 45 * MS_PER_MINUTE, sessions = emptyList(), events = events)
    private val tomorrow = SelectedDay(LocalDate(2026, 9, 18), isToday = false, totalMs = 0, sessions = emptyList(), isTomorrow = true, isFuture = true, events = events)
    private val empty = SelectedDay(LocalDate(2026, 11, 21), isToday = false, totalMs = 0, sessions = emptyList(), isFuture = true)

    private var day by mutableStateOf(past)

    private lateinit var words: Words

    /** The words of the sheet in the language of the app, read in the composition. */
    private class Words(
        val strings: String,
        val lesson: String,
        val events: String,
        val edit: String,
        val add: String,
        val later: String,
        val tomorrow: String,
        val noEvents: String,
        val noEventsText: String,
    )

    private fun show() {
        compose.setContent {
            words = Words(
                strings = pair("Замена струн", stringResource(Res.string.event_all_day)),
                lesson = pair(
                    pair(pair(stringResource(Res.string.event_time_range, "17:00", "17:45"), stringResource(Res.string.event_kind_lesson)), "Анна Сергеевна"),
                    stringResource(Res.string.event_repeat_weekly_short),
                ),
                events = stringResource(Res.string.practice_day_events),
                edit = stringResource(Res.string.practice_day_edit),
                add = stringResource(Res.string.practice_day_add),
                later = stringResource(Res.string.practice_day_time_later),
                tomorrow = stringResource(Res.string.practice_day_tomorrow),
                noEvents = stringResource(Res.string.practice_day_no_events),
                noEventsText = stringResource(Res.string.practice_day_no_events_text),
            )
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp)) {
                    Box(Modifier.padding(20.dp)) { DaySheetContent(day, onIntent = { intents += it }, zone = zone) }
                }
            }
        }
        compose.waitForIdle()
    }

    /** «a, b»: two parts of what a reader hears, in the language of the app. */
    @Composable
    private fun pair(a: String, b: String): String = stringResource(Res.string.practice_pair_description, a, b)

    private fun gone(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    /** A day gone by with events: its time and «Изменить», «События» — a heading — and its rows in the order of the day, each one sentence. */
    @Test
    fun aDayGoneByHasItsTimeAndItsEventsUnderAHeading() {
        show()
        compose.onNodeWithText(words.edit).assertExists()
        compose.onNode(hasText(words.events) and isHeading()).assertExists()
        val strings = compose.onNodeWithContentDescription(words.strings).assertHeightIsAtLeast(64.dp).getUnclippedBoundsInRoot()
        val lesson = compose.onNodeWithContentDescription(words.lesson).assertHeightIsAtLeast(64.dp).getUnclippedBoundsInRoot()
        assertTrue("«весь день» first: ${strings.top} over ${lesson.top}", strings.bottom <= lesson.top)
        val heading = compose.onNode(hasText(words.events) and isHeading()).getUnclippedBoundsInRoot()
        assertTrue("the rows under their heading", heading.bottom <= strings.top)
        // the words of a row are not read apart: a reader hears the row
        assertEquals(0, compose.onAllNodes(hasText("Анна Сергеевна", substring = true)).fetchSemanticsNodes().size)
        assertTrue("no line of a day to come", gone(words.later))
    }

    /**
     * Tomorrow (spec 3.36.9: «Пока день будущий, «Изменить» у него нет»): the chip «завтра», «Время появится, когда день наступит.», the
     * rows right under it — no heading «События» — and no «Изменить» or «Добавить»: the time of a day to come is not edited (3.35).
     */
    @Test
    fun aDayToComeHasItsEventsAndNoTimeToEdit() {
        day = tomorrow
        show()
        compose.onNodeWithText(words.tomorrow).assertExists()
        val later = compose.onNodeWithText(words.later).getUnclippedBoundsInRoot()
        val strings = compose.onNodeWithContentDescription(words.strings).getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription(words.lesson).assertExists()
        assertTrue("the rows under «${words.later}»", later.bottom <= strings.top)
        assertTrue("no «${words.edit}»", gone(words.edit))
        assertTrue("no «${words.add}»", gone(words.add))
        assertEquals("no heading «${words.events}»", 0, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes().size)
        assertTrue("nothing it did", intents.isEmpty())
    }

    /** A day to come without events: «Событий нет» and what a day holds; no «Время появится…», nothing to edit. */
    @Test
    fun aDayToComeWithoutEventsSaysSo() {
        day = empty
        show()
        compose.onNodeWithText(words.noEvents).assertExists()
        compose.onNodeWithText(words.noEventsText).assertExists()
        assertTrue("no «${words.later}»", gone(words.later))
        assertTrue("no «${words.edit}»", gone(words.edit))
        assertTrue("no «${words.add}»", gone(words.add))
    }
}
