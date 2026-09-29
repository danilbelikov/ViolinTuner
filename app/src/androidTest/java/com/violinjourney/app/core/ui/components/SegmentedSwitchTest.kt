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
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
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

    private fun show(compact: Boolean, modifier: Modifier = Modifier, fontScale: Float? = null) {
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

    private companion object {
        const val TAG = "switch"
        val LABELS = listOf("Разбираю", "Учу", "В репертуаре")
        val FRENCH = listOf("Déchiffrage", "En travail", "Au répertoire")
    }
}
