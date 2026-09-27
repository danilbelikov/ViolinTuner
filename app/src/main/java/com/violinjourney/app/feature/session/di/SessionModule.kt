package com.violinjourney.app.feature.session.di

import com.violinjourney.app.core.audio.playback.AndroidPlaybackFocus
import com.violinjourney.app.core.audio.playback.ChainSessionPlayer
import com.violinjourney.app.core.audio.playback.PlaybackFocus
import com.violinjourney.app.core.audio.playback.SessionPlayerFactory
import com.violinjourney.app.core.audio.playback.VideoPictureFactory
import com.violinjourney.app.core.audio.playback.VideoTrackRenderer
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.backing.BackingConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    /** One focus per player: each holds its own request. */
    @Provides
    fun provideSessionPlayerFactory(config: SoundConfig, backing: BackingConfig, focus: Provider<AndroidPlaybackFocus>): SessionPlayerFactory =
        SessionPlayerFactory { ChainSessionPlayer(config, backing, focus.get()) }

    /** Unscoped, as above: the backing's preview asks for the sound with a request of its own. */
    @Provides
    fun providePlaybackFocus(impl: AndroidPlaybackFocus): PlaybackFocus = impl

    @Provides
    fun provideVideoPictureFactory(): VideoPictureFactory = VideoPictureFactory { VideoTrackRenderer(it) }
}
