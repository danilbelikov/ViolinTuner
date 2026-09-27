package com.violinjourney.app.core.ui.permission

/**
 * One system dialog about the microphone at a time. A second tap while the dialog is up must not ask again:
 * Android answers such a request at once with an empty result — it would read as «blocked» and open the app
 * settings over the dialog — and iOS queues a second answer to the same dialog. Either way the event
 * `mic_permission` is an answer to a dialog that was shown, once for each (spec 3.34).
 */
class MicAsk {
    private var asking = false

    /** False while a dialog is up; otherwise marks one as up and says it may be asked. */
    fun mayAsk(): Boolean {
        if (asking) return false
        asking = true
        return true
    }

    /** The dialog was answered: the next tap may ask again. */
    fun answered() {
        asking = false
    }
}
