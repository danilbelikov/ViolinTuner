package com.example.violintuner.feature.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.core.ui.motion.rememberAnimationsRemoved
import com.example.violintuner.feature.history.components.CardActions
import com.example.violintuner.feature.share.ShareHost
import com.example.violintuner.core.domain.repertoire.SectionRef
import com.example.violintuner.feature.repertoire.sections.SectionsEffect
import com.example.violintuner.feature.repertoire.sections.SectionsViewModel
import com.example.violintuner.feature.share.ShareViewModel

@Composable
fun HistoryRoute(
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenSound: (sessionId: Long) -> Unit,
    onOpenSection: (SectionRef) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
    sectionsViewModel: SectionsViewModel = hiltViewModel(),
    shareViewModel: ShareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sections by sectionsViewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenSection by rememberUpdatedState(onOpenSection)
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
                }
            }
        }
    }
    // The selection mode does not outlive the screen (spec 3.18): another tab, the app in the
    // background. A rotation stops the screen too, but only to rebuild it — that keeps the mode.
    BackHandler(enabled = state.selection.active) { viewModel.onIntent(HistoryIntent.Select(SelectionIntent.Closed)) }
    val activity = LocalActivity.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) {
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
            cardActions = remember(shareViewModel, onOpenSound, viewModel) {
                CardActions(onShare = shareViewModel::start, onSound = onOpenSound, onBest = { viewModel.onIntent(HistoryIntent.BestToggled(it)) })
            },
        )
    }
    ShareHost(shareViewModel)
}
