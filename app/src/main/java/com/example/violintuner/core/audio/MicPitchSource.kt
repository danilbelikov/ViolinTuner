package com.example.violintuner.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.violintuner.core.audio.dsp.PitchDetector
import com.example.violintuner.core.di.IoDispatcher
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.PitchFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
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
    private val config: IntonationConfig,
    private val detectorProvider: Provider<PitchDetector>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PitchSource {

    override val requiresMicPermission: Boolean = true

    override val frames: Flow<PitchFrame> = flow {
        val (recorder, sampleRateHz) = openRecorder()
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw unavailable("AudioRecord did not start, the microphone may be in use")
            }
            val analyzer = FrameAnalyzer(detectorProvider.get(), config, sampleRateHz)
            val hop = ShortArray(config.hopSizeSamples)
            while (coroutineContext.isActive) {
                val read = recorder.read(hop, 0, hop.size, AudioRecord.READ_BLOCKING)
                if (read < 0) throw unavailable("AudioRecord.read failed with code $read")
                analyzer.push(hop, read)?.let { emit(it) }
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }.flowOn(ioDispatcher)

    /** First recorder that initializes, trying the native sample rate before the others. */
    // The permission is checked by the caller: see PitchSource.requiresMicPermission.
    @SuppressLint("MissingPermission")
    private fun openRecorder(): Pair<AudioRecord, Int> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val source = if (audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val nativeRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        val rates = (listOfNotNull(nativeRate) + config.supportedSampleRatesHz)
            .filter { it in config.supportedSampleRatesHz }
            .distinct()
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
        throw unavailable("AudioRecord could not be initialized at any of $rates Hz")
    }

    /** Logged here because the view model turns the exception into a screen state and retries. */
    private fun unavailable(message: String): MicUnavailableException {
        Log.w(TAG, message)
        return MicUnavailableException(message)
    }

    private companion object {
        const val TAG = "MicPitchSource"
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BUFFERED_HOPS = 8
    }
}
