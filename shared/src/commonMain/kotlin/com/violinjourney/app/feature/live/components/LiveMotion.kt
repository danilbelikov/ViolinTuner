package com.violinjourney.app.feature.live.components

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Motion of the Live screen from the handoff `anims` table. The zone color cross-fade is not
 * here: it is a spec number (5.3) and arrives through LiveState.
 */
object LiveMotion {
    /** `light.off`: cubic-bezier(.3, 0, .8, .15). */
    val LightOffEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** `light.on`: cubic-bezier(0, 0, 0, 1). */
    val LightOnEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Note, placeholder, status and marker appearing or disappearing. */
    const val CONTENT_FADE_MS = 300

    /** The status line changing its words: "Играйте…" to "Слишком шумно". */
    const val STATUS_LINE_SWAP_MS = 200

    // The glow of the ring (spec 5.8, handoff `anims`): up faster than down, so that a short
    // slip out of the zone does not put the ring out.
    const val GLOW_RISE_MS = 500
    const val GLOW_FALL_MS = 900

    /** With system animations off the glow changes in steps, cross-faded. */
    const val GLOW_STEP_MS = 300

    // Waves: rare by rule. At most two alive follows from the numbers: 900 ms of life, 600 apart.
    const val WAVE_MS = 900
    const val WAVE_MIN_INTERVAL_MS = 600L

    /** The wave on every new note; the one that rewards a held note stays whatever this says. */
    const val NEW_NOTE_WAVE = true

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

    /** The key goes down by its travel and comes back. */
    const val RECORD_PRESS_MS = 90

    // The light in the hall (spec 3.27, 5.20; handoff `light.*`): it goes out like a switch — fast,
    // gathering speed — when a note is held, and comes back like a dimmer — slowly, easing — after
    // six seconds without one. A recording puts it out at once and keeps it out until «стоп».
    const val LIGHT_OFF_MS = 420
    const val LIGHT_ON_MS = 1_600
    const val LIGHT_ON_AFTER_MS = 6_000L

    /** One full breath of the red dot while recording. */
    const val RECORDING_PULSE_MS = 1_200
    const val RECORDING_PULSE_MIN_ALPHA = 0.35f

    /** Marker spring, settles in about 120 ms. */
    const val MARKER_DAMPING = 0.8f
    const val MARKER_STIFFNESS = 600f

    /** The practice tag: the paper pours in from the notch when a practice starts (handoff nav_bar 35). */
    const val PRACTICE_TAG_FILL_MS = 300

    // The bookmark of blocks (spec 5.21, handoff 30 `anims`): the outline turns into paper and back; at the goal the
    // brass line runs to the edge, then the rim closes and «готово» comes in. The minutes left change without motion.
    const val BOOKMARK_SWAP_MS = 300
    const val BOOKMARK_FILL_MS = 240
    const val BOOKMARK_DONE_MS = 400
    const val BOOKMARK_DONE_DELAY_MS = 80

    // The sheets of blocks: «Сначала — занятие» gives way to «Что играем» in place — the content cross-fades, the sheet grows.
    const val BLOCK_SHEET_SWAP_MS = 240
    const val BLOCK_SHEET_GROW_MS = 320
}
