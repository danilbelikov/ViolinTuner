package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val IMAGE_TYPE = "public.image"

@Composable
actual fun rememberImagePicker(onPicked: (String) -> Unit): () -> Unit {
    val current by rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate { path -> current(path) } }
    return remember(delegate) { { present(delegate) } }
}

private fun present(delegate: PickerDelegate) {
    val configuration = PHPickerConfiguration().apply {
        filter = PHPickerFilter.imagesFilter
        selectionLimit = 1
    }
    val picker = PHPickerViewController(configuration).apply { this.delegate = delegate }
    topController()?.presentViewController(picker, animated = true, completion = null)
}

private fun topController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

/**
 * The picker lends the picture as a file that is gone once its callback returns: it is copied into the app's temporary
 * folder first, and that copy is what the app imports.
 */
@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(private val onPath: (String) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadFileRepresentationForTypeIdentifier(IMAGE_TYPE) { url, _ ->
            val lent = url ?: return@loadFileRepresentationForTypeIdentifier
            val extension = lent.pathExtension?.takeIf { it.isNotEmpty() } ?: "jpg"
            val copy = NSURL.fileURLWithPath("${NSTemporaryDirectory()}${NSUUID().UUIDString}.$extension")
            if (NSFileManager.defaultManager.copyItemAtURL(lent, copy, null)) {
                val path = copy.path ?: return@loadFileRepresentationForTypeIdentifier
                dispatch_async(dispatch_get_main_queue()) { onPath(path) }
            }
        }
    }
}
