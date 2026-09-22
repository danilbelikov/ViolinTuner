package com.example.violintuner.feature.live

import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.BuildConfig
import com.example.violintuner.R
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.core.ui.permission.isMicPermissionGranted
import com.example.violintuner.core.ui.permission.rememberMicPermissionRequester
import com.example.violintuner.feature.home.HomeLookViewModel
import com.example.violintuner.feature.journey.LocalHomeLook

/** Entry point of the Live destination: owns the ViewModel, its effects and the mic permission. */
@Composable
fun LiveRoute(
    onOpenSession: (sessionId: Long) -> Unit,
    onOpenPractice: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenSession by rememberUpdatedState(onOpenSession)
    val currentOnOpenPractice by rememberUpdatedState(onOpenPractice)

    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = true) { granted ->
        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted))
    }

    // The player's hands are busy: the screen must not dim while Live is open (spec 3.6).
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Also catches a permission revoked or granted in the system settings while we were away.
    // "Remove animations" of the system settings; read again on every return, like the permission.
    var reduceMotion by remember { mutableStateOf(context.animationsRemoved()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(LiveIntent.MicPermissionChanged(context.isMicPermissionGranted()))
        reduceMotion = context.animationsRemoved()
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is LiveEffect.OpenSession -> currentOnOpenSession(effect.id)
                    LiveEffect.ShowNoNotesRecorded ->
                        Toast.makeText(context, R.string.record_no_notes, Toast.LENGTH_SHORT).show()
                    LiveEffect.RequestMicPermission -> requestMicPermission()
                    LiveEffect.OpenPractice -> currentOnOpenPractice()
                }
            }
        }
    }

    // the room of Live is the home as it stands (spec 3.27): the same look the journey and «Занятия» draw
    val homeLook by hiltViewModel<HomeLookViewModel>().state.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalHomeLook provides homeLook, LocalReduceMotion provides reduceMotion) {
        LiveScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, reduceMotion = reduceMotion, showVenue = !BuildConfig.PLAIN_LIVE)
    }
}

/** True when the user has switched system animations off (accessibility: "remove animations"). */
private fun Context.animationsRemoved(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
