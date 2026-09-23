package com.violinjourney.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.TabIcon
import com.violinjourney.app.core.ui.theme.ViolinTheme

private val IconSize = 24.dp
private val MarkSize = 8.dp
private val MarkOutline = 2.dp
/** Where the mark sits relative to the icon: right 15 / top 3 of the 64×32 pill, right 7 of the compact one (handoff 14b, 14c). */
private val MarkOffsetX = 5.dp
private val MarkOffsetY = (-1).dp
private val CompactBarHeight = 64.dp
private val CompactPillWidth = 48.dp
private val CompactPillHeight = 32.dp
private val CompactPillCorner = 16.dp
private const val MARK_MS = 150
private const val ICON_SWAP_MS = 150

/**
 * The tab bar (spec 4). [practiceRunning] puts a mark on the «Занятия» tab; [compact] is the
 * 64-dp landscape bar with the label beside the pill (handoff 10i, 10j).
 */
@Composable
fun AppBottomBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    practiceRunning: Boolean = false,
    compact: Boolean = false,
) {
    if (compact) {
        CompactBar(current, onSelect, practiceRunning, modifier)
    } else {
        TallBar(current, onSelect, practiceRunning, modifier)
    }
}

@Composable
private fun TallBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    practiceRunning: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surfaceContainer,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = { TabGlyph(destination, selected = destination == current, marked = practiceRunning && destination == TopLevelDestination.PRACTICE) },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onPrimaryContainer,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun CompactBar(
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    practiceRunning: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .height(CompactBarHeight)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(CompactPillCorner))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    text = stringResource(destination.labelRes),
                    color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                )
            }
        }
    }
}

private fun TopLevelDestination.tabIcon(): TabIcon = when (this) {
    TopLevelDestination.LIVE -> AppIcons.TabLive
    TopLevelDestination.PRACTICE -> AppIcons.TabPractice
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

@Preview
@Composable
private fun AppBottomBarPreview() {
    ViolinTheme {
        AppBottomBar(current = TopLevelDestination.LIVE, onSelect = {}, practiceRunning = true)
    }
}

@Preview(widthDp = 892)
@Composable
private fun AppBottomBarCompactPreview() {
    ViolinTheme {
        AppBottomBar(current = TopLevelDestination.PRACTICE, onSelect = {}, practiceRunning = true, compact = true)
    }
}
