package com.example.violintuner.core.data.progress.di

import com.example.violintuner.core.data.progress.RoomTrophyRepository
import com.example.violintuner.core.domain.progress.TrophyRepository
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
