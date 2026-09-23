package com.example.violintuner.core.data.backing.di

import com.example.violintuner.BuildConfig
import com.example.violintuner.core.audio.backing.AndroidAudioRoutes
import com.example.violintuner.core.audio.backing.AppBackingFiles
import com.example.violintuner.core.audio.backing.AudioRoutes
import com.example.violintuner.core.audio.backing.BackingFileImporter
import com.example.violintuner.core.audio.backing.BackingImporter
import com.example.violintuner.core.audio.backing.BackingPreview
import com.example.violintuner.core.audio.backing.DeviceHeadphoneCalibrator
import com.example.violintuner.core.audio.backing.HeadphoneCalibrator
import com.example.violintuner.core.audio.backing.MediaBackingPreview
import com.example.violintuner.core.audio.backing.BackingPlaybackFactory
import com.example.violintuner.core.audio.backing.FakeHeadphoneRoutes
import com.example.violintuner.core.audio.backing.TrackBackingPlayback
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
    abstract fun bindBackingFileImporter(impl: BackingImporter): BackingFileImporter

    @Binds
    abstract fun bindBackingPreview(impl: MediaBackingPreview): BackingPreview

    @Binds
    abstract fun bindHeadphoneCalibrator(impl: DeviceHeadphoneCalibrator): HeadphoneCalibrator

    @Binds
    @Singleton
    abstract fun bindHeadphoneLatencyStore(impl: DataStoreHeadphoneLatencyStore): HeadphoneLatencyStore

    companion object {
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
