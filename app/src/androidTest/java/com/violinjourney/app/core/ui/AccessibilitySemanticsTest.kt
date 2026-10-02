package com.violinjourney.app.core.ui

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.domain.practice.PracticeRecap
import com.violinjourney.app.core.domain.practice.RecapRoad
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.feature.journey.taktsInWords
import com.violinjourney.app.feature.practice.Gift
import com.violinjourney.app.feature.practice.NextTrophy
import com.violinjourney.app.feature.practice.PracticeSheet
import com.violinjourney.app.feature.practice.components.GiftSheetContent
import com.violinjourney.app.feature.practice.components.NamePhotoSheetContent
import com.violinjourney.app.feature.practice.components.RecapButtons
import com.violinjourney.app.feature.practice.components.RecapSheetContent
import com.violinjourney.app.feature.practice.components.TrophiesSheetContent
import com.violinjourney.app.shared.resources.gift_description
import com.violinjourney.app.shared.resources.gift_hours_few
import com.violinjourney.app.shared.resources.gift_hours_many
import com.violinjourney.app.shared.resources.gift_hours_one
import com.violinjourney.app.shared.resources.gift_next_description
import com.violinjourney.app.shared.resources.gift_title
import com.violinjourney.app.shared.resources.home_travel
import com.violinjourney.app.shared.resources.journey_earned
import com.violinjourney.app.shared.resources.path_name_photo
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.shared.resources.profile_name_label
import com.violinjourney.app.shared.resources.progress_trophy_names
import com.violinjourney.app.shared.resources.recap_description_head
import com.violinjourney.app.shared.resources.recap_description_level
import com.violinjourney.app.shared.resources.recap_description_level_up
import com.violinjourney.app.shared.resources.recap_road_enough
import com.violinjourney.app.shared.resources.recap_title
import com.violinjourney.app.shared.resources.sound_ab_original
import com.violinjourney.app.shared.resources.sound_ab_processed
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_few
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_many
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_one
import com.violinjourney.app.shared.resources.tolerance_intermediate_name
import com.violinjourney.app.shared.resources.tolerance_intermediate_text
import com.violinjourney.app.shared.resources.trophies_count
import com.violinjourney.app.shared.resources.trophies_heading_description
import com.violinjourney.app.shared.resources.trophies_line_next
import com.violinjourney.app.shared.resources.trophies_title
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.practice.ForgottenPractice
import com.violinjourney.app.core.domain.practice.PieceBlock
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.feature.history.HistoryFilter
import com.violinjourney.app.feature.history.HistoryScreen
import com.violinjourney.app.feature.history.HistoryState
import com.violinjourney.app.feature.history.components.SessionCard
import com.violinjourney.app.core.ui.components.ShortfallPlate
import com.violinjourney.app.feature.home.TwoWay
import com.violinjourney.app.feature.journey.JourneyReducer
import com.violinjourney.app.feature.journey.JourneyWindowCard
import com.violinjourney.app.feature.journey.WindowLook
import com.violinjourney.app.feature.journey.cityToOf
import com.violinjourney.app.feature.onboarding.OnboardingScreen
import com.violinjourney.app.feature.onboarding.OnboardingState
import com.violinjourney.app.feature.onboarding.OnboardingStep
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.feature.practice.PracticePrompt
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.feature.practice.PracticeScreen
import com.violinjourney.app.feature.practice.ProgressReducer
import com.violinjourney.app.feature.practice.WindowFit
import com.violinjourney.app.feature.practice.components.ForgottenSheetContent
import com.violinjourney.app.feature.practice.components.PathRow
import com.violinjourney.app.feature.practice.components.PracticeCalendar
import com.violinjourney.app.feature.practice.components.SummarySheetContent
import com.violinjourney.app.feature.repertoire.sections.SectionsScreen
import com.violinjourney.app.feature.repertoire.sections.SectionsState
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.EqBand
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.feature.sound.RecordingName
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.feature.sound.SoundIntent
import com.violinjourney.app.feature.sound.SoundMode
import com.violinjourney.app.feature.sound.SoundScreen
import com.violinjourney.app.feature.sound.SoundState
import com.violinjourney.app.feature.sound.components.ParamSlider
import com.violinjourney.app.feature.sound.components.SliderModel
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.TopLevelDestination
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_part_of
import com.violinjourney.app.shared.resources.block_played_title
import com.violinjourney.app.shared.resources.journey_enough
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.nav_repertoire
import com.violinjourney.app.shared.resources.onboarding_part_intro
import com.violinjourney.app.shared.resources.onboarding_part_setup
import com.violinjourney.app.shared.resources.onboarding_progress_description
import com.violinjourney.app.shared.resources.onboarding_skip
import com.violinjourney.app.shared.resources.path_description
import com.violinjourney.app.shared.resources.practice_day_description
import com.violinjourney.app.shared.resources.practice_day_none
import com.violinjourney.app.shared.resources.practice_forgotten_title
import com.violinjourney.app.shared.resources.practice_month_back
import com.violinjourney.app.shared.resources.practice_month_days_few
import com.violinjourney.app.shared.resources.practice_month_days_many
import com.violinjourney.app.shared.resources.practice_month_days_one
import com.violinjourney.app.shared.resources.practice_month_description
import com.violinjourney.app.shared.resources.practice_month_forward
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.practice_played_done_description
import com.violinjourney.app.shared.resources.practice_played_dropped_description
import com.violinjourney.app.shared.resources.practice_step_down
import com.violinjourney.app.shared.resources.practice_step_up
import com.violinjourney.app.shared.resources.practice_stepper_description_was
import com.violinjourney.app.shared.resources.practice_streak_days_description_few
import com.violinjourney.app.shared.resources.practice_streak_days_description_many
import com.violinjourney.app.shared.resources.practice_streak_days_description_one
import com.violinjourney.app.shared.resources.practice_streak_days_few
import com.violinjourney.app.shared.resources.practice_streak_days_many
import com.violinjourney.app.shared.resources.practice_streak_days_one
import com.violinjourney.app.shared.resources.practice_week_description
import com.violinjourney.app.shared.resources.progress_level_names
import com.violinjourney.app.shared.resources.takt_icon
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupCandidate
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManifest
import com.violinjourney.app.core.backup.BackupProgress
import com.violinjourney.app.feature.backup.BackupScreen
import com.violinjourney.app.feature.backup.BackupState
import com.violinjourney.app.feature.backup.RestoreScreen
import com.violinjourney.app.feature.backup.RestoreStage
import com.violinjourney.app.feature.backup.RestoreState
import com.violinjourney.app.shared.resources.backup_chip_no_video
import com.violinjourney.app.shared.resources.backup_count_days_few
import com.violinjourney.app.shared.resources.backup_count_days_many
import com.violinjourney.app.shared.resources.backup_count_days_one
import com.violinjourney.app.shared.resources.backup_count_level
import com.violinjourney.app.shared.resources.backup_count_pieces_few
import com.violinjourney.app.shared.resources.backup_count_pieces_many
import com.violinjourney.app.shared.resources.backup_count_pieces_one
import com.violinjourney.app.shared.resources.backup_count_sessions_few
import com.violinjourney.app.shared.resources.backup_count_sessions_many
import com.violinjourney.app.shared.resources.backup_count_sessions_one
import com.violinjourney.app.shared.resources.backup_phase_now
import com.violinjourney.app.shared.resources.backup_saved_title
import com.violinjourney.app.shared.resources.backup_title
import com.violinjourney.app.shared.resources.restore_copy_from
import com.violinjourney.app.shared.resources.restore_copy_version
import com.violinjourney.app.shared.resources.restore_title
import kotlinx.datetime.atStartOfDayIn
import kotlin.math.abs
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.feature.backup.DataBlockState
import com.violinjourney.app.feature.backup.DataGroup
import com.violinjourney.app.feature.backup.DataRunning
import com.violinjourney.app.feature.backup.JobPhase
import com.violinjourney.app.feature.settings.SettingsScreen
import com.violinjourney.app.feature.settings.SettingsState
import com.violinjourney.app.shared.resources.a4_option_description
import com.violinjourney.app.shared.resources.analytics_row
import com.violinjourney.app.shared.resources.backup_block_title
import com.violinjourney.app.shared.resources.backup_part_video
import com.violinjourney.app.shared.resources.backup_percent
import com.violinjourney.app.shared.resources.backup_phase_part
import com.violinjourney.app.shared.resources.backup_row_restore
import com.violinjourney.app.shared.resources.backup_row_saving
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.nav_settings
import com.violinjourney.app.shared.resources.restore_row_wait_copy
import com.violinjourney.app.shared.resources.settings_a4_note
import com.violinjourney.app.shared.resources.settings_a4_title
import com.violinjourney.app.shared.resources.settings_group_app
import com.violinjourney.app.shared.resources.settings_group_intonation
import com.violinjourney.app.shared.resources.settings_group_records
import com.violinjourney.app.shared.resources.settings_tolerance_note
import com.violinjourney.app.shared.resources.settings_tolerance_title
import com.violinjourney.app.shared.resources.tolerance_cents
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
 * activation, a slider that resets only from its actions, the calendar's month, days and arrows, the path row, the week, the
 * chip of the streak and the window of the home of «Занятия», the faded «Пропустить», the four tabs and the titles of «Репертуар»
 * and «Записи», the stepper, «Что играли» and the title of «Занятие не закончено» (spec 3.36.3); the recap and the gift as one
 * paragraph each on their title, their buttons still buttons, «Трофеи, 2 из 10» and the nearest one «следующий», the field «Имя»;
 * the strip of the introduction, the cards of the tolerance, «Настройки» and their «Данные» (R8); the passport of a copy as one phrase,
 * the headers of a copy and a restore and the titles of their outcomes as headings, the line of phases as the step of now (stage 122).
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

    /**
     * The plate of what is missing (spec 3.36.7, 5.29 R7): it tells, it does not forbid — one text for a reader, its [description] when
     * given, else the words and the caption; no role of a button, no action, never «disabled», and not a single text of its own left
     * apart from it.
     */
    @Test
    fun aShortfallPlateIsWordsNotADisabledButton() {
        compose.setContent {
            ViolinTheme {
                Column {
                    ShortfallPlate(text = SHORT, caption = ABOUT, description = SAID)
                    ShortfallPlate(text = SHORT, caption = ABOUT)
                }
            }
        }
        val plates = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).fetchSemanticsNodes()
        assertEquals(listOf(listOf(SAID), listOf("$SHORT, $ABOUT")), plates.map { it.config[SemanticsProperties.ContentDescription] })
        plates.forEach { plate ->
            assertEquals("no role", null, plate.config.getOrNull(SemanticsProperties.Role))
            assertFalse("not «disabled»", SemanticsProperties.Disabled in plate.config)
            assertFalse("no touch", SemanticsActions.OnClick in plate.config)
            assertFalse("no long touch", SemanticsActions.OnLongClick in plate.config)
        }
        // the words are in the description, not nodes apart
        compose.onAllNodesWithText(SHORT, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(ABOUT, substring = true).assertCountEquals(0)
    }

    /** The player at the bottom of «Звук» (spec 3.36.5): its large A/B are two radio buttons their activation picks; the wave seeks nowhere. */
    @Test
    fun theHalvesOfAbAnswerTheReadersActivationAndTheWaveIgnoresIt() {
        val original = mutableListOf<Pair<Boolean, Boolean>>()
        val seeks = mutableListOf<Long>()
        val meters = mutableStateOf<SoundMeters?>(null)
        var a = ""
        var b = ""
        compose.setContent {
            a = stringResource(Res.string.sound_ab_original)
            b = stringResource(Res.string.sound_ab_processed)
            ViolinTheme {
                SoundScreen(
                    state = soundOf(PlayerState(ready = true, durationMs = 60_000, processed = true)),
                    meters = meters,
                    onIntent = { intent ->
                        when (intent) {
                            is SoundIntent.OriginalSelected -> original += intent.original to intent.held
                            is SoundIntent.SeekRequested -> seeks += intent.positionMs
                            else -> Unit
                        }
                    },
                    config = SoundConfig(),
                    backingConfig = BackingConfig(),
                )
            }
        }
        compose.onNodeWithContentDescription(a).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription(b).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(true to false, false to false), original)

        // the wave: a slider for swipes, and an activation that seeks nowhere (the cards are closed: no slider of theirs is there)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(emptyList<Long>(), seeks)
    }

    /** «Звук» of a recording with the processing of «Камерный зал», heard through [player]. */
    private fun soundOf(player: PlayerState): SoundState {
        val config = SoundConfig()
        return SoundState(
            loading = false, mode = SoundMode.RECORDING, recording = RecordingName(1, "Take", null, 0), own = false,
            settings = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config), caption = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL),
            chips = emptyList(), custom = false, canReset = false, savedHint = false, band = EqBand.PRESENCE, details = false,
            player = player, listening = true, waveform = null, recordings = emptyList(), today = LocalDate(2026, 9, 27), affected = 0, dialog = null,
        )
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

    /**
     * The window of the home on «Занятия» (spec 3.36.2): one button whose words hold the call and the purse — the pill of the takts
     * is not a stop apart, out of them. In the line with a thumbnail and with the picture alike.
     */
    @Test
    fun theWindowOfTheHomeIsOneButtonWithTheCallAndTheTaktsInItsWords() {
        val window = JourneyReducer.windowOf(JourneyProgress(earned = PURSE, spent = 0, arrivals = listOf(Arrival(JourneyRoute.HOME, 1)), extras = emptySet()))
        var look by mutableStateOf<WindowLook>(WindowLook.Line)
        var call = ""
        var sign = ""
        compose.setContent {
            call = stringResource(Res.string.journey_enough, cityToOf(1))
            sign = stringResource(Res.string.takt_icon)
            // the picture standing still, as with «убрать анимации»: a living one would ask for frames all the time
            CompositionLocalProvider(LocalReduceMotion provides true) {
                ViolinTheme { JourneyWindowCard(window, look, onClick = {}) }
            }
        }
        val purse = Formats.takts(PURSE)
        listOf(WindowLook.Line, WindowLook.Picture(WindowFit.PictureMax)).forEach { shown ->
            look = shown
            compose.waitForIdle()
            val button = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNode()
            assertTrue("pressed: $shown", SemanticsActions.OnClick in button.config)
            val said = button.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                button.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            assertTrue("the call «$call» in the words of the window ($shown): $said", call in said)
            assertTrue("the purse «$purse» in the words of the window ($shown): $said", purse in said)
            assertTrue("the sign «$sign» in the words of the window ($shown): $said", sign in said)
            // the purse is read with the window, never as a node of its own
            compose.onAllNodesWithText(purse).assertCountEquals(1)
        }
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

    /**
     * The strip of the way is one phrase for a reader, the part and the screen of seven (spec 3.36.8): «Знакомство, экран 1 из 7» on the
     * first page — its twin on the page is not heard — and «Настройка, экран 5 из 7» on «Микрофон».
     */
    @Test
    fun theStripOfTheWayIsOnePhraseOfItsPartAndScreen() {
        var step by mutableStateOf(OnboardingStep.WELCOME)
        var intro = ""
        var setup = ""
        compose.setContent {
            intro = stringResource(Res.string.onboarding_progress_description, stringResource(Res.string.onboarding_part_intro), 1, OnboardingStep.entries.size)
            setup = stringResource(Res.string.onboarding_progress_description, stringResource(Res.string.onboarding_part_setup), 5, OnboardingStep.entries.size)
            ViolinTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    OnboardingScreen(OnboardingState(step, a4Hz = 440, a4OptionsHz = listOf(440, 441, 442, 443), tolerance = TolerancePreset.INTERMEDIATE), onIntent = {})
                }
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(intro).assertCountEquals(1)
        step = OnboardingStep.MICROPHONE
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(setup).assertCountEquals(1)
        compose.onAllNodesWithContentDescription(intro).assertCountEquals(0)
    }

    /**
     * A card of the tolerance of the setup is a choice that says its name, its caption and its cents — «Средний, чувствуется вибрато,
     * плюс-минус 8 центов» and «выбрано»; its bar and its parts are not stops of their own (spec 3.36.8).
     */
    @Test
    fun aCardOfTheToleranceSaysItsNameCaptionAndCents() {
        var said = ""
        var name = ""
        compose.setContent {
            name = stringResource(Res.string.tolerance_intermediate_name)
            val cents = TolerancePreset.INTERMEDIATE.cents
            val spoken = stringResource(
                Formats.plural(cents, Res.string.tolerance_cents_spoken_one, Res.string.tolerance_cents_spoken_few, Res.string.tolerance_cents_spoken_many),
                cents,
            )
            said = "$name, ${stringResource(Res.string.tolerance_intermediate_text)}, $spoken"
            ViolinTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    OnboardingScreen(OnboardingState(OnboardingStep.TOLERANCE, a4Hz = 440, a4OptionsHz = listOf(440, 441, 442, 443), tolerance = TolerancePreset.INTERMEDIATE), onIntent = {})
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(said)
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onAllNodesWithText(name).assertCountEquals(0)
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

    // The sheets of time (spec 3.36.3, TalkBack): the stepper, «Что играли», «Занятие не закончено».

    private val lessonStart = kotlin.time.Instant.parse("2026-09-27T17:55:00Z").toEpochMilliseconds()
    private val lessonTitles = mapOf(1L to "D-dur", 2L to "Kayser 3", 3L to "Concerto")

    /** D-dur 15 of 15, Kayser 10 of 10, the concerto from minute 40 on the bookmark: at 47 minutes 7 of 15, at 37 cut off. */
    private val lessonBlocks = PracticeBlocks(
        practiceStartedAtEpochMs = lessonStart,
        current = PieceBlock(3, lessonStart + 40 * MS_PER_MINUTE, 15 * MS_PER_MINUTE),
        finished = listOf(
            PieceBlock(1, lessonStart, 15 * MS_PER_MINUTE, endedAtEpochMs = lessonStart + 15 * MS_PER_MINUTE),
            PieceBlock(2, lessonStart + 15 * MS_PER_MINUTE, 10 * MS_PER_MINUTE, endedAtEpochMs = lessonStart + 25 * MS_PER_MINUTE),
        ),
    )

    @Test
    fun theStepperReadsItsNumberWithItsCaptionAndItsButtonsAreNamedButtons() {
        val config = PracticeConfig()
        var sheet = PracticeReducer.summarySheet(lessonStart, 47 * MS_PER_MINUTE, config, lessonBlocks, lessonTitles)
        repeat(2) { sheet = PracticeReducer.step(sheet, -1, config) }
        var spoken = ""
        var down = ""
        var up = ""
        compose.setContent {
            spoken = stringResource(
                Res.string.practice_stepper_description_was,
                Formats.minutesInWords(37 * MS_PER_MINUTE), "17:55", "18:32", Formats.minutesInWords(47 * MS_PER_MINUTE),
            )
            down = stringResource(Res.string.practice_step_down, config.editStepMinutes)
            up = stringResource(Res.string.practice_step_up, config.editStepMinutes)
            ViolinTheme { SummarySheetContent(sheet, config.editStepMinutes, TimeZone.UTC, onStep = {}) }
        }
        // «37 минут, с 17:55 до 18:32, было 47 минут» — one node, the number and its caption
        compose.onNodeWithContentDescription(spoken).assertExists()
        listOf(down, up).forEach { name ->
            compose.onNodeWithContentDescription(name).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsEnabled()
        }
    }

    @Test
    fun theButtonsOfAStepperAtItsLimitsAreOff() {
        val config = PracticeConfig()
        val sheet = PracticeReducer.summarySheet(lessonStart, 4 * MS_PER_MINUTE, config)
        var down = ""
        var up = ""
        compose.setContent {
            down = stringResource(Res.string.practice_step_down, config.editStepMinutes)
            up = stringResource(Res.string.practice_step_up, config.editStepMinutes)
            ViolinTheme { SummarySheetContent(sheet, config.editStepMinutes, TimeZone.UTC, onStep = {}) }
        }
        compose.onNodeWithContentDescription(down).assertIsNotEnabled()
        compose.onNodeWithContentDescription(up).assertIsNotEnabled()
    }

    @Test
    fun whatWasPlayedIsAGroupWithItsNameAndEachRowIsOneSentence() {
        val config = PracticeConfig()
        val whole = PracticeReducer.summarySheet(lessonStart, 47 * MS_PER_MINUTE, config, lessonBlocks, lessonTitles)
        var group = ""
        var done = ""
        var part = ""
        compose.setContent {
            group = stringResource(Res.string.block_played_title)
            done = stringResource(Res.string.practice_played_done_description, "Kayser 3", Formats.minutesInWords(10 * MS_PER_MINUTE))
            part = stringResource(Res.string.practice_pair_description, "Concerto", stringResource(Res.string.block_part_of, 7, 15))
            ViolinTheme { SummarySheetContent(whole, config.editStepMinutes, TimeZone.UTC, onStep = {}) }
        }
        compose.onNodeWithContentDescription(group).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
        compose.onNodeWithContentDescription(done).assertExists()
        compose.onNodeWithContentDescription(part).assertExists()
        // the words of a row are not read one by one
        compose.onAllNodesWithText("Kayser 3").assertCountEquals(0)
    }

    @Test
    fun aBlockCutOffIsReadAsNotFitted() {
        val config = PracticeConfig()
        var cut = PracticeReducer.summarySheet(lessonStart, 47 * MS_PER_MINUTE, config, lessonBlocks, lessonTitles)
        repeat(2) { cut = PracticeReducer.step(cut, -1, config) }
        var dropped = ""
        compose.setContent {
            dropped = stringResource(Res.string.practice_played_dropped_description, "Concerto")
            ViolinTheme { SummarySheetContent(cut, config.editStepMinutes, TimeZone.UTC, onStep = {}) }
        }
        compose.onNodeWithContentDescription(dropped).assertExists()
    }

    @Test
    fun theTitleOfTheForgottenPracticeIsAHeading() {
        val start = kotlin.time.Instant.parse("2026-09-27T17:38:00Z").toEpochMilliseconds()
        val practice = RunningPractice(start, lastSoundEpochMs = start + 64 * MS_PER_MINUTE)
        val endings = ForgottenPractice.endings(practice, start + 192 * MS_PER_MINUTE, PracticeConfig())
        var title = ""
        compose.setContent {
            title = stringResource(Res.string.practice_forgotten_title).uppercase()
            ViolinTheme { ForgottenSheetContent(PracticePrompt.Forgotten(practice), endings = { endings }, zone = TimeZone.UTC) }
        }
        compose.onNodeWithText(title).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // The sheets of progress (spec 3.36.3, TalkBack): the recap, the gift, «Трофеи», «Имя и фото».

    /** A recap of [minutes] after [before] of practice: 212 notes, two elements; [road], [streak]. */
    private fun recapOf(minutes: Long, before: Long, road: RecapRoad, streak: Int, extended: Boolean = streak > 0): PracticeRecap {
        val duration = minutes * MS_PER_MINUTE
        val sources = JourneyRules.taktsBySource(212, duration, JourneyConfig(), 2)
        val progress = ProgressConfig()
        return PracticeRecap(
            durationMs = duration, dayTotalMs = null, takts = sources.total, sources = sources, road = road,
            streakDays = streak, streakExtended = extended,
            levelBefore = Progress.levelOf(before, progress), levelAfter = Progress.levelOf(before + duration, progress),
        )
    }

    /** 47 h 17 min before the practice: level 5, «Гаммы», 2 h 43 min to the 6th — the mockup. */
    private val mockupTotal = (47 * 60 + 17) * MS_PER_MINUTE

    @Test
    fun theRecapReadsAsOneParagraphOnItsTitleAndItsButtonsStayButtons() {
        // 47 minutes: 71 + 94 + 60 = 225 takts; enough for Prague; eight days, grown today; level 5, 1 h 56 min to the 6th
        val recap = recapOf(47, mockupTotal, RecapRoad.Leg(nextIndex = 5, price = 3_000, balanceBefore = 2_900, balanceAfter = 3_125), streak = 8)
        var head = ""
        var takts = ""
        var enough = ""
        var days = ""
        var level = ""
        var travel = ""
        var done = ""
        compose.setContent {
            val title = stringResource(Res.string.recap_title)
            head = stringResource(Res.string.recap_description_head, title, Formats.minutesInWords(47 * MS_PER_MINUTE))
            takts = stringResource(Res.string.journey_earned, taktsInWords(225))
            enough = stringResource(Res.string.recap_road_enough, cityToOf(5))
            days = stringResource(
                Formats.plural(8, Res.string.practice_streak_days_description_one, Res.string.practice_streak_days_description_few, Res.string.practice_streak_days_description_many),
                8,
            )
            val names = stringArrayResource(Res.array.progress_level_names)
            level = stringResource(Res.string.recap_description_level, 5, names[4], 6, Formats.remainingTime(116 * MS_PER_MINUTE))
            travel = stringResource(Res.string.home_travel)
            done = stringResource(Res.string.profile_done)
            ViolinTheme {
                Column {
                    RecapSheetContent(recap, onTravel = {}, onDone = {}, low = false, animated = false)
                    RecapButtons(onDone = {})
                }
            }
        }
        // one paragraph on the title, a heading: «Занятие сохранено, 47 мин. Плюс 225 тактов: … Хватает до Праги. 8 дней подряд. …»
        val paragraph = descriptions().single { it.startsWith(head) }
        compose.onNodeWithContentDescription(paragraph).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        listOf(enough, days, level).forEach { assertTrue("«$it» in «$paragraph»", paragraph.contains(it)) }
        // the parts are not read one by one: the rolled number, the takts of the sources, «+1 день» — no «+» anywhere as a word
        compose.onAllNodesWithText(takts).assertCountEquals(0)
        compose.onAllNodesWithText("+", substring = true).assertCountEquals(0)
        // and the two answers are still buttons: the paragraph did not swallow «В дорогу» (R-9)
        listOf(travel, done).forEach { word ->
            compose.onNodeWithText(word).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsEnabled()
        }
    }

    @Test
    fun aRecapWithoutAStreakSaysNoneAndANewLevelIsSaidAsNew() {
        // 2 h 50 min over 47 h 17 min: level 6, «Этюды», 49 h 53 min to the 7th; an old practice saved days later — a streak of 0
        val recap = recapOf(170, mockupTotal, RecapRoad.NotStarted, streak = 0)
        var head = ""
        var levelUp = ""
        var noDays = ""
        compose.setContent {
            head = stringResource(Res.string.recap_description_head, stringResource(Res.string.recap_title), Formats.minutesInWords(170 * MS_PER_MINUTE))
            val names = stringArrayResource(Res.array.progress_level_names)
            levelUp = stringResource(Res.string.recap_description_level_up, 6, names[5], 7, Formats.remainingTime((49 * 60 + 53) * MS_PER_MINUTE))
            noDays = stringResource(
                Formats.plural(0, Res.string.practice_streak_days_description_one, Res.string.practice_streak_days_description_few, Res.string.practice_streak_days_description_many),
                0,
            )
            ViolinTheme { RecapSheetContent(recap, onTravel = {}, onDone = {}, low = false, animated = false) }
        }
        val paragraph = descriptions().single { it.startsWith(head) }
        assertTrue("«$levelUp» in «$paragraph»", paragraph.contains(levelUp))
        assertFalse("a streak of 0 is not said: «$paragraph»", paragraph.contains(noDays))
    }

    @Test
    fun theGiftReadsAsOneParagraphWithTheTrophyAfterIt() {
        val gift = Gift(hours = 50, index = 2, awardedDate = LocalDate(2026, 9, 27), next = NextTrophy(100, index = 3, remainingMs = 50 * 60 * MS_PER_MINUTE))
        var said = ""
        var next = ""
        compose.setContent {
            val trophies = stringArrayResource(Res.array.progress_trophy_names)
            val hours = stringResource(
                Formats.plural(50, Res.string.gift_hours_one, Res.string.gift_hours_few, Res.string.gift_hours_many),
                Formats.grouped(50),
            )
            // «Новый трофей: Струна, 50 часов за скрипкой, 27 сентября.» and «Дальше — Смычок, 100 ч, ещё 50 ч»
            said = stringResource(Res.string.gift_description, stringResource(Res.string.gift_title), trophies[2], hours, Formats.dayAndMonth(gift.awardedDate))
            next = stringResource(Res.string.gift_next_description, trophies[3], Formats.hoursMark(100), Formats.remainingTime(50 * 60 * MS_PER_MINUTE))
            ViolinTheme { GiftSheetContent(gift, low = false, animated = false) }
        }
        val paragraph = descriptions().single { it.startsWith(said) }
        compose.onNodeWithContentDescription(paragraph).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertTrue("«$next» in «$paragraph»", paragraph.contains(next))
    }

    @Test
    fun theTrophiesAreAHeadingWithTheirCountAndTheNearestIsSaidToBeNext() {
        val lines = ProgressReducer.trophyLines(mockupTotal, listOf(Trophy(1, LocalDate(2026, 6, 14), shown = true), Trophy(10, LocalDate(2026, 7, 2), shown = true)), ProgressConfig())
        var heading = ""
        var nearest = ""
        compose.setContent {
            val names = stringArrayResource(Res.array.progress_trophy_names)
            heading = stringResource(Res.string.trophies_heading_description, stringResource(Res.string.trophies_title), stringResource(Res.string.trophies_count, 2, 10))
            nearest = stringResource(Res.string.trophies_line_next, names[2], Formats.hoursMark(50), Formats.remainingTime((2 * 60 + 43) * MS_PER_MINUTE))
            ViolinTheme { TrophiesSheetContent(lines, mockupTotal) }
        }
        // «Трофеи, 2 из 10» — a heading
        compose.onNodeWithContentDescription(heading).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        // «Струна, 50 ч, ещё 2 ч 43 мин, следующий»
        compose.onNodeWithContentDescription(nearest).assertExists()
    }

    @Test
    fun theFieldOfTheNameIsReadAsNameAndTheTitleIsAHeading() {
        var label = ""
        var title = ""
        compose.setContent {
            label = stringResource(Res.string.profile_name_label)
            title = stringResource(Res.string.path_name_photo).uppercase()
            ViolinTheme {
                NamePhotoSheetContent(PracticeSheet.Profile(nameDraft = "", importingPhoto = false), hasPhoto = false, photo = null, onIntent = {}, onPickPhoto = {})
            }
        }
        compose.onNode(hasSetTextAction() and hasContentDescription(label)).assertExists()
        compose.onNodeWithText(title).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // «Настройки» of R8 (spec 3.36.8, TalkBack): the header and the labels of the groups are headings; a segment of the reference is
    // «440 герц», of the tolerance «Средний, плюс-минус 8 центов», each a chosen radio button in a group named by its row, whose title is
    // not read apart; a row of «Данные» that waits for the other job is «недоступно» and its caption, whole, says why; the statistics is a
    // switch, on or off.

    /** «Настройки» with the group «Данные» in [data]. */
    @Composable
    private fun Settings(data: DataBlockState, analytics: Boolean = true) = ViolinTheme {
        SettingsScreen(
            state = SettingsState(440, UserSettings.A4_OPTIONS_HZ, TolerancePreset.INTERMEDIATE, SoundCaption.BuiltIn(BuiltInPreset.OFF), analyticsEnabled = analytics),
            onIntent = {},
            onBack = {},
            dataBlock = {
                DataGroup(data, analytics, onAnalyticsChange = {}, onOpenBackup = {}, onOpenRunningRestore = {}, onPickCopy = {}, onOpenPrivacy = {})
            },
            onLanguageClick = {},
        )
    }

    @Test
    fun theHeaderAndTheLabelsOfTheGroupsOfSettingsAreHeadings() {
        var words = emptyList<String>()
        compose.setContent {
            words = listOf(stringResource(Res.string.nav_settings)) +
                listOf(Res.string.settings_group_intonation, Res.string.settings_group_records, Res.string.backup_block_title, Res.string.settings_group_app)
                    .map { stringResource(it).uppercase() }
            Settings(DataBlockState(dateRead = true))
        }
        compose.waitForIdle()
        words.forEach { word -> compose.onNodeWithText(word).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)) }
    }

    @Test
    fun theSegmentsOfSettingsSayTheirHertzAndCentsInAGroupNamedByTheirRow() {
        var hertz = ""
        var middle = ""
        var reference = ""
        var tolerance = ""
        var title = ""
        var number = ""
        compose.setContent {
            hertz = stringResource(Res.string.a4_option_description, 440)
            val cents = TolerancePreset.INTERMEDIATE.cents
            middle = stringResource(Res.string.tolerance_intermediate_name) + ", " + stringResource(
                Formats.plural(cents, Res.string.tolerance_cents_spoken_one, Res.string.tolerance_cents_spoken_few, Res.string.tolerance_cents_spoken_many),
                cents,
            )
            title = stringResource(Res.string.settings_a4_title)
            reference = title + ", " + stringResource(Res.string.settings_a4_note)
            tolerance = stringResource(Res.string.settings_tolerance_title) + ", " + stringResource(Res.string.settings_tolerance_note)
            number = stringResource(Res.string.tolerance_cents, cents)
            Settings(DataBlockState(dateRead = true))
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(hertz).assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithContentDescription(middle).assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithContentDescription(reference).assertExists()
        compose.onNodeWithContentDescription(tolerance).assertExists()
        // the title of a row is the name of its group, the second line of a segment is in its description: neither is read apart
        compose.onAllNodesWithText(title).assertCountEquals(0)
        compose.onAllNodesWithText(number).assertCountEquals(0)
    }

    @Test
    fun aRowOfDataWaitingForTheOtherJobIsUnavailableAndItsCaptionSaysWhy() {
        var saving = ""
        var progress = ""
        var restore = ""
        var why = ""
        var analytics = ""
        compose.setContent {
            saving = stringResource(Res.string.backup_row_saving)
            progress = stringResource(Res.string.backup_percent, 56) + stringResource(Res.string.dot_separator) +
                stringResource(Res.string.backup_phase_part, stringResource(Res.string.backup_part_video), 7, 12)
            restore = stringResource(Res.string.backup_row_restore)
            why = stringResource(Res.string.restore_row_wait_copy)
            analytics = stringResource(Res.string.analytics_row)
            ViolinTheme {
                DataGroup(
                    state = DataBlockState(dateRead = true, running = DataRunning(restore = false, percent = 56, phase = JobPhase.Files(BackupPart.VIDEO, 7, 12))),
                    analyticsEnabled = false,
                    onAnalyticsChange = {},
                    onOpenBackup = {},
                    onOpenRunningRestore = {},
                    onPickCopy = {},
                    onOpenPrivacy = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasText(saving) and hasText(progress)).assertIsEnabled()
        compose.onNode(hasText(restore) and hasText(why)).assertIsNotEnabled()
        compose.onNodeWithText(analytics).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff()
    }

    // «Копия данных» and «Восстановить из копии» of R8 (spec 3.36.8, TalkBack): the headers of the screens and the titles of their
    // outcomes are headings; the passport of a copy is one phrase and its words are not read apart; the line of phases is one phrase of
    // its step of now, and the percent is the value of the bar, the number itself only drawn.

    /** The passport of a copy of the mockups made on 12 September, without its video; what is in the app now, smaller. */
    private fun passportState(): RestoreState {
        val counts = BackupCounts(sessions = 64, takes = 6, pieces = 12, pages = 48, practiceDays = 41, trophies = 3, level = 9, withSound = 50, videos = 6)
        val madeAt = LocalDate(2026, 9, 12).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + NOON_MS
        val manifest = BackupManifest(1, "1.4", 13, madeAt, "Pixel 10a", BackupPart.entries.toSet() - BackupPart.VIDEO, counts, mapOf(BackupPart.DATA to 12_000_000L))
        val now = BackupContents(BackupCounts(sessions = 5, pieces = 5, practiceDays = 38, level = 5), mapOf(BackupPart.DATA to 4_000_000L))
        return RestoreState(stage = RestoreStage.Ready(BackupCandidate.Copy("content://downloads/1", "копия.zip", PASSPORT_BYTES, manifest, missingBytes = 0), now))
    }

    @Test
    fun thePassportOfACopyIsOnePhraseAndItsWordsAreNotReadApart() {
        var said = ""
        var title = ""
        compose.setContent {
            title = stringResource(Res.string.restore_copy_from, Formats.recordDate(LocalDate(2026, 9, 12), withYear = false))
            val inside = listOf(
                stringResource(Formats.plural(41, Res.string.backup_count_days_one, Res.string.backup_count_days_few, Res.string.backup_count_days_many), 41),
                stringResource(Res.string.backup_count_level, 9),
                stringResource(Formats.plural(64, Res.string.backup_count_sessions_one, Res.string.backup_count_sessions_few, Res.string.backup_count_sessions_many), 64),
                stringResource(Formats.plural(12, Res.string.backup_count_pieces_one, Res.string.backup_count_pieces_few, Res.string.backup_count_pieces_many), 12),
                stringResource(Res.string.backup_chip_no_video),
            )
            said = listOf(title, Formats.fileSize(PASSPORT_BYTES), "Pixel 10a", stringResource(Res.string.restore_copy_version, "1.4")).joinToString(", ") +
                ": " + inside.joinToString(", ")
            ViolinTheme { RestoreScreen(state = passportState(), onIntent = {}, today = LocalDate(2026, 10, 2)) }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(said).assertExists()
        // the title and the chips of the card are in its phrase, not apart (the merged tree: what TalkBack gets) — the title as it is
        // drawn, its day kept to its month by a no-break space (review of stage 122), and as it is said
        compose.onAllNodes(hasWords(title)).assertCountEquals(0)
        compose.onAllNodesWithText("Pixel 10a", substring = true).assertCountEquals(0)
    }

    @Test
    fun theHeadersOfACopyAndARestoreAndTheTitlesOfTheirOutcomesAreHeadings() {
        var copyTitle = ""
        var saved = ""
        var restoreTitle = ""
        var job by mutableStateOf<BackupJob>(BackupJob.Idle)
        var restore by mutableStateOf(false)
        val counts = BackupCounts(sessions = 5, pieces = 2, practiceDays = 7)
        val contents = BackupContents(counts, mapOf(BackupPart.DATA to 300_000L))
        compose.setContent {
            copyTitle = stringResource(Res.string.backup_title)
            saved = stringResource(Res.string.backup_saved_title)
            restoreTitle = stringResource(Res.string.restore_title)
            ViolinTheme {
                if (restore) {
                    RestoreScreen(state = passportState(), onIntent = {}, today = LocalDate(2026, 10, 2))
                } else {
                    BackupScreen(BackupState(contents = contents, job = job, shareUpToBytes = SHARE_UP_TO), fileName = "копия.zip", onIntent = {})
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(copyTitle).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val manifest = BackupManifest(1, "1.4", 13, 0L, "Pixel 10a", BackupPart.entries.toSet(), counts, contents.bytes)
        job = BackupJob.Saved("копия.zip", bytes = 300_000L, place = null, manifest = manifest)
        compose.waitForIdle()
        compose.onNodeWithText(saved).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        // the saved copy has ✕ and no title in its header
        compose.onAllNodesWithText(copyTitle).assertCountEquals(0)
        restore = true
        compose.waitForIdle()
        compose.onNodeWithText(restoreTitle).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun theLineOfPhasesIsOnePhraseOfItsStepAndThePercentIsTheBar() {
        var now = ""
        var percent = ""
        val all = BackupPart.entries.toSet()
        val job = BackupJob.Saving("копия.zip", visible = true, progress = BackupProgress(BackupPart.VIDEO, 7, 12, doneBytes = 56, totalBytes = 100), parts = all, filled = all)
        compose.setContent {
            now = stringResource(Res.string.backup_phase_now, stringResource(Res.string.backup_phase_part, stringResource(Res.string.backup_part_video), 7, 12))
            percent = stringResource(Res.string.backup_percent, 56)
            ViolinTheme {
                BackupScreen(BackupState(contents = BackupContents(BackupCounts(sessions = 5), mapOf(BackupPart.DATA to 100L)), job = job, shareUpToBytes = SHARE_UP_TO), fileName = "копия.zip", onIntent = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(now).assertExists()
        compose.onAllNodesWithText(percent).assertCountEquals(0)
        // its value, not only a bar: a bar that went on its own would be «выполняется» to TalkBack, not «56 %» (review of stage 122)
        compose.onNode(
            SemanticsMatcher("a bar at 56 %") { node ->
                node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.let { abs(it.current - PERCENT_56) < BAR_SLACK && it.range == 0f..1f } == true
            },
        ).assertExists()
    }

    /** A text whose words are [words], whether its numbers are kept to their words by a no-break space or not. */
    private fun hasWords(words: String) = SemanticsMatcher("the words «$words»") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.replace('\u00A0', ' ') == words.replace('\u00A0', ' ') } == true
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

        /** The plate of the journey short of Prague: 472 of 1 600 in the purse. */
        const val SHORT = "не хватает 1\u00A0128"
        const val ABOUT = "примерно 4\u00A0занятия"
        const val SAID = "не хватает 1\u00A0128 тактов, примерно 4\u00A0занятия"

        /** 17 h 27 min — the month of the mockups. */
        const val SEPTEMBER_MS = (17 * 60 + 27) * MS_PER_MINUTE

        /** The purse of the mockups: enough for Cremona and far beyond. */
        const val PURSE = 47_884L

        /** The weight of the passport of a copy: 3,4 ГБ. */
        const val PASSPORT_BYTES = 3_650_722_202L
        const val NOON_MS = 12 * 60 * 60 * 1_000L

        /** «Отправить…» up to 200 МБ (spec 5.14). */
        const val SHARE_UP_TO = 200L * 1024 * 1024

        /** 56 of 100 bytes moved, and how near a bar's value may be to it. */
        const val PERCENT_56 = 0.56f
        const val BAR_SLACK = 0.001f
    }
}
