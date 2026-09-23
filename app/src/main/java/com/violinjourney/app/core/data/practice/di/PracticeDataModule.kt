package com.violinjourney.app.core.data.practice.di

import com.violinjourney.app.core.data.practice.RoomPieceBlockRepository
import com.violinjourney.app.core.data.practice.RoomPracticeRepository
import com.violinjourney.app.core.domain.practice.PieceBlockRepository
import com.violinjourney.app.core.domain.practice.PracticeRepository
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
