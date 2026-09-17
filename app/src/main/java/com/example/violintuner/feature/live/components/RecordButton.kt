package com.example.violintuner.feature.live.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.violintuner.R
import com.example.violintuner.core.ui.theme.ViolinTheme

/**
 * Record button (spec 3.9): accent circle with a dot; while recording it turns to the "off"
 * color with a white rounded square inside. Disabled it only dims: outside play mode and
 * without a microphone there is nothing to record.
 */
@Composable
fun RecordButton(
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    val spec = tween<androidx.compose.ui.graphics.Color>(LiveMotion.RECORD_MORPH_MS)
    val container by animateColorAsState(if (recording) zoneColors.off else colors.primary, spec, label = "recordContainer")
    val glyph by animateColorAsState(if (recording) zoneColors.onOff else colors.onPrimary, spec, label = "recordGlyph")
    val glyphSize by animateDpAsState(
        if (recording) LiveDimens.RecordStopSize else LiveDimens.RecordDotSize,
        tween(LiveMotion.RECORD_MORPH_MS), label = "recordGlyphSize",
    )
    // from a circle (half the size) down to the small corner of the stop square
    val glyphCorner by animateDpAsState(
        if (recording) LiveDimens.RecordStopCorner else LiveDimens.RecordDotSize / 2,
        tween(LiveMotion.RECORD_MORPH_MS), label = "recordGlyphCorner",
    )
    Box(
        modifier = modifier
            .size(LiveDimens.RecordButtonSize)
            .clip(CircleShape)
            .background(container)
            .clickable(
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
