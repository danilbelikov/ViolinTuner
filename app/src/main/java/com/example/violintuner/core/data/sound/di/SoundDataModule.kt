package com.example.violintuner.core.data.sound.di

import com.example.violintuner.core.data.sound.RoomSoundRepository
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SoundDataModule {
    @Binds
    @Singleton
    abstract fun bindSoundRepository(impl: RoomSoundRepository): SoundRepository

    companion object {
        /** The numbers of spec 5.11. Nothing here is the user's to change, so one instance serves everyone. */
        @Provides
        @Singleton
        fun provideSoundConfig(): SoundConfig = SoundConfig()
    }
}
