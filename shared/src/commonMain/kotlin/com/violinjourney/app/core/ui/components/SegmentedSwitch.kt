package com.violinjourney.app.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.theme.AppShapes

private const val SWITCH_MS = 200
private const val LINE_HEIGHT = 1.15f
private const val DISABLED_ALPHA = 0.38f

/** The regular switch (spec 5.29): a container of 52, the pills of 44 inside it — 4 from its edges, 4 between them. */
private val RegularHeight = 52.dp
private val RegularEdge = 4.dp
private val RegularBetween = 2.dp

/** The compact one, for the A/B of a player in a low window: 28 seen, 48 pressed (at least); pills of 24, 2 from the edges. */
private val CompactTouch = 48.dp
private val CompactHeight = 28.dp
private val CompactEdge = 2.dp
private val CompactBetween = 1.dp
private val CompactCorner = 14.dp

/**
 * The one segmented switch of the app (spec 3.36.1, 5.29; components.html `.seg`): the chosen segment is a pill in the accent, no
 * frame and no dividers; the whole height of the container is pressed, not only the pill, and the ripple starts under the finger.
 * A label that does not fit goes on a second line; a large font makes the container and its pills taller rather than cut that line
 * (52 and 44 are the least), and only a third line — a very large font breaking a word — ends in an ellipsis. Three switches are not
 * this one: «Игра | Настройка» of Live, the switches over pictures (the house, a stop of the journey) and — until R5 moves it here —
 * the A/B of the player. [compact] is for that A/B: 28 dp to see, at least 48 to press. [fontSize] is larger for segments that are a
 * single sign, like ♭ ♮ ♯.
 *
 * [wholeWords] — no label breaks inside a word (the status of an element, spec 3.36.4): equal shares while each holds its widest
 * word, otherwise the segments share the row by their words, and where even that is too narrow the labels step down together
 * ([SegmentFit]). [byWords] — by the words always, not equally: the status in the narrow left column lying, where «В репертуаре»
 * would not stand in a third (spec 3.36.4). Both measure the labels and need a bounded width. [description] — the name of the group
 * for TalkBack («Статус»), on the group itself.
 *
 * [containerColor] — the ground of the container where surfaceContainer would not stand out: the ground of the screen in a sheet
 * («Тональность», 5.29 R4). [enabled] false — the whole switch sleeps: 0.38 with its ground, no segment answers, TalkBack says
 * «недоступно» (the sign and the mode without a tonic, the key of a scale in an edit); the reason is its caller's line under it.
 * [segmentEnabled] — one segment sleeps alone at 0.38 while the others answer (the octave that would leave the violin).
 */
@Composable
fun SegmentedSwitch(
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fontSize: Int = 14,
    byWords: Boolean = false,
    wholeWords: Boolean = false,
    description: String? = null,
    containerColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    segmentEnabled: (Int) -> Boolean = { true },
) {
    val looks = SwitchLooks(containerColor, enabled, segmentEnabled)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * LINE_HEIGHT).sp,
        fontWeight = FontWeight.Bold,
    )
    if (!byWords && !wholeWords) {
        SwitchRow(labels, selectedIndex, onSelect, modifier, compact, labelStyle, weights = null, description, looks)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val room = constraints.maxWidth
        val plan = remember(labels, labelStyle, measurer, density, room, compact, byWords) {
            val around = labels.indices.map { index -> with(density) { labelRoom(index, labels.lastIndex, compact).toPx() } }
            SegmentFit.plan(
                room = room.toFloat(),
                around = around,
                slack = with(density) { SegmentSlack.toPx() },
                maxSp = fontSize.toFloat(),
                share = if (byWords) SegmentFit.Share.ByWords else SegmentFit.Share.Equal,
            ) { sizeSp ->
                val style = labelStyle.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * LINE_HEIGHT).sp)
                labels.map { label ->
                    SegmentFit.Label(
                        line = measurer.lineWidth(label, style),
                        word = label.split(' ', '\n', '\t').filter { it.isNotEmpty() }.maxOfOrNull { measurer.lineWidth(it, style) } ?: 0f,
                    )
                }
            }
        }
        val style = if (plan.sizeSp == fontSize.toFloat()) labelStyle else labelStyle.copy(fontSize = plan.sizeSp.sp, lineHeight = (plan.sizeSp * LINE_HEIGHT).sp)
        // a row of no width yet (a transition) has nothing to share
        SwitchRow(labels, selectedIndex, onSelect, Modifier, compact, style, plan.widths.takeIf { widths -> widths.all { it > 0f } }, description, looks)
    }
}

/** What of a switch is not about its words: its ground, and what of it answers. */
private class SwitchLooks(val containerColor: Color, val enabled: Boolean, val segmentEnabled: (Int) -> Boolean)

@Composable
private fun SwitchRow(
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    compact: Boolean,
    labelStyle: TextStyle,
    weights: List<Float>?,
    description: String?,
    looks: SwitchLooks,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (looks.containerColor.isSpecified) looks.containerColor else colors.surfaceContainer
    val edge = if (compact) CompactEdge else RegularEdge
    val between = if (compact) CompactBetween else RegularBetween
    // the compact container is drawn in the middle of its touch target, 10 dp of air above and below it
    val inset = if (compact) (CompactTouch - CompactHeight) / 2 else 0.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 52 (48 to press the compact one) at the usual font; the tallest label decides above it, and the segments and their
            // pills stretch to the height — the whole of it stays pressed
            .heightIn(min = if (compact) CompactTouch else RegularHeight)
            .height(IntrinsicSize.Min)
            // dimmed as a whole, its ground too, in its own colours — as a disabled button is
            .then(if (looks.enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .then(
                if (compact) {
                    Modifier.drawBehind {
                        val corner = CompactCorner.toPx()
                        val air = inset.toPx()
                        drawRoundRect(container, topLeft = Offset(0f, air), size = Size(size.width, size.height - 2 * air), cornerRadius = CornerRadius(corner))
                    }
                } else {
                    Modifier.clip(AppShapes.Control).background(container)
                },
            )
            .selectableGroup()
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val answers = looks.enabled && looks.segmentEnabled(index)
            // a segment of its own is dimmed alone; a switch dimmed as a whole is not dimmed twice
            val segmentDim = if (looks.enabled && !answers) Modifier.alpha(DISABLED_ALPHA) else Modifier
            val pill by animateColorAsState(if (selected) colors.primaryContainer else Color.Transparent, tween(SWITCH_MS), label = "segment")
            val interaction = remember { MutableInteractionSource() }
            val pillPresses = remember(interaction) { ShiftedInteractionSource(interaction) }
            Box(
                modifier = Modifier
                    .weight(weights?.getOrNull(index) ?: 1f)
                    .fillMaxHeight()
                    .then(segmentDim)
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
                        enabled = answers,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .padding(
                            start = if (index == 0) edge else between,
                            end = if (index == labels.lastIndex) edge else between,
                            top = inset + edge,
                            bottom = inset + edge,
                        )
                        // the press comes in the coordinates of the whole segment, the ripple is drawn in the pill
                        .onPlaced { pillPresses.shift = it.positionInParent() }
                        .fillMaxSize()
                        .clip(if (compact) AppShapes.S else AppShapes.Segment)
                        .background(pill)
                        .indication(pillPresses, ripple())
                        .padding(horizontal = LabelPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = labelStyle,
                    )
                }
            }
        }
    }
}

/** The label's own padding inside its pill, at each side. */
private val LabelPadding = 4.dp

/** The row is laid out in whole pixels: a word that fits only by a hair is not trusted. */
private val SegmentSlack = 1.dp

/**
 * Around a label within its segment, for [SegmentFit]: the edge or the gap at each side of its pill and its own padding — 4 + 2 + 4 + 4
 * at the ends of the row, 2 + 2 + 4 + 4 in the middle (the compact one: 2 or 1 instead of 4 and 2).
 */
private fun labelRoom(index: Int, last: Int, compact: Boolean): Dp {
    val edge = if (compact) CompactEdge else RegularEdge
    val between = if (compact) CompactBetween else RegularBetween
    return (if (index == 0) edge else between) + (if (index == last) edge else between) + LabelPadding * 2
}

private fun TextMeasurer.lineWidth(text: String, style: TextStyle): Float =
    measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat()
