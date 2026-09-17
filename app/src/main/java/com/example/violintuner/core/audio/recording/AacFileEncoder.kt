package com.example.violintuner.core.audio.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * PCM16 mono → AAC-LC in an .m4a (spec 6). The codec runs on its own thread behind a bounded
 * queue: the microphone loop only copies a hop into the queue and never waits for the codec.
 * A full queue means the device cannot keep up; the take is then given up, not the frames.
 */
class AacFileEncoder(private val file: File, private val sampleRateHz: Int) : PcmEncoder {
    private class Chunk(val samples: ShortArray, val count: Int)

    private val queue = ArrayBlockingQueue<Chunk>(QUEUE_HOPS)

    @Volatile private var failed = false

    @Volatile private var finishing = false

    // Both are created here, on the caller's thread, so that a device without the encoder
    // fails the factory call instead of the worker.
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
        }
        configure(format, null, null, 0)
        start()
    }
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val worker = Thread(::encodeLoop, "session-audio-encoder").apply { start() }

    override fun offer(hop: ShortArray, count: Int): Boolean {
        if (failed || finishing) return false
        return queue.offer(Chunk(hop.copyOf(count), count))
    }

    override fun finish(): Boolean {
        finishing = true
        worker.join(FINISH_TIMEOUT_MS)
        if (worker.isAlive) {
            Log.w(TAG, "encoder did not finish in $FINISH_TIMEOUT_MS ms")
            failed = true
        }
        if (failed) file.delete()
        return !failed && file.length() > 0
    }

    private fun encodeLoop() {
        var track = -1
        var muxing = false
        var samplesIn = 0L
        val info = MediaCodec.BufferInfo()
        try {
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val chunk = queue.poll(POLL_MS, TimeUnit.MILLISECONDS)
                    if (chunk != null || (finishing && queue.isEmpty())) {
                        val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (index >= 0) {
                            val pts = samplesIn * MICROS_PER_SECOND / sampleRateHz
                            if (chunk == null) {
                                codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val buffer = codec.getInputBuffer(index)!!
                                buffer.clear()
                                buffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(chunk.samples, 0, chunk.count)
                                codec.queueInputBuffer(index, 0, chunk.count * BYTES_PER_SAMPLE, pts, 0)
                                samplesIn += chunk.count
                            }
                        } else if (chunk != null) {
                            // No input buffer in time: the codec is stuck. Dropping the chunk
                            // would put a hole into the sound, so the take ends here.
                            throw IllegalStateException("no codec input buffer within $CODEC_TIMEOUT_US us")
                        }
                    }
                }
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, 0)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    } else if (index >= 0) {
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (info.size > 0 && muxing && !isConfig) {
                            muxer.writeSampleData(track, codec.getOutputBuffer(index)!!, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                            break
                        }
                    } else {
                        break
                    }
                }
            }
        } catch (e: RuntimeException) {
            // MediaCodec and MediaMuxer report every failure as a runtime exception. The take
            // is lost, the session is not: it is saved without sound (spec 3.9).
            Log.w(TAG, "encoding failed, the session will have no audio", e)
            failed = true
        } finally {
            runCatching { if (muxing) muxer.stop() }.onFailure { failed = true }
            runCatching { muxer.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private companion object {
        const val TAG = "AacFileEncoder"
        const val CHANNELS = 1
        const val BIT_RATE = 64_000
        const val BYTES_PER_SAMPLE = 2
        const val MAX_INPUT_BYTES = 16_384

        /** About two seconds of hops: more than any hiccup, far less than memory would notice. */
        const val QUEUE_HOPS = 200
        const val POLL_MS = 20L
        const val CODEC_TIMEOUT_US = 500_000L
        const val FINISH_TIMEOUT_MS = 3_000L
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
