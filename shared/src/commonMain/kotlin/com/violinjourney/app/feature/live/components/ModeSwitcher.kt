package com.violinjourney.app.feature.live.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.feature.live.LiveMode
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mode_play
import com.violinjourney.app.shared.resources.mode_tuning
import org.jetbrains.compose.resources.stringResource

private val LabelStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)

/**
 * «Игра | Настройка» (spec 3.1) as a thing of the room (spec 3.27, handoff 29j): a maple plank with
 * a bone slider that slides under the chosen word. Both halves are as wide as the longer word, so
 * the slider only moves. While a recording runs the plank is faded and does not answer.
 */
@Composable
fun ModeSwitcher(
    mode: LiveMode,
    onSelect: (LiveMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val wood = LiveTheme.venueColors
    val surface = MaterialTheme.colorScheme.surface
    val play = stringResource(Res.string.mode_play)
    val tuning = stringResource(Res.string.mode_tuning)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val half = remember(play, tuning, density) {
        with(density) { maxOf(measurer.measure(play, LabelStyle).size.width, measurer.measure(tuning, LabelStyle).size.width).toDp() } + LiveDimens.SwitcherSegmentPadding * 2
    }
    val slider by animateDpAsState(if (mode == LiveMode.PLAY) 0.dp else half, tween(LiveMotion.SWITCHER_SLIDE_MS), label = "switcherSlider")
    val plank = if (enabled) wood.maple else lerp(wood.maple, surface, DISABLED_PLANK)
    val bone = if (enabled) wood.bone else lerp(wood.bone, surface, DISABLED_SLIDER)
    Box(
        modifier = modifier
            .height(LiveDimens.SwitcherHeight)
            .clip(RoundedCornerShape(LiveDimens.SwitcherCorner))
            .background(plank)
            .border(LiveDimens.SwitcherBorder, wood.mapleDark, RoundedCornerShape(LiveDimens.SwitcherCorner))
            .padding(LiveDimens.SwitcherInset),
    ) {
        Box(
            Modifier
                .offset { IntOffset(slider.roundToPx(), 0) }
                .width(half)
                .fillMaxHeight()
                .background(bone, RoundedCornerShape(LiveDimens.SwitcherSliderCorner)),
        )
        Row(Modifier.selectableGroup()) {
            Segment(play, mode == LiveMode.PLAY, enabled, half, wood.ink, if (enabled) wood.boneShade else wood.muted) { onSelect(LiveMode.PLAY) }
            Segment(tuning, mode == LiveMode.TUNING, enabled, half, wood.ink, if (enabled) wood.boneShade else wood.muted) { onSelect(LiveMode.TUNING) }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, enabled: Boolean, width: Dp, ink: Color, idle: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(LiveDimens.SwitcherSliderCorner))
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = if (selected) ink else idle, style = LabelStyle, maxLines = 1)
    }
}

/** A plank that does not answer: its wood sinks into the dark (handoff 29j, «неактивен во время записи»). */
private const val DISABLED_PLANK = 0.45f
private const val DISABLED_SLIDER = 0.5f
