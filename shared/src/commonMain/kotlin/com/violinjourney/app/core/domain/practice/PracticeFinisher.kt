package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.journey.NoJourney
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.domain.journey.NoPracticeNotes
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import com.violinjourney.app.core.domain.journey.TaktEarning
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.analytics.PracticeFinished
import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A practice [PracticeFinisher.save] has just stored: its row, with the id it got, and what it earned (spec 3.31). */
data class SavedPractice(val entry: PracticeEntry, val earning: TaktEarning)

/**
 * Ends the running practice: a row in the repository, then the store is cleared. Both the
 * practice screen and the forgotten-practice prompt go through here, and [save] checks that the
 * practice it was asked about still runs. One per app (SharedModule, IosGraph): its lock takes the
 * answers one at a time, so two answers to the same practice — a double tap, the sheet and the
 * prompt at once — store it once. Once begun, an answer runs to its end: the rows and the clearing
 * of the store go together, and a caller gone halfway cannot leave the practice stored and still
 * running, to be stored again by the next answer (spec 5.6).
 * The blocks of the practice (spec 3.28) are saved and paid for here too, and go with it when it is discarded.
 */
class PracticeFinisher(
    private val repository: PracticeRepository,
    private val store: RunningPracticeStore,
    private val clock: WallClock,
    private val notes: PracticeNotesStore = NoPracticeNotes,
    private val journey: JourneyRepository = NoJourney,
    private val journeyConfig: JourneyConfig = JourneyConfig(),
    private val blocks: BlockStore = NoBlocks,
    private val blockHistory: PieceBlockRepository = NoBlockHistory,
    private val config: PracticeConfig = PracticeConfig(),
    private val analytics: Analytics = NoOpAnalytics(),
) {
    private val lock = Mutex()

    private val savingNow = MutableStateFlow(false)
    private val saved = MutableStateFlow<SavedPractice?>(null)

    /** True while [save] writes the rows of a practice: its gift of a trophy waits for its recap (spec 3.31). */
    val saving: StateFlow<Boolean> = savingNow.asStateFlow()

    /**
     * The practice this process saved last, whoever answered — the practice screen or the forgotten-practice prompt
     * over it; null before the first. In memory only: «Занятия» recap it from here, and what was saved before a screen
     * opened is history to it, not news (spec 3.31). Set before [saving] goes back to false.
     */
    val lastSaved: StateFlow<SavedPractice?> = saved.asStateFlow()

    /**
     * The earning of the practice as it was stored — what «Занятие сохранено» shows (spec 3.31); null when
     * no practice with that start runs any more (already saved or discarded elsewhere).
     */
    suspend fun save(startedAtEpochMs: Long, durationMs: Long): TaktEarning? =
        lock.withLock {
            withContext(NonCancellable) {
                savingNow.value = true
                try {
                    saveRunning(startedAtEpochMs, durationMs)
                } finally {
                    savingNow.value = false
                }
            }
        }

    private suspend fun saveRunning(startedAtEpochMs: Long, durationMs: Long): TaktEarning? {
        val running = store.running.first() ?: return null
        if (running.startedAtEpochMs != startedAtEpochMs) return null
        val date = practiceDateOf(startedAtEpochMs, clock.zone)
        val entry = PracticeEntry(
            date = date,
            startedAtEpochMs = startedAtEpochMs,
            durationMs = durationMs,
            manual = false,
        )
        val stored = entry.copy(id = repository.add(entry))
        // Blocks are cut at the end of what is saved: the stepper and «Закончить в 18:42» cut them too (spec 5.21).
        // An element is paid for once a day, so what was paid that day already counts.
        val paidThatDay = blockHistory.blocks.first().filter { it.date == date && it.paid }.mapTo(mutableSetOf()) { it.pieceId }
        val played = BlockRules.played(BlockRules.ofPractice(running, blocks.blocks.first()), startedAtEpochMs + durationMs, config)
        val piecesPaid = blockHistory.add(BlockRules.settle(played, date, paidThatDay)).count { it.paid }
        // The journey is paid in clean notes, in time at the stand and in elements played for their goal
        // (spec 5.17, 5.19, 5.21). Only a practice that was really timed earns: a day typed in by hand would be takts for nothing.
        val notesPlayed = notes.countFor(startedAtEpochMs)
        val earning = TaktEarning(
            atEpochMs = clock.millis(), notesPlayed = notesPlayed.played, notesInTune = notesPlayed.inTune, durationMs = durationMs,
            takts = JourneyRules.taktsFor(notesPlayed.inTune, durationMs, journeyConfig, piecesPaid),
            piecesPaid = piecesPaid,
        )
        journey.earn(earning)
        analytics.track(PracticeFinished(minutes = (durationMs / MS_PER_MINUTE).toInt(), blocks = played.size, bars = earning.takts))
        notes.clear()
        blocks.clear()
        store.clear()
        saved.value = SavedPractice(stored, earning)
        return earning
    }

    /**
     * The practice that began at [startedAtEpochMs] goes unsaved, its notes and blocks with it. As [save], it ends only
     * that one: a sheet answered late — its practice saved from the prompt meanwhile, and the next one begun — leaves
     * the next one running.
     */
    suspend fun discard(startedAtEpochMs: Long): Unit = lock.withLock {
        withContext(NonCancellable) {
            if (store.running.first()?.startedAtEpochMs != startedAtEpochMs) return@withContext
            notes.clear()
            blocks.clear()
            store.clear()
        }
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
