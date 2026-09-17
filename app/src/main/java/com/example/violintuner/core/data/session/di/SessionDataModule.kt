package com.example.violintuner.core.data.session.di

import android.content.Context
import androidx.room.Room
import com.example.violintuner.core.data.session.AppDatabase
import com.example.violintuner.core.data.session.RoomSessionRepository
import com.example.violintuner.core.data.session.SessionDao
import com.example.violintuner.core.domain.session.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionDataModule {
    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.FILE_NAME).build()

        @Provides
        fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()
    }
}
