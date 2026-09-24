package com.violinjourney.app.feature.repertoire.scale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.scale_locked
import org.jetbrains.compose.resources.getString

@Composable
fun ScaleFormRoute(
    onClose: () -> Unit,
    onOpenScale: (pieceId: Long) -> Unit,
    onCloseDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScaleFormViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenScale by rememberUpdatedState(onOpenScale)
    val currentOnCloseDeleted by rememberUpdatedState(onCloseDeleted)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    ScaleFormEffect.Close -> currentOnClose()
                    is ScaleFormEffect.OpenScale -> currentOnOpenScale(effect.pieceId)
                    ScaleFormEffect.CloseDeleted -> currentOnCloseDeleted()
                    ScaleFormEffect.ShowLocked -> messages.show(getString(Res.string.scale_locked))
                }
            }
        }
    }
    ScaleFormScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
