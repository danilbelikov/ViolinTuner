package com.violinjourney.app.core.audio.recording

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.NoOpAnalytics
import kotlin.concurrent.Volatile
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * [AudioTap] fed hop by hop from the microphone loop. Pure Kotlin: the encoder comes from a
 * factory. The audio thread calls [onHop] and [onStreamEnded], anyone may call the rest.
 */
class HopAudioTap(
    private val encoderFactory: PcmEncoderFactory,
    private val finishDispatcher: CoroutineDispatcher,
    /** Where an encoder that could not be made is told (spec 3.34): the take goes on without sound, and nobody would know why. */
    private val analytics: Analytics = NoOpAnalytics(),
) : AudioTap {
    private val lock = PlatformLock()
    private var pendingFile: PlatformFile? = null
    private var encoder: PcmEncoder? = null

    /**
     * The file of a take the stream itself is closing or has closed (it ended before [stop]): its outcome, once the closing
     * is over. [stop] waits for it instead of guessing — taken for unfinished, a good file would be thrown away.
     */
    private var streamFinish: CompletableDeferred<Boolean>? = null

    @Volatile
    override var state: AudioTap.State = AudioTap.State.Idle
        private set

    @Volatile
    override var sampleRateHz: Int? = null
        private set

    override fun start(file: PlatformFile) = lock.withLock {
        if (state != AudioTap.State.Idle) return
        pendingFile = file
        streamFinish = null
        state = AudioTap.State.Starting
    }

    /** [hopStartTMs] is the frame-clock time of the first sample of [hop]. */
    fun onHop(hop: ShortArray, count: Int, hopStartTMs: Long, sampleRateHz: Int) {
        if (state == AudioTap.State.Idle || state == AudioTap.State.Failed) return // the common case, no lock
        var notMade: Exception? = null
        lock.withLock {
            if (state == AudioTap.State.Starting) {
                val file = pendingFile ?: return
                pendingFile = null
                val created = try {
                    encoderFactory.create(file, sampleRateHz)
                } catch (e: Exception) {
                    notMade = e
                    null
                }
                if (created == null) {
                    state = AudioTap.State.Failed
                } else {
                    encoder = created
                    this.sampleRateHz = sampleRateHz
                    state = AudioTap.State.Running(hopStartTMs)
                }
            }
            val running = encoder
            if (running != null && state is AudioTap.State.Running && !running.offer(hop, count)) {
                // Fell behind: the frames matter more than the sound. Keep what is encoded out
                // of the session rather than a take with a hole in it.
                state = AudioTap.State.Failed
            }
        }
        // told once per take, and outside the lock the stream reads under
        notMade?.let { analytics.error(ErrorGroup.MEDIA, "the encoder of a take could not be made, the take has no sound", it) }
    }

    /** The stream is going away (Live left, microphone failed): close the file here and now. */
    fun onStreamEnded() {
        val closing = lock.withLock {
            pendingFile = null
            val running = encoder
            encoder = null
            if (running == null) {
                if (state == AudioTap.State.Starting) state = AudioTap.State.Failed
                return
            }
            val outcome = CompletableDeferred<Boolean>()
            streamFinish = outcome
            Triple(running, state is AudioTap.State.Running, outcome)
        }
        val (running, healthy, outcome) = closing
        var complete = false
        try {
            complete = running.finish() && healthy
        } finally {
            outcome.complete(complete) // whatever finish did: a stop waiting for it must not wait forever
        }
    }

    override suspend fun stop(): Boolean {
        val (running, wasHealthy, byStream) = lock.withLock {
            val taken = Triple(encoder, state is AudioTap.State.Running, streamFinish)
            encoder = null
            pendingFile = null
            streamFinish = null
            state = AudioTap.State.Idle
            taken
        }
        // Closed by the stream — maybe still closing, on the audio thread: its answer, when it has one.
        if (running == null) return byStream != null && withContext(NonCancellable) { byStream.await() }
        // finish() joins the encoder thread: keep that off the caller's dispatcher. The encoder is out of the tap
        // already, so it is closed even for a caller cancelled meanwhile — else its file would stay without an end
        // and its thread alive, and "back to idle either way" would not hold.
        val complete = withContext(finishDispatcher + NonCancellable) { running.finish() }
        return complete && wasHealthy
    }
}
