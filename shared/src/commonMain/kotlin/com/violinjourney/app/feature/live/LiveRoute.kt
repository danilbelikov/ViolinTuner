package com.violinjourney.app.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
import com.violinjourney.app.core.ui.permission.rememberMicPermissionCheck
import com.violinjourney.app.core.ui.permission.rememberMicPermissionRequester
import com.violinjourney.app.feature.home.HomeLookViewModel
import com.violinjourney.app.feature.journey.LocalHomeLook
import com.violinjourney.app.feature.live.block.BlockEffect
import com.violinjourney.app.feature.live.block.BlockViewModel
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.record_no_notes
import org.jetbrains.compose.resources.getString

/** Entry point of the Live destination: owns the ViewModel, its effects and the mic permission. */
@Composable
fun LiveRoute(
    onOpenSession: (sessionId: Long) -> Unit,
    onFinishPractice: () -> Unit,
    onOpenRepertoire: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel,
    blockViewModel: BlockViewModel,
    homeLookViewModel: HomeLookViewModel,
    tracking: AnalyticsViewModel,
    /** Live in the room and in the halls (spec 3.27); false — the plain field of the zone (`-PplainLive=true`). */
    showVenue: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val block by blockViewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenRepertoire by rememberUpdatedState(onOpenRepertoire)
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnFinishPractice by rememberUpdatedState(onFinishPractice)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)

    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = true, onAnswer = tracking::onMicPermissionAnswered) { granted ->
        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted))
    }

    // The player's hands are busy: the screen must not dim while Live is open (spec 3.6).
    KeepScreenOn()

    // Also catches a permission revoked or granted in the system settings while we were away.
    // "Remove animations" of the system settings; read again on every return, like the permission.
    val reduceMotion = rememberAnimationsRemoved()
    val micPermissionGranted = rememberMicPermissionCheck()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(LiveIntent.MicPermissionChanged(micPermissionGranted()))
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is LiveEffect.OpenSession -> currentOnOpenSession(effect.id)
                    LiveEffect.ShowNoNotesRecorded ->
                        messages.show(getString(Res.string.record_no_notes))
                    LiveEffect.RequestMicPermission -> requestMicPermission()
                    LiveEffect.FinishPractice -> currentOnFinishPractice()
                    LiveEffect.OpenSettings -> currentOnOpenSettings()
                }
            }
        }
    }

    LaunchedEffect(blockViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            blockViewModel.effects.collect { effect ->
                when (effect) {
                    BlockEffect.OpenRepertoire -> currentOnOpenRepertoire()
                }
            }
        }
    }

    // the room of Live is the home as it stands (spec 3.27): the same look the journey and «Занятия» draw
    val homeLook by homeLookViewModel.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalHomeLook provides homeLook, LocalReduceMotion provides reduceMotion) {
        LiveScreen(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = modifier,
            reduceMotion = reduceMotion,
            showVenue = showVenue,
            block = block,
            onBlockIntent = blockViewModel::onIntent,
        )
    }
}

