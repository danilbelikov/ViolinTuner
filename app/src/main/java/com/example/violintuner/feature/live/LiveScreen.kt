package com.example.violintuner.feature.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.violintuner.R
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.live.components.CentsScale
import com.example.violintuner.feature.live.components.HoldRing
import com.example.violintuner.feature.live.components.LiveDimens
import com.example.violintuner.feature.live.components.LiveMotion
import com.example.violintuner.feature.live.components.MicGlyph
import com.example.violintuner.feature.live.components.MicPermissionPrompt
import com.example.violintuner.feature.live.components.ModeSwitcher
import com.example.violintuner.feature.live.components.NoteLabel
import com.example.violintuner.feature.live.components.RecordButton
import com.example.violintuner.feature.live.components.StatusRow
import com.example.violintuner.feature.live.components.StringRow
import com.example.violintuner.feature.live.components.ZoneEllipse
import com.example.violintuner.feature.live.components.zoneBackground

/**
 * Live screen (spec 3.1, handoff variant 8). Stateless. Wider than tall gets the landscape
 * layout (ring on the left, controls on the right), anything else the portrait column.
 */
@Composable
fun LiveScreen(
    state: LiveState,
    onIntent: (LiveIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneColors = ViolinTheme.zoneColors
    val sounding = state.signal as? LiveSignal.Sounding

    // Gradient, status, ring fill and marker halo always share this one color (spec 3.4).
    val zoneColor by animateColorAsState(
        targetValue = zoneColors.colorFor(sounding?.zone),
        animationSpec = tween(state.zoneCrossfadeMs),
        label = "zoneColor",
    )

    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var ringCenter by remember { mutableStateOf(Offset.Unspecified) }
    val ringModifier = Modifier.onGloballyPositioned { ringCenter = it.centerIn(rootPosition) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = LiveLayoutMath.isLandscape(maxWidth.value, maxHeight.value)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .onGloballyPositioned { rootPosition = it.positionInRoot() }
                .zoneBackground(
                    gradient = zoneColors.gradientFor(sounding?.zone),
                    crossfadeMs = state.zoneCrossfadeMs,
                    ellipse = if (landscape) ZoneEllipse.LANDSCAPE else ZoneEllipse.PORTRAIT,
                    center = { ringCenter },
                ),
        ) {
            if (landscape) {
                LandscapeLayout(state, zoneColor, onIntent, ringModifier)
            } else {
                PortraitLayout(state, zoneColor, onIntent, ringModifier)
            }
        }
    }
}

@Composable
private fun PortraitLayout(
    state: LiveState,
    zoneColor: Color,
    onIntent: (LiveIntent) -> Unit,
    ringModifier: Modifier,
) {
    val noMic = state.signal == LiveSignal.NoMicPermission
    val sounding = state.signal as? LiveSignal.Sounding
    val chromeAlpha = if (noMic) LiveDimens.CHROME_ALPHA_NO_MIC else 1f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        AnimatedVisibility(
            visible = state.mode == LiveMode.TUNING,
            enter = expandVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                fadeIn(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
            exit = shrinkVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                fadeOut(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
        ) {
            StringRow(
                tuning = state.tuning,
                onStringClick = { onIntent(LiveIntent.StringClicked(it)) },
                modifier = Modifier.alpha(chromeAlpha),
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val reserved = if (noMic) {
                LiveDimens.PromptReservedHeight
            } else {
                LiveDimens.IndicatorSpacing + LiveDimens.StatusRowHeight
            }
            val ringSize = ringSizeFor(state, landscape = false, maxWidth, maxHeight, reserved)
            Column(
                verticalArrangement = Arrangement.spacedBy(LiveDimens.IndicatorSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Ring(state, zoneColor, ringSize, ringModifier)
                if (noMic) {
                    MicPermissionPrompt(onGrantClick = { onIntent(LiveIntent.GrantMicClicked) })
                } else {
                    StatusRow(direction = sounding?.direction, color = zoneColor, visible = sounding != null)
                }
            }
        }
        Scale(
            state = state,
            zoneColor = zoneColor,
            modifier = Modifier.padding(
                start = LiveDimens.ScreenPadding,
                end = LiveDimens.ScreenPadding,
                bottom = LiveDimens.ScaleBottomPadding,
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

/** Handoff `v1-land`: ring panel on the left; switcher, status, scale and record on the right. */
@Composable
private fun LandscapeLayout(
    state: LiveState,
    zoneColor: Color,
    onIntent: (LiveIntent) -> Unit,
    ringModifier: Modifier,
) {
    val noMic = state.signal == LiveSignal.NoMicPermission
    val sounding = state.signal as? LiveSignal.Sounding
    val chromeAlpha = if (noMic) LiveDimens.CHROME_ALPHA_NO_MIC else 1f

    Row(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(LiveDimens.LANDSCAPE_RING_PANEL_FRACTION)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            val ringSize = ringSizeFor(state, landscape = true, maxWidth, maxHeight, reserved = 0.dp)
            Ring(state, zoneColor, ringSize, ringModifier)
        }
        Column(
            modifier = Modifier
                .weight(1f - LiveDimens.LANDSCAPE_RING_PANEL_FRACTION)
                .fillMaxHeight()
                .padding(
                    start = LiveDimens.LandscapePaddingStart,
                    end = LiveDimens.LandscapePaddingEnd,
                    top = LiveDimens.LandscapePaddingVertical,
                    bottom = LiveDimens.LandscapePaddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(LiveDimens.LandscapeSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModeSwitcher(
                mode = state.mode,
                onSelect = { onIntent(LiveIntent.SelectMode(it)) },
                modifier = Modifier.alpha(chromeAlpha),
            )
            // No hint line here: there is no height for it, the button colors and the lock
            // badge carry the same information.
            if (state.mode == LiveMode.TUNING) {
                StringRow(
                    tuning = state.tuning,
                    onStringClick = { onIntent(LiveIntent.StringClicked(it)) },
                    modifier = Modifier.alpha(chromeAlpha),
                    showHint = false,
                    topPadding = 0.dp,
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (noMic) {
                    MicPermissionPrompt(onGrantClick = { onIntent(LiveIntent.GrantMicClicked) })
                } else {
                    StatusRow(
                        direction = sounding?.direction,
                        color = zoneColor,
                        visible = sounding != null,
                        height = maxHeight.coerceIn(LiveDimens.LandscapeStatusMinHeight, LiveDimens.StatusRowHeight),
                        wordStyle = ViolinTheme.liveTypography.statusLandscape,
                    )
                }
            }
            Scale(state = state, zoneColor = zoneColor)
            RecordButton(
                onClick = { onIntent(LiveIntent.RecordClicked) },
                modifier = Modifier
                    .padding(top = LiveDimens.LandscapeRecordTopPadding)
                    .alpha(chromeAlpha),
            )
        }
    }
}

private fun ringSizeFor(state: LiveState, landscape: Boolean, maxWidth: Dp, maxHeight: Dp, reserved: Dp): Dp {
    val design = LiveLayoutMath.designRing(
        landscape = landscape,
        tuning = state.mode == LiveMode.TUNING,
        noMic = state.signal == LiveSignal.NoMicPermission,
    )
    val margin = LiveDimens.RingMargin.value * 2
    return LiveLayoutMath.ringDiameter(
        designDp = design,
        availableWidthDp = maxWidth.value - margin,
        availableHeightDp = maxHeight.value - if (landscape) margin else 0f,
        reservedHeightDp = reserved.value,
    ).dp
}

@Composable
private fun Ring(state: LiveState, zoneColor: Color, size: Dp, modifier: Modifier) {
    val zoneColors = ViolinTheme.zoneColors
    val sounding = state.signal as? LiveSignal.Sounding
    HoldRing(
        progress = sounding?.holdProgress?.toFloat() ?: 0f,
        trackColor = if (sounding != null) zoneColors.ringTrack else zoneColors.none,
        fillColor = zoneColor,
        size = size,
        modifier = modifier,
    ) {
        RingContent(state.signal, noteScale = LiveLayoutMath.noteScale(size.value))
    }
}

@Composable
private fun Scale(state: LiveState, zoneColor: Color, modifier: Modifier = Modifier) {
    val sounding = state.signal as? LiveSignal.Sounding
    CentsScale(
        markerFraction = sounding?.let { ScaleMath.markerFraction(it.cents, state.scale).toFloat() },
        inTuneFraction = ScaleMath.inTuneFraction(state.scale).toFloat(),
        haloColor = zoneColor,
        modifier = modifier.alpha(
            when {
                sounding != null -> 1f
                state.signal == LiveSignal.NoMicPermission -> LiveDimens.SCALE_ALPHA_NO_MIC
                else -> LiveDimens.SCALE_ALPHA_IDLE
            },
        ),
    )
}

private enum class RingContentKind { NOTE, SILENCE, TOO_NOISY, MIC_UNAVAILABLE, NO_MIC }

/**
 * What is inside the ring. Kinds cross-fade; the note itself changes at once, because the note
 * name has to follow the playing immediately.
 */
@Composable
private fun RingContent(signal: LiveSignal, noteScale: Float) {
    val note = (signal as? LiveSignal.Sounding)?.note
    var lastNote by remember { mutableStateOf<Note?>(null) }
    if (note != null) SideEffect { lastNote = note }

    val kind = when (signal) {
        is LiveSignal.Sounding -> RingContentKind.NOTE
        LiveSignal.Silence -> RingContentKind.SILENCE
        LiveSignal.TooNoisy -> RingContentKind.TOO_NOISY
        LiveSignal.MicUnavailable -> RingContentKind.MIC_UNAVAILABLE
        LiveSignal.NoMicPermission -> RingContentKind.NO_MIC
    }
    // Both layers fill the ring, otherwise Crossfade stacks them from the top-start corner and
    // texts of different width sit side by side during the fade.
    Crossfade(
        targetState = kind,
        modifier = Modifier.fillMaxSize(),
        animationSpec = tween(LiveMotion.CONTENT_FADE_MS),
        label = "ringContent",
    ) { shown ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (shown) {
                // while fading out the signal is already silent: keep showing the last note
                RingContentKind.NOTE -> (note ?: lastNote)?.let { NoteLabel(it, scale = noteScale) }
                RingContentKind.SILENCE -> RingPlaceholder(stringResource(R.string.live_silence))
                RingContentKind.TOO_NOISY -> RingPlaceholder(stringResource(R.string.live_too_noisy))
                RingContentKind.MIC_UNAVAILABLE -> RingPlaceholder(stringResource(R.string.live_mic_unavailable))
                RingContentKind.NO_MIC -> MicGlyph()
            }
        }
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
