package com.example.violintuner.navigation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.core.ui.theme.ViolinTheme

private val IconSize = 24.dp
private val MarkSize = 8.dp
private val MarkOutline = 2.dp
/** Where the mark sits relative to the icon: right 14 / top 4 of the 64×32 pill (handoff 10i). */
private val MarkOffsetX = 6.dp
private val CompactBarHeight = 64.dp
private val CompactPillWidth = 48.dp
private val CompactPillHeight = 32.dp
private val CompactPillCorner = 16.dp
private const val MARK_MS = 150

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
                icon = { TabIcon(destination, marked = practiceRunning && destination == TopLevelDestination.PRACTICE) },
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
                    TabIcon(
                        destination = destination,
                        marked = practiceRunning && destination == TopLevelDestination.PRACTICE,
                        tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
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

@Composable
private fun TabIcon(destination: TopLevelDestination, marked: Boolean, tint: androidx.compose.ui.graphics.Color? = null) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(IconSize)) {
        if (tint == null) {
            Icon(painter = painterResource(destination.iconRes), contentDescription = null)
        } else {
            Icon(painter = painterResource(destination.iconRes), contentDescription = null, tint = tint)
        }
        // "A practice is running", visible from any tab (spec 3.12); the outline keeps it off the icon.
        AnimatedVisibility(
            visible = marked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = MarkOffsetX),
            enter = scaleIn(tween(MARK_MS)) + fadeIn(tween(MARK_MS)),
            exit = scaleOut(tween(MARK_MS)) + fadeOut(tween(MARK_MS)),
        ) {
            Box(
                modifier = Modifier
                    .size(MarkSize + MarkOutline * 2)
                    .background(colors.surfaceContainer, CircleShape)
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
