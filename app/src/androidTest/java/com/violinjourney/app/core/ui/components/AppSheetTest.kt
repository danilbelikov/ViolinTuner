package com.violinjourney.app.core.ui.components

import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    /**
     * «Поделиться» begins closable and holds while its file is made (spec 3.36.5, 5.29 R5): the handle goes, its room stays — the sheet
     * keeps its height, and its top edge does not come down by the 29 dp between the handle (49) and the top of a sheet without one
     * (20) — the content, laid out from the bottom of the window, stands where it stood either way; the sheet does not go under a swipe,
     * and «назад» does nothing — Material's own «назад», set when the window opened closable, would ask the owner to hide it. Let go
     * again, it hides under a swipe as before.
     */
    @Test
    fun aSheetThatBeginsToHoldKeepsTheRoomOfItsHandleAndHearsNoBack() {
        var holds by mutableStateOf(false)
        compose.setContent {
            ViolinTheme {
                AppSheet(
                    value = value,
                    onHide = {
                        hides++
                        value = null
                    },
                    dismissible = !holds,
                    keepHandleRoom = true,
                ) { shown -> Box(Modifier.fillMaxWidth().height(SHEET.dp)) { Text(shown) } }
            }
        }
        compose.waitForIdle()
        value = SHOWN
        compose.waitForIdle()
        val before = sheetTop()

        holds = true
        compose.waitForIdle()
        assertEquals("the sheet keeps its height: its top edge stands where it stood with the handle", before.value, sheetTop().value, 0.5f)
        swipeDown()
        compose.onNodeWithText(SHOWN).assertIsDisplayed()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(SHOWN).assertIsDisplayed()
        compose.runOnIdle { assertEquals("held: nobody was asked to hide it", 0, hides) }
        assertEquals(before.value, sheetTop().value, 0.5f)

        holds = false
        compose.waitForIdle()
        assertEquals(before.value, sheetTop().value, 0.5f)
        swipeDown()
        compose.onNodeWithText(SHOWN).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, hides) }
    }

    /**
     * A sheet whose window opened holding — «Поделиться» made again by a turn of the phone while its file is made — hears no «назад»
     * while it holds; let go («Отмена», a failure), «назад» hides it and tells its owner once, as it does a sheet that opened closable.
     * Material's own «назад» is set off for good when such a window opens, and its window's cancel does nothing: the frame hears it.
     */
    @Test
    fun aSheetThatOpenedHoldingHidesOnBackOnceItIsLetGo() {
        var holds by mutableStateOf(true)
        compose.setContent {
            ViolinTheme {
                AppSheet(
                    value = value,
                    onHide = {
                        hides++
                        value = null
                    },
                    dismissible = !holds,
                    keepHandleRoom = true,
                ) { shown -> Box(Modifier.fillMaxWidth().height(SHEET.dp)) { Text(shown) } }
            }
        }
        compose.waitForIdle()
        value = SHOWN
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(SHOWN).assertIsDisplayed()
        compose.runOnIdle { assertEquals("held: nobody was asked to hide it", 0, hides) }

        holds = false
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(SHOWN).assertDoesNotExist()
        compose.runOnIdle { assertEquals("told once", 1, hides) }
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

    /** The top edge of the sheet: the pane Material gives it. */
    private fun sheetTop(): Dp = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).getUnclippedBoundsInRoot().top

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

    /**
     * «Имя и фото» on its side with the keyboard up (5.29 R3): the buttons of a value that asks for it leave the bottom of the sheet
     * for the end of what scrolls, and the field in focus keeps its focus and stands whole over the keyboard — pinned, the buttons
     * would leave it less room than itself; with the keyboard down they are pinned again. The keyboard is the insets a real one gives
     * the window of the sheet, handed to its view; the field is a focus target, so that no real keyboard comes up and hands its own.
     */
    @Test
    fun overTheKeyboardTheButtonsGiveTheirPlaceToTheFieldInFocus() {
        val focus = FocusRequester()
        lateinit var view: View
        compose.setContent {
            ViolinTheme {
                AppSheet(
                    value = value,
                    onHide = { value = null },
                    bottom = { { AppSheetButtons(main = SAVE, onMain = { mains++ }) } },
                    buttonsInContentOverKeyboard = { true },
                ) { shown ->
                    // the view of the sheet's window: the keyboard is handed to it
                    view = LocalView.current
                    Text(shown)
                    Box(Modifier.fillMaxWidth().height(FIELD.dp).focusRequester(focus).focusable().testTag(FIELD_TAG))
                    // the counter and the line under the field
                    Box(Modifier.fillMaxWidth().height(UNDER_FIELD.dp))
                }
            }
        }
        compose.waitForIdle()
        value = SHOWN
        compose.waitForIdle()
        compose.runOnIdle { focus.requestFocus() }
        compose.onNodeWithTag(FIELD_TAG).assertIsFocused()
        val window = compose.onAllNodes(isRoot()).filter(hasAnyDescendant(hasText(SHOWN))).onFirst().getUnclippedBoundsInRoot()
        // a keyboard that leaves the sheet its handle, the pinned buttons and less than the field over them
        val keyboardDp = window.bottom - window.top - (HANDLE + PINNED + LEFT_OVER).dp
        val keyboard = with(compose.density) { keyboardDp.roundToPx() }
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, keyboardInsets(keyboard)) }
        compose.waitForIdle()

        compose.onNodeWithTag(FIELD_TAG).assertIsFocused()
        val field = compose.onNodeWithTag(FIELD_TAG).getBoundsInRoot()
        assertTrue("the field whole: ${field.bottom - field.top} of $FIELD", field.bottom - field.top >= (FIELD - 1).dp)
        assertTrue("over the keyboard: ${field.bottom}, the keyboard at ${window.bottom - keyboardDp}", field.bottom <= window.bottom - keyboardDp + 1.dp)
        compose.onAllNodesWithText(SAVE).assertCountEquals(1)

        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, keyboardInsets(0)) }
        compose.waitForIdle()
        compose.onNodeWithText(SAVE).assertIsDisplayed()
        compose.onAllNodesWithText(SAVE).assertCountEquals(1)
    }

    /**
     * A sheet never lies under a cutout of the camera or a bar of the system at a side of its window (the review of stage 110: on 640 ×
     * 360 with a cutout of 36.5 the tonics of «Тональность» went under it, and under a bar of three buttons at the right its «Готово»).
     * The window hands the sheet's view a cutout at the left and a bar at the right; the content, 20 in from the sheet's sides, stands
     * clear of both.
     */
    @Test
    fun aSheetKeepsOffTheCutoutAndTheBarAtTheSidesOfItsWindow() {
        lateinit var view: View
        compose.setContent {
            ViolinTheme {
                AppSheet(value = value, onHide = { value = null }) { shown ->
                    view = LocalView.current
                    Box(Modifier.fillMaxWidth().height(SHEET.dp).testTag(CONTENT_TAG)) { Text(shown) }
                }
            }
        }
        compose.waitForIdle()
        value = SHOWN
        compose.waitForIdle()
        val window = compose.onAllNodes(isRoot()).filter(hasAnyDescendant(hasText(SHOWN))).onFirst().getUnclippedBoundsInRoot()
        val cutout = with(compose.density) { CUTOUT.dp.roundToPx() }
        val bar = with(compose.density) { SIDE_BAR.dp.roundToPx() }
        val sides = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutout, 0, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, bar, 0))
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, sides) }
        compose.waitForIdle()

        val content = compose.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
        assertTrue("off the cutout: ${content.left}, the window at ${window.left}", content.left >= window.left + (CUTOUT + CONTENT_SIDE).dp - 1.dp)
        assertTrue("off the bar: ${content.right}, the window at ${window.right}", content.right <= window.right - (SIDE_BAR + CONTENT_SIDE).dp + 1.dp)
        compose.onNodeWithText(SHOWN).assertIsDisplayed()
    }

    /** What the window hands its views: the keyboard alone, [bottom] px from the bottom; none at 0. */
    private fun keyboardInsets(bottom: Int): WindowInsetsCompat = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
        .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
        .build()

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

        /** The field of the name and what stands under it; the handle of the sheet (5 and 22 over and under it). */
        const val FIELD = 56
        const val UNDER_FIELD = 40
        const val FIELD_TAG = "field"
        const val HANDLE = 49

        /** The pinned buttons of one main button: 18 over it, 56, the field of 16 under it — and what a keyboard leaves over them. */
        const val PINNED = 90
        const val LEFT_OVER = 30

        /** A cutout of the camera at the left of the window, a bar of three buttons at its right; the fields of a sheet's content. */
        const val CUTOUT = 40
        const val SIDE_BAR = 48
        const val CONTENT_SIDE = 20
        const val CONTENT_TAG = "content"
    }
}
