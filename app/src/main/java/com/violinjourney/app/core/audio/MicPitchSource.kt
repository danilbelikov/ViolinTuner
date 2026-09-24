package com.violinjourney.app.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.violinjourney.app.BuildConfig
import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.audio.recording.HopAudioTap
import com.violinjourney.app.core.audio.recording.PcmEncoderFactory
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Microphone input (spec 6): AudioRecord, mono PCM16, UNPROCESSED source when the device has
 * one. Reading and pitch detection run on the I/O dispatcher; each collection opens its own
 * recorder and releases it when the collector is cancelled.
 */
class MicPitchSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectorFactory: PitchDetectorFactory,
    encoderFactory: PcmEncoderFactory,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PitchSource {

    override val requiresMicPermission: Boolean = true

    private val tap = HopAudioTap(encoderFactory, ioDispatcher)
    override val audioTap: AudioTap get() = tap

    /** The last word of the input on its time: a frame it had captured, and when (spec 5.25). */
    private class Anchor(val frame: Long, val nanos: Long, val rate: Int)

    @Volatile private var anchor: Anchor? = null

    override val clock: SampleClock = SampleClock { tMs ->
        anchor?.let { at -> at.nanos + ((tMs * at.rate / MS_PER_SECOND) - at.frame) * NANOS_PER_SECOND / at.rate }
    }

    override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
        val (recorder, sampleRateHz) = openRecorder(config)
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw unavailable(MicUnavailableReason.OPEN_FAILED, "AudioRecord did not start, the microphone may be in use")
            }
            val analyzer = FrameAnalyzer(detectorFactory.create(config), config, sampleRateHz)
            val hop = ShortArray(config.hopSizeSamples)
            val watchdog = DigitalSilenceWatchdog(config, sampleRateHz)
            val stats = if (BuildConfig.DEBUG) FrameStats(config, "src=${recorder.audioSource} rate=$sampleRateHz") { Log.d(TAG, it) } else null
            var samplesRead = 0L
            val timestamp = AudioTimestamp()
            anchor = null
            while (coroutineContext.isActive) {
                val read = recorder.read(hop, 0, hop.size, AudioRecord.READ_BLOCKING)
                if (read < 0) throw unavailable(MicUnavailableReason.READ_FAILED, "AudioRecord.read failed with code $read")
                if (watchdog.isDead(hop, read)) throw unavailable(MicUnavailableReason.DIGITAL_SILENCE, "input is digitally silent, reopening")
                // Same sample clock as FrameAnalyzer: a frame's tMs is the end of its hop, so the
                // hop that follows a frame starts exactly at that frame's time.
                tap.onHop(hop, read, hopStartTMs = samplesRead * MS_PER_SECOND / sampleRateHz, sampleRateHz)
                samplesRead += read
                // counted from the same start as the samples read: frame N of the input is sample N of the take
                if (recorder.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                    anchor = Anchor(timestamp.framePosition, timestamp.nanoTime, sampleRateHz)
                }
                analyzer.push(hop, read)?.let { frame ->
                    stats?.add(frame)
                    emit(frame)
                }
            }
        } finally {
            tap.onStreamEnded()
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }.flowOn(ioDispatcher)

    /** First recorder that initializes, trying the native sample rate before the others. */
    // The permission is checked by the caller: see PitchSource.requiresMicPermission.
    @SuppressLint("MissingPermission")
    private fun openRecorder(config: IntonationConfig): Pair<AudioRecord, Int> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val source = if (audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        val rates = SampleRates.candidates(nativeRate, config.supportedSampleRatesHz)
        for (rate in rates) {
            val minBytes = AudioRecord.getMinBufferSize(rate, CHANNEL, ENCODING)
            if (minBytes <= 0) continue
            val bufferBytes = maxOf(minBytes, config.hopSizeSamples * BUFFERED_HOPS * BYTES_PER_SAMPLE)
            val recorder = try {
                AudioRecord(source, rate, CHANNEL, ENCODING, bufferBytes)
            } catch (e: IllegalArgumentException) {
                // this rate / source combination is not supported here: try the next one
                continue
            }
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder to rate
            recorder.release()
        }
        throw unavailable(MicUnavailableReason.OPEN_FAILED, "AudioRecord could not be initialized at any of $rates Hz")
    }

    /** Logged here because the view model turns the exception into a screen state and retries. */
    private fun unavailable(reason: MicUnavailableReason, message: String): MicUnavailableException {
        Log.w(TAG, message)
        return MicUnavailableException(reason, message)
    }

    private companion object {
        const val TAG = "MicPitchSource"
        const val MS_PER_SECOND = 1_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BUFFERED_HOPS = 8
    }
}
