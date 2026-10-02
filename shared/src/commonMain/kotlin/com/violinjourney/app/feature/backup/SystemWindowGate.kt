package com.violinjourney.app.feature.backup

/**
 * One system window of a copy at a time, one for each press (spec 3.36.8, 5.29 R8): the pickers — the place to write a copy, the copy
 * to bring back — the sheet of «Поделиться» and the policy in the browser. The face under a button that puts one up does not change,
 * and two taps 30–60 ms apart both reach the button before the window is up: each would put up its own — two «Сохранить как…», two
 * choosers (the lesson of stage 120: a press acts once).
 *
 * A picker is up from its launch until it answers — with what was picked, or with nothing when it was closed ([answering]): the result
 * of an activity on Android comes for a cancel as well, the delegate of the picker on iOS says either. Meanwhile no other window goes
 * up, however long the person stays in it. A sheet and the browser say nothing when they close: after one, no window goes up for
 * [holdMs]. A picker that the platform sees gone without a word ([pickerGone]; iOS — nothing the app put up is there or on its way) is
 * taken as answered, so no button stays quiet for the rest of the screen's life over an answer that will not come.
 *
 * Kept with the system of a screen (`rememberBackupSystem`). A gate lost with a recreated activity knows of no picker and lets one more
 * launch through, as there was no gate before; the answer of the old picker comes to the new gate and finds it free. Time only from
 * [now] — milliseconds that only go forward.
 */
class SystemWindowGate(
    private val now: () -> Long,
    private val holdMs: Long = SHEET_HOLD_MS,
    private val pickerGone: () -> Boolean = { false },
) {
    /** A picker is up and has not answered yet. */
    private var pickerUp = false

    /** Until when the last sheet holds every window; null — none was put up. */
    private var heldUntilMs: Long? = null

    /** Puts a picker up by [launch] — or nothing while a window of the copy is up. True when it went up. */
    fun picker(launch: () -> Unit): Boolean {
        if (busy()) return false
        // up before the launch, so that no answer can come ahead of it; a launch that throws leaves nothing up
        pickerUp = true
        var launched = false
        try {
            launch()
            launched = true
        } finally {
            if (!launched) pickerUp = false
        }
        return true
    }

    /** Puts up by [launch] a window that says nothing when it closes — the sheet of «Поделиться», the browser. True when it went up. */
    fun sheet(launch: () -> Unit): Boolean {
        if (busy()) return false
        launch()
        heldUntilMs = now() + holdMs
        return true
    }

    /**
     * The answer of a picker — what was picked, or null when it was closed without a pick — handed to [onAnswer] with the gate free
     * again: whatever the answer starts may put up a window of its own.
     */
    fun <T> answering(onAnswer: (T?) -> Unit): (T?) -> Unit = { answer ->
        pickerUp = false
        onAnswer(answer)
    }

    private fun busy(): Boolean {
        if (pickerUp && pickerGone()) pickerUp = false
        val heldUntil = heldUntilMs
        return pickerUp || (heldUntil != null && now() < heldUntil)
    }

    companion object {
        /**
         * How long a sheet holds every window (spec 5.29 R8): the second touch of a double tap comes within 300 ms of the release of
         * the first — the double-tap time of the system on Android and of Compose on iOS — and a button acts on its own release, as
         * long as the finger stays: `clickable` sets no upper bound on a press. A quick tap is down for some 50–150 ms, so the second
         * acts within some 450 ms of the first; 800 covers that with room to spare. A second press held longer than half a second is
         * no double tap but a press of its own.
         */
        const val SHEET_HOLD_MS = 800L
    }
}
