package com.example.violintuner.feature.repertoire.piece

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.R
import com.example.violintuner.core.ui.permission.isMicPermissionGranted
import com.example.violintuner.core.ui.permission.rememberMicPermissionRequester
import com.example.violintuner.feature.history.SelectionIntent
import com.example.violintuner.feature.history.components.CardActions
import com.example.violintuner.feature.share.ShareHost
import com.example.violintuner.feature.share.ShareViewModel
import java.io.File

/** Entry point of a piece: owns the view model, its effects and the two system screens that add photos. */
@Composable
fun PieceRoute(
    onClose: () -> Unit,
    onOpenForm: (pieceId: Long, focusNotes: Boolean) -> Unit,
    onOpenStand: (pieceId: Long, pageIndex: Int) -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenSound: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PieceViewModel = hiltViewModel(),
    shareViewModel: ShareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val take by viewModel.takeState.collectAsStateWithLifecycle()
    val takeSounds by viewModel.takeSounds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenForm by rememberUpdatedState(onOpenForm)
    val currentOnOpenStand by rememberUpdatedState(onOpenStand)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)

    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = true) { granted ->
        viewModel.onIntent(PieceIntent.MicPermissionChanged(granted))
    }
    // Also catches a permission revoked or granted in the system settings while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(PieceIntent.MicPermissionChanged(context.isMicPermissionGranted()))
    }
    // A take is played with the hands on the violin: the screen must not dim under it.
    if (take.recording) {
        DisposableEffect(activity) {
            val window = activity?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    // Neither needs a permission: the picker hands over only what was picked, and the system
    // camera is another app writing into a file lent to it for the occasion.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.onIntent(PieceIntent.PhotosPicked(uris.map { it.toString() }))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        viewModel.onIntent(PieceIntent.CameraFinished(saved))
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PieceEffect.Close -> currentOnClose()
                    is PieceEffect.OpenForm -> currentOnOpenForm(effect.pieceId, effect.focusNotes)
                    is PieceEffect.OpenStand -> currentOnOpenStand(effect.pieceId, effect.pageIndex)
                    is PieceEffect.LaunchCamera -> camera.launch(
                        FileProvider.getUriForFile(context, "${context.packageName}$FILES_AUTHORITY_SUFFIX", File(effect.filePath)),
                    )
                    PieceEffect.ShowPhotoFailed -> Toast.makeText(context, R.string.profile_photo_failed, Toast.LENGTH_SHORT).show()
                    PieceEffect.RequestMicPermission -> requestMicPermission()
                    PieceEffect.ShowNoNotesRecorded -> Toast.makeText(context, R.string.record_no_notes, Toast.LENGTH_SHORT).show()
                    is PieceEffect.OpenSession -> currentOnOpenSession(effect.sessionId)
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
        takeSounds = takeSounds,
        takeActions = remember(shareViewModel, onOpenSound) { CardActions(onShare = shareViewModel::start, onSound = onOpenSound) },
    )
    ShareHost(shareViewModel)
}

/** Matches `android:authorities` of the FileProvider in the manifest. */
private const val FILES_AUTHORITY_SUFFIX = ".files"
