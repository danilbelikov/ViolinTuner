package com.violinjourney.app.feature.events.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
import com.violinjourney.app.feature.backup.rememberSystemWindowGate
import com.violinjourney.app.feature.repertoire.piece.rememberPieceSystem
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.record_no_notes
import org.jetbrains.compose.resources.getString

/**
 * The entry of the screen of an event (spec 3.35, 3.36.9): owns its view model, its effects and the system windows of «Добавить запись» —
 * the camera, the picker of videos, the picker of a sound file (the one of a backing, 3.32) and the sheet that rescues a shot. One system
 * window per press ([rememberSystemWindowGate]): a second tap of a double tap opens nothing over the first. [onOpenForm] — the form of
 * the event: «Изменить», and «Добавить заметку» at its notes.
 */
@Composable
fun EventRoute(
    onClose: () -> Unit,
    onOpenForm: (eventId: Long, focusNotes: Boolean) -> Unit,
    onOpenPiece: (pieceId: Long) -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenRepertoire: () -> Unit,
    viewModel: EventViewModel,
    tracking: AnalyticsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // a state, not a value: it changes twenty times a second while a sound is recorded, and only the bar of the recording reads it whole
    val take = viewModel.takeState.collectAsStateWithLifecycle()
    val recording by remember(take) { derivedStateOf { take.value.recording } }
    val import by viewModel.mediaImport.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenForm by rememberUpdatedState(onOpenForm)
    val currentOnOpenPiece by rememberUpdatedState(onOpenPiece)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnOpenRepertoire by rememberUpdatedState(onOpenRepertoire)

    // «Разрешить доступ» of «Записать звук»: the system asks, or — refused for good — its settings open, as on Live (spec 3.4)
    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = true, onAnswer = tracking::onMicPermissionAnswered) { granted ->
        viewModel.onIntent(EventIntent.MicPermissionChanged(granted))
    }
    val micPermissionGranted = rememberMicPermissionCheck()
    // also a permission given or taken in the system settings while we were away: the row of the sheet wakes, and records only by its press
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(EventIntent.MicPermissionChanged(micPermissionGranted()))
    }
    // a recording is made with the hands on the violin: the screen must not go dark under it
    if (recording) KeepScreenOn()

    val gate = rememberSystemWindowGate()
    val shot = gate.answering<Boolean> { saved -> viewModel.onIntent(EventIntent.VideoShotFinished(saved = saved == true)) }
    val system = rememberPieceSystem(
        onPhotosPicked = {},
        onCameraFinished = {},
        onVideoShot = { saved -> shot(saved) },
        onVideoPicked = gate.answering { viewModel.onIntent(EventIntent.VideoPicked(it)) },
        // the picker of a backing is the picker of a sound file (spec 5.28)
        onBackingPicked = gate.answering { viewModel.onIntent(EventIntent.SoundPicked(it)) },
    )

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    EventEffect.Close -> currentOnClose()
                    is EventEffect.OpenForm -> currentOnOpenForm(effect.eventId, effect.focusNotes)
                    is EventEffect.OpenPiece -> currentOnOpenPiece(effect.pieceId)
                    is EventEffect.OpenSession -> currentOnOpenSession(effect.sessionId)
                    EventEffect.OpenRepertoire -> currentOnOpenRepertoire()
                    EventEffect.RequestMicPermission -> requestMicPermission()
                    // a camera that cannot come up now leaves no file behind: the shot is as if backed out of
                    is EventEffect.LaunchVideoCamera -> if (!gate.picker { system.launchVideoCamera(effect.filePath, effect.quality) }) {
                        viewModel.onIntent(EventIntent.VideoShotFinished(saved = false))
                    }
                    EventEffect.PickVideo -> gate.picker(system.pickVideo)
                    EventEffect.PickSound -> gate.picker(system.pickBacking)
                    is EventEffect.ShareVideo -> gate.sheet { system.shareVideo(effect.filePath) }
                    EventEffect.ShowNoNotes -> messages.show(getString(Res.string.record_no_notes))
                }
            }
        }
    }

    EventScreen(
        state = state,
        take = take,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        import = import,
    )
}
