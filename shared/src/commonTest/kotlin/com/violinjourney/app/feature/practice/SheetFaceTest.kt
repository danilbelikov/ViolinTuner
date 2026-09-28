package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth

/**
 * The one frame of the sheets of «Занятия» (spec 3.36.3): which face it shows for the state, what a swipe of each face means, and
 * which faces have a way back to the one under them.
 */
class SheetFaceTest {
    private val config = PracticeConfig()
    private val today = LocalDate(2026, 9, 17)
    private val day = LocalDate(2026, 9, 16)

    private fun state(sheet: PracticeSheet?, sheetsAway: Boolean = false, selected: LocalDate? = null): PracticeState = PracticeReducer.stateOf(
        entries = listOf(PracticeEntry(day, 0, 50 * MS_PER_MINUTE, manual = false)),
        sessions = emptyList(),
        runningSince = null,
        month = YearMonth(2026, 9),
        selectedDate = selected,
        sheet = sheet,
        today = today,
        zone = TimeZone.of("Europe/Moscow"),
        config = config,
        trophies = listOf(Trophy(1, today, shown = true)),
        profile = Profile.EMPTY,
        avatarPath = null,
        progressConfig = ProgressConfig(),
        sheetsAway = sheetsAway,
    )

    private val summary = PracticeReducer.summarySheet(1_000, 47 * MS_PER_MINUTE, config)
    private val edit = PracticeReducer.editSheet(day, 50 * MS_PER_MINUTE, config)

    @Test
    fun `each sheet has its face and a record away hides them`() {
        assertNull(SheetFaces.of(state(null)))
        assertEquals(SheetFace.Summary(summary), SheetFaces.of(state(summary)))
        val dayState = state(PracticeSheet.Day(day), selected = day)
        assertEquals(SheetFace.Day(dayState.selected!!), SheetFaces.of(dayState))
        assertEquals(SheetFace.EditTime(edit), SheetFaces.of(state(edit, selected = day)))
        val path = state(PracticeSheet.Path)
        assertEquals(SheetFace.Path(path.header), SheetFaces.of(path))
        // a record opened from the sheet of the day is on screen: the frame steps aside, the model keeps the sheet
        assertNull(SheetFaces.of(state(PracticeSheet.Day(day), sheetsAway = true, selected = day)))
        // not in the frame until stage 107: their own windows
        assertNull(SheetFaces.of(state(PracticeSheet.Trophies)))
        assertNull(SheetFaces.of(state(PracticeSheet.Profile(nameDraft = "", importingPhoto = false))))
    }

    @Test
    fun `a swipe of each face says what it means`() {
        assertEquals(PracticeIntent.SummaryHidden, SheetFaces.hideIntent(SheetFace.Summary(summary)))
        val dayState = state(PracticeSheet.Day(day), selected = day)
        assertEquals(PracticeIntent.DayHidden, SheetFaces.hideIntent(SheetFace.Day(dayState.selected!!)))
        // «Время за день» — «Отмена»: the day is not changed, the sheet of the day comes back
        assertEquals(PracticeIntent.EditTimeCancelled, SheetFaces.hideIntent(SheetFace.EditTime(edit)))
        assertEquals(PracticeIntent.PathHidden, SheetFaces.hideIntent(SheetFace.Path(state(PracticeSheet.Path).header)))
    }

    @Test
    fun `only a sheet over another has a way back`() {
        assertEquals(PracticeIntent.EditTimeCancelled, SheetFaces.backIntent(SheetFace.EditTime(edit)))
        assertNull(SheetFaces.backIntent(SheetFace.Summary(summary)))
        assertNull(SheetFaces.backIntent(SheetFace.Day(state(PracticeSheet.Day(day), selected = day).selected!!)))
        assertNull(SheetFaces.backIntent(SheetFace.Path(state(PracticeSheet.Path).header)))
    }
}
