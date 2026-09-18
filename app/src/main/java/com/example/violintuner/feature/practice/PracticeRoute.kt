package com.example.violintuner.feature.practice

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
import com.example.violintuner.R

@Composable
fun PracticeRoute(
    onOpenLive: () -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenLive by rememberUpdatedState(onOpenLive)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PracticeEffect.OpenLive -> currentOnOpenLive()
                    is PracticeEffect.OpenSession -> currentOnOpenSession(effect.id)
                    PracticeEffect.ShowTooShort ->
                        Toast.makeText(context, R.string.practice_too_short, Toast.LENGTH_SHORT).show()
                    PracticeEffect.ShowPhotoFailed ->
                        Toast.makeText(context, R.string.profile_photo_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PracticeScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
