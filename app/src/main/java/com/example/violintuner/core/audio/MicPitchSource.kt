package com.example.violintuner.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.violintuner.BuildConfig
import com.example.violintuner.core.audio.dsp.PitchDetector
import com.example.violintuner.core.di.IoDispatcher
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.PitchFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
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
            val watchdog = DigitalSilenceWatchdog(config, sampleRateHz)
            val stats = if (BuildConfig.DEBUG) FrameStats(config, sampleRateHz, recorder.audioSource) else null
            while (coroutineContext.isActive) {
                val read = recorder.read(hop, 0, hop.size, AudioRecord.READ_BLOCKING)
                if (read < 0) throw unavailable("AudioRecord.read failed with code $read")
                if (watchdog.isDead(hop, read)) throw unavailable("input is digitally silent, reopening")
                analyzer.push(hop, read)?.let { frame ->
                    stats?.add(frame)
                    emit(frame)
                }
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

    /**
     * Debug builds only: one logcat line per second with what the detector saw, for tuning the
     * silence and clarity thresholds on a real instrument (`adb logcat -s MicPitchSource`).
     */
    private class FrameStats(
        private val config: IntonationConfig,
        sampleRateHz: Int,
        audioSource: Int,
    ) {
        private val header = "src=$audioSource rate=$sampleRateHz"
        private var windowStartMs = -1L
        private var frames = 0
        private var confident = 0
        private var peakRms = 0.0
        private var peakClarity = 0.0
        private var lastConfidentHz = 0.0
        private val notes = sortedMapOf<Int, Int>()
        private var maxGapMs = 0L
        private var lastFrameMs = -1L

        fun add(frame: PitchFrame) {
            if (windowStartMs < 0) windowStartMs = frame.tMs
            if (lastFrameMs >= 0) maxGapMs = maxOf(maxGapMs, frame.tMs - lastFrameMs)
            lastFrameMs = frame.tMs
            frames++
            peakRms = maxOf(peakRms, frame.rms)
            peakClarity = maxOf(peakClarity, frame.clarity)
            val freq = frame.freqHz
            if (freq != null && frame.clarity >= config.clarityThreshold && frame.rms >= config.silenceRms) {
                confident++
                lastConfidentHz = freq
                frame.midi?.let { notes[it] = (notes[it] ?: 0) + 1 }
            }
            if (frame.tMs - windowStartMs < LOG_PERIOD_MS) return
            val peakDbfs = if (peakRms > 0) DB_PER_DECADE * log10(peakRms) else Double.NEGATIVE_INFINITY
            Log.d(
                TAG,
                "$header t=${frame.tMs / LOG_PERIOD_MS}s frames=$frames confident=$confident " +
                    "peakRms=%.1f dBFS peakClarity=%.2f lastHz=%.1f".format(peakDbfs, peakClarity, lastConfidentHz) +
                    " midi=$notes maxStepMs=$maxGapMs",
            )
            notes.clear()
            maxGapMs = 0
            windowStartMs = frame.tMs
            frames = 0
            confident = 0
            peakRms = 0.0
            peakClarity = 0.0
        }

        private companion object {
            const val LOG_PERIOD_MS = 1_000L
            const val DB_PER_DECADE = 20.0
        }
    }

    private companion object {
        const val TAG = "MicPitchSource"
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BUFFERED_HOPS = 8
    }
}
