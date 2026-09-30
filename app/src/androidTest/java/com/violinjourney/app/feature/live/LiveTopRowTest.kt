package com.violinjourney.app.feature.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.components.LiveDimens
import com.violinjourney.app.feature.live.components.LiveRecordKey
import com.violinjourney.app.feature.live.components.SettingsGear
import com.violinjourney.app.feature.live.components.StatusFit
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mode_play
import com.violinjourney.app.shared.resources.mode_tuning
import com.violinjourney.app.shared.resources.nav_settings
import com.violinjourney.app.shared.resources.record_start
import com.violinjourney.app.shared.resources.status_in_tune
import com.violinjourney.app.shared.resources.tuning_string_hz
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The top of Live of R6 (spec 3.36.6, 5.29 R6) by what a finger and an eye meet: «Игра | Настройка» stands whole in its row and
 * never under the touch of the gear, even on a phone of 320 at the font 1.5, where its words step down and it leaves the middle;
 * the whole height of the row answers; the letter and the hertz of a string stand in its 58 at the font 1.5; lying down the switcher
 * stands on the axis of its column — over the key — or as near to it as the gear's touch lets it, and in a low landscape it does not
 * jump when the mode changes and the word stands whole in «Настройка», not below its compact size.
 *
 * Live is laid out in a window of its own size, whatever the device's ([WINDOW], and a fake [LocalWindowInfo] too), on its plain
 * field (no picture): the geometry is the same. A window wider than the device is centred on it, so everything is measured from the
 * window, never from the root. The widening of a touch target under 48 is switched off, so the rows are measured as they lay
 * themselves out. The words are read in the composition, in the language of the process ([speaking]).
 */
@RunWith(AndroidJUnit4::class)
class LiveTopRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = mutableMapOf<String, String>()
    private val intents = mutableListOf<LiveIntent>()
    private var state by mutableStateOf(stateOf(LiveMode.PLAY))

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    /** The words of the composition in [tag], whatever the device speaks: Compose reads the language of the process when it composes. */
    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun stateOf(mode: LiveMode, locked: ViolinString? = null) =
        LiveReducer.stateOf(LiveTarget(mode, locked), IntonationConfig(), LiveSignal.Silence)

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(width: Dp, height: Dp, fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val base = LocalViewConfiguration.current
        val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
        val window = with(density) { Window(IntSize(width.roundToPx(), height.roundToPx())) }
        CompositionLocalProvider(
            LocalWindowInfo provides window,
            LocalViewConfiguration provides noWidening,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            Box(Modifier.requiredSize(width, height).testTag(WINDOW)) { content() }
        }
    }

    private fun show(width: Dp, height: Dp, fontScale: Float = 1f) {
        compose.setContent {
            words[PLAY] = stringResource(Res.string.mode_play)
            words[TUNING] = stringResource(Res.string.mode_tuning)
            words[SETTINGS] = stringResource(Res.string.nav_settings)
            words[RECORD] = stringResource(Res.string.record_start)
            words[IN_TUNE] = stringResource(Res.string.status_in_tune)
            IntonationConfig().let { config ->
                LiveReducer.stringHzOf(config).forEach { (string, hz) -> words[string.name] = stringResource(Res.string.tuning_string_hz, hz) }
            }
            ViolinTheme {
                InWindow(width, height, fontScale) {
                    LiveScreenLayout(
                        state = state,
                        onIntent = { intents += it },
                        slots = LiveSlots(
                            recordKey = { recording, enabled, alpha -> LiveRecordKey(recording, enabled, onClick = {}, alpha = alpha) },
                            gear = { enabled, modifier -> SettingsGear(onClick = {}, modifier = modifier, enabled = enabled) },
                        ),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun word(key: String): String = words.getValue(key)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    /** The window Live is laid out in, where the root has it: every position is measured from it. */
    private fun window(): DpRect = compose.onNodeWithTag(WINDOW).bounds()

    /** The segment of the switcher with [key]'s word: the node that answers a touch, its word merged into it. */
    private fun segment(key: String) = compose.onNodeWithText(word(key))

    /** The word itself, for its layout. */
    private fun segmentWord(key: String) = compose.onNodeWithText(word(key), useUnmergedTree = true)

    private fun gear() = compose.onNodeWithContentDescription(word(SETTINGS))

    /** The record key — the button TalkBack names «Начать запись». */
    private fun recordKey() = compose.onNodeWithContentDescription(word(RECORD))

    /** Upright the top row stands 12 in from the sides of the window (the disc of the gear 16, its touch 4 nearer). */
    private fun uprightRowStart(): Dp = window().left + LiveDimens.GearEnd - (LiveDimens.GearTouch - LiveDimens.GearSize) / 2

    /** Lying down it is the right column: after the panel of the ring and the column's own padding. */
    private fun lyingRowStart(): Dp {
        val window = window()
        return window.left + window.width * LiveDimens.LANDSCAPE_RING_PANEL_FRACTION + LiveDimens.LandscapePaddingStart
    }

    private fun assertTheSwitcherStandsWholeClearOfTheGear(what: String, rowStart: Dp) {
        assertWholeOnOneLine(segmentWord(PLAY), word(PLAY))
        assertWholeOnOneLine(segmentWord(TUNING), word(TUNING))
        val play = segment(PLAY).bounds()
        val tuning = segment(TUNING).bounds()
        val gear = gear().bounds()
        assertEquals("$what: the gear is pressed over 48", 48f, gear.width.value, 0.5f)
        assertTrue("$what: «${word(TUNING)}» ends at ${tuning.right}, the gear's touch begins at ${gear.left}", tuning.right <= gear.left + 0.5.dp)
        assertTrue("$what: the switcher begins at ${play.left}, inside its row from $rowStart", play.left >= rowStart - 0.5.dp)
        listOf(play, tuning).forEach { assertEquals("$what: the whole row answers a touch", 48f, it.height.value, 0.5f) }
    }

    /**
     * Lying down the switcher stands on the axis of its column — the middle of the record key under it (`live.html`, 5) — or, where
     * at its size it does not fit there clear of the gear's touch, as near to it as that touch lets it: its end at the start of the
     * touch, the whole of it on the side of the axis away from the gear.
     */
    private fun assertTheSwitcherStandsOnTheAxisOfItsColumn(what: String) {
        val play = segment(PLAY).bounds()
        val tuning = segment(TUNING).bounds()
        val start = play.left - LiveDimens.SwitcherInset
        val end = tuning.right + LiveDimens.SwitcherInset
        val key = recordKey().bounds()
        val axis = (key.left + key.right) / 2
        val middle = (start + end) / 2
        if (abs((middle - axis).value) > 1f) {
            val gear = gear().bounds()
            assertEquals("$what: off the axis ($middle against $axis) only up to the gear's touch", gear.left.value, end.value, 0.5f)
            assertTrue("$what: off the axis toward the start, not past it", middle < axis)
        }
    }

    @Test
    fun onAPhoneOf320AtTheFontOneAndAHalfTheLongestWordsOfTheSwitcherStandWholeAndClearOfTheGear() {
        // «Настройка» is the widest of the ten languages: at 12 sp and 1.5 it does not fit the middle of the row, and moves
        speaking("ru")
        show(width = 320.dp, height = 640.dp, fontScale = 1.5f)
        assertTheSwitcherStandsWholeClearOfTheGear("ru, 320, 1.5", uprightRowStart())
        val size = segmentWord(TUNING).textLayout().layoutInput.style.fontSize
        assertTrue("the word steps down, to no less than 12 sp: $size", size.value in 12f..14.5f)
        state = stateOf(LiveMode.TUNING)
        compose.waitForIdle()
        assertTheSwitcherStandsWholeClearOfTheGear("ru, 320, 1.5, «Настройка»", uprightRowStart())
    }

    @Test
    fun onAPhoneOf320AtTheFontOneAndAHalfTheSwitcherInTheLanguageOfTheDeviceStandsWholeAndClearOfTheGear() {
        show(width = 320.dp, height = 640.dp, fontScale = 1.5f)
        assertTheSwitcherStandsWholeClearOfTheGear("the device's language, 320, 1.5", uprightRowStart())
    }

    @Test
    fun onTheBaseScreenTheSwitcherStandsInTheMiddleAtItsFullSizeAndTheWholeRowAnswers() {
        show(width = 412.dp, height = 788.dp)
        assertTheSwitcherStandsWholeClearOfTheGear("412", uprightRowStart())
        val play = segment(PLAY).bounds()
        val tuning = segment(TUNING).bounds()
        // the two segments stand in the capsule with the same inset on either side
        val middle = (play.left + tuning.right) / 2 - window().left
        assertEquals("the switcher stands in the middle of 412", 206f, middle.value, 1f)
        assertTrue("a segment is not narrower than 106: ${play.width}", play.width >= 105.5.dp && tuning.width >= 105.5.dp)
        assertEquals(15f, segmentWord(TUNING).textLayout().layoutInput.style.fontSize.value, 0.01f)
        // a touch 1 dp under the top of the row: over the capsule of glass, and still the segment's
        segment(TUNING).performTouchInput { click(Offset(width / 2f, 1.dp.toPx())) }
        compose.runOnIdle { assertEquals(listOf(LiveIntent.SelectMode(LiveMode.TUNING)), intents) }
    }

    @Test
    fun atTheFontOneAndAHalfTheLetterAndTheHertzOfEveryStringStandWholeInTheirButton() {
        speaking("ru")
        state = stateOf(LiveMode.TUNING, locked = ViolinString.D4)
        show(width = 320.dp, height = 640.dp, fontScale = 1.5f)
        ViolinString.entries.forEach { string ->
            val letter = string.note.letter.toString()
            val button = compose.onNodeWithText(letter).bounds()
            assertEquals("$letter: the button is 58 high", 58f, button.height.value, 0.5f)
            listOf(compose.onNodeWithText(letter, useUnmergedTree = true), compose.onNodeWithText(word(string.name), useUnmergedTree = true)).forEach { text ->
                val bounds = text.bounds()
                assertTrue("$letter: $bounds stands inside its button $button", bounds.top >= button.top - 0.5.dp && bounds.bottom <= button.bottom + 0.5.dp)
                assertTrue("$letter: $bounds stands inside its button $button", bounds.left >= button.left - 0.5.dp && bounds.right <= button.right + 0.5.dp)
            }
            assertWholeOnOneLine(compose.onNodeWithText(word(string.name), useUnmergedTree = true), word(string.name))
        }
    }

    @Test
    fun lyingDownTheSwitcherStandsOnTheAxisOfItsColumnOverTheKey() {
        // 892 × 412: a column of 460 — every language stands in its middle at 15 sp, in «Игра» and in «Настройка» alike
        show(width = 892.dp, height = 412.dp)
        assertTheSwitcherStandsWholeClearOfTheGear("the device's language, 892 × 412", lyingRowStart())
        val key = recordKey().bounds()
        val middle = (segment(PLAY).bounds().left + segment(TUNING).bounds().right) / 2
        assertEquals("the switcher over the key", ((key.left + key.right) / 2).value, middle.value, 1f)
        state = stateOf(LiveMode.TUNING)
        compose.waitForIdle()
        assertTheSwitcherStandsOnTheAxisOfItsColumn("the device's language, 892 × 412, «Настройка»")
    }

    @Test
    fun inALowLandscapeTheSwitcherDoesNotJumpWithTheModeAndTheWordStandsWholeInTuning() {
        // 640 × 360 on a phone with a side cutout: a window of about 603 × 336
        speaking("ru")
        show(width = 603.dp, height = 336.dp)
        val inPlay = segment(PLAY).bounds()
        assertEquals("the column is 8 in from the top of a low window", 8f, (inPlay.top - window().top).value, 0.5f)
        // «Настройка» keeps its 15 sp and does not fit the middle of a column of 300: it stands off it, up to the gear's touch
        assertTheSwitcherStandsOnTheAxisOfItsColumn("ru, 603 × 336")
        state = stateOf(LiveMode.TUNING)
        compose.waitForIdle()
        val inTuning = segment(PLAY).bounds()
        assertEquals("the switcher stays where it stood", inPlay.top.value, inTuning.top.value, 0.5f)
        assertEquals("the switcher stays where it stood", inPlay.left.value, inTuning.left.value, 0.5f)
        // the word and the cents take the room left to them (spec 5.29 R6): ≈ 50 in exact dp — the full size where the pixels of the
        // screen leave that, the compact one on 2.625 px a dp (48.4 in whole pixels) — never smaller, and whole
        val word = compose.onNodeWithText(word(IN_TUNE), useUnmergedTree = true)
        val size = word.textLayout().layoutInput.style.fontSize
        assertTrue("the word of the status in «Настройка» at $size, not below compact", size.value >= StatusFit.COMPACT.wordSp)
        assertWholeOnOneLine(word, word(IN_TUNE))
        // and the keys stand at the bottom of the column, 8 over the window's edge (the key of 76, whose soft shadow takes no room)
        val key = recordKey().bounds()
        assertTrue("the record key ends at ${key.bottom - window().top}", abs((key.bottom - window().top).value - (336f - 8f)) <= 0.5f)
        assertTheSwitcherStandsWholeClearOfTheGear("ru, 603 × 336, «Настройка»", lyingRowStart())
    }

    private companion object {
        const val WINDOW = "window"
        const val PLAY = "play"
        const val TUNING = "tuning"
        const val SETTINGS = "settings"
        const val RECORD = "record"
        const val IN_TUNE = "inTune"
    }
}
