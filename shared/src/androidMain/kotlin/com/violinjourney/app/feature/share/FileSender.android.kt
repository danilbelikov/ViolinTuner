package com.violinjourney.app.feature.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.violinjourney.app.core.audio.share.ShareNames
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.share_chooser
import com.violinjourney.app.shared.resources.share_no_app
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
actual fun rememberFileSender(): (file: PlatformFile, text: String?) -> Unit {
    val context = LocalContext.current
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    return remember(context) {
        { file, text -> scope.launch { context.send(file, text) { messages.show(it) } } }
    }
}

/** The receiver gets a temporary grant to read this one file; the app asks for no permission (spec 3.17). */
private suspend fun Context.send(file: PlatformFile, text: String?, say: (String) -> Unit) {
    val uri = FileProvider.getUriForFile(this, "$packageName$FILES_AUTHORITY_SUFFIX", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (file.name.endsWith(ShareNames.VIDEO_EXTENSION)) VIDEO_TYPE else AUDIO_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
        // the chooser reads the grant and the preview from the clip data
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, getString(Res.string.share_chooser)))
    } catch (e: ActivityNotFoundException) {
        say(getString(Res.string.share_no_app))
    }
}

private const val AUDIO_TYPE = "audio/mp4"
private const val VIDEO_TYPE = "video/mp4"

/** Matches `android:authorities` of the FileProvider in the manifest. */
private const val FILES_AUTHORITY_SUFFIX = ".files"
