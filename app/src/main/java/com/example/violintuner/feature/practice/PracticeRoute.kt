package com.example.violintuner.feature.practice

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.feature.home.HomeLookViewModel
import com.example.violintuner.feature.journey.JourneyWindowCard
import com.example.violintuner.feature.journey.LocalHomeLook
import com.example.violintuner.core.ui.motion.rememberAnimationsRemoved

@Composable
fun PracticeRoute(
    onOpenLive: () -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenJourney: () -> Unit,
    onOpenHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val journey by viewModel.journeyWindow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenLive by rememberUpdatedState(onOpenLive)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnOpenJourney by rememberUpdatedState(onOpenJourney)
    val currentOnOpenHome by rememberUpdatedState(onOpenHome)
    val homeLook by hiltViewModel<HomeLookViewModel>().state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PracticeEffect.OpenLive -> currentOnOpenLive()
                    is PracticeEffect.OpenSession -> currentOnOpenSession(effect.id)
                    PracticeEffect.OpenJourney -> currentOnOpenJourney()
                    PracticeEffect.OpenHome -> currentOnOpenHome()
                    PracticeEffect.ShowTooShort ->
                        Toast.makeText(context, R.string.practice_too_short, Toast.LENGTH_SHORT).show()
                    PracticeEffect.ShowPhotoFailed ->
                        Toast.makeText(context, R.string.profile_photo_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Decorative motion of this screen (spec 3.16) follows the system setting «убрать анимации».
    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved(), LocalHomeLook provides homeLook) {
        PracticeScreen(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = modifier,
            journeyCard = { compact ->
                journey?.let { window ->
                    // the window is always the home (spec 3.25): the road is taken from there
                    JourneyWindowCard(window, compact, onClick = { viewModel.onIntent(PracticeIntent.HomeClicked) })
                }
            },
        )
    }
}
