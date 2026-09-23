package com.violinjourney.app.core.data.di

import android.content.Context
import androidx.room.Room
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.DatabaseMigrations
import com.violinjourney.app.core.data.backing.BackingDao
import com.violinjourney.app.core.data.practice.PieceBlockDao
import com.violinjourney.app.core.data.practice.PracticeDao
import com.violinjourney.app.core.data.progress.TrophyDao
import com.violinjourney.app.core.data.repertoire.RepertoireDao
import com.violinjourney.app.core.data.session.SessionDao
import com.violinjourney.app.core.data.sound.SoundDao
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

    @Provides
    fun providePieceBlockDao(database: AppDatabase): PieceBlockDao = database.pieceBlockDao()

    @Provides
    fun provideBackingDao(database: AppDatabase): BackingDao = database.backingDao()

    @Provides
    fun provideTrophyDao(database: AppDatabase): TrophyDao = database.trophyDao()

    @Provides
    fun provideRepertoireDao(database: AppDatabase): RepertoireDao = database.repertoireDao()

    @Provides
    fun provideSoundDao(database: AppDatabase): SoundDao = database.soundDao()
}
