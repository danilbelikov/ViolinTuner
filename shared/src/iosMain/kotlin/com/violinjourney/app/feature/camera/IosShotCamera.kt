package com.violinjourney.app.feature.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.violinjourney.app.core.audio.backing.HostClock
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.recording.video.VideoMux
import com.violinjourney.app.core.ui.components.KeepScreenOn
import com.violinjourney.app.ios.withBackgroundTime
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFoundation.AVAssetExportPresetPassthrough
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceRotationCoordinator
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureExposureModeAutoExpose
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureFocusModeAutoFocus
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVErrorRecordingSuccessfullyFinishedKey
import platform.AVFoundation.AVFileTypeQuickTimeMovie
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableCompositionTrack
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.duration
import platform.AVFoundation.exposureMode
import platform.AVFoundation.exposurePointOfInterest
import platform.AVFoundation.exposurePointOfInterestSupported
import platform.AVFoundation.focusMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.focusPointOfInterestSupported
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.timeRange
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.CMTimeSubtract
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_time

/**
 * [ShotCamera] of iOS: an AVCaptureSession with a movie output and no sound — the sound is the take's own, from the
 * microphone the pitch is read from. The start of the picture is counted back from its end, as on Android: the file
 * says how long it is, and frames stop being taken the moment the recording is stopped.
 *
 * Everything the session, its input and its output are told goes through one serial queue, as in Apple's AVCam: a
 * start and a stop of the session never overtake each other, and the main thread never waits for an input to be made.
 * The viewfinder and the shot follow the turn of the phone ([AVCaptureDeviceRotationCoordinator]); the shot takes the
 * turn the phone is held at when it starts, as on Android.
 */
@OptIn(ExperimentalForeignApi::class)
class IosShotCamera : ShotCamera {
    val session = AVCaptureSession()
    private val output = AVCaptureMovieFileOutput()
    private val queue = dispatch_queue_create(QUEUE_NAME, null)

    /** The camera bound now: set on the main thread once the queue has bound it. */
    @Volatile private var device: AVCaptureDevice? = null

    /** The queue's. */
    private var input: AVCaptureDeviceInput? = null

    // Of the recording under way: set on the main thread before it starts, read on the queue and by the delegate.
    @Volatile private var finished: CompletableDeferred<Boolean>? = null
    @Volatile private var file: PlatformFile? = null
    @Volatile private var startEventNanos: Long? = null

    /** When the delegate heard that the recording ended — the end, for one the system cut short. */
    @Volatile private var endedNanos: Long? = null

    /** When the output was told to stop — by [stopRecording], or by [release] before it. */
    @Volatile private var stoppedAtNanos: Long? = null

    // The main thread's: the layer of the viewfinder and what turns it.
    private var preview: AVCaptureVideoPreviewLayer? = null
    private var rotation: AVCaptureDeviceRotationCoordinator? = null
    private var released = false
    private var turnStepsLeft = 0

    override var startNanos: Long? = null
        private set

    private val delegate = object : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
        override fun captureOutput(output: AVCaptureFileOutput, didStartRecordingToOutputFileAtURL: NSURL, fromConnections: List<*>) {
            startEventNanos = HostClock.nowNanos()
        }

        override fun captureOutput(output: AVCaptureFileOutput, didFinishRecordingToOutputFileAtURL: NSURL, fromConnections: List<*>, error: NSError?) {
            endedNanos = HostClock.nowNanos()
            // An error with the file still whole — the session interrupted when the app left, the disk full, the length
            // reached — keeps what was shot: the system says so by AVErrorRecordingSuccessfullyFinishedKey.
            val whole = error == null || when (val finishedWell = error.userInfo[AVErrorRecordingSuccessfullyFinishedKey]) {
                is NSNumber -> finishedWell.boolValue
                is Boolean -> finishedWell
                else -> false
            }
            finished?.complete(whole && (file?.sizeBytes() ?: 0) > 0)
        }
    }

    /**
     * Binds the camera on the given side — made and configured on the queue, not on the main thread; false when there is
     * none, or it refused. The caller's thread (the main one) learns of it and turns the viewfinder.
     */
    suspend fun bind(front: Boolean): Boolean {
        val bound = onQueue { configure(front) } ?: return false
        if (bound !== device) {
            device = bound
            coordinate()
        } else {
            turnPreview()
        }
        return true
    }

    /** The queue's: the input of [front] in the session, and the session running. */
    private fun configure(front: Boolean): AVCaptureDevice? {
        val position = if (front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
        val camera = AVCaptureDevice.defaultDeviceWithDeviceType(AVCaptureDeviceTypeBuiltInWideAngleCamera, AVMediaTypeVideo, position) ?: return null
        val next = AVCaptureDeviceInput.deviceInputWithDevice(camera, null) ?: return null
        session.beginConfiguration()
        input?.let(session::removeInput)
        input = null
        // 1080p where the camera has it (spec 5.25)
        session.sessionPreset = if (session.canSetSessionPreset(AVCaptureSessionPreset1920x1080)) AVCaptureSessionPreset1920x1080 else AVCaptureSessionPresetHigh
        if (!session.canAddInput(next)) {
            session.commitConfiguration()
            return null
        }
        session.addInput(next)
        if (!session.outputs.contains(output) && session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()
        input = next
        if (!session.running) session.startRunning()
        return camera
    }

    /** A shot under way goes on: the screen will bind again. */
    fun unbind() {
        dispatch_async(queue) { if (!output.recording) session.stopRunning() }
    }

    override fun release() {
        released = true
        dispatch_async(queue) {
            stopOutput()
            session.stopRunning()
        }
    }

    /**
     * The queue's: any recording not finished yet is stopped — one whose start is still on its way too — and the moment is
     * kept for the start of the picture. One the system has ended already is left as it ended.
     */
    private fun stopOutput() {
        val done = finished ?: return
        if (stoppedAtNanos != null || done.isCompleted) return
        stoppedAtNanos = HostClock.nowNanos()
        output.stopRecording()
    }

    /** The viewfinder's layer, from [PreviewView]; the main thread. */
    fun attach(layer: AVCaptureVideoPreviewLayer) {
        preview = layer
        coordinate()
    }

    /** The main thread's: what turns the viewfinder and the shot, made anew for another camera or another layer. */
    private fun coordinate() {
        val camera = device ?: return
        val layer = preview ?: return
        rotation = AVCaptureDeviceRotationCoordinator(device = camera, previewLayer = layer)
        turnPreview()
    }

    /** The viewfinder upright for the way the phone is held now. The main thread. */
    fun turnPreview() {
        val angle = rotation?.videoRotationAngleForHorizonLevelPreview ?: return
        val connection = preview?.connection ?: return
        if (connection.videoRotationAngle != angle && connection.isVideoRotationAngleSupported(angle)) connection.videoRotationAngle = angle
    }

    /**
     * The phone turned. The coordinator learns of the turn of the screen a moment after the device reports it, and a turn
     * from one side to the other keeps the size of the view — no layout comes after it — so the angle is looked at for a
     * second after the turn. The main thread.
     */
    fun followTurn() {
        val idle = turnStepsLeft <= 0
        turnStepsLeft = TURN_STEPS
        if (idle) stepTurn()
    }

    private fun stepTurn() {
        turnPreview()
        turnStepsLeft--
        if (released || turnStepsLeft <= 0) return
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, TURN_STEP_MS * NANOS_PER_MS), dispatch_get_main_queue()) { stepTurn() }
    }

    override fun focus(x: Float, y: Float) {
        val camera = device ?: return
        val layer = preview ?: return
        // a share of the viewfinder → the layer's points → the sensor's own, its turn and the AspectFill crop undone
        val point = layer.bounds.useContents { layer.captureDevicePointOfInterestForPoint(CGPointMake(x * size.width, y * size.height)) }
        dispatch_async(queue) {
            if (!camera.lockForConfiguration(null)) return@dispatch_async
            if (camera.focusPointOfInterestSupported && camera.isFocusModeSupported(AVCaptureFocusModeAutoFocus)) {
                camera.focusPointOfInterest = point
                camera.focusMode = AVCaptureFocusModeAutoFocus
            }
            if (camera.exposurePointOfInterestSupported && camera.isExposureModeSupported(AVCaptureExposureModeAutoExpose)) {
                camera.exposurePointOfInterest = point
                camera.exposureMode = AVCaptureExposureModeAutoExpose
            }
            camera.unlockForConfiguration()
        }
    }

    override fun startRecording(file: PlatformFile) {
        startNanos = null
        startEventNanos = null
        stoppedAtNanos = null
        endedNanos = null
        this.file = file
        val done = CompletableDeferred<Boolean>()
        finished = done
        // the turn the phone is held at when the shot starts; it does not change during the shot, as on Android
        val angle = rotation?.videoRotationAngleForHorizonLevelCapture ?: PORTRAIT_DEGREES
        val url = NSURL.fileURLWithPath(file.path)
        dispatch_async(queue) {
            val connection = output.connectionWithMediaType(AVMediaTypeVideo) as? AVCaptureConnection
            if (connection == null) {
                // no camera in the session (it refused): no picture — the take stays sound
                done.complete(false)
                return@dispatch_async
            }
            if (connection.isVideoRotationAngleSupported(angle)) connection.videoRotationAngle = angle
            output.startRecordingToOutputFileURL(url, delegate)
        }
    }

    override suspend fun stopRecording(): Boolean {
        val done = finished ?: return false
        // the app may be leaving (spec 3.32): the end of the file is waited for in time of its own
        return withBackgroundTime(BACKGROUND_NAME) {
            onQueue { stopOutput() }
            // the delegate hears of a recording stopped by release() or by the system too; one that never says is no picture
            val usable = withTimeoutOrNull(FINISH_WAIT_MS) { done.await() } ?: false
            // a recording the system cut short keeps what it shot, and its end is the system's, not ours: the earlier of the two
            val end = listOfNotNull(stoppedAtNanos, endedNanos).minOrNull()
            val seconds = file?.let { CMTimeGetSeconds(AVURLAsset(uRL = NSURL.fileURLWithPath(it.path), options = null).duration) } ?: 0.0
            val counted = end?.let { (it - (seconds * NANOS_PER_SECOND).toLong()).takeIf { seconds > 0 } }
            startNanos = listOfNotNull(startEventNanos, counted).maxOrNull()
            usable
        }
    }

    /** [work] on the camera's queue; what it throws is thrown here. Not cancellable: the session is left whole. */
    private suspend fun <T> onQueue(work: () -> T): T = suspendCoroutine { continuation ->
        dispatch_async(queue) { continuation.resumeWith(runCatching(work)) }
    }

    private companion object {
        const val QUEUE_NAME = "com.violinjourney.capture"
        const val BACKGROUND_NAME = "shot"
        const val PORTRAIT_DEGREES = 90.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MS = 1_000_000L

        /** How long the end of a recording is waited for before its picture is given up. */
        const val FINISH_WAIT_MS = 5_000L

        /** After a turn of the phone the angle of the viewfinder is looked at this often, this many times. */
        const val TURN_STEP_MS = 50L
        const val TURN_STEPS = 20
    }
}

/** Where the preview layer of the camera is drawn, over the whole view; it turns with the phone. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(private val camera: IosShotCamera) : UIView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)) {
    private val preview = AVCaptureVideoPreviewLayer(session = camera.session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }

    init {
        layer.addSublayer(preview)
        camera.attach(preview)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        preview.frame = bounds
        CATransaction.commit()
        camera.turnPreview()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun CaptureViewfinder(camera: ShotCamera, front: Boolean, enabled: Boolean, onBindFailed: () -> Unit, modifier: Modifier) {
    val ios = camera as IosShotCamera
    val failed by rememberUpdatedState(onBindFailed)
    // a take is played with the hands on the violin: the screen must not dim under it
    KeepScreenOn()
    LaunchedEffect(enabled, front) {
        // a bind given up for another side (a quick switch) says nothing about the side bound now
        if (enabled && !ios.bind(front) && isActive) failed()
    }
    DisposableEffect(ios) { onDispose { ios.unbind() } }
    // the phone turned: the viewfinder turns with it, a turn from one side to the other included
    DisposableEffect(ios) {
        val device = UIDevice.currentDevice
        device.beginGeneratingDeviceOrientationNotifications()
        val turns = NSNotificationCenter.defaultCenter.addObserverForName(UIDeviceOrientationDidChangeNotification, null, NSOperationQueue.mainQueue) { _ ->
            ios.followTurn()
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(turns)
            device.endGeneratingDeviceOrientationNotifications()
        }
    }
    val view = remember(ios) { PreviewView(ios) }
    // only looked at: a touch on it stays with Compose, as on the picture of a video take
    UIKitView(factory = { view }, modifier = modifier, properties = UIKitInteropProperties(interactionMode = null))
}

@Composable
actual fun rememberCapturePermissions(onAnswer: () -> Unit): CapturePermissions {
    val answered by rememberUpdatedState(onAnswer)
    return remember {
        CapturePermissions(
            granted = {
                (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized) to
                    (AVAudioApplication.sharedInstance.recordPermission == AVAudioApplicationRecordPermissionGranted)
            },
            request = {
                // one system question after the other: the camera, then the microphone
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                    AVAudioApplication.requestRecordPermissionWithCompletionHandler { _ ->
                        dispatch_async(dispatch_get_main_queue()) { answered() }
                    }
                }
            },
            openSettings = {
                NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null) }
            },
        )
    }
}

/**
 * The picture of the camera and the sound of the take into one movie on iOS (spec 3.32, 5.25): the sound at zero, the
 * picture moved by the shift — later by a positive one, cut at its start by a negative one. Nothing is re-encoded.
 */
@OptIn(ExperimentalForeignApi::class)
object IosVideoMux : VideoMux {
    override suspend fun mux(picture: PlatformFile, sound: PlatformFile, target: PlatformFile, shiftUs: Long): Boolean {
        val video = AVURLAsset(uRL = NSURL.fileURLWithPath(picture.path), options = null)
        val audio = AVURLAsset(uRL = NSURL.fileURLWithPath(sound.path), options = null)
        val pictureTrack = video.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return false
        val soundTrack = audio.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return false
        val composition = AVMutableComposition()
        val toPicture = composition.addMutableTrackWithMediaType(AVMediaTypeVideo, kCMPersistentTrackID_Invalid) as? AVMutableCompositionTrack ?: return false
        val toSound = composition.addMutableTrackWithMediaType(AVMediaTypeAudio, kCMPersistentTrackID_Invalid) as? AVMutableCompositionTrack ?: return false
        val shift = CMTimeMakeWithSeconds(shiftUs / US_PER_SECOND, TIMESCALE)
        val whole = pictureTrack.timeRange
        val placed = if (shiftUs >= 0) {
            toPicture.insertTimeRange(whole, pictureTrack, shift, null)
        } else {
            // frames from before the sound are left out
            val skip = CMTimeMakeWithSeconds(-shiftUs / US_PER_SECOND, TIMESCALE)
            val kept = whole.useContents { CMTimeRangeMake(skip, CMTimeSubtract(duration.readValue(), skip)) }
            toPicture.insertTimeRange(kept, pictureTrack, kCMTimeZero.readValue(), null)
        }
        if (!placed) return false
        if (!toSound.insertTimeRange(soundTrack.timeRange, soundTrack, kCMTimeZero.readValue(), null)) return false
        toPicture.preferredTransform = pictureTrack.preferredTransform
        val export = AVAssetExportSession(asset = composition, presetName = AVAssetExportPresetPassthrough) ?: return false
        export.outputURL = NSURL.fileURLWithPath(target.path)
        export.outputFileType = AVFileTypeQuickTimeMovie
        // a take finished after the app left (spec 3.32): the export runs in time of its own, or iOS would stop it halfway
        return withBackgroundTime(BACKGROUND_NAME) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { export.cancelExport() }
                export.exportAsynchronouslyWithCompletionHandler {
                    continuation.resume(export.status == AVAssetExportSessionStatusCompleted && target.sizeBytes() > 0)
                }
            }
        }
    }

    private const val US_PER_SECOND = 1_000_000.0
    private const val TIMESCALE = 600
    private const val BACKGROUND_NAME = "video"
}
