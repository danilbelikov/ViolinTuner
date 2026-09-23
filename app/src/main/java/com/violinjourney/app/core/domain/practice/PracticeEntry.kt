package com.violinjourney.app.core.domain.practice

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * A finished practice. [date] is the local date of its start, fixed when it was saved: it does
 * not move when the user changes time zones (spec 3.12).
 */
data class PracticeEntry(
    val date: LocalDate,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    /** Entered through "change time" rather than timed. */
    val manual: Boolean,
    val id: Long = 0,
)

/** The practice in progress; lives outside the database because it has no end yet. */
data class RunningPractice(
    val startedAtEpochMs: Long,
    /** When the Live engine last reported a note while this practice ran; null = never. */
    val lastSoundEpochMs: Long?,
) {
    /** Never negative: a system clock moved backwards reads as zero, not as a debt. */
    fun elapsedMs(nowEpochMs: Long): Long = (nowEpochMs - startedAtEpochMs).coerceAtLeast(0)
}

/** The day a practice started on belongs to, in the zone the device was in at that moment. */
fun practiceDateOf(startedAtEpochMs: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(startedAtEpochMs).atZone(zone).toLocalDate()

interface PracticeRepository {
    /** Every saved practice, oldest first. Sums are taken in memory: a year is a few hundred rows. */
    val entries: Flow<List<PracticeEntry>>

    suspend fun add(entry: PracticeEntry): Long

    /**
     * "Change time": every entry of [date] is replaced by one manual entry of [durationMs];
     * zero only removes them (spec 5.6). [startedAtEpochMs] is the nominal start of the new entry.
     */
    suspend fun replaceDay(date: LocalDate, durationMs: Long, startedAtEpochMs: Long)
}

interface RunningPracticeStore {
    /** Null when no practice runs. */
    val running: Flow<RunningPractice?>

    suspend fun start(startedAtEpochMs: Long)

    /** No-op when nothing runs. */
    suspend fun markSound(epochMs: Long)

    suspend fun clear()
}
