package com.example.violintuner.feature.live.components

/**
 * Motion of the Live screen from the handoff `anims` table. The zone color cross-fade is not
 * here: it is a spec number (5.3) and arrives through LiveState.
 */
internal object LiveMotion {
    /** Note, placeholder, status and marker appearing or disappearing. */
    const val CONTENT_FADE_MS = 300

    /** Hold ring falling back to empty; growth is not animated, it follows the domain. */
    const val RING_RESET_MS = 250

    /** Arrow / dot pop when the direction changes. */
    const val DIRECTION_POP_MS = 150
    const val DIRECTION_POP_FROM = 0.6f

    const val SWITCHER_SLIDE_MS = 200
    const val STRING_ROW_EXPAND_MS = 200

    const val LOCK_POP_MS = 150
    const val LOCK_POP_PEAK_MS = 100
    const val LOCK_POP_OVERSHOOT = 1.1f

    /** Record button: circle to square, accent to red. */
    const val RECORD_MORPH_MS = 200

    /** One full breath of the red dot while recording. */
    const val RECORDING_PULSE_MS = 1_200
    const val RECORDING_PULSE_MIN_ALPHA = 0.35f

    /** Marker spring, settles in about 120 ms. */
    const val MARKER_DAMPING = 0.8f
    const val MARKER_STIFFNESS = 600f

    /** The practice chip appears and disappears without drawing the eye (handoff `anims`). */
    const val PRACTICE_CHIP_FADE_MS = 200
}
