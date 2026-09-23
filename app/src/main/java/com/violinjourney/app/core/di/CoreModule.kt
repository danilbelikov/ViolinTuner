package com.violinjourney.app.core.di

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Dispatcher for CPU-bound work such as the intonation engine. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Dispatcher for blocking I/O such as reading the microphone. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun provideIntonationConfig(): IntonationConfig = IntonationConfig()

    @Provides
    @Singleton
    fun providePracticeConfig(): PracticeConfig = PracticeConfig()

    @Provides
    @Singleton
    fun provideProgressConfig(): ProgressConfig = ProgressConfig()

    @Provides
    @Singleton
    fun provideRepertoireConfig(): RepertoireConfig = RepertoireConfig()

    /** Wall clock for session start times and "today" in the history; tests pass a fixed one. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
