package com.example.violintuner.feature.live.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.violintuner.R
import com.example.violintuner.feature.live.LiveMode

private const val HALF = 0.5f

/** Segmented "Игра | Настройка" control; the active segment is filled (spec 3.1). */
@Composable
fun ModeSwitcher(
    mode: LiveMode,
    onSelect: (LiveMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(LiveDimens.SwitcherCorner)
    val fillBias by animateFloatAsState(
        targetValue = if (mode == LiveMode.PLAY) -1f else 1f,
        animationSpec = tween(LiveMotion.SWITCHER_SLIDE_MS),
        label = "switcherFill",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LiveDimens.SwitcherHeight)
            .clip(shape)
            .border(LiveDimens.SwitcherBorder, colors.outlineVariant, shape),
    ) {
        // The fill slides between the halves; the segments themselves are transparent.
        Box(
            Modifier
                .fillMaxWidth(HALF)
                .fillMaxHeight()
                .align(BiasAlignment(horizontalBias = fillBias, verticalBias = 0f))
                .background(colors.primaryContainer),
        )
        Row(modifier = Modifier.selectableGroup()) {
            Segment(
                label = stringResource(R.string.mode_play),
                selected = mode == LiveMode.PLAY,
                onClick = { onSelect(LiveMode.PLAY) },
            ) { tint ->
                Box(
                    Modifier
                        .size(LiveDimens.SwitcherIconSize)
                        .border(LiveDimens.SwitcherIconStroke, tint, CircleShape),
                )
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(LiveDimens.SwitcherBorder)
                    .background(colors.outlineVariant),
            )
            Segment(
                label = stringResource(R.string.mode_tuning),
                selected = mode == LiveMode.TUNING,
                onClick = { onSelect(LiveMode.TUNING) },
            ) { tint ->
                Box(
                    Modifier
                        .size(LiveDimens.SwitcherTuningIconWidth, LiveDimens.SwitcherIconSize)
                        .background(tint, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun RowScope.Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(
            LiveDimens.SwitcherIconGap,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
        Text(
            text = label,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
