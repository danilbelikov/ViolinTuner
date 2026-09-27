package com.violinjourney.app.testing

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sound files of the kinds a backing comes in, made on the device for the tests of the decoder: a WAV of any width
 * written by hand, and an HE-AAC `.m4a` from the device's own encoder. Each is a tone of [hz] at a quarter of full scale.
 */
object TestSound {
    private const val LEVEL = 0.25
    private const val TIMEOUT_US = 10_000L
    private const val FRAMES_PER_INPUT = 1_024
    private const val HE_BIT_RATE = 48_000
    private const val WAVE_FORMAT_PCM = 1
    private const val WAVE_FORMAT_IEEE_FLOAT = 3
    private const val MICROS = 1_000_000L

    private fun sample(n: Long, rate: Int, hz: Double) = sin(2 * PI * hz * n / rate) * LEVEL

    /** A RIFF WAV of [bits] per sample — integers, or 32-bit floats when [float] — with the same tone on every channel. */
    fun wav(file: File, rate: Int, channels: Int, bits: Int, float: Boolean = false, seconds: Double, hz: Double = 440.0): File {
        val frames = (rate * seconds).toInt()
        val bytesPerSample = bits / 8
        val data = frames * channels * bytesPerSample
        val out = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        out.put("fmt ".toByteArray()).putInt(16)
        out.putShort((if (float) WAVE_FORMAT_IEEE_FLOAT else WAVE_FORMAT_PCM).toShort()).putShort(channels.toShort())
        out.putInt(rate).putInt(rate * channels * bytesPerSample).putShort((channels * bytesPerSample).toShort()).putShort(bits.toShort())
        out.put("data".toByteArray()).putInt(data)
        for (i in 0 until frames) {
            val value = sample(i.toLong(), rate, hz)
            repeat(channels) {
                when {
                    float -> out.putFloat(value.toFloat())
                    bits == 16 -> out.putShort((value * Short.MAX_VALUE).roundToInt().toShort())
                    bits == 24 -> {
                        val v = (value * 8_388_607).roundToInt()
                        out.put(v.toByte()).put((v shr 8).toByte()).put((v shr 16).toByte())
                    }
                    bits == 32 -> out.putInt((value * Int.MAX_VALUE).roundToInt())
                    else -> error("no $bits-bit WAV here")
                }
            }
        }
        file.writeBytes(out.array())
        return file
    }

    /** An HE-AAC (SBR) mono `.m4a` by the device's encoder; null when the device has none that takes it. */
    fun heAac(file: File, rate: Int, seconds: Int, hz: Double = 440.0): File? {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE)
            setInteger(MediaFormat.KEY_BIT_RATE, HE_BIT_RATE)
        }
        val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) ?: return null
        val codec = MediaCodec.createByCodecName(name)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: IllegalArgumentException) {
            codec.release()
            return null
        } catch (e: IllegalStateException) {
            codec.release()
            return null
        }
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val info = MediaCodec.BufferInfo()
        val total = rate.toLong() * seconds
        var fed = 0L
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = checkNotNull(codec.getInputBuffer(index)).order(ByteOrder.nativeOrder())
                    buffer.clear()
                    val frames = minOf(buffer.capacity() / 2, FRAMES_PER_INPUT, (total - fed).toInt())
                    if (frames <= 0) {
                        codec.queueInputBuffer(index, 0, 0, fed * MICROS / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        for (i in 0 until frames) buffer.putShort((sample(fed + i, rate, hz) * Short.MAX_VALUE).roundToInt().toShort())
                        codec.queueInputBuffer(index, 0, frames * 2, fed * MICROS / rate, 0)
                        fed += frames
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                index >= 0 -> {
                    val buffer = checkNotNull(codec.getOutputBuffer(index))
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!config && info.size > 0 && track >= 0) {
                        buffer.position(info.offset).limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
        return file
    }

    /** Pitch by zero crossings over [count] samples at [rate] — crude, and plenty to tell a tone from noise. */
    fun pitchOf(samples: FloatArray, count: Int, rate: Int): Double {
        var crossings = 0
        for (i in 1 until count) if ((samples[i - 1] < 0) != (samples[i] < 0)) crossings++
        return crossings / 2.0 * rate / count
    }
}
