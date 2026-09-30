package com.violinjourney.app.feature.live.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import com.violinjourney.app.core.ui.components.glass
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.feature.live.StatusDot
import com.violinjourney.app.feature.live.StatusLine
import com.violinjourney.app.feature.live.StatusMessage
import com.violinjourney.app.feature.live.TuningState
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.live_mic_unavailable
import com.violinjourney.app.shared.resources.live_silence
import com.violinjourney.app.shared.resources.live_too_noisy
import com.violinjourney.app.shared.resources.tuning_hint_auto
import com.violinjourney.app.shared.resources.tuning_hint_locked
import org.jetbrains.compose.resources.stringResource

/**
 * The line above the ring (spec 3.14, 3.36.6): a dot that says whether one may play, and a few words. In tuning mode the same
 * line carries the hint of the string row. Over the picture ([plate]) it stands on a capsule of smoked glass, 36 high, without an
 * edge — the words light, never grey (spec 5.29 R6); the plain Live has no plate (5.20). With [line] null — a note sounds — it
 * fades out showing what it showed last and keeps its height: the ring below does not jump. Nothing here pulses.
 */
@Composable
fun StatusLineRow(line: StatusLine?, tuning: TuningState, modifier: Modifier = Modifier, plate: Boolean = false) {
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
        // Both phrases fill the line and stand in its middle: otherwise Crossfade stacks them from its start, and while they cross
        // the shorter one stands off the middle by half the difference, then jumps to it (as the ring's content, whose layers fill it)
        Crossfade(
            targetState = line ?: lastShown,
            modifier = Modifier.fillMaxWidth(),
            animationSpec = tween(LiveMotion.STATUS_LINE_SWAP_MS),
            label = "statusLine",
        ) { shown ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (shown != null) {
                    Row(
                        modifier = if (plate) {
                            Modifier
                                .height(LiveDimens.StatusPlateHeight)
                                .glass(RoundedCornerShape(LiveDimens.StatusPlateCorner))
                                .padding(horizontal = LiveDimens.StatusPlatePadding)
                        } else {
                            Modifier
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LiveDimens.StatusLineGap),
                    ) {
                        Dot(shown.dot)
                        Text(
                            text = messageOf(shown.message, tuning),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = LiveTheme.liveTypography.statusLine,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Two shapes as well as two colors: a filled dot may, a hollow ring may not (spec 2, principle 5). */
@Composable
private fun Dot(dot: StatusDot) {
    val colors = LiveTheme.statusColors
    when (dot) {
        StatusDot.READY -> Box(Modifier.size(LiveDimens.StatusDotReady).background(colors.ready, CircleShape))
        StatusDot.BLOCKED -> Box(Modifier.size(LiveDimens.StatusDotBlocked).border(LiveDimens.StatusLineDotStroke, colors.blocked, CircleShape))
    }
}

@Composable
private fun messageOf(message: StatusMessage, tuning: TuningState): String = when (message) {
    StatusMessage.PLAY -> stringResource(Res.string.live_silence)
    StatusMessage.TOO_NOISY -> stringResource(Res.string.live_too_noisy)
    StatusMessage.MIC_UNAVAILABLE -> stringResource(Res.string.live_mic_unavailable)
    StatusMessage.TUNE_AUTO -> stringResource(Res.string.tuning_hint_auto)
    StatusMessage.TUNE_LOCKED -> tuning.lockedString?.let { locked ->
        stringResource(Res.string.tuning_hint_locked, locked.note.letter.toString(), tuning.stringHz.getValue(locked))
    } ?: stringResource(Res.string.tuning_hint_auto)
}
