package com.violinjourney.app.core.di

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
}
