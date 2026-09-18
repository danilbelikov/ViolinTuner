package com.example.violintuner.feature.repertoire.piece

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.R
import java.io.File

/** Entry point of a piece: owns the view model, its effects and the two system screens that add photos. */
@Composable
fun PieceRoute(
    onClose: () -> Unit,
    onOpenForm: (pieceId: Long, focusNotes: Boolean) -> Unit,
    onOpenStand: (pieceId: Long, pageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PieceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenForm by rememberUpdatedState(onOpenForm)
    val currentOnOpenStand by rememberUpdatedState(onOpenStand)

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
                }
            }
        }
    }

    val addPhoto = remember(viewModel, gallery) {
        AddPhotoActions(
            onCamera = { viewModel.onIntent(PieceIntent.CameraClicked) },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
    PieceScreen(state = state, onIntent = viewModel::onIntent, addPhoto = addPhoto, modifier = modifier)
}

/** Matches `android:authorities` of the FileProvider in the manifest. */
private const val FILES_AUTHORITY_SUFFIX = ".files"
