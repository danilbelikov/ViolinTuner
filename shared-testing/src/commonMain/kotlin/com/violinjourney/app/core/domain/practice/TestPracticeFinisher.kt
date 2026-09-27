package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.journey.NoJourney
import com.violinjourney.app.core.domain.journey.NoPracticeNotes
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import com.violinjourney.app.core.time.WallClock

/**
 * A [PracticeFinisher] for a test, with the parameters in the constructor's order: what the test does not look at is a
 * stand-in. The app wires every one of them itself — the constructor has no defaults, so a forgotten one does not compile.
 */
fun testPracticeFinisher(
    repository: PracticeRepository,
    store: RunningPracticeStore,
    clock: WallClock,
    notes: PracticeNotesStore = NoPracticeNotes,
    journey: JourneyRepository = NoJourney,
    journeyConfig: JourneyConfig = JourneyConfig(),
    blocks: BlockStore = NoBlocks,
    blockHistory: PieceBlockRepository = NoBlockHistory,
    config: PracticeConfig = PracticeConfig(),
    analytics: Analytics = NoOpAnalytics(),
): PracticeFinisher = PracticeFinisher(repository, store, clock, notes, journey, journeyConfig, blocks, blockHistory, config, analytics)
