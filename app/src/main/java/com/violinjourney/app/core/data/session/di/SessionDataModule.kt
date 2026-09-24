package com.violinjourney.app.core.data.session.di

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.data.session.RoomSessionRepository
import com.violinjourney.app.core.data.session.SessionDao
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.time.WallClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionDataModule {
    @Provides
    @Singleton
    fun provideSessionRepository(
        dao: SessionDao,
        defaultConfig: IntonationConfig,
        audioFiles: SessionAudioFiles,
        clock: WallClock,
        analytics: Analytics,
    ): SessionRepository = RoomSessionRepository(dao, defaultConfig, audioFiles, clock, analytics)
}
