package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.io.PlatformFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** Where the sound goes now, and when that changes (spec 3.32: no headphones — no take under the backing). */
interface AudioRoutes {
    fun current(): AudioRoute

    val changes: Flow<AudioRoute>
}

/**
 * The backing played into the headphones while a take is recorded (spec 3.32). It reports the moment its first
 * frame left the output on `CLOCK_MONOTONIC` — what the shift of the take is measured from — and stops by itself,
 * rather than fall through to the speaker, when the headphones go.
 */
interface BackingPlayback {
    /** How far it has played, in ms; null while it does not play. */
    val position: StateFlow<Long?>

    /** Plays [pcm] — 16-bit stereo at [sampleRate], as the backing cache makes it — from its start. Returns at once. */
    fun start(pcm: PlatformFile, sampleRate: Int)

    /** When the first frame left the output; null until the output has said so. */
    val startNanos: Long?

    /** Stops; how far it had played, in ms. */
    fun stop(): Long
}

fun interface BackingPlaybackFactory {
    fun create(): BackingPlayback
}

/** The fake build (`-PfakePitch=true`, the emulator): pretend wireless headphones, so a take under the backing can be tried without any. */
class FakeHeadphoneRoutes : AudioRoutes {
    private val route = AudioRoute(BackingOutput.BLUETOOTH, "Emulator headphones")

    override fun current(): AudioRoute = route

    override val changes: Flow<AudioRoute> = flowOf(route)
}
