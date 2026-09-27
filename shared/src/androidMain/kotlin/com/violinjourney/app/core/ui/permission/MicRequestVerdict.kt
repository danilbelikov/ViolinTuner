package com.violinjourney.app.core.ui.permission

/**
 * What Android's answer to a request for the microphone was (spec 3.4, 3.34). Android does not say whether it showed
 * its dialog, so the answer is read from what it tells around the request:
 *
 * - allowed — [MicPermissionAnswer.GRANTED];
 * - a rationale wanted before or after the request — the dialog was shown: the first refusal raises the rationale, the
 *   second refusal comes from a dialog shown with it — [MicPermissionAnswer.DENIED];
 * - no rationale before or after, and a refusal from a dialog was seen before — Android refuses without asking now
 *   (after two refusals it never shows the dialog again): [MicPermissionAnswer.BLOCKED], however long the answer took;
 * - no rationale before or after and no refusal seen — the very first dialog closed by a tap beside it, or a refusal
 *   the app has no mark of: an answer faster than [DIALOG_NOT_SHOWN_MS] came without a dialog — BLOCKED, a slower one
 *   is the dialog closed — DENIED.
 *
 * Time alone was the rule before: a cold permission controller on a slow phone answers a blocked request later than
 * 400 ms, and «Разрешить доступ» then did nothing at all.
 */
internal object MicRequestVerdict {
    /** A request the system answers faster than this was answered without showing its dialog. */
    const val DIALOG_NOT_SHOWN_MS = 400L

    fun answerOf(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean, answeredInMs: Long, refusedBefore: Boolean): MicPermissionAnswer =
        when {
            granted -> MicPermissionAnswer.GRANTED
            rationaleBefore || rationaleAfter -> MicPermissionAnswer.DENIED
            refusedBefore -> MicPermissionAnswer.BLOCKED
            answeredInMs < DIALOG_NOT_SHOWN_MS -> MicPermissionAnswer.BLOCKED
            else -> MicPermissionAnswer.DENIED
        }

    /** A refusal to remember: one made in a dialog that was shown (the rationale tells it). */
    fun isSeenRefusal(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): Boolean =
        !granted && (rationaleBefore || rationaleAfter)
}
