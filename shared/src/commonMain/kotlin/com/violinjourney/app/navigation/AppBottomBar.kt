package com.violinjourney.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.ShiftedInteractionSource
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.TabIcon
import org.jetbrains.compose.resources.stringResource

private val IconSize = 24.dp
private val MarkSize = 8.dp
private val MarkOutline = 2.dp
/** Where the mark sits relative to the icon: right 11 / top 3 of the 56×32 pill, right 7 of the compact one (handoff 14b, 14c; components.html). */
private val MarkOffsetX = 5.dp
private val MarkOffsetY = (-1).dp

/** The tall bar (spec 5.29): 6 above the items, an item of 56, the system's inset below; 8 at the sides, as in components.html. */
private val TallBarTop = 6.dp
private val TallBarSide = 8.dp
private val TallItemHeight = 56.dp
private val TallPillWidth = 56.dp
private val TallPillHeight = 32.dp
private val TallPillCorner = 16.dp
private val TallLabelGap = 4.dp
/** A label keeps 2 dp off each side of its item: the room it is sized for is the item less these (82 dp on a 360-dp screen). */
private val TallLabelInset = 2.dp

private val CompactBarHeight = 64.dp
private val CompactPillWidth = 48.dp
private val CompactPillHeight = 32.dp
private val CompactPillCorner = 16.dp
private val CompactPaddingHorizontal = 6.dp
private val CompactPaddingVertical = 4.dp
private val CompactLabelGap = 8.dp
/** What an item of the compact bar takes besides its label: the paddings, the pill and the gap. */
private val CompactItemChrome = CompactPaddingHorizontal * 2 + CompactPillWidth + CompactLabelGap
private const val MARK_MS = 150
private const val ICON_SWAP_MS = 150

/**
 * The tab bar (spec 3.36.1, 5.29): four tabs — «Занятия · Live · Репертуар · Записи». [practiceRunning] puts a mark on the
 * «Занятия» tab, seen from any tab; [compact] is the 64-dp landscape bar with the label beside the pill (handoff 10i, 10j),
 * pressed over its whole height. [dimmed] is how bright the items are, 1 — in full: read only while drawing, the background
 * of the bar stays and taps pass through. Live lends it its light while resumed ([TabBarLight], spec 3.36.6) — the bar is under it
 * only upright — behind the switch `LiveSwitches.DIM_TAB_BAR`, off by default.
 */
@Composable
fun AppBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    practiceRunning: Boolean = false,
    compact: Boolean = false,
    dimmed: () -> Float = { 1f },
) {
    if (compact) {
        CompactBar(current, onSelect, practiceRunning, dimmed, modifier)
    } else {
        TallBar(current, onSelect, practiceRunning, dimmed, modifier)
    }
}

/**
 * A row of its own, as [CompactBar], instead of the 80-dp `NavigationBar` of Material: an item of 56 with the pill of 56×32
 * (spec 5.29). The labels are of one size, the largest at which all four fit their items on one line ([TabLabels]).
 */
@Composable
private fun TallBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    practiceRunning: Boolean,
    dimmed: () -> Float,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val destinations = TopLevelDestination.entries
    val labels = destinations.map { stringResource(it.labelRes) }
    // the weight of the design and no tracking: the 0.5 sp of labelMedium alone would keep «Enregistrements» off 10 sp on 360 dp
    val baseStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(start = TallBarSide, top = TallBarTop, end = TallBarSide),
    ) {
        val room = with(density) { (maxWidth / destinations.size - TallLabelInset * 2).toPx() }
        // the style is a key: on iOS Manrope comes a frame after the first one, and the size must follow the real font
        val labelSp = remember(labels, room, baseStyle, density, measurer) {
            TabLabels.size(room) { sizeSp -> labels.maxOf { measurer.widthOf(it, baseStyle.copy(fontSize = sizeSp.sp)) } }
        }
        val labelStyle = baseStyle.copy(fontSize = labelSp.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = dimmed() }
                .selectableGroup(),
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = destination == current
                val interaction = remember { MutableInteractionSource() }
                val pillPresses = remember(interaction) { ShiftedInteractionSource(interaction) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TallItemHeight)
                        .selectable(
                            selected = selected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(destination) },
                        ),
                    verticalArrangement = Arrangement.spacedBy(TallLabelGap, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // the ripple stays in the pill, as the indicator of Material had it; the whole item is pressed
                    Box(
                        modifier = Modifier
                            .onPlaced { pillPresses.shift = it.positionInParent() }
                            .size(TallPillWidth, TallPillHeight)
                            .clip(RoundedCornerShape(TallPillCorner))
                            .background(if (selected) colors.primaryContainer else Color.Transparent)
                            .indication(pillPresses, ripple()),
                        contentAlignment = Alignment.Center,
                    ) {
                        TabGlyph(destination, selected = selected, marked = practiceRunning && destination == TopLevelDestination.PRACTICE)
                    }
                    Text(
                        text = labels[index],
                        modifier = Modifier.padding(horizontal = TallLabelInset),
                        color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = labelStyle,
                    )
                }
            }
        }
    }
}

/**
 * The landscape bar (handoff 10i, 10j): the look as before — a pill of 48×32 with the label beside it, the items spread
 * evenly — but an item is pressed over the whole height of the bar, 64 dp, not only around the pill (spec 3.36.1). The
 * labels stay on one line and of one size; at 12 sp they fit even the narrowest 640 dp, the size only gives way with a
 * large font.
 */
@Composable
private fun CompactBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    practiceRunning: Boolean,
    dimmed: () -> Float,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val destinations = TopLevelDestination.entries
    val labels = destinations.map { stringResource(it.labelRes) }
    val baseStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .height(CompactBarHeight),
    ) {
        val room = with(density) { maxWidth.toPx() }
        val chrome = with(density) { CompactItemChrome.toPx() } * destinations.size
        val labelSp = remember(labels, room, chrome, baseStyle, density, measurer) {
            TabLabels.size(room) { sizeSp -> chrome + labels.sumOf { measurer.widthOf(it, baseStyle.copy(fontSize = sizeSp.sp)).toDouble() }.toFloat() }
        }
        val labelStyle = baseStyle.copy(fontSize = labelSp.sp)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dimmed() }
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = destination == current
                val interaction = remember { MutableInteractionSource() }
                val placePresses = remember(interaction) { ShiftedInteractionSource(interaction) }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(destination) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // the ripple keeps the rounded place it always had around the pill and the label
                    Row(
                        modifier = Modifier
                            .onPlaced { placePresses.shift = it.positionInParent() }
                            .clip(RoundedCornerShape(CompactPillCorner))
                            .indication(placePresses, ripple())
                            .padding(horizontal = CompactPaddingHorizontal, vertical = CompactPaddingVertical),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CompactLabelGap),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(CompactPillWidth, CompactPillHeight)
                                .background(if (selected) colors.primaryContainer else colors.surfaceContainer, RoundedCornerShape(CompactPillCorner)),
                            contentAlignment = Alignment.Center,
                        ) {
                            TabGlyph(
                                destination = destination,
                                selected = selected,
                                marked = practiceRunning && destination == TopLevelDestination.PRACTICE,
                            )
                        }
                        Text(
                            text = labels[index],
                            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            style = labelStyle,
                        )
                    }
                }
            }
        }
    }
}

/** The width a label takes on one line, in pixels. */
private fun TextMeasurer.widthOf(text: String, style: TextStyle): Float =
    measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat()

private fun TopLevelDestination.tabIcon(): TabIcon = when (this) {
    TopLevelDestination.LIVE -> AppIcons.TabLive
    TopLevelDestination.PRACTICE -> AppIcons.TabPractice
    TopLevelDestination.REPERTOIRE -> AppIcons.TabRepertoire
    TopLevelDestination.HISTORY -> AppIcons.TabRecords
}

/**
 * The outline of the tab, or — selected — the same sign with its body filled and the detail
 * inside it painted in the colour of the pill, which reads as cut out (spec 3.16, handoff 14b).
 */
@Composable
private fun TabGlyph(destination: TopLevelDestination, selected: Boolean, marked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val icon = destination.tabIcon()
    Box(modifier = Modifier.size(IconSize)) {
        Crossfade(targetState = selected, animationSpec = tween(ICON_SWAP_MS), label = "tabIcon") { filled ->
            if (filled) {
                AppIcon(icon.selected, contentDescription = null, tint = colors.onPrimaryContainer)
                icon.selectedCut?.let { AppIcon(it, contentDescription = null, tint = colors.primaryContainer) }
            } else {
                AppIcon(icon.normal, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
        // "A practice is running", visible from any tab (spec 3.12); the outline — in the colour of what lies under it — keeps it off the icon.
        AnimatedVisibility(
            visible = marked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = MarkOffsetX, y = MarkOffsetY),
            enter = scaleIn(tween(MARK_MS)) + fadeIn(tween(MARK_MS)),
            exit = scaleOut(tween(MARK_MS)) + fadeOut(tween(MARK_MS)),
        ) {
            Box(
                modifier = Modifier
                    .size(MarkSize + MarkOutline * 2)
                    .background(if (selected) colors.primaryContainer else colors.surfaceContainer, CircleShape)
                    .padding(MarkOutline)
                    .background(colors.primary, CircleShape),
            )
        }
    }
}
