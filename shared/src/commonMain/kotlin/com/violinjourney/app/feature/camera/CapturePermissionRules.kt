package com.violinjourney.app.feature.camera

/**
 * Whether a permission of a shot is refused for good (spec 3.36.4): then «Разрешить доступ» opens the settings of the app
 * instead of asking the system, which would answer at once with nothing. Kept for the camera and the microphone apart — the
 * settings may give back the one that was refused for good while the other can still be asked by a dialog. Pure.
 *
 * - Allowed — not refused.
 * - BLOCKED — refused for good: an answer to a request says so, and on iOS every report does.
 * - ASKABLE in an answer to a request — its dialog was shown and refused: it may be shown again.
 * - ASKABLE in a report on a return to the screen keeps what was known: Android does not know BLOCKED before a request, so such a
 *   report is no news that the refusal has gone — only an allowed one is.
 */
object CapturePermissionRules {
    fun blocked(wasBlocked: Boolean, access: CaptureAccess, answered: Boolean): Boolean = when (access) {
        CaptureAccess.GRANTED -> false
        CaptureAccess.BLOCKED -> true
        CaptureAccess.ASKABLE -> !answered && wasBlocked
    }

    /** «Разрешить доступ» opens the settings: one that is not allowed is refused for good. */
    fun settingsOnly(cameraBlocked: Boolean, camera: Boolean?, micBlocked: Boolean, mic: Boolean?): Boolean =
        (cameraBlocked && camera != true) || (micBlocked && mic != true)
}
