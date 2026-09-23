package com.violinjourney.app.core.data.practice

import com.violinjourney.app.core.domain.practice.PracticeEntry
import java.time.LocalDate

object PracticeMapper {
    fun toEntity(entry: PracticeEntry) = PracticeEntity(
        id = entry.id,
        date = entry.date.toString(),
        startedAtEpochMs = entry.startedAtEpochMs,
        durationMs = entry.durationMs,
        manual = entry.manual,
    )

    fun toDomain(entity: PracticeEntity) = PracticeEntry(
        date = LocalDate.parse(entity.date),
        startedAtEpochMs = entity.startedAtEpochMs,
        durationMs = entity.durationMs,
        manual = entity.manual,
        id = entity.id,
    )
}
