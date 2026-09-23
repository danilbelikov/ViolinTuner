package com.violinjourney.app.core.data.journey.di

import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.journey.JourneyDao
import com.violinjourney.app.core.data.journey.RoomHomeRepository
import com.violinjourney.app.core.data.journey.RoomJourneyRepository
import com.violinjourney.app.core.domain.home.HomeRepository
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import com.violinjourney.app.core.settings.DataStorePracticeNotesStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class JourneyDataModule {
    @Binds
    @Singleton
    abstract fun bindJourneyRepository(impl: RoomJourneyRepository): JourneyRepository

    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: RoomHomeRepository): HomeRepository

    @Binds
    @Singleton
    abstract fun bindPracticeNotesStore(impl: DataStorePracticeNotesStore): PracticeNotesStore

    companion object {
        @Provides
        fun provideJourneyDao(database: AppDatabase): JourneyDao = database.journeyDao()

        @Provides
        @Singleton
        fun provideJourneyConfig(): JourneyConfig = JourneyConfig()
    }
}
