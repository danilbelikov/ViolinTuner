package com.example.violintuner.core.backup.di

import com.example.violintuner.core.backup.AppBackupDocuments
import com.example.violintuner.core.backup.AppBackupStore
import com.example.violintuner.core.backup.BackupConfig
import com.example.violintuner.core.backup.BackupDocuments
import com.example.violintuner.core.backup.BackupKeepAlive
import com.example.violintuner.core.backup.BackupService
import com.example.violintuner.core.backup.BackupStore
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
