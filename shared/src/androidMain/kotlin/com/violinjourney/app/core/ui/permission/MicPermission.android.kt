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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val MIC_PERMISSION = Manifest.permission.RECORD_AUDIO

fun Context.isMicPermissionGranted(): Boolean =
    ContextCompat.checkSelfPermission(this, MIC_PERMISSION) == PackageManager.PERMISSION_GRANTED

/** Seen allowed — by a dialog, in the settings, on a return to the screen: a later refusal is judged afresh. */
private fun Context.forgetMicRefusal() = forgetRefusal(PermissionMark.MIC)


@Composable
actual fun rememberMicPermissionRequester(
    openSettingsWhenBlocked: Boolean,
    onAnswer: (MicPermissionAnswer) -> Unit,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnAnswer by rememberUpdatedState(onAnswer)

    fun shouldShowRationale(): Boolean =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, MIC_PERMISSION)

    // Saveable: the activity made anew under the dialog (a turn of the phone) still knows what was asked and when —
    // elapsedRealtime runs on for the whole life of the phone.
    var requestedAtMs by rememberSaveable { mutableLongStateOf(0L) }
    var rationaleBeforeRequest by rememberSaveable { mutableStateOf(false) }
    val ask = remember { MicAsk() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ask.answered()
        currentOnResult(granted)
        val rationaleAfter = shouldShowRationale()
        val answer = MicRequestVerdict.answerOf(
            granted = granted,
            rationaleBefore = rationaleBeforeRequest,
            rationaleAfter = rationaleAfter,
            answeredInMs = SystemClock.elapsedRealtime() - requestedAtMs,
            refusedBefore = context.hasRefusal(PermissionMark.MIC),
        )
        when {
            granted -> context.forgetMicRefusal()
            MicRequestVerdict.isSeenRefusal(granted, rationaleBeforeRequest, rationaleAfter) -> context.rememberRefusal(PermissionMark.MIC)
        }
        currentOnAnswer(answer)
        if (answer == MicPermissionAnswer.BLOCKED && openSettingsWhenBlocked) context.openAppSettings()
    }
    return {
        when {
            // nothing to ask, so no answer to count (spec 3.34) — as on iOS
            context.isMicPermissionGranted() -> {
                context.forgetMicRefusal()
                currentOnResult(true)
            }
            // the dialog is up: a second request would be answered at once with nothing, and read as «blocked»
            !ask.mayAsk() -> Unit
            else -> {
                rationaleBeforeRequest = shouldShowRationale()
                requestedAtMs = SystemClock.elapsedRealtime()
                launcher.launch(MIC_PERMISSION)
            }
        }
    }
}

internal fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

@Composable
actual fun rememberMicPermissionCheck(): () -> Boolean {
    val context = LocalContext.current
    // read on every return to the screen: a microphone allowed in the settings clears the mark of a refusal too
    return remember(context) { { context.isMicPermissionGranted().also { granted -> if (granted) context.forgetMicRefusal() } } }
}
