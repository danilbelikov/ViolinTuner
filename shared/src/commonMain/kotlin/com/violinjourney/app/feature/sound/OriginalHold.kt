package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.audio.playback.SessionPlayer

/**
 * A/B of a player (spec 3.17) as both screens that play a recording have it — «Звук» and the recording itself (spec 3.36.5): a tap
 * chooses A or B; a finger held on A plays the original only for as long as it stays there («пока держишь») and lets it go back to B.
 * Only a hold that began at B goes back to B, and a let-go that follows no hold leaves the choice alone: a tap on A, whose press ends
 * as well, keeps A. Main thread, one per screen.
 */
class OriginalHold {
    private var holding = false

    /** [original] — A (true) or B; [held] — the word of a hold: its start ([original] true) or its end ([original] false). */
    fun select(player: SessionPlayer?, original: Boolean, held: Boolean) {
        val current = player ?: return
        if (held) {
            if (original && !current.state.value.original) {
                holding = true
                current.setOriginal(true)
            } else if (!original && holding) {
                holding = false
                current.setOriginal(false)
            }
        } else {
            holding = false
            current.setOriginal(original)
        }
    }
}
