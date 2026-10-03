package com.violinjourney.app.feature.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.form.EventFormIntent
import com.violinjourney.app.feature.events.form.EventFormReducer
import com.violinjourney.app.feature.events.form.EventFormSheetCard
import com.violinjourney.app.feature.events.form.FormDraft
import com.violinjourney.app.feature.events.form.FormSheet
import com.violinjourney.app.feature.events.form.TimeWheel
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_wheel_hours_few
import com.violinjourney.app.shared.resources.event_wheel_hours_many
import com.violinjourney.app.shared.resources.event_wheel_hours_one
import com.violinjourney.app.shared.resources.event_wheel_minutes_few
import com.violinjourney.app.shared.resources.event_wheel_minutes_many
import com.violinjourney.app.shared.resources.event_wheel_minutes_one
import com.violinjourney.app.testing.TestWindow
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The wheels of «Время» (spec 3.36.9, 5.29 R9; plan D26): a slow swipe of two rows lets the wheel go and it snaps to a row — the value two
 * on, told once, when the wheel has stopped, and not on the way (the value at the end of the drag is the row the snap goes to, so a wheel
 * that told every row it passed would be caught); a value given from outside (a chip of «Частое») turns the wheel to it, and a swipe goes
 * on from there; for TalkBack a wheel is one node — its value in words, said once as its state, within its range, and the actions
 * «Больше» and «Меньше»; the minutes of the sheet step by 5 — twelve values. Laid out in a window of its own size ([TestWindow]).
 */
@RunWith(AndroidJUnit4::class)
class TimeWheelTest {
    @get:Rule
    val compose = createComposeRule()

    private val settled = mutableListOf<Int>()
    private var selected by mutableIntStateOf(START)

    private fun show() {
        compose.setContent {
            ViolinTheme {
                TestWindow(DpSize(412.dp, 892.dp)) {
                    TimeWheel(
                        labels = HOURS.map { it.toString().padStart(2, '0') },
                        selected = selected,
                        said = { "$it $UNIT" },
                        up = UP,
                        down = DOWN,
                        onSettle = { value ->
                            settled += value
                            // as the form does: the sheet takes the value and gives it back
                            selected = value
                        },
                        modifier = Modifier.testTag(WHEEL),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** A slow drag of [rows] rows up ([SLOP] aside), then let go: too slow to fling, the wheel snaps to the nearest row. */
    private fun SemanticsNodeInteraction.dragRows(rows: Int) = performTouchInput {
        val x = centerX
        val start = height - ROW.toPx() / 2
        swipe(Offset(x, start), Offset(x, start - rows * ROW.toPx() - SLOP.toPx()), durationMillis = SLOW_MS)
    }

    @Test
    fun aSlowSwipeOfTwoRowsSettlesTwoValuesOn() {
        show()
        compose.onNodeWithTag(WHEEL).dragRows(2)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("told once, when it stopped — not each row on the way", listOf(START + 2), settled) }
        compose.onNodeWithTag(WHEEL).assert(hasStateDescription("${START + 2} $UNIT"))
    }

    /** «Частое» gives the wheel 3 from 17: the wheel turns to it the nearest way — a swipe of a row then settles at 4, not at 18. */
    @Test
    fun aValueGivenFromOutsideTurnsTheWheelToIt() {
        show()
        compose.runOnIdle { selected = 3 }
        compose.waitForIdle()
        compose.onNodeWithTag(WHEEL).dragRows(1)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("on from where it was turned to, told once: the turn itself tells nothing", listOf(4), settled) }
    }

    @Test
    fun forTalkBackAWheelIsOneValueInItsRangeWithTwoActions() {
        show()
        val wheel = compose.onNodeWithTag(WHEEL)
        val node = wheel.fetchSemanticsNode()
        val range: ProgressBarRangeInfo = node.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals("its value", START.toFloat(), range.current, 0f)
        assertEquals("from 0", 0f, range.range.start, 0f)
        assertEquals("to 23", 23f, range.range.endInclusive, 0f)
        assertEquals("said in words, as its state", "$START $UNIT", node.config[SemanticsProperties.StateDescription])
        assertFalse("said once: no name repeating the value", SemanticsProperties.ContentDescription in node.config)
        val actions = node.config[SemanticsActions.CustomActions]
        assertEquals(listOf(UP, DOWN), actions.map { it.label })
        compose.runOnIdle { actions.first { it.label == UP }.action() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("«$UP»", START + 1, settled.last()) }
        compose.runOnIdle { actions.first { it.label == DOWN }.action() }
        compose.runOnIdle { assertEquals("«$DOWN» from the value it was told", START, settled.last()) }
    }

    /**
     * The sheet «Время» at 17:30: the hours say «17 часов» within 0–23, the minutes «30 минут» within twelve values — the step of a start,
     * 5 minutes (spec 5.28) — and a swipe of a row on the minutes tells the form 17:35's minute, 35.
     */
    @Test
    fun theMinutesOfTheSheetStepByFive() {
        val config = EventsConfig()
        val kinds = KindRules.all(emptyList(), config)
        val draft = FormDraft(KindRef.BuiltIn(BuiltInKind.LESSON), LocalDate(2026, 9, 28), 17 * 60 + 30, 45, Repeat.NONE, null, "", "", "")
        val state = EventFormReducer.stateOf(
            draft = draft, sheet = FormSheet.Time(17 * 60 + 30, allDay = false), dialog = null, loading = false, isNew = true, edited = null,
            editedSeries = null, initialDate = draft.date, events = emptyList(), kinds = kinds, today = LocalDate(2026, 9, 27), focusNotes = false,
            config = config,
        )
        val intents = mutableListOf<EventFormIntent>()
        var hours = ""
        var minutes = ""
        compose.setContent {
            hours = stringResource(Formats.plural(17, Res.string.event_wheel_hours_one, Res.string.event_wheel_hours_few, Res.string.event_wheel_hours_many), 17)
            minutes = stringResource(Formats.plural(30, Res.string.event_wheel_minutes_one, Res.string.event_wheel_minutes_few, Res.string.event_wheel_minutes_many), 30)
            ViolinTheme { TestWindow(DpSize(412.dp, 892.dp)) { EventFormSheetCard(state, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
        val hourRange = compose.onNode(hasStateDescription(hours)).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals("24 hours", 23f, hourRange.range.endInclusive, 0f)
        val minuteWheel = compose.onNode(hasStateDescription(minutes))
        val minuteRange = minuteWheel.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals("twelve minutes, 5 apart", 11f, minuteRange.range.endInclusive, 0f)
        assertEquals("30 is the seventh", 6f, minuteRange.current, 0f)
        minuteWheel.dragRows(1)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(EventFormIntent.TimeMinute(35), intents.filterIsInstance<EventFormIntent.TimeMinute>().last()) }
    }

    /** The node whose state TalkBack says is [value]. */
    private fun hasStateDescription(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private companion object {
        const val WHEEL = "wheel"
        const val START = 17
        const val UNIT = "ч"
        const val UP = "Больше"
        const val DOWN = "Меньше"
        val HOURS = List(24) { it }
        val ROW = 44.dp

        /**
         * Half the touch slop of a phone (8 dp) over the rows: the content moves by what is dragged past the slop, so it stops 4 short of
         * the row — or 4 past it, were there no slop — nearer to that row than to its neighbours either way.
         */
        val SLOP = 4.dp

        /** Slow enough not to fling: some 60 dp a second. */
        const val SLOW_MS = 1_500L
    }
}
