package com.violinjourney.app.core.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val MIC_PERMISSION = Manifest.permission.RECORD_AUDIO

// A request the system answers faster than this was answered without showing its dialog.
private const val DIALOG_NOT_SHOWN_MS = 400L

fun Context.isMicPermissionGranted(): Boolean =
    ContextCompat.checkSelfPermission(this, MIC_PERMISSION) == PackageManager.PERMISSION_GRANTED

/** What the system answered, once, to a request that was actually made (spec 3.34). */
enum class MicPermissionAnswer(val key: String) {
    GRANTED("granted"),
    DENIED("denied"),

    /** «Denied for good»: the system will not show its dialog again, and only its settings can help. */
    BLOCKED("blocked"),
}

/**
 * Returns a function that asks for RECORD_AUDIO and reports the answer to [onResult].
 *
 * [onAnswer] fires only for an answer to a dialog this function asked for — never on the checks
 * made on every return to the screen, which is why it can be counted (spec 3.34).
 *
 * With [openSettingsWhenBlocked] a request the system refuses to show ("denied for good": it
 * answers at once and wants no rationale before or after) opens the app settings instead,
 * because nothing else can help then (spec 3.4).
 */
@Composable
fun rememberMicPermissionRequester(
    openSettingsWhenBlocked: Boolean,
    onAnswer: (MicPermissionAnswer) -> Unit = {},
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnAnswer by rememberUpdatedState(onAnswer)

    fun shouldShowRationale(): Boolean =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, MIC_PERMISSION)

    var requestedAtMs by remember { mutableLongStateOf(0L) }
    var rationaleBeforeRequest by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
        val answeredWithoutDialog = SystemClock.elapsedRealtime() - requestedAtMs < DIALOG_NOT_SHOWN_MS
        val blocked = !granted && answeredWithoutDialog && !rationaleBeforeRequest && !shouldShowRationale()
        currentOnAnswer(
            when {
                granted -> MicPermissionAnswer.GRANTED
                blocked -> MicPermissionAnswer.BLOCKED
                else -> MicPermissionAnswer.DENIED
            },
        )
        if (blocked && openSettingsWhenBlocked) context.openAppSettings()
    }
    return {
        rationaleBeforeRequest = shouldShowRationale()
        requestedAtMs = SystemClock.elapsedRealtime()
        launcher.launch(MIC_PERMISSION)
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
