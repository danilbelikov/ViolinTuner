package com.violinjourney.app.feature.camera

/** The screen of «Снять под минусовку» (spec 3.32, 3.36.4). */
data class CaptureState(
    val title: String = "",
    /** Null until reported; false — not allowed: the line «нет разрешения» stands over the sleeping shutter. */
    val cameraPermission: Boolean? = null,
    val micPermission: Boolean? = null,
    /** The camera is refused for good ([CapturePermissionRules]); cleared once it is allowed. */
    val cameraBlocked: Boolean = false,
    /** The microphone is refused for good ([CapturePermissionRules]); cleared once it is allowed. */
    val micBlocked: Boolean = false,
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
    /**
     * The name the system gives the headphones on, when the sound goes to headphones — «Pixel Buds» over the shutter; null without
     * headphones, or when the system gave no name (then the line says «Наушники»).
     */
    val headphonesName: String? = null,
    /** Free space for fewer than ten minutes of picture: «Мало места: хватит примерно на N мин». */
    val spaceMinutes: Int? = null,
    /** The picture and the sound are being made into one file. */
    val saving: Boolean = false,
    /** The backing is being made ready for the mix: the button waits (spec 5.25). */
    val preparing: Boolean = false,
    /** The last shot was cut short by a lost microphone (and kept quietly): a plate says so until the next frame of sound. */
    val micUnavailable: Boolean = false,
    /**
     * The backing's sound could not be made — no room for it, a damaged file (spec 5.25): no take under it until the
     * screen is opened anew; a plate over the button says why.
     */
    val backingUnprepared: Boolean = false,
) {
    /**
     * One that is not allowed is refused for good: «Разрешить доступ» opens the settings of the app instead of asking the system,
     * which would answer at once with nothing.
     */
    val permissionBlocked: Boolean get() =
        CapturePermissionRules.settingsOnly(cameraBlocked, cameraPermission, micBlocked, micPermission)

    val canRecord: Boolean get() =
        cameraPermission == true && micPermission == true && !cameraFailed && !(underBacking && (noHeadphones || preparing || backingUnprepared)) && !saving
}

sealed interface CaptureIntent {
    /**
     * Where the two permissions stand: on every return to the screen ([answered] false), and as the system's answer to a request
     * ([answered] true) — only an answer can say «denied for good» on Android.
     */
    data class PermissionsChanged(val camera: CaptureAccess, val mic: CaptureAccess, val answered: Boolean) : CaptureIntent

    /** «Разрешить доступ» of the line over the shutter (spec 3.36.4). */
    data object GrantClicked : CaptureIntent

    data object RecordClicked : CaptureIntent

    data object SwitchCameraClicked : CaptureIntent

    data class FocusAt(val x: Float, val y: Float) : CaptureIntent

    data object CloseClicked : CaptureIntent

    data object CameraBindFailed : CaptureIntent

    /** The app went to the background or the screen went dark — not a turn of the phone, which only rebuilds the screen. */
    data object ScreenLeft : CaptureIntent
}

sealed interface CaptureEffect {
    data object Close : CaptureEffect

    data object RequestPermissions : CaptureEffect

    /** The permissions are refused for good: only the settings of the app can give them (spec 3.4, 3.36.4). */
    data object OpenSettings : CaptureEffect

    data object ShowNoNotes : CaptureEffect

    /** The picture and the sound could not be made into one: the take was kept as sound. */
    data object ShowVideoFailed : CaptureEffect
}
