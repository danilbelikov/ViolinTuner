package com.violinjourney.app.feature.live.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.ui.theme.LiveTheme

private const val SHARP_SIGN = "#"

/**
 * Huge letter with accidental, octave smaller and muted at the baseline (spec 3.1). [scale]
 * ties the size to the ring; the system font scale is deliberately ignored, because the note
 * has to fit the ring and is huge already.
 */
@Composable
fun NoteLabel(note: Note, modifier: Modifier = Modifier, scale: Float = 1f) {
    val base = LiveTheme.liveTypography
    val fontScale = LocalDensity.current.fontScale
    val typography = remember(base, scale, fontScale) {
        val factor = scale / fontScale
        base.copy(
            note = base.note.copy(fontSize = base.note.fontSize * factor, lineHeight = base.note.lineHeight * factor),
            octave = base.octave.copy(fontSize = base.octave.fontSize * factor),
        )
    }
    Row(modifier = modifier) {
        Text(
            text = if (note.isSharp) "${note.letter}$SHARP_SIGN" else note.letter.toString(),
            modifier = Modifier.alignByBaseline(),
            color = MaterialTheme.colorScheme.onSurface,
            style = typography.note,
            maxLines = 1,
        )
        Text(
            text = note.octave.toString(),
            modifier = Modifier
                .alignByBaseline()
                .padding(start = LiveDimens.OctaveStartPadding * scale),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = typography.octave,
            maxLines = 1,
        )
    }
}
