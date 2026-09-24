package com.violinjourney.app.core.di

import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.backing.BackingPlaybackFactory
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.recording.TakePipeline
import kotlinx.coroutines.CoroutineDispatcher
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PieceBlockRepository
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.progress.TrophyRepository
import com.violinjourney.app.core.domain.venue.VenueStore
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.history.HistorySectionAsk
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The classes of the shared module, which knows no javax.inject (iOS has none): what an @Inject
 * constructor gave Hilt before, a provider gives it here — every parameter, as Hilt filled them.
 */
@Module
@InstallIn(SingletonComponent::class)
object SharedModule {
    @Provides
    fun provideTrophyAwarder(repository: TrophyRepository, config: ProgressConfig, clock: WallClock) =
        TrophyAwarder(repository, config, clock)

    @Provides
    fun provideVenues(store: VenueStore, journey: JourneyRepository) = Venues(store, journey)

    /** One per app: the ask travels from Live to «Занятия» (spec 3.12). */
    @Provides
    @Singleton
    fun provideFinishPracticeAsk() = FinishPracticeAsk()

    /** One per app: the section of «Записи» asked for from Live (spec 3.28). */
    @Provides
    @Singleton
    fun provideHistorySectionAsk() = HistorySectionAsk()

    @Provides
    fun providePracticeFinisher(
        repository: PracticeRepository,
        store: RunningPracticeStore,
        clock: WallClock,
        notes: PracticeNotesStore,
        journey: JourneyRepository,
        journeyConfig: JourneyConfig,
        blocks: BlockStore,
        blockHistory: PieceBlockRepository,
        config: PracticeConfig,
        analytics: Analytics,
    ) = PracticeFinisher(repository, store, clock, notes, journey, journeyConfig, blocks, blockHistory, config, analytics)

    /** One per app: whoever must not start under a recording asks it (spec 3.20). */
    @Provides
    @Singleton
    fun provideRecordingWatch() = RecordingWatch()

    /** One per screen, not a singleton: it holds that screen's wish to record. */
    @Provides
    fun provideTakePipeline(
        pitchSource: PitchSource,
        sessionRepository: SessionRepository,
        audioFiles: SessionAudioFiles,
        runningPractice: RunningPracticeStore,
        practiceConfig: PracticeConfig,
        clock: WallClock,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
        watch: RecordingWatch,
        practiceNotes: PracticeNotesStore,
        journeyConfig: JourneyConfig,
        backings: BackingRepository,
        backingPlaybackFactory: BackingPlaybackFactory,
        backingConfig: BackingConfig,
        analytics: Analytics,
    ) = TakePipeline(
        pitchSource, sessionRepository, audioFiles, runningPractice, practiceConfig, clock, dispatcher, watch,
        practiceNotes, journeyConfig, backings, backingPlaybackFactory, backingConfig, analytics,
    )
}
