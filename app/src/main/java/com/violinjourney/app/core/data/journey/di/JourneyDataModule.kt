package com.violinjourney.app.core.data.journey.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.journey.JourneyDao
import com.violinjourney.app.core.data.journey.RoomHomeRepository
import com.violinjourney.app.core.data.journey.RoomJourneyRepository
import com.violinjourney.app.core.domain.home.HomeRepository
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import com.violinjourney.app.core.settings.DataStorePracticeNotesStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class JourneyDataModule {
    companion object {
        @Provides
        @Singleton
        fun provideJourneyRepository(
            dao: JourneyDao,
            analytics: Analytics,
        ): JourneyRepository = RoomJourneyRepository(dao, analytics)

        @Provides
        @Singleton
        fun provideHomeRepository(dao: JourneyDao, analytics: Analytics): HomeRepository = RoomHomeRepository(dao, analytics)

        @Provides
        @Singleton
        fun providePracticeNotesStore(store: DataStore<Preferences>): PracticeNotesStore = DataStorePracticeNotesStore(store)

        @Provides
        fun provideJourneyDao(database: AppDatabase): JourneyDao = database.journeyDao()

        @Provides
        @Singleton
        fun provideJourneyConfig(): JourneyConfig = JourneyConfig()
    }
}
