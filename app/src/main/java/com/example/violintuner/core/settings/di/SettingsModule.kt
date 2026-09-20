package com.example.violintuner.core.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.violintuner.core.backup.BackupPrefs
import com.example.violintuner.core.domain.practice.RunningPracticeStore
import com.example.violintuner.core.domain.progress.ProfileRepository
import com.example.violintuner.core.domain.repertoire.StandHintStore
import com.example.violintuner.core.settings.DataStoreBackupPrefs
import com.example.violintuner.core.settings.DataStoreProfileRepository
import com.example.violintuner.core.settings.DataStoreRunningPracticeStore
import com.example.violintuner.core.settings.DataStoreSettingsRepository
import com.example.violintuner.core.settings.DataStoreStandHintStore
import com.example.violintuner.core.settings.IntonationConfigSource
import com.example.violintuner.core.settings.SettingsConfigSource
import com.example.violintuner.core.settings.SettingsRepository
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
    abstract fun bindProfileRepository(impl: DataStoreProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindStandHintStore(impl: DataStoreStandHintStore): StandHintStore

    @Binds
    @Singleton
    abstract fun bindBackupPrefs(impl: DataStoreBackupPrefs): BackupPrefs

    companion object {
        private const val FILE_NAME = "user_settings"

        // DataStore allows one instance per file: keep it a singleton.
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }
    }
}
