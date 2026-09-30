package com.violinjourney.app.feature.live.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.ShiftedInteractionSource
import com.violinjourney.app.core.ui.components.glass
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.feature.live.LiveMode
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mode_play
import com.violinjourney.app.shared.resources.mode_tuning
import org.jetbrains.compose.resources.stringResource

/** The line of the words of the switcher, to their size. */
private const val WORD_LINE_HEIGHT = 1.3f

/** A word laid out in whole pixels: one that fits only by a hair is not trusted. */
private val WordSlack = 1.dp

/** The words of the switcher (spec 5.29 R6): 15 sp, 700, or smaller where the plan steps them down. */
@Composable
private fun switcherWordStyle(sizeSp: Float): TextStyle = MaterialTheme.typography.labelLarge.copy(
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * WORD_LINE_HEIGHT).sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.sp,
)

/**
 * How the switcher stands in its row ([SwitcherFit]): [centered] — the widest it may be in the middle of the row, clear of the
 * touch of the gear and of as much on the other side; [beside] — the widest it may be up to the touch of the gear; [keepSize] —
 * the words keep the size the row up to the gear gives them rather than step down for the middle (landscape). The words are
 * measured here, in the font and at the font scale of the screen; the style is a key, since on iOS Manrope arrives a frame later
 * and the plan must follow the real font.
 */
@Composable
fun rememberSwitcherPlan(centered: Dp, beside: Dp, keepSize: Boolean = false): SwitcherFit.Plan {
    val play = stringResource(Res.string.mode_play)
    val tuning = stringResource(Res.string.mode_tuning)
    val style = switcherWordStyle(SwitcherFit.MAX_SP)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(play, tuning, style, centered, beside, keepSize, measurer, density) {
        with(density) {
            SwitcherFit.plan(
                centered = centered.value,
                beside = beside.value,
                inset = LiveDimens.SwitcherInset.value,
                minSegment = LiveDimens.SwitcherSegmentMin.value,
                padding = LiveDimens.SwitcherSegmentPadding.value,
                minPadding = LiveDimens.SwitcherSegmentPaddingMin.value,
                keepSize = keepSize,
            ) { sizeSp ->
                val at = style.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * WORD_LINE_HEIGHT).sp)
                val widest = maxOf(
                    measurer.measure(play, at, softWrap = false, maxLines = 1).size.width,
                    measurer.measure(tuning, at, softWrap = false, maxLines = 1).size.width,
                )
                widest.toDp().value + WordSlack.value
            }
        }
    }
}

/**
 * «Игра | Настройка» (spec 3.1, 3.36.6): a capsule of smoked glass 44 high with a bone slider 36 high under the chosen word, which
 * rides over in 200 ms — its own control of Live, not the segmented switch of R1. The chosen word is ink on the bone, the other the
 * caption of the glass. Both segments are as wide as [plan] says ([rememberSwitcherPlan]), so the slider only moves; the whole
 * height of the row, 48, answers a touch. While a recording runs the switcher does not answer; its dimming is the caller's.
 */
@Composable
fun ModeSwitcher(
    mode: LiveMode,
    onSelect: (LiveMode) -> Unit,
    plan: SwitcherFit.Plan,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LiveTheme.venueColors
    val segment = plan.segment.dp
    val slider by animateDpAsState(if (mode == LiveMode.PLAY) 0.dp else segment, tween(LiveMotion.SWITCHER_SLIDE_MS), label = "switcherSlider")
    val style = switcherWordStyle(plan.sizeSp)
    val ground = liveGlassGround()
    Box(
        modifier = modifier
            .width(segment * 2 + LiveDimens.SwitcherInset * 2)
            .height(LiveDimens.TopRowHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        // the capsule of glass, 2 inside the row above and below
        Box(
            Modifier
                .fillMaxWidth()
                .height(LiveDimens.SwitcherHeight)
                .glass(RoundedCornerShape(LiveDimens.SwitcherCorner), edge = true, ground = ground),
        )
        // the bone slider under the chosen word
        Box(
            Modifier
                .offset { IntOffset((LiveDimens.SwitcherInset + slider).roundToPx(), 0) }
                .size(segment, LiveDimens.SwitcherSliderHeight)
                .background(colors.bone, RoundedCornerShape(LiveDimens.SwitcherSliderCorner)),
        )
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = LiveDimens.SwitcherInset)
                .selectableGroup(),
        ) {
            Segment(stringResource(Res.string.mode_play), mode == LiveMode.PLAY, enabled, segment, style, colors.ink, colors.glassCaption) {
                onSelect(LiveMode.PLAY)
            }
            Segment(stringResource(Res.string.mode_tuning), mode == LiveMode.TUNING, enabled, segment, style, colors.ink, colors.glassCaption) {
                onSelect(LiveMode.TUNING)
            }
        }
    }
}

/** A half of the switcher: the whole height of the row answers, the ripple is drawn in the shape of the slider. */
@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    width: Dp,
    style: TextStyle,
    ink: Color,
    caption: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val sliderPresses = remember(interaction) { ShiftedInteractionSource(interaction) }
    // the ink follows the slider over
    val color by animateColorAsState(if (selected) ink else caption, tween(LiveMotion.SWITCHER_SLIDE_MS), label = "switcherWord")
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(LiveDimens.SwitcherSliderHeight)
                // the press comes in the coordinates of the whole segment, the ripple is drawn where the slider stands
                .onPlaced { sliderPresses.shift = it.positionInParent() }
                .clip(RoundedCornerShape(LiveDimens.SwitcherSliderCorner))
                .indication(sliderPresses, ripple()),
        )
        Text(text = label, color = color, style = style, maxLines = 1, softWrap = false)
    }
}
