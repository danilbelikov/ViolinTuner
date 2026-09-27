package com.violinjourney.app.core.recording.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

/**
 * The one splice of a picture and a sound into an `.mp4`, nothing re-encoded. The take of the app's camera (spec 3.32,
 * 5.25): the sound stays at zero — the analysis and the backing's shift are counted from its first sample — and the
 * picture is moved by how much later it began; frames from before the sound (a camera quicker than the microphone) are
 * left out up to the first key frame. The video sent by «Поделиться» (spec 3.17, 3.19): the picture of the take as it
 * was, beside its rendered sound.
 */
object VideoMuxer {
    /** How far the picture is to be moved, in µs: its start minus the sound's, both on `CLOCK_MONOTONIC`. */
    fun shiftUs(pictureStartNanos: Long, soundStartNanos: Long): Long = VideoShift.shiftUs(pictureStartNanos, soundStartNanos)

    /** Where a picture sample at [ptsUs] lands after the shift; null — before the sound, left out. */
    fun shiftedUs(ptsUs: Long, shiftUs: Long): Long? = VideoShift.shiftedUs(ptsUs, shiftUs)

    /** The take of the app's camera: [splice] with the picture moved by [shiftUs]. */
    fun mux(picture: File, sound: File, target: File, shiftUs: Long): Boolean = splice(picture, sound, target, shiftUs)

    /**
     * The picture track of [picture], moved by [pictureShiftUs], and the sound track of [sound] as it is, into [target].
     * True when the whole thing worked; [target] is whole then, and gone otherwise. [onProgress] follows the picture, when
     * its length is known. [keepGoing] is asked before every sample — the render of «Поделиться» throws its cancellation
     * from there, and it leaves this as it came: a given-up share is not a failed one.
     */
    fun splice(
        picture: File,
        sound: File,
        target: File,
        pictureShiftUs: Long,
        onProgress: (Float) -> Unit = {},
        keepGoing: () -> Unit = {},
    ): Boolean {
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
            // the turn of the camera lives in the container, not in the samples
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
            val toVideo = muxer.addTrack(videoFormat)
            val toAudio = muxer.addTrack(audio.getTrackFormat(audioTrack))
            muxer.start()
            started = true

            val maxInput = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val buffer = ByteBuffer.allocate(maxOf(maxInput, SAMPLE_BUFFER))
            val info = MediaCodec.BufferInfo()
            // a file that does not say how long it is gets no progress, rather than a fall
            val durationUs = if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) videoFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1) else null
            var videoLeft = true
            var audioLeft = true
            var keyFrameSeen = false
            // interleaved by time, as a player reads it: whichever track is behind goes next
            while (videoLeft || audioLeft) {
                keepGoing()
                val videoTime = if (videoLeft) video.sampleTime + pictureShiftUs else Long.MAX_VALUE
                val fromVideo = videoLeft && (!audioLeft || videoTime <= audio.sampleTime)
                val from = if (fromVideo) video else audio
                val size = from.readSampleData(buffer, 0)
                if (size < 0) {
                    if (fromVideo) videoLeft = false else audioLeft = false
                    continue
                }
                val key = from.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                if (fromVideo) {
                    val at = shiftedUs(from.sampleTime, pictureShiftUs)
                    if (at != null && (keyFrameSeen || key)) {
                        keyFrameSeen = true
                        info.set(0, size, at, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        muxer.writeSampleData(toVideo, buffer, info)
                    }
                    durationUs?.let { onProgress((from.sampleTime.toFloat() / it).coerceIn(0f, 1f)) }
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
        } catch (e: CancellationException) {
            // before the catches below: a cancellation is an IllegalStateException too, and it is not a failure
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "cannot read the picture or the sound", e)
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
    private const val SAMPLE_BUFFER = 2 * 1024 * 1024
}
