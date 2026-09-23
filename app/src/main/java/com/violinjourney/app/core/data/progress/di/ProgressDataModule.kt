package com.violinjourney.app.core.data.progress.di

import com.violinjourney.app.core.data.progress.RoomTrophyRepository
import com.violinjourney.app.core.domain.progress.TrophyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressDataModule {
    @Binds
    @Singleton
    abstract fun bindTrophyRepository(impl: RoomTrophyRepository): TrophyRepository
}
