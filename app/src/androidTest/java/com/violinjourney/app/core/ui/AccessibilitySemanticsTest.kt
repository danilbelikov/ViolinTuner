package com.violinjourney.app.core.ui

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.feature.history.HistoryFilter
import com.violinjourney.app.feature.history.HistoryScreen
import com.violinjourney.app.feature.history.HistoryState
import com.violinjourney.app.feature.history.components.SessionCard
import com.violinjourney.app.feature.home.TwoWay
import com.violinjourney.app.feature.onboarding.OnboardingScreen
import com.violinjourney.app.feature.onboarding.OnboardingState
import com.violinjourney.app.feature.onboarding.OnboardingStep
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.feature.practice.PracticeScreen
import com.violinjourney.app.feature.practice.ProgressReducer
import com.violinjourney.app.feature.practice.components.PathRow
import com.violinjourney.app.feature.practice.components.PracticeCalendar
import com.violinjourney.app.feature.repertoire.sections.SectionsScreen
import com.violinjourney.app.feature.repertoire.sections.SectionsState
import com.violinjourney.app.feature.sound.components.MiniPlayer
import com.violinjourney.app.feature.sound.components.MiniPlayerMetrics
import com.violinjourney.app.feature.sound.components.ParamSlider
import com.violinjourney.app.feature.sound.components.SliderModel
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.TopLevelDestination
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.nav_repertoire
import com.violinjourney.app.shared.resources.onboarding_skip
import com.violinjourney.app.shared.resources.path_description
import com.violinjourney.app.shared.resources.practice_day_description
import com.violinjourney.app.shared.resources.practice_day_none
import com.violinjourney.app.shared.resources.practice_month_back
import com.violinjourney.app.shared.resources.practice_month_days_few
import com.violinjourney.app.shared.resources.practice_month_days_many
import com.violinjourney.app.shared.resources.practice_month_days_one
import com.violinjourney.app.shared.resources.practice_month_description
import com.violinjourney.app.shared.resources.practice_month_forward
import com.violinjourney.app.shared.resources.practice_streak_days_description_few
import com.violinjourney.app.shared.resources.practice_streak_days_description_many
import com.violinjourney.app.shared.resources.practice_streak_days_description_one
import com.violinjourney.app.shared.resources.practice_streak_days_few
import com.violinjourney.app.shared.resources.practice_streak_days_many
import com.violinjourney.app.shared.resources.practice_streak_days_one
import com.violinjourney.app.shared.resources.practice_week_description
import com.violinjourney.app.shared.resources.progress_level_names
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What TalkBack and VoiceOver are told on the screens where the eye has more than the reader: the date of a card under
 * its day header (spec 3.21), which side of a two-way switch is chosen, «A» and «B» that answer the reader's
 * activation, a slider that resets only from its actions, the calendar's month, days and arrows, the path row, the week and the
 * chip of the streak of «Занятия», the faded «Пропустить», the four tabs and the titles of «Репертуар» and «Записи».
 */
@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun descriptions(): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.ContentDescription] }

    @Test
    fun aRecordCardTellsItsDateThoughItShowsOnlyTheTime() {
        val day = LocalDate(2026, 9, 20)
        val card = HistoryCard(id = 1, title = null, startedAtEpochMs = 1_790_000_000_000, date = day, durationMs = 495_000, hasAudio = true)
        compose.setContent { ViolinTheme { SessionCard(card, TimeZone.UTC, onClick = {}) } }
        val date = Formats.recordDate(day, withYear = false)
        assertTrue("the card says «$date» to a reader: ${descriptions()}", descriptions().any { date in it })
        compose.onAllNodesWithText(date, substring = true).assertCountEquals(0)
    }

    @Test
    fun aTwoWaySwitchSaysWhichSideIsChosen() {
        val chosen = mutableListOf<Boolean>()
        compose.setContent { ViolinTheme { TwoWay(ROOM, OUTSIDE, secondChosen = true, onChoose = { chosen += it }) } }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
        assertEquals(2, tabs.size)
        fun selectedOf(word: String) = compose.onNodeWithText(word).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        assertEquals(false, selectedOf(ROOM))
        assertEquals(true, selectedOf(OUTSIDE))
        compose.onNodeWithText(ROOM).performClick()
        assertEquals(listOf(false), chosen)
    }

    @Test
    fun theHalvesOfAbAnswerTheReadersActivationAndTheWaveIgnoresIt() {
        val original = mutableListOf<Pair<Boolean, Boolean>>()
        val seeks = mutableListOf<Long>()
        val meters = mutableStateOf<SoundMeters?>(null)
        compose.setContent {
            ViolinTheme {
                MiniPlayer(
                    player = PlayerState(ready = true, durationMs = 60_000, processed = true),
                    position = { 0 },
                    waveform = null,
                    meters = meters,
                    metrics = MiniPlayerMetrics.Regular,
                    onPlayPause = {},
                    onSeek = { seeks += it },
                    onOriginal = { value, held -> original += value to held },
                )
            }
        }
        val halves = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        halves.assertCountEquals(2)
        halves[0].performSemanticsAction(SemanticsActions.OnClick)
        halves[1].performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(true to false, false to false), original)

        // the wave: a slider for swipes, and an activation that seeks nowhere
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(emptyList<Long>(), seeks)
    }

    @Test
    fun aSliderResetsFromItsActionsNotOnTheReadersActivation() {
        val fractions = mutableListOf<Float>()
        var resets = 0
        compose.setContent {
            ViolinTheme {
                ParamSlider(
                    model = SliderModel(label = "Hall", hint = null, valueText = "25 %", fraction = 0.25f, defaultFraction = 0.5f, bipolar = false),
                    enabled = true,
                    onFraction = { fractions += it },
                    onStep = {},
                    onReset = { resets++ },
                )
            }
        }
        val track = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        track.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("the activation changes nothing", 0, resets)
        assertEquals(emptyList<Float>(), fractions)

        val actions = track.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(1, actions.size)
        compose.runOnIdle { actions.single().action() }
        assertEquals(1, resets)
        assertEquals(emptyList<Float>(), fractions)
    }

    /**
     * The header of the calendar (spec 3.36.2): one heading — «Сентябрь, 17 ч 27 мин, 24 дня» — its words not read one by one; a
     * month without practice is its name alone. No legend any more.
     */
    @Test
    fun theCalendarsMonthIsAHeadingThatSaysItsSumAndDays() {
        val month = YearMonth(2026, 9)
        var monthDays by mutableStateOf(24)
        var expected = ""
        compose.setContent {
            val title = Formats.monthTitle(month, currentYear = 2026)
            val days = stringResource(Formats.plural(24, Res.string.practice_month_days_one, Res.string.practice_month_days_few, Res.string.practice_month_days_many), 24)
            expected = stringResource(Res.string.practice_month_description, title, Formats.minutesInWords(SEPTEMBER_MS), days)
            ViolinTheme {
                PracticeCalendar(
                    month, septemberCells(), canGoForward = false, onMonthBack = {}, onMonthForward = {}, onDaySelected = {},
                    currentYear = 2026, monthMs = if (monthDays > 0) SEPTEMBER_MS else 0, monthDays = monthDays,
                )
            }
        }
        compose.waitForIdle()
        val header = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNode().config
        assertEquals(listOf(expected), header[SemanticsProperties.ContentDescription])
        compose.onAllNodesWithText(Formats.minutesInWords(SEPTEMBER_MS), substring = true).assertCountEquals(0)

        monthDays = 0
        compose.waitForIdle()
        val bare = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNode().config
        assertEquals(listOf(Formats.monthTitle(month, currentYear = 2026)), bare[SemanticsProperties.ContentDescription])
    }

    /**
     * A day to come is not selectable until the events of R9 (spec 3.36.2); a cell is as high as its row, 52, and the whole of it is
     * the touch. It is touched at its bottom corners: a circle of 40 alone would take a touch up to 48 around it (Compose widens a
     * small target, the lesson of stage 101), and the top of the cell and its middle are within that — its corners are not.
     */
    @Test
    fun aFutureDayIsNotEnabledAndAPastDayIsTouchedAtTheCornersOfItsCell() {
        val picked = mutableListOf<LocalDate>()
        var future = ""
        var past = ""
        compose.setContent {
            val none = stringResource(Res.string.practice_day_none)
            future = stringResource(Res.string.practice_day_description, Formats.dayWithWeekday(LocalDate(2026, 9, 28)), none)
            past = stringResource(Res.string.practice_day_description, Formats.dayWithWeekday(LocalDate(2026, 9, 3)), none)
            ViolinTheme {
                PracticeCalendar(
                    YearMonth(2026, 9), septemberCells(), canGoForward = false, onMonthBack = {}, onMonthForward = {}, onDaySelected = { picked += it },
                    currentYear = 2026, monthMs = 0, monthDays = 0,
                )
            }
        }
        compose.onNodeWithContentDescription(future).assertIsNotEnabled()
        val day = compose.onNodeWithContentDescription(past).assertIsEnabled().assertHeightIsAtLeast(52.dp)
        day.performTouchInput { click(Offset(1f, height - 1f)) }
        day.performTouchInput { click(Offset(width - 1f, height - 1f)) }
        assertEquals(listOf(LocalDate(2026, 9, 3), LocalDate(2026, 9, 3)), picked)
    }

    /** The arrows of the month are named buttons to a reader, as the IconButton they were; the one that cannot go on is off. */
    @Test
    fun theArrowsOfTheMonthAreNamedButtonsAndTheOneThatCannotGoOnIsOff() {
        var back = ""
        var forward = ""
        compose.setContent {
            back = stringResource(Res.string.practice_month_back)
            forward = stringResource(Res.string.practice_month_forward)
            ViolinTheme {
                PracticeCalendar(
                    YearMonth(2026, 9), septemberCells(), canGoForward = false, onMonthBack = {}, onMonthForward = {}, onDaySelected = {},
                    currentYear = 2026, monthMs = 0, monthDays = 0,
                )
            }
        }
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        compose.onNodeWithContentDescription(back).assert(button).assertIsEnabled()
        compose.onNodeWithContentDescription(forward).assert(button).assertIsNotEnabled()
    }

    /** September 2026 as the calendar gets it: 24 days of practice for 17 h 27 min, today the 27th, the 28th to come. */
    private fun septemberCells(): List<CalendarCell?> = (1..30).map {
        val date = LocalDate(2026, 9, it)
        CalendarCell(date, totalMs = 0, fillLevel = 0, isToday = it == 27, isSelected = false, isFuture = it > 27)
    }

    /**
     * The path row of «Занятия» (spec 3.36.2): one description — the level, its name, the whole time and what is left to the next
     * level, the progress in words — pressed as a button, and announced as one: a node with a range is announced by Android as a
     * progress bar whatever its role, so the row has none. The words on it are not read one by one.
     */
    @Test
    fun thePathRowReadsAsOneDescriptionWithItsProgressAndIsAButton() {
        val header = ProgressReducer.headerOf(47 * 60 * MS_PER_MINUTE + 17 * MS_PER_MINUTE, emptyList(), name = "", avatarPath = null, ProgressConfig())
        var expected = ""
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            val name = stringArrayResource(Res.array.progress_level_names)[header.level - 1]
            expected = stringResource(
                Res.string.path_description, header.level, name, Formats.totalTime(header.totalMs), header.nextLevel!!, Formats.remainingTime(header.toNextLevelMs!!),
            )
            ViolinTheme { PathRow(header, photo = null, onClick = {}) }
        }
        compose.waitForIdle()
        assertEquals(listOf(expected), descriptions())
        val node = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNode()
        assertTrue("it is pressed", SemanticsActions.OnClick in node.config)
        assertFalse("no range: it would be announced as a progress bar", SemanticsProperties.ProgressBarRangeInfo in node.config)
        // what TalkBack is told: the class of the node, from the accessibility delegate of the Compose view itself
        val className = compose.runOnUiThread { view!!.accessibilityNodeProvider?.createAccessibilityNodeInfo(node.id)?.className?.toString() }
        assertEquals("android.widget.Button", className)
        compose.onAllNodesWithText(Formats.totalTime(header.totalMs), substring = true).assertCountEquals(0)
    }

    /**
     * «Сегодня» (spec 3.36.2): the bars are one description — «Неделя: …; сегодня …» — and the chip is «8 дней подряд»; its words
     * and its flame are not read apart.
     */
    @Test
    fun theWeekReadsAsOneDescriptionAndTheChipAsDaysInARow() {
        // Sunday 27 September 2026, eight days in a row from the 20th: 45 minutes a day
        val today = LocalDate(2026, 9, 27)
        val entries = (20..27).map { PracticeEntry(LocalDate(2026, 9, it), startedAtEpochMs = 0, durationMs = 45 * MS_PER_MINUTE, manual = false) }
        val state = PracticeReducer.stateOf(
            entries = entries, sessions = emptyList(), runningSince = null, month = YearMonth(2026, 9), selectedDate = null, sheet = null,
            today = today, zone = TimeZone.UTC, config = PracticeConfig(), trophies = emptyList(), profile = Profile.EMPTY, avatarPath = null,
            progressConfig = ProgressConfig(),
        )
        var week = ""
        var streak = ""
        var chipWords = ""
        compose.setContent {
            week = stringResource(Res.string.practice_week_description, Formats.minutesInWords(state.summary.weekMs), Formats.minutesInWords(state.todayMs))
            streak = stringResource(
                Formats.plural(8, Res.string.practice_streak_days_description_one, Res.string.practice_streak_days_description_few, Res.string.practice_streak_days_description_many),
                8,
            )
            chipWords = stringResource(Formats.plural(8, Res.string.practice_streak_days_one, Res.string.practice_streak_days_few, Res.string.practice_streak_days_many), 8)
            ViolinTheme { PracticeScreen(state, onIntent = {}, zone = TimeZone.UTC) }
        }
        compose.waitForIdle()
        val said = descriptions()
        assertTrue("the bars say «$week»: $said", week in said)
        assertTrue("the chip says «$streak»: $said", streak in said)
        compose.onAllNodesWithText(chipWords).assertCountEquals(0)
    }

    @Test
    fun theFadedSkipOfTheLastPageIsNotThereForAReader() {
        val skip = showIntroduction(OnboardingStep.DATA)
        compose.onAllNodesWithText(skip).assertCountEquals(0)
    }

    @Test
    fun theSkipOfAnEarlierPageIsThereForAReader() {
        val skip = showIntroduction(OnboardingStep.WELCOME)
        compose.onAllNodesWithText(skip).assertCountEquals(1)
    }

    /** «Репертуар» reads as its neighbours do — a tab, its name and «выбрано» (spec 3.36.1); the item is pressed at its full height. */
    @Test
    fun theFourTabsReadAsTabsAndTheRepertoireIsSelectedAsItsNeighbours() {
        var repertoire = ""
        var history = ""
        var current by mutableStateOf(TopLevelDestination.PRACTICE)
        compose.setContent {
            repertoire = stringResource(Res.string.nav_repertoire)
            history = stringResource(Res.string.nav_history)
            ViolinTheme { AppBottomBar(current = current, onSelect = { current = it }, practiceRunning = true) }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
        assertEquals("the icons are silent: the label says it", emptyList<String>(), descriptions())
        compose.onNodeWithText(repertoire).assertIsNotSelected().performClick()
        compose.onNodeWithText(repertoire).assertIsSelected().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText(history).performClick()
        compose.onNodeWithText(history).assertIsSelected()
        compose.onNodeWithText(repertoire).assertIsNotSelected()
    }

    @Test
    fun anItemOfTheCompactBarIsPressedOverTheWholeHeight() {
        var repertoire = ""
        compose.setContent {
            repertoire = stringResource(Res.string.nav_repertoire)
            ViolinTheme { AppBottomBar(current = TopLevelDestination.REPERTOIRE, onSelect = {}, compact = true) }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
        compose.onNodeWithText(repertoire).assertIsSelected().assertHeightIsAtLeast(64.dp)
    }

    /** The title of a tab is a heading a reader can jump to (spec 3.36.1): «Репертуар» over the sections. */
    @Test
    fun theTitleOfTheRepertoireTabIsAHeading() {
        var title = ""
        compose.setContent {
            title = stringResource(Res.string.nav_repertoire)
            ViolinTheme {
                SectionsScreen(SectionsState(loading = false, cards = emptyList(), total = SectionCount.EMPTY, maxNameLength = 24), onIntent = {})
            }
        }
        compose.onNodeWithText(title).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    /** «Записи» over the records, where the switch «Записи | Репертуар» stood (spec 3.36.1): a heading, not a tab of its own. */
    @Test
    fun theTitleOfTheRecordsTabIsAHeading() {
        var title = ""
        val state = HistoryState(
            loading = false, totalCount = 0, days = emptyList(), chartTop = 0, today = LocalDate(2026, 9, 28),
            filter = HistoryFilter.ALL, cards = emptyList(),
        )
        compose.setContent {
            title = stringResource(Res.string.nav_history)
            ViolinTheme { HistoryScreen(state, onIntent = {}, zone = TimeZone.UTC) }
        }
        compose.onNodeWithText(title).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    /** The introduction on [step]; gives back the word of «Пропустить» in the language of the test. */
    private fun showIntroduction(step: OnboardingStep): String {
        var skip = ""
        compose.setContent {
            skip = stringResource(Res.string.onboarding_skip)
            ViolinTheme { OnboardingScreen(OnboardingState(step, a4Hz = 440, a4OptionsHz = listOf(440, 441, 442, 443), tolerance = TolerancePreset.INTERMEDIATE), onIntent = {}) }
        }
        compose.waitForIdle()
        assertFalse("the word is read", skip.isEmpty())
        return skip
    }

    private companion object {
        const val ROOM = "Room"
        const val OUTSIDE = "Outside"

        /** 17 h 27 min — the month of the mockups. */
        const val SEPTEMBER_MS = (17 * 60 + 27) * MS_PER_MINUTE
    }
}
