package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.violinjourney.app.core.io.PickedCopies
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
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
    SystemScreens.present(picker)
}

/**
 * The picker lends the picture as a file that is gone once its callback returns: it is copied into `tmp/picked/` first
 * ([PickedCopies]), and that copy is what the app imports; the importer lets it go once read.
 */
@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(private val onPath: (String) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadFileRepresentationForTypeIdentifier(IMAGE_TYPE) { url, _ ->
            val lent = url ?: return@loadFileRepresentationForTypeIdentifier
            val extension = lent.pathExtension?.takeIf { it.isNotEmpty() } ?: "jpg"
            val path = PickedCopies.copy(lent, "${NSUUID().UUIDString}.$extension") ?: return@loadFileRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) { onPath(path) }
        }
    }
}
