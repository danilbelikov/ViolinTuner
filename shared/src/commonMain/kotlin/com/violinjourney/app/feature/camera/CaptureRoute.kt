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

/**
 * «Снять под минусовку» (spec 3.32): permissions, the camera bound to the screen, the screen kept on.
 * [changingConfigurations]: the screen stops only to come back turned (Android) — a shot goes on through that.
 */
@Composable
fun CaptureRoute(
    onClose: () -> Unit,
    viewModel: CaptureViewModel,
    modifier: Modifier = Modifier,
    changingConfigurations: () -> Boolean = { false },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)

    val permissions = rememberCapturePermissions(onAnswer = { camera, mic ->
        viewModel.onIntent(CaptureIntent.PermissionsChanged(camera, mic, answered = true))
    })
    // Asked for by itself once, when the screen first opens (spec 3.32); after a refusal only «Разрешить доступ» asks — or opens the
    // settings. The answer to this first request does not open them by itself, even refused for good (spec 3.36.4).
    var asked by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val (camera, mic) = permissions.status()
        viewModel.onIntent(CaptureIntent.PermissionsChanged(camera, mic, answered = false))
        if (!asked && (camera != CaptureAccess.GRANTED || mic != CaptureAccess.GRANTED)) {
            asked = true
            permissions.request()
        }
    }

    // «назад» during a shot stops it and keeps the take, like the button: leaving must not lose what was played
    BackHandler(enabled = state.recording) { viewModel.onIntent(CaptureIntent.RecordClicked) }
    // the app in the background — «Домой», the power button, a call over the whole screen: the take is kept quietly
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!changingConfigurations()) viewModel.onIntent(CaptureIntent.ScreenLeft)
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    CaptureEffect.Close -> currentOnClose()
                    CaptureEffect.RequestPermissions -> permissions.request()
                    CaptureEffect.OpenSettings -> permissions.openSettings()
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
        modifier = modifier,
    )
}
