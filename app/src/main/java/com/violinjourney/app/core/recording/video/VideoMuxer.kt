package com.violinjourney.app.core.recording.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * The picture of the app's camera and the sound of the take's own chain, made into one `.mp4` (spec 3.32, 5.25). The
 * sound stays at zero — the analysis and the backing's shift are counted from its first sample — and the picture is
 * moved by how much later it began; frames from before the sound (a camera quicker than the microphone) are left out
 * up to the first key frame. Nothing is re-encoded.
 */
object VideoMuxer {
    /** How far the picture is to be moved, in µs: its start minus the sound's, both on `CLOCK_MONOTONIC`. */
    fun shiftUs(pictureStartNanos: Long, soundStartNanos: Long): Long = (pictureStartNanos - soundStartNanos) / NANOS_PER_US

    /** Where a picture sample at [ptsUs] lands after the shift; null — before the sound, left out. */
    fun shiftedUs(ptsUs: Long, shiftUs: Long): Long? = (ptsUs + shiftUs).takeIf { it >= 0 }

    /** True when the whole thing worked; [target] is whole then, and gone otherwise. */
    fun mux(picture: File, sound: File, target: File, shiftUs: Long): Boolean {
        val video = MediaExtractor()
        val audio = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        var whole = false
        try {
            video.setDataSource(picture.absolutePath)
            audio.setDataSource(sound.absolutePath)
            val videoTrack = (0 until video.trackCount).firstOrNull { video.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return false
            val audioTrack = (0 until audio.trackCount).firstOrNull { audio.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return false
            val videoFormat = video.getTrackFormat(videoTrack)
            video.selectTrack(videoTrack)
            audio.selectTrack(audioTrack)
            target.parentFile?.mkdirs()
            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
            val toVideo = muxer.addTrack(videoFormat)
            val toAudio = muxer.addTrack(audio.getTrackFormat(audioTrack))
            muxer.start()
            started = true

            val maxInput = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val buffer = ByteBuffer.allocate(maxOf(maxInput, SAMPLE_BUFFER))
            val info = MediaCodec.BufferInfo()
            var videoLeft = true
            var audioLeft = true
            var keyFrameSeen = false
            while (videoLeft || audioLeft) {
                val videoTime = if (videoLeft) video.sampleTime + shiftUs else Long.MAX_VALUE
                val fromVideo = videoLeft && (!audioLeft || videoTime <= audio.sampleTime)
                val from = if (fromVideo) video else audio
                val size = from.readSampleData(buffer, 0)
                if (size < 0) {
                    if (fromVideo) videoLeft = false else audioLeft = false
                    continue
                }
                val key = from.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                if (fromVideo) {
                    val at = shiftedUs(from.sampleTime, shiftUs)
                    if (at != null && (keyFrameSeen || key)) {
                        keyFrameSeen = true
                        info.set(0, size, at, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        muxer.writeSampleData(toVideo, buffer, info)
                    }
                } else {
                    info.set(0, size, from.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(toAudio, buffer, info)
                }
                from.advance()
            }
            muxer.stop()
            started = false
            whole = target.length() > 0
            return whole
        } catch (e: IOException) {
            Log.w(TAG, "cannot read the shot or the sound", e)
            return false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "muxing failed", e)
            return false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "muxing failed", e)
            return false
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            video.release()
            audio.release()
            if (!whole) target.delete()
        }
    }

    private const val TAG = "VideoMuxer"
    private const val NANOS_PER_US = 1_000L
    private const val SAMPLE_BUFFER = 2 * 1024 * 1024
}
