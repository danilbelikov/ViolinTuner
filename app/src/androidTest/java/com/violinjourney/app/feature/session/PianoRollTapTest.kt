package com.violinjourney.app.feature.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.StringFinger
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.components.PianoRoll
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tap on the roll opens the note under the finger after the roll changed width — a rotation on iOS, where the
 * composition lives on: the scroll is remembered per geometry, and the tap has to read the scroll of the geometry shown.
 * A note picked far from the cursor of a sound that plays stays in view: the roll does not follow the cursor while it is picked.
 * Where a note stands is asked by a tap on it: the tap opens it only where the roll shows it.
 */
@RunWith(AndroidJUnit4::class)
class PianoRollTapTest {
    @get:Rule
    val compose = createComposeRule()

    private fun segment(midi: Int, startMs: Long, endMs: Long) = RollSegment(
        note = Note(midi), startMs = startMs, endMs = endMs, meanCents = 0.0, minCents = 0.0, maxCents = 0.0,
        zone = Zone.IN_TUNE, steady = true, contour = emptyList(), position = StringFinger.of(midi),
    )

    // A minute: A4 at the start, B4 at the fifty-fifth second — far beyond the first screen of the roll.
    private val content = SessionContent(
        title = null, startedAtEpochMs = 0, durationMs = 60_000, toleranceCents = 8.0, scorePercent = 100, nearPercent = 0,
        offPercent = 0, maeCents = 0.0, biasCents = 0.0, biasZone = null, perString = emptyMap(), problemNotes = emptyList(),
        rollNotes = listOf(Note(71), Note(69)), segments = listOf(segment(69, 0, 1_000), segment(71, 55_000, 56_000)), hasAudio = false,
    )

    @Test
    fun aTapAfterTheRollChangedWidthOpensTheNoteUnderTheFinger() {
        // both narrower than any phone, so that the roll really is as wide as asked
        var width by mutableStateOf(NARROW.dp)
        val clicked = mutableListOf<Int>()
        compose.setContent {
            ViolinTheme {
                Box(Modifier.width(width)) {
                    PianoRoll(content = content, selectedSegment = null, onSegmentClick = { clicked += it }, modifier = Modifier.testTag(ROLL))
                }
            }
        }
        // a first touch: the tap detector starts here, with the scroll of this width
        val narrow = PianoRollMath(content.durationMs, content.rollNotes.size, viewportWidth = NARROW - PADDING * 2 - GUTTER)
        compose.onNodeWithTag(ROLL).performTouchInput {
            click(Offset((PADDING + GUTTER + narrow.x(500)).dp.toPx(), (PADDING + TICKS + narrow.barTop(1) + PianoRollMath.BAR_HEIGHT / 2).dp.toPx()))
        }
        compose.waitForIdle()
        assertEquals(listOf(0), clicked)

        compose.runOnIdle { width = WIDE.dp }
        compose.waitForIdle()

        // to the end of the minute: 1 800 dp of time in a view of 300, scrolled 1 500 dp
        repeat(SWIPES) {
            compose.onNodeWithTag(ROLL).performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
        val roll = PianoRollMath(content.durationMs, content.rollNotes.size, viewportWidth = WIDE - PADDING * 2 - GUTTER)
        // the middle of B4's bar, where the end of the roll shows it: card padding, the names of the notes, the time line
        val x = PADDING + GUTTER + roll.x(55_500) - roll.maxScroll
        val y = PADDING + TICKS + roll.barTop(0) + PianoRollMath.BAR_HEIGHT / 2
        compose.onNodeWithTag(ROLL).performTouchInput { click(Offset(x.dp.toPx(), y.dp.toPx())) }
        compose.waitForIdle()

        assertEquals(listOf(0, 1), clicked)
    }

    /**
     * Spec 3.36.5: a row of «Что уходит» brings the roll to where its note drifts the most and outlines it, «как при касании ленты» —
     * while the sound plays too. B4, fifty-five seconds in, is picked while the cursor plays at the start: the next chunks of sound do
     * not take the roll back to the cursor under the open sheet; once the note is let go, the roll follows the cursor again.
     */
    @Test
    fun aNotePickedFarFromThePlayingCursorStaysInViewUntilItIsLetGo() {
        var cursor by mutableLongStateOf(0L)
        var picked by mutableStateOf<Int?>(null)
        val clicked = mutableListOf<Int>()
        compose.setContent {
            ViolinTheme {
                Box(Modifier.width(WIDE.dp)) {
                    PianoRoll(
                        content = content,
                        selectedSegment = picked,
                        onSegmentClick = { clicked += it },
                        modifier = Modifier.testTag(ROLL),
                        cursorMs = { cursor },
                        followCursor = true,
                    )
                }
            }
        }
        compose.waitForIdle()
        val roll = PianoRollMath(content.durationMs, content.rollNotes.size, viewportWidth = WIDE - PADDING * 2 - GUTTER)
        compose.runOnIdle { picked = 1 }
        compose.waitForIdle()
        // the chunks of sound that come while its sheet is open, at the start of the roll — far out of the view of B4
        listOf(1_000L, 1_050L, 1_100L).forEach { at ->
            compose.runOnIdle { cursor = at }
            compose.waitForIdle()
        }
        // B4 where the roll brought it: its start at a fifth of the view, as far as the roll goes
        val shown = checkNotNull(roll.scrollToShow(55_000, 56_000, 0f))
        val y = PADDING + TICKS + roll.barTop(0) + PianoRollMath.BAR_HEIGHT / 2
        compose.onNodeWithTag(ROLL).performTouchInput { click(Offset((PADDING + GUTTER + roll.x(55_500) - shown).dp.toPx(), y.dp.toPx())) }
        compose.waitForIdle()
        assertEquals("B4 is in view under its sheet while the sound plays at the start", listOf(1), clicked)

        // let go: the roll follows the cursor again — back at the start, where A4 is
        compose.runOnIdle { picked = null }
        compose.waitForIdle()
        val following = checkNotNull(roll.scrollToFollow(1_100, shown))
        val a4 = PADDING + TICKS + roll.barTop(1) + PianoRollMath.BAR_HEIGHT / 2
        compose.onNodeWithTag(ROLL).performTouchInput { click(Offset((PADDING + GUTTER + roll.x(500) - following).dp.toPx(), a4.dp.toPx())) }
        compose.waitForIdle()
        assertEquals("A4 is in view: the roll is with the cursor again", listOf(1, 0), clicked)
    }

    private companion object {
        const val ROLL = "roll"
        const val SWIPES = 6
        const val NARROW = 260f
        const val WIDE = 360f

        /** The roll's card padding, the gutter of the note names and the height of the time line, in dp (PianoRoll.kt). */
        const val PADDING = 12f
        const val GUTTER = 36f
        const val TICKS = 22f
    }
}
