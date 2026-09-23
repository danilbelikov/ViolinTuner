package com.example.violintuner.feature.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.R

/** «Снять под минусовку» (spec 3.32): permissions, the camera bound to the screen, the screen kept on. */
@Composable
fun CaptureRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: CaptureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surface by viewModel.camera.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val currentOnClose by rememberUpdatedState(onClose)

    fun report() = viewModel.onIntent(CaptureIntent.PermissionsChanged(context.granted(Manifest.permission.CAMERA), context.granted(Manifest.permission.RECORD_AUDIO)))
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { report() }
    // asked for by itself once, when the screen first opens; after a refusal only the button asks — or the settings
    var asked by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        report()
        if (!asked && (!context.granted(Manifest.permission.CAMERA) || !context.granted(Manifest.permission.RECORD_AUDIO))) {
            asked = true
            permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    // a take is played with the hands on the violin: the screen must not dim under it
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(state.cameraPermission, state.front, lifecycleOwner) {
        if (state.cameraPermission == true && !viewModel.camera.bind(lifecycleOwner, state.front)) viewModel.onIntent(CaptureIntent.CameraBindFailed)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.camera.unbind() } }
    // the picture is written the way the phone is held when the shot starts
    view.display?.rotation?.let { viewModel.camera.setRotation(it) }

    // «назад» during a shot stops it and keeps the take, like the button: leaving must not lose what was played
    BackHandler(enabled = state.recording) { viewModel.onIntent(CaptureIntent.RecordClicked) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    CaptureEffect.Close -> currentOnClose()
                    CaptureEffect.RequestPermissions -> permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    CaptureEffect.ShowNoNotes -> Toast.makeText(context, R.string.record_no_notes, Toast.LENGTH_SHORT).show()
                    CaptureEffect.ShowVideoFailed -> Toast.makeText(context, R.string.capture_video_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    CaptureScreen(
        state = state,
        surfaceRequest = surface,
        onIntent = viewModel::onIntent,
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        modifier = modifier,
    )
}

private fun Context.granted(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
