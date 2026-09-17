package com.example.violintuner.core.audio.di

import com.example.violintuner.BuildConfig
import com.example.violintuner.core.audio.FakePitchSource
import com.example.violintuner.core.audio.FakeScenario
import com.example.violintuner.core.audio.MicPitchSource
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.audio.dsp.MpmDetector
import com.example.violintuner.core.audio.dsp.PitchDetector
import com.example.violintuner.core.domain.IntonationConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    /** The microphone, or the looping demo script when built with `-PfakePitch=true`. */
    @Provides
    fun providePitchSource(config: IntonationConfig, mic: Provider<MicPitchSource>): PitchSource =
        if (BuildConfig.FAKE_PITCH_SOURCE) FakePitchSource(FakeScenario.DEMO, config) else mic.get()

    // MPM over YIN by DetectorComparisonTest: same accuracy on clean tones, slightly smaller
    // error under noise, negligible extra cost. Detectors are stateful: one per audio stream.
    @Provides
    fun providePitchDetector(config: IntonationConfig): PitchDetector = MpmDetector(config)
}
