package com.violinjourney.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
import com.violinjourney.app.feature.home.art.homeModeNow
import com.violinjourney.app.feature.journey.art.ScenePicture
import com.violinjourney.app.feature.journey.art.rememberScene
import com.violinjourney.app.feature.journey.art.rememberSceneSeconds
import com.violinjourney.app.feature.journey.cityOf
import com.violinjourney.app.feature.journey.placeOf
import kotlinx.coroutines.delay

/** Which way the title card leads (spec 3.25, handoff 28d). */
enum class SplashKind(val scene: String) { AWAY("splash.away"), HOME("splash.home") }

/**
 * How long a title card stays. It is a change of the place of action, not a loading — so no bar and
 * no spinner — and it must not get tiresome: from the third showing since the app was started the
 * way out is shorter. Going home is no event, it is a return: shorter and quieter from the start.
 */
object SplashMotion {
    const val AWAY_MS = 1_800L
    const val AWAY_SHORT_MS = 1_200L
    const val HOME_MS = 900L
    const val REDUCED_MS = 800L
    const val SHORT_FROM_SHOWING = 3
    const val CAPTION_AT = 0.33f
    const val CAPTION_FADE_MS = 600

    /** Showings of the way out since the process started. */
    private var shown = 0

    fun durationOf(kind: SplashKind, reduce: Boolean): Long = when {
        reduce -> REDUCED_MS
        kind == SplashKind.HOME -> HOME_MS
        else -> { shown++; if (shown >= SHORT_FROM_SHOWING) AWAY_SHORT_MS else AWAY_MS }
    }
}

/**
 * The title card between the home and the journey: the sky of the hour, the plane of our postcards
 * with its light, two flocks, clouds or stars, the skyline of cities and the dashed route; the way
 * home — a door ajar and the window with the lamp. No touches are needed; a tap skips it.
 */
@Composable
fun SplashRoute(kind: SplashKind, onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val reduce = rememberAnimationsRemoved()
    val done by rememberUpdatedState(onDone)
    val duration = remember(kind, reduce) { SplashMotion.durationOf(kind, reduce) }
    // the timer and a tap may both end it: it ends once
    val finished = remember { booleanArrayOf(false) }
    val finish = { if (!finished[0]) { finished[0] = true; done() } }
    LaunchedEffect(kind) {
        delay(duration)
        finish()
    }
    BackHandler {}
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        val colors = MaterialTheme.colorScheme
        val mode = remember { homeModeNow() }
        val caption = remember { Animatable(if (reduce) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (reduce) return@LaunchedEffect
            delay((duration * SplashMotion.CAPTION_AT).toLong())
            caption.animateTo(1f, tween(SplashMotion.CAPTION_FADE_MS))
        }
        Box(modifier.fillMaxSize().background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = stringResource(R.string.splash_skip)) { finish() }) {
            ScenePicture(rememberScene(kind.scene, mode, folder = "home"), description = stringResource(if (kind == SplashKind.AWAY) R.string.splash_away else R.string.splash_home), modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds(), whole = true, centred = true)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))).padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 56.dp).graphicsLayer { alpha = caption.value },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(if (kind == SplashKind.AWAY) R.string.splash_away else R.string.splash_home).uppercase(), color = colors.primary, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold))
                if (kind == SplashKind.HOME) {
                    Text(houseName(ui.house), color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                } else if (!ui.loading) {
                    val at = JourneyRules.currentIndex(ui.progress)
                    Text(if (at == 0) cityOf(0) else stringResource(R.string.splash_stop, cityOf(at), at, JourneyRoute.stops.lastIndex), color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(placeOf(at), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
