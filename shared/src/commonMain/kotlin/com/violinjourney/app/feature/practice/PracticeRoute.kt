package com.violinjourney.app.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

@Composable
fun PracticeRoute(
    onOpenLive: () -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenJourney: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEvent: (eventId: Long) -> Unit,
    onOpenEventForm: (date: LocalDate) -> Unit,
    takeEventDate: () -> LocalDate?,
    modifier: Modifier = Modifier,
    viewModel: PracticeViewModel,
    homeLookViewModel: HomeLookViewModel,
) {
    // The reminder is reckoned anew when «Занятия» open (spec 3.36.9) — here, before the first frame reads the state: the state held
    // while the screen was away would show the card of an event over meanwhile, and fade it out on the open screen. Back to the front
    // from the background, the same before the frame (ON_START comes first).
    val opened = remember(viewModel) {
        viewModel.onIntent(PracticeIntent.Opened)
        viewModel.state
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onIntent(PracticeIntent.Opened) }
    val state by opened.collectAsStateWithLifecycle()
    val journey by viewModel.journeyWindow.collectAsStateWithLifecycle()
    // a state, not a value: read by the timer alone, so a tick of the practice clock recomposes only the timer
    val timer = viewModel.timer.collectAsStateWithLifecycle()
    val messages = LocalMessages.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenLive by rememberUpdatedState(onOpenLive)
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnOpenJourney by rememberUpdatedState(onOpenJourney)
    val currentOnOpenHome by rememberUpdatedState(onOpenHome)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentOnOpenEvent by rememberUpdatedState(onOpenEvent)
    val currentOnOpenEventForm by rememberUpdatedState(onOpenEventForm)
    val currentTakeEventDate by rememberUpdatedState(takeEventDate)
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
                    is PracticeEffect.OpenEvent -> currentOnOpenEvent(effect.id)
                    is PracticeEffect.OpenEventForm -> currentOnOpenEventForm(effect.date)
                    PracticeEffect.ShowTooShort ->
                        messages.show(getString(Res.string.practice_too_short))
                    PracticeEffect.ShowPhotoFailed ->
                        messages.show(getString(Res.string.profile_photo_failed))
                }
            }
        }
    }

    // Back from a record, an event or the form of one opened from the sheet of the day (spec 3.36.2, 3.36.9): the sheet that stepped aside
    // for it rises again — on the day an event was saved on, told first (plan D24).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        currentTakeEventDate()?.let { viewModel.onIntent(PracticeIntent.EventSaved(it)) }
        viewModel.onIntent(PracticeIntent.Resumed)
    }

    // Decorative motion of this screen (spec 3.16) follows the system setting «убрать анимации».
    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved(), LocalHomeLook provides homeLook) {
        PracticeScreen(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = modifier,
            journeyCard = { look ->
                journey?.let { window ->
                    // the window is where the player is (spec 3.27): the home leads home, a city to the journey
                    JourneyWindowCard(window, look, onClick = { viewModel.onIntent(if (window.here is Venue.Hall) PracticeIntent.JourneyClicked else PracticeIntent.HomeClicked) })
                }
            },
            timer = { timer.value },
        )
    }
}
