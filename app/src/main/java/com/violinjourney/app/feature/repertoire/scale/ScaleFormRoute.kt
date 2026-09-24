package com.violinjourney.app.feature.repertoire.scale

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.R

@Composable
fun ScaleFormRoute(
    onClose: () -> Unit,
    onOpenScale: (pieceId: Long) -> Unit,
    onCloseDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScaleFormViewModel = hiltViewModel<HiltScaleFormViewModel>(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
                    ScaleFormEffect.ShowLocked -> Toast.makeText(context, R.string.scale_locked, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    ScaleFormScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
