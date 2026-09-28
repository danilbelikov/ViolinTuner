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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
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

    private companion object {
        const val TAG = "switch"
        val LABELS = listOf("Разбираю", "Учу", "В репертуаре")
    }
}
