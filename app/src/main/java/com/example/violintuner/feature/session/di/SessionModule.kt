package com.example.violintuner.feature.session.di

import com.example.violintuner.core.audio.playback.ChainSessionPlayer
import com.example.violintuner.core.audio.playback.SessionPlayerFactory
import com.example.violintuner.core.domain.sound.SoundConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    fun provideSessionPlayerFactory(config: SoundConfig): SessionPlayerFactory = SessionPlayerFactory { ChainSessionPlayer(config) }
}
