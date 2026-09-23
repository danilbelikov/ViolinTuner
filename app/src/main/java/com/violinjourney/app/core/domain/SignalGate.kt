package com.violinjourney.app.core.domain

enum class FrameKind {
    /** Confident pitch inside the range of interest. */
    PITCHED,

    /** Loud enough, but no usable pitch: low clarity or out of range. */
    UNCLEAR,

    /** RMS below the silence threshold. */
    QUIET,
}

enum class SignalState { PITCHED, GAP, SILENCE, TOO_NOISY }

/**
 * Turns per-frame kinds into a signal state using the spec 5.1 timeouts: no pitch for longer
 * than the silence timeout is SILENCE, an uninterrupted UNCLEAR run longer than the noisy
 * timeout is TOO_NOISY, anything shorter is a GAP the caller bridges with the last reading.
 */
class SignalGate(private val config: IntonationConfig) {
    private var lastPitchedMs: Long? = null
    private var unclearSinceMs: Long? = null

    fun update(tMs: Long, kind: FrameKind): SignalState {
        if (kind == FrameKind.UNCLEAR) {
            if (unclearSinceMs == null) unclearSinceMs = tMs
        } else {
            unclearSinceMs = null
        }
        if (kind == FrameKind.PITCHED) {
            lastPitchedMs = tMs
            return SignalState.PITCHED
        }
        val unclearSince = unclearSinceMs
        val lastPitched = lastPitchedMs
        return when {
            unclearSince != null && tMs - unclearSince > config.noisyTimeoutMs -> SignalState.TOO_NOISY
            lastPitched == null || tMs - lastPitched > config.silenceTimeoutMs -> SignalState.SILENCE
            else -> SignalState.GAP
        }
    }

    fun reset() {
        lastPitchedMs = null
        unclearSinceMs = null
    }
}
