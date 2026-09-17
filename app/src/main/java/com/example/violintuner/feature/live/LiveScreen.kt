package com.example.violintuner.feature.live

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.violintuner.R
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.live.components.CentsScale
import com.example.violintuner.feature.live.components.HoldRing
import com.example.violintuner.feature.live.components.LiveDimens
import com.example.violintuner.feature.live.components.MicGlyph
import com.example.violintuner.feature.live.components.MicPermissionPrompt
import com.example.violintuner.feature.live.components.ModeSwitcher
import com.example.violintuner.feature.live.components.NoteLabel
import com.example.violintuner.feature.live.components.RecordButton
import com.example.violintuner.feature.live.components.StatusRow
import com.example.violintuner.feature.live.components.zoneBackground

/** Portrait Live screen (spec 3.1, handoff variant 8). Stateless. */
@Composable
fun LiveScreen(
    state: LiveState,
    onIntent: (LiveIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneColors = ViolinTheme.zoneColors
    val signal = state.signal
    val sounding = signal as? LiveSignal.Sounding
    val noMic = signal == LiveSignal.NoMicPermission

    // Gradient, status, ring fill and marker halo always share this one color (spec 3.4).
    val zoneColor by animateColorAsState(
        targetValue = zoneColors.colorFor(sounding?.zone),
        animationSpec = tween(state.zoneCrossfadeMs),
        label = "zoneColor",
    )

    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var ringCenter by remember { mutableStateOf(Offset.Unspecified) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onGloballyPositioned { rootPosition = it.positionInRoot() }
            .zoneBackground(
                gradient = zoneColors.gradientFor(sounding?.zone),
                crossfadeMs = state.zoneCrossfadeMs,
                center = { ringCenter },
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val chromeAlpha = if (noMic) LiveDimens.CHROME_ALPHA_NO_MIC else 1f
            ModeSwitcher(
                mode = state.mode,
                onSelect = { onIntent(LiveIntent.SelectMode(it)) },
                modifier = Modifier
                    .padding(
                        start = LiveDimens.ScreenPadding,
                        end = LiveDimens.ScreenPadding,
                        top = LiveDimens.SwitcherTopPadding,
                    )
                    .alpha(chromeAlpha),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    LiveDimens.IndicatorSpacing,
                    Alignment.CenterVertically,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HoldRing(
                    progress = sounding?.holdProgress?.toFloat() ?: 0f,
                    trackColor = if (sounding != null) zoneColors.ringTrack else zoneColors.none,
                    fillColor = zoneColor,
                    size = if (noMic) LiveDimens.RingSizeNoMic else LiveDimens.RingSize,
                    modifier = Modifier.onGloballyPositioned { ringCenter = it.centerIn(rootPosition) },
                ) {
                    RingContent(signal)
                }
                if (noMic) {
                    MicPermissionPrompt(onGrantClick = { onIntent(LiveIntent.GrantMicClicked) })
                } else {
                    StatusRow(
                        direction = sounding?.direction,
                        color = zoneColor,
                        visible = sounding != null,
                    )
                }
            }
            CentsScale(
                markerFraction = sounding?.let { ScaleMath.markerFraction(it.cents, state.scale).toFloat() },
                inTuneFraction = ScaleMath.inTuneFraction(state.scale).toFloat(),
                haloColor = zoneColor,
                modifier = Modifier
                    .padding(
                        start = LiveDimens.ScreenPadding,
                        end = LiveDimens.ScreenPadding,
                        bottom = LiveDimens.ScaleBottomPadding,
                    )
                    .alpha(
                        when {
                            sounding != null -> 1f
                            noMic -> LiveDimens.SCALE_ALPHA_NO_MIC
                            else -> LiveDimens.SCALE_ALPHA_IDLE
                        },
                    ),
            )
            RecordButton(
                onClick = { onIntent(LiveIntent.RecordClicked) },
                modifier = Modifier
                    .padding(vertical = LiveDimens.RecordPaddingVertical)
                    .alpha(chromeAlpha),
            )
        }
    }
}

@Composable
private fun RingContent(signal: LiveSignal) {
    when (signal) {
        is LiveSignal.Sounding -> NoteLabel(signal.note)
        LiveSignal.Silence -> RingPlaceholder(stringResource(R.string.live_silence))
        LiveSignal.TooNoisy -> RingPlaceholder(stringResource(R.string.live_too_noisy))
        LiveSignal.NoMicPermission -> MicGlyph()
    }
}

@Composable
private fun RingPlaceholder(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = LiveDimens.ScreenPadding),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = ViolinTheme.liveTypography.placeholder,
    )
}

/** Center of this layout in the coordinates of the screen root placed at [rootPosition]. */
private fun LayoutCoordinates.centerIn(rootPosition: Offset): Offset =
    boundsInRoot().center - rootPosition
