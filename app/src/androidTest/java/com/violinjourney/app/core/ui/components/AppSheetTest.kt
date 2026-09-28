package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
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
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The frame of a sheet (spec 3.36.1, 3.36.3): a swipe only hides it — [AppSheet] says so to its owner once, and none of its buttons is
 * pressed by it; a sheet that may not be closed stays under a swipe, and goes — sliding, not stuck as an invisible window — when
 * its owner lets it go. One frame, many faces: a value given after a swipe brings the frame up again, «назад» of a face with a way
 * back puts its parent in place, and the buttons of a face stay at the bottom while what is above them scrolls.
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

    @Test
    fun aValueGivenAfterASwipeBringsTheSheetUpAgain() {
        compose.setContent {
            ViolinTheme {
                AppSheet(
                    value = value,
                    onHide = {
                        hides++
                        // the owner puts back the parent of the face swiped away, as «Время за день» gives the sheet of the day back
                        value = PARENT
                    },
                ) { shown -> Box(Modifier.fillMaxWidth().height(SHEET.dp)) { Text(shown) } }
            }
        }
        compose.waitForIdle()
        value = SHOWN
        compose.waitForIdle()
        swipeDown()
        compose.onNodeWithText(PARENT).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, hides) }
    }

    @Test
    fun backOnASheetWithAWayBackGoesThereAndTheSheetStays() {
        var backs = 0
        compose.setContent {
            ViolinTheme {
                AppSheet(
                    value = value,
                    onHide = {
                        hides++
                        value = null
                    },
                    onBack = if (value == SHOWN) ({ backs++; value = PARENT }) else null,
                ) { shown -> Box(Modifier.fillMaxWidth().height(SHEET.dp)) { Text(shown) } }
            }
        }
        compose.waitForIdle()
        value = SHOWN
        compose.waitForIdle()
        val top = compose.onNodeWithText(SHOWN).getUnclippedBoundsInRoot().top
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(PARENT).assertIsDisplayed()
        // where it stood once it has settled; that it never went down on the way is what the tests of a face in place below watch
        assertEquals("back where it stood", top.value, compose.onNodeWithText(PARENT).getUnclippedBoundsInRoot().top.value, 1f)
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals("«назад» of the face is not a hide", 0, hides)
        }
        // the parent has no way back: «назад» now hides the frame
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(PARENT).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, hides) }
    }

    /** A face of the frame in these tests: two kinds, as «Занятие не закончено» and «Закончить занятие», or the day and its time. */
    private sealed interface Face {
        val title: String
        val height: Int
    }

    private data class Asked(override val title: String, override val height: Int) : Face

    /** The face with buttons pinned to the bottom of the sheet. */
    private data class Answered(override val title: String, override val height: Int) : Face

    /**
     * One frame, many faces (spec 3.36.3): a value of another kind and another height takes the place of the one shown in the same
     * frame. Frame by frame, the top of the sheet — its title, first in both faces — never goes below the lower of the two places the
     * faces settle at: a face that slid down and rose again, or a sheet made anew for the new face that rose from the bottom, would.
     * Nothing hid it on the way.
     */
    private fun aFaceTakesThePlaceOf(from: Face, to: Face) {
        var face by mutableStateOf<Face?>(null)
        compose.setContent {
            ViolinTheme {
                AppSheet(
                    value = face,
                    onHide = {
                        hides++
                        face = null
                    },
                    bottom = { shown -> if (shown is Answered) ({ AppSheetButtons(main = SAVE, onMain = { mains++ }, quiet = DISCARD) }) else null },
                ) { shown ->
                    Text(shown.title)
                    Box(Modifier.fillMaxWidth().height(shown.height.dp))
                }
            }
        }
        compose.waitForIdle()
        face = from
        compose.waitForIdle()
        val stood = top(from.title)
        compose.mainClock.autoAdvance = false
        face = to
        var lowest = stood
        repeat(FACE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            listOf(from.title, to.title).forEach { title ->
                if (compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()) lowest = maxOf(lowest, top(title))
            }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(to.title).assertIsDisplayed()
        val settled = top(to.title)
        // the frame took the height of the new face: it moved by the difference at least — or this test would prove nothing
        assertTrue("from $stood to $settled", abs(settled.value - stood.value) >= abs(to.height - from.height) - 1)
        assertTrue("in place: at most $lowest, the faces stand at $stood and $settled", lowest <= maxOf(stood, settled) + 1.dp)
        compose.runOnIdle { assertEquals("nothing hid it", 0, hides) }
    }

    private fun top(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top

    @Test
    fun aTallerFaceWithButtonsTakesThePlaceOfAShorterOneInPlace() =
        aFaceTakesThePlaceOf(from = Asked(SHOWN, SHORT_FACE), to = Answered(PARENT, TALL_FACE))

    @Test
    fun aShorterFaceTakesThePlaceOfATallerOneWithButtonsInPlace() =
        aFaceTakesThePlaceOf(from = Answered(SHOWN, TALL_FACE), to = Asked(PARENT, SHORT_FACE))

    @Test
    fun theButtonsStayAtTheBottomWhileTheContentScrolls() {
        compose.setContent {
            ViolinTheme {
                AppSheet(
                    value = value,
                    onHide = { value = null },
                    bottom = { { AppSheetButtons(main = SAVE, onMain = { mains++ }, quiet = DISCARD) } },
                ) { shown ->
                    Column {
                        Text(shown)
                        // far taller than any window: the content scrolls
                        Box(Modifier.fillMaxWidth().height(TALL.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        value = SHOWN
        compose.waitForIdle()
        // seen — not 3000 dp down the scroll with the end of the content, where the scroll would clip them out of sight
        compose.onNodeWithText(SAVE).assertIsDisplayed()
        compose.onNodeWithText(DISCARD).assertIsDisplayed()
        val save = compose.onNodeWithText(SAVE).getUnclippedBoundsInRoot()
        val shown = compose.onNodeWithText(SHOWN).getUnclippedBoundsInRoot()
        assertEquals("the buttons stand below the content that scrolls", true, save.top > shown.bottom)
    }

    private companion object {
        const val PARENT = "Лист дня"
        const val TALL = 3_000
        const val SHOWN = "Закончить занятие"
        const val SAVE = "Сохранить"
        const val DISCARD = "Не сохранять"
        const val SHEET = 200
        const val SWIPE = 700
        const val SWIPE_MS = 150L

        /** Frames into the slide of 250 ms or so: the sheet is still on its way down. */
        const val SOME_FRAMES = 3

        /** The faces of different heights, and frames enough for a slide down and up (≈ 0.6 s) to show. */
        const val SHORT_FACE = 200
        const val TALL_FACE = 400
        const val FACE_FRAMES = 40
    }
}
