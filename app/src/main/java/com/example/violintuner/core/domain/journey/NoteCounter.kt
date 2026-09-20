package com.example.violintuner.core.domain.journey

import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.Zone

/** Notes of a running practice: how many were played and how many of them in tune. */
data class NoteCount(val played: Int, val inTune: Int) {
    operator fun plus(other: NoteCount) = NoteCount(played + other.played, inTune + other.inTune)

    companion object {
        val ZERO = NoteCount(0, 0)
    }
}

/**
 * Counts the notes the engine hears (spec 5.17). A note is what the engine has locked on and kept
 * for at least [JourneyConfig.minNoteMs]; it is in tune when most of its fresh readings stood in
 * the green zone — the zone the player sees, with the tolerance the player chose. Readings held
 * through a pitch gap are not measurements and are skipped, as they are by the recorder. Time is
 * the frames' own; not thread-safe — it lives on the engine's thread, next to it.
 */
class NoteCounter(private val config: JourneyConfig) {
    private var note: Note? = null
    private var startedAtMs = 0L
    private var lastAtMs = 0L
    private var readings = 0
    private var inTune = 0
    private var pending = NoteCount.ZERO

    fun add(tMs: Long, reading: IntonationReading) {
        val active = reading as? IntonationReading.Active
        if (active == null) {
            close()
            return
        }
        if (active.held) return
        if (active.note != note) {
            close()
            note = active.note
            startedAtMs = tMs
        }
        lastAtMs = tMs
        readings++
        if (active.zone == Zone.IN_TUNE) inTune++
    }

    /** What has been counted since the last call; the running note is not in it until it ends. */
    fun take(): NoteCount = pending.also { pending = NoteCount.ZERO }

    /** The source stopped: the note that was sounding ends here. */
    fun finish(): NoteCount {
        close()
        return take()
    }

    private fun close() {
        if (note != null && lastAtMs - startedAtMs >= config.minNoteMs) {
            pending += NoteCount(played = 1, inTune = if (inTune * 2 > readings) 1 else 0)
        }
        note = null
        readings = 0
        inTune = 0
    }
}

/** The notes of the practice that runs, kept on disk between flushes of the counter. */
interface PracticeNotesStore {
    /**
     * Adds to the notes of the practice that started at [practiceStartedAtEpochMs]. Notes are
     * kept under the start of their practice: what belongs to another practice — a flush that
     * came late, a practice that was discarded — is dropped rather than carried over.
     */
    suspend fun add(practiceStartedAtEpochMs: Long, count: NoteCount)

    /** The notes of that very practice; zero for any other. */
    suspend fun countFor(practiceStartedAtEpochMs: Long): NoteCount

    suspend fun clear()
}

/** For chains that count nothing: tests of the pipeline, and builds before the journey. */
object NoPracticeNotes : PracticeNotesStore {
    override suspend fun add(practiceStartedAtEpochMs: Long, count: NoteCount) = Unit

    override suspend fun countFor(practiceStartedAtEpochMs: Long): NoteCount = NoteCount.ZERO

    override suspend fun clear() = Unit
}
