package com.violinjourney.app.core.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.violinjourney.app.core.backup.BackupPrefs
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.progress.ProfileRepository
import com.violinjourney.app.core.domain.repertoire.StandHintStore
import com.violinjourney.app.core.domain.venue.VenueStore
import com.violinjourney.app.core.settings.DataStoreBackupPrefs
import com.violinjourney.app.core.settings.DataStoreBlockStore
import com.violinjourney.app.core.settings.DataStoreProfileRepository
import com.violinjourney.app.core.settings.DataStoreRunningPracticeStore
import com.violinjourney.app.core.settings.DataStoreSettingsRepository
import com.violinjourney.app.core.settings.DataStoreStandHintStore
import com.violinjourney.app.core.settings.DataStoreVenueStore
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    companion object {
        @Provides
        @Singleton
        fun provideSettingsRepository(
            dataStore: DataStore<Preferences>,
        ): SettingsRepository = DataStoreSettingsRepository(dataStore)

        @Provides
        @Singleton
        fun provideConfigSource(
            default: IntonationConfig,
            repository: SettingsRepository,
        ): IntonationConfigSource = SettingsConfigSource(default, repository)

        @Provides
        @Singleton
        fun provideRunningPracticeStore(
            dataStore: DataStore<Preferences>,
        ): RunningPracticeStore = DataStoreRunningPracticeStore(dataStore)

        @Provides
        @Singleton
        fun provideBlockStore(store: DataStore<Preferences>): BlockStore = DataStoreBlockStore(store)

        @Provides
        @Singleton
        fun provideProfileRepository(
            dataStore: DataStore<Preferences>,
        ): ProfileRepository = DataStoreProfileRepository(dataStore)

        @Provides
        @Singleton
        fun provideStandHintStore(dataStore: DataStore<Preferences>): StandHintStore = DataStoreStandHintStore(dataStore)

        @Provides
        @Singleton
        fun provideBackupPrefs(store: DataStore<Preferences>): BackupPrefs = DataStoreBackupPrefs(store)

        @Provides
        @Singleton
        fun provideVenueStore(dataStore: DataStore<Preferences>): VenueStore = DataStoreVenueStore(dataStore)

        private const val FILE_NAME = "user_settings"

        // DataStore allows one instance per file: keep it a singleton.
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }
    }
}
