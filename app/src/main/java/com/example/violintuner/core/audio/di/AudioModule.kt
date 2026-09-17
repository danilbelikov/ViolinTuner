package com.example.violintuner.core.audio.di

import com.example.violintuner.core.audio.FakePitchSource
import com.example.violintuner.core.audio.FakeScenario
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.domain.IntonationConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    // Until MicPitchSource exists (spec step 4) the app runs on the looping demo signal.
    @Provides
    fun providePitchSource(config: IntonationConfig): PitchSource =
        FakePitchSource(FakeScenario.DEMO, config)
}
