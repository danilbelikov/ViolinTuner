package com.violinjourney.app.feature.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [ShotCamera] of Android: CameraX, the preview and a video capture without sound, bound to the screen by
 * [CaptureViewfinder]. It says when its recording began on `CLOCK_MONOTONIC`, the clock the microphone reports on too.
 */
class CameraXShotCamera(private val context: Context) : ShotCamera {
    private val mutableSurface = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = mutableSurface.asStateFlow()

    private val preview = Preview.Builder().build().apply { setSurfaceProvider { request -> mutableSurface.value = request } }

    // 1080p where the camera has it, the nearest below where it does not (spec 5.25)
    private val recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
        .build()
    private val videoCapture = VideoCapture.withOutput(recorder)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var recording: Recording? = null
    private var finalized: CompletableDeferred<Boolean>? = null

    override var startNanos: Long? = null
        private set

    /** When CameraX said the recording began — before the first frame, often by far: a lower bound, not the start. */
    private var startEventNanos: Long? = null
    private var recordedNanos: Long = 0

    suspend fun bind(owner: LifecycleOwner, front: Boolean): Boolean {
        val cameras = provider ?: ProcessCameraProvider.awaitInstance(context).also { provider = it }
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        return try {
            if (!cameras.hasCamera(selector)) return false
            cameras.unbindAll()
            camera = cameras.bindToLifecycle(owner, selector, preview, videoCapture)
            true
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "no such camera", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "the camera refused", e)
            false
        }
    }

    fun unbind() {
        provider?.unbindAll()
        camera = null
        mutableSurface.value = null
    }

    override fun release() {
        recording?.close()
        recording = null
        unbind()
    }

    override fun focus(x: Float, y: Float) {
        val control = camera?.cameraControl ?: return
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(x, y)
        control.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    fun setRotation(rotation: Int) {
        if (recording == null) videoCapture.targetRotation = rotation
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalPersistentRecording::class])
    override fun startRecording(file: File) {
        startNanos = null
        startEventNanos = null
        recordedNanos = 0
        val done = CompletableDeferred<Boolean>()
        finalized = done
        // no audio on purpose: the sound is the take's own, from the microphone the pitch is read from
        recording = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            // a turn of the phone recreates the screen and binds the camera anew: the shot must survive it (spec 3.32)
            .asPersistentRecording()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> startEventNanos = System.nanoTime()
                    is VideoRecordEvent.Finalize -> {
                        recordedNanos = event.recordingStats.recordedDurationNanos
                        val usable = event.error in USABLE && file.isFile && file.length() > 0
                        if (!usable) Log.w(TAG, "the shot ended with error ${event.error}", event.cause)
                        done.complete(usable)
                    }
                }
            }
    }

    /**
     * The picture's start is counted back from its end (spec 5.25): frames stop being taken the moment the recording is
     * stopped, and the file says how long it is. «Start» comes before the encoder has its first key frame — on the
     * emulator 1.4 s before — so the picture set by it began too early and ran ahead of the sound.
     */
    override suspend fun stopRecording(): Boolean {
        val running = recording ?: return false
        recording = null
        val stoppedAt = System.nanoTime()
        running.stop()
        val usable = finalized?.await() ?: false
        // no length told (the recording broke off): the event is all there is
        val counted = (stoppedAt - recordedNanos).takeIf { recordedNanos > 0 }
        startNanos = listOfNotNull(startEventNanos, counted).maxOrNull()
        return usable
    }

    private companion object {
        const val TAG = "ShotCamera"

        /** Endings after which the file still holds what was shot up to that moment. */
        val USABLE = setOf(
            VideoRecordEvent.Finalize.ERROR_NONE,
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE,
        )
    }
}
