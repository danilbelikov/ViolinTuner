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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.components.LiveDimens
import com.violinjourney.app.feature.live.components.LiveRecordKey
import com.violinjourney.app.feature.live.components.SettingsGear
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mic_permission_grant
import com.violinjourney.app.shared.resources.mic_permission_text
import com.violinjourney.app.shared.resources.mic_permission_title
import com.violinjourney.app.shared.resources.status_in_tune
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
 * Live without the permission for the microphone, R6 (spec 3.36.6, 5.29 R6): no ring — the card «нет разрешения» in its place;
 * upright between the place of the status line and the bottom row (in «Настройка» over the scale), its middle at 60 % of the place
 * unless it rests on its bottom ([LiveLayoutMath.promptTop]), not wider than 360 with 26 from the sides; lying down in the middle of
 * the panel of the ring, not wider than 340. In every window of the stage — 412 × 892, 360 × 640 and 320 × 500 upright, 892 × 412
 * and 640 × 360 behind a side cutout lying down — at the font 1, 1.3 and 1.5, in «Игра» and in «Настройка»: its title and «Разрешить
 * доступ» stand whole in the place, no word of the title broken inside, the button a button of 54 whatever the place; its text is
 * seen in whole lines or, where not a line of it stands, heard with the title; the veil of the room (5.20) takes the card's middle
 * and its width. «Разрешить доступ» asks for the permission. Before R6 the prompt under the ring hid its button in landscape.
 *
 * Live is laid out in a window of its own size, whatever the device's ([WINDOW], and a fake [LocalWindowInfo] too), over a picture
 * that only keeps what Live tells it ([look]); everything is measured from the window, the place in the whole pixels its column lays
 * its rows out in. The words are read in the composition ([speaking]).
 */
@RunWith(AndroidJUnit4::class)
class LiveNoPermissionTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = mutableMapOf<String, String>()
    private var state by mutableStateOf(stateOf(LiveMode.PLAY))
    private var windowSize by mutableStateOf(DpSize(412.dp, 788.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private val intents = mutableListOf<LiveIntent>()

    /** What Live tells the picture behind it: where the veil stands and how wide it is. */
    private var look: LiveBackdrop? = null

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun stateOf(mode: LiveMode) = LiveReducer.stateOf(LiveTarget(mode, null), IntonationConfig(), LiveSignal.NoMicPermission)

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
            words[TITLE] = stringResource(Res.string.mic_permission_title)
            words[TEXT] = stringResource(Res.string.mic_permission_text)
            words[GRANT] = stringResource(Res.string.mic_permission_grant)
            words[IN_TUNE] = stringResource(Res.string.status_in_tune)
            ViolinTheme {
                InWindow {
                    LiveScreenLayout(
                        state = state,
                        onIntent = { intents += it },
                        slots = LiveSlots(
                            backdrop = { picture -> look = picture },
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

    private fun Dp.px(): Int = with(compose.density) { roundToPx() }

    private fun Int.dp(): Dp = with(compose.density) { toDp() }

    private fun Float.dp(): Dp = with(compose.density) { toDp() }

    /**
     * The place of the card, in the whole pixels the layout gives it: upright between the place of the status line and the keys (over
     * the scale in «Настройка») — each row rounded by itself, as the column lays them out; lying down the panel of the ring, its share
     * of the row.
     */
    private fun placeOf(tuning: Boolean, landscape: Boolean): DpRect {
        val box = window()
        return if (landscape) {
            val panel = (box.width.px() * LiveDimens.LANDSCAPE_RING_PANEL_FRACTION).roundToInt()
            DpRect(box.left, box.top, box.left + panel.dp(), box.bottom)
        } else {
            val above = LiveDimens.SwitcherTopPadding.px() + LiveDimens.TopRowHeight.px() +
                (if (tuning) LiveDimens.StringRowTopPadding.px() + LiveDimens.StringButtonHeight.px() else 0) +
                LiveDimens.StatusLineTopPadding.px() + LiveDimens.StatusLineHeight.px()
            val below = LiveDimens.KeyRowPadding.px() * 2 + LiveDimens.RecordKeySize.px() +
                (if (tuning) LiveDimens.ScaleTopGap.px() + LiveDimens.ScaleHeight.px() else 0)
            DpRect(box.left, box.top + above.dp(), box.right, box.top + (box.height.px() - below).dp())
        }
    }

    private fun assertInside(what: String, inner: DpRect, outer: DpRect) {
        assertTrue("$what: $inner stands inside $outer", inner.left >= outer.left - 0.5.dp && inner.right <= outer.right + 0.5.dp)
        assertTrue("$what: $inner stands inside $outer", inner.top >= outer.top - 0.5.dp && inner.bottom <= outer.bottom + 0.5.dp)
    }

    private fun Char.breaksALine() = this == ' ' || this == '-'

    /** The card itself: the group TalkBack goes through — the title, the text, the button. */
    private fun card(): DpRect = compose.onNodeWithText(word(TITLE)).onParent().bounds()

    private fun assertTheCardStandsInItsPlace(what: String, tuning: Boolean, landscape: Boolean) {
        val place = placeOf(tuning, landscape)
        val card = card()
        assertInside("$what: the card", card, place)
        // not wider than 360 with 26 from the sides upright, than 340 with the ring's margin lying down; in the middle of its place
        val cardWidth = if (landscape) {
            minOf(LiveDimens.PromptMaxWidthLandscape.px(), place.width.px() - LiveDimens.PromptPanelMargin.px() * 2)
        } else {
            minOf(LiveDimens.PromptMaxWidth.px(), place.width.px() - LiveDimens.PromptSide.px() * 2)
        }
        assertEquals("$what: the width of the card", cardWidth.dp().value, card.width.value, 0.5f)
        assertEquals("$what: the card in the middle of its place", ((place.left + place.right) / 2).value, ((card.left + card.right) / 2).value, 0.5f)
        // upright its middle at 60 % of its place, below the middle — unless it rests on its bottom; lying down in the middle of the panel
        val top = if (landscape) {
            (place.height.px() - card.height.px()) / 2
        } else {
            LiveLayoutMath.promptTop(place.height.px().toFloat(), card.height.px().toFloat()).roundToInt()
        }
        assertEquals("$what: the top of the card in its place ${place.height}", top.dp().value, (card.top - place.top).value, 0.5f)

        val title = compose.onNodeWithText(word(TITLE))
        title.assert(isHeading())
        assertInside("$what: the title", title.bounds(), card)
        val layout = title.textLayout()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("$what: the title breaks its line ${line + 1} inside a word, at $end", end > 0 && word(TITLE)[end - 1].breaksALine())
        }
        assertFalse("$what: the title lost a line", layout.multiParagraph.didExceedMaxLines)
        val button = compose.onNodeWithText(word(GRANT))
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val grant = button.bounds()
        assertInside("$what: «${word(GRANT)}»", grant, card)
        assertEquals("$what: the button is 54 high", 54f, grant.height.value, 0.5f)
        // the words of the button stand whole: on one line, or on two at a space
        val words = compose.onNodeWithText(word(GRANT), useUnmergedTree = true)
        assertInside("$what: the words of the button", words.bounds(), grant)
        val wordsLayout = words.textLayout()
        assertFalse("$what: the words of the button lost a line", wordsLayout.multiParagraph.didExceedMaxLines)
        if (wordsLayout.lineCount == 1) {
            assertTrue("$what: the words of the button are cut", wordsLayout.multiParagraph.maxIntrinsicWidth <= wordsLayout.size.width)
        } else {
            val end = wordsLayout.getLineEnd(0)
            assertTrue("$what: the words of the button break inside a word", word(GRANT)[end - 1].breaksALine())
        }
        assertTheTextIsSeenInWholeLinesOrHeard(what, title)
        // no ring and no word of the status: the card is in their place
        assertTrue("$what: no word of the status", compose.onAllNodesWithText(word(IN_TUNE)).fetchSemanticsNodes().isEmpty())
        assertTheVeilFollowsTheCard(what, card)
    }

    /**
     * The text scrolls inside the card in whole lines (spec 3.36.6, 5.29 R6): its window ends where one of its lines ends — never in the
     * middle of a line, which would show the tops of its letters and no text. Where not a line of it stands, it is not on the screen,
     * and TalkBack, which skips what has no room there, hears it with the title.
     */
    private fun assertTheTextIsSeenInWholeLinesOrHeard(what: String, title: SemanticsNodeInteraction) {
        val seen = compose.onAllNodesWithText(word(TEXT), useUnmergedTree = true).fetchSemanticsNodes()
        if (seen.isEmpty()) {
            title.assert(
                SemanticsMatcher("the title says the text") { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains(word(TEXT)) } == true
                },
            )
            return
        }
        val text = compose.onNodeWithText(word(TEXT), useUnmergedTree = true)
        val window = with(compose.density) { text.getBoundsInRoot().height.toPx() }
        val layout = text.textLayout()
        val ends = (0 until layout.lineCount).map { layout.getLineBottom(it) }
        assertTrue("$what: the text shows $window px, not a whole line of it — its lines end at $ends", ends.any { abs(it - window) <= 1f })
    }

    /** The veil of the room (5.20) from the middle of the card, its width in the place of the diameter of the ring. */
    private fun assertTheVeilFollowsTheCard(what: String, card: DpRect) {
        val picture = checkNotNull(look) { "$what: Live tells the picture where the card is" }
        val box = window()
        assertEquals("$what: the veil as wide as the card", card.width.value, picture.ringDiameter().dp().value, 0.5f)
        val middle = picture.ringCenter()
        assertEquals("$what: the veil at the middle of the card, down", ((card.top + card.bottom) / 2 - box.top).value, middle.y.dp().value, 0.5f)
        // Live finds the middle from the bounds of the card clipped to the screen: a window wider than the device (892 or 603 wide
        // lying down on a phone upright) puts part of the panel off the screen, and there only the height is the card's own
        val screen = compose.onRoot().bounds()
        if (card.left >= screen.left && card.right <= screen.right) {
            assertEquals("$what: the veil at the middle of the card, across", ((card.left + card.right) / 2 - box.left).value, middle.x.dp().value, 0.5f)
        }
    }

    private fun everywhere(language: String?) {
        language?.let(::speaking)
        show()
        for (size in WINDOWS) {
            for (font in FONTS) {
                for (mode in LiveMode.entries) {
                    windowSize = size
                    fontScale = font
                    state = stateOf(mode)
                    compose.waitForIdle()
                    val landscape = size.width > size.height
                    assertTheCardStandsInItsPlace("${language ?: "device"}, ${size.width} × ${size.height}, $font, $mode", mode == LiveMode.TUNING, landscape)
                }
            }
        }
    }

    @Test
    fun inRussianTheCardStandsInItsPlaceInEveryWindowAtEveryFont() = everywhere("ru")

    @Test
    fun inGermanTheCardStandsInItsPlaceInEveryWindowAtEveryFont() = everywhere("de")

    @Test
    fun inFrenchTheCardStandsInItsPlaceInEveryWindowAtEveryFont() = everywhere("fr")

    @Test
    fun inTheLanguageOfTheDeviceTheCardStandsInItsPlaceInEveryWindowAtEveryFont() = everywhere(null)

    /** Spec 3.4: «Разрешить доступ» asks for the permission — the one thing the card is there for, upright and lying down. */
    @Test
    fun theButtonAsksForThePermission() {
        show()
        for (size in listOf(DpSize(412.dp, 788.dp), DpSize(603.dp, 336.dp))) {
            windowSize = size
            compose.waitForIdle()
            intents.clear()
            compose.onNodeWithText(word(GRANT)).performClick()
            compose.runOnIdle { assertEquals("${size.width} × ${size.height}", listOf<LiveIntent>(LiveIntent.GrantMicClicked), intents.toList()) }
        }
    }

    private companion object {
        const val WINDOW = "window"
        const val TITLE = "title"
        const val TEXT = "text"
        const val GRANT = "grant"
        const val IN_TUNE = "inTune"

        /**
         * 412 × 892, 360 × 640 and the small phone of 320 × 500 upright (Live over the tabs; 320 × 500 is Live itself); 892 × 412 and
         * 640 × 360 behind a side cutout lying down.
         */
        val WINDOWS = listOf(
            DpSize(412.dp, 788.dp),
            DpSize(360.dp, 506.dp),
            DpSize(320.dp, 500.dp),
            DpSize(892.dp, 412.dp),
            DpSize(603.dp, 336.dp),
        )
        val FONTS = listOf(1f, 1.3f, 1.5f)
    }
}
