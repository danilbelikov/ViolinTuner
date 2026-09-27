package com.violinjourney.app.feature.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
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
