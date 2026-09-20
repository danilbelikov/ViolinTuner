package com.example.violintuner.core.domain.practice

import com.example.violintuner.core.domain.journey.JourneyConfig
import com.example.violintuner.core.domain.journey.JourneyRepository
import com.example.violintuner.core.domain.journey.NoJourney
import com.example.violintuner.core.domain.journey.JourneyRules
import com.example.violintuner.core.domain.journey.NoPracticeNotes
import com.example.violintuner.core.domain.journey.PracticeNotesStore
import com.example.violintuner.core.domain.journey.TaktEarning
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Ends the running practice: a row in the repository, then the store is cleared. Both the
 * practice screen and the forgotten-practice prompt go through here, and [save] checks that the
 * practice it was asked about still runs, so two answers to the same practice cannot store it twice.
 */
class PracticeFinisher @Inject constructor(
    private val repository: PracticeRepository,
    private val store: RunningPracticeStore,
    private val clock: Clock,
    private val notes: PracticeNotesStore = NoPracticeNotes,
    private val journey: JourneyRepository = NoJourney,
    private val journeyConfig: JourneyConfig = JourneyConfig(),
) {
    /** False when no practice with that start runs any more (already saved or discarded elsewhere). */
    suspend fun save(startedAtEpochMs: Long, durationMs: Long): Boolean {
        val running = store.running.first() ?: return false
        if (running.startedAtEpochMs != startedAtEpochMs) return false
        repository.add(
            PracticeEntry(
                date = practiceDateOf(startedAtEpochMs, clock.zone),
                startedAtEpochMs = startedAtEpochMs,
                durationMs = durationMs,
                manual = false,
            ),
        )
        // The journey is paid in clean notes and in time at the stand (spec 5.17). Only a practice
        // that was really timed earns: a day typed in by hand would be takts for nothing.
        val played = notes.countFor(startedAtEpochMs)
        journey.earn(
            TaktEarning(
                atEpochMs = clock.millis(), notesPlayed = played.played, notesInTune = played.inTune, durationMs = durationMs,
                takts = JourneyRules.taktsFor(played.inTune, durationMs, journeyConfig),
            ),
        )
        notes.clear()
        store.clear()
        return true
    }

    suspend fun discard() {
        notes.clear()
        store.clear()
    }
}
