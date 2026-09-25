package com.violinjourney.app.feature.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.violinjourney.app.core.audio.backing.HostClock
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.recording.video.VideoMux
import com.violinjourney.app.core.ui.components.KeepScreenOn
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
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
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureFocusModeAutoFocus
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
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
import platform.AVFoundation.focusMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.focusPointOfInterestSupported
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
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.QOS_CLASS_USER_INITIATED

/**
 * [ShotCamera] of iOS: an AVCaptureSession with a movie output and no sound — the sound is the take's own, from the
 * microphone the pitch is read from. The start of the picture is counted back from its end, as on Android: the file
 * says how long it is, and frames stop being taken the moment the recording is stopped.
 */
@OptIn(ExperimentalForeignApi::class)
class IosShotCamera : ShotCamera {
    val session = AVCaptureSession()
    private val output = AVCaptureMovieFileOutput()
    private var device: AVCaptureDevice? = null
    private var input: AVCaptureDeviceInput? = null
    private var finished: CompletableDeferred<Boolean>? = null
    private var file: PlatformFile? = null
    private var startEventNanos: Long? = null

    override var startNanos: Long? = null
        private set

    private val delegate = object : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
        override fun captureOutput(output: AVCaptureFileOutput, didStartRecordingToOutputFileAtURL: NSURL, fromConnections: List<*>) {
            startEventNanos = HostClock.nowNanos()
        }

        override fun captureOutput(output: AVCaptureFileOutput, didFinishRecordingToOutputFileAtURL: NSURL, fromConnections: List<*>, error: NSError?) {
            // an error with the file still whole (the disk filled up, the length was reached) keeps what was shot
            finished?.complete((file?.sizeBytes() ?: 0) > 0)
        }
    }

    /** Binds the camera on the given side; false when there is none, or it refused. */
    fun bind(front: Boolean): Boolean {
        val position = if (front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
        val camera = AVCaptureDevice.defaultDeviceWithDeviceType(AVCaptureDeviceTypeBuiltInWideAngleCamera, AVMediaTypeVideo, position) ?: return false
        val next = AVCaptureDeviceInput.deviceInputWithDevice(camera, null) ?: return false
        session.beginConfiguration()
        input?.let(session::removeInput)
        // 1080p where the camera has it (spec 5.25)
        session.sessionPreset = if (session.canSetSessionPreset(AVCaptureSessionPreset1920x1080)) AVCaptureSessionPreset1920x1080 else AVCaptureSessionPresetHigh
        if (!session.canAddInput(next)) {
            session.commitConfiguration()
            return false
        }
        session.addInput(next)
        if (!session.outputs.contains(output) && session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()
        device = camera
        input = next
        if (!session.running) dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) { session.startRunning() }
        return true
    }

    fun unbind() {
        if (output.recording) return // a shot under way goes on; the screen will bind again
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) { session.stopRunning() }
    }

    override fun release() {
        if (output.recording) output.stopRecording()
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) { session.stopRunning() }
    }

    override fun focus(x: Float, y: Float) {
        val camera = device ?: return
        if (!camera.focusPointOfInterestSupported || !camera.lockForConfiguration(null)) return
        camera.focusPointOfInterest = CGPointMake(x.toDouble(), y.toDouble())
        camera.focusMode = AVCaptureFocusModeAutoFocus
        camera.unlockForConfiguration()
    }

    override fun startRecording(file: PlatformFile) {
        startNanos = null
        startEventNanos = null
        this.file = file
        finished = CompletableDeferred()
        (output.connectionWithMediaType(AVMediaTypeVideo) as? AVCaptureConnection)?.let { connection ->
            // the picture is written the right way up for a phone held upright on the stand
            if (connection.isVideoRotationAngleSupported(PORTRAIT_DEGREES)) connection.videoRotationAngle = PORTRAIT_DEGREES
        }
        output.startRecordingToOutputFileURL(NSURL.fileURLWithPath(file.path), delegate)
    }

    override suspend fun stopRecording(): Boolean {
        val done = finished ?: return false
        if (!output.recording) return false
        val stoppedAt = HostClock.nowNanos()
        output.stopRecording()
        val usable = done.await()
        val seconds = file?.let { CMTimeGetSeconds(AVURLAsset(uRL = NSURL.fileURLWithPath(it.path), options = null).duration) } ?: 0.0
        val counted = (stoppedAt - (seconds * NANOS_PER_SECOND).toLong()).takeIf { seconds > 0 }
        startNanos = listOfNotNull(startEventNanos, counted).maxOrNull()
        return usable
    }

    private companion object {
        const val PORTRAIT_DEGREES = 90.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

/** Where the preview layer of the camera is drawn, over the whole view. */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)) {
    private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }

    init {
        layer.addSublayer(preview)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        preview.frame = bounds
        CATransaction.commit()
    }
}

@Composable
actual fun CaptureViewfinder(camera: ShotCamera, front: Boolean, enabled: Boolean, onBindFailed: () -> Unit, modifier: Modifier) {
    val ios = camera as IosShotCamera
    val failed by rememberUpdatedState(onBindFailed)
    // a take is played with the hands on the violin: the screen must not dim under it
    KeepScreenOn()
    LaunchedEffect(enabled, front) {
        if (enabled && !ios.bind(front)) failed()
    }
    DisposableEffect(ios) { onDispose { ios.unbind() } }
    val view = remember(ios) { PreviewView(ios.session) }
    UIKitView(factory = { view }, modifier = modifier)
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
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { export.cancelExport() }
            export.exportAsynchronouslyWithCompletionHandler {
                continuation.resume(export.status == AVAssetExportSessionStatusCompleted && target.sizeBytes() > 0)
            }
        }
    }

    private const val US_PER_SECOND = 1_000_000.0
    private const val TIMESCALE = 600
}
