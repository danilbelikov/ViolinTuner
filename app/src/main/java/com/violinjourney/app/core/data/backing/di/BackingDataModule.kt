package com.violinjourney.app.core.data.backing.di

import com.violinjourney.app.BuildConfig
import com.violinjourney.app.core.audio.backing.AndroidAudioRoutes
import com.violinjourney.app.core.audio.backing.AppBackingFiles
import com.violinjourney.app.core.audio.backing.AudioRoutes
import com.violinjourney.app.core.audio.backing.BackingFileImporter
import com.violinjourney.app.core.audio.backing.BackingImporter
import com.violinjourney.app.core.audio.backing.BackingPreview
import com.violinjourney.app.core.audio.backing.MediaBackingPreview
import com.violinjourney.app.core.audio.backing.BackingPlaybackFactory
import com.violinjourney.app.core.audio.backing.FakeHeadphoneRoutes
import com.violinjourney.app.core.audio.backing.TrackBackingPlayback
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.backing.BackingPcmCache
import com.violinjourney.app.core.data.backing.BackingDao
import com.violinjourney.app.core.data.backing.RoomBackingRepository
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingFiles
import com.violinjourney.app.core.domain.backing.BackingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class BackingDataModule {
    @Binds
    @Singleton
    abstract fun bindBackingFiles(impl: AppBackingFiles): BackingFiles

    @Binds
    @Singleton
    abstract fun bindBackingPcm(impl: BackingPcmCache): BackingPcm

    @Binds
    abstract fun bindBackingFileImporter(impl: BackingImporter): BackingFileImporter

    @Binds
    abstract fun bindBackingPreview(impl: MediaBackingPreview): BackingPreview


    companion object {
        @Provides
        @Singleton
        fun provideBackingRepository(
            dao: BackingDao,
            files: BackingFiles,
            @IoDispatcher io: CoroutineDispatcher,
        ): BackingRepository = RoomBackingRepository(dao, files, io)

        @Provides
        @Singleton
        fun provideBackingConfig(): BackingConfig = BackingConfig()

        @Provides
        @Singleton
        fun provideAudioRoutes(real: javax.inject.Provider<AndroidAudioRoutes>): AudioRoutes =
            if (BuildConfig.FAKE_PITCH_SOURCE) FakeHeadphoneRoutes() else real.get()

        @Provides
        fun provideBackingPlaybackFactory(routes: AudioRoutes): BackingPlaybackFactory = BackingPlaybackFactory { TrackBackingPlayback(routes) }
    }
}
