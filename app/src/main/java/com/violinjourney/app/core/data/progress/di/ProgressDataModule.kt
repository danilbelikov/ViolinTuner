package com.violinjourney.app.core.data.progress.di

import com.violinjourney.app.core.data.progress.RoomTrophyRepository
import com.violinjourney.app.core.data.progress.TrophyDao
import com.violinjourney.app.core.domain.progress.TrophyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProgressDataModule {
    @Provides
    @Singleton
    fun provideTrophyRepository(dao: TrophyDao): TrophyRepository = RoomTrophyRepository(dao)
}
