package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_close
import com.violinjourney.app.shared.resources.restore_title
import com.violinjourney.app.shared.resources.session_back
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
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
 * The header of a screen over the tabs (spec 3.36.8, 5.29 R8): 56 upright, 48 in a window wider than high; «назад» and ✕ are 48 and
 * say what they do; the title is a heading on one line, cut with an ellipsis where it is long and read whole; without a button it
 * starts at 16 from the edge. Laid out in a window of its own size ([TestWindow]), the words read in the composition.
 */
@RunWith(AndroidJUnit4::class)
class ScreenHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private var windowSize by mutableStateOf(DpSize(412.dp, 868.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private var title = ""
    private var back = ""
    private var close = ""
    private var pressed = 0

    /** The language of the device, given back after every test. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun show() {
        compose.setContent {
            title = stringResource(Res.string.restore_title)
            back = stringResource(Res.string.session_back)
            close = stringResource(Res.string.backup_close)
            ViolinTheme {
                TestWindow(windowSize, fontScale = fontScale) {
                    Column {
                        ScreenHeader(title, onBack = { pressed++ }, modifier = Modifier.testTag(WITH_BACK))
                        ScreenHeader(title = null, onBack = { pressed++ }, modifier = Modifier.testTag(WITH_CLOSE), close = true)
                        ScreenHeader(title, onBack = null, modifier = Modifier.testTag(TITLE_ALONE))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun windowLeft(): Dp = compose.onNodeWithTag(TEST_WINDOW).bounds().left

    private fun assertNear(what: String, expected: Dp, actual: Dp) =
        assertTrue("$what: $actual, expected $expected", actual in (expected - 0.5.dp)..(expected + 0.5.dp))

    @Test
    fun theHeaderIs56UprightAnd48Lying() {
        show()
        assertNear("upright", ScreenHeaderDefaults.Height, compose.onNodeWithTag(WITH_BACK).bounds().height)
        windowSize = DpSize(603.dp, 308.dp)
        compose.waitForIdle()
        assertNear("lying", ScreenHeaderDefaults.HeightLying, compose.onNodeWithTag(WITH_BACK).bounds().height)
    }

    @Test
    fun backAndCloseAre48AndSayWhatTheyDo() {
        show()
        val backButton = compose.onNode(hasContentDescription(back) and hasClickAction())
        backButton.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val box = backButton.bounds()
        assertNear("«назад» wide", ScreenHeaderDefaults.Button, box.width)
        assertNear("«назад» high", ScreenHeaderDefaults.Button, box.height)
        assertNear("«назад» 4 from the edge", windowLeft() + ScreenHeaderDefaults.Inset, box.left)
        backButton.performSemanticsAction(SemanticsActions.OnClick)
        val closeButton = compose.onNode(hasContentDescription(close) and hasClickAction())
        assertNear("✕ wide", ScreenHeaderDefaults.Button, closeButton.bounds().width)
        closeButton.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("both did what they say", 2, pressed)
    }

    @Test
    fun theTitleIsAHeadingAndWithoutAButtonStartsAt16() {
        show()
        compose.onAllNodes(isHeading()).assertCountEquals(2)
        val withBack = compose.onNode(isHeading() and hasAnyAncestor(hasTestTag(WITH_BACK))).bounds()
        val alone = compose.onNode(isHeading() and hasAnyAncestor(hasTestTag(TITLE_ALONE))).bounds()
        assertNear("the title without a button", windowLeft() + ScreenHeaderDefaults.TitleSide, alone.left)
        assertNear("the title after «назад»", windowLeft() + ScreenHeaderDefaults.Inset * 2 + ScreenHeaderDefaults.Button, withBack.left)
    }

    /** A long title stands on one line with an ellipsis, and a reader hears it whole: German «Aus Kopie wiederherstellen» on 360 at 1.3. */
    @Test
    fun aLongTitleIsCutWithAnEllipsisOnOneLine() {
        Locale.setDefault(Locale.forLanguageTag("de"))
        windowSize = DpSize(360.dp, 640.dp)
        fontScale = 1.3f
        show()
        val node = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(WITH_BACK)), useUnmergedTree = true)
        val layout = node.textLayout()
        assertEquals("one line", 1, layout.lineCount)
        assertTrue("cut with an ellipsis", layout.isLineEllipsized(0))
    }

    private companion object {
        const val WITH_BACK = "with back"
        const val WITH_CLOSE = "with close"
        const val TITLE_ALONE = "title alone"
    }
}
