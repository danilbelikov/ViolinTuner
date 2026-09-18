package com.example.violintuner.core.domain.practice

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
        store.clear()
        return true
    }

    suspend fun discard() = store.clear()
}
