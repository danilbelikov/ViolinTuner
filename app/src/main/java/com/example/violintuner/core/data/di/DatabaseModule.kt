package com.example.violintuner.core.data.di

import android.content.Context
import androidx.room.Room
import com.example.violintuner.core.data.AppDatabase
import com.example.violintuner.core.data.DatabaseMigrations
import com.example.violintuner.core.data.practice.PracticeDao
import com.example.violintuner.core.data.session.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.FILE_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

    @Provides
    fun providePracticeDao(database: AppDatabase): PracticeDao = database.practiceDao()
}
