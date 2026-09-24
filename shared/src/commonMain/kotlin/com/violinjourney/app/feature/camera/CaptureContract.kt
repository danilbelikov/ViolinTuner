package com.violinjourney.app.feature.camera

/** The screen of «Снять под минусовку» (spec 3.32). */
data class CaptureState(
    val title: String = "",
    /** Null until reported; false — refused: the screen explains and offers the settings. */
    val cameraPermission: Boolean? = null,
    val micPermission: Boolean? = null,
    /** The camera could not be had (none on this side, or it refused). */
    val cameraFailed: Boolean = false,
    val front: Boolean = false,
    val recording: Boolean = false,
    val elapsedSeconds: Long = 0,
    val backingTitle: String? = null,
    val backingPlayedMs: Long? = null,
    val backingDurationMs: Long = 0,
    /** The piece's backing is on (the chip): the take is made under it, in headphones. Off — a plain video. */
    val underBacking: Boolean = false,
    /** No headphones: under the backing it would reach the microphone, the button sleeps (spec 3.32). */
    val noHeadphones: Boolean = false,
    /** Free space for fewer than ten minutes of picture: «Мало места: хватит примерно на N мин». */
    val spaceMinutes: Int? = null,
    /** The picture and the sound are being made into one file. */
    val saving: Boolean = false,
    /** The backing is being made ready for the mix: the button waits (spec 5.25). */
    val preparing: Boolean = false,
    val micUnavailable: Boolean = false,
) {
    val canRecord: Boolean get() = cameraPermission == true && micPermission == true && !cameraFailed && !(underBacking && (noHeadphones || preparing)) && !saving
}

sealed interface CaptureIntent {
    data class PermissionsChanged(val camera: Boolean, val mic: Boolean) : CaptureIntent

    data object RecordClicked : CaptureIntent

    data object SwitchCameraClicked : CaptureIntent

    data class FocusAt(val x: Float, val y: Float) : CaptureIntent

    data object CloseClicked : CaptureIntent

    data object CameraBindFailed : CaptureIntent
}

sealed interface CaptureEffect {
    data object Close : CaptureEffect

    data object RequestPermissions : CaptureEffect

    data object ShowNoNotes : CaptureEffect

    /** The picture and the sound could not be made into one: the take was kept as sound. */
    data object ShowVideoFailed : CaptureEffect
}
