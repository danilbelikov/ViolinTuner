package com.example.violintuner.feature.camera

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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's own camera for «Снять под минусовку» (spec 3.32): a viewfinder and the picture alone — the sound is the
 * take's chain's. It says when its recording began on `CLOCK_MONOTONIC`, the clock the microphone reports on too.
 * Main thread only.
 */
interface ShotCamera {
    /** What the viewfinder draws into; null until the camera is bound. */
    val surfaceRequest: StateFlow<SurfaceRequest?>

    /** Binds the camera to [owner]; false when there is none to bind (no such camera, or it refused). */
    suspend fun bind(owner: LifecycleOwner, front: Boolean): Boolean

    fun unbind()

    /** Focus and exposure at a point of the viewfinder, 0…1 each way. */
    fun focus(x: Float, y: Float)

    /** How the phone is turned (a `Surface.ROTATION_*`): the picture is written the right way up. */
    fun setRotation(rotation: Int)

    fun startRecording(file: File)

    /** When the recording's first frame came, on `CLOCK_MONOTONIC`; null until it has. */
    val startNanos: Long?

    /** Stops; true when the file holds a picture worth keeping. */
    suspend fun stopRecording(): Boolean
}

fun interface ShotCameraFactory {
    fun create(): ShotCamera
}

class CameraXShotCamera @Inject constructor(@ApplicationContext private val context: Context) : ShotCamera {
    private val mutableSurface = MutableStateFlow<SurfaceRequest?>(null)
    override val surfaceRequest: StateFlow<SurfaceRequest?> = mutableSurface.asStateFlow()

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

    override suspend fun bind(owner: LifecycleOwner, front: Boolean): Boolean {
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

    override fun unbind() {
        provider?.unbindAll()
        camera = null
        mutableSurface.value = null
    }

    override fun focus(x: Float, y: Float) {
        val control = camera?.cameraControl ?: return
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(x, y)
        control.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    override fun setRotation(rotation: Int) {
        if (recording == null) videoCapture.targetRotation = rotation
    }

    override fun startRecording(file: File) {
        startNanos = null
        val done = CompletableDeferred<Boolean>()
        finalized = done
        // no audio on purpose: the sound is the take's own, from the microphone the pitch is read from
        recording = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> startNanos = System.nanoTime()
                    is VideoRecordEvent.Finalize -> {
                        val usable = event.error in USABLE && file.isFile && file.length() > 0
                        if (!usable) Log.w(TAG, "the shot ended with error ${event.error}", event.cause)
                        done.complete(usable)
                    }
                }
            }
    }

    override suspend fun stopRecording(): Boolean {
        val running = recording ?: return false
        recording = null
        running.stop()
        return finalized?.await() ?: false
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
