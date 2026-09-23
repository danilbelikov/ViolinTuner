package com.violinjourney.app.core.backup.di

import com.violinjourney.app.core.backup.AppBackupDocuments
import com.violinjourney.app.core.backup.AppBackupStore
import com.violinjourney.app.core.backup.BackupConfig
import com.violinjourney.app.core.backup.BackupDocuments
import com.violinjourney.app.core.backup.BackupKeepAlive
import com.violinjourney.app.core.backup.BackupService
import com.violinjourney.app.core.backup.BackupStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
    }
}
