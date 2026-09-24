package com.violinjourney.app.core.audio.recording

import kotlin.concurrent.Volatile
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [AudioTap] fed hop by hop from the microphone loop. Pure Kotlin: the encoder comes from a
 * factory. The audio thread calls [onHop] and [onStreamEnded], anyone may call the rest.
 */
class HopAudioTap(
    private val encoderFactory: PcmEncoderFactory,
    private val finishDispatcher: CoroutineDispatcher,
) : AudioTap {
    private val lock = PlatformLock()
    private var pendingFile: PlatformFile? = null
    private var encoder: PcmEncoder? = null

    /** Result of a take that the stream itself had to close (it ended before [stop]). */
    private var closedByStream: Boolean? = null

    @Volatile
    override var state: AudioTap.State = AudioTap.State.Idle
        private set

    @Volatile
    override var sampleRateHz: Int? = null
        private set

    override fun start(file: PlatformFile) = lock.withLock {
        if (state != AudioTap.State.Idle) return
        pendingFile = file
        closedByStream = null
        state = AudioTap.State.Starting
    }

    /** [hopStartTMs] is the frame-clock time of the first sample of [hop]. */
    fun onHop(hop: ShortArray, count: Int, hopStartTMs: Long, sampleRateHz: Int) {
        if (state == AudioTap.State.Idle || state == AudioTap.State.Failed) return // the common case, no lock
        lock.withLock {
            if (state == AudioTap.State.Starting) {
                val file = pendingFile ?: return
                pendingFile = null
                val created = runCatching { encoderFactory.create(file, sampleRateHz) }.getOrNull()
                if (created == null) {
                    state = AudioTap.State.Failed
                    return
                }
                encoder = created
                this.sampleRateHz = sampleRateHz
                state = AudioTap.State.Running(hopStartTMs)
            }
            val running = encoder ?: return
            if (state is AudioTap.State.Running && !running.offer(hop, count)) {
                // Fell behind: the frames matter more than the sound. Keep what is encoded out
                // of the session rather than a take with a hole in it.
                state = AudioTap.State.Failed
            }
        }
    }

    /** The stream is going away (Live left, microphone failed): close the file here and now. */
    fun onStreamEnded() {
        val toFinish = lock.withLock {
            pendingFile = null
            val running = encoder
            encoder = null
            if (running == null) {
                if (state == AudioTap.State.Starting) state = AudioTap.State.Failed
                return
            }
            running to (state is AudioTap.State.Running)
        }
        val complete = toFinish.first.finish() && toFinish.second
        lock.withLock { closedByStream = complete }
    }

    override suspend fun stop(): Boolean {
        val (running, wasHealthy, streamResult) = lock.withLock {
            val taken = Triple(encoder, state is AudioTap.State.Running, closedByStream)
            encoder = null
            pendingFile = null
            closedByStream = null
            state = AudioTap.State.Idle
            taken
        }
        if (running == null) return streamResult == true
        // finish() joins the encoder thread: keep that off the caller's dispatcher
        val complete = withContext(finishDispatcher) { running.finish() }
        return complete && wasHealthy
    }
}
