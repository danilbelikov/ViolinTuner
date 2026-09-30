package com.violinjourney.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four labels of the tab bar are one size (spec 3.36.1, 5.29 R1), and it steps down together until the widest of the language
 * stands whole in its quarter: on phones of 320 and 360 at the font 1.3 and 1.5, in Russian, German, French, Portuguese, Spanish and
 * Italian, and in the compact bar lying down. «Репертуар» at 1.5 on 320 was cut at its end («Репертуа|»): the one size stopped at 10
 * sp. Below 10 sp it goes only at a large font, never smaller on the screen than 10 sp of the default font; where the whole French
 * «Enregistrements» does not fit even at 10 sp, the short «Enreg.» comes, and TalkBack hears the whole word.
 *
 * The bar is laid out in a width of its own ([InWidth], a fake [LocalWindowInfo] too); the font scale is the test's, linear. The
 * words are read in the composition, in the language of the process ([speaking]).
 */
@RunWith(AndroidJUnit4::class)
class TabBarLabelsTest {
    @get:Rule
    val compose = createComposeRule()

    private var width by mutableStateOf(320.dp)
    private var fontScale by mutableFloatStateOf(1.5f)
    private var compact by mutableStateOf(false)
    private var whole: List<String> = emptyList()
    private var short: List<String> = emptyList()

    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWidth(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val info = with(density) { Window(IntSize(width.roundToPx(), WINDOW_HEIGHT.roundToPx())) }
        CompositionLocalProvider(LocalWindowInfo provides info, LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.requiredWidth(width)) { content() }
        }
    }

    private fun show() {
        compose.setContent {
            whole = TopLevelDestination.entries.map { stringResource(it.labelRes) }
            short = TopLevelDestination.entries.map { stringResource(it.shortLabelRes ?: it.labelRes) }
            ViolinTheme {
                InWidth { AppBottomBar(current = TopLevelDestination.REPERTOIRE, onSelect = {}, compact = compact) }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The label each tab shows — the whole one, or the short one where the whole ones do not fit — whole, in its tab, all one size. */
    private fun assertTheLabelsStandWhole(what: String) {
        val sizes = TopLevelDestination.entries.indices.map { index ->
            val shown = if (exists(whole[index])) whole[index] else short[index]
            val label = compose.onAllNodesWithText(shown, useUnmergedTree = true).onFirst()
            assertWholeOnOneLine(label, "$what: $shown")
            val tab = compose.onNodeWithText(shown).getUnclippedBoundsInRoot()
            val bounds = label.getUnclippedBoundsInRoot()
            assertTrue("$what: «$shown» at $bounds inside its tab $tab", bounds.left >= tab.left - 0.5.dp && bounds.right <= tab.right + 0.5.dp)
            // a short label says the whole word to TalkBack
            if (shown != whole[index]) compose.onNodeWithContentDescription(whole[index]).fetchSemanticsNode()
            label.textLayout().layoutInput.style.fontSize.value
        }
        assertTrue("$what: the four labels are one size: $sizes", sizes.all { it == sizes.first() })
        assertTrue("$what: no label smaller on the screen than 10 sp of the default font: ${sizes.first()} sp", sizes.first() * fontScale >= LEAST_ON_SCREEN - 0.01f)
    }

    private fun labelsStandWhole(language: String) {
        speaking(language)
        show()
        for (phone in listOf(320.dp, 360.dp)) {
            for (font in listOf(1f, 1.3f, 1.5f)) {
                width = phone
                fontScale = font
                compact = false
                compose.waitForIdle()
                assertTheLabelsStandWhole("$language, $phone, $font")
            }
        }
        // lying down: the compact bar, on 640 and behind a side cutout
        for (lying in listOf(640.dp, 603.dp)) {
            for (font in listOf(1f, 1.3f, 1.5f)) {
                width = lying
                fontScale = font
                compact = true
                compose.waitForIdle()
                assertTheLabelsStandWhole("$language, compact $lying, $font")
            }
        }
    }

    @Test
    fun inRussianTheFourLabelsStandWhole() = labelsStandWhole("ru")

    @Test
    fun inGermanTheFourLabelsStandWhole() = labelsStandWhole("de")

    @Test
    fun inFrenchTheFourLabelsStandWhole() = labelsStandWhole("fr")

    @Test
    fun inPortugueseTheFourLabelsStandWhole() = labelsStandWhole("pt")

    @Test
    fun inSpanishTheFourLabelsStandWhole() = labelsStandWhole("es")

    @Test
    fun inItalianTheFourLabelsStandWhole() = labelsStandWhole("it")

    /** «Репертуар» at 1.5 on 320: 9 sp, drawn 13.5 — below the 10 sp of the default font, above what 10 sp is there. */
    @Test
    fun onAPhoneOf320AtTheFont1_5TheRussianLabelsGoBelow10Sp() {
        speaking("ru")
        show()
        val size = compose.onNodeWithText(whole[TopLevelDestination.REPERTOIRE.ordinal], useUnmergedTree = true).textLayout().layoutInput.style.fontSize.value
        assertEquals(9f, size, 0.01f)
    }

    /** French on 320 at the default font: «Enregistrements» does not fit at 10 sp, «Enreg.» does at 12 — and is heard whole. */
    @Test
    fun inFrenchOnAPhoneOf320TheShortLabelComesAndIsHeardWhole() {
        speaking("fr")
        fontScale = 1f
        show()
        val history = TopLevelDestination.HISTORY.ordinal
        assertTrue("the short one is shown: ${short[history]}", exists(short[history]) && !exists(whole[history]))
        compose.onNodeWithContentDescription(whole[history]).fetchSemanticsNode()
        assertEquals(12f, compose.onNodeWithText(short[history], useUnmergedTree = true).textLayout().layoutInput.style.fontSize.value, 0.01f)
    }

    private companion object {
        val WINDOW_HEIGHT: Dp = 640.dp

        /** 10 sp of the default font, the least a label is drawn (spec 5.29 R1; `TabLabels.LEAST_DP`, internal to the shared code). */
        const val LEAST_ON_SCREEN = 10f
    }
}
