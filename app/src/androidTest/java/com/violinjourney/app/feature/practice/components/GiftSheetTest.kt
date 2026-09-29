package com.violinjourney.app.feature.practice.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.feature.practice.PracticeState
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.gift_next_description
import com.violinjourney.app.shared.resources.gift_thanks
import com.violinjourney.app.shared.resources.progress_trophy_names
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Подарок» as a face of the one frame of «Занятия» (spec 3.13, 3.36.3), on the state the reducer makes of the table of trophies: an
 * empty day set to 12 h gives the trophies of 1 h and 10 h at once, and the card «Дальше» says what is left of the real 12 h to the
 * next mark — «Струна · 50 ч · ещё 38 ч». The first gift slides up; the next one comes into the same frame — in place after «Спасибо»,
 * rising again after a swipe or once the frame that went down under it is down, never taken as seen unseen, never an unseen window
 * that takes the next touch; while «Занятие не закончено» is shown the gift waits under it.
 *
 * The words are read where the sheet reads them, in the composition: a context read when the test is made may still speak the
 * language of the device, while the activity of the test — and with it the sheet — speaks the one chosen for the app in the system.
 */
@RunWith(AndroidJUnit4::class)
class GiftSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 9, 27)
    private val intents = mutableListOf<PracticeIntent>()

    /** The table of trophies: 1 h and 10 h given, neither seen yet. */
    private var trophies by mutableStateOf(listOf(Trophy(1, today, shown = false), Trophy(10, today, shown = false)))

    /** The view model marks a trophy seen when its gift is answered; off — the test writes the table itself. */
    private var answers = true
    private var promptShown by mutableStateOf(false)

    /** «Спасибо», the names of the trophies and what the card «Дальше» says, in the language of the sheet. */
    private var thanks = ""
    private var names: List<String> = emptyList()
    private var next = ""

    private fun state(): PracticeState = PracticeReducer.stateOf(
        entries = listOf(PracticeEntry(today, startedAtEpochMs = 0, durationMs = 12 * MS_PER_HOUR, manual = true)),
        sessions = emptyList(),
        runningSince = null,
        month = YearMonth(2026, 9),
        selectedDate = null,
        sheet = null,
        today = today,
        zone = TimeZone.UTC,
        config = PracticeConfig(),
        trophies = trophies,
        profile = Profile.EMPTY,
        avatarPath = null,
        progressConfig = ProgressConfig(),
    )

    private fun seen(hours: Int) {
        trophies = trophies.map { if (it.hours == hours) it.copy(shown = true) else it }
    }

    private fun show() {
        compose.setContent {
            thanks = stringResource(Res.string.gift_thanks)
            names = stringArrayResource(Res.array.progress_trophy_names)
            // «Дальше — Струна, 50 ч, ещё 38 ч»: 12 h played of the 50 of the next mark
            next = stringResource(Res.string.gift_next_description, names[2], Formats.hoursMark(50), Formats.remainingTime(38 * MS_PER_HOUR))
            ViolinTheme {
                CompositionLocalProvider(LocalAppPromptShown provides promptShown) {
                    PracticeSheetHost(
                        state = state(),
                        onIntent = { intent ->
                            intents += intent
                            if (answers && intent is PracticeIntent.GiftAccepted) seen(intent.hours)
                        },
                        zone = TimeZone.UTC,
                        photo = null,
                    )
                }
            }
        }
    }

    private fun top(): Dp = compose.onNodeWithText(thanks).getUnclippedBoundsInRoot().top

    /** The gift of [index] — its name is in the one paragraph the sheet is for TalkBack — and its card «Дальше», 38 h to «Струна». */
    private fun assertGiftOf(index: Int) {
        compose.onNodeWithContentDescription(names[index], substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(next, substring = true).assertIsDisplayed()
    }

    @Test
    fun theFirstGiftSlidesUp() {
        // the clock of the animations stands; the window of the sheet is laid out all the same
        compose.mainClock.autoAdvance = false
        show()
        var early: Dp? = null
        repeat(FRAMES) {
            if (early == null && compose.onAllNodesWithText(thanks).fetchSemanticsNodes().isNotEmpty()) early = top()
            if (early == null) compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settled = top()
        val first = requireNotNull(early) { "the sheet never showed" }
        assertTrue("the sheet comes from below: $first, then $settled", first > settled + RISE.dp)
        assertGiftOf(0)
    }

    @Test
    fun theGiftAfterASwipedOneComesUp() {
        show()
        compose.waitForIdle()
        compose.onNodeWithText(thanks).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
        // a swipe is «Спасибо» (spec 3.13), once; the next trophy comes up in the frame the swipe took down
        compose.onNodeWithText(thanks).assertIsDisplayed()
        assertGiftOf(1)
        compose.runOnIdle { assertEquals(listOf<PracticeIntent>(PracticeIntent.GiftAccepted(1)), intents) }

        compose.onNodeWithText(thanks).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(thanks).assertCountEquals(0)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    /**
     * «Спасибо» of the first gift: the table marks it seen and the next gift is the face — the same frame shows it in place, as the
     * faces of 3.36.3 change. Frame by frame «Спасибо» — at the bottom of the frame — never goes below where the two gifts stand: a
     * frame that slid down and rose again, or a window made anew for the next gift and rising from the bottom, would take it there.
     */
    @Test
    fun thanksSwapsTheNextGiftInPlace() {
        answers = false
        show()
        compose.waitForIdle()
        compose.onNodeWithText(thanks).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf<PracticeIntent>(PracticeIntent.GiftAccepted(1)), intents) }
        val stood = top()
        compose.mainClock.autoAdvance = false
        // the view model's answer: the trophy seen, written once
        seen(1)
        var lowest = stood
        repeat(FACE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            if (compose.onAllNodesWithText(thanks).fetchSemanticsNodes().isNotEmpty()) lowest = maxOf(lowest, top())
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertGiftOf(1)
        val settled = top()
        assertTrue("in place: at most $lowest, the gifts stand at $stood and $settled", lowest <= maxOf(stood, settled) + 1.dp)
        compose.runOnIdle { assertEquals("nothing hid it: no second answer", 1, intents.size) }
    }

    /**
     * «Спасибо» and at once «назад», the table's answer coming while the frame goes down (5.29 R3): the hide means the gift the frame
     * showed when it began to go — «Канифоль» once more, which changes nothing — and «Колок», come on the way down, rises once the frame
     * is down: it is not taken as seen before it was seen.
     */
    @Test
    fun theNextGiftThatComesWhileTheFrameGoesDownIsNotTakenUnseen() {
        answers = false
        show()
        compose.waitForIdle()
        compose.onNodeWithText(thanks).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        Espresso.pressBack()
        // the table marks «Канифоль» seen while the frame slides down: «Колок» is the face from the next frame on
        seen(1)
        compose.waitForIdle()
        repeat(SOME_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithContentDescription(names[1], substring = true).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("«Колок» is not answered: $intents", listOf(PracticeIntent.GiftAccepted(1), PracticeIntent.GiftAccepted(1)), intents)
        }
        compose.onNodeWithText(thanks).assertIsDisplayed()
        assertGiftOf(1)
    }

    /** «Занятие не закончено» over the screen (spec 3.36.3): the gift is not shown under it, and rises once it has gone. */
    @Test
    fun aGiftWaitsWhileThePromptOfTheAppIsShown() {
        promptShown = true
        show()
        compose.waitForIdle()
        compose.onAllNodesWithText(thanks).assertCountEquals(0)
        promptShown = false
        compose.waitForIdle()
        compose.onNodeWithText(thanks).assertIsDisplayed()
        assertGiftOf(0)
        compose.runOnIdle { assertTrue("nothing was answered while it waited", intents.isEmpty()) }
    }

    private companion object {
        const val RISE = 40
        const val FRAMES = 30
        const val SWIPE = 700
        const val SWIPE_MS = 150L

        /** Frames enough for a slide down and up (≈ 0.6 s) to show. */
        const val FACE_FRAMES = 40

        /** Frames into a slide down of 150–250 ms: the frame is still on its way. */
        const val SOME_FRAMES = 3
    }
}
