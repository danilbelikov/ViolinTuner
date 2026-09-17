package com.example.violintuner.feature.live

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.R

/** Entry point of the Live destination: owns the ViewModel and performs its effects. */
@Composable
fun LiveRoute(
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    LiveEffect.ShowRecordingUnavailable ->
                        Toast.makeText(context, R.string.record_unavailable, Toast.LENGTH_SHORT).show()
                    // Cannot occur yet: NoMicPermission is unreachable while the pitch source is
                    // fake. The permission launcher arrives with MicPitchSource (spec step 4).
                    LiveEffect.RequestMicPermission -> Unit
                }
            }
        }
    }

    LiveScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
