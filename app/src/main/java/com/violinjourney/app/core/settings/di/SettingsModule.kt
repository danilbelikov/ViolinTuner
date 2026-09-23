package com.violinjourney.app.core.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.violinjourney.app.core.backup.BackupPrefs
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
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindConfigSource(impl: SettingsConfigSource): IntonationConfigSource

    @Binds
    @Singleton
    abstract fun bindRunningPracticeStore(impl: DataStoreRunningPracticeStore): RunningPracticeStore

    @Binds
    @Singleton
    abstract fun bindBlockStore(impl: DataStoreBlockStore): BlockStore

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: DataStoreProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindStandHintStore(impl: DataStoreStandHintStore): StandHintStore

    @Binds
    @Singleton
    abstract fun bindBackupPrefs(impl: DataStoreBackupPrefs): BackupPrefs

    @Binds
    @Singleton
    abstract fun bindVenueStore(impl: DataStoreVenueStore): VenueStore

    companion object {
        private const val FILE_NAME = "user_settings"

        // DataStore allows one instance per file: keep it a singleton.
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }
    }
}
