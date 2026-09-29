package com.violinjourney.app.core.ui.permission

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Marks of a refusal made in a shown system dialog (`MicRequestVerdict`, spec 3.4, 3.36.4): a mark of this phone alone, so they lie
 * in `noBackupFilesDir`, out of the files a copy of the data takes (spec 3.20). With a mark, a later request the system answers
 * without its dialog is «denied for good», however long it took. [MIC] is shared by Live, a take and the own camera; [CAMERA] is the
 * own camera's and the system camera's of a piece.
 */
internal enum class PermissionMark(val fileName: String) {
    MIC("mic_refused"),
    CAMERA("camera_refused"),
}

private const val TAG = "PermissionMark"

private fun Context.markFile(mark: PermissionMark): File = File(noBackupFilesDir, mark.fileName)

internal fun Context.hasRefusal(mark: PermissionMark): Boolean = markFile(mark).exists()

/** Seen allowed — by a dialog, in the settings, on a return to the screen: a later refusal is judged afresh. */
internal fun Context.forgetRefusal(mark: PermissionMark) {
    markFile(mark).delete()
}

internal fun Context.rememberRefusal(mark: PermissionMark) {
    try {
        markFile(mark).createNewFile()
    } catch (e: IOException) {
        Log.w(TAG, "the refusal of ${mark.fileName} is not remembered: the next request is judged by time", e)
    }
}
