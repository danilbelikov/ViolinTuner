package com.violinjourney.app.feature.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.violinjourney.app.core.ui.components.KeepScreenOn

@Composable
actual fun rememberCapturePermissions(onAnswer: () -> Unit): CapturePermissions {
    val context = LocalContext.current
    val answered by rememberUpdatedState(onAnswer)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answered() }
    return remember(context) {
        CapturePermissions(
            granted = { context.granted(Manifest.permission.CAMERA) to context.granted(Manifest.permission.RECORD_AUDIO) },
            request = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
            openSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
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
    surface?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}

private fun Context.granted(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
