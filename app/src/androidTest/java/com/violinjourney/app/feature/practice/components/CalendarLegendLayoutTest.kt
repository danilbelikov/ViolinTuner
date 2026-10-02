package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.domain.practice.PracticeStats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_events_hint
import com.violinjourney.app.testing.TestWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The legend of the kinds and the hint lying (spec 3.36.9, 5.29 R9 «Легенда и подсказка»; the review of stage 97): under the grid —
 * where the column beside the grid is too narrow for them — they are as wide as the grid of 350, as in portrait, not as the column; and
 * a month is as wide as its column whatever stands under or beside its grid, so the change of the month slides it without a curtain: a
 * legend that comes with the month is not unveiled by a width growing from the grid's to the column's. Laid out in a window of its own
 * size ([TestWindow]) at 1.5 pixels a dp, so the windows lying fit the screen of the emulator upright.
 */
@RunWith(AndroidJUnit4::class)
class CalendarLegendLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val config = EventsConfig()

    /** The kinds of the mockups: the built-in four, «Оркестр» and «Мастер-класс» — in the order of the form. */
    private val kinds: List<EventKind> = KindRules.ordered(
        KindRules.all(listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1), StoredKind.Own(10, "Мастер-класс", 4, KindSign.BOLT, 2)), config),
    )

    private fun cells(month: YearMonth): List<CalendarCell?> = PracticeStats.calendarCells(month).map { date ->
        date?.let { CalendarCell(it, totalMs = 0, fillLevel = 0, isToday = false, isSelected = false, isFuture = it > TODAY) }
    }

    private fun legendNode() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) and hasLegendWords())

    /** The legend is one node whose description lists the kinds of the month (spec 3.36.9). */
    private fun hasLegendWords() = SemanticsMatcher("the legend of the kinds") { node ->
        node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty().any { "Оркестр" in it }
    }

    /**
     * Lying in a right column of 450 — where the legend does not stand beside the grid (484 is the least for it): the hint, and then the
     * legend, under the grid end where the grid ends, 350 from the left of the column, not at the end of the column.
     */
    @Test
    fun lyingUnderTheGridTheHintAndTheLegendAreAsWideAsTheGrid() {
        var hint by mutableStateOf(true)
        var hintWords = ""
        compose.setContent {
            hintWords = stringResource(Res.string.practice_events_hint)
            ViolinTheme {
                TestWindow(DpSize(COLUMN.width + 32.dp, 412.dp), density = DENSITY) {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        PracticeCalendar(
                            OCTOBER, cells(OCTOBER), canGoForward = true, onMonthBack = {}, onMonthForward = {}, onDaySelected = {},
                            currentYear = 2026, monthMs = 0, monthDays = 0, modifier = Modifier.width(COLUMN.width).testTag(CALENDAR),
                            metrics = CalendarMetrics.Landscape, legend = if (hint) emptyList() else kinds, hint = hint,
                            legendBeside = CalendarMetrics.Landscape.legendBeside(COLUMN.width),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val left = compose.onNodeWithTag(CALENDAR).getUnclippedBoundsInRoot().left
        val hintBounds = compose.onNodeWithText(hintWords).getUnclippedBoundsInRoot()
        assertTrue("the hint ends with the grid at 350: ${hintBounds.right - left}", hintBounds.right - left <= GRID + 0.5.dp)
        hint = false
        compose.waitForIdle()
        val legend = legendNode().getUnclippedBoundsInRoot()
        assertTrue("the legend is as wide as the grid of 350: ${legend.width}", legend.right - left <= GRID + 0.5.dp)
    }

    /**
     * 892 × 412 — the right column of 596, the legend beside the grid: from a month without events (nothing beside the grid) the arrow
     * forward goes to one with them. Frame by frame, three frames into the change of the month: the legend comes sliding with its
     * grid, cut only by the slide (40 at most) at the end of the column — not by a month as narrow as its grid and widening on a
     * spring, which shows a strip of it.
     */
    @Test
    fun aMonthIsAsWideAsItsColumnSoItsLegendComesWithItWhole() {
        var month by mutableStateOf(OCTOBER)
        compose.setContent {
            ViolinTheme {
                TestWindow(DpSize(WIDE.width + 16.dp, 412.dp), density = DENSITY) {
                    Box(Modifier.padding(end = 16.dp)) {
                        PracticeCalendar(
                            month, cells(month), canGoForward = true, onMonthBack = {}, onMonthForward = {}, onDaySelected = {},
                            currentYear = 2026, monthMs = 0, monthDays = 0, modifier = Modifier.width(WIDE.width),
                            metrics = CalendarMetrics.Landscape, legend = if (month == OCTOBER) emptyList() else kinds,
                            monthEvents = if (month == OCTOBER) 0 else 17, monthIsFuture = month > OCTOBER,
                            legendBeside = CalendarMetrics.Landscape.legendBeside(WIDE.width),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        month = NOVEMBER
        repeat(CHANGE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val node = legendNode().fetchSemanticsNode()
        val shown = with(Density(DENSITY)) { node.boundsInRoot.width.toDp() }
        val whole = legendNode().getUnclippedBoundsInRoot().width
        compose.mainClock.autoAdvance = true
        assertTrue("three frames in, the legend shows $shown of its $whole: no more than the slide is cut", shown >= whole - SLIDE - 1.dp)
    }

    private companion object {
        const val CALENDAR = "calendar"
        val TODAY = LocalDate(2026, 10, 2)
        val OCTOBER = YearMonth(2026, 10)
        val NOVEMBER = YearMonth(2026, 11)

        /** The grid lying (5.29 R2). */
        val GRID = 350.dp

        /** A right column the legend does not stand beside the grid in (under 484), and the one of 892 × 412, where it does. */
        val COLUMN = DpSize(450.dp, 0.dp)
        val WIDE = DpSize(596.dp, 0.dp)

        /** How far the month slides in (the handoff, «Смена месяца»). */
        val SLIDE = 40.dp
        const val CHANGE_FRAMES = 3

        /** Pixels a dp of the windows here: wider than a phone upright in dp, they still fit its screen in pixels. */
        const val DENSITY = 1.5f
    }
}
