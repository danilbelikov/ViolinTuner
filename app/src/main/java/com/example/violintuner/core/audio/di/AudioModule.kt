package com.example.violintuner.core.audio.di

import com.example.violintuner.BuildConfig
import com.example.violintuner.core.audio.AndroidRecordingRate
import com.example.violintuner.core.audio.FakePitchSource
import com.example.violintuner.core.audio.FakeScenario
import com.example.violintuner.core.audio.MicPitchSource
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.audio.RecordingRate
import com.example.violintuner.core.audio.dsp.MpmDetector
import com.example.violintuner.core.audio.dsp.PitchDetectorFactory
import com.example.violintuner.core.audio.playback.AppSessionWaveforms
import com.example.violintuner.core.audio.playback.SessionWaveforms
import com.example.violintuner.core.audio.recording.AacFileEncoder
import com.example.violintuner.core.audio.recording.AppSessionAudioFiles
import com.example.violintuner.core.audio.recording.PcmEncoderFactory
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.recording.TakePipeline
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
    fun providePitchSource(mic: Provider<MicPitchSource>): PitchSource =
        if (BuildConfig.FAKE_PITCH_SOURCE) FakePitchSource(FakeScenario.DEMO) else mic.get()

    /** The fake source has no microphone: its takes are mixed at the rate the take pipeline assumes then. */
    @Provides
    fun provideRecordingRate(real: Provider<AndroidRecordingRate>): RecordingRate =
        if (BuildConfig.FAKE_PITCH_SOURCE) RecordingRate { TakePipeline.DEFAULT_RATE } else real.get()

    // MPM over YIN by DetectorComparisonTest: same accuracy on clean tones, slightly smaller
    // error under noise, negligible extra cost.
    @Provides
    fun providePcmEncoderFactory(): PcmEncoderFactory = PcmEncoderFactory(::AacFileEncoder)

    @Provides
    fun provideSessionAudioFiles(impl: AppSessionAudioFiles): SessionAudioFiles = impl

    @Provides
    fun provideVideoFiles(impl: com.example.violintuner.core.recording.video.AppVideoFiles): com.example.violintuner.core.recording.video.VideoFiles = impl

    @Provides
    fun provideFileTakeAnalyzer(impl: com.example.violintuner.core.recording.DecodingFileTakeAnalyzer): com.example.violintuner.core.recording.FileTakeAnalyzer = impl

    @Provides
    fun provideSessionWaveforms(impl: AppSessionWaveforms): SessionWaveforms = impl

    @Provides
    fun providePitchDetectorFactory(): PitchDetectorFactory = PitchDetectorFactory(::MpmDetector)
}
