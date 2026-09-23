package com.violinjourney.app.feature.live.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme

private const val TABULAR_FIGURES = "tnum"
private const val EDGE_ALPHA = 0.5f
private const val SHADOW_ALPHA = 0.30f

/**
 * The tag of the practice to the right of the record key (spec 3.12, handoff nav_bar 35): the mirror of the bookmark
 * of blocks on its left — the notch on the side of the key, the rounded corners outside. No practice: the outline, a
 * stopwatch and «Начать занятие»; a tap starts one. A practice runs: paper, a velvet dot, «занятие» and the time; a
 * tap leads to «Закончить занятие». Starting, the paper pours in from the notch. [maxWidth] is what the row can give.
 */
@Composable
fun PracticeTag(
    practiceMs: Long?,
    maxWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val colors = ViolinTheme.venueColors
    val running = practiceMs != null
    // while the paper drains back into the outline the state is already «no practice»: keep the last time on it
    var lastMs by remember { mutableLongStateOf(0L) }
    if (practiceMs != null) SideEffect { lastMs = practiceMs }
    val time = Formats.timer(practiceMs ?: lastMs)
    val paper by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(LiveMotion.PRACTICE_TAG_FILL_MS),
        label = "practiceTagPaper",
    )

    val startText = stringResource(R.string.practice_start)
    val label = stringResource(R.string.practice_chip_label)
    val description = if (running) stringResource(R.string.practice_timer_description, time) else startText
    val width = minOf(maxWidth, tagWidth(startText, label, time))

    Box(
        modifier = modifier
            .size(width, LiveDimens.BookmarkHeight + LiveDimens.BookmarkShadowRoom)
            .drawBehind {
                val height = LiveDimens.BookmarkHeight.toPx()
                val outline = tagPath(Size(size.width, height), LiveDimens.BookmarkNotch.toPx(), LiveDimens.BookmarkCorner.toPx())
                translate(top = LiveDimens.BookmarkShadow.toPx()) { drawPath(outline, Color.Black.copy(alpha = SHADOW_ALPHA)) }
                if (paper < 1f) drawPath(outline, colors.bone.copy(alpha = EDGE_ALPHA), style = Stroke(LiveDimens.BookmarkEdge.toPx()))
                if (paper > 0f) clipRect(right = size.width * paper) { drawPath(outline, colors.bone) }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Crossfade(
            targetState = running,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = LiveDimens.PracticeTagTextStart, end = LiveDimens.PracticeTagTextEnd, bottom = LiveDimens.BookmarkShadowRoom),
            animationSpec = tween(if (reduceMotion) 0 else LiveMotion.PRACTICE_TAG_FILL_MS),
            label = "practiceTagContent",
        ) { shownRunning ->
            if (shownRunning) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(LiveDimens.BookmarkTextGap, Alignment.CenterVertically),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LiveDimens.PracticeTagDotGap)) {
                        Box(Modifier.size(LiveDimens.PracticeTagDot).background(colors.velvet, CircleShape))
                        Text(label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = labelStyle())
                    }
                    Text(time, color = colors.ink, maxLines = 1, style = timeStyle())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LiveDimens.PracticeTagIconGap),
                ) {
                    AppIcon(AppIcons.Timer, contentDescription = null, size = LiveDimens.PracticeTagIcon, tint = colors.bone)
                    // «Начать / занятие»: the words wrap in two lines by themselves, as in the handoff
                    Text(startText, color = colors.bone, maxLines = 2, overflow = TextOverflow.Ellipsis, style = startStyle())
                }
            }
        }
    }
}

/**
 * As wide as the widest of its two faces needs, never narrower than the handoff's 124: the width does not jump when
 * the practice starts, and every language keeps its words whole.
 */
@Composable
private fun tagWidth(startText: String, label: String, time: String): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val start = startStyle()
    val labelStyle = labelStyle()
    val timeStyle = timeStyle()
    return with(density) {
        val longestWord = startText.split(' ').maxOf { measurer.measure(it, start, maxLines = 1).size.width }.toDp()
        val idle = LiveDimens.PracticeTagIcon + LiveDimens.PracticeTagIconGap + longestWord
        val runningLabel = LiveDimens.PracticeTagDot + LiveDimens.PracticeTagDotGap + measurer.measure(label, labelStyle, maxLines = 1).size.width.toDp()
        val runningTime = measurer.measure(time, timeStyle, maxLines = 1).size.width.toDp()
        maxOf(LiveDimens.PracticeTagMinWidth, maxOf(idle, runningLabel, runningTime) + LiveDimens.PracticeTagTextStart + LiveDimens.PracticeTagTextEnd)
    }
}

@Composable
private fun startStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.5.sp)

@Composable
private fun labelStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)

@Composable
private fun timeStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp, fontFeatureSettings = TABULAR_FIGURES)

/**
 * The handoff's `prk(w, h)`: `M0 0 H w−6 Q w 0 w 6 V h−6 Q w h w−6 h H0 L15 h/2 Z` — the bookmark turned round: the
 * notch on the left, towards the key, the right corners rounded.
 */
internal fun tagPath(size: Size, notch: Float, corner: Float): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(0f, 0f)
    lineTo(w - corner, 0f)
    quadraticTo(w, 0f, w, corner)
    lineTo(w, h - corner)
    quadraticTo(w, h, w - corner, h)
    lineTo(0f, h)
    lineTo(notch, h / 2f)
    close()
}
