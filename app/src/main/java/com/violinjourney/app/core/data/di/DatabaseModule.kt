package com.violinjourney.app.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.DatabaseMigrations
import com.violinjourney.app.core.data.backing.BackingDao
import com.violinjourney.app.core.data.events.EventDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = appDatabaseBuilder(context, AppDatabase.FILE_NAME).build()

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

    @Provides
    fun provideEventDao(database: AppDatabase): EventDao = database.eventDao()
}

/**
 * How the app opens its database: [name] under `databases/`, or an absolute path — a copy unpacked beside the data is
 * opened by this same builder before it is put in place (spec 5.14), so it passes the same migrations and the same check.
 */
fun appDatabaseBuilder(context: Context, name: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL)
