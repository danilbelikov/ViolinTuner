package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved

/**
 * The tab «Репертуар» (spec 3.36.1, 3.36.4): its view model and the ways out of it — a section's list, the form of a new element
 * picked in «Что добавить?» ([onNew]: `Routes.newElement` — the scales have a form of their own) and, from «Время по элементам», an
 * element's own screen; all open above the tabs.
 */
@Composable
fun SectionsRoute(
    onOpenSection: (SectionRef) -> Unit,
    onOpenPiece: (pieceId: Long) -> Unit,
    onNew: (SectionRef) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SectionsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenSection by rememberUpdatedState(onOpenSection)
    val currentOnOpenPiece by rememberUpdatedState(onOpenPiece)
    val currentOnNew by rememberUpdatedState(onNew)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is SectionsEffect.OpenSection -> currentOnOpenSection(effect.ref)
                    is SectionsEffect.OpenPiece -> currentOnOpenPiece(effect.id)
                    is SectionsEffect.OpenNew -> currentOnNew(effect.section)
                }
            }
        }
    }
    // The bars of «Время по элементам» grow once; with «убрать анимации» they simply stand (spec 3.28).
    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved()) {
        SectionsScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
    }
}
