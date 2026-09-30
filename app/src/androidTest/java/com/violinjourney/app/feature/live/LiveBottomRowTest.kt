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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.block.BlockBookmark
import com.violinjourney.app.feature.live.block.Bookmark
import com.violinjourney.app.feature.live.components.LiveDimens
import com.violinjourney.app.feature.live.components.LiveRecordKey
import com.violinjourney.app.feature.live.components.PracticeTag
import com.violinjourney.app.feature.live.components.SettingsGear
import com.violinjourney.app.feature.live.venue.VenueLook
import com.violinjourney.app.navigation.LocalTabBarLight
import com.violinjourney.app.navigation.TabBarLight
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_done
import com.violinjourney.app.shared.resources.block_done_description
import com.violinjourney.app.shared.resources.block_entry
import com.violinjourney.app.shared.resources.block_entry_description
import com.violinjourney.app.shared.resources.block_entry_hint
import com.violinjourney.app.shared.resources.block_left_few
import com.violinjourney.app.shared.resources.block_left_many
import com.violinjourney.app.shared.resources.block_left_one
import com.violinjourney.app.shared.resources.block_running_description
import com.violinjourney.app.shared.resources.practice_chip_label
import com.violinjourney.app.shared.resources.practice_start
import com.violinjourney.app.shared.resources.practice_tag_start
import com.violinjourney.app.shared.resources.practice_timer_description
import com.violinjourney.app.shared.resources.record_start
import com.violinjourney.app.shared.resources.record_stop
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bottom row of Live of R6 (spec 3.36.6, 5.29 R6) by what an eye and a finger meet: «Что играю» and the practice tag are one
 * form as wide as each other — 150, 124, 104, ≈ 94 as the row allows — with the key in the middle and 8 between; their words stand
 * whole at the font 1.3 and 1.5 on phones of 360 and 320 and lying down in 892 × 412 and 640 × 360 behind a side cutout, in Russian,
 * German, French, Portuguese and Spanish — no word broken inside, no number parted from its unit, and only the name of an element ends
 * in «…»; the paper of a block drains into the glass with the line and the «готово» it had; the key tells TalkBack what it does and
 * does not answer where there is nothing to record; the light of Live is lent to the tab bar while it is resumed, whatever the shape
 * of Live.
 *
 * Live is laid out in a window of its own size, whatever the device's ([WINDOW], and a fake [LocalWindowInfo] too), on its plain
 * field (no picture) unless a test needs the light to go out; everything is measured from the window. The widening of a touch target
 * under 48 is switched off. The words are read in the composition, in the language of the process ([speaking]).
 */
@RunWith(AndroidJUnit4::class)
class LiveBottomRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = mutableMapOf<String, String>()
    private var state by mutableStateOf(stateOf(LiveMode.PLAY))
    private var bookmark by mutableStateOf<Bookmark>(Bookmark.Entry)
    private var practiceMs by mutableStateOf<Long?>(null)
    private var windowSize by mutableStateOf(DpSize(360.dp, 506.dp))
    private var fontScale by mutableFloatStateOf(1f)

    /** A picture behind Live, so that its light goes out while a note sounds; and the tab bar Live lends it to. */
    private var picture = false
    private var bar: TabBarLight? = null

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    /** The words of the composition in [tag], whatever the device speaks: Compose reads the language of the process when it composes. */
    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun stateOf(mode: LiveMode, signal: LiveSignal = LiveSignal.Silence, recording: RecordingState? = null) =
        LiveReducer.stateOf(LiveTarget(mode, null), IntonationConfig(), signal, recording = recording)

    /** A4 sounding in tune: the light goes out over a picture. */
    private val sounding = LiveSignal.Sounding(note = Note(A4), zone = Zone.IN_TUNE, direction = null, displayCents = 3)

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val base = LocalViewConfiguration.current
        val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
        val size = windowSize
        val info = with(density) { Window(IntSize(size.width.roundToPx(), size.height.roundToPx())) }
        CompositionLocalProvider(
            LocalWindowInfo provides info,
            LocalViewConfiguration provides noWidening,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            Box(Modifier.requiredSize(size.width, size.height).testTag(WINDOW)) { content() }
        }
    }

    private fun show() {
        compose.setContent {
            words[ENTRY] = stringResource(Res.string.block_entry)
            words[ENTRY_HINT] = stringResource(Res.string.block_entry_hint)
            words[ENTRY_SAID] = stringResource(Res.string.block_entry_description)
            words[START] = stringResource(Res.string.practice_tag_start)
            words[PRACTICE] = stringResource(Res.string.practice_chip_label)
            words[START_SAID] = stringResource(Res.string.practice_start)
            words[RUNNING_SAID] = stringResource(Res.string.practice_timer_description, TIME)
            words[LEFT] = stringResource(Formats.plural(LEFT_MINUTES, Res.string.block_left_one, Res.string.block_left_few, Res.string.block_left_many), LEFT_MINUTES)
            words[DONE] = stringResource(Res.string.block_done)
            words[BLOCK_SAID] = stringResource(Res.string.block_running_description, TITLE, Formats.minutesInWords(LEFT_MINUTES * MS_PER_MINUTE))
            words[RECORD] = stringResource(Res.string.record_start)
            words[STOP] = stringResource(Res.string.record_stop)
            words[DONE_SAID] = stringResource(Res.string.block_done_description, TITLE)
            val lent = bar
            ViolinTheme {
                CompositionLocalProvider(LocalTabBarLight provides (lent ?: LocalTabBarLight.current)) {
                    InWindow {
                        LiveScreenLayout(
                            state = state,
                            onIntent = {},
                            slots = LiveSlots(
                                backdrop = if (picture) NoPicture else null,
                                bookmark = { width, light -> BlockBookmark(bookmark, width, onClick = {}, light = light) },
                                tag = { width, light -> PracticeTag(practiceMs, width, onClick = {}, light = light) },
                                recordKey = { recording, enabled, alpha -> LiveRecordKey(recording, enabled, onClick = {}, alpha = alpha) },
                                gear = { enabled, modifier -> SettingsGear(onClick = {}, modifier = modifier, enabled = enabled) },
                                // as LiveScreen lends it with its switch on
                                light = { chrome -> if (lent != null) LendTabBarLight(light = chrome) },
                            ),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun word(key: String): String = words.getValue(key)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    /** The window Live is laid out in, where the root has it: every position is measured from it. */
    private fun window(): DpRect = compose.onNodeWithTag(WINDOW).bounds()

    /** A card by the one phrase TalkBack hears of it. */
    private fun card(said: String): DpRect = compose.onNodeWithContentDescription(said).bounds()

    private fun key() = compose.onNodeWithContentDescription(word(RECORD))

    private fun Char.breaksALine() = this == ' ' || this == '-'

    /**
     * [text] of a card stands whole in [card]: inside it; no line of it broken inside a word — each ends at a space or a hyphen — nor
     * between a number and its unit («ещё 12» / «мин»); no line dropped and none ending in «…», unless it is the name of an element
     * ([title]); on one line, not cut at its end either.
     */
    private fun assertWhole(what: String, text: String, card: DpRect, title: Boolean = false) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        val bounds = node.bounds()
        assertTrue("$what: «$text» at $bounds stands inside its card $card", bounds.left >= card.left - 0.5.dp && bounds.right <= card.right + 0.5.dp)
        assertTrue("$what: «$text» at $bounds stands inside its card $card", bounds.top >= card.top - 0.5.dp && bounds.bottom <= card.bottom + 0.5.dp)
        val layout = node.textLayout()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("$what: «$text» breaks its line ${line + 1} inside a word, at $end", end > 0 && text[end - 1].breaksALine())
            assertFalse("$what: «$text» parts a number from its unit at the end of its line ${line + 1}", text.substring(0, end).trimEnd().last().isDigit())
        }
        if (title) return
        for (line in 0 until layout.lineCount) assertFalse("$what: «$text» ends its line ${line + 1} in «…»", layout.isLineEllipsized(line))
        assertFalse("$what: «$text» lost a line to maxLines", layout.multiParagraph.didExceedMaxLines)
        if (layout.lineCount == 1) {
            assertTrue(
                "$what: «$text» needs ${layout.multiParagraph.maxIntrinsicWidth} px on its line and has ${layout.size.width}",
                layout.multiParagraph.maxIntrinsicWidth <= layout.size.width,
            )
        }
    }

    /** No practice, no block: «Что играю» / «выбрать» on glass, «Начать» / «занятие» on glass. */
    private fun assertTheIdleCardsStandWhole(what: String) {
        val entry = card(word(ENTRY_SAID))
        assertWhole(what, word(ENTRY), entry)
        assertWhole(what, word(ENTRY_HINT), entry)
        val tag = card(word(START_SAID))
        assertWhole(what, word(START), tag)
        assertWhole(what, word(PRACTICE), tag)
    }

    /** A block and a practice run: the name with «…», «ещё 12 мин» and «готово» ready over it on paper; the time and «занятие». */
    private fun assertTheRunningCardsStandWhole(what: String) {
        val block = card(word(BLOCK_SAID))
        assertWhole(what, TITLE, block, title = true)
        assertWhole(what, word(LEFT), block)
        assertWhole(what, word(DONE), block)
        val tag = card(word(RUNNING_SAID))
        assertWhole(what, TIME, tag)
        assertWhole(what, word(PRACTICE), tag)
    }

    private fun wordsWholeEverywhere(language: String) {
        speaking(language)
        show()
        for (size in WORD_WINDOWS) {
            for (font in listOf(1.3f, 1.5f)) {
                windowSize = size
                fontScale = font
                bookmark = Bookmark.Entry
                practiceMs = null
                compose.waitForIdle()
                val where = "$language, ${size.width} × ${size.height}, $font"
                assertTheIdleCardsStandWhole("$where, idle")
                bookmark = Bookmark.Running(TITLE, minutesLeft = LEFT_MINUTES, progress = PROGRESS)
                practiceMs = PRACTICE_MS
                compose.waitForIdle()
                assertTheRunningCardsStandWhole("$where, running")
            }
        }
    }

    @Test
    fun inRussianAtLargeFontsTheWordsOfBothCardsStandWhole() = wordsWholeEverywhere("ru")

    @Test
    fun inGermanAtLargeFontsTheWordsOfBothCardsStandWhole() = wordsWholeEverywhere("de")

    @Test
    fun inFrenchAtLargeFontsTheWordsOfBothCardsStandWhole() = wordsWholeEverywhere("fr")

    @Test
    fun inPortugueseAtLargeFontsTheWordsOfBothCardsStandWhole() = wordsWholeEverywhere("pt")

    @Test
    fun inSpanishAtLargeFontsTheWordsOfBothCardsStandWhole() = wordsWholeEverywhere("es")

    /** The cards as wide as the row [rowStart] + [rowWidth] allows (both alike), the key in its middle, 8 between them, on one line. */
    private fun assertTheRow(what: String, rowStart: Dp, rowWidth: Dp) {
        val entry = card(word(ENTRY_SAID))
        val tag = card(word(START_SAID))
        val key = key().bounds()
        val card = LiveLayoutMath.keyCardWidth(rowWidth.value)
        assertEquals("$what: «Что играю» is as wide as the row allows", card, entry.width.value, 0.5f)
        assertEquals("$what: the practice tag as wide as «Что играю»", entry.width.value, tag.width.value, 0.5f)
        assertEquals("$what: a card is 60 high", 60f, entry.height.value, 0.5f)
        assertEquals("$what: the key is 76", 76f, key.width.value, 0.5f)
        assertEquals("$what: the key in the middle of its row", (rowStart + rowWidth / 2).value, ((key.left + key.right) / 2).value, 0.5f)
        assertEquals("$what: 8 between «Что играю» and the key", 8f, (key.left - entry.right).value, 0.5f)
        assertEquals("$what: 8 between the key and the practice tag", 8f, (tag.left - key.right).value, 0.5f)
        assertEquals("$what: the cards and the key on one line", ((key.top + key.bottom) / 2).value, ((entry.top + entry.bottom) / 2).value, 0.5f)
    }

    @Test
    fun bothCardsAreAsWideAsEachOtherAndTheKeyStandsInTheMiddle() {
        show()
        // upright the row is the width of the window: 150 on 412, 124 on 360, 104 on 320
        for (size in listOf(DpSize(412.dp, 788.dp), DpSize(360.dp, PHONE_HEIGHT), DpSize(320.dp, 500.dp))) {
            windowSize = size
            compose.waitForIdle()
            val box = window()
            assertTheRow("${size.width}", box.left, box.width)
        }
        // lying down it is the right column: 892 × 412 — 150; 640 × 360 behind a side cutout (603 × 336) — ≈ 94, the icons hidden
        for (size in listOf(DpSize(892.dp, 412.dp), DpSize(640.dp, 360.dp), DpSize(603.dp, 336.dp))) {
            windowSize = size
            compose.waitForIdle()
            val box = window()
            val start = box.left + box.width * LiveDimens.LANDSCAPE_RING_PANEL_FRACTION + LiveDimens.LandscapePaddingStart
            val width = box.width * (1f - LiveDimens.LANDSCAPE_RING_PANEL_FRACTION) - LiveDimens.LandscapePaddingStart - LiveDimens.LandscapePaddingEnd
            assertTheRow("${size.width} × ${size.height}", start, width)
        }
    }

    @Test
    fun theRecordKeyTellsTalkBackWhatItDoesAndDoesNotAnswerWhereThereIsNothingToRecord() {
        show()
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        key().assert(button).assertHasClickAction().assertIsEnabled()
        state = stateOf(LiveMode.PLAY, recording = RecordingState(elapsedMs = 84_000))
        compose.waitForIdle()
        compose.onNodeWithContentDescription(word(STOP)).assert(button).assertIsEnabled()
        // in «Настройка» there is nothing to record, nor without a microphone or its permission (spec 3.9): 0.4 and no answer
        for (quiet in listOf(stateOf(LiveMode.TUNING), stateOf(LiveMode.PLAY, LiveSignal.MicUnavailable), stateOf(LiveMode.PLAY, LiveSignal.NoMicPermission))) {
            state = quiet
            compose.waitForIdle()
            key().assert(button).assertIsNotEnabled()
        }
    }

    /** A lifecycle the test moves by hand, on the main thread. */
    private class Owner : LifecycleOwner {
        val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun liveLendsItsLightToTheTabBarWhileResumedOnly() {
        val bar = TabBarLight()
        val owner = Owner()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        var shown by mutableStateOf(true)
        var level by mutableFloatStateOf(DIMMED)
        compose.setContent {
            CompositionLocalProvider(LocalTabBarLight provides bar, LocalLifecycleOwner provides owner) {
                if (shown) LendTabBarLight(light = { level })
            }
        }
        compose.runOnIdle { assertEquals("lent while resumed", DIMMED, bar.alpha(), 0f) }
        level = HALF
        compose.runOnIdle { assertEquals("read at every draw, not kept", HALF, bar.alpha(), 0f) }
        // leaving Live pauses it at once: the bar is whole before the cross-fade of the navigation ends
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { assertEquals("taken back as Live pauses", 1f, bar.alpha(), 0f) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals("lent again as it resumes", HALF, bar.alpha(), 0f) }
        shown = false
        compose.runOnIdle { assertEquals("taken back as Live goes", 1f, bar.alpha(), 0f) }
    }

    /**
     * Spec 3.36.6: the bar dims with the controls wherever the root shows it under Live — upright, by the orientation of the window.
     * Upright in a split screen Live is wider than tall and lays itself out lying down (412 × 340), while the root shows the bar under
     * it: Live lends its light there too — lent by the shape of Live, the bar stayed whole while the switcher, the cards and the key
     * went down to 0.38. Lying down the root shows no bar on Live, and the light lent dims nothing.
     */
    @Test
    fun uprightInASplitScreenLiveLaidOutLyingDownDimsTheBarUnderIt() {
        val lent = TabBarLight()
        bar = lent
        picture = true
        windowSize = DpSize(412.dp, 340.dp)
        state = stateOf(LiveMode.PLAY, sounding)
        show()
        compose.runOnIdle { assertEquals("the bar goes down with the light of Live", VenueLook.chromeAlpha(1f), lent.alpha(), 0.01f) }
    }

    /** The colour of the window at [x], [y] (dp from the root), from a capture of the window. */
    private fun colourAt(pixels: PixelMap, x: Dp, y: Dp): Color {
        val window = window()
        return with(compose.density) { pixels[(x - window.left).roundToPx(), (y - window.top).roundToPx()] }
    }

    /**
     * Spec 3.28, 5.21: a block stopped at 40 % — the paper drains into the glass of «Что играю» (300 ms) with its brass line where it
     * stood, not run to its end as if the block were done: brass at a fifth of the line, the bone of its track at seven tenths. Frame
     * by frame, a third of the way through the cross-fade.
     */
    @Test
    fun aStoppedBlockDrainsIntoTheGlassWithItsLineWhereItStood() {
        show()
        bookmark = Bookmark.Running(TITLE, minutesLeft = LEFT_MINUTES, progress = PROGRESS)
        compose.waitForIdle()
        val card = card(word(BLOCK_SAID))
        compose.mainClock.autoAdvance = false
        bookmark = Bookmark.Entry
        repeat(DRAINING_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        // the draw pass of the last frame done, the clock still held
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(WINDOW).captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = true
        val y = card.bottom - LiveDimens.CardBarBottom - LiveDimens.CardBar / 2
        val start = card.left + LiveDimens.CardBarSide
        val length = card.width - LiveDimens.CardBarSide * 2
        val brass = colourAt(pixels, start + length * BRASS_AT, y)
        val track = colourAt(pixels, start + length * TRACK_AT, y)
        // the brass (#C9A24A) is far less blue than the bone of the track (#D8D0BE), both at the alpha of the draining paper
        assertTrue("the line of 40 % drains as it stood: brass $brass at a fifth, track $track at seven tenths", track.blue - brass.blue > COLOUR_APART)
    }

    /**
     * Spec 3.28, 3.36.6: a done block that ends with the practice drains into the glass as it was — in its brass rim, «готово» —
     * not, for the 300 ms of the cross-fade, a paper with no rim and the minutes back; once the glass is in, no rim is left.
     */
    @Test
    fun aDoneBlockDrainsIntoTheGlassInItsRim() {
        show()
        bookmark = Bookmark.Done(TITLE)
        compose.waitForIdle()
        val card = card(word(DONE_SAID))
        val y = (card.top + card.bottom) / 2
        // the middle of the rim of 2.5 outside the paper, and the field a little further out
        val rimAt = card.left - LiveDimens.CardRim / 2
        val fieldAt = card.left - FIELD_OUT
        compose.mainClock.autoAdvance = false
        bookmark = Bookmark.Entry
        repeat(DRAINING_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        val draining = compose.onNodeWithTag(WINDOW).captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val gone = compose.onNodeWithTag(WINDOW).captureToImage().toPixelMap()
        val rim = colourAt(draining, rimAt, y)
        val field = colourAt(draining, fieldAt, y)
        assertTrue("the draining paper keeps its brass rim: $rim against the field $field", rim.red - field.red > COLOUR_APART)
        val after = colourAt(gone, rimAt, y)
        val afterField = colourAt(gone, fieldAt, y)
        assertTrue("no rim once the glass is in: $after against the field $afterField", abs(after.red - afterField.red) < COLOUR_SAME)
    }

    private companion object {
        const val WINDOW = "window"
        const val ENTRY = "entry"
        const val ENTRY_HINT = "entryHint"
        const val ENTRY_SAID = "entrySaid"
        const val START = "start"
        const val PRACTICE = "practice"
        const val START_SAID = "startSaid"
        const val RUNNING_SAID = "runningSaid"
        const val LEFT = "left"
        const val DONE = "done"
        const val BLOCK_SAID = "blockSaid"
        const val RECORD = "record"
        const val STOP = "stop"

        /** A phone of 640: Live over the tabs. */
        val PHONE_HEIGHT = 506.dp

        /** The longest name of an element the repertoire gives by default, and a goal of two figures. */
        const val TITLE = "Концерт ля минор, соч. 3 № 6, I. Allegro"
        const val LEFT_MINUTES = 12
        const val PROGRESS = 0.4f
        const val PRACTICE_MS = 24 * 60_000L + 18_000L
        const val TIME = "24:18"

        const val DIMMED = 0.38f
        const val HALF = 0.7f

        const val DONE_SAID = "doneSaid"
        const val A4 = 69

        /** A picture behind Live that draws nothing: Live puts its light out over it while a note sounds (spec 3.27). */
        val NoPicture: @Composable (LiveBackdrop) -> Unit = {}

        /**
         * The windows of the words: 360 × 640 and 320 upright (cards of 124 and 104), 892 × 412 (the right column, 150) and 640 × 360
         * behind a side cutout (603 × 336, ≈ 94 — the narrowest card of the stage, no icons).
         */
        val WORD_WINDOWS = listOf(DpSize(360.dp, 506.dp), DpSize(320.dp, 506.dp), DpSize(892.dp, 412.dp), DpSize(603.dp, 336.dp))

        /** Six frames of 16 ms: about a third of the cross-fade of 300 ms, the first frame composing the change. */
        const val DRAINING_FRAMES = 6

        /** Where on the brass line of a block of 40 % the brass is and where its track. */
        const val BRASS_AT = 0.2f
        const val TRACK_AT = 0.7f

        /** How far apart two colours of the test are in a channel (0…1) — and how near is the same. */
        const val COLOUR_APART = 0.1f
        const val COLOUR_SAME = 0.04f

        /** The field of Live left of the card, clear of its rim and of the reach of its shadow sideways. */
        val FIELD_OUT = 8.dp
    }
}
