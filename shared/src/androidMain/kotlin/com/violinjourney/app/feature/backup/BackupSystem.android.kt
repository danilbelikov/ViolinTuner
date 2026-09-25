package com.violinjourney.app.feature.backup

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_share
import com.violinjourney.app.shared.resources.privacy_no_browser
import com.violinjourney.app.shared.resources.share_no_app
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

private const val ZIP_TYPE = "application/zip"
private const val FILES_AUTHORITY_SUFFIX = ".files"

/** What the system's «Открыть» is asked for: a copy is a zip, but file managers and clouds call a zip all sorts of things. */
val BACKUP_FILE_TYPES = arrayOf(ZIP_TYPE, "application/x-zip-compressed", "application/octet-stream", "*/*")

@Composable
actual fun rememberBackupSystem(onPlacePicked: (uri: String?) -> Unit, onCopyPicked: (uri: String?) -> Unit): BackupSystem {
    val context = LocalContext.current
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val placePicked by rememberUpdatedState(onPlacePicked)
    val copyPicked by rememberUpdatedState(onCopyPicked)
    val place = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { uri -> placePicked(uri?.toString()) }
    val copy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> copyPicked(uri?.toString()) }
    return remember(context) {
        BackupSystem(
            pickPlace = { fileName -> place.launch(fileName) },
            pickCopy = { copy.launch(BACKUP_FILE_TYPES) },
            shareFile = { path -> scope.launch { context.shareBackup(File(path)) { messages.show(it) } } },
            restart = context::restartApp,
            openPrivacyPolicy = { scope.launch { context.openPrivacyPolicy { messages.show(it) } } },
        )
    }
}

/**
 * The process starts anew: the copy lies unpacked and marked, and the start of the next process
 * puts it in place before anything has opened the database (spec 5.14). The launcher activity is
 * asked for first, the process ends after.
 */
private fun Context.restartApp() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) startActivity(intent)
    exitProcess(0)
}

private suspend fun Context.shareBackup(file: File, say: (String) -> Unit) {
    val uri = FileProvider.getUriForFile(this, "$packageName$FILES_AUTHORITY_SUFFIX", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = ZIP_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, getString(Res.string.backup_share)))
    } catch (_: ActivityNotFoundException) {
        say(getString(Res.string.share_no_app))
    }
}

private suspend fun Context.openPrivacyPolicy(say: (String) -> Unit) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
    } catch (_: ActivityNotFoundException) {
        say(getString(Res.string.privacy_no_browser))
    }
}
