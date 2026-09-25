package com.violinjourney.app.feature.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The permissions of the camera and the microphone a shot needs (spec 3.32), and the way to the settings after a refusal. */
class CapturePermissions(
    /** Whether the camera and the microphone are allowed now. */
    val granted: () -> Pair<Boolean, Boolean>,
    /** Asks for both; the answer comes to the callback of [rememberCapturePermissions]. */
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
expect fun rememberCapturePermissions(onAnswer: () -> Unit): CapturePermissions

/**
 * The picture of [camera] on the screen, bound to it while [enabled] — [front] or back; [onBindFailed] when there is no
 * such camera or it refused. The screen stays on while it is shown: a take is played with the hands on the violin.
 */
@Composable
expect fun CaptureViewfinder(camera: ShotCamera, front: Boolean, enabled: Boolean, onBindFailed: () -> Unit, modifier: Modifier)
