package com.violinjourney.app.feature.repertoire.stand

/** Durations and strengths of the music stand, from the `anims` table of the handoff and 5.29 R4. */
object StandMotion {
    const val PANEL_IN_MS = 200
    const val PANEL_OUT_MS = 300
    const val PAGE_TURN_MS = 250
    const val EDGE_FLASH_MS = 200
    const val EDGE_FLASH_ALPHA = 0.2f
    const val BOUNCE_MS = 150
    const val DOUBLE_TAP_MS = 200
    /** The hint of the first visit comes and goes (5.29 R4); how long it stays is the view model's — `RepertoireConfig.standHintMs`. */
    const val HINT_FADE_MS = 300

    /** The dashed outline of a zone of the hint, in the accent. */
    const val HINT_ALPHA = 0.8f

    /** The fill of a zone of the hint, in the accent. */
    const val HINT_FILL_ALPHA = 0.08f
}
