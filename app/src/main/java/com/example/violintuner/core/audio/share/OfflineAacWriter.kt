package com.example.violintuner.core.audio.share

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/**
 * PCM into an `.m4a`, as fast as the codec goes and on the caller's thread. The recording
 * encoder ([com.example.violintuner.core.audio.recording.AacFileEncoder]) is built the other way
 * round — it must never make the microphone wait and gives up when it falls behind; a file being
 * rendered has all the time it needs and must not lose a sample. Same codec, same container.
 * Every failure of `MediaCodec` or `MediaMuxer` surfaces as a runtime exception from [write] or
 * [finish]; [abort] never throws.
 */
internal class OfflineAacWriter(file: File, private val sampleRate: Int, bitRate: Int) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
        }
        // Without the flag `configure` fails — and only on a device (see CLAUDE.md, «Сессии»).
        configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        start()
    }
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxing = false
    private var samplesIn = 0L
    private var released = false

    fun write(samples: ShortArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                val buffer = codec.getInputBuffer(index) ?: throw IllegalStateException("no codec input buffer")
                buffer.clear()
                val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                val portion = minOf(count - offset, shorts.remaining())
                shorts.put(samples, offset, portion)
                codec.queueInputBuffer(index, 0, portion * BYTES_PER_SAMPLE, samplesIn * MICROS / sampleRate, 0)
                samplesIn += portion
                offset += portion
            }
            drain(untilEnd = false)
        }
    }

    /** Flushes the codec and closes the file. */
    fun finish() {
        while (true) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, samplesIn * MICROS / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            }
            drain(untilEnd = false)
        }
        drain(untilEnd = true)
        release(stopMuxer = true)
    }

    /** Lets go of the codec and the file handle after a failure or a cancellation; the half-written file is the caller's to delete. */
    fun abort() {
        runCatching { release(stopMuxer = muxing) }
    }

    private fun drain(untilEnd: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (untilEnd) TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                }
                index >= 0 -> {
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (info.size > 0 && muxing && !isConfig) {
                        muxer.writeSampleData(track, codec.getOutputBuffer(index) ?: throw IllegalStateException("no codec output buffer"), info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                !untilEnd -> return
            }
        }
    }

    private fun release(stopMuxer: Boolean) {
        if (released) return
        released = true
        try {
            if (stopMuxer) muxer.stop()
        } finally {
            runCatching { muxer.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private companion object {
        const val MAX_INPUT_BYTES = 16_384
        const val BYTES_PER_SAMPLE = 2
        const val MICROS = 1_000_000L
        const val TIMEOUT_US = 10_000L
    }
}
