package com.violinjourney.app.feature.repertoire.stand

import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.permission.isMicPermissionGranted
import com.violinjourney.app.core.ui.permission.rememberMicPermissionRequester
import com.violinjourney.app.feature.repertoire.piece.PieceEffect
import com.violinjourney.app.feature.repertoire.piece.PieceIntent
import com.violinjourney.app.feature.repertoire.piece.PieceViewModel
import kotlinx.coroutines.launch

/**
 * Entry point of the music stand. [pieceViewModel] is the view model of the piece screen lying
 * underneath, not a new one: the take belongs to it, which is why walking between the piece and
 * its stand does not break a recording (spec 3.15).
 */
@Composable
fun StandRoute(
    pieceViewModel: PieceViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StandViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val take by pieceViewModel.takeState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)

    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = true) { granted ->
        pieceViewModel.onIntent(PieceIntent.MicPermissionChanged(granted))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        pieceViewModel.onIntent(PieceIntent.MicPermissionChanged(context.isMicPermissionGranted()))
    }
    // Notes are read with the hands on the violin: the screen stays on for as long as the stand is open.
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    if (state.showHint) {
        LaunchedEffect(Unit) { Toast.makeText(context, R.string.stand_hint, Toast.LENGTH_LONG).show() }
    }

    LaunchedEffect(viewModel, pieceViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        StandEffect.Close -> currentOnClose()
                    }
                }
            }
            // While the stand is on top, the piece screen is not listening: what its view model has to say is said here.
            pieceViewModel.effects.collect { effect ->
                when (effect) {
                    PieceEffect.RequestMicPermission -> requestMicPermission()
                    PieceEffect.ShowNoNotesRecorded -> Toast.makeText(context, R.string.record_no_notes, Toast.LENGTH_SHORT).show()
                    PieceEffect.ShowPhotoFailed -> Toast.makeText(context, R.string.profile_photo_failed, Toast.LENGTH_SHORT).show()
                    // Navigation of the piece screen: nothing on the stand asks for it.
                    PieceEffect.Close, is PieceEffect.OpenForm, is PieceEffect.OpenStand, is PieceEffect.LaunchCamera, is PieceEffect.OpenSession,
                    // and there is no video take from the stand: the camera takes the screen the music is on (spec 3.19)
                    is PieceEffect.LaunchVideoCamera, is PieceEffect.ShareVideo,
                    // nor a backing: it is the piece screen's (spec 3.32)
                    PieceEffect.PickBackingFile, is PieceEffect.OpenCapture,
                    -> Unit
                }
            }
        }
    }

    StandScreen(
        state = state,
        take = take,
        onIntent = viewModel::onIntent,
        onRecordClick = { pieceViewModel.onIntent(PieceIntent.StandRecordClicked) },
        modifier = modifier,
    )
}
