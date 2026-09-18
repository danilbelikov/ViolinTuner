package com.example.violintuner.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.example.violintuner.core.domain.practice.PracticeEntry
import com.example.violintuner.core.domain.session.SessionSummary
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.practice.components.EditTimeSheetContent
import com.example.violintuner.feature.practice.components.SummarySheetContent
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The month of the design brief: September 2026, today Thursday the 17th. */
private object Sample {
    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    val today: LocalDate = LocalDate.of(2026, 9, 17)
    private val minutes = mapOf(
        1 to 30, 2 to 50, 4 to 100, 5 to 15, 7 to 45, 8 to 70, 9 to 25, 11 to 125, 12 to 40, 13 to 55,
        14 to 35, 15 to 80, 16 to 50, 17 to 45,
    )
    val entries = minutes.map { (day, m) ->
        PracticeEntry(LocalDate.of(2026, 9, day), startedAtEpochMs = 0, durationMs = m * MS_PER_MINUTE, manual = false)
    }
    val sessions = listOf(session(1, 15, 18, 84), session(2, 15, 19, 71))

    private fun session(id: Long, day: Int, hour: Int, score: Int) = SessionSummary(
        id = id, title = null,
        startedAtEpochMs = LocalDate.of(2026, 9, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        durationMs = 8 * MS_PER_MINUTE + 15_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = score, nearPercent = 100 - score, offPercent = 0, maeCents = 5.0, biasCents = -4.0,
        previewZones = listOf(Zone.IN_TUNE, Zone.NEAR, Zone.IN_TUNE, Zone.OFF, Zone.IN_TUNE, Zone.IN_TUNE, Zone.NEAR, Zone.IN_TUNE),
        audioPath = null,
    )

    fun state(
        runningMs: Long? = null,
        selected: LocalDate = today,
        entries: List<PracticeEntry> = this.entries,
        sheet: PracticeSheet? = null,
    ): PracticeState = PracticeReducer.stateOf(
        entries = entries, sessions = sessions, runningMs = runningMs, month = YearMonth.of(2026, 9),
        selectedDate = selected, sheet = sheet, today = today, zone = zone, config = PracticeConfig(),
        intonationConfig = IntonationConfig(),
    )
}

@Preview(name = "10a idle", widthDp = 412, heightDp = 892)
@Composable
private fun IdlePreview() {
    ViolinTheme { PracticeScreen(state = Sample.state(), onIntent = {}, zone = Sample.zone) }
}

@Preview(name = "10b running", widthDp = 412, heightDp = 892)
@Composable
private fun RunningPreview() {
    ViolinTheme { PracticeScreen(state = Sample.state(runningMs = 754_000), onIntent = {}, zone = Sample.zone) }
}

@Preview(name = "10c1 empty day", widthDp = 412, heightDp = 892)
@Composable
private fun EmptyDayPreview() {
    ViolinTheme { PracticeScreen(state = Sample.state(selected = LocalDate.of(2026, 9, 3)), onIntent = {}, zone = Sample.zone) }
}

@Preview(name = "10c2 day with records", widthDp = 412, heightDp = 892)
@Composable
private fun RecordsDayPreview() {
    ViolinTheme { PracticeScreen(state = Sample.state(selected = LocalDate.of(2026, 9, 15)), onIntent = {}, zone = Sample.zone) }
}

@Preview(name = "10g empty state", widthDp = 412, heightDp = 892)
@Composable
private fun EmptyStatePreview() {
    ViolinTheme { PracticeScreen(state = Sample.state(entries = emptyList()), onIntent = {}, zone = Sample.zone) }
}

@Preview(name = "10j landscape running", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapePreview() {
    ViolinTheme { PracticeScreen(state = Sample.state(runningMs = 3_754_000), onIntent = {}, zone = Sample.zone) }
}

@Preview(name = "10d summary sheet", widthDp = 412)
@Composable
private fun SummarySheetPreview() {
    ViolinTheme {
        SummarySheetContent(
            sheet = PracticeReducer.summarySheet(startedAtEpochMs = 0, actualMs = 47 * MS_PER_MINUTE, PracticeConfig()),
            stepMinutes = 5,
            onIntent = {},
        )
    }
}

@Preview(name = "10e edit time sheet", widthDp = 412)
@Composable
private fun EditTimeSheetPreview() {
    ViolinTheme {
        EditTimeSheetContent(
            sheet = PracticeReducer.editSheet(LocalDate.of(2026, 9, 16), 50 * MS_PER_MINUTE, PracticeConfig()),
            stepMinutes = 5,
            onIntent = {},
        )
    }
}
