package com.example.violintuner.feature.live

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.R

private const val MIC_PERMISSION = Manifest.permission.RECORD_AUDIO

// A request the system answers faster than this was answered without showing its dialog.
private const val DIALOG_NOT_SHOWN_MS = 400L

/** Entry point of the Live destination: owns the ViewModel, its effects and the mic permission. */
@Composable
fun LiveRoute(
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun shouldShowRationale(): Boolean =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, MIC_PERMISSION)

    var requestedAtMs by remember { mutableLongStateOf(0L) }
    var rationaleBeforeRequest by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted))
        // Denied "for good": the system no longer shows its dialog, it answers at once and asks
        // for no rationale before or after. Only the app settings can help then (spec 3.4).
        val answeredWithoutDialog = SystemClock.elapsedRealtime() - requestedAtMs < DIALOG_NOT_SHOWN_MS
        if (!granted && answeredWithoutDialog && !rationaleBeforeRequest && !shouldShowRationale()) {
            context.openAppSettings()
        }
    }

    // The player's hands are busy: the screen must not dim while Live is open (spec 3.6).
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Also catches a permission revoked or granted in the system settings while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = ContextCompat.checkSelfPermission(context, MIC_PERMISSION) == PackageManager.PERMISSION_GRANTED
        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted))
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    LiveEffect.ShowRecordingUnavailable ->
                        Toast.makeText(context, R.string.record_unavailable, Toast.LENGTH_SHORT).show()
                    LiveEffect.RequestMicPermission -> {
                        rationaleBeforeRequest = shouldShowRationale()
                        requestedAtMs = SystemClock.elapsedRealtime()
                        permissionLauncher.launch(MIC_PERMISSION)
                    }
                }
            }
        }
    }

    LiveScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
