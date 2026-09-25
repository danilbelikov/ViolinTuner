package com.violinjourney.app.feature.camera

import com.violinjourney.app.core.io.PlatformFile

/**
 * The app's own camera for «Снять под минусовку» (spec 3.32): a viewfinder and the picture alone — the sound is the
 * take's chain's. It says when its recording began on the clock the microphone reports on too (`CLOCK_MONOTONIC` on
 * Android, the host clock on iOS). Main thread only. Binding it to the screen is the business of [CaptureViewfinder].
 */
interface ShotCamera {
    /** Stops whatever is recorded, keeping nothing, and lets the camera go: the screen is gone for good. */
    fun release()

    /** Focus and exposure at a point of the viewfinder, 0…1 each way. */
    fun focus(x: Float, y: Float)

    fun startRecording(file: PlatformFile)

    /** When the recording's first frame was taken; known once [stopRecording] has returned. */
    val startNanos: Long?

    /** Stops; true when the file holds a picture worth keeping. */
    suspend fun stopRecording(): Boolean
}

fun interface ShotCameraFactory {
    fun create(): ShotCamera
}
