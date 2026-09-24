package com.violinjourney.app.core.data.practice.di

import com.violinjourney.app.core.data.practice.PieceBlockDao
import com.violinjourney.app.core.data.practice.PracticeDao
import com.violinjourney.app.core.data.practice.RoomPieceBlockRepository
import com.violinjourney.app.core.data.practice.RoomPracticeRepository
import com.violinjourney.app.core.domain.practice.PieceBlockRepository
import com.violinjourney.app.core.domain.practice.PracticeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PracticeDataModule {
    @Provides
    @Singleton
    fun providePracticeRepository(dao: PracticeDao): PracticeRepository = RoomPracticeRepository(dao)

    @Provides
    @Singleton
    fun providePieceBlockRepository(dao: PieceBlockDao): PieceBlockRepository = RoomPieceBlockRepository(dao)
}
