package com.example.violintuner.feature.session.di

import com.example.violintuner.feature.session.player.MediaPlayerSessionPlayer
import com.example.violintuner.feature.session.player.SessionPlayerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    fun provideSessionPlayerFactory(): SessionPlayerFactory = SessionPlayerFactory(::MediaPlayerSessionPlayer)
}
