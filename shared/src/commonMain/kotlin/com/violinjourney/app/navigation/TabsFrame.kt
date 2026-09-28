package com.violinjourney.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private enum class TabsSlot { Bar, Body }

/**
 * The root of the tabs (spec 3.36.2): the screens over the ground of the app and the tab bar at the bottom — what Material's
 * `Scaffold` was here, with one difference. `Scaffold` places the bar after the body, so the opaque bar is drawn over it and cuts
 * whatever a screen draws past its bottom edge — the glow of the living «Начать занятие», which the spec lays on the bar (3.36.2,
 * 5.10). Here the bar is measured first, as there, and the body gets the same padding — the bar's height at the bottom, or the
 * bottom inset without a bar; the [contentWindowInsets] at the top and the sides — but the body is placed after the bar and drawn
 * over it. The body draws nothing of its own in the strip of the bar: the screens pad themselves by the [PaddingValues] they get,
 * and a touch there finds no target in them and goes on to the bar.
 *
 * The body gets its padding as `Scaffold` gave it: apply it with `padding` and `consumeWindowInsets`.
 */
@Composable
fun TabsFrame(
    bottomBar: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    // written while measuring, before the body is composed: the body is recomposed only when it changes
    val padding = remember { MeasuredPadding() }
    val bar: @Composable () -> Unit = remember(bottomBar) { { Box { bottomBar() } } }
    val body: @Composable () -> Unit = remember(content, padding) { { Box { content(padding) } } }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        SubcomposeLayout { constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val barPlaceable = subcompose(TabsSlot.Bar, bar).first().measure(loose)
            val noBar = barPlaceable.width == 0 && barPlaceable.height == 0
            val insets = contentWindowInsets.asPaddingValues(this)
            padding.holder = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                top = insets.calculateTopPadding(),
                end = insets.calculateEndPadding(layoutDirection),
                bottom = if (noBar) insets.calculateBottomPadding() else barPlaceable.height.toDp(),
            )
            val bodyPlaceable = subcompose(TabsSlot.Body, body).first().measure(loose)
            layout(width, height) {
                barPlaceable.place(0, height - barPlaceable.height)
                // after the bar: what a screen draws past its bottom edge lies on the bar
                bodyPlaceable.place(0, 0)
            }
        }
    }
}

/** One padding for the body whose values change while measuring, as `Scaffold` keeps it: no new object, no recomposition. */
private class MeasuredPadding : PaddingValues {
    var holder by mutableStateOf(PaddingValues(0.dp))

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = holder.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = holder.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = holder.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = holder.calculateBottomPadding()
}
