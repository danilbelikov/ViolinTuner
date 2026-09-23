package com.example.violintuner.core.data.backing.di

import com.example.violintuner.core.audio.backing.AppBackingFiles
import com.example.violintuner.core.audio.backing.BackingPcm
import com.example.violintuner.core.audio.backing.BackingPcmCache
import com.example.violintuner.core.data.backing.RoomBackingRepository
import com.example.violintuner.core.domain.backing.BackingConfig
import com.example.violintuner.core.domain.backing.BackingFiles
import com.example.violintuner.core.domain.backing.BackingRepository
import com.example.violintuner.core.domain.backing.HeadphoneLatencyStore
import com.example.violintuner.core.settings.DataStoreHeadphoneLatencyStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackingDataModule {
    @Binds
    @Singleton
    abstract fun bindBackingRepository(impl: RoomBackingRepository): BackingRepository

    @Binds
    @Singleton
    abstract fun bindBackingFiles(impl: AppBackingFiles): BackingFiles

    @Binds
    @Singleton
    abstract fun bindBackingPcm(impl: BackingPcmCache): BackingPcm

    @Binds
    @Singleton
    abstract fun bindHeadphoneLatencyStore(impl: DataStoreHeadphoneLatencyStore): HeadphoneLatencyStore

    companion object {
        @Provides
        @Singleton
        fun provideBackingConfig(): BackingConfig = BackingConfig()
    }
}
