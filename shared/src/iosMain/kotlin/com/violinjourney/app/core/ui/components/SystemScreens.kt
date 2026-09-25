package com.violinjourney.app.core.ui.components

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
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
import platform.darwin.dispatch_get_main_queue
import platform.Foundation.writeToFile

/**
 * The system's own screens the app puts in front of itself on iOS: the photo picker, the camera, the file picker and
 * the sheet of «Поделиться». Each answer comes on the main thread; the delegates are kept by whoever asks, for as long
 * as the screen is up — UIKit holds them weakly.
 */
@OptIn(ExperimentalForeignApi::class)
internal object SystemScreens {
    fun topController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }

    fun present(controller: UIViewController) {
        topController()?.presentViewController(controller, animated = true, completion = null)
    }

    /** The photo library: pictures or videos, up to [limit] (0 — any number); each lent file is copied into the temporary folder. */
    fun mediaPicker(videos: Boolean, limit: Long, delegate: MediaPickerDelegate): PHPickerViewController {
        val configuration = PHPickerConfiguration().apply {
            filter = if (videos) PHPickerFilter.videosFilter else PHPickerFilter.imagesFilter
            selectionLimit = limit
        }
        return PHPickerViewController(configuration).apply { this.delegate = delegate }
    }

    fun cameraAvailable(): Boolean =
        UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    fun camera(video: Boolean, delegate: CameraDelegate): UIImagePickerController = UIImagePickerController().apply {
        sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (video) {
            mediaTypes = listOf(MOVIE_TYPE)
            cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModeVideo
        }
        this.delegate = delegate
    }

    fun audioPicker(delegate: DocumentDelegate): UIDocumentPickerViewController =
        UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeAudio), asCopy = true).apply { this.delegate = delegate }

    fun share(path: String) {
        present(UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null))
    }

    private const val MOVIE_TYPE = "public.movie"
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
        val saved = when {
            path == null -> false
            video -> (didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL)?.let { shot ->
                NSFileManager.defaultManager.removeItemAtPath(path, null)
                NSFileManager.defaultManager.moveItemAtURL(shot, NSURL.fileURLWithPath(path), null)
            } == true
            else -> (didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)?.let { photo ->
                UIImageJPEGRepresentation(photo, JPEG_QUALITY)?.writeToFile(path, atomically = true)
            } == true
        }
        onDone(saved)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onDone(false)
    }
}

private const val JPEG_QUALITY = 0.95

/** A file picked in Files, copied by the system for the app: its `file:` URI, or null when nothing was picked. */
@OptIn(ExperimentalForeignApi::class)
internal class DocumentDelegate(private val onPicked: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(::copyToTemporary))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onPicked(null)
}

@OptIn(ExperimentalForeignApi::class)
private fun copyToTemporary(lent: NSURL): String? {
    val extension = lent.pathExtension?.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
    val copy = NSURL.fileURLWithPath("${NSTemporaryDirectory()}${NSUUID().UUIDString}$extension")
    return if (NSFileManager.defaultManager.copyItemAtURL(lent, copy, null)) copy.absoluteString else null
}
