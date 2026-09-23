package com.violinjourney.app.feature.repertoire.piece

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.permission.isMicPermissionGranted
import com.violinjourney.app.core.ui.analytics.AnalyticsViewModel
import com.violinjourney.app.core.ui.permission.rememberMicPermissionRequester
import com.violinjourney.app.feature.history.SelectionIntent
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.feature.share.ShareHost
import com.violinjourney.app.feature.share.ShareViewModel
import java.io.File

/** Entry point of a piece: owns the view model, its effects and the two system screens that add photos. */
@Composable
fun PieceRoute(
    onClose: () -> Unit,
    onOpenForm: (pieceId: Long, focusNotes: Boolean, scale: Boolean) -> Unit,
    onOpenStand: (pieceId: Long, pageIndex: Int) -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenSound: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCapture: (pieceId: Long) -> Unit = {},
    viewModel: PieceViewModel = hiltViewModel(),
    shareViewModel: ShareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val take by viewModel.takeState.collectAsStateWithLifecycle()
    val videoImport by viewModel.videoImport.collectAsStateWithLifecycle()
    val backing by viewModel.backing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenForm by rememberUpdatedState(onOpenForm)
    val currentOnOpenStand by rememberUpdatedState(onOpenStand)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnOpenCapture by rememberUpdatedState(onOpenCapture)

    val tracking = hiltViewModel<AnalyticsViewModel>()
    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = true, onAnswer = tracking::onMicPermissionAnswered) { granted ->
        viewModel.onIntent(PieceIntent.MicPermissionChanged(granted))
    }
    // Also catches a permission revoked or granted in the system settings while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(PieceIntent.MicPermissionChanged(context.isMicPermissionGranted()))
        viewModel.onIntent(PieceIntent.ScreenResumed)
    }
    // A take is played with the hands on the violin: the screen must not dim under it.
    if (take.recording) {
        DisposableEffect(activity) {
            val window = activity?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    // The picker needs no permission: it hands over only what was picked. The system camera does since the app has a
    // camera of its own (spec 3.32): an app that declares CAMERA may not start another's camera without it granted.
    var afterCameraPermission by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        afterCameraPermission?.invoke(granted)
        afterCameraPermission = null
        if (!granted) Toast.makeText(context, R.string.camera_no_permission, Toast.LENGTH_LONG).show()
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
        viewModel.onIntent(PieceIntent.PhotosPicked(uris.map { it.toString() }))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        viewModel.onIntent(PieceIntent.CameraFinished(saved))
    }

    // A video take (spec 3.19): the system camera (the permission above) and the system picker.
    val videoCamera = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        viewModel.onIntent(PieceIntent.VideoShotFinished(saved))
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onIntent(PieceIntent.VideoPicked(uri?.toString()))
    }
    // A backing (spec 3.32): any sound file the system can hand over; no permission, the pick is the permission.
    val backingPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onIntent(PieceIntent.BackingPicked(uri?.toString()))
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PieceEffect.Close -> currentOnClose()
                    is PieceEffect.OpenForm -> currentOnOpenForm(effect.pieceId, effect.focusNotes, effect.scale)
                    is PieceEffect.OpenStand -> currentOnOpenStand(effect.pieceId, effect.pageIndex)
                    is PieceEffect.LaunchCamera -> withCamera(
                        launch = { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}$FILES_AUTHORITY_SUFFIX", File(effect.filePath))) },
                        refused = { viewModel.onIntent(PieceIntent.CameraFinished(saved = false)) },
                    )
                    PieceEffect.ShowPhotoFailed -> Toast.makeText(context, R.string.profile_photo_failed, Toast.LENGTH_SHORT).show()
                    PieceEffect.RequestMicPermission -> requestMicPermission()
                    PieceEffect.ShowNoNotesRecorded -> Toast.makeText(context, R.string.record_no_notes, Toast.LENGTH_SHORT).show()
                    is PieceEffect.OpenSession -> currentOnOpenSession(effect.sessionId)
                    is PieceEffect.LaunchVideoCamera -> withCamera(
                        launch = { videoCamera.launch(FileProvider.getUriForFile(context, "${context.packageName}$FILES_AUTHORITY_SUFFIX", File(effect.filePath))) },
                        refused = { viewModel.onIntent(PieceIntent.VideoShotFinished(saved = false)) },
                    )
                    is PieceEffect.ShareVideo -> context.shareVideo(File(effect.filePath))
                    PieceEffect.PickBackingFile -> backingPicker.launch(arrayOf(AUDIO_TYPES))
                    is PieceEffect.OpenCapture -> currentOnOpenCapture(effect.pieceId)
                }
            }
        }
    }

    // The selection mode does not outlive the screen (spec 3.18); a rotation only rebuilds it and keeps the mode.
    BackHandler(enabled = state.selection.active) { viewModel.onIntent(PieceIntent.Select(SelectionIntent.Closed)) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.onIntent(PieceIntent.Select(SelectionIntent.Closed))
    }

    val addPhoto = remember(viewModel, gallery) {
        AddPhotoActions(
            onCamera = { viewModel.onIntent(PieceIntent.CameraClicked) },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
    PieceScreen(
        state = state, take = take, onIntent = viewModel::onIntent, addPhoto = addPhoto, modifier = modifier,
        takeActions = remember(shareViewModel, onOpenSound, viewModel) { CardActions(onShare = shareViewModel::start, onSound = onOpenSound, onBest = { viewModel.onIntent(PieceIntent.BestToggled(it)) }) },
        videoImport = videoImport,
        onPickVideo = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
        backing = backing,
    )
    ShareHost(shareViewModel)
}

/** Matches `android:authorities` of the FileProvider in the manifest. */
private const val FILES_AUTHORITY_SUFFIX = ".files"

/** A shot that did not become a take goes to the system sheet as it is — the only way to keep it (spec 3.19). */
private fun Context.shareVideo(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName$FILES_AUTHORITY_SUFFIX", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = VIDEO_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.share_no_app, Toast.LENGTH_SHORT).show()
    }
}

private const val VIDEO_TYPE = "video/mp4"
private const val AUDIO_TYPES = "audio/*"
