package com.violinjourney.app.feature.sound

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.EqBand
import com.violinjourney.app.core.domain.sound.SoundBlock
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.UserPreset
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_block_title
import com.violinjourney.app.shared.resources.backing_gain
import com.violinjourney.app.shared.resources.backing_heard_violin
import com.violinjourney.app.shared.resources.backing_heard_with
import com.violinjourney.app.shared.resources.backing_offset_recorded
import com.violinjourney.app.shared.resources.backing_preparing
import com.violinjourney.app.shared.resources.backing_recorded_in
import com.violinjourney.app.shared.resources.backing_recorded_in_latency
import com.violinjourney.app.shared.resources.backing_recorded_wired
import com.violinjourney.app.shared.resources.backing_take_unprepared
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.piece_delete_confirm
import com.violinjourney.app.shared.resources.session_player_play
import com.violinjourney.app.shared.resources.sound_ab_original
import com.violinjourney.app.shared.resources.sound_ab_processed
import com.violinjourney.app.shared.resources.sound_block_collapse
import com.violinjourney.app.shared.resources.sound_block_compressor
import com.violinjourney.app.shared.resources.sound_block_eq
import com.violinjourney.app.shared.resources.sound_block_expand
import com.violinjourney.app.shared.resources.sound_block_output
import com.violinjourney.app.shared.resources.sound_block_reverb
import com.violinjourney.app.shared.resources.sound_block_switch
import com.violinjourney.app.shared.resources.sound_everyone_title
import com.violinjourney.app.shared.resources.sound_limiter_note
import com.violinjourney.app.shared.resources.sound_listen_latest
import com.violinjourney.app.shared.resources.sound_listen_on
import com.violinjourney.app.shared.resources.sound_listen_other
import com.violinjourney.app.shared.resources.sound_meter_none
import com.violinjourney.app.shared.resources.sound_meter_output
import com.violinjourney.app.shared.resources.sound_param_output
import com.violinjourney.app.shared.resources.sound_preset_names
import com.violinjourney.app.shared.resources.sound_reset
import com.violinjourney.app.shared.resources.sound_session_row
import com.violinjourney.app.shared.resources.sound_share
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.numbers
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Звук» of R5 (spec 3.36.5, 5.29 R5) by what a finger, an eye and TalkBack meet: every card closed when it opens, its header one button
 * «1, Эквалайзер, …» with its switch apart; the player at the bottom covers nothing of the cards; in a window lower than 700 the meter
 * stands in the line of the time and A/B is the compact one, pressed over 48; lying — the bar and the presets on the left over the
 * player, the cards on the right; the line «Слушать на» opens the sheet of the recordings, the one listened on marked; a preset of the
 * user's own goes by a long press; the screen of everyone resets and does not share; while the backing is made the switches sleep.
 *
 * Every screen is laid out in a window of its own size, whatever the device's ([WINDOW], and the window the panel reads —
 * [LocalWindowInfo]); the words are read in the composition, in the language of the device.
 */
@RunWith(AndroidJUnit4::class)
class SoundScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<SoundIntent>()
    private val words = mutableMapOf<String, String>()

    /** Nothing plays through the chain: the meter at rest. */
    private val meters = mutableStateOf<SoundMeters?>(null)

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    /** The words of the composition in [tag], whatever the device speaks: Compose reads the language of the process when it composes. */
    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(width: Dp, height: Dp, fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val window = with(density) { Window(IntSize(width.roundToPx(), height.roundToPx())) }
        CompositionLocalProvider(LocalWindowInfo provides window, LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.requiredSize(width, height).testTag(WINDOW)) { content() }
        }
    }

    private fun show(state: SoundState, width: Dp = 412.dp, height: Dp = 800.dp, fontScale: Float = 1f) = show({ state }, width, height, fontScale)

    /** [state] is read in the composition: a test may change what the screen shows and keep the screen (its scroll). */
    private fun show(state: () -> SoundState, width: Dp = 412.dp, height: Dp = 800.dp, fontScale: Float = 1f) {
        compose.setContent {
            words[PLAY] = stringResource(Res.string.session_player_play)
            words[A] = stringResource(Res.string.sound_ab_original)
            words[B] = stringResource(Res.string.sound_ab_processed)
            words[EQ] = stringResource(Res.string.sound_block_eq)
            words[EQ_SWITCH] = stringResource(Res.string.sound_block_switch, stringResource(Res.string.sound_block_eq))
            words[EXPAND] = stringResource(Res.string.sound_block_expand)
            words[BACKING] = stringResource(Res.string.backing_block_title)
            words[OUTPUT] = stringResource(Res.string.sound_meter_output)
            words[TITLE] = stringResource(Res.string.sound_session_row)
            words[EVERYONE] = stringResource(Res.string.sound_everyone_title)
            words[SHARE] = stringResource(Res.string.sound_share)
            words[RESET] = stringResource(Res.string.sound_reset)
            words[OTHER] = stringResource(Res.string.sound_listen_other)
            words[PREPARING] = stringResource(Res.string.backing_preparing)
            words[WITH] = stringResource(Res.string.backing_heard_with)
            words[VIOLIN] = stringResource(Res.string.backing_heard_violin)
            words[HALL] = stringArrayResource(Res.array.sound_preset_names)[BuiltInPreset.CHAMBER_HALL.ordinal]
            words[LENGTH] = Formats.duration(PLAYER.durationMs)
            words[COLLAPSE] = stringResource(Res.string.sound_block_collapse)
            words[AS_RECORDED] = stringResource(Res.string.backing_offset_recorded, SoundFormats.signedMs(RECORDED_OFFSET))
            words[IN_BUDS_LATENCY] = stringResource(Res.string.backing_recorded_in_latency, BUDS, SoundFormats.signedMs(LATENCY))
            words[IN_BUDS] = stringResource(Res.string.backing_recorded_in, BUDS)
            words[WIRED] = stringResource(Res.string.backing_recorded_wired)
            words[UNPREPARED] = stringResource(Res.string.backing_take_unprepared)
            words[DELETE] = stringResource(Res.string.piece_delete_confirm)
            words[LISTEN_ON] = stringResource(Res.string.sound_listen_on)
            words[LISTEN_LATEST] = stringResource(Res.string.sound_listen_on) + stringResource(Res.string.dot_separator) + stringResource(Res.string.sound_listen_latest)
            words[OUTPUT_GAIN] = stringResource(Res.string.sound_param_output)
            words[LIMITER_NOTE] = stringResource(Res.string.sound_limiter_note)
            words[BACKING_GAIN] = stringResource(Res.string.backing_gain)
            words[NO_LEVEL] = stringResource(Res.string.sound_meter_none)
            listOf(Res.string.sound_block_eq, Res.string.sound_block_compressor, Res.string.sound_block_reverb, Res.string.sound_block_output)
                .forEachIndexed { index, title -> words["$CARD${index + 1}"] = stringResource(title) }
            ViolinTheme {
                InWindow(width, height, fontScale) {
                    SoundScreen(
                        state = state(),
                        meters = meters,
                        onIntent = { intents += it },
                        config = CONFIG,
                        backingConfig = BackingConfig(),
                        zone = UTC,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun word(key: String): String = words.getValue(key)

    private fun leftOf(node: SemanticsNodeInteraction): Dp = node.getUnclippedBoundsInRoot().left - compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot().left

    private fun rightOf(node: SemanticsNodeInteraction): Dp = node.getUnclippedBoundsInRoot().right - compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot().left

    private fun assertNear(expected: Dp, actual: Dp, what: String) = assertTrue("$what: $actual, not $expected", abs(expected.value - actual.value) <= 1f)

    /** The header of a card: one button that says its number, its name and its values. */
    private fun header(number: Int, name: String) = compose.onNode(hasContentDescription("$number, $name", substring = true))

    /**
     * Spec 3.36.5: «При открытии экрана все блоки свёрнуты». A header is one button — «1, Эквалайзер, …» and «Развернуть» — that opens
     * its card; the switch is a button of its own; no slider of a card is there, only the wave of the player.
     */
    @Test
    fun whenTheScreenOpensEveryCardIsClosedAndItsHeaderIsOneButton() {
        show(RECORDING)
        val eq = header(1, word(EQ))
        eq.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(word(EXPAND), eq.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertCountEquals(1)
        eq.performClick()
        compose.runOnIdle { assertEquals(SoundIntent.CardToggled(SoundCard.EQ), intents.last()) }
        compose.onNodeWithContentDescription(word(EQ_SWITCH)).performClick()
        compose.runOnIdle { assertEquals(SoundIntent.BlockSwitched(SoundBlock.EQ, on = false), intents.last()) }
    }

    /** «Прокручивается середина»: the last card — «Минусовка», the fifth — scrolled into view ends over the panel of the player. */
    @Test
    fun thePlayerAtTheBottomCoversNothingOfTheCards() {
        show(UNDER_BACKING)
        val last = header(BACKING_NUMBER, word(BACKING))
        last.performScrollTo()
        compose.waitForIdle()
        val card = last.getUnclippedBoundsInRoot()
        val panelTop = compose.onNodeWithContentDescription(word(PLAY)).getUnclippedBoundsInRoot().top - REGULAR_TOP
        assertTrue("«Минусовка» ends over the panel: ${card.bottom} ≤ $panelTop", card.bottom <= panelTop + 0.5.dp)
    }

    /**
     * Upright on a phone (412 × 800): «play» 56; A/B the whole width, its halves «A, оригинал» and «B, обработка» to TalkBack; the backing
     * under it; the meter under all of them, told «выход».
     */
    @Test
    fun uprightThePlayerHasPlay56TheLargeAbAndTheMeterUnderThem() {
        show(UNDER_BACKING)
        val play = compose.onNodeWithContentDescription(word(PLAY))
        play.assertHeightIsEqualTo(56.dp)
        val a = compose.onNodeWithContentDescription(word(A))
        val b = compose.onNodeWithContentDescription(word(B))
        a.assertIsNotSelected()
        b.assertIsSelected()
        assertNear(SCREEN_PADDING, leftOf(a), "A at the edge of the panel")
        assertNear(412.dp - SCREEN_PADDING, rightOf(b), "B at the other edge: the whole width")
        val meter = compose.onNodeWithContentDescription(word(OUTPUT)).getUnclippedBoundsInRoot()
        val with = compose.onNodeWithText(word(WITH)).getUnclippedBoundsInRoot()
        assertTrue("the meter under the backing: $meter, the backing $with", meter.top >= with.bottom)
        a.performClick()
        compose.runOnIdle { assertEquals(SoundIntent.OriginalSelected(original = true, held = false), intents.last()) }
    }

    /**
     * Lower than 700 (360 × 640, spec 3.36.5): «play» 48; the meter in the line of the time, between the time and the length; A/B the
     * compact one the whole width, pressed over 48.
     */
    @Test
    fun inALowWindowTheMeterStandsInTheLineOfTheTimeAndTheAbIsCompact() {
        show(RECORDING, width = 360.dp, height = 640.dp)
        val play = compose.onNodeWithContentDescription(word(PLAY))
        play.assertHeightIsEqualTo(48.dp)
        val meter = compose.onNodeWithContentDescription(word(OUTPUT)).getUnclippedBoundsInRoot()
        val length = compose.onNodeWithText(word(LENGTH)).getUnclippedBoundsInRoot()
        val playBounds = play.getUnclippedBoundsInRoot()
        assertTrue("the meter on the row of «play»: $meter, «play» $playBounds", meter.top >= playBounds.top && meter.bottom <= playBounds.bottom)
        assertTrue("the meter before the length: $meter, the length $length", meter.right <= length.left)
        val a = compose.onNodeWithContentDescription(word(A))
        a.assertHeightIsEqualTo(48.dp)
        val b = compose.onNodeWithContentDescription(word(B))
        assertTrue("A/B under the row of «play»", a.getUnclippedBoundsInRoot().top >= playBounds.bottom - 0.5.dp)
        assertNear(360.dp - SCREEN_PADDING, rightOf(b), "the compact A/B the whole width")
    }

    /**
     * Lying (892 × 412, spec 3.36.5): on the left, 340 wide, the bar and the presets over the player — its rows 16 at the edge; the cards
     * on the right, 8 past the meeting of the columns.
     */
    @Test
    fun lyingTheBarAndThePresetsStandLeftOverThePlayerAndTheCardsRight() {
        show(RECORDING, width = 892.dp, height = 412.dp)
        val title = compose.onNode(isHeading() and hasText(word(TITLE)))
        val play = compose.onNodeWithContentDescription(word(PLAY))
        val hall = compose.onNode(hasText(word(HALL)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        assertTrue("the title in the left column: ${rightOf(title)}", rightOf(title) <= LEFT_COLUMN)
        assertNear(SCREEN_PADDING, leftOf(play), "«play» at the edge of the left column")
        play.assertHeightIsEqualTo(48.dp)
        assertTrue("the presets over the player", hall.getUnclippedBoundsInRoot().bottom <= play.getUnclippedBoundsInRoot().top)
        assertNear(LEFT_COLUMN + COLUMN_GAP, leftOf(header(1, word(EQ))), "the cards in the right column")
    }

    /**
     * «Звук записей» (spec 3.36.5): «Сбросить» at the right of the bar and no «Поделиться»; the line «Слушать на» and its «Другая» open
     * the sheet of the recordings.
     */
    @Test
    fun theScreenOfEveryoneResetsAndOpensTheSheetOfTheRecordingsFromItsLine() {
        show(EVERYONE_STATE)
        compose.onNode(isHeading() and hasText(word(EVERYONE))).assertIsDisplayed()
        compose.onNodeWithText(word(RESET)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(word(SHARE)).assertCountEquals(0)
        compose.onNodeWithText(word(OTHER)).performClick()
        compose.runOnIdle { assertEquals(SoundIntent.ListenOnClicked, intents.last()) }
    }

    /** A recording shares from its bar and has no «Сбросить» (spec 3.36.5: its reset is «Как у всех»). */
    @Test
    fun aRecordingSharesFromItsBarAndHasNoReset() {
        show(RECORDING)
        compose.onNodeWithContentDescription(word(SHARE)).performClick()
        compose.runOnIdle { assertEquals(SoundIntent.ShareClicked, intents.last()) }
        compose.onAllNodesWithText(word(RESET)).assertCountEquals(0)
    }

    /**
     * The sheet «Слушать на…» (spec 3.36.5): the cards of the recordings; the one listened on — here the older, not the newest — is
     * marked chosen, and no other is; a card chooses, telling its id.
     */
    @Test
    fun theSheetOfTheRecordingsMarksTheOneListenedOnAndATouchChoosesAnother() {
        show(EVERYONE_STATE.copy(recording = RecordingName(1, null, MENUET, 0), dialog = SoundDialog.PickRecording))
        compose.onNode(hasContentDescription(MENUET, substring = true) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsSelected()
        val newest = compose.onNode(hasContentDescription(OTHER_PIECE, substring = true) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        newest.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        newest.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SoundIntent.RecordingPicked(2), intents.last()) }
    }

    /** «Слушать на · последняя со звуком» while the newest recording is listened on, and «Слушать на» once it is an older one (spec 3.36.5). */
    @Test
    fun theLineOfTheRecordingSaysTheLatestOnlyWhileTheNewestIsListenedOn() {
        var state by mutableStateOf(EVERYONE_STATE)
        show({ state })
        compose.onNodeWithText(word(LISTEN_LATEST)).assertIsDisplayed()
        state = EVERYONE_STATE.copy(recording = RecordingName(1, null, MENUET, 0))
        compose.waitForIdle()
        compose.onNodeWithText(word(LISTEN_ON)).assertIsDisplayed()
        compose.onAllNodesWithText(word(LISTEN_LATEST)).assertCountEquals(0)
    }

    /**
     * «Слушать на…» opens with the one listened on in sight (spec 3.36.5): deep in a long day it stands first in the sheet, whole; near
     * the top of its day the header of the day stands over it. The sheet's words in a box of 360 × 400.
     */
    @Test
    fun theSheetOfTheRecordingsOpensWithTheOneListenedOnInSight() {
        var current by mutableStateOf(DEEP_IN_THE_DAY)
        var day = ""
        compose.setContent {
            day = Formats.recordDayHeader(DAY, withYear = false)
            val density = LocalDensity.current
            ViolinTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                    Box(Modifier.requiredSize(SHEET_WIDTH, SHEET_HEIGHT).testTag(WINDOW)) {
                        // each opening a sheet of its own, as the sheet is made anew each time it comes up
                        key(current) { ListenOnSheetContent(LONG_DAY, current, DAY, UTC, onPick = {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val box = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        fun assertTheOneListenedOnIsWhole() {
            val card = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("the one listened on whole in the sheet: $card in $box", card.top >= box.top - 0.5.dp && card.bottom <= box.bottom + 0.5.dp)
        }
        assertTheOneListenedOnIsWhole()

        current = NEAR_THE_TOP
        compose.waitForIdle()
        assertTheOneListenedOnIsWhole()
        // the header of the day — a heading row with the date in it — stands over it
        compose.onNodeWithText(day).assertIsDisplayed()
    }

    /**
     * A preset of the user's own goes by a long press (spec 3.17, 3.36.5), told to TalkBack as a verb — «…удерживайте, чтобы удалить»;
     * a built-in one has no long press at all.
     */
    @Test
    fun aPresetOfTheUsersOwnIsRemovedByALongPress() {
        show(RECORDING)
        val mine = compose.onNode(hasText(MY_HALL.name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        assertEquals(word(DELETE), mine.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
        mine.performScrollTo().performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(SoundIntent.PresetLongPressed(PresetRef.User(MY_HALL.id)), intents.last()) }
        val hall = compose.onNode(hasText(word(HALL)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        hall.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }

    /**
     * «Минусовка» opened (spec 3.36.5): its header says «Свернуть»; under its sliders «Как записано · +200 мс» and the line of the
     * headphones — «Записано в Pixel Buds · +200 мс учтено».
     */
    @Test
    fun anOpenedBackingSaysHowItWasRecordedAndWhatWasAllowedFor() {
        show(UNDER_BACKING.copy(expanded = setOf(SoundCard.BACKING)))
        assertEquals(word(COLLAPSE), header(BACKING_NUMBER, word(BACKING)).fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        compose.onNodeWithText(word(AS_RECORDED)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(word(IN_BUDS_LATENCY)).performScrollTo().assertIsDisplayed()
    }

    /** Nothing added to the clocks — the name alone, without «· … учтено»; wired headphones — words of their own (spec 3.36.5). */
    @Test
    fun theLineOfTheHeadphonesSaysOnlyWhatTheTakeKeeps() {
        var recordedWith by mutableStateOf<RecordedWith>(RecordedWith.Wireless(BUDS, latencyMs = 0))
        show({ UNDER_BACKING.copy(expanded = setOf(SoundCard.BACKING), backing = UNDER_BACKING.backing!!.copy(recordedWith = recordedWith)) })
        compose.onNodeWithText(word(IN_BUDS)).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(word(IN_BUDS_LATENCY)).assertCountEquals(0)
        recordedWith = RecordedWith.Wired
        compose.waitForIdle()
        compose.onNodeWithText(word(WIRED)).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(word(IN_BUDS)).assertCountEquals(0)
    }

    /**
     * A backing that could not be prepared, the player saying so after the card was opened (spec 3.36.5): the card does not open and has
     * no chevron — its header is no button — and says why under its name; no slider of it, only the wave; no row of the backing in the
     * player.
     */
    @Test
    fun aBackingThatCouldNotBePreparedStaysShutThoughAskedOpen() {
        show(UNDER_BACKING.copy(player = PLAYER.copy(hasBacking = false), expanded = setOf(SoundCard.BACKING)))
        header(BACKING_NUMBER, word(BACKING)).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNode(hasContentDescription(word(UNPREPARED), substring = true)).assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertCountEquals(1)
        compose.onAllNodes(hasText(word(WITH)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(0)
    }

    /**
     * A card opened at the bottom of the middle is brought into sight whole over the player (spec 3.36.5): «Громкость», its header at
     * the edge of the panel of 360 × 640, opens — its last line, the note of the limiter, ends over the panel, not under it.
     */
    @Test
    fun aCardOpenedJustOverThePlayerIsBroughtIntoSightWhole() {
        var state by mutableStateOf(RECORDING)
        show({ state }, width = 360.dp, height = 640.dp)
        header(OUTPUT_NUMBER, word("$CARD$OUTPUT_NUMBER")).performScrollTo()
        compose.waitForIdle()
        state = RECORDING.copy(expanded = setOf(SoundCard.OUTPUT))
        compose.waitForIdle()
        val panelTop = compose.onNodeWithContentDescription(word(PLAY)).getUnclippedBoundsInRoot().top - COMPACT_TOP
        val note = compose.onNodeWithText(word(LIMITER_NOTE), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the note of the limiter ends over the panel: ${note.bottom} ≤ $panelTop", note.bottom <= panelTop + 0.5.dp)
        header(OUTPUT_NUMBER, word("$CARD$OUTPUT_NUMBER")).assertIsDisplayed()
    }

    /**
     * The value of a slider is never cut (5.29 R5): Spanish «Volumen del acompañamiento» in the narrow column lying (603 × 360 behind a
     * cutout) at the font 1.3 is wider than the card — its value, measured first, stands whole on one line at the right, and the name
     * goes on two lines at its space before it.
     */
    @Test
    fun inTheNarrowColumnLyingTheValueOfASliderStandsWholeBesideItsName() {
        speaking("es")
        show(UNDER_BACKING.copy(expanded = setOf(SoundCard.BACKING)), width = 603.dp, height = 360.dp, fontScale = 1.3f)
        val valueText = SoundFormats.decibels(UNDER_BACKING.backing!!.gainDb.toDouble(), signed = true)
        val value = compose.onNodeWithText(valueText, useUnmergedTree = true)
        assertWholeOnOneLine(value, valueText)
        val name = compose.onNodeWithText(word(BACKING_GAIN), useUnmergedTree = true)
        assertWordsWhole(name, word(BACKING_GAIN))
        val nameRight = name.getUnclippedBoundsInRoot().right
        val valueLeft = value.getUnclippedBoundsInRoot().left
        assertTrue("the name ends before the value: $nameRight ≤ $valueLeft", nameRight <= valueLeft + 0.5.dp)
    }

    /**
     * A name one word of which would not stand whole beside the room of the value steps down rather than break (5.29 R5): German
     * «Ausgangsverstärkung» in the narrow column lying at the font 1.3 — whole on one line, and so is its value.
     */
    @Test
    fun inTheNarrowColumnLyingALongWordOfASliderStepsDownRatherThanBreak() {
        speaking("de")
        show(RECORDING.copy(expanded = setOf(SoundCard.OUTPUT)), width = 603.dp, height = 360.dp, fontScale = 1.3f)
        assertWholeOnOneLine(compose.onNodeWithText(word(OUTPUT_GAIN), useUnmergedTree = true), word(OUTPUT_GAIN))
        // the value of the slider, beside its name — the same «+2 дБ» stands in the header's short values too
        val valueText = SoundFormats.decibels(RECORDING.settings.output.gainDb, signed = true)
        val value = compose.onNode(hasText(valueText) and hasAnySibling(hasText(word(OUTPUT_GAIN))), useUnmergedTree = true)
        assertWholeOnOneLine(value, valueText)
    }

    /**
     * The name of a card steps down rather than break (5.29 R5): Italian «Equalizzatore» and «Compressore» beside the switch of a phone of
     * 320 at the font 1.3 (125 dp for them, ≈ 137 needed at 16 sp) — each header stays one line of name over one of values, no taller
     * than that of «Sala», whose name is one short word (≈ 67 dp at this font); a word broken onto a second line would make it ≈ 87.
     */
    @Test
    fun onANarrowPhoneAtALargeFontTheNameOfACardStepsDownRatherThanBreak() {
        speaking("it")
        show(RECORDING, width = 320.dp, height = 640.dp, fontScale = 1.3f)
        val oneLine = header(REVERB_NUMBER, word("$CARD$REVERB_NUMBER")).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        (1..OUTPUT_NUMBER).forEach { number ->
            val height = header(number, word("$CARD$number")).getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue("the header of «${word("$CARD$number")}» is one line of name: $height, one short word $oneLine", height <= oneLine + 0.5.dp)
        }
    }

    /**
     * The line of the time of the compact panel in the narrow column lying (603 × 360 at the font 1.3, in Russian — «ограничитель» and
     * «выход» do not stand in it together with both times, 5.29 R5): what gives way gives way whole — the number of the meter ends
     * inside the meter, and the length, where it still stands, after the meter.
     */
    @Test
    fun inTheNarrowColumnLyingTheLineOfTheTimeKeepsTheMeterWhole() {
        speaking("ru")
        show(RECORDING, width = 603.dp, height = 360.dp, fontScale = 1.3f)
        val meter = compose.onNodeWithContentDescription(word(OUTPUT)).getUnclippedBoundsInRoot()
        val number = compose.onNode(hasText(word(NO_LEVEL)) and hasAnyAncestor(hasContentDescription(word(OUTPUT))), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("the number of the meter inside it: $number, the meter $meter", number.left >= meter.left - 0.5.dp && number.right <= meter.right + 0.5.dp)
        // the length gives way where the meter needs its room: then it is not placed at all
        val lengths = compose.onAllNodesWithText(word(LENGTH), useUnmergedTree = true)
        lengths.fetchSemanticsNodes().forEachIndexed { index, node ->
            if (node.layoutInfo.isPlaced) {
                val left = lengths[index].getUnclippedBoundsInRoot().left
                assertTrue("the length after the meter: $left, the meter ends at ${meter.right}", meter.right <= left + 0.5.dp)
            }
        }
    }

    /** [node]'s words go on to a next line only at a space, and nothing of them is cut. */
    private fun assertWordsWhole(node: SemanticsNodeInteraction, text: String) {
        val layout = node.textLayout()
        for (line in 0 until layout.lineCount - 1) {
            assertTrue("«$text» goes on at a space — ${layout.numbers(node)}", text[layout.getLineEnd(line) - 1].isWhitespace())
        }
        assertTrue("«$text» is not cut — ${layout.numbers(node)}", !layout.isLineEllipsized(layout.lineCount - 1) && !layout.didOverflowHeight)
    }

    /**
     * «Готовим минусовку…» (spec 5.25, 3.36.5): the words in the player, A/B and the backing seen and asleep — they answer nothing; the
     * cards go on answering.
     */
    @Test
    fun whileTheBackingIsMadeTheSwitchesOfThePlayerSleep() {
        show(UNDER_BACKING.copy(player = null, preparingBacking = true))
        compose.onNodeWithText(word(PREPARING)).assertIsDisplayed()
        compose.onNodeWithContentDescription(word(A)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(word(B)).assertIsNotEnabled()
        compose.onNode(hasText(word(WITH)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsNotEnabled()
        compose.onNode(hasText(word(VIOLIN)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsNotEnabled()
        header(1, word(EQ)).performClick()
        compose.runOnIdle { assertEquals(SoundIntent.CardToggled(SoundCard.EQ), intents.last()) }
    }

    private companion object {
        const val PLAY = "play"
        const val A = "a"
        const val B = "b"
        const val EQ = "eq"
        const val EQ_SWITCH = "eqSwitch"
        const val EXPAND = "expand"
        const val BACKING = "backing"
        const val OUTPUT = "output"
        const val TITLE = "title"
        const val EVERYONE = "everyone"
        const val SHARE = "share"
        const val RESET = "reset"
        const val OTHER = "other"
        const val PREPARING = "preparing"
        const val WITH = "with"
        const val VIOLIN = "violin"
        const val HALL = "hall"
        const val LENGTH = "length"
        const val WINDOW = "window"
        const val COLLAPSE = "collapse"
        const val AS_RECORDED = "asRecorded"
        const val IN_BUDS_LATENCY = "inBudsLatency"
        const val IN_BUDS = "inBuds"
        const val WIRED = "wired"
        const val UNPREPARED = "unprepared"
        const val DELETE = "delete"
        const val LISTEN_ON = "listenOn"
        const val LISTEN_LATEST = "listenLatest"
        const val OUTPUT_GAIN = "outputGain"
        const val LIMITER_NOTE = "limiterNote"
        const val BACKING_GAIN = "backingGain"
        const val NO_LEVEL = "noLevel"
        const val CARD = "card"
        const val MENUET = "Менуэт соль мажор"
        const val OTHER_PIECE = "Концерт ля минор"
        const val BACKING_NUMBER = 5
        const val OUTPUT_NUMBER = 4
        const val REVERB_NUMBER = 3
        const val BUDS = "Pixel Buds"
        const val LATENCY = 200
        const val RECORDED_OFFSET = 200

        // The layout of «Звук» (5.29 R5, SoundScreen.kt): the fields of the screen; lying, the left column of 340 on 892 and 8 past
        // the meeting of the columns; the regular panel stands 12 over «play».
        val SCREEN_PADDING = 16.dp
        val LEFT_COLUMN = 340.dp
        val COLUMN_GAP = 8.dp
        val REGULAR_TOP = 12.dp

        /** The compact panel stands 8 over «play» (5.29 R5: fields 8 / 16 / 10). */
        val COMPACT_TOP = 8.dp

        /** The words of the sheet «Слушать на…» on a phone, and a day of thirty recordings: one deep in it, and the second of it. */
        val SHEET_WIDTH = 360.dp
        val SHEET_HEIGHT = 400.dp
        const val DAY_OF_MONTH = 27
        val DAY: LocalDate = LocalDate(2026, 9, DAY_OF_MONTH)
        const val DEEP_IN_THE_DAY = 5L
        const val NEAR_THE_TOP = 29L

        val UTC: TimeZone = TimeZone.UTC
        val CONFIG = SoundConfig()
        val HALL_SETTINGS = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, CONFIG)
        val MY_HALL = UserPreset(id = 7, name = "Мой зал", settings = HALL_SETTINGS.copy(reverb = HALL_SETTINGS.reverb.copy(mix = 0.3)))
        val PLAYER = PlayerState(ready = true, positionMs = 65_000, durationMs = 125_000, processed = true)

        val RECORDING = SoundState(
            loading = false, mode = SoundMode.RECORDING, recording = RecordingName(1, null, MENUET, 0), own = false, settings = HALL_SETTINGS,
            caption = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), chips = SoundReducer.chipsOf(HALL_SETTINGS, listOf(MY_HALL), CONFIG),
            custom = false, canReset = false, savedHint = false, band = EqBand.BODY, details = false, player = PLAYER, listening = true,
            waveform = null, recordings = emptyList(), today = LocalDate(2026, 9, 27), affected = 0, dialog = null,
        )

        val UNDER_BACKING = RECORDING.copy(
            player = PLAYER.copy(hasBacking = true),
            backing = BackingBlockState(-6f, 240, RECORDED_OFFSET, title = "фортепиано", durationMs = 220_000, recordedWith = RecordedWith.Wireless(BUDS, LATENCY)),
        )

        private fun card(id: Long, piece: String, day: Int) = HistoryCard(
            id = id, title = null, startedAtEpochMs = 0, date = LocalDate(2026, 9, day), durationMs = 125_000, pieceTitle = piece,
            pieceId = id, hasAudio = true,
        )

        /** Thirty recordings of one day, newest first. */
        val LONG_DAY = (30L downTo 1L).map { card(it, "Этюд $it", DAY_OF_MONTH) }

        val EVERYONE_STATE = RECORDING.copy(
            mode = SoundMode.EVERYONE, recording = RecordingName(2, null, OTHER_PIECE, 0), canReset = true, affected = 23,
            recordings = listOf(card(2, OTHER_PIECE, 27), card(1, MENUET, 26)),
        )
    }
}
