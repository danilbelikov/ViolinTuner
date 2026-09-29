package com.violinjourney.app.feature.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.violinjourney.app.core.ui.components.KeepScreenOn
import com.violinjourney.app.core.ui.permission.MicAsk
import com.violinjourney.app.core.ui.permission.PermissionMark
import com.violinjourney.app.core.ui.permission.forgetRefusal
import com.violinjourney.app.core.ui.permission.hasRefusal
import com.violinjourney.app.core.ui.permission.openAppSettings
import com.violinjourney.app.core.ui.permission.rememberRefusal

private const val CAMERA = Manifest.permission.CAMERA
private const val MIC = Manifest.permission.RECORD_AUDIO

@Composable
actual fun rememberCapturePermissions(onAnswer: (camera: CaptureAccess, mic: CaptureAccess) -> Unit): CapturePermissions {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val answered by rememberUpdatedState(onAnswer)

    fun rationale(permission: String): Boolean = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    // Saveable: the activity made anew under the dialogs (a turn of the phone) still knows what was asked and when — elapsedRealtime
    // runs on for the whole life of the phone.
    var requestedAtMs by rememberSaveable { mutableLongStateOf(0L) }
    var cameraRationaleBefore by rememberSaveable { mutableStateOf(false) }
    var micRationaleBefore by rememberSaveable { mutableStateOf(false) }
    // the dialogs are up: a second request would be answered at once with nothing, and read as «blocked»
    val ask = remember { MicAsk() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        ask.answered()
        val answeredInMs = SystemClock.elapsedRealtime() - requestedAtMs

        // each permission apart, by the rule of the microphone of Live, with its own mark of a refusal seen in a shown dialog
        fun judge(permission: String, mark: PermissionMark, rationaleBefore: Boolean): CaptureAccess {
            val granted = result[permission] ?: context.granted(permission)
            val rationaleAfter = rationale(permission)
            val access = CaptureRequestVerdict.accessOf(granted, rationaleBefore, rationaleAfter, answeredInMs, context.hasRefusal(mark))
            when {
                granted -> context.forgetRefusal(mark)
                CaptureRequestVerdict.isSeenRefusal(granted, rationaleBefore, rationaleAfter) -> context.rememberRefusal(mark)
            }
            return access
        }
        answered(judge(CAMERA, PermissionMark.CAMERA, cameraRationaleBefore), judge(MIC, PermissionMark.MIC, micRationaleBefore))
    }
    return remember(context, activity, launcher) {
        CapturePermissions(
            // Android does not tell «denied for good» without a request: not allowed is ASKABLE here, the answer knows better. A
            // permission seen allowed — in the settings too — clears the mark of its refusal, as the microphone of Live does.
            status = {
                fun of(permission: String, mark: PermissionMark): CaptureAccess =
                    if (context.granted(permission)) CaptureAccess.GRANTED.also { context.forgetRefusal(mark) } else CaptureAccess.ASKABLE
                of(CAMERA, PermissionMark.CAMERA) to of(MIC, PermissionMark.MIC)
            },
            request = {
                if (ask.mayAsk()) {
                    cameraRationaleBefore = rationale(CAMERA)
                    micRationaleBefore = rationale(MIC)
                    requestedAtMs = SystemClock.elapsedRealtime()
                    launcher.launch(arrayOf(CAMERA, MIC))
                }
            },
            openSettings = { context.openAppSettings() },
        )
    }
}

@Composable
actual fun CaptureViewfinder(camera: ShotCamera, front: Boolean, enabled: Boolean, onBindFailed: () -> Unit, modifier: Modifier) {
    val cameraX = camera as CameraXShotCamera
    val surface by cameraX.surfaceRequest.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val failed by rememberUpdatedState(onBindFailed)
    // a take is played with the hands on the violin: the screen must not dim under it
    KeepScreenOn()
    LaunchedEffect(enabled, front, lifecycleOwner) {
        if (enabled && !cameraX.bind(lifecycleOwner, front)) failed()
    }
    DisposableEffect(cameraX) { onDispose { cameraX.unbind() } }
    // the picture is written the way the phone is held when the shot starts
    view.display?.rotation?.let(cameraX::setRotation)
    // the viewfinder keeps the matrix from its points to the surface's: a touch is turned and uncropped by it (spec 3.32)
    surface?.let {
        CameraXViewfinder(
            surfaceRequest = it,
            modifier = modifier.onSizeChanged { size -> cameraX.viewfinderSize = size },
            coordinateTransformer = cameraX.coordinates,
        )
    }
}

private fun Context.granted(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
