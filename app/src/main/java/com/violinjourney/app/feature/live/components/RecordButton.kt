package com.violinjourney.app.feature.live.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.theme.ViolinTheme

/**
 * Record button (spec 3.9) as the key of an old tape recorder (spec 3.27, handoff 29j): a bone
 * face in a brass rim with the violet dot of the app, standing on a short hard shadow; pressed, it
 * goes down by its travel. While recording the key is red with a white stop square inside. Disabled
 * it only dims: outside play mode and without a microphone there is nothing to record.
 */
@Composable
fun RecordButton(
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wood = ViolinTheme.venueColors
    val zoneColors = ViolinTheme.zoneColors
    val accent = MaterialTheme.colorScheme.primary
    val spec = tween<Color>(LiveMotion.RECORD_MORPH_MS)
    val face by animateColorAsState(if (recording) zoneColors.off else wood.bone, spec, label = "recordFace")
    val rim by animateColorAsState(if (recording) wood.recordingRim else wood.brass, spec, label = "recordRim")
    val glyph by animateColorAsState(if (recording) zoneColors.onOff else accent, spec, label = "recordGlyph")
    val glyphSize by animateDpAsState(
        if (recording) LiveDimens.RecordStopSize else LiveDimens.RecordDotSize,
        tween(LiveMotion.RECORD_MORPH_MS), label = "recordGlyphSize",
    )
    // from a circle (half the size) down to the small corner of the stop square
    val glyphCorner by animateDpAsState(
        if (recording) LiveDimens.RecordStopCorner else LiveDimens.RecordDotSize / 2,
        tween(LiveMotion.RECORD_MORPH_MS), label = "recordGlyphCorner",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateDpAsState(if (pressed && enabled) LiveDimens.RecordTravel else 0.dp, tween(LiveMotion.RECORD_PRESS_MS), label = "recordTravel")
    Box(modifier = modifier.size(LiveDimens.RecordButtonSize, LiveDimens.RecordButtonSize + LiveDimens.RecordShadow)) {
        // the hard shadow the key stands on: shorter as the key goes down
        Box(
            Modifier
                .offset(y = LiveDimens.RecordShadow)
                .size(LiveDimens.RecordButtonSize)
                .background(ShadowColor, CircleShape),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(0, travel.roundToPx()) }
                .size(LiveDimens.RecordButtonSize)
                .clip(CircleShape)
                .background(face)
                .border(LiveDimens.RecordRim, rim, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClickLabel = stringResource(if (recording) R.string.record_stop else R.string.record_start),
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(glyphSize)
                    .background(glyph, RoundedCornerShape(glyphCorner)),
            )
        }
    }
}

private val ShadowColor = Color.Black.copy(alpha = 0.35f)
