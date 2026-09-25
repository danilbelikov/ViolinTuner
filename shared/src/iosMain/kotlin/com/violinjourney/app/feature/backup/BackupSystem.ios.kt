package com.violinjourney.app.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.violinjourney.app.core.backup.IosPickedPlaces
import com.violinjourney.app.core.ui.components.SystemScreens
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject

/**
 * A copy on iOS: its place is a folder picked in Files — the app writes the archive into it, with the access the pick
 * gave — and the copy to bring back is picked in Files and copied in by the system. The app starts anew in place.
 */
@Composable
actual fun rememberBackupSystem(onPlacePicked: (uri: String?) -> Unit, onCopyPicked: (uri: String?) -> Unit): BackupSystem {
    val placePicked by rememberUpdatedState(onPlacePicked)
    val copyPicked by rememberUpdatedState(onCopyPicked)
    val restart = LocalAppRestart.current
    return remember(restart) {
        var fileName = ""
        val folder = PickerDelegate { url -> placePicked(url?.let { IosPickedPlaces.place(it, fileName) }) }
        val copy = PickerDelegate { url -> copyPicked(url?.absoluteString) }
        BackupSystem(
            pickPlace = { name ->
                fileName = name
                SystemScreens.present(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder)).apply { delegate = folder })
            },
            pickCopy = {
                val types = listOfNotNull(UTTypeZIP, UTTypeData)
                SystemScreens.present(UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true).apply { delegate = copy })
            },
            shareFile = SystemScreens::share,
            restart = restart,
            openPrivacyPolicy = {
                NSURL.URLWithString(PRIVACY_POLICY_URL)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null) }
            },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(private val onPicked: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onPicked(null)
}
