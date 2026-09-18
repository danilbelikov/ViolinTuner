package com.example.violintuner.feature.live.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.violintuner.R
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.live.StatusDot
import com.example.violintuner.feature.live.StatusLine
import com.example.violintuner.feature.live.StatusMessage
import com.example.violintuner.feature.live.TuningState

/**
 * The small line above the ring (spec 3.14, handoff 12a, 12b): a dot that says whether one may
 * play, and a few words. In tuning mode the same line carries the hint of the string row.
 * With [line] null — a note sounds — it fades out showing what it showed last and keeps its
 * height: the ring below does not jump. Nothing here pulses.
 */
@Composable
fun StatusLineRow(line: StatusLine?, tuning: TuningState, modifier: Modifier = Modifier) {
    var lastShown by remember { mutableStateOf(line) }
    if (line != null) SideEffect { lastShown = line }
    val alpha by animateFloatAsState(
        targetValue = if (line != null) 1f else 0f,
        animationSpec = tween(LiveMotion.CONTENT_FADE_MS),
        label = "statusLineAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LiveDimens.StatusLineHeight)
            .padding(horizontal = LiveDimens.ScreenPadding)
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = line ?: lastShown, animationSpec = tween(LiveMotion.STATUS_LINE_SWAP_MS), label = "statusLine") { shown ->
            if (shown != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LiveDimens.StatusLineGap),
                ) {
                    Dot(shown.dot)
                    Text(
                        text = messageOf(shown.message, tuning),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = ViolinTheme.liveTypography.statusLine,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Two shapes as well as two colors: a filled dot may, a hollow one may not (spec 2, principle 5). */
@Composable
private fun Dot(dot: StatusDot) {
    val colors = ViolinTheme.statusColors
    val shape = Modifier.size(LiveDimens.StatusLineDot)
    when (dot) {
        StatusDot.READY -> Box(shape.background(colors.ready, CircleShape))
        StatusDot.BLOCKED -> Box(shape.border(LiveDimens.StatusLineDotStroke, colors.blocked, CircleShape))
    }
}

@Composable
private fun messageOf(message: StatusMessage, tuning: TuningState): String = when (message) {
    StatusMessage.PLAY -> stringResource(R.string.live_silence)
    StatusMessage.TOO_NOISY -> stringResource(R.string.live_too_noisy)
    StatusMessage.MIC_UNAVAILABLE -> stringResource(R.string.live_mic_unavailable)
    StatusMessage.TUNE_AUTO -> stringResource(R.string.tuning_hint_auto)
    StatusMessage.TUNE_LOCKED -> tuning.lockedString?.let { locked ->
        stringResource(R.string.tuning_hint_locked, locked.note.letter.toString(), tuning.stringHz.getValue(locked))
    } ?: stringResource(R.string.tuning_hint_auto)
}
