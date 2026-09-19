package com.example.violintuner.feature.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
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
import com.example.violintuner.feature.history.components.CardActions
import com.example.violintuner.feature.repertoire.RepertoireEffect
import com.example.violintuner.feature.repertoire.RepertoireViewModel
import com.example.violintuner.feature.share.ShareHost
import com.example.violintuner.feature.share.ShareViewModel

@Composable
fun HistoryRoute(
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenSound: (sessionId: Long) -> Unit,
    onOpenPiece: (pieceId: Long) -> Unit,
    onNewPiece: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
    repertoireViewModel: RepertoireViewModel = hiltViewModel(),
    shareViewModel: ShareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val repertoire by repertoireViewModel.state.collectAsStateWithLifecycle()
    val currentOnOpenPiece by rememberUpdatedState(onOpenPiece)
    val currentOnNewPiece by rememberUpdatedState(onNewPiece)
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

    LaunchedEffect(repertoireViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            repertoireViewModel.effects.collect { effect ->
                when (effect) {
                    is RepertoireEffect.OpenPiece -> currentOnOpenPiece(effect.id)
                    RepertoireEffect.OpenNewPiece -> currentOnNewPiece()
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
    HistoryScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        repertoire = repertoire,
        onRepertoireIntent = repertoireViewModel::onIntent,
        cardActions = remember(shareViewModel, onOpenSound) { CardActions(onShare = shareViewModel::start, onSound = onOpenSound) },
    )
    ShareHost(shareViewModel)
}
