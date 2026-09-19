package com.example.violintuner.core.audio.playback

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/**
 * A recorded session as a stream of mono PCM: `MediaExtractor` + a `MediaCodec` decoder. What
 * `MediaPlayer` did inside itself, taken apart — the samples are needed in hand to run them
 * through the sound chain, for the speaker and for the file alike. Not thread-safe: one thread
 * opens, reads, seeks and releases.
 */
class PcmDecoder private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    val sampleRate: Int,
    val durationUs: Long,
) {
    private val info = MediaCodec.BufferInfo()
    private var channels = 1
    private var inputDone = false
    private var outputDone = false

    /** Output buffer that was handed out only in part; the rest goes to the next [read]. */
    private var heldIndex = -1
    private var heldSamples: java.nio.ShortBuffer? = null

    /** After a seek the codec starts at a frame boundary before the target: this many mono samples are dropped to land on it. */
    private var toSkip = 0L
    private var seekTargetUs = -1L

    val totalSamples: Long get() = durationUs * sampleRate / MICROS

    /** Fills [out] with up to its size of mono samples; returns how many, or -1 once the recording has ended. */
    fun read(out: ShortArray): Int {
        var written = 0
        while (written < out.size) {
            val held = heldSamples
            if (held != null) {
                written += drain(held, out, written)
                if (!held.hasRemaining() || held.remaining() < channels) releaseHeld()
                continue
            }
            if (outputDone) break
            feed()
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                index >= 0 -> {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (info.size > 0) {
                        if (seekTargetUs >= 0) {
                            toSkip = ((seekTargetUs - info.presentationTimeUs).coerceAtLeast(0) * sampleRate / MICROS)
                            seekTargetUs = -1
                        }
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer == null) {
                            codec.releaseOutputBuffer(index, false)
                        } else {
                            buffer.position(info.offset).limit(info.offset + info.size)
                            heldSamples = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                            heldIndex = index
                        }
                    } else {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
        }
        return if (written == 0 && outputDone) END else written
    }

    /** The next [read] starts at [positionUs] of the recording, to the sample. */
    fun seekTo(positionUs: Long) {
        releaseHeld()
        val target = positionUs.coerceIn(0, durationUs)
        extractor.seekTo(target, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        inputDone = false
        outputDone = false
        toSkip = 0
        seekTargetUs = target
    }

    fun release() {
        releaseHeld()
        runCatching { codec.stop() }.onFailure { Log.w(TAG, "codec did not stop cleanly", it) }
        codec.release()
        extractor.release()
    }

    /**
     * Gives the codec everything it will take right now. One buffer a call, followed by a wait for
     * output, held decoding down to about the speed of the sound itself: the player ran dry and a
     * file took as long to render as to play. A codec with a full input never waits for nothing.
     */
    private fun feed() {
        while (!inputDone) {
            val index = codec.dequeueInputBuffer(0)
            if (index < 0) return
            val buffer = codec.getInputBuffer(index) ?: return
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                extractor.advance()
            }
        }
    }

    /** Moves samples from the codec's buffer to [out], folding channels into one and dropping what a seek asked to skip. */
    private fun drain(from: java.nio.ShortBuffer, out: ShortArray, offset: Int): Int {
        var written = 0
        while (offset + written < out.size && from.remaining() >= channels) {
            var sum = 0
            repeat(channels) { sum += from.get() }
            if (toSkip > 0) {
                toSkip--
            } else {
                out[offset + written++] = (sum / channels).toShort()
            }
        }
        return written
    }

    private fun releaseHeld() {
        if (heldIndex >= 0) codec.releaseOutputBuffer(heldIndex, false)
        heldIndex = -1
        heldSamples = null
    }

    companion object {
        const val END = -1
        private const val TAG = "PcmDecoder"
        private const val MICROS = 1_000_000L
        private const val TIMEOUT_US = 10_000L

        /** Null when the file cannot be opened or holds no sound this device can decode. */
        fun open(file: File): PcmDecoder? {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(file.absolutePath)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw IOException("no audio track")
                val format = extractor.getTrackFormat(track)
                extractor.selectTrack(track)
                val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
                return PcmDecoder(
                    extractor = extractor,
                    codec = codec,
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    durationUs = format.getLong(MediaFormat.KEY_DURATION),
                ).also { it.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) }
            } catch (e: IOException) {
                Log.w(TAG, "cannot open ${file.name}", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "cannot decode ${file.name}", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "decoder refused ${file.name}", e)
            }
            codec?.release()
            extractor.release()
            return null
        }
    }
}
