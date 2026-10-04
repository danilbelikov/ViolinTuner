package com.violinjourney.app.core.ui.components

import com.violinjourney.app.core.domain.VideoQuality
import com.violinjourney.app.core.io.PickedCopies
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCurrent
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import platform.darwin.dispatch_time
import platform.Foundation.writeToFile
import platform.UIKit.UIImagePickerControllerQualityType640x480
import platform.UIKit.UIImagePickerControllerQualityTypeHigh
import platform.UIKit.UIImagePickerControllerQualityTypeIFrame1280x720

/**
 * The system's own screens the app puts in front of itself on iOS: the photo picker, the camera, the file picker and
 * the sheet of «Поделиться». Each answer comes on the main thread; the delegates are kept by whoever asks, for as long
 * as the screen is up — UIKit holds them weakly.
 */
@OptIn(ExperimentalForeignApi::class)
internal object SystemScreens {
    /**
     * The app's own screen, set once by `MainViewController`. Compose shows its menus and dialogs in windows of their own,
     * and one of them is the key window while it is up: a system screen put in front of it goes away with it.
     */
    var host: UIViewController? = null

    /** The controller on top of the app's own window, one that is not on its way out. */
    fun topController(): UIViewController? {
        var controller = host?.view?.window?.rootViewController ?: UIApplication.sharedApplication.keyWindow?.rootViewController
        while (true) {
            val next = controller?.presentedViewController ?: return controller
            if (next.isBeingDismissed()) return controller
            controller = next
        }
    }

    /** Screens [present] has been asked for and has not put up yet: it waits a moment before each. */
    private var waiting = 0

    /**
     * Puts [controller] in front of the app's own window, never of a menu's (see [host]). It waits for a screen of the
     * app's window that is on its way out: UIKit would not present on top of it.
     */
    fun present(controller: UIViewController) {
        waiting++
        presentWhenSettled(controller, attemptsLeft = SETTLE_ATTEMPTS)
    }

    private fun presentWhenSettled(controller: UIViewController, attemptsLeft: Int) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, SETTLE_STEP_NANOS), dispatch_get_main_queue()) {
            val top = topController()
            val closing = top?.presentedViewController?.isBeingDismissed() == true
            if (top != null && closing && attemptsLeft > 0) {
                presentWhenSettled(controller, attemptsLeft - 1)
            } else {
                // up now — or never, with no window to put it in front of: it waits no more either way
                waiting--
                top?.presentViewController(controller, animated = true, completion = null)
            }
        }
    }

    /**
     * Nothing the app has put in front of its own window is there, nor on its way ([present] waits before it puts one up): a
     * picker that went without a word from its delegate — or that UIKit would not present — has gone (`SystemWindowGate`).
     */
    fun nothingUp(): Boolean = waiting == 0 && topController()?.presentingViewController == null

    /** The photo library: pictures, up to [limit] (0 — any number); each lent file is copied into `tmp/picked/` ([PickedCopies]). */
    fun photoPicker(limit: Long, delegate: MediaPickerDelegate): PHPickerViewController {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = limit
        }
        return PHPickerViewController(configuration).apply { this.delegate = delegate }
    }

    /**
     * The photo library: one video, handed over the moment it is picked ([VideoPickerDelegate]), its file made later. The video is asked
     * for as the library keeps it (`current`): left to itself (`automatic`) the library may re-encode it first, and that is minutes of
     * «Добавляем видео…» (spec 5.13, 0.94); a trim, a slow motion or a cinematic video it renders all the same.
     */
    fun videoPicker(delegate: VideoPickerDelegate): PHPickerViewController {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.videosFilter
            selectionLimit = 1
            preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
        }
        return PHPickerViewController(configuration).apply { this.delegate = delegate }
    }

    fun cameraAvailable(): Boolean =
        UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    /**
     * The system camera; a video in [quality] — told, or it shoots in its own default of 480 × 360 (spec 3.19, 5.13). 1080p is the best
     * the camera has: the picker names no 1080p of its own.
     */
    fun camera(video: Boolean, delegate: CameraDelegate, quality: VideoQuality = VideoQuality.P720): UIImagePickerController = UIImagePickerController().apply {
        sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (video) {
            mediaTypes = listOf(MOVIE_TYPE)
            cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModeVideo
            videoQuality = when (quality) {
                VideoQuality.P480 -> UIImagePickerControllerQualityType640x480
                VideoQuality.P720 -> UIImagePickerControllerQualityTypeIFrame1280x720
                VideoQuality.P1080 -> UIImagePickerControllerQualityTypeHigh
            }
        }
        this.delegate = delegate
    }

    fun audioPicker(delegate: DocumentDelegate): UIDocumentPickerViewController =
        UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeAudio), asCopy = true).apply { this.delegate = delegate }

    fun share(path: String) {
        present(UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null))
    }

    private const val MOVIE_TYPE = "public.movie"

    /** A menu closes within a frame or two; its dismissal animation within a few tenths of a second. */
    private const val SETTLE_STEP_NANOS = 100_000_000L
    private const val SETTLE_ATTEMPTS = 10
}

/** Answers with the `file:` URIs of copies of what was picked; an empty list when the picker was closed. */
@OptIn(ExperimentalForeignApi::class)
internal class MediaPickerDelegate(private val typeIdentifier: String, private val onPicked: (List<String>) -> Unit) :
    NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            onPicked(emptyList())
            return
        }
        val copies = arrayOfNulls<String>(results.size)
        var left = results.size
        results.forEachIndexed { index, result ->
            // the picker lends a file that is gone once its callback returns: it is copied first
            result.itemProvider.loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, _ ->
                copies[index] = url?.let(::copyToTemporary)
                dispatch_async(dispatch_get_main_queue()) {
                    left--
                    if (left == 0) onPicked(copies.filterNotNull())
                }
            }
        }
    }
}

/** The photo or the video of the camera written to [outPath]; true when there is one. */
@OptIn(ExperimentalForeignApi::class)
internal class CameraDelegate(private val video: Boolean, private val outPath: () -> String?, private val onDone: (Boolean) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val path = outPath()
        val photo = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        when {
            path == null -> onDone(false)
            video -> onDone(
                (didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL)?.let { shot ->
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                    NSFileManager.defaultManager.moveItemAtURL(shot, NSURL.fileURLWithPath(path), null)
                } == true,
            )
            photo == null -> onDone(false)
            // A full shot takes a moment to encode: not on the main thread, where the camera is sliding away meanwhile.
            // The answer still comes on the main thread.
            else -> dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) {
                val written = autoreleasepool { UIImageJPEGRepresentation(photo, JPEG_QUALITY)?.writeToFile(path, atomically = true) == true }
                dispatch_async(dispatch_get_main_queue()) { onDone(written) }
            }
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onDone(false)
    }
}

private const val JPEG_QUALITY = 0.95

/**
 * A file picked in Files, copied by the system for the app: the `file:` URI of the app's copy under the file's own name
 * ([copyKeepingName]), or null when nothing was picked.
 */
@OptIn(ExperimentalForeignApi::class)
internal class DocumentDelegate(private val onPicked: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let { copyKeepingName(it) })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onPicked(null)
}

/**
 * The app's own copy of a file picked in Files, under the file's own name — the name of a backing is its title (spec 3.32) —
 * in a folder of its own inside [folder] (`tmp/picked/`, [PickedCopies]), which keeps two picks of one name apart. Its
 * `file:` URI; null when it cannot be made. The picker (`asCopy`) has put a copy into the app's tmp already, and that copy is
 * the app's to keep: it is moved, not copied again — no seconds of copying on the main thread, no second copy of hundreds of
 * megabytes, nothing left in the picker's Inbox. A lent file that cannot be moved is copied. The importer moves the result
 * in or deletes it, and lets the folder of the pick go.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun copyKeepingName(lent: NSURL, folder: String = PickedCopies.root()): String? {
    val name = lent.lastPathComponent?.takeIf { it.isNotBlank() && '/' !in it } ?: return copyToTemporary(lent)
    val manager = NSFileManager.defaultManager
    val home = PickedCopies.newFolder(folder.trimEnd('/')) ?: return null
    val copy = NSURL.fileURLWithPath("$home/$name")
    if (manager.moveItemAtURL(lent, copy, null)) return copy.absoluteString
    // a move that failed halfway may have left a piece behind; the folder is ours alone
    manager.removeItemAtURL(copy, null)
    if (manager.copyItemAtURL(lent, copy, null)) return copy.absoluteString
    manager.removeItemAtPath(home, null)
    return null
}

/** A copy of [lent] in a pick of its own in `tmp/picked/`, under a new name with the lent file's extension: its `file:` URI. */
@OptIn(ExperimentalForeignApi::class)
internal fun copyToTemporary(lent: NSURL): String? {
    val extension = lent.pathExtension?.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
    return PickedCopies.copy(lent, "${NSUUID().UUIDString}$extension")?.let { NSURL.fileURLWithPath(it).absoluteString }
}
