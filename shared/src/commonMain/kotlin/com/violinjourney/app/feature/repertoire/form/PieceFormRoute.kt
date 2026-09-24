package com.violinjourney.app.feature.repertoire.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun PieceFormRoute(
    onClose: () -> Unit,
    onOpenCreated: (pieceId: Long) -> Unit,
    onCloseDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PieceFormViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenCreated by rememberUpdatedState(onOpenCreated)
    val currentOnCloseDeleted by rememberUpdatedState(onCloseDeleted)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PieceFormEffect.Close -> currentOnClose()
                    is PieceFormEffect.OpenCreated -> currentOnOpenCreated(effect.pieceId)
                    PieceFormEffect.CloseDeleted -> currentOnCloseDeleted()
                }
            }
        }
    }
    PieceFormScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
