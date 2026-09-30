package com.violinjourney.app.feature.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.ui.format.CentsFormat
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.core.ui.theme.LiveTypography
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.components.LiveDimens
import com.violinjourney.app.feature.live.components.LiveRecordKey
import com.violinjourney.app.feature.live.components.SettingsGear
import com.violinjourney.app.feature.live.components.StatusFit
import com.violinjourney.app.feature.live.components.StatusStep
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mode_play
import com.violinjourney.app.shared.resources.record_start
import com.violinjourney.app.shared.resources.status_flat
import com.violinjourney.app.shared.resources.status_in_tune
import com.violinjourney.app.shared.resources.status_sharp
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The word and the cents in the right column of a low landscape (spec 5.29 R6, «Шкала»): they take the room the column leaves them —
 * full, compact, then smaller down to the letters of the strings — and where not even that stands they give way whole, their place
 * kept; a glyph is never clipped. The emulator's 640 × 360 lying down leaves Live 308 high, not the 336 of a phone with a thin status
 * bar: «в строе +2» was cut in half under the scale there (stages 114–115). The window of 603 × 336 alone missed it.
 *
 * The step shown is the largest whose row the room holds, as the spec counts it: the tallest whole line of the word and the cents, or
 * the sign; across — the sign, two gaps, the widest of the three words and the widest cents. The room is measured in the whole pixels
 * the column lays its rows out in (the review of stage 116: on 2.625 px a dp the rows of 603 × 336 leave the word 48.4, not the 50 of
 * exact dp — the compact size in «Настройка»), and the lines by the test's own measurer; a pixel or two either way is let pass.
 *
 * Live is laid out in a window of its own size ([WINDOW], a fake [LocalWindowInfo] too), on its plain field; everything is measured
 * from the window. The words are read in the composition, in Russian ([speaking]). The font is scaled as Compose scales it — on every
 * version of Android along the curve of Android 14, where a large size grows little (36 sp is 36 dp at 1.3 and at 1.5) — and, [linear],
 * as Android scales it before 14 (minSdk 26): the harder case, the cents of 36 sp at 1.5 are 54 dp there.
 */
@RunWith(AndroidJUnit4::class)
class LiveLandscapeWordTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = mutableMapOf<String, String>()
    private var state by mutableStateOf(stateOf(LiveMode.PLAY, inTune))
    private var windowSize by mutableStateOf(DpSize(603.dp, 308.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private var linear by mutableStateOf(false)

    /** What the test measures the lines with: the measurer, the styles and the density of the window, and the three words of the zones. */
    private class Tools(val measurer: TextMeasurer, val typography: LiveTypography, val density: Density, val zones: List<String>)

    private var tools: Tools? = null

    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun stateOf(mode: LiveMode, signal: LiveSignal) = LiveReducer.stateOf(LiveTarget(mode, null), IntonationConfig(), signal)

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    /** The font scaled in a straight line, as Android scales it before 14: Compose's own [Density] follows the curve of Android 14. */
    private class LinearDensity(override val density: Float, override val fontScale: Float) : Density {
        override fun TextUnit.toDp(): Dp = Dp(value * fontScale)

        override fun Dp.toSp(): TextUnit = (value / fontScale).sp
    }

    @Composable
    private fun InWindow(content: @Composable () -> Unit) {
        val device = LocalDensity.current
        val base = LocalViewConfiguration.current
        val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
        val size = windowSize
        val info = with(device) { Window(IntSize(size.width.roundToPx(), size.height.roundToPx())) }
        val density = if (linear) LinearDensity(device.density, fontScale) else Density(device.density, fontScale)
        CompositionLocalProvider(
            LocalWindowInfo provides info,
            LocalViewConfiguration provides noWidening,
            LocalDensity provides density,
        ) {
            tools = Tools(
                measurer = rememberTextMeasurer(),
                typography = LiveTheme.liveTypography,
                density = LocalDensity.current,
                zones = listOf(stringResource(Res.string.status_in_tune), stringResource(Res.string.status_sharp), stringResource(Res.string.status_flat)),
            )
            Box(Modifier.requiredSize(size.width, size.height).testTag(WINDOW)) { content() }
        }
    }

    private fun show() {
        compose.setContent {
            words[IN_TUNE] = stringResource(Res.string.status_in_tune)
            words[SHARP] = stringResource(Res.string.status_sharp)
            words[PLAY] = stringResource(Res.string.mode_play)
            words[RECORD] = stringResource(Res.string.record_start)
            ViolinTheme {
                InWindow {
                    LiveScreenLayout(
                        state = state,
                        onIntent = {},
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

    private fun window(): DpRect = compose.onNodeWithTag(WINDOW).bounds()

    private fun exists(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The pixel [dp] of the root falls on. */
    private fun Density.px(dp: Dp): Int = dp.toPx().roundToInt()

    /**
     * The room of the word and the cents in the right column, in whole pixels, as the column lays its rows out: from what stands round
     * it — the plate of the status line a gap under the top row, or under the strings of «Настройка», and a gap under it; over the
     * scale of «Настройка» and the keys, a gap from each — and across, the column's inside: the window less the panel of the ring and
     * the column's own sides. Each row and gap is its own whole number of pixels ([Density.roundToPx]), as `spacedBy`, `height` and
     * `padding` make them.
     */
    private fun roomPx(tuning: Boolean): Room = with(compose.density) {
        val column = LiveLayoutMath.landscapeColumn(windowSize.height.value)
        val gap = column.gap.dp.roundToPx()
        val above = px(if (tuning) compose.onNodeWithText("G").bounds().bottom else compose.onNodeWithText(word(PLAY)).bounds().bottom)
        val key = px(compose.onNodeWithContentDescription(word(RECORD)).bounds().top)
        val top = above + gap + LiveLayoutMath.PortraitRows.STATUS_PLATE.dp.roundToPx() + gap
        val bottom = key - LiveLayoutMath.LANDSCAPE_KEYS_AIR.dp.roundToPx() - gap - if (tuning) column.scale.dp.roundToPx() + gap else 0
        val window = windowSize.width.roundToPx()
        val across = window - (window * LiveDimens.LANDSCAPE_RING_PANEL_FRACTION).roundToInt() -
            LiveDimens.LandscapePaddingStart.roundToPx() - LiveDimens.LandscapePaddingEnd.roundToPx()
        Room(top, bottom, across)
    }

    private data class Room(val top: Int, val bottom: Int, val across: Int) {
        val height: Int get() = bottom - top
    }

    /** The row of [step] as the spec counts it (5.29 R6), in pixels of the window: its height and its width. */
    private fun rowOf(step: StatusStep): Pair<Float, Float> {
        val tools = checkNotNull(tools)
        return with(tools.density) {
            val word = tools.typography.status.copy(fontSize = step.wordSp.sp)
            val cents = tools.typography.cents.copy(fontSize = step.centsSp.sp)
            val lines = tools.zones.map { tools.measurer.measure(it, word, maxLines = 1, softWrap = false).size }
            val centsLine = tools.measurer.measure(WIDEST_CENTS, cents, maxLines = 1, softWrap = false).size
            val sign = maxOf(step.arrowDp, step.dotDp).dp.toPx()
            val height = maxOf(lines.maxOf { it.height }.toFloat(), centsLine.height.toFloat(), sign)
            height to sign + step.gapDp.dp.toPx() * 2 + lines.maxOf { it.width } + centsLine.width
        }
    }

    /** The largest step whose row stands in [height] × [width] pixels; null — none does. */
    private fun largestIn(height: Float, width: Float): StatusStep? =
        StatusFit.steps.firstOrNull { step -> rowOf(step).let { (h, w) -> h <= height && w <= width } }

    /**
     * The word and its cents stand whole, each on one line, not cut above or below, inside their room — or neither is there; and the
     * size they stand at is the largest the room holds: not smaller than the one that stands with a pixel or two to spare, not larger
     * than the one that stands with them given; absent only where not even the least of them stands with that to spare.
     */
    private fun assertTheLargestWholeOrAbsent(what: String, word: String, cents: String, tuning: Boolean) {
        val shown = exists(word)
        assertEquals("$what: the word and the cents go together", shown, exists(cents))
        val room = roomPx(tuning)
        val surely = largestIn(room.height - SLACK_PX, room.across - SLACK_PX)
        val atMost = largestIn(room.height + SLACK_PX, room.across + SLACK_PX)
        val steps = StatusFit.steps
        if (!shown) {
            assertTrue("$what: the word gave way where ${surely?.wordSp} sp stands in ${room.height} × ${room.across} px", surely == null)
            return
        }
        assertTrue("$what: the word is shown where not even the least step stands in ${room.height} × ${room.across} px", atMost != null)
        val size = compose.onAllNodesWithText(word, useUnmergedTree = true).onFirst().textLayout().layoutInput.style.fontSize.value
        val at = steps.indexOfFirst { abs(it.wordSp - size) < 0.01f }
        assertTrue("$what: $size sp is one of the steps", at >= 0)
        assertTrue("$what: $size sp, not larger than ${atMost?.wordSp} sp in ${room.height} × ${room.across} px", at >= steps.indexOf(atMost))
        if (surely != null) {
            assertTrue("$what: $size sp, not smaller than ${surely.wordSp} sp in ${room.height} × ${room.across} px", at <= steps.indexOf(surely))
        }
        val (top, bottom) = with(compose.density) { room.top.toDp() to room.bottom.toDp() }
        listOf(word, cents).forEach { text ->
            val node = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
            assertWholeOnOneLine(node, "$what: $text")
            val bounds = node.bounds()
            assertTrue("$what: «$text» at ${bounds.top}…${bounds.bottom} in its room $top…$bottom", bounds.top >= top - 0.5.dp && bounds.bottom <= bottom + 0.5.dp)
            assertTrue("$what: «$text» inside the window", bounds.right <= window().right + 0.5.dp)
        }
    }

    @Test
    fun inALowLandscapeTheWordAndTheCentsTakeTheLargestSizeTheirRoomHoldsOrGiveWayWhole() {
        speaking("ru")
        show()
        for (scaled in listOf(false, true)) {
            for (size in listOf(DpSize(603.dp, 308.dp), DpSize(603.dp, 336.dp), DpSize(640.dp, 336.dp))) {
                for (font in listOf(1f, 1.3f, 1.5f)) {
                    for (mode in LiveMode.entries) {
                        for ((signal, key) in listOf(inTune to IN_TUNE, sharp to SHARP)) {
                            linear = scaled
                            windowSize = size
                            fontScale = font
                            state = stateOf(mode, signal)
                            compose.waitForIdle()
                            val what = "${size.width} × ${size.height}, $font${if (scaled) " linear" else ""}, $mode, $key"
                            assertTheLargestWholeOrAbsent(what, word(key), CentsFormat.signed(signal.displayCents.toDouble()), tuning = mode == LiveMode.TUNING)
                        }
                    }
                }
            }
        }
    }

    /**
     * 640 × 360 behind a side cutout (603 × 336): in «Игра» the word keeps its full 28 sp; in «Настройка» the column leaves it about 50
     * in exact dp — the full size where the pixels of the screen leave that (2 or 3 px a dp), the compact 24 sp on 2.625 px a dp, where
     * the rows round up to 48.4 — never less than compact, never cut.
     */
    @Test
    fun at640x360BehindACutoutTheWordIsFullInPlayAndNotBelowCompactInTuning() {
        speaking("ru")
        windowSize = DpSize(603.dp, 336.dp)
        show()
        for (mode in LiveMode.entries) {
            state = stateOf(mode, inTune)
            compose.waitForIdle()
            val node = compose.onNodeWithText(word(IN_TUNE), useUnmergedTree = true)
            val size = node.textLayout().layoutInput.style.fontSize
            if (mode == LiveMode.PLAY) {
                assertEquals("«Игра»: the word keeps its 28 sp", 28.sp, size)
            } else {
                assertTrue("«Настройка»: the word at $size, not below compact", size.value >= StatusFit.COMPACT.wordSp)
            }
            assertWholeOnOneLine(node, word(IN_TUNE))
        }
    }

    /**
     * The steps between the compact size and the least one are there to be used: where the room of «Настройка» holds a smaller step at a
     * large font, the word stands at it and does not give way (the letters of the strings, 20 sp, are the least).
     */
    @Test
    fun atALargeFontTheWordStepsDownRatherThanGiveWay() {
        speaking("ru")
        windowSize = DpSize(603.dp, 336.dp)
        linear = true
        fontScale = 1.3f
        state = stateOf(LiveMode.TUNING, inTune)
        show()
        val nodes = compose.onAllNodesWithText(word(IN_TUNE), useUnmergedTree = true).fetchSemanticsNodes()
        assertFalse("603 × 336 at 1.3, «Настройка»: the word is there", nodes.isEmpty())
        val shown = compose.onNodeWithText(word(IN_TUNE), useUnmergedTree = true).textLayout().layoutInput.style.fontSize.value
        assertTrue("603 × 336 at 1.3, «Настройка»: below compact, not below the least — $shown sp", shown < StatusFit.COMPACT.wordSp && shown >= StatusFit.LEAST_WORD_SP)
        // 603 × 308 in «Игра» at 1.5, linear: the column of 300 is narrower than the full row — a smaller step, not nothing
        windowSize = DpSize(603.dp, 308.dp)
        fontScale = 1.5f
        state = stateOf(LiveMode.PLAY, inTune)
        compose.waitForIdle()
        val play = compose.onNodeWithText(word(IN_TUNE), useUnmergedTree = true).textLayout().layoutInput.style.fontSize.value
        assertTrue("603 × 308 at 1.5, «Игра»: $play sp, smaller than the full size and not below the least", play < StatusFit.FULL.wordSp && play >= StatusFit.LEAST_WORD_SP)
    }

    @Test
    fun whereLiveIs308HighTheWordGivesWayInTuningAndItsPlaceStays() {
        speaking("ru")
        windowSize = DpSize(603.dp, 308.dp)
        state = stateOf(LiveMode.TUNING, inTune)
        show()
        assertTrue("the word gives way in the 22 left to it", !exists(word(IN_TUNE)))
        assertTrue("the cents with it", !exists(CentsFormat.signed(inTune.displayCents.toDouble())))
        // its place kept: the keys stand at the bottom of the column, 8 over the window's edge, as with the word
        val key = compose.onNodeWithContentDescription(word(RECORD)).bounds()
        assertTrue("the record key ends at ${key.bottom - window().top}", abs((key.bottom - window().top).value - (308f - 8f)) <= 0.5f)
        // in «Игра» the same window leaves them 116: the full size
        state = stateOf(LiveMode.PLAY, inTune)
        compose.waitForIdle()
        assertEquals(28.sp, compose.onNodeWithText(word(IN_TUNE), useUnmergedTree = true).textLayout().layoutInput.style.fontSize)
    }

    private companion object {
        const val WINDOW = "window"
        const val IN_TUNE = "inTune"
        const val SHARP = "sharp"
        const val PLAY = "play"
        const val RECORD = "record"
        const val A4 = 69

        /** The widest the cents get, as the row reserves them: a real minus and two tabular digits. */
        const val WIDEST_CENTS = "−00"

        /** What the room measured here may differ from the room the column gives by: a pixel or two of the weights of the row. */
        const val SLACK_PX = 2f

        val inTune = LiveSignal.Sounding(note = Note(A4), zone = Zone.IN_TUNE, direction = null, displayCents = 2)
        val sharp = LiveSignal.Sounding(note = Note(A4), zone = Zone.NEAR, direction = Direction.SHARP, displayCents = 12)
    }
}
