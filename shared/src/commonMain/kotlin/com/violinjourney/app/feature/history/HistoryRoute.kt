package com.violinjourney.app.feature.history

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.feature.repertoire.sections.SectionsEffect
import com.violinjourney.app.feature.repertoire.sections.SectionsViewModel

@Composable
fun HistoryRoute(
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenSound: (sessionId: Long) -> Unit,
    onOpenSection: (SectionRef) -> Unit,
    onOpenPiece: (pieceId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel,
    sectionsViewModel: SectionsViewModel,
    /** «Поделиться» of a card (spec 3.17): the platform prepares the file and hands it to other apps. */
    onShare: (sessionId: Long) -> Unit,
    /** Where sharing shows how it goes; drawn over the screen. */
    shareHost: @Composable () -> Unit = {},
    /** True while the screen is only rebuilt (a rotation on Android): the selection survives that. */
    changingConfigurations: () -> Boolean = { false },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sections by sectionsViewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenSection by rememberUpdatedState(onOpenSection)
    val currentOnOpenPiece by rememberUpdatedState(onOpenPiece)
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is HistoryEffect.OpenSession -> currentOnOpenSession(effect.id)
                }
            }
        }
    }

    LaunchedEffect(sectionsViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            sectionsViewModel.effects.collect { effect ->
                when (effect) {
                    is SectionsEffect.OpenSection -> currentOnOpenSection(effect.ref)
                    is SectionsEffect.OpenPiece -> currentOnOpenPiece(effect.id)
                }
            }
        }
    }
    // The selection mode does not outlive the screen (spec 3.18): another tab, the app in the
    // background. A rotation stops the screen too, but only to rebuild it — that keeps the mode.
    BackHandler(enabled = state.selection.active) { viewModel.onIntent(HistoryIntent.Select(SelectionIntent.Closed)) }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !changingConfigurations()) {
                viewModel.onIntent(HistoryIntent.Select(SelectionIntent.Closed))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The bars of the chart rise once; with «убрать анимации» they simply stand (spec 5.15).
    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved()) {
        HistoryScreen(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = modifier,
            sections = sections,
            onSectionsIntent = sectionsViewModel::onIntent,
            cardActions = remember(onShare, onOpenSound, viewModel) {
                CardActions(onShare = onShare, onSound = onOpenSound, onBest = { viewModel.onIntent(HistoryIntent.BestToggled(it)) })
            },
        )
    }
    shareHost()
}
