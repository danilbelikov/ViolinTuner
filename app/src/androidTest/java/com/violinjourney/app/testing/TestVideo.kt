package com.violinjourney.app.testing

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.violinjourney.app.core.audio.recording.AacFileEncoder
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue

/**
 * A real `.mp4` made on the device for the tests of video takes: a picture whose brightness says
 * which frame it is ([lumaOf]) beside a sound made by our own AAC encoder — a tone whose pitch
 * says where in the file one is.
 */
object TestVideo {
    const val WIDTH = 320
    const val HEIGHT = 240
    const val FPS = 15
    const val SAMPLE_RATE = 48_000
    private const val HOP = 512
    private const val TIMEOUT_US = 10_000L
    private const val LUMA_STEP = 3
    private const val LUMA_FROM = 40

    /** Brightness of frame [index]: steps up through the mid greys and wraps. */
    fun lumaOf(index: Int): Int = LUMA_FROM + (index * LUMA_STEP) % 150

    /** [hzAt] is the pitch of the tone at a second of the file; null is silence. [withSound] false makes a mute video. */
    fun make(file: File, seconds: Int, withSound: Boolean = true, rotation: Int = 0, hzAt: (Double) -> Double? = { 440.0 }): File {
        val audio = if (withSound) encodeSound(File(file.parentFile, file.name + ".m4a"), seconds, hzAt) else null
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.setOrientationHint(rotation)
        val extractor = audio?.let { MediaExtractor().apply { setDataSource(it.absolutePath); selectTrack(0) } }
        val audioTrack = extractor?.let { muxer.addTrack(it.getTrackFormat(0)) }

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var videoTrack = -1
        var started = false
        var frame = 0
        val frames = seconds * FPS
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    if (frame == frames) {
                        codec.queueInputBuffer(index, 0, 0, frame * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val image = checkNotNull(codec.getInputImage(index))
                        image.planes.forEachIndexed { plane, p -> fill(p.buffer, if (plane == 0) lumaOf(frame) else 128) }
                        val size = WIDTH * HEIGHT * 3 / 2
                        codec.queueInputBuffer(index, 0, size, frame * 1_000_000L / FPS, 0)
                        frame++
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    videoTrack = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }
                out >= 0 -> {
                    val buffer = checkNotNull(codec.getOutputBuffer(out))
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && started) {
                        muxer.writeSampleData(videoTrack, buffer, info)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    codec.releaseOutputBuffer(out, false)
                }
            }
        }
        codec.stop()
        codec.release()

        if (extractor != null && audioTrack != null) {
            val buffer = ByteBuffer.allocate(256 * 1024)
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(audioTrack, buffer, info)
                extractor.advance()
            }
            extractor.release()
        }
        muxer.stop()
        muxer.release()
        audio?.delete()
        return file
    }

    private fun fill(buffer: ByteBuffer, value: Int) {
        val byte = value.toByte()
        buffer.position(0)
        while (buffer.hasRemaining()) buffer.put(byte)
    }

    private fun encodeSound(file: File, seconds: Int, hzAt: (Double) -> Double?): File {
        val encoder = AacFileEncoder(file, SAMPLE_RATE)
        val hop = ShortArray(HOP)
        var phase = 0.0
        var sample = 0L
        val total = seconds.toLong() * SAMPLE_RATE
        while (sample < total) {
            for (i in hop.indices) {
                val hz = hzAt(sample.toDouble() / SAMPLE_RATE)
                if (hz == null) {
                    hop[i] = 0
                } else {
                    phase += 2 * PI * hz / SAMPLE_RATE
                    hop[i] = (sin(phase) * 0.4 * Short.MAX_VALUE).toInt().toShort()
                }
                sample++
            }
            // the encoder of a recording gives up when it cannot keep up with a microphone; a test is not one
            while (!encoder.offer(hop, hop.size)) Thread.sleep(2)
        }
        assertTrue(encoder.finish())
        return file
    }
}
