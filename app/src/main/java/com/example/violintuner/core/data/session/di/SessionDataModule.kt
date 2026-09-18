package com.example.violintuner.core.data.session.di

import com.example.violintuner.core.data.session.RoomSessionRepository
import com.example.violintuner.core.domain.session.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionDataModule {
    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository
}
