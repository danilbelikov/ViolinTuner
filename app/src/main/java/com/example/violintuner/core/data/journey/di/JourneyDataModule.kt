package com.example.violintuner.core.data.journey.di

import com.example.violintuner.core.data.AppDatabase
import com.example.violintuner.core.data.journey.JourneyDao
import com.example.violintuner.core.data.journey.RoomHomeRepository
import com.example.violintuner.core.data.journey.RoomJourneyRepository
import com.example.violintuner.core.domain.home.HomeRepository
import com.example.violintuner.core.domain.journey.JourneyConfig
import com.example.violintuner.core.domain.journey.JourneyRepository
import com.example.violintuner.core.domain.journey.PracticeNotesStore
import com.example.violintuner.core.settings.DataStorePracticeNotesStore
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
