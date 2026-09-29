package com.violinjourney.app.feature.camera

import com.violinjourney.app.core.ui.permission.MicPermissionAnswer
import com.violinjourney.app.core.ui.permission.MicRequestVerdict

/**
 * What Android's answer to the request of a shot said of one of its two permissions, the camera or the microphone (spec 3.36.4) —
 * judged for each apart by the rule of the microphone of Live ([MicRequestVerdict]): the rationale wanted before or after the
 * request, the mark of a refusal seen in a shown dialog (each permission has its own), and, without either, the time of the answer.
 * Two dialogs of one request take the time of both: a second permission answered without its dialog after a slow first one reads by
 * time as a dialog closed — the mark is what tells it (`docs/notes/redesign.md`, stage 109).
 */
internal object CaptureRequestVerdict {
    fun accessOf(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean, answeredInMs: Long, refusedBefore: Boolean): CaptureAccess =
        when (MicRequestVerdict.answerOf(granted, rationaleBefore, rationaleAfter, answeredInMs, refusedBefore)) {
            MicPermissionAnswer.GRANTED -> CaptureAccess.GRANTED
            MicPermissionAnswer.DENIED -> CaptureAccess.ASKABLE
            MicPermissionAnswer.BLOCKED -> CaptureAccess.BLOCKED
        }

    /** A refusal to remember with the mark of its permission: one made in a dialog that was shown (the rationale tells it). */
    fun isSeenRefusal(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): Boolean =
        MicRequestVerdict.isSeenRefusal(granted, rationaleBefore, rationaleAfter)
}
