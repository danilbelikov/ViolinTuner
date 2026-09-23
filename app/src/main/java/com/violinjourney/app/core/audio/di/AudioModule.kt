package com.violinjourney.app.core.audio.di

import com.violinjourney.app.BuildConfig
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.audio.AndroidRecordingRate
import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.MicPitchSource
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.RecordingRate
import com.violinjourney.app.core.audio.dsp.MpmDetector
import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.playback.AppSessionWaveforms
import com.violinjourney.app.core.audio.playback.SessionWaveforms
import com.violinjourney.app.core.audio.recording.AacFileEncoder
import com.violinjourney.app.core.audio.recording.AppSessionAudioFiles
import com.violinjourney.app.core.audio.recording.PcmEncoderFactory
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.recording.TakePipeline
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
    fun providePcmEncoderFactory(analytics: Analytics): PcmEncoderFactory =
        PcmEncoderFactory { file, rate -> AacFileEncoder(file, rate, analytics) }

    @Provides
    fun provideSessionAudioFiles(impl: AppSessionAudioFiles): SessionAudioFiles = impl

    @Provides
    fun provideVideoFiles(impl: com.violinjourney.app.core.recording.video.AppVideoFiles): com.violinjourney.app.core.recording.video.VideoFiles = impl

    @Provides
    fun provideFileTakeAnalyzer(impl: com.violinjourney.app.core.recording.DecodingFileTakeAnalyzer): com.violinjourney.app.core.recording.FileTakeAnalyzer = impl

    @Provides
    fun provideSessionWaveforms(impl: AppSessionWaveforms): SessionWaveforms = impl

    @Provides
    fun providePitchDetectorFactory(): PitchDetectorFactory = PitchDetectorFactory(::MpmDetector)
}
