package com.example.violintuner.feature.journey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.violintuner.R
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.feature.journey.art.Postcard
import com.example.violintuner.feature.journey.art.SceneCamera
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.rememberSceneSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The postcard alone on the whole screen (spec 3.23). Upright, the picture stands by its height
 * and is wider than the screen: a drag looks around, the far plane lagging behind the near one.
 * A pinch comes closer — it is a drawing, it does not blur; a double tap is 1× ↔ 2×. The panel
 * leaves after a few seconds and comes back on a tap, as on the music stand; the screen stays on.
 */
@Composable
internal fun FullscreenPostcard(state: StopState, city: String, onIntent: (StopIntent) -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(SceneCamera.COVER_ZOOM) }
    val panX = remember { Animatable(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var panel by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }

    LaunchedEffect(panel, touches) {
        if (!panel) return@LaunchedEffect
        delay(JourneyMotion.FULLSCREEN_PANEL_HIDE_MS)
        panel = false
    }
    val view = LocalView.current
    DisposableEffect(view) {
        val before = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = before }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, drag, change, _ ->
                    zoom = SceneCamera.zoom(zoom, change, size.width.toFloat(), size.height.toFloat())
                    val (x, y) = SceneCamera.clamp(panX.value + drag.x, panY + drag.y, zoom, size.width.toFloat(), size.height.toFloat())
                    scope.launch { panX.snapTo(x) }
                    panY = y
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { panel = !panel },
                    onDoubleTap = {
                        zoom = SceneCamera.nextZoom(zoom, size.width.toFloat(), size.height.toFloat())
                        val (x, y) = SceneCamera.clamp(panX.value, panY, zoom, size.width.toFloat(), size.height.toFloat())
                        scope.launch { panX.snapTo(x) }
                        panY = y
                    },
                )
            },
    ) {
        Postcard(
            state.stop, description = stringResource(R.string.journey_card_description, city),
            modifier = Modifier.fillMaxSize(),
            mode = if (state.day) SceneMode.DAY else SceneMode.EVENING, inside = state.inside,
            seconds = rememberSceneSeconds(),
            camera = { Triple(zoom, panX.value, panY) },
        )
        val fade = if (reduce) 0 else JourneyMotion.FULLSCREEN_PANEL_FADE_MS
        AnimatedVisibility(visible = panel, enter = fadeIn(tween(fade)), exit = fadeOut(tween(fade)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))).padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.journey_fullscreen_close), role = Role.Button) { onIntent(StopIntent.FullscreenClosed) },
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(AppIcons.FullscreenExit, contentDescription = null, tint = Color.White) }
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(city, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(placeOf(state.index), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))).padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.dayUnlocked || state.secondViewUnlocked) {
                        val chip = FilterChipDefaults.filterChipColors(labelColor = Color.White, containerColor = Color.Black.copy(alpha = 0.35f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fun touched(intent: StopIntent) { touches++; onIntent(intent) }
                            if (state.dayUnlocked) {
                                FilterChip(selected = !state.day, onClick = { touched(StopIntent.DaySelected(false)) }, label = { Text(stringResource(R.string.journey_mode_evening)) }, colors = chip)
                                FilterChip(selected = state.day, onClick = { touched(StopIntent.DaySelected(true)) }, label = { Text(stringResource(R.string.journey_mode_day)) }, colors = chip)
                            }
                            if (state.secondViewUnlocked) {
                                FilterChip(selected = !state.inside, onClick = { touched(StopIntent.InsideSelected(false)) }, label = { Text(stringResource(R.string.journey_mode_outside)) }, colors = chip)
                                FilterChip(selected = state.inside, onClick = { touched(StopIntent.InsideSelected(true)) }, label = { Text(stringResource(R.string.journey_mode_inside)) }, colors = chip)
                            }
                        }
                    }
                    Text(stringResource(R.string.journey_fullscreen_hint), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
