package com.example.violintuner.feature.live.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.feature.live.TuningState

private val LetterSize = 22.sp
private val CaptionSize = 11.sp

/**
 * G · D · A · E buttons of the tuning mode (spec 3.5) with the hint line from the handoff.
 * The target string is highlighted; a tap pins it (lock badge), a second tap returns to auto.
 */
@Composable
fun StringRow(
    tuning: TuningState,
    onStringClick: (ViolinString) -> Unit,
    modifier: Modifier = Modifier,
    showHint: Boolean = true,
    topPadding: Dp = LiveDimens.StringRowTopPadding,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(LiveDimens.StringButtonGap)) {
            ViolinString.entries.forEach { string ->
                StringButton(
                    letter = string.note.letter.toString(),
                    hz = tuning.stringHz.getValue(string),
                    isTarget = string == tuning.targetString,
                    isLocked = string == tuning.lockedString,
                    onClick = { onStringClick(string) },
                )
            }
        }
        if (showHint) {
            Text(
                text = hintOf(tuning),
                modifier = Modifier.padding(top = LiveDimens.StringHintTopPadding),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun hintOf(tuning: TuningState): String {
    val target = tuning.targetString ?: return stringResource(R.string.tuning_hint_auto)
    val letter = target.note.letter.toString()
    val hz = tuning.stringHz.getValue(target)
    return if (tuning.lockedString != null) {
        stringResource(R.string.tuning_hint_locked, letter, hz)
    } else {
        stringResource(R.string.tuning_hint_auto_nearest, letter, hz)
    }
}

@Composable
private fun StringButton(
    letter: String,
    hz: Int,
    isTarget: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        isLocked -> colors.primary
        isTarget -> colors.primaryContainer
        else -> colors.surfaceContainer
    }
    val content = when {
        isLocked -> colors.onPrimary
        isTarget -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    val shape = RoundedCornerShape(LiveDimens.StringButtonCorner)
    val lockedDescription = stringResource(R.string.tuning_string_locked)
    val autoDescription = stringResource(R.string.tuning_string_auto)
    Box {
        Column(
            modifier = Modifier
                .size(LiveDimens.StringButtonWidth, LiveDimens.StringButtonHeight)
                .clip(shape)
                .background(container)
                .selectable(selected = isTarget, role = Role.Button, onClick = onClick)
                .semantics { stateDescription = if (isLocked) lockedDescription else autoDescription },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = letter,
                color = content,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = LetterSize,
                    lineHeight = LetterSize,
                    fontWeight = if (isTarget) FontWeight.Bold else FontWeight.SemiBold,
                ),
            )
            Text(
                text = stringResource(R.string.tuning_string_hz, hz),
                color = content,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = CaptionSize,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        AnimatedVisibility(
            visible = isLocked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = LiveDimens.StringLockBadgeOffset, y = -LiveDimens.StringLockBadgeOffset),
            enter = scaleIn(
                keyframes {
                    durationMillis = LiveMotion.LOCK_POP_MS
                    LiveMotion.LOCK_POP_OVERSHOOT at LiveMotion.LOCK_POP_PEAK_MS
                },
            ) + fadeIn(tween(LiveMotion.LOCK_POP_PEAK_MS)),
            exit = scaleOut(tween(LiveMotion.LOCK_POP_PEAK_MS)) + fadeOut(tween(LiveMotion.LOCK_POP_PEAK_MS)),
        ) {
            LockBadge()
        }
    }
}

// Lock glyph from the handoff SVG, 12 × 12 viewport: body rect and shackle arc.
private const val LOCK_VIEWPORT = 12f

@Composable
private fun LockBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(LiveDimens.StringLockBadgeSize)
            .border(LiveDimens.StringLockBadgeOutline, colors.surface, CircleShape)
            .padding(LiveDimens.StringLockBadgeOutline / 2)
            .background(colors.onSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val glyph: Color = colors.onPrimary
        Canvas(Modifier.size(LiveDimens.StringLockIconSize)) {
            scale(scale = size.width / LOCK_VIEWPORT, pivot = Offset.Zero) {
                drawRoundRect(
                    color = glyph,
                    topLeft = Offset(2f, 5.5f),
                    size = Size(8f, 5.5f),
                    cornerRadius = CornerRadius(1.2f, 1.2f),
                )
                // shackle: half circle of radius 2.2 around (6, 4) with legs down to the body
                drawArc(
                    color = glyph,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(3.8f, 1.8f),
                    size = Size(4.4f, 4.4f),
                    style = Stroke(width = 1.8f, cap = StrokeCap.Butt),
                )
                drawLine(glyph, Offset(3.8f, 4f), Offset(3.8f, 5.5f), strokeWidth = 1.8f)
                drawLine(glyph, Offset(8.2f, 4f), Offset(8.2f, 5.5f), strokeWidth = 1.8f)
            }
        }
    }
}
