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
import kotlinx.coroutines.flow.first

/**
 * Ends the running practice: a row in the repository, then the store is cleared. Both the
 * practice screen and the forgotten-practice prompt go through here, and [save] checks that the
 * practice it was asked about still runs, so two answers to the same practice cannot store it twice.
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
    /**
     * The earning of the practice as it was stored — what «Занятие сохранено» shows (spec 3.31); null when
     * no practice with that start runs any more (already saved or discarded elsewhere).
     */
    suspend fun save(startedAtEpochMs: Long, durationMs: Long): TaktEarning? {
        val running = store.running.first() ?: return null
        if (running.startedAtEpochMs != startedAtEpochMs) return null
        val date = practiceDateOf(startedAtEpochMs, clock.zone)
        repository.add(
            PracticeEntry(
                date = date,
                startedAtEpochMs = startedAtEpochMs,
                durationMs = durationMs,
                manual = false,
            ),
        )
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
        return earning
    }

    suspend fun discard() {
        notes.clear()
        blocks.clear()
        store.clear()
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
