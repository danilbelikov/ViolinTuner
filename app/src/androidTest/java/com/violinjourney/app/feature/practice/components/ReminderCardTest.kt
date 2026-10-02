package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventReminder
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Reminder
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.ui.components.DockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.JourneyWindowCard
import com.violinjourney.app.feature.journey.WindowLook
import com.violinjourney.app.feature.journey.WindowSample
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.feature.practice.PracticeScreen
import com.violinjourney.app.feature.practice.PracticeState
import com.violinjourney.app.feature.practice.WindowFit
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.TopLevelDestination
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.event_kind_lesson_word
import com.violinjourney.app.shared.resources.event_kind_rehearsal_word
import com.violinjourney.app.shared.resources.event_reminder_more
import com.violinjourney.app.shared.resources.event_reminder_more_description_few
import com.violinjourney.app.shared.resources.event_reminder_more_description_many
import com.violinjourney.app.shared.resources.event_reminder_more_description_one
import com.violinjourney.app.shared.resources.event_reminder_tomorrow_at_said
import com.violinjourney.app.shared.resources.event_reminder_tomorrow_said
import com.violinjourney.app.shared.resources.event_reminder_yesterday_at
import com.violinjourney.app.shared.resources.event_reminder_yesterday_at_said
import com.violinjourney.app.shared.resources.event_running_until_said
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.practice_start
import com.violinjourney.app.shared.resources.practice_week
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.yearMonth
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reminder on «Занятия» (spec 3.36.9, 5.29 R9 «Напоминание») where layout and motion are the risk: a row of the full card at least
 * 56 — the whole row, its fields too, is the node a reader hears — and «ещё N» 48, a button; the target of «ещё N» of the compact card
 * 56 wide (52 lying) and as high as its row, never squeezed, its words whole — «3 weitere» at the font 1.3 — and the words of the row
 * the gap of the row (12, lying 10) before the rule in front of it; the first line of a row two lines at most, cut with an ellipsis;
 * the full card in a window of 892 and the compact one on a phone of 360 × 640, whole above the bottom zone with «Сегодня», the window
 * of the home giving way to it first — whole above the zone too where «Сегодня» leaves it room, its line under the card and below the
 * fold where «Сегодня» is full (3.36.9; 5.29 R9, «Уточнено на этапе 97 (ведущий)»). And its movement (the review of stage 97): a card
 * that goes on the open screen fades out in 300 ms
 * holding its words, and the window of the home moves once, when it has faded out; one that changed where the screen did not see it
 * comes and goes at once; one there at the first frame stands without a fade. Laid out in a window of its own size ([TestWindow]); the
 * words read in the composition; the movement frame by frame, the motion of the screen on.
 */
@RunWith(AndroidJUnit4::class)
class ReminderCardTest {
    @get:Rule
    val compose = createComposeRule()

    /** The language of the device, given back after every test. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private val config = EventsConfig()
    private val kinds = KindRules.all(listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1)), config)
    private val zone = TimeZone.of("Europe/Moscow")
    private val today = LocalDate(2026, 9, 27)
    private val tomorrow = LocalDate(2026, 9, 28)

    /** 18:42 on Sunday 27 September, as in the mockups. */
    private val now: Instant = today.atTime(18, 42).toInstant(zone)

    private fun event(
        id: Long,
        start: Int?,
        duration: Int? = null,
        title: String = "",
        place: String = "",
        kind: KindRef = KindRef.BuiltIn(BuiltInKind.LESSON),
        date: LocalDate = tomorrow,
    ) = CalendarEvent(id, kind, date, start, duration, title, place, notes = "", seriesId = null, detached = false, createdAtEpochMs = id)

    /** Tomorrow's lesson at 17:00 with Анна Сергеевна, the orchestra at 11:00 and an exam the whole day — three events. */
    private val three = listOf(
        event(1, 17 * 60, 45, place = "Анна Сергеевна"),
        event(2, 11 * 60, 120, kind = KindRef.Custom(9)),
        event(3, null, title = "Отборочный тур Международного конкурса юных скрипачей имени Л. Когана", kind = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)),
    )

    /** Tomorrow's three with a start: the orchestra at 11:00 is the one of the compact card, «ещё 2» for the lesson and the rehearsal. */
    private val threeAtTimes = listOf(
        event(1, 17 * 60, 45, place = "Анна Сергеевна"),
        event(2, 11 * 60, 120, kind = KindRef.Custom(9)),
        event(3, 19 * 60, kind = KindRef.BuiltIn(BuiltInKind.REHEARSAL)),
    )

    private fun reminderOf(events: List<CalendarEvent>, at: Instant = now): Reminder = EventReminder.of(events, kinds, at, zone, config)!!

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /**
     * The target of «ещё N» of a compact card: the narrowest of the buttons — the row beside it is a button too since stage 98: it
     * opens the screen of its event.
     */
    private fun narrowestButton(): SemanticsNodeInteraction {
        val buttons = compose.onAllNodes(isButton)
        val nodes = buttons.fetchSemanticsNodes()
        return buttons[nodes.indices.minBy { nodes[it].boundsInRoot.width }]
    }

    /**
     * «Занятия» on the Sunday of the mockups: 40 minutes on each day of September in [days] — by default the week of the mockups, so
     * «Сегодня» is full: today's time, «Неделя» and the chip of the streak — and the reminder of [events].
     */
    private fun practiceState(events: List<CalendarEvent>, days: IntRange = 21..27): PracticeState {
        val entries = days.map { PracticeEntry(LocalDate(2026, 9, it), startedAtEpochMs = 0, durationMs = 40 * MS_PER_MINUTE, manual = false) }
        return PracticeReducer.stateOf(
            entries = entries, sessions = emptyList(), runningSince = null, month = today.yearMonth, selectedDate = null, sheet = null,
            today = today, zone = zone, config = PracticeConfig(), trophies = emptyList(), profile = Profile("Аня", avatarFile = null),
            avatarPath = null, progressConfig = ProgressConfig(), events = events, kinds = KindRules.ordered(kinds), now = now, eventsConfig = config,
        )
    }

    @Test
    fun aRowOfTheFullCardIsAtLeast56AndMoreIs48AndAButton() {
        var exam = ""
        var orchestra = ""
        var lesson = ""
        var more = ""
        var moreSaid = ""
        compose.setContent {
            // the sentences TalkBack hears of the three rows, in the language of the app: a kind of one's own is its name as written
            exam = stringResource(
                Res.string.practice_pair_description,
                stringResource(Res.string.event_reminder_tomorrow_said, three.last().title),
                stringResource(Res.string.event_all_day),
            )
            orchestra = stringResource(Res.string.event_reminder_tomorrow_at_said, Formats.clockOf(11 * 60), "Оркестр")
            lesson = stringResource(
                Res.string.practice_pair_description,
                stringResource(Res.string.event_reminder_tomorrow_at_said, Formats.clockOf(17 * 60), stringResource(Res.string.event_kind_lesson_word)),
                "Анна Сергеевна",
            )
            more = stringResource(Res.string.event_reminder_more, 1)
            val hidden = Formats.plural(1, Res.string.event_reminder_more_description_one, Res.string.event_reminder_more_description_few, Res.string.event_reminder_more_description_many)
            moreSaid = stringResource(hidden, 1, stringResource(Res.string.event_kind_lesson_word))
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp)) {
                    Box(Modifier.padding(16.dp)) {
                        ReminderCard(reminderOf(three), compact = false, lying = false, onMore = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        // «весь день» first, then 11:00: the lesson at 17:00 is the third, under «ещё 1»
        compose.onAllNodesWithText(more, useUnmergedTree = true).fetchSemanticsNodes().let { assertTrue("«$more» is drawn", it.isNotEmpty()) }
        // a row is a button since stage 98 — it opens the screen of its event — and «ещё 1» is one of its own
        val rows = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) and isButton and !hasContentDescription(moreSaid)).fetchSemanticsNodes()
        assertEquals("two rows of the full card", 2, rows.size)
        assertEquals(
            "the exam of the whole day, then the orchestra at 11:00 — each one sentence",
            listOf(listOf(exam), listOf(orchestra)), rows.map { it.config[SemanticsProperties.ContentDescription] },
        )
        // the node a reader hears is the whole row, its fields of 8 too: the orchestra on one line is 56, not the 40 of its words
        with(compose.density) {
            rows.forEach { row -> assertTrue("a row of at least 56: ${row.size.height.toDp()}", row.size.height.toDp() >= 56.dp - 0.5.dp) }
        }
        compose.onNode(hasContentDescription(moreSaid) and isButton).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        assertTrue("the lesson is under «ещё», not a row", compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(lesson))).fetchSemanticsNodes().isEmpty())
    }

    /**
     * A row opens the screen of its event (spec 3.36.9, stage 98): in the full card every row, in the compact one its row — beside
     * «ещё N», which opens the sheet of the day; «ещё N» opens no event.
     */
    @Test
    fun aRowOpensTheScreenOfItsEvent() {
        val opened = mutableListOf<Long>()
        val days = mutableListOf<LocalDate>()
        var orchestra = ""
        var compact by mutableStateOf(false)
        compose.setContent {
            orchestra = stringResource(Res.string.event_reminder_tomorrow_at_said, Formats.clockOf(11 * 60), "Оркестр")
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp)) {
                    Box(Modifier.padding(16.dp)) {
                        ReminderCard(reminderOf(threeAtTimes), compact = compact, lying = false, onMore = { days += it }, onOpen = { opened += it })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(orchestra).assert(isButton).assertHasClickAction().performClick()
        assertEquals(listOf(2L), opened)

        compact = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription(orchestra).assertHasClickAction().performClick()
        assertEquals("the row of the compact card opens its event too", listOf(2L, 2L), opened)
        narrowestButton().performClick()
        assertEquals("«ещё N» opens the sheet of their day, not an event", listOf(2L, 2L), opened)
        assertEquals(listOf(tomorrow), days)
    }

    /**
     * The target of «ещё N» of the compact card: 56 wide upright, 52 lying, as high as its row and at least 56 — never squeezed; the
     * words of the row end the gap of the row before the rule in front of it — 12, lying 10 (events-views.html, 2), not against it.
     */
    @Test
    fun theTargetOfTheCompactCardIs56WideAnd52LyingAndAtLeast56High() {
        var lying by mutableStateOf(false)
        compose.setContent {
            ViolinTheme {
                TestWindow(DpSize(360.dp, 640.dp)) {
                    Box(Modifier.padding(16.dp)) {
                        ReminderCard(reminderOf(three), compact = true, lying = lying, onMore = {}, modifier = Modifier.testTag(CARD))
                    }
                }
            }
        }
        for ((sideways, width, gap) in listOf(Triple(false, 56.dp, 12.dp), Triple(true, 52.dp, 10.dp))) {
            lying = sideways
            compose.waitForIdle()
            val target = narrowestButton().assertHasClickAction().getUnclippedBoundsInRoot()
            assertEquals("lying $sideways: the target is $width wide", width.value, target.width.value, 0.5f)
            assertTrue("lying $sideways: the target is at least 56 high: ${target.height}", target.height >= 56.dp - 0.5.dp)
            val card = compose.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
            assertEquals("lying $sideways: the target runs to the right edge of the card", card.right.value, target.right.value, 0.5f)
            // the room of the words: the column the first line was laid out in, from its start
            val words = compose.onAllNodesWithText(three.last().title, substring = true, useUnmergedTree = true)[0]
            val room = with(compose.density) { words.textLayout().layoutInput.constraints.maxWidth.toDp() }
            val wordsEnd = words.getUnclippedBoundsInRoot().left + room
            assertEquals("lying $sideways: the words end $gap before the rule of 1 before the target", (target.left - RULE - gap).value, wordsEnd.value, 0.5f)
        }
    }

    /**
     * At the font 1.3 on 360 in [language]: the first line of a row two lines at most, cut with an ellipsis where the title is longer;
     * «ещё 2» of the compact target in its words whole — «2 weitere» on two lines at the space rather than broken, «2 de plus» too — and
     * the target keeps its 56.
     */
    private fun assertWordsAtTheFont1_3(language: String) {
        Locale.setDefault(Locale.forLanguageTag(language))
        var more = ""
        compose.setContent {
            more = stringResource(Res.string.event_reminder_more, 2)
            ViolinTheme {
                TestWindow(DpSize(360.dp, 640.dp), fontScale = 1.3f) {
                    Column(Modifier.padding(16.dp)) {
                        // the exam of the whole day is first: its long title is the first line of either card
                        ReminderCard(reminderOf(three), compact = true, lying = false, onMore = {})
                        ReminderCard(reminderOf(three.drop(2)), compact = false, lying = false, onMore = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        val title = three.last().title
        val firstLines = compose.onAllNodesWithText(title, substring = true, useUnmergedTree = true)
        val count = firstLines.fetchSemanticsNodes().size
        assertEquals("$language: the first line of each card", 2, count)
        for (index in 0 until count) {
            val layout = firstLines[index].textLayout()
            assertTrue("$language: the first line takes ${layout.lineCount} lines", layout.lineCount <= 2)
            assertTrue("$language: a longer title is cut with an ellipsis", layout.isLineEllipsized(layout.lineCount - 1))
        }
        assertWordsWhole(compose.onNodeWithText(more, useUnmergedTree = true), "$language: $more")
        val target = narrowestButton().getUnclippedBoundsInRoot()
        assertEquals("$language: the target keeps its 56", 56f, target.width.value, 0.5f)
    }

    @Test
    fun atTheFont1_3TheWordsStandInRussian() = assertWordsAtTheFont1_3("ru")

    @Test
    fun atTheFont1_3TheWordsStandInGerman() = assertWordsAtTheFont1_3("de")

    @Test
    fun atTheFont1_3TheWordsStandInFrench() = assertWordsAtTheFont1_3("fr")

    @Test
    fun atTheFont1_3TheWordsStandInItalian() = assertWordsAtTheFont1_3("it")

    /**
     * 360 × 640 (spec 3.36.9: the compact card at a window lower than 700) with «Сегодня» full, as the mockups have it — today's time,
     * «Неделя» and the chip of the streak — on a phone under its bars with the tabs under it ([SmallPhone]). «Сегодня» and the card of
     * tomorrow's events stand whole above the bottom zone; the window of the home gives way first (5.29 R9, «Уточнено на этапе 97
     * (ведущий)»): it is its line, its smallest form, 12 under the card — and here the line may go on below the fold: the top field,
     * the path row, «Сегодня», the card, the line and their gaps are some 481 on a first screen of 456. The card is the compact one:
     * the orchestra at 11:00 alone — not the lesson at 17:00 and its teacher — and the target of «ещё 2», 56 wide.
     */
    @Test
    fun onA360By640PhoneAFullTodayAndTheCompactCardStandAboveTheBottomZone() {
        var start = ""
        var orchestra = ""
        var week = ""
        var lesson = ""
        var more = ""
        val state = practiceState(threeAtTimes)
        compose.setContent {
            start = stringResource(Res.string.practice_start)
            orchestra = stringResource(Res.string.event_reminder_tomorrow_at_said, Formats.clockOf(11 * 60), "Оркестр")
            week = stringResource(Res.string.practice_week)
            val lessonWord = stringResource(Res.string.event_kind_lesson_word)
            lesson = stringResource(
                Res.string.practice_pair_description, stringResource(Res.string.event_reminder_tomorrow_at_said, Formats.clockOf(17 * 60), lessonWord), "Анна Сергеевна",
            )
            val hidden = Formats.plural(2, Res.string.event_reminder_more_description_one, Res.string.event_reminder_more_description_few, Res.string.event_reminder_more_description_many)
            more = stringResource(hidden, 2, stringResource(Res.string.practice_pair_description, lessonWord, stringResource(Res.string.event_kind_rehearsal_word)))
            ViolinTheme { SmallPhone(state) }
        }
        compose.waitForIdle()
        val zoneTop = zoneTop(start)
        // the card, its field of 4 around its row (5.29 R9)
        val row = compose.onNodeWithContentDescription(orchestra).getUnclippedBoundsInRoot()
        val cardTop = row.top - CARD_FIELD
        val cardBottom = row.bottom + CARD_FIELD
        assertTrue("the compact card on the first screen, above the zone at $zoneTop: $cardBottom", cardBottom <= zoneTop + 0.5.dp)
        // «Сегодня» whole over the card, so over the zone too: «Неделя», its last line — it is full here
        val weekLine = compose.onNodeWithText(week).getUnclippedBoundsInRoot()
        assertTrue("«Сегодня» over the card: «$week» ends at ${weekLine.bottom}, the card begins at $cardTop", weekLine.bottom <= cardTop - BLOCK_GAP + 0.5.dp)
        // the window has given way as far as it goes — its line, 12 under the card; below the fold here, as the spec lets it
        val window = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        assertTrue("the window of the home is its line, not a picture: ${window.height}", window.height < WindowFit.PictureMin)
        assertEquals("the line 12 under the card", (cardBottom + BLOCK_GAP).value, window.top.value, 0.5f)
        assertTrue("the compact card has one row: no lesson", compose.onAllNodesWithContentDescription(lesson).fetchSemanticsNodes().isEmpty())
        assertTrue("no second line: no teacher", compose.onAllNodesWithText("Анна Сергеевна", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        val target = compose.onNode(hasContentDescription(more) and isButton).getUnclippedBoundsInRoot()
        assertEquals("«ещё 2» is the target of 56", 56f, target.width.value, 0.5f)
    }

    /**
     * 360 × 640 with «Сегодня» short — «Ещё не играли»: no practice this week, the streak broken. The window of the home gives way to
     * the card of tomorrow's events (3.36.9, R2): without the card it is a picture; with it less than 72 is left, the window is its
     * line, 12 under the card, and stands whole above the bottom zone. A window that did not count the card would keep its picture under
     * the card and end under the zone — the test checks that it would, so the state alone cannot make it pass.
     */
    @Test
    fun onA360By640PhoneTheWindowOfTheHomeGivesWayToTheCompactCardAboveTheBottomZone() {
        var start = ""
        var orchestra = ""
        // the practice of the first half of the month: none today, an empty week, no streak
        val withCard = practiceState(threeAtTimes, days = 1..13)
        var state by mutableStateOf(withCard.copy(reminder = null))
        compose.setContent {
            start = stringResource(Res.string.practice_start)
            orchestra = stringResource(Res.string.event_reminder_tomorrow_at_said, Formats.clockOf(11 * 60), "Оркестр")
            ViolinTheme { SmallPhone(state) }
        }
        compose.waitForIdle()
        val alone = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        assertTrue("without the card the window of the home is a picture: ${alone.height}", alone.height >= WindowFit.PictureMin + WindowFit.Line)

        state = withCard
        compose.waitForIdle()
        val zoneTop = zoneTop(start)
        val row = compose.onNodeWithContentDescription(orchestra).getUnclippedBoundsInRoot()
        val cardBottom = row.bottom + CARD_FIELD
        assertTrue("the compact card above the zone at $zoneTop: $cardBottom", cardBottom <= zoneTop + 0.5.dp)
        val window = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        assertEquals("the window 12 under the card", (cardBottom + BLOCK_GAP).value, window.top.value, 0.5f)
        // a window that did not count the card would keep the picture it has without it — there, under the card: under the zone
        assertTrue("a window keeping its picture would end at ${window.top + alone.height}, under the zone at $zoneTop", window.top + alone.height > zoneTop + 0.5.dp)
        assertTrue("the window of the home gives way: its bottom ${window.bottom} above the zone at $zoneTop", window.bottom <= zoneTop + 0.5.dp)
    }

    /**
     * 412 × 892 (spec 3.36.9: the full card from a window of 700): two rows — the exam of the whole day with «весь день» on its second
     * line, the orchestra at 11:00 — and «ещё 1» a row of 48 across the card, not the target of 56. A screen that chose its card by
     * the width of the window would stand the compact one here.
     */
    @Test
    fun onA412By892PhoneTheFullCardStands() {
        var exam = ""
        var orchestra = ""
        var allDay = ""
        var more = ""
        val state = practiceState(three)
        compose.setContent {
            exam = stringResource(
                Res.string.practice_pair_description, stringResource(Res.string.event_reminder_tomorrow_said, three.last().title), stringResource(Res.string.event_all_day),
            )
            orchestra = stringResource(Res.string.event_reminder_tomorrow_at_said, Formats.clockOf(11 * 60), "Оркестр")
            allDay = stringResource(Res.string.event_all_day)
            val hidden = Formats.plural(1, Res.string.event_reminder_more_description_one, Res.string.event_reminder_more_description_few, Res.string.event_reminder_more_description_many)
            more = stringResource(hidden, 1, stringResource(Res.string.event_kind_lesson_word))
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp - 24.dp - 24.dp), told = DpSize(412.dp, 892.dp)) {
                    PracticeScreen(state = state, onIntent = {}, zone = zone, journeyCard = { look -> WindowStandIn(look) })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(exam).assertHeightIsAtLeast(56.dp)
        compose.onNodeWithContentDescription(orchestra).assertHeightIsAtLeast(56.dp)
        assertTrue("the second line of the exam", compose.onAllNodesWithText(allDay, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val moreRow = compose.onNode(hasContentDescription(more) and isButton).assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
        assertTrue("«ещё 1» is a row across the card: ${moreRow.width}", moreRow.width > 100.dp)
    }

    /**
     * A card that goes on the open screen (spec 3.36.9, «Движение»; 5.29 R9): it fades out in 300 ms holding its words, and the window
     * of the home under it keeps its place and height until the card has faded out, then moves once. Frame by frame: half way through
     * the fade the row is there, drawn between the card and the ground; after it the row is gone and the window stands higher.
     */
    @Test
    fun aCardThatGoesOnTheOpenScreenFadesOutAndTheWindowMovesOnceAfterIt() {
        val withCard = practiceState(listOf(event(1, 17 * 60, 45, place = "Анна Сергеевна")))
        var state by mutableStateOf(withCard)
        val said = showMoving({ state })
        val row = compose.onNodeWithContentDescription(said).getUnclippedBoundsInRoot()
        val window = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        val shown = capture()

        // the lesson is over while the screen is open: the same count of unseen changes, so it fades
        state = withCard.copy(reminder = null)
        repeat(HALF_FADE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(said).assertExists()
        val fading = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        assertEquals("the window keeps its place while the card fades", window.top.value, fading.top.value, 0.5f)
        assertEquals("and its height", window.height.value, fading.height.value, 0.5f)
        val half = capture()
        val card = cardPoint(row)
        val ground = groundPoint(row)
        assertBetween("half way through the fade", half[card], shown[card], half[ground])

        repeat(REST_OF_FADE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        assertTrue("faded out: no card", compose.onAllNodesWithContentDescription(said).fetchSemanticsNodes().isEmpty())
        val moved = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        assertTrue("the window takes the place of the card: ${moved.top} above ${window.top}", moved.top < window.top - row.height)
    }

    /**
     * A card that went where the screen did not see it (the review of stage 97: the screen back after the lesson ended, the count of
     * unseen changes one more) is gone at once, without a fade, and the window takes its place in the same frame.
     */
    @Test
    fun aCardThatWentOutOfSightIsGoneAtOnce() {
        val withCard = practiceState(listOf(event(1, 17 * 60, 45, place = "Анна Сергеевна")))
        var state by mutableStateOf(withCard)
        val said = showMoving({ state })
        val window = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()

        state = withCard.copy(reminder = null, reminderEpoch = withCard.reminderEpoch + 1)
        repeat(ONE_FRAME) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        assertTrue("gone in the first frame", compose.onAllNodesWithContentDescription(said).fetchSemanticsNodes().isEmpty())
        assertTrue("the window has its place at once", compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot().top < window.top)
    }

    /** A card there at the first frame of the screen (spec 3.36.9, «Загрузка»: it comes with «Сегодня») stands at once — no fade in. */
    @Test
    fun aCardThereAtTheFirstFrameStandsWithoutAFade() {
        val withCard = practiceState(listOf(event(1, 17 * 60, 45, place = "Анна Сергеевна")))
        val said = showMoving({ withCard }, settleFrames = ONE_FRAME)
        val row = compose.onNodeWithContentDescription(said).getUnclippedBoundsInRoot()
        val first = capture()
        repeat(REST_OF_FADE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        val later = capture()
        val point = cardPoint(row)
        assertTrue("the card of the first frame is the card at rest: ${first[point]} and ${later[point]}", apart(first[point], later[point]) < SAME)
    }

    /**
     * An event of yesterday going past midnight (plan D7): «Вчера в 23:00 — Ночной концерт» on its first line, and to a reader «Вчера в
     * 23:00 Ночной концерт, Клуб, идёт до 01:00».
     */
    @Test
    fun anEventOfYesterdayPastMidnightSaysYesterday() {
        val late = event(1, 23 * 60, 120, title = "Ночной концерт", place = "Клуб", kind = KindRef.BuiltIn(BuiltInKind.PERFORMANCE), date = today)
        val reminder = reminderOf(listOf(late), at = tomorrow.atTime(0, 30).toInstant(zone))
        var line = ""
        var said = ""
        compose.setContent {
            line = stringResource(Res.string.event_reminder_yesterday_at, Formats.clockOf(23 * 60), "Ночной концерт")
            said = stringResource(
                Res.string.practice_pair_description,
                stringResource(Res.string.practice_pair_description, stringResource(Res.string.event_reminder_yesterday_at_said, Formats.clockOf(23 * 60), "Ночной концерт"), "Клуб"),
                stringResource(Res.string.event_running_until_said, Formats.clockOf(60)),
            )
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp)) {
                    Box(Modifier.padding(16.dp)) { ReminderCard(reminder, compact = false, lying = false, onMore = {}) }
                }
            }
        }
        compose.waitForIdle()
        assertTrue("«$line»", compose.onAllNodesWithText(line, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription(said).assertHeightIsAtLeast(56.dp)
    }

    /**
     * «Занятия» upright at 412 × 892 with the state [state] gives, the motion of the screen on — the living button, which asks for frames
     * all the time, takes them one by one (the clock held) — and the window of the home a plain box; [settleFrames] given. Returns the
     * sentence of the row of tomorrow's lesson at 17:00 with Анна Сергеевна.
     */
    private fun showMoving(state: () -> PracticeState, settleFrames: Int = SETTLE_FRAMES): String {
        var said = ""
        compose.mainClock.autoAdvance = false
        compose.setContent {
            said = stringResource(
                Res.string.practice_pair_description,
                stringResource(Res.string.event_reminder_tomorrow_at_said, Formats.clockOf(17 * 60), stringResource(Res.string.event_kind_lesson_word)),
                "Анна Сергеевна",
            )
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp - 24.dp - 24.dp), told = DpSize(412.dp, 892.dp)) {
                    CompositionLocalProvider(LocalReduceMotion provides false) {
                        PracticeScreen(state = state(), onIntent = {}, zone = zone, journeyCard = { look -> WindowStandIn(look) })
                    }
                }
            }
        }
        repeat(settleFrames) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        return said
    }

    /**
     * «Занятия» on a phone of 360 × 640 (spec 3.36.9: the compact card at a window lower than 700): the screen under its bars — the
     * 640 less the status bar and the gesture bar of 24 each — with the tabs under it; the window it is told of — the whole 640. The
     * window of the home is the real one: at home, the takts enough («Хватает до Праги — в путь»).
     */
    @Composable
    private fun SmallPhone(state: PracticeState) {
        TestWindow(DpSize(360.dp, 640.dp - 24.dp - 24.dp), told = DpSize(360.dp, 640.dp)) {
            Column {
                PracticeScreen(
                    state = state,
                    onIntent = {},
                    modifier = Modifier.weight(1f),
                    zone = zone,
                    journeyCard = { look -> JourneyWindowCard(WindowSample.enough, look, onClick = {}, modifier = Modifier.testTag(WINDOW)) },
                )
                AppBottomBar(TopLevelDestination.PRACTICE, onSelect = {})
            }
        }
    }

    /**
     * The top of the bottom zone of a window lower than 700 (5.29 R2): its top field above its button. The node of [start] in the merged
     * tree is the button itself — checked, so the field is counted from the button and not from its words.
     */
    private fun zoneTop(start: String): Dp {
        val zone = DockMetrics.Low
        val button = compose.onNode(hasText(start) and isButton).getUnclippedBoundsInRoot()
        assertEquals("«$start» is the button of the zone", zone.button.value, button.height.value, 0.5f)
        return button.top - zone.top
    }

    private fun capture(): PixelMap = compose.onNodeWithTag(TEST_WINDOW).captureToImage().toPixelMap()

    /** A pixel of the card itself, on the right of its row where no words are: the colour of a card over the ground. */
    private fun cardPoint(row: DpRect): Pair<Int, Int> = pixelAt(row.right - 16.dp, (row.top + row.bottom) / 2)

    /** A pixel of the ground of the screen beside the card: in the field of 16 on the left. */
    private fun groundPoint(row: DpRect): Pair<Int, Int> = pixelAt(6.dp, (row.top + row.bottom) / 2)

    private fun pixelAt(x: Dp, y: Dp): Pair<Int, Int> {
        val window = compose.onNodeWithTag(TEST_WINDOW).getUnclippedBoundsInRoot()
        return with(compose.density) { (x - window.left).roundToPx() to (y - window.top).roundToPx() }
    }

    private operator fun PixelMap.get(point: Pair<Int, Int>): Color = this[point.first, point.second]

    private fun apart(a: Color, b: Color): Float = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    /** [value] lies between [one] and [other] in every channel, a tenth of the way in from each end at least. */
    private fun assertBetween(what: String, value: Color, one: Color, other: Color) {
        fun between(v: Float, a: Float, b: Float): Boolean {
            val margin = abs(a - b) * BETWEEN_MARGIN
            return v > minOf(a, b) + margin && v < maxOf(a, b) - margin
        }
        assertTrue("$what: the card and the ground are told apart — $one, $other", apart(one, other) > SAME)
        assertTrue("$what: $value between the card $one and the ground $other", between(value.blue, one.blue, other.blue))
    }

    /**
     * The window of the home as the screen asks for it, a plain box with nothing living in it: the picture's height and what stands
     * under it, the line — what the screen measures it by, so it moves and grows as the real one would.
     */
    @Composable
    private fun WindowStandIn(look: WindowLook) {
        when (look) {
            is WindowLook.Picture -> Box(Modifier.fillMaxWidth().height(look.height + UNDER_PICTURE).testTag(WINDOW))
            WindowLook.UnderPicture -> Box(Modifier.fillMaxWidth().height(UNDER_PICTURE))
            else -> Box(Modifier.fillMaxWidth().height(LINE).testTag(WINDOW))
        }
    }

    private companion object {
        const val CARD = "card"

        /** The window of the home — not "window", the tag of the box of [TestWindow]. */
        const val WINDOW = "home"

        /** The rule before the target of «ещё N» (5.29 R9). */
        val RULE = 1.dp

        /** The field of the card under its rows, upright (5.29 R9), and the gap between the blocks of «Занятия» (5.29 R2). */
        val CARD_FIELD = 4.dp
        val BLOCK_GAP = 12.dp

        /** The stand-in window: what stands under its picture, and its line. */
        val UNDER_PICTURE = 40.dp
        val LINE = 72.dp

        /** Frames: the screen settled; half way through the fade of 300 ms; the rest of it and some; a frame. */
        const val SETTLE_FRAMES = 20
        const val HALF_FADE_FRAMES = 9
        const val REST_OF_FADE_FRAMES = 20
        const val ONE_FRAME = 1

        /** Colours apart by no more than this are the same; a value between two is a tenth of the way in from each. */
        const val SAME = 2f / 255
        const val BETWEEN_MARGIN = 0.1f
    }
}
