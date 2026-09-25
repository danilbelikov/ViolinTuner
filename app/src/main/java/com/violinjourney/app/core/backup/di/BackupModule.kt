package com.violinjourney.app.core.backup.di

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.backup.AppBackupDocuments
import com.violinjourney.app.core.backup.AppBackupStore
import com.violinjourney.app.core.backup.BackupConfig
import com.violinjourney.app.core.backup.BackupDocuments
import com.violinjourney.app.core.backup.BackupKeepAlive
import com.violinjourney.app.core.backup.BackupManager
import com.violinjourney.app.core.backup.BackupService
import com.violinjourney.app.core.backup.BackupSpeed
import com.violinjourney.app.core.backup.BackupStore
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.backup.BackupPrefs
import com.violinjourney.app.core.time.WallClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    abstract fun bindStore(impl: AppBackupStore): BackupStore

    @Binds
    abstract fun bindDocuments(impl: AppBackupDocuments): BackupDocuments

    @Binds
    abstract fun bindKeepAlive(impl: BackupService.Starter): BackupKeepAlive

    companion object {
        @Provides
        fun provideConfig(): BackupConfig = BackupConfig()

        /** One per app: it measures how fast copies go on this phone (spec 5.14). */
        @Provides
        @Singleton
        fun provideSpeed(config: BackupConfig) = BackupSpeed(config)

        /** One per app, with a scope of its own: minutes of work must not end because a screen did (spec 3.20). */
        @Provides
        @Singleton
        fun provideManager(
            store: BackupStore,
            documents: BackupDocuments,
            prefs: BackupPrefs,
            keepAlive: BackupKeepAlive,
            config: BackupConfig,
            speed: BackupSpeed,
            clock: WallClock,
            elapsed: ElapsedClock,
            @IoDispatcher io: CoroutineDispatcher,
            analytics: Analytics,
        ) = BackupManager(store, documents, prefs, keepAlive, config, speed, clock, elapsed, io, analytics)
    }
}
