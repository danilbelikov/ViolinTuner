package com.violinjourney.app.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.violinjourney.app.core.backup.IosPickedPlaces
import com.violinjourney.app.core.ui.components.SystemScreens
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject

/**
 * A copy on iOS: its place is a folder picked in Files — the app writes the archive into it, with the access the pick
 * gave — and the copy to bring back is picked in Files and read where it lies (spec 3.20), with no copy of it made by
 * the system first. The app starts anew in place. A copy does not go on while the app is away: iOS gives it seconds.
 */
@Composable
actual fun rememberBackupSystem(onPlacePicked: (uri: String?) -> Unit, onCopyPicked: (uri: String?) -> Unit): BackupSystem {
    val placePicked by rememberUpdatedState(onPlacePicked)
    val copyPicked by rememberUpdatedState(onCopyPicked)
    val restart = LocalAppRestart.current
    // One window per press, as on Android, though UIKit would refuse a second one while the first is coming up: the delegate of
    // a picker answers a pick and a cancel; a picker gone without a word is seen in UIKit's own state (SystemScreens.nothingUp).
    val gate = remember { SystemWindowGate(now = ::uptimeMs, pickerGone = SystemScreens::nothingUp) }
    return remember(restart) { iosBackupSystem(gate, onPlacePicked = { placePicked(it) }, onCopyPicked = { copyPicked(it) }, restart = restart) }
}

/**
 * The system of a copy on iOS behind its [gate] — out of the composition, so that a test drives it with a [present], a [share] and an
 * [openUrl] of its own: each picker goes up through [SystemWindowGate.picker] and answers through [SystemWindowGate.answering] (its
 * delegate, kept here — UIKit holds it weakly), the sheet and the browser go up through [SystemWindowGate.sheet].
 */
internal fun iosBackupSystem(
    gate: SystemWindowGate,
    onPlacePicked: (uri: String?) -> Unit,
    onCopyPicked: (uri: String?) -> Unit,
    restart: () -> Unit,
    present: (UIViewController) -> Unit = SystemScreens::present,
    share: (path: String) -> Unit = SystemScreens::share,
    openUrl: (NSURL) -> Unit = { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null) },
): BackupSystem {
    var fileName = ""
    val folder = PickerDelegate(gate.answering<NSURL> { url -> onPlacePicked(url?.let { IosPickedPlaces.place(it, fileName) }) })
    val copy = PickerDelegate(gate.answering<NSURL> { url -> onCopyPicked(url?.let(IosPickedPlaces::copy)) })
    return BackupSystem(
        pickPlace = { name ->
            gate.picker {
                fileName = name
                present(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder)).apply { delegate = folder })
            }
        },
        pickCopy = {
            gate.picker {
                val types = listOfNotNull(UTTypeZIP, UTTypeData)
                // opened in place: a copy made first would need the room of the whole archive twice, and stay in tmp
                present(UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = false).apply { delegate = copy })
            }
        },
        shareFile = { path -> gate.sheet { share(path) } },
        restart = restart,
        openPrivacyPolicy = { gate.sheet { NSURL.URLWithString(PRIVACY_POLICY_URL)?.let(openUrl) } },
        goesOnInBackground = false,
    )
}

/** Milliseconds since the start of the phone, which only go forward. */
private fun uptimeMs(): Long = (NSProcessInfo.processInfo.systemUptime * MS_PER_SECOND).toLong()

private const val MS_PER_SECOND = 1_000

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(private val onPicked: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onPicked(null)
}
