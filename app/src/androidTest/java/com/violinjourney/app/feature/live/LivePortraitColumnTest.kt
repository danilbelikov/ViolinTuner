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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.LiveLayoutMath.PortraitRows
import com.violinjourney.app.feature.live.components.LiveDimens
import com.violinjourney.app.feature.live.components.LiveRecordKey
import com.violinjourney.app.feature.live.components.RecordingStrip
import com.violinjourney.app.feature.live.components.SettingsGear
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mode_play
import com.violinjourney.app.shared.resources.mode_tuning
import com.violinjourney.app.shared.resources.record_start
import com.violinjourney.app.shared.resources.record_stop
import com.violinjourney.app.shared.resources.recording_elapsed_description
import com.violinjourney.app.shared.resources.status_flat
import com.violinjourney.app.shared.resources.tuning_hint_auto
import com.violinjourney.app.shared.resources.tuning_hint_locked
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
 * The column of portrait Live of R6 (spec 3.36.6, 5.29 R6), measured on the screen itself, on a phone of 360 × 640 (Live 506 over
 * the tabs), where the ring is bound by the height: its rows stand where the model of the ring puts them ([PortraitRows]) and the
 * ring is the model's — 230 in «Игра», 116 in «Настройка»; switching the mode, the ring shrinks and grows back frame by frame as the
 * strings and the scale under the word unfold, never by a jump; the phrases of the status line stand in the middle while they
 * cross-fade; the slider at an end of the scale stands whole.
 *
 * Live is laid out in a window of its own size, whatever the device's ([WINDOW], and a fake [LocalWindowInfo] too), on its plain
 * field (no picture), and everything is measured from the window. The widening of a touch target under 48 is switched off. The ring
 * is measured by its note: NoteLabel draws the note of the handoff for the ring of 300 and scales it with the ring
 * ([LiveLayoutMath.noteScale]), so the size of the octave says the diameter. The words are read in the composition.
 */
@RunWith(AndroidJUnit4::class)
class LivePortraitColumnTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = mutableMapOf<String, String>()
    private var state by mutableStateOf(stateOf(LiveMode.PLAY))
    private var gauge by mutableStateOf(LiveGauge())

    /** The octave of the note at the ring of the handoff, in sp; and the bone of the slider — both read in the composition. */
    private var octave = 0f
    private var bone = Color.Unspecified

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    /** The words of the composition in [tag], whatever the device speaks: Compose reads the language of the process when it composes. */
    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun stateOf(mode: LiveMode, signal: LiveSignal = LiveSignal.Silence, locked: ViolinString? = null) =
        LiveReducer.stateOf(LiveTarget(mode, locked), IntonationConfig(), signal)

    /** A4 sounding [cents] off: in tune within the tolerance, else flat or sharp and off by more than 20. */
    private fun sounding(cents: Int = 3) = LiveSignal.Sounding(
        note = Note(A4),
        zone = if (abs(cents) <= IN_TUNE_CENTS) Zone.IN_TUNE else Zone.OFF,
        direction = when {
            abs(cents) <= IN_TUNE_CENTS -> null
            cents < 0 -> Direction.FLAT
            else -> Direction.SHARP
        },
        displayCents = cents,
    )

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val base = LocalViewConfiguration.current
        val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
        val window = with(density) { Window(IntSize(WIDTH.roundToPx(), HEIGHT.roundToPx())) }
        CompositionLocalProvider(
            LocalWindowInfo provides window,
            LocalViewConfiguration provides noWidening,
            LocalDensity provides Density(density.density, 1f),
        ) {
            Box(Modifier.requiredSize(WIDTH, HEIGHT).testTag(WINDOW)) { content() }
        }
    }

    private fun show() {
        compose.setContent {
            words[PLAY] = stringResource(Res.string.mode_play)
            words[TUNING] = stringResource(Res.string.mode_tuning)
            words[RECORD] = stringResource(Res.string.record_start)
            words[STOP] = stringResource(Res.string.record_stop)
            words[TAKE] = stringResource(Res.string.recording_elapsed_description, Formats.duration(TAKE_MS))
            words[FLAT] = stringResource(Res.string.status_flat)
            words[TUNE_AUTO] = stringResource(Res.string.tuning_hint_auto)
            val d = ViolinString.D4
            words[TUNE_LOCKED] = stringResource(
                Res.string.tuning_hint_locked,
                d.note.letter.toString(),
                LiveReducer.stringHzOf(IntonationConfig()).getValue(d),
            )
            ViolinTheme {
                octave = LiveTheme.liveTypography.octave.fontSize.value
                bone = LiveTheme.venueColors.bone
                InWindow {
                    LiveScreenLayout(
                        state = state,
                        onIntent = {},
                        slots = LiveSlots(
                            recordKey = { recording, enabled, alpha -> LiveRecordKey(recording, enabled, onClick = {}, alpha = alpha) },
                            gear = { enabled, modifier -> SettingsGear(onClick = {}, modifier = modifier, enabled = enabled) },
                            recordingStrip = { recording, ribbon, modifier -> RecordingStrip(recording, ribbon, modifier) },
                        ),
                        gauge = { gauge },
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

    /** The record key — the button TalkBack names «Начать запись». */
    private fun recordKey() = compose.onNodeWithContentDescription(word(RECORD))

    /** The diameter of the ring in dp, by the size of the octave of its note: NoteLabel scales the note of the ring of 300 with it. */
    private fun ring(): Float {
        val size = compose.onNodeWithText(OCTAVE, useUnmergedTree = true).textLayout().layoutInput.style.fontSize.value
        return size / octave / LiveLayoutMath.noteScale(1f)
    }

    /** The ring the model gives Live of [WIDTH] × [HEIGHT] in the mode, the scale under the word shown in «Настройка». */
    private fun modelRing(tuning: Boolean, recording: Boolean = false): Float = LiveLayoutMath.ringDiameter(
        designDp = LiveLayoutMath.designRing(landscape = false, tuning = tuning),
        availableWidthDp = (WIDTH - LiveDimens.RingMargin * 2).value,
        availableHeightDp = LiveLayoutMath.ringBlockHeight(HEIGHT.value, tuning, recording),
        reservedHeightDp = LiveLayoutMath.reservedUnderRing(if (tuning) 1f else 0f),
    )

    /** The rows over and under the place of the ring where the model puts them: the top row, the strings of «Настройка», the keys. */
    private fun assertTheRowsOfTheModel(what: String, tuning: Boolean) {
        val window = window()
        val segment = compose.onNodeWithText(word(PLAY)).bounds()
        assertEquals("$what: the top row under its air", PortraitRows.AIR, (segment.top - window.top).value, 0.5f)
        assertEquals("$what: the top row", PortraitRows.TOP_ROW, segment.height.value, 0.5f)
        if (tuning) {
            val string = compose.onNodeWithText(ViolinString.G3.note.letter.toString()).bounds()
            assertEquals("$what: the strings under their air", PortraitRows.TOP + PortraitRows.AIR, (string.top - window.top).value, 0.5f)
            assertEquals("$what: a string", PortraitRows.STRING_BUTTON, string.height.value, 0.5f)
        }
        // the key of 76, whose soft shadow takes no room, the air of the row under it
        val key = recordKey().bounds()
        assertEquals("$what: the key", PortraitRows.KEY_ROW, key.height.value, 0.5f)
        assertEquals("$what: the keys over their air", HEIGHT.value - PortraitRows.AIR, (key.bottom - window.top).value, 0.5f)
    }

    /** Spec 5.29 R6: the model of the ring is the screen — each row where it says, and the ring it gives: 230 and 116 here. */
    @Test
    fun onAPhoneOf360x640TheRowsAndTheRingAreThoseOfTheModel() {
        state = stateOf(LiveMode.PLAY, sounding())
        show()
        assertEquals("«Игра»: the ring of the model", modelRing(tuning = false), ring(), RING_PIXELS)
        assertEquals("«Игра»: 230, the plate of 36 took 8 from the 238 of before R6", 230f, ring(), RING_PIXELS)
        assertTheRowsOfTheModel("«Игра»", tuning = false)
        state = stateOf(LiveMode.TUNING, sounding())
        compose.waitForIdle()
        assertEquals("«Настройка»: the ring of the model", modelRing(tuning = true), ring(), RING_PIXELS)
        assertEquals("«Настройка»: 116, 2 less than the 118 of before R6", 116f, ring(), RING_PIXELS)
        assertTheRowsOfTheModel("«Настройка»", tuning = true)
    }

    /**
     * The ring after [change], frame by frame for longer than the strings and the scale take to unfold; then the rest of the way at
     * once.
     */
    private fun ringsAfter(change: () -> Unit): List<Float> {
        compose.mainClock.autoAdvance = false
        change()
        val rings = List(TRANSITION_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            ring()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return rings
    }

    private fun assertItComesWithoutAJump(what: String, from: Float, rings: List<Float>, to: Float) {
        var last = from
        rings.forEachIndexed { frame, ring ->
            assertTrue("$what: in frame $frame the ring went from $last to $ring — $rings", abs(ring - last) <= MOST_A_FRAME)
            last = ring
        }
        assertEquals("$what: it comes to the ring of the model — $rings", to, ring(), RING_PIXELS)
        val between = rings.count { it > minOf(from, to) + 1f && it < maxOf(from, to) - 1f }
        assertTrue("$what: it gets there on the way, not at once — $rings", between >= 3)
    }

    /**
     * Spec 3.36.6: the scale moved under the word takes its room inside the place of the ring as it unfolds (5.29 R6: the room under
     * the ring grows by its 44 in the time of the string row) — on a small phone the ring shrinks and grows back as smoothly as it did
     * when the scale unfolded under the place. A scale that took its room at once would take 44 from the ring in one frame.
     */
    @Test
    fun switchingTheModeTheRingShrinksAndGrowsBackWithoutAJump() {
        state = stateOf(LiveMode.PLAY, sounding())
        show()
        val play = ring()
        val intoTuning = ringsAfter { state = stateOf(LiveMode.TUNING, sounding()) }
        assertItComesWithoutAJump("into «Настройка»", play, intoTuning, modelRing(tuning = true))
        val backToPlay = ringsAfter { state = stateOf(LiveMode.PLAY, sounding()) }
        assertItComesWithoutAJump("back to «Игра»", intoTuning.last(), backToPlay, modelRing(tuning = false))
    }

    /**
     * Spec 3.36.6: the strip of a take stands on its own glass of 50, 22 from the sides of the screen, 12 over the keys; it takes its
     * height from the ring — on a phone of 360 × 640 the ring loses 58 (230 → 172), smoothly, in the 200 ms of the strip, and gets it
     * back as the take stops.
     */
    @Test
    fun startingATakeTheRingGivesTheStripItsHeightWithoutAJump() {
        state = stateOf(LiveMode.PLAY, sounding())
        show()
        val quiet = ring()
        val recording = ringsAfter { state = stateOf(LiveMode.PLAY, sounding()).copy(recording = RecordingState(elapsedMs = TAKE_MS)) }
        assertItComesWithoutAJump("the take starts", quiet, recording, modelRing(tuning = false, recording = true))
        assertEquals("the ring gives the strip 58", 172f, ring(), RING_PIXELS)
        val window = window()
        val strip = compose.onNodeWithContentDescription(word(TAKE)).bounds()
        val key = compose.onNodeWithContentDescription(word(STOP)).bounds()
        assertEquals("the strip is 50 high", 50f, strip.height.value, 0.5f)
        assertEquals("22 from the left of the screen", 22f, (strip.left - window.left).value, 0.5f)
        assertEquals("22 from the right of the screen", 22f, (window.right - strip.right).value, 0.5f)
        assertEquals("12 over the key", 12f, (key.top - strip.bottom).value, 0.5f)
        val stopped = ringsAfter { state = stateOf(LiveMode.PLAY, sounding()) }
        assertItComesWithoutAJump("the take stops", recording.last(), stopped, modelRing(tuning = false))
    }

    /**
     * Spec 5.29 R6: the plate of the status line changes its phrase in 200 ms, in place. The two phrases of a lock and of auto differ
     * by about 80 dp in Russian: stacked from the start of the cross-fade, the shorter would stand 40 off the middle and jump there.
     */
    @Test
    fun whileThePhrasesOfTheStatusLineCrossFadeEachStandsWhereItStandsAlone() {
        speaking("ru")
        state = stateOf(LiveMode.TUNING, locked = ViolinString.D4)
        show()
        val locked = compose.onNodeWithText(word(TUNE_LOCKED), useUnmergedTree = true).bounds()
        state = stateOf(LiveMode.TUNING)
        compose.waitForIdle()
        val auto = compose.onNodeWithText(word(TUNE_AUTO), useUnmergedTree = true).bounds()
        assertTrue("the two phrases differ in width: ${auto.width} and ${locked.width}", abs((auto.width - locked.width).value) > PHRASES_DIFFER)

        compose.mainClock.autoAdvance = false
        state = stateOf(LiveMode.TUNING, locked = ViolinString.D4)
        var together = 0
        repeat(SWAP_FRAMES) { frame ->
            compose.mainClock.advanceTimeByFrame()
            val shown = listOf(TUNE_AUTO to auto, TUNE_LOCKED to locked).filter { (key, _) ->
                compose.onAllNodesWithText(word(key), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            if (shown.size == 2) together++
            shown.forEach { (key, alone) ->
                val now = compose.onNodeWithText(word(key), useUnmergedTree = true).bounds()
                assertEquals("frame $frame: «${word(key)}» stands where it stands alone", alone.left.value, now.left.value, 0.5f)
            }
        }
        compose.mainClock.autoAdvance = true
        assertTrue("the two phrases were seen crossing", together > 0)
    }

    /** Whether some pixel of the column [x] from [from] down to [to] of the window is the bone of the slider. */
    private fun boneIn(x: Dp, from: Dp, to: Dp): Boolean {
        val window = window()
        val pixels = compose.onNodeWithTag(WINDOW).captureToImage().toPixelMap()
        return with(compose.density) {
            val column = (x - window.left).roundToPx()
            ((from - window.top).roundToPx()..(to - window.top).roundToPx()).any { y ->
                val pixel = pixels[column, y]
                abs(pixel.red - bone.red) <= CHANNEL && abs(pixel.green - bone.green) <= CHANNEL && abs(pixel.blue - bone.blue) <= CHANNEL
            }
        }
    }

    /**
     * Spec 5.29 R6: the slider and its halo as they were — whole at an end of the line too, where the cents of «Настройка» put it
     * (they go far past its ±50). The line is 24 in from the sides; the outer half of the slider of 8 stands in that room.
     */
    @Test
    fun theSliderAtAnEndOfTheScaleStandsWhole() {
        state = stateOf(LiveMode.TUNING, sounding(cents = -FAR))
        gauge = LiveGauge(cents = -FAR.toDouble())
        show()
        val word = compose.onNodeWithText(word(FLAT), useUnmergedTree = true).bounds()
        val key = recordKey().bounds()
        val outerHalf = LiveDimens.MarkerWidth / 4
        val window = window()
        assertTrue("the slider at the start of the line is whole", boneIn(window.left + LiveDimens.ScreenPadding - outerHalf, word.bottom, key.top))
        gauge = LiveGauge(cents = FAR.toDouble())
        compose.waitForIdle()
        assertTrue("the slider at the end of the line is whole", boneIn(window.right - LiveDimens.ScreenPadding + outerHalf, word.bottom, key.top))
    }

    private companion object {
        const val WINDOW = "window"
        const val PLAY = "play"
        const val TUNING = "tuning"
        const val RECORD = "record"
        const val STOP = "stop"
        const val TAKE = "take"

        /** A take of 1:24 under way. */
        const val TAKE_MS = 84_000L
        const val FLAT = "flat"
        const val TUNE_AUTO = "tuneAuto"
        const val TUNE_LOCKED = "tuneLocked"

        /** A phone of 360 × 640: Live over the tabs. */
        val WIDTH = 360.dp
        val HEIGHT = 506.dp

        /**
         * How far the ring on the screen may stand from the ring of the model, in dp. The model counts its rows in dp; the screen lays
         * each of them out in whole pixels, and at 2.625 px a dp every 12 (31.5 px), the plate of 36 (94.5) and the window itself
         * (506 → 1328 px) round by up to half a pixel, all to the ring's cost: on the Pixel 7 the ring comes out 1.2–1.3 dp under
         * the model (228.8 for 230, 114.7 for 116). A row off by what matters — the 12 of the review — moves it by 12.
         */
        const val RING_PIXELS = 1.5f

        const val A4 = 69
        const val OCTAVE = "4"
        const val IN_TUNE_CENTS = 8

        /** Cents far past the ±50 of the scale: the slider stands on an end of its line. */
        const val FAR = 80

        /**
         * The most the ring may change in a frame of 16 ms: it shrinks by 114 (the strings of 70 and the scale of 44) in the 200 ms
         * of both, eased — at the steepest 2.7 times the mean, about 24 in a frame, whether the two start in the same frame or one
         * apart. The scale taking its 44 at once jumps by 44.
         */
        const val MOST_A_FRAME = 32f

        /** Frames of 16 ms that outlast the 200 ms of the strings and the scale. */
        const val TRANSITION_FRAMES = 25

        /** Frames of 16 ms that outlast the 200 ms of the swap of a phrase. */
        const val SWAP_FRAMES = 20

        /** The two phrases must differ this much in width (dp), or the test proves nothing. */
        const val PHRASES_DIFFER = 20f

        /** How far a channel of a captured pixel may be from the bone, of 1. */
        const val CHANNEL = 3f / 255f
    }
}
