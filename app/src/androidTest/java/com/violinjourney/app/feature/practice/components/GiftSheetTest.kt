package com.violinjourney.app.feature.practice.components

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.Gift
import com.violinjourney.app.feature.practice.PracticeIntent
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two trophies at once (spec 3.13): the first swiped away, the second comes into the same sheet — and has to come up,
 * not wait below the screen as an unseen window that takes the next touch for «Спасибо».
 */
@RunWith(AndroidJUnit4::class)
class GiftSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val thanks = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.gift_thanks)
    private val date = LocalDate(2026, 9, 27)

    private var gift by mutableStateOf<Gift?>(Gift(hours = 1, index = 0, awardedDate = date))

    private fun show() {
        compose.setContent {
            ViolinTheme {
                // as «Занятия» shows it: the table marks the gift seen, the next one takes its place
                gift?.let { shown ->
                    GiftSheet(
                        shown,
                        onIntent = { intent ->
                            if (intent is PracticeIntent.GiftAccepted) gift = if (intent.hours == 1) Gift(hours = 10, index = 1, awardedDate = date) else null
                        },
                    )
                }
            }
        }
    }

    @Test
    fun theFirstGiftSlidesUp() {
        // the clock of the animations stands; the window of the sheet is laid out all the same
        compose.mainClock.autoAdvance = false
        show()
        var early: Dp? = null
        repeat(FRAMES) {
            if (early == null && compose.onAllNodesWithText(thanks).fetchSemanticsNodes().isNotEmpty()) {
                early = compose.onNodeWithText(thanks).getUnclippedBoundsInRoot().top
            }
            if (early == null) compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settled = compose.onNodeWithText(thanks).getUnclippedBoundsInRoot().top
        val first = requireNotNull(early) { "the sheet never showed" }
        assertTrue("the sheet comes from below: $first, then $settled", first > settled + RISE.dp)
    }

    @Test
    fun theGiftAfterASwipedOneComesUp() {
        show()
        compose.waitForIdle()
        compose.onNodeWithText(thanks).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
        compose.onNodeWithText(thanks).assertIsDisplayed()

        compose.onNodeWithText(thanks).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(thanks).assertCountEquals(0)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    private companion object {
        const val RISE = 40
        const val FRAMES = 30
        const val SWIPE = 700
        const val SWIPE_MS = 150L
    }
}
