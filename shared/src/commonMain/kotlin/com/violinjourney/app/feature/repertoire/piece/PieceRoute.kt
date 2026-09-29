package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.ui.analytics.AnalyticsViewModel
import com.violinjourney.app.core.ui.components.KeepScreenOn
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.core.ui.permission.rememberMicPermissionCheck
import com.violinjourney.app.core.ui.permission.rememberMicPermissionRequester
import com.violinjourney.app.feature.history.SelectionIntent
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.profile_photo_failed
import com.violinjourney.app.shared.resources.record_no_notes
import org.jetbrains.compose.resources.getString

/** Entry point of a piece: owns the view model, its effects and the system screens that add photos, videos and a backing. */
@Composable
fun PieceRoute(
    onClose: () -> Unit,
    onOpenForm: (pieceId: Long, focusNotes: Boolean, scale: Boolean) -> Unit,
    onOpenStand: (pieceId: Long, pageIndex: Int) -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenSound: (sessionId: Long) -> Unit,
    viewModel: PieceViewModel,
    tracking: AnalyticsViewModel,
    /** «Поделиться» of a take (spec 3.17): the platform prepares the file and hands it to other apps. */
    onShare: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCapture: (pieceId: Long) -> Unit = {},
    /** Where sharing shows how it goes; drawn over the screen. */
    shareHost: @Composable () -> Unit = {},
    /** True while the screen is only rebuilt (a rotation on Android): the selection survives that. */
    changingConfigurations: () -> Boolean = { false },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // a state, not a value: it changes twenty times a second while a take runs, and only the record row reads it whole
    val take = viewModel.takeState.collectAsStateWithLifecycle()
    val recording by remember(take) { derivedStateOf { take.value.recording } }
    val videoImport by viewModel.videoImport.collectAsStateWithLifecycle()
    val backing by viewModel.backing.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenForm by rememberUpdatedState(onOpenForm)
    val currentOnOpenStand by rememberUpdatedState(onOpenStand)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnOpenCapture by rememberUpdatedState(onOpenCapture)

    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = true, onAnswer = tracking::onMicPermissionAnswered) { granted ->
        viewModel.onIntent(PieceIntent.MicPermissionChanged(granted))
    }
    val micPermissionGranted = rememberMicPermissionCheck()
    // Also catches a permission revoked or granted in the system settings while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(PieceIntent.MicPermissionChanged(micPermissionGranted()))
        viewModel.onIntent(PieceIntent.ScreenResumed)
    }
    // A take is played with the hands on the violin, and the backing listened to often along with it: the screen must not
    // dim under either — its going dark would end the backing's listening (below).
    if (recording || backing?.previewing == true) KeepScreenOn()

    val system = rememberPieceSystem(
        onPhotosPicked = { viewModel.onIntent(PieceIntent.PhotosPicked(it)) },
        onCameraFinished = { viewModel.onIntent(PieceIntent.CameraFinished(it)) },
        onVideoShot = { viewModel.onIntent(PieceIntent.VideoShotFinished(it)) },
        onVideoPicked = { viewModel.onIntent(PieceIntent.VideoPicked(it)) },
        onBackingPicked = { viewModel.onIntent(PieceIntent.BackingPicked(it)) },
    )

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PieceEffect.Close -> currentOnClose()
                    is PieceEffect.OpenForm -> currentOnOpenForm(effect.pieceId, effect.focusNotes, effect.scale)
                    is PieceEffect.OpenStand -> currentOnOpenStand(effect.pieceId, effect.pageIndex)
                    is PieceEffect.LaunchCamera -> system.launchCamera(effect.filePath)
                    PieceEffect.ShowPhotoFailed -> messages.show(getString(Res.string.profile_photo_failed))
                    PieceEffect.RequestMicPermission -> requestMicPermission()
                    PieceEffect.ShowNoNotesRecorded -> messages.show(getString(Res.string.record_no_notes))
                    is PieceEffect.OpenSession -> currentOnOpenSession(effect.sessionId)
                    is PieceEffect.LaunchVideoCamera -> system.launchVideoCamera(effect.filePath)
                    is PieceEffect.ShareVideo -> system.shareVideo(effect.filePath)
                    PieceEffect.PickBackingFile -> system.pickBacking()
                    is PieceEffect.OpenCapture -> currentOnOpenCapture(effect.pieceId)
                }
            }
        }
    }

    // Neither the selection mode (spec 3.18) nor listening to the backing (spec 3.32) outlives the screen — the app going to
    // the background included; a rotation only rebuilds it and keeps both.
    BackHandler(enabled = state.selection.active) { viewModel.onIntent(PieceIntent.Select(SelectionIntent.Closed)) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!changingConfigurations()) {
            viewModel.onIntent(PieceIntent.Select(SelectionIntent.Closed))
            viewModel.onIntent(PieceIntent.LeavingScreen)
        }
    }

    // What the route opens by itself leaves the screen as surely as what the view model opens: the backing listened to
    // stops first, so it neither plays over «Звук» nor on behind a picker (spec 3.32).
    val leaving = remember(viewModel) { { viewModel.onIntent(PieceIntent.LeavingScreen) } }
    val addPhoto = remember(viewModel, system) {
        AddPhotoActions(
            onCamera = { viewModel.onIntent(PieceIntent.CameraClicked) },
            onGallery = { leaving(); system.pickPhotos() },
        )
    }
    PieceScreen(
        state = state, take = take, onIntent = viewModel::onIntent, addPhoto = addPhoto, modifier = modifier,
        takeActions = remember(onShare, onOpenSound, viewModel) {
            CardActions(
                onShare = { leaving(); onShare(it) },
                onSound = { leaving(); onOpenSound(it) },
                // the question over the screen: the backing listened to plays on under it, as under the question of the picked ones
                onDelete = { viewModel.onIntent(PieceIntent.Select(SelectionIntent.DeleteOneClicked(it))) },
                onBest = { viewModel.onIntent(PieceIntent.BestToggled(it)) },
            )
        },
        videoImport = videoImport,
        onPickVideo = remember(viewModel, system) { { leaving(); system.pickVideo() } },
        backing = backing,
    )
    shareHost()
}
