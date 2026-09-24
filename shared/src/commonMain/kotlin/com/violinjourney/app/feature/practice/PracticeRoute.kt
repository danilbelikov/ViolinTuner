package com.violinjourney.app.feature.practice

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
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
import com.violinjourney.app.feature.home.HomeLookViewModel
import com.violinjourney.app.feature.journey.JourneyWindowCard
import com.violinjourney.app.feature.journey.LocalHomeLook
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_too_short
import com.violinjourney.app.shared.resources.profile_photo_failed
import org.jetbrains.compose.resources.getString

@Composable
fun PracticeRoute(
    onOpenLive: () -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenJourney: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PracticeViewModel,
    homeLookViewModel: HomeLookViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val journey by viewModel.journeyWindow.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenLive by rememberUpdatedState(onOpenLive)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnOpenJourney by rememberUpdatedState(onOpenJourney)
    val currentOnOpenHome by rememberUpdatedState(onOpenHome)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val homeLook by homeLookViewModel.state.collectAsStateWithLifecycle()

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
                        messages.show(getString(Res.string.practice_too_short))
                    PracticeEffect.ShowPhotoFailed ->
                        messages.show(getString(Res.string.profile_photo_failed))
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
