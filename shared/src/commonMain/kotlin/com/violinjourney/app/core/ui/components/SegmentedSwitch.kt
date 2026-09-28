package com.violinjourney.app.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.theme.AppShapes

private const val SWITCH_MS = 200
private const val LINE_HEIGHT = 1.15f

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
 */
@Composable
fun SegmentedSwitch(
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fontSize: Int = 14,
) {
    val colors = MaterialTheme.colorScheme
    val container = colors.surfaceContainer
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
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val pill by animateColorAsState(if (selected) colors.primaryContainer else Color.Transparent, tween(SWITCH_MS), label = "segment")
            val interaction = remember { MutableInteractionSource() }
            val pillPresses = remember(interaction) { ShiftedInteractionSource(interaction) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
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
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * LINE_HEIGHT).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}
