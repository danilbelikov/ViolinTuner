package com.violinjourney.app.core.recording

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.backing.BackingPlaybackFactory
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.NoPracticeNotes
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A [TakePipeline] for a test, with the parameters in the constructor's order: what the test does not look at is a
 * stand-in — a watch of its own, no notes, no backings. The app wires every one of them itself (one [RecordingWatch] per
 * app among them): the constructor has no defaults, so a forgotten one does not compile.
 */
fun testTakePipeline(
    pitchSource: PitchSource,
    sessionRepository: SessionRepository,
    audioFiles: SessionAudioFiles,
    runningPractice: RunningPracticeStore,
    practiceConfig: PracticeConfig,
    clock: WallClock,
    dispatcher: CoroutineDispatcher,
    watch: RecordingWatch = RecordingWatch(),
    practiceNotes: PracticeNotesStore = NoPracticeNotes,
    journeyConfig: JourneyConfig = JourneyConfig(),
    backings: BackingRepository = NoBackings,
    backingPlaybackFactory: BackingPlaybackFactory? = null,
    backingConfig: BackingConfig = BackingConfig(),
    analytics: Analytics = NoOpAnalytics(),
): TakePipeline = TakePipeline(
    pitchSource, sessionRepository, audioFiles, runningPractice, practiceConfig, clock, dispatcher, watch, practiceNotes, journeyConfig,
    backings, backingPlaybackFactory, backingConfig, analytics,
)
