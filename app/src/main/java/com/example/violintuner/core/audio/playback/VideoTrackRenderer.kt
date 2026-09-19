package com.example.violintuner.core.audio.playback

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoState(
    /** As it is seen, the turn of the camera applied; zero until the file has been looked into. */
    val width: Int = 0,
    val height: Int = 0,
    /** There is a frame on the surface: the placeholder may go. */
    val showing: Boolean = false,
    /** No picture this device can decode. The sound and the analysis do not depend on it (spec 3.19). */
    val failed: Boolean = false,
)

/**
 * The picture of a video take (spec 3.19, 5.13): `MediaExtractor` + a `MediaCodec` decoder onto a
 * [Surface], led by the clock of the sound. It plays nothing by itself — [follow] tells it where
 * the player is and whether it moves, and a frame is shown when the sound has come to its time.
 * The picture follows the sound, never the other way round: a late frame is dropped, the sound
 * does not wait. Between two words of the player the position is carried on by the monotonic clock.
 *
 * Calls only leave wishes under a lock; a thread of its own decodes. Without a surface there is
 * no decoder — a surface that comes back (a rotation) gets a new one at the same place.
 */
/** The picture of one video take, as the screen's view model sees it; the real one is [VideoTrackRenderer]. */
interface VideoPicture {
    val state: StateFlow<VideoState>

    /** Null takes the picture off the surface that is about to go. */
    fun setSurface(next: Surface?)

    /** Where the sound is, and whether it moves. */
    fun follow(positionMs: Long, playing: Boolean)

    fun release()
}

fun interface VideoPictureFactory {
    fun create(file: File): VideoPicture
}

class VideoTrackRenderer(
    private val file: File,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
    /**
     * For the instrumented test only: the emulator's hardware decoder draws onto a view but not
     * into an `ImageReader`, which is where a test reads its frames from.
     */
    private val softwareDecoder: Boolean = false,
) : VideoPicture {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    // Wishes, under the lock.
    private var surface: Surface? = null
    private var surfaceSerial = 0
    private var positionMs = 0L
    private var positionAtMs = 0L
    private var playing = false
    private var released = false

    private val mutableState = MutableStateFlow(VideoState())
    override val state: StateFlow<VideoState> = mutableState.asStateFlow()

    private val thread = Thread(::run, "video-track").apply { start() }

    /** Null takes the picture off: the surface is about to go. Blocks until the decoder has let go of it. */
    override fun setSurface(next: Surface?): Unit = lock.withLock {
        surface = next
        surfaceSerial++
        changed.signalAll()
        if (next == null) {
            val serial = surfaceSerial
            // a surface destroyed under a running codec crashes it: wait, briefly, until the thread has dropped it
            var waited = 0L
            while (boundSerial != IDLE && boundSerial < serial && !released && waited < SURFACE_WAIT_MS) {
                changed.awaitNanos(WAIT_STEP_MS * NANOS_PER_MS)
                waited += WAIT_STEP_MS
            }
        }
    }

    /** Where the sound is, and whether it moves. Cheap; meant to be called on every word of the player. */
    override fun follow(positionMs: Long, playing: Boolean): Unit = lock.withLock {
        this.positionMs = positionMs
        this.positionAtMs = nowMs()
        this.playing = playing
        changed.signalAll()
    }

    override fun release() {
        lock.withLock {
            released = true
            changed.signalAll()
        }
        thread.join(RELEASE_WAIT_MS)
    }

    /** Serial of the surface the decoder holds now; [IDLE] while it holds none. Under the lock. */
    private var boundSerial = IDLE

    private fun targetUs(): Long = lock.withLock {
        val carried = if (playing) nowMs() - positionAtMs else 0
        (positionMs + carried.coerceIn(0, MAX_CARRY_MS)) * MICROS_PER_MS
    }

    private fun run() {
        while (true) {
            val (target, serial) = lock.withLock {
                while (surface == null && !released) changed.await()
                if (released) return
                surface!! to surfaceSerial
            }
            val session = open(target) ?: run {
                mutableState.value = mutableState.value.copy(failed = true)
                lock.withLock { while (surfaceSerial == serial && !released) changed.await() }
                null
            }
            if (session != null) {
                lock.withLock { boundSerial = serial }
                try {
                    session.loop(serial)
                } catch (e: IllegalStateException) {
                    // the codec died — a surface torn away, a decoder that gave up
                    Log.w(TAG, "decoder stopped", e)
                    mutableState.value = mutableState.value.copy(failed = true, showing = false)
                } finally {
                    session.close()
                    lock.withLock {
                        boundSerial = IDLE
                        changed.signalAll()
                    }
                }
                // failed for good on this surface: wait for another, or for the end
                if (mutableState.value.failed) lock.withLock { while (surfaceSerial == serial && !released) changed.await() }
            }
            if (lock.withLock { released }) return
        }
    }

    private fun open(target: Surface): Session? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IOException("no video track")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            val turned = rotation % HALF_TURN != 0
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            mutableState.value = mutableState.value.copy(width = if (turned) height else width, height = if (turned) width else height, failed = false)
            val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
            codec = softwareDecoderFor(mime)?.let(MediaCodec::createByCodecName) ?: MediaCodec.createDecoderByType(mime)
            // the decoder turns the picture itself when it draws onto a surface: the format carries the rotation
            codec.configure(format, target, null, 0)
            codec.start()
            return Session(extractor, codec)
        } catch (e: IOException) {
            Log.w(TAG, "cannot open the picture of ${file.name}", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "cannot decode the picture of ${file.name}", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "decoder refused the picture of ${file.name}", e)
        }
        codec?.release()
        extractor.release()
        return null
    }

    private fun softwareDecoderFor(mime: String): String? {
        if (!softwareDecoder) return null
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { codec ->
            !codec.isEncoder && codec.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                (codec.name.startsWith("c2.android.") || codec.name.startsWith("OMX.google."))
        }?.name
    }

    private inner class Session(private val extractor: MediaExtractor, private val codec: MediaCodec) {
        private val info = MediaCodec.BufferInfo()
        private var inputDone = false
        private var outputDone = false

        /** Time of the frame on the surface; [NOTHING] before the first. */
        private var shownUs = NOTHING

        /** A decoded frame whose time has not come yet. */
        private var heldIndex = -1
        private var heldUs = 0L

        /** After a seek: frames before this are decoded and thrown away. */
        private var dropBeforeUs = NOTHING

        fun loop(serial: Int) {
            seek(targetUs())
            while (true) {
                val (alive, moving) = lock.withLock { (!released && surfaceSerial == serial) to playing }
                if (!alive) return
                val target = targetUs()
                // The sound jumped — a drag of the slider, «Смотреть это место», the return to the start at the end.
                val jumped = shownUs != NOTHING && (target < shownUs - BACK_TOLERANCE_US || target > shownUs + FORWARD_SEEK_US)
                if (jumped) seek(target)

                if (heldIndex >= 0) {
                    val early = heldUs - target
                    when {
                        early > 0 && moving -> sleep(minOf(early / MICROS_PER_MS, MAX_NAP_MS).coerceAtLeast(1))
                        early > 0 -> if (shownUs == NOTHING) show() else await(PAUSED_NAP_MS)
                        else -> show()
                    }
                    continue
                }
                if (outputDone) {
                    await(PAUSED_NAP_MS)
                    continue
                }
                // Standing still with the right frame on the surface: nothing to decode.
                if (!moving && shownUs != NOTHING && dropBeforeUs == NOTHING) {
                    await(PAUSED_NAP_MS)
                    continue
                }
                decode(target, moving)
            }
        }

        private fun decode(target: Long, moving: Boolean) {
            feed()
            val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (index < 0) return
            val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (ended) outputDone = true
            // Only the bare end-of-stream marker is no frame. A decoder that draws onto a surface may
            // well report frames of size zero (the emulator's does): the size says nothing here.
            if (ended && info.size == 0) {
                codec.releaseOutputBuffer(index, false)
                return
            }
            val us = info.presentationTimeUs
            val winding = dropBeforeUs != NOTHING && us < dropBeforeUs && !outputDone
            val late = moving && shownUs != NOTHING && us < target - LATE_US
            if (winding || late) {
                // not shown: on the way to the place asked for, or too late to matter — the sound does not wait
                codec.releaseOutputBuffer(index, false)
                return
            }
            dropBeforeUs = NOTHING
            heldIndex = index
            heldUs = us
        }

        private fun show() {
            codec.releaseOutputBuffer(heldIndex, true)
            shownUs = heldUs
            heldIndex = -1
            if (!mutableState.value.showing) mutableState.value = mutableState.value.copy(showing = true)
        }

        private fun seek(targetUs: Long) {
            if (heldIndex >= 0) codec.releaseOutputBuffer(heldIndex, false)
            heldIndex = -1
            extractor.seekTo(targetUs.coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec.flush()
            inputDone = false
            outputDone = false
            // the frame that stands at the target is the last one not after it; the one on the surface stays until then
            dropBeforeUs = targetUs - FRAME_GUESS_US
            shownUs = NOTHING_YET_AFTER_SEEK
        }

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

        private fun await(ms: Long) = lock.withLock { if (!released) changed.awaitNanos(ms * NANOS_PER_MS) }

        private fun sleep(ms: Long) = lock.withLock { if (!released) changed.awaitNanos(ms * NANOS_PER_MS) }

        fun close() {
            runCatching { if (heldIndex >= 0) codec.releaseOutputBuffer(heldIndex, false) }
            runCatching { codec.stop() }.onFailure { Log.w(TAG, "codec did not stop cleanly", it) }
            codec.release()
            extractor.release()
        }
    }

    private companion object {
        const val TAG = "VideoTrackRenderer"
        const val IDLE = -1
        const val NOTHING = Long.MIN_VALUE
        const val NOTHING_YET_AFTER_SEEK = Long.MIN_VALUE
        const val MICROS_PER_MS = 1_000L
        const val NANOS_PER_MS = 1_000_000L
        const val HALF_TURN = 180

        /** A frame later than this is not worth showing (spec 5.13). */
        const val LATE_US = 50_000L

        /** Further ahead than this is a jump, not playing on: seek instead of decoding all the way. */
        const val FORWARD_SEEK_US = 1_000_000L
        const val BACK_TOLERANCE_US = 100_000L

        /** After a seek frames are dropped up to the target less about a frame, so that the one standing at the target is shown. */
        const val FRAME_GUESS_US = 40_000L

        /** The player speaks every ~43 ms; silent for longer than this, it is not carried on any further. */
        const val MAX_CARRY_MS = 500L
        const val MAX_NAP_MS = 10L
        const val PAUSED_NAP_MS = 50L
        const val DEQUEUE_TIMEOUT_US = 5_000L
        const val SURFACE_WAIT_MS = 500L
        const val WAIT_STEP_MS = 10L
        const val RELEASE_WAIT_MS = 1_000L
    }
}
