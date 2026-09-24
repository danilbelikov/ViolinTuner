package com.violinjourney.app.core.data.practice

import com.violinjourney.app.core.domain.practice.PracticeEntry
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.test.Test

class PracticeMapperTest {
    @Test
    fun `entry survives the round trip and the date is stored as iso text`() {
        val entry = PracticeEntry(
            date = LocalDate(2026, 9, 7),
            startedAtEpochMs = 1_789_000_000_000,
            durationMs = 47 * 60_000L,
            manual = true,
            id = 12,
        )
        val entity = PracticeMapper.toEntity(entry)
        assertEquals("2026-09-07", entity.date)
        assertEquals(entry, PracticeMapper.toDomain(entity))
    }
}
