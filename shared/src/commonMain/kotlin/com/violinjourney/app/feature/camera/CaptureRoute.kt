package com.violinjourney.app.feature.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.capture_video_failed
import com.violinjourney.app.shared.resources.record_no_notes
import org.jetbrains.compose.resources.getString

/** «Снять под минусовку» (spec 3.32): permissions, the camera bound to the screen, the screen kept on. */
@Composable
fun CaptureRoute(onClose: () -> Unit, viewModel: CaptureViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)

    lateinit var permissions: CapturePermissions
    fun report() = permissions.granted().let { (camera, mic) -> viewModel.onIntent(CaptureIntent.PermissionsChanged(camera, mic)) }
    permissions = rememberCapturePermissions(onAnswer = ::report)
    // asked for by itself once, when the screen first opens; after a refusal only the button asks — or the settings
    var asked by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        report()
        val (camera, mic) = permissions.granted()
        if (!asked && (!camera || !mic)) {
            asked = true
            permissions.request()
        }
    }

    // «назад» during a shot stops it and keeps the take, like the button: leaving must not lose what was played
    BackHandler(enabled = state.recording) { viewModel.onIntent(CaptureIntent.RecordClicked) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    CaptureEffect.Close -> currentOnClose()
                    CaptureEffect.RequestPermissions -> permissions.request()
                    CaptureEffect.ShowNoNotes -> messages.show(getString(Res.string.record_no_notes))
                    CaptureEffect.ShowVideoFailed -> messages.showLong(getString(Res.string.capture_video_failed))
                }
            }
        }
    }

    CaptureScreen(
        state = state,
        viewfinder = { viewfinderModifier ->
            CaptureViewfinder(
                camera = viewModel.camera,
                front = state.front,
                enabled = state.cameraPermission == true,
                onBindFailed = { viewModel.onIntent(CaptureIntent.CameraBindFailed) },
                modifier = viewfinderModifier,
            )
        },
        onIntent = viewModel::onIntent,
        onOpenSettings = permissions.openSettings,
        modifier = modifier,
    )
}
