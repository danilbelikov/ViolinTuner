package com.violinjourney.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.domain.TolerancePreset
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
import com.violinjourney.app.shared.resources.practice_legend_less
import com.violinjourney.app.shared.resources.practice_legend_more
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
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
 * activation, a slider that resets only from its actions, the calendar's legend and month, the faded «Пропустить», the
 * four tabs and the titles of «Репертуар» and «Записи».
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

    @Test
    fun theCalendarsLegendIsSilentAndItsMonthIsAHeading() {
        val month = YearMonth(2026, 9)
        var less = ""
        var more = ""
        val cells = (1..30).map { CalendarCell(LocalDate(2026, 9, it), totalMs = 0, fillLevel = 0, isToday = false, isSelected = false, isFuture = false) }
        compose.setContent {
            less = stringResource(Res.string.practice_legend_less)
            more = stringResource(Res.string.practice_legend_more)
            ViolinTheme { PracticeCalendar(month, cells, canGoForward = false, onMonthBack = {}, onMonthForward = {}, onDaySelected = {}) }
        }
        // the tree a reader walks: a cleared node keeps its children only in the unmerged tree of the test
        compose.onAllNodesWithText(less).assertCountEquals(0)
        compose.onAllNodesWithText(more).assertCountEquals(0)
        val heading = compose.onNodeWithText(Formats.monthAndYear(month)).fetchSemanticsNode().config
        assertTrue("the month is a heading", SemanticsProperties.Heading in heading)
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
    }
}
