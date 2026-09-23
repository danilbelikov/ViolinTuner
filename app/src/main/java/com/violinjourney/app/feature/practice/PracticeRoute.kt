package com.violinjourney.app.feature.practice

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
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
import com.violinjourney.app.feature.home.HomeLookViewModel
import com.violinjourney.app.feature.journey.JourneyWindowCard
import com.violinjourney.app.feature.journey.LocalHomeLook

@Composable
fun PracticeRoute(
    onOpenLive: () -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenJourney: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
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
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val homeLook by hiltViewModel<HomeLookViewModel>().state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    PracticeEffect.OpenLive -> currentOnOpenLive()
                    is PracticeEffect.OpenSession -> currentOnOpenSession(effect.id)
                    PracticeEffect.OpenJourney -> currentOnOpenJourney()
                    PracticeEffect.OpenHome -> currentOnOpenHome()
                    PracticeEffect.OpenSettings -> currentOnOpenSettings()
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
                    // the window is where the player is (spec 3.27): the home leads home, a city to the journey
                    JourneyWindowCard(window, compact, onClick = { viewModel.onIntent(if (window.here is Venue.Hall) PracticeIntent.JourneyClicked else PracticeIntent.HomeClicked) })
                }
            },
        )
    }
}
