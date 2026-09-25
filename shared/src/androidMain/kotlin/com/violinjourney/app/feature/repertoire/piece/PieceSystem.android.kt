package com.violinjourney.app.feature.repertoire.piece

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.camera_no_permission
import com.violinjourney.app.shared.resources.share_chooser
import com.violinjourney.app.shared.resources.share_no_app
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
actual fun rememberPieceSystem(
    onPhotosPicked: (uris: List<String>) -> Unit,
    onCameraFinished: (saved: Boolean) -> Unit,
    onVideoShot: (saved: Boolean) -> Unit,
    onVideoPicked: (uri: String?) -> Unit,
    onBackingPicked: (uri: String?) -> Unit,
): PieceSystem {
    val context = LocalContext.current
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    val photosPicked by rememberUpdatedState(onPhotosPicked)
    val cameraFinished by rememberUpdatedState(onCameraFinished)
    val videoShot by rememberUpdatedState(onVideoShot)
    val videoPicked by rememberUpdatedState(onVideoPicked)
    val backingPicked by rememberUpdatedState(onBackingPicked)

    // The picker needs no permission: it hands over only what was picked. The system camera does since the app has a
    // camera of its own (spec 3.32): an app that declares CAMERA may not start another's camera without it granted.
    var afterCameraPermission by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        afterCameraPermission?.invoke(granted)
        afterCameraPermission = null
        if (!granted) scope.launch { messages.showLong(getString(Res.string.camera_no_permission)) }
    }
    fun withCamera(launch: () -> Unit, refused: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launch()
        } else {
            afterCameraPermission = { granted -> if (granted) launch() else refused() }
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        photosPicked(uris.map { it.toString() })
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved -> cameraFinished(saved) }
    // A video take (spec 3.19): the system camera (the permission above) and the system picker.
    val videoCamera = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved -> videoShot(saved) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> videoPicked(uri?.toString()) }
    // A backing (spec 3.32): any sound file the system can hand over; no permission, the pick is the permission.
    val backingPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> backingPicked(uri?.toString()) }

    return remember(context) {
        PieceSystem(
            launchCamera = { path ->
                withCamera(launch = { camera.launch(context.providedUri(path)) }, refused = { cameraFinished(false) })
            },
            launchVideoCamera = { path ->
                withCamera(launch = { videoCamera.launch(context.providedUri(path)) }, refused = { videoShot(false) })
            },
            pickPhotos = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            pickVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            pickBacking = { backingPicker.launch(arrayOf(AUDIO_TYPES)) },
            shareVideo = { path -> scope.launch { context.shareVideo(File(path)) { messages.show(it) } } },
        )
    }
}

private fun Context.providedUri(path: String) = FileProvider.getUriForFile(this, "$packageName$FILES_AUTHORITY_SUFFIX", File(path))

private suspend fun Context.shareVideo(file: File, say: (String) -> Unit) {
    val uri = providedUri(file.path)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = VIDEO_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, getString(Res.string.share_chooser)))
    } catch (_: ActivityNotFoundException) {
        say(getString(Res.string.share_no_app))
    }
}

/** Matches `android:authorities` of the FileProvider in the manifest. */
private const val FILES_AUTHORITY_SUFFIX = ".files"
private const val VIDEO_TYPE = "video/mp4"
private const val AUDIO_TYPES = "audio/*"
