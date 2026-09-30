package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.numbers
import com.violinjourney.app.testing.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The segmented switch of the redesign (spec 3.36.1, 5.29): the whole height of its container is pressed, not only the pill drawn
 * 4 dp inside it; the compact one answers over 48 dp while 28 are seen; a large font makes the container taller instead of cutting
 * a label; each segment reads as a radio button, chosen or not.
 *
 * Compose widens by itself a touch target lower than 48 dp — the pill of 44 by 2 dp above and below, the compact pill of 24 to 48 —
 * so with it a touch near the edge would land on the pill as well and could not tell the two apart. The tests switch it off: they
 * see what the switch lays out itself.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    private var selected by mutableStateOf<Int?>(0)

    private fun show(
        compact: Boolean,
        modifier: Modifier = Modifier,
        fontScale: Float? = null,
        enabled: Boolean = true,
        segmentEnabled: (Int) -> Boolean = { true },
    ) {
        compose.setContent {
            val base = LocalViewConfiguration.current
            val density = LocalDensity.current
            val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
            CompositionLocalProvider(
                LocalViewConfiguration provides noWidening,
                LocalDensity provides (fontScale?.let { Density(density.density, it) } ?: density),
            ) {
                ViolinTheme {
                    SegmentedSwitch(
                        labels = LABELS,
                        selectedIndex = selected,
                        onSelect = { selected = it },
                        modifier = modifier.testTag(TAG),
                        compact = compact,
                        enabled = enabled,
                        segmentEnabled = segmentEnabled,
                    )
                }
            }
        }
    }

    /** A touch 1 dp under the top edge of the middle segment: 3 dp above its pill, inside the container. */
    private fun touchTheMiddleNearTheTop() {
        compose.onNodeWithTag(TAG).performTouchInput { click(Offset(width / 2f, 1.dp.toPx())) }
    }

    @Test
    fun theWholeHeightOfTheContainerIsPressed() {
        show(compact = false)
        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(52.dp)
        LABELS.forEach { compose.onNodeWithText(it).assertTouchHeightIsEqualTo(52.dp) }
        touchTheMiddleNearTheTop()
        compose.runOnIdle { assertEquals(1, selected) }
    }

    /** The sign and the mode of «Тональность» without a tonic (spec 3.36.4): the whole switch sleeps and says so. */
    @Test
    fun aSwitchThatSleepsIsReadAsUnavailableAndTakesNoTouch() {
        show(compact = false, enabled = false)
        LABELS.forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        touchTheMiddleNearTheTop()
        compose.runOnIdle { assertEquals(0, selected) }
    }

    /** The octave off the violin (spec 3.36.4): that segment alone sleeps; the others answer. */
    @Test
    fun aSegmentThatSleepsAloneLeavesTheOthersAnswering() {
        show(compact = false, segmentEnabled = { it != 1 })
        compose.onNodeWithText(LABELS[1]).assertIsNotEnabled()
        compose.onNodeWithText(LABELS[2]).assertIsEnabled()
        touchTheMiddleNearTheTop()
        compose.runOnIdle { assertEquals(0, selected) }
        compose.onNodeWithText(LABELS[2]).performClick()
        compose.runOnIdle { assertEquals(2, selected) }
    }

    @Test
    fun theCompactSwitchAnswersOverFortyEightDp() {
        show(compact = true)
        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(48.dp)
        // each segment, not only its pill of 24, is pressed over the 48
        LABELS.forEach { compose.onNodeWithText(it).assertTouchHeightIsEqualTo(48.dp) }
    }

    @Test
    fun aLargeFontMakesTheContainerTallerRatherThanCutALabel() {
        // three segments of 100 dp: at twice the font «Разбираю» goes on two lines, which a container of 52 would cut
        show(compact = false, modifier = Modifier.width(300.dp), fontScale = 2f)
        val container = compose.onNodeWithTag(TAG).getUnclippedBoundsInRoot().height
        assertTrue("the container grows from 52: $container", container > 52.dp)
        LABELS.forEach { compose.onNodeWithText(it).assertTouchHeightIsEqualTo(container) }
    }

    @Test
    fun eachSegmentReadsAsARadioButtonChosenOrNot() {
        show(compact = false)
        LABELS.forEach { label -> compose.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)) }
        compose.onNodeWithText(LABELS[0]).assertIsSelected()
        compose.onNodeWithText(LABELS[2]).assertIsNotSelected()
    }

    private fun showStatus(labels: List<String>, width: Int, fontScale: Float, byWords: Boolean) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ViolinTheme {
                    SegmentedSwitch(
                        labels = labels,
                        selectedIndex = 0,
                        onSelect = {},
                        modifier = Modifier.width(width.dp),
                        byWords = byWords,
                        wholeWords = true,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Each label goes on to a next line only at a space and is not cut: no word of it breaks by the letter. */
    private fun assertWordsWhole(labels: List<String>) {
        labels.forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                assertTrue("«$label» breaks inside a word after «${label.substring(0, end)}»", label[end - 1].isWhitespace())
            }
            assertFalse("«$label» is cut", layout.isLineEllipsized(layout.lineCount - 1))
        }
    }

    // spec 3.36.4, the review of stage 109: 360 × 640 at the font 1.3 (here linear: 14 sp are 18.2 dp) — a third of the column of 328
    // leaves «репертуаре» 95 dp, and it broke by the letter; the steps share the row by their words instead
    @Test
    fun theStatusOn360WithALargeFontBreaksNoWord() {
        showStatus(LABELS, width = 328, fontScale = 1.3f, byWords = false)
        assertWordsWhole(LABELS)
    }

    // the left column of 300 lying (268 inside its fields): shared by the whole lines, «Déchiffrage» got 78 dp and broke by the letter
    // while «Au répertoire» could have gone on two lines at its space
    @Test
    fun byWordsAWordStandsWholeAndALabelOfTwoWordsWrapsAtItsSpace() {
        showStatus(FRENCH, width = 268, fontScale = 1f, byWords = true)
        assertWordsWhole(FRENCH)
    }

    // ---- the player of R5 (spec 3.36.5, 5.29 R5)

    private val holds = mutableListOf<Pair<Int, Boolean>>()

    private fun showAb(compact: Boolean = false, fontScale: Float? = null) {
        compose.setContent {
            val base = LocalViewConfiguration.current
            val density = LocalDensity.current
            val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
            CompositionLocalProvider(
                LocalViewConfiguration provides noWidening,
                LocalDensity provides (fontScale?.let { Density(density.density, it) } ?: density),
            ) {
                ViolinTheme {
                    SegmentedSwitch(
                        labels = listOf("A", "B"),
                        selectedIndex = selected,
                        onSelect = { selected = it },
                        modifier = Modifier.width(96.dp).testTag(TAG),
                        compact = compact,
                        strong = true,
                        segmentDescriptions = AB_WORDS,
                        onHold = { index, held -> holds += index to held },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** «Пока держишь» (spec 3.17): a long press on A says when it begins and when the finger lets go; it does not choose. */
    @Test
    fun aHoldOnASegmentIsToldWhenItBeginsAndWhenItEnds() {
        selected = 1
        showAb()
        compose.onNodeWithContentDescription(AB_WORDS[0]).performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(0 to true, 0 to false), holds)
            assertEquals("a hold does not choose: B stays chosen under it", 1, selected)
        }
    }

    /** A tap chooses and tells no hold — its press ends, but it held nothing. */
    @Test
    fun aTapOnAHoldableSegmentChoosesAndHoldsNothing() {
        selected = 1
        showAb()
        compose.onNodeWithContentDescription(AB_WORDS[0]).performClick()
        compose.runOnIdle {
            assertEquals(0, selected)
            assertEquals(emptyList<Pair<Int, Boolean>>(), holds)
        }
    }

    /** To TalkBack each half is «A, оригинал» / «B, обработка», a radio button with its mark, that its activation chooses; «A» alone is not heard. */
    @Test
    fun eachSegmentIsReadByItsDescriptionAndTheReaderChooses() {
        selected = 1
        showAb()
        val a = compose.onNodeWithContentDescription(AB_WORDS[0])
        a.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsNotSelected()
        compose.onNodeWithContentDescription(AB_WORDS[1]).assertIsSelected()
        compose.onAllNodesWithText("A").assertCountEquals(0)
        a.performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(0, selected) }
    }

    /** The compact A/B of a low window: 48 to press, whole segments of 48 — the width of 96 gives each half its target. */
    @Test
    fun theCompactAbIsPressedOverFortyEightEachWay() {
        showAb(compact = true)
        AB_WORDS.forEach { word ->
            compose.onNodeWithContentDescription(word).assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp)
        }
    }

    /**
     * …and at the font 1.3 (spec 5.29 R5: seen 28, pressed 48): «A» of 14 sp is 18.8 dp there (Android's curve of large fonts), its
     * line of 1.15 — 21.6 dp — stands in the pill of 24. The box of the font's own ascent and descent (Manrope: 1.37 em, 26.3 dp)
     * grew the pill and the switch to 50.67 (ExactLines).
     */
    @Test
    fun theCompactAbAtALargeFontStaysFortyEight() {
        showAb(compact = true, fontScale = 1.3f)
        AB_WORDS.forEach { word ->
            compose.onNodeWithContentDescription(word).assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp)
        }
    }

    /**
     * The backing's switch in [width] dp at the font 1.0 whatever the device's: the sizes these tests expect are worked out at it —
     * a device left at a large font (the check of a stage sets 1.3) would take other ways of fitting.
     */
    private fun showBacking(width: Int, compact: Boolean = false) {
        compose.setContent {
            val base = LocalViewConfiguration.current
            val density = LocalDensity.current
            val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
            CompositionLocalProvider(LocalViewConfiguration provides noWidening, LocalDensity provides Density(density.density, 1f)) {
                ViolinTheme {
                    SegmentedSwitch(
                        labels = SPANISH,
                        selectedIndex = 0,
                        onSelect = {},
                        modifier = Modifier.width(width.dp).testTag(TAG),
                        compact = compact,
                        strong = true,
                        shrinkToTwoLines = true,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * «С минусовкой | Только скрипка» (5.29 R5): a label too wide for its half at 14 sp takes both down to two lines of 12 in the same
     * height — the words are whole, they go on at a space. Spanish «Con acompañamiento» (≈ 154 dp at 14 sp, 132 at 12, «acompañamiento»
     * alone 105) in halves of 135 — 121 for the words: the column of 640 × 360 lying behind a cutout.
     */
    @Test
    fun aLabelTooWideForItsHalfGoesOnTwoSmallerLinesInTheSameHeight() {
        showBacking(width = 270)
        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(52.dp)
        SPANISH.forEach { label ->
            // an AnnotatedString: its semantics hands the layout drawn (testing/TextLayouts.kt)
            val layout = compose.onNodeWithText(label, useUnmergedTree = true).textLayout()
            assertEquals("«$label» at 12 sp", 12f, layout.layoutInput.style.fontSize.value, 0.01f)
            for (line in 0 until layout.lineCount - 1) {
                assertTrue("«$label» goes on at a space", label[layout.getLineEnd(line) - 1].isWhitespace())
            }
            assertFalse("«$label» is not cut", layout.isLineEllipsized(layout.lineCount - 1))
        }
        assertEquals(2, compose.onNodeWithText(SPANISH[0], useUnmergedTree = true).textLayout().lineCount)
    }

    /** Labels that stand in their halves keep their size. */
    @Test
    fun labelsThatStandInTheirHalvesKeepFourteen() {
        showBacking(width = 412)
        SPANISH.forEach { label ->
            assertEquals(14f, compose.onNodeWithText(label, useUnmergedTree = true).textLayout().layoutInput.style.fontSize.value, 0.01f)
        }
    }

    /**
     * The compact switch of a low window (5.29 R5: seen 28, pressed 48): its pills of 24 hold one line — the same Spanish in the same 270
     * goes down to one line of 12, «Con acompañamiento» (132 + 11 of 135) taking its line and «Solo violín» the rest, and the switch
     * stays 48: two lines of 12 (≈ 28) would have grown it to ≈ 52.
     */
    @Test
    fun aCompactLabelTooWideForItsHalfStaysOnOneSmallerLineInTheSameHeight() {
        showBacking(width = 270, compact = true)
        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(48.dp)
        SPANISH.forEach { label ->
            val node = compose.onNodeWithText(label, useUnmergedTree = true)
            assertEquals("«$label» at 12 sp", 12f, node.textLayout().layoutInput.style.fontSize.value, 0.01f)
            assertWholeOnOneLine(node, label)
        }
    }

    /**
     * The large A/B of «Звук» with its words (spec 3.36.5, 5.29 R5): «A» / «B» a step larger and heavier before the words, which are
     * cut nowhere — Portuguese «processado» at the font 1.3, [width] dp wide. Without the words for TalkBack, so that their layout can
     * be read: the screen gives each half its description instead.
     */
    private fun showWordsAb(width: Int, compact: Boolean) {
        compose.setContent {
            val base = LocalViewConfiguration.current
            val density = LocalDensity.current
            val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
            CompositionLocalProvider(LocalViewConfiguration provides noWidening, LocalDensity provides Density(density.density, LARGE_FONT)) {
                ViolinTheme {
                    SegmentedSwitch(
                        labels = PORTUGUESE_AB,
                        selectedIndex = 1,
                        onSelect = {},
                        modifier = Modifier.width(width.dp).testTag(TAG),
                        compact = compact,
                        prefixes = AB_SIDES,
                        shrinkToTwoLines = true,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** The label of the half [index] as it is drawn: the side, the gap of an en space, the word. */
    private fun abLabel(index: Int): String = AB_SIDES[index] + PREFIX_GAP + PORTUGUESE_AB[index]

    /**
     * The compact A/B with its words in the narrow column lying (5.29 R5: 12 sp in one line): «B processado» at the font 1.3 is ≈ 130 dp
     * at 14 sp, its half of 270 leaves 123 — both labels go down to one line of 12 (≈ 109), the side measured with them, and the switch
     * stays 48.
     */
    @Test
    fun theCompactAbWithItsWordsStaysOnOneSmallerLineInFortyEight() {
        showWordsAb(width = 270, compact = true)
        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(48.dp)
        AB_SIDES.indices.forEach { index ->
            val node = compose.onNodeWithText(abLabel(index), useUnmergedTree = true)
            assertEquals("«${abLabel(index)}» at 12 sp", 12f, node.textLayout().layoutInput.style.fontSize.value, 0.01f)
            assertWholeOnOneLine(node, abLabel(index))
        }
    }

    /**
     * The regular A/B with its words where even 12 sp do not stand in a line (5.29 R5: two lines of 12 in the same height): in 230 at the
     * font 1.3 «B processado» (≈ 109 at 12 sp) has 100 — it goes on after the side, at the en space, «processado» (≈ 90) whole on its
     * line; nothing ends in «…», and the switch stays 52.
     */
    @Test
    fun theRegularAbWithItsWordsGoesOnAfterItsSideAndBreaksNoWord() {
        showWordsAb(width = 230, compact = false)
        compose.onNodeWithTag(TAG).assertHeightIsEqualTo(52.dp)
        val node = compose.onNodeWithText(abLabel(1), useUnmergedTree = true)
        val layout = node.textLayout()
        assertEquals("two lines: ${layout.numbers(node)}", 2, layout.lineCount)
        assertTrue("it goes on at the en space after «B» — ${layout.numbers(node)}", abLabel(1)[layout.getLineEnd(0) - 1] == PREFIX_GAP.single())
        AB_SIDES.indices.forEach { index ->
            val each = compose.onNodeWithText(abLabel(index), useUnmergedTree = true).textLayout()
            assertFalse("«${abLabel(index)}» is not cut", each.isLineEllipsized(each.lineCount - 1) || each.didOverflowHeight)
        }
    }

    private companion object {
        const val TAG = "switch"
        val LABELS = listOf("Разбираю", "Учу", "В репертуаре")
        val FRENCH = listOf("Déchiffrage", "En travail", "Au répertoire")
        val SPANISH = listOf("Con acompañamiento", "Solo violín")
        val AB_WORDS = listOf("A, original", "B, processed")

        /** The large A/B of «Звук» in Portuguese, and the sides before its words; the gap between them is the switch's en space. */
        val PORTUGUESE_AB = listOf("original", "processado")
        val AB_SIDES = listOf("A", "B")
        const val PREFIX_GAP = "\u2002"
        const val LARGE_FONT = 1.3f
    }
}
