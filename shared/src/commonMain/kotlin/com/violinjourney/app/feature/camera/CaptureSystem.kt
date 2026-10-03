package com.violinjourney.app.feature.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.violinjourney.app.core.domain.VideoQuality

/** Where a permission of a shot stands (spec 3.32, 3.36.4). */
enum class CaptureAccess {
    GRANTED,

    /** Not allowed, and the system may still be asked: its dialog comes up. */
    ASKABLE,

    /** «Denied for good»: the system does not ask any more — only its settings can help (spec 3.4). */
    BLOCKED,
}

/**
 * The permissions of the camera and the microphone a shot needs (spec 3.32), and the way to the settings once the system asks no
 * more (spec 3.36.4). [status] — where both stand now, the camera first: on iOS BLOCKED is the system's own «denied»; Android does
 * not tell it without a request, and reports ASKABLE — the answer to a request knows ([CapturePermissionRules]).
 */
class CapturePermissions(
    val status: () -> Pair<CaptureAccess, CaptureAccess>,
    /** Asks for both; the answer comes to the callback of [rememberCapturePermissions], after the dialogs, or at once without them. */
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * [onAnswer] — what the system answered to a [CapturePermissions.request], the camera and the microphone apart. On Android BLOCKED
 * is read from what the system says around the request and from the mark of a refusal seen in a shown dialog (the camera's own,
 * the microphone's shared with Live), as the microphone of Live is (`MicRequestVerdict`); on iOS it is the system's «denied».
 */
@Composable
expect fun rememberCapturePermissions(onAnswer: (camera: CaptureAccess, mic: CaptureAccess) -> Unit): CapturePermissions

/**
 * The picture of [camera] on the screen, bound to it while [enabled] — [front] or back, recording in [quality] («Качество видео»,
 * spec 3.19); [onBindFailed] when there is no such camera or it refused. The screen stays on while it is shown: a take is played with the hands on the violin.
 */
@Composable
expect fun CaptureViewfinder(
    camera: ShotCamera,
    front: Boolean,
    quality: VideoQuality,
    enabled: Boolean,
    onBindFailed: () -> Unit,
    modifier: Modifier,
)
