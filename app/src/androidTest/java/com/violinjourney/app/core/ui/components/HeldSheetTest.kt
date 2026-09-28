package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A sheet closed by its own button — the owner drops its value — slides away as a swiped one does (the sheets of
 * «Занятия», the blocks of Live); one that another sheet replaces goes at once; and whatever way it went, the next
 * opening comes up from below — also when it went while it was still rising.
 */
@RunWith(AndroidJUnit4::class)
class HeldSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var value by mutableStateOf<String?>(FIRST)
    private var slideAway by mutableStateOf(true)

    @OptIn(ExperimentalMaterial3Api::class)
    private lateinit var state: SheetState

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Sheet() {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        state = sheetState
        val shown = rememberHeldSheet(value, sheetState, slideAway) ?: return
        ModalBottomSheet(onDismissRequest = { value = null }, sheetState = sheetState) {
            Box(Modifier.fillMaxWidth().height(SHEET.dp)) { Text(shown) }
        }
    }

    private fun show() {
        compose.setContent { ViolinTheme { Sheet() } }
        compose.waitForIdle()
        compose.onNodeWithText(FIRST).assertIsDisplayed()
    }

    /** Opens the sheet and stops the clock while it still rises: M3 counts it visible only once the rise settles. */
    @OptIn(ExperimentalMaterial3Api::class)
    private fun startRising() {
        value = null
        compose.setContent { ViolinTheme { Sheet() } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        value = FIRST
        frames(RISING)
        compose.onNodeWithText(FIRST).assertExists()
        assertFalse("the sheet still rises", state.isVisible)
    }

    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    private fun top(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top

    @Test
    fun aDroppedSheetSlidesAwayBeforeItGoes() {
        show()
        compose.mainClock.autoAdvance = false
        value = null
        frames(SOME)
        compose.onNodeWithText(FIRST).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(FIRST).assertDoesNotExist()
    }

    @Test
    fun aSheetReplacedByAnotherGoesAtOnce() {
        show()
        slideAway = false
        compose.mainClock.autoAdvance = false
        value = null
        frames(1)
        compose.onNodeWithText(FIRST).assertDoesNotExist()
    }

    @Test
    fun aValueBackWhileTheSheetSlidesBringsItUp() {
        show()
        val expanded = top(FIRST)
        compose.mainClock.autoAdvance = false
        value = null
        frames(SOME)
        value = SECOND
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(SECOND).assertIsDisplayed()
        assertTrue("up again: ${top(SECOND)} where it was at $expanded", top(SECOND) <= expanded + 1.dp)
    }

    /**
     * The hold itself: a value replaced by another one starts no slide — only a value that goes does. Faces of other kinds and heights
     * in one frame, «Занятие не закончено» → «Закончить занятие», are watched frame by frame on `AppSheet` itself (`AppSheetTest`,
     * `ForgottenPracticeSheetTest`).
     */
    @Test
    fun aValueReplacedByAnotherKeepsTheSheetWhereItIs() {
        show()
        val expanded = top(FIRST)
        compose.mainClock.autoAdvance = false
        value = SECOND
        var lowest = expanded
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            if (compose.onAllNodesWithText(SECOND).fetchSemanticsNodes().isNotEmpty()) lowest = maxOf(lowest, top(SECOND))
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(SECOND).assertIsDisplayed()
        assertTrue("in place: at most $lowest where it stood at $expanded", lowest <= expanded + 1.dp)
    }

    @Test
    fun aSheetDroppedWhileItRisesSlidesAway() {
        startRising()
        value = null
        frames(SOME)
        compose.onNodeWithText(FIRST).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertOpensFromBelow()
    }

    @Test
    fun aSwipedSheetGoesWithNothingLeftBehind() {
        show()
        compose.onNodeWithText(FIRST).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
        compose.onNodeWithText(FIRST).assertDoesNotExist()
    }

    @Test
    fun theNextOpeningAfterASlideComesFromBelow() {
        show()
        value = null
        compose.waitForIdle()
        assertOpensFromBelow()
    }

    @Test
    fun theNextOpeningAfterACutComesFromBelow() {
        show()
        slideAway = false
        value = null
        compose.waitForIdle()
        slideAway = true
        assertOpensFromBelow()
    }

    @Test
    fun theNextOpeningAfterACutWhileRisingComesFromBelow() {
        startRising()
        slideAway = false
        value = null
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        slideAway = true
        assertOpensFromBelow()
    }

    private fun assertOpensFromBelow() {
        compose.onNodeWithText(FIRST).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        value = SECOND
        var early: Dp? = null
        repeat(FRAMES) {
            if (early == null && compose.onAllNodesWithText(SECOND).fetchSemanticsNodes().isNotEmpty()) early = top(SECOND)
            if (early == null) compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val first = requireNotNull(early) { "the sheet never showed" }
        assertTrue("the sheet comes from below: $first, then ${top(SECOND)}", first > top(SECOND) + RISE.dp)
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val SHEET = 300
        const val SOME = 3
        const val RISING = 15
        const val FRAMES = 30
        const val RISE = 40
        const val SWIPE = 700
        const val SWIPE_MS = 150L
    }
}
