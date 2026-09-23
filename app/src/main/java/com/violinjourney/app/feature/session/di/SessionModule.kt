package com.violinjourney.app.feature.session.di

import com.violinjourney.app.core.audio.playback.ChainSessionPlayer
import com.violinjourney.app.core.audio.playback.SessionPlayerFactory
import com.violinjourney.app.core.audio.playback.VideoPictureFactory
import com.violinjourney.app.core.audio.playback.VideoTrackRenderer
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.backing.BackingConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    fun provideSessionPlayerFactory(config: SoundConfig, backing: BackingConfig): SessionPlayerFactory = SessionPlayerFactory { ChainSessionPlayer(config, backing) }

    @Provides
    fun provideVideoPictureFactory(): VideoPictureFactory = VideoPictureFactory { VideoTrackRenderer(it) }
}
