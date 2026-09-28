package com.violinjourney.app.core.domain.practice

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    /**
     * The last sign of life of this practice; null = never. A note Live heard, a take, a video shot — or «Продолжаю заниматься»
     * of «Занятие не закончено», which counts the same for the hour of silence and the limit (spec 3.12, 5.6).
     */
    val lastSoundEpochMs: Long?,
    /**
     * The last mark was set by the answer «Продолжаю заниматься», not by a sound (spec 3.36.3): «Занятие не закончено» then says
     * «вы ответили…» rather than «Скрипка звучала…». A real sound takes it back.
     */
    val lastMarkByAnswer: Boolean = false,
) {
    /** Never negative: a system clock moved backwards reads as zero, not as a debt. */
    fun elapsedMs(nowEpochMs: Long): Long = (nowEpochMs - startedAtEpochMs).coerceAtLeast(0)
}

/**
 * The day a practice that started at [startedAtEpochMs] belongs to, read in [zone] — the zone at the moment it is saved
 * (spec 3.12: the date is fixed when it is saved). A practice during which the zone changed is dated in the new one.
 */
fun practiceDateOf(startedAtEpochMs: Long, zone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(startedAtEpochMs).toLocalDateTime(zone).date

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

    /**
     * One practice at a time (spec 3.12): begins one at [startedAtEpochMs] only when none runs, by one write — so two
     * taps, or «Начать занятие» on two screens at once, never begin two, and a start never moves the start of the one
     * that runs. True — this call began it.
     */
    suspend fun startIfIdle(startedAtEpochMs: Long): Boolean

    /** A note, a take, a video shot at [epochMs]: the last sign of life, and no longer an answer's. No-op when nothing runs. */
    suspend fun markSound(epochMs: Long)

    /**
     * «Продолжаю заниматься» at [epochMs] (spec 3.12, 3.36.3): the last sign of life, as a sound is, but marked as set by the answer —
     * the next «Занятие не закончено» says so. Only [markSound] takes the mark back; [startIfIdle] and [clear] forget it. No-op when
     * nothing runs.
     */
    suspend fun markContinued(epochMs: Long)

    suspend fun clear()
}
