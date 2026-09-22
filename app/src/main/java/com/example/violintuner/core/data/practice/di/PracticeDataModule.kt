package com.example.violintuner.core.data.practice.di

import com.example.violintuner.core.data.practice.RoomPieceBlockRepository
import com.example.violintuner.core.data.practice.RoomPracticeRepository
import com.example.violintuner.core.domain.practice.PieceBlockRepository
import com.example.violintuner.core.domain.practice.PracticeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PracticeDataModule {
    @Binds
    @Singleton
    abstract fun bindPracticeRepository(impl: RoomPracticeRepository): PracticeRepository

    @Binds
    @Singleton
    abstract fun bindPieceBlockRepository(impl: RoomPieceBlockRepository): PieceBlockRepository
}
