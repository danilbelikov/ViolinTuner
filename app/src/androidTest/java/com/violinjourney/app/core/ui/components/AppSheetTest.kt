package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The frame of a sheet (spec 3.36.1): a swipe only hides it — [AppSheet] says so to its owner once, and none of its buttons is
 * pressed by it; a sheet that may not be closed stays under a swipe, and goes — sliding, not stuck as an invisible window — when
 * its owner lets it go.
 */
@RunWith(AndroidJUnit4::class)
class AppSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var value by mutableStateOf<String?>(null)
    private var hides = 0
    private var mains = 0
    private var quiets = 0

    private fun show(dismissible: Boolean) {
        compose.setContent {
            ViolinTheme {
                AppSheet(
                    value = value,
                    onHide = {
                        hides++
                        value = null
                    },
                    dismissible = dismissible,
                ) { shown ->
                    Box(Modifier.fillMaxWidth().height(SHEET.dp)) { Text(shown) }
                    AppSheetButtons(main = SAVE, onMain = { mains++ }, quiet = DISCARD, onQuiet = { quiets++ })
                }
            }
        }
        compose.waitForIdle()
        value = SHOWN
        compose.waitForIdle()
        compose.onNodeWithText(SHOWN).assertIsDisplayed()
    }

    private fun swipeDown() {
        compose.onNodeWithText(SHOWN).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
    }

    @Test
    fun aSwipeOnlyHidesTheSheet() {
        show(dismissible = true)
        swipeDown()
        compose.onNodeWithText(SHOWN).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("told once", 1, hides)
            assertEquals("nothing saved", 0, mains)
            assertEquals("nothing refused", 0, quiets)
        }
    }

    @Test
    fun aSheetLetGoByItsOwnerGoes() {
        show(dismissible = true)
        value = null
        compose.waitForIdle()
        compose.onNodeWithText(SHOWN).assertDoesNotExist()
        compose.runOnIdle { assertEquals("the owner closed it, not a swipe", 0, hides) }
    }

    @Test
    fun aSheetThatMayNotBeClosedStaysUnderASwipe() {
        show(dismissible = false)
        swipeDown()
        compose.onNodeWithText(SHOWN).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, hides) }
    }

    /**
     * The sheet that may not be closed refuses to hide only while its owner holds it: `SheetState.hide()` asks the same question,
     * and a sheet refused there would vanish at once — no slide, the scrim gone in a frame. A few frames after the owner lets it go
     * it is still there, sliding; then it is gone.
     */
    @Test
    fun aSheetThatMayNotBeClosedGoesWhenItsOwnerLetsItGo() {
        show(dismissible = false)
        compose.mainClock.autoAdvance = false
        value = null
        repeat(SOME_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText(SHOWN).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(SHOWN).assertDoesNotExist()
        compose.onNodeWithText(SAVE).assertDoesNotExist()
        compose.runOnIdle { assertEquals("the owner let it go, nobody hid it", 0, hides) }
    }

    private companion object {
        const val SHOWN = "Закончить занятие"
        const val SAVE = "Сохранить"
        const val DISCARD = "Не сохранять"
        const val SHEET = 200
        const val SWIPE = 700
        const val SWIPE_MS = 150L

        /** Frames into the slide of 250 ms or so: the sheet is still on its way down. */
        const val SOME_FRAMES = 3
    }
}
