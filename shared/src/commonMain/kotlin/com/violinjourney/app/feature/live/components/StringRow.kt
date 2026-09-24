package com.violinjourney.app.feature.live.components

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.feature.live.TuningState
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.tuning_string_auto
import com.violinjourney.app.shared.resources.tuning_string_hz
import com.violinjourney.app.shared.resources.tuning_string_locked
import org.jetbrains.compose.resources.stringResource

private val LetterSize = 22.sp
private val CaptionSize = 11.sp

/**
 * G · D · A · E buttons of the tuning mode (spec 3.5), drawn as four pegs (spec 3.27). The hint
 * that used to stand under them is the status line of the screen now (spec 3.14).
 * The target string is highlighted; a tap pins it (lock badge), a second tap returns to auto.
 */
@Composable
fun StringRow(
    tuning: TuningState,
    onStringClick: (ViolinString) -> Unit,
    modifier: Modifier = Modifier,
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
    }
}

/**
 * A peg (spec 3.27, handoff 29j): ebony with a nickel head; the nearest string is maple with a bone
 * head; the locked one is bone with a brass rim and head and wears the lock.
 */
@Composable
private fun StringButton(
    letter: String,
    hz: Int,
    isTarget: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
) {
    val wood = LiveTheme.venueColors
    val body = when {
        isLocked -> wood.bone
        isTarget -> wood.maple
        else -> wood.ebony
    }
    val edge = when {
        isLocked -> wood.brass
        isTarget -> wood.mapleLit
        else -> wood.ebonyEdge
    }
    val head = when {
        isLocked -> wood.brass
        isTarget -> wood.boneShade
        else -> wood.nickel
    }
    val letterColor = if (isLocked) wood.ink else wood.bone
    val captionColor = if (isLocked) wood.inkSoft else wood.caption
    val shape = RoundedCornerShape(LiveDimens.StringButtonCorner)
    val lockedDescription = stringResource(Res.string.tuning_string_locked)
    val autoDescription = stringResource(Res.string.tuning_string_auto)
    Box(contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .size(LiveDimens.StringButtonWidth, LiveDimens.StringButtonHeight)
                .clip(shape)
                .background(body)
                .border(LiveDimens.StringButtonEdge, edge, shape)
                .selectable(selected = isTarget, role = Role.Button, onClick = onClick)
                .semantics { stateDescription = if (isLocked) lockedDescription else autoDescription },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = letter,
                color = letterColor,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = LetterSize, lineHeight = LetterSize, fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(Res.string.tuning_string_hz, hz),
                color = captionColor,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = CaptionSize, fontWeight = FontWeight.Medium),
            )
        }
        // the head of the peg stands out over the top edge
        Box(
            Modifier
                .offset(y = -LiveDimens.StringPegHeadRise)
                .size(LiveDimens.StringPegHeadWidth, LiveDimens.StringPegHeadHeight)
                .background(head, RoundedCornerShape(LiveDimens.StringPegHeadHeight / 2)),
        )
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
    val wood = LiveTheme.venueColors
    Box(
        modifier = modifier
            .size(LiveDimens.StringLockBadgeSize)
            .background(wood.bone, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val glyph: Color = wood.ink
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
