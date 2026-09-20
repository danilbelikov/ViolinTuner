package com.example.violintuner.feature.repertoire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.core.domain.repertoire.SectionRef

@Composable
fun SectionRoute(
    onOpenPiece: (pieceId: Long) -> Unit,
    onNew: (SectionRef) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RepertoireViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenPiece by rememberUpdatedState(onOpenPiece)
    val currentOnNew by rememberUpdatedState(onNew)
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is RepertoireEffect.OpenPiece -> currentOnOpenPiece(effect.id)
                    is RepertoireEffect.OpenNew -> currentOnNew(effect.section)
                    RepertoireEffect.Close -> currentOnClose()
                }
            }
        }
    }
    SectionScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
