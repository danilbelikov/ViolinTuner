package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The heights of the fade over the bottom zone (spec 5.29): 28 by default, 16 in the left column of «Занятия» in landscape, 0 — none. */
object DockDefaults {
    val Fade = 28.dp
    val FadeLeftColumn = 16.dp

    /** Between the rows of the zone: the main button and the halves under it, the line of a missing permission and the key. */
    val RowGap = 10.dp
}

/**
 * The height of the bottom zone for what scrolls under it — the bottom padding of a list, so that nothing stays hidden under the
 * buttons for good. Given by [AppDock] to its content from the first frame; 0 outside one, and 0 while the keyboard is up under a
 * zone that rises with it — the content then ends at the top of the zone and nothing runs under it.
 */
val LocalDockInset = compositionLocalOf { 0.dp }

/** What the rows of the bottom zone know: the zone is [compact] (a window no higher than 360 dp) — its buttons are [buttonHeight]. */
@LayoutScopeMarker
@Stable
interface DockScope : ColumnScope {
    val compact: Boolean
    val buttonHeight: Dp
}

private class DockScopeInstance(column: ColumnScope, override val compact: Boolean, override val buttonHeight: Dp) : DockScope, ColumnScope by column

private enum class DockSlot { Zone, Content }

/**
 * A screen or a column with its main action pinned at the bottom (spec 3.36.1, 5.29, «Нижняя зона»): «Смотрят — сверху,
 * нажимают — снизу». The [content] takes the whole height and scrolls under the zone; the zone stands at the bottom, over it, and
 * gives its height to the content as [LocalDockInset] — a list pads its end with it (`contentPadding` of a lazy list, or a spacer
 * at the end of a column). The zone is measured first, as the bars of a Scaffold are, so the padding is there from the first frame.
 *
 * The zone ([dock]) is a column of rows 10 dp apart — the main button; a row of two halves under it; the line of a missing
 * permission over a sleeping key — on the ground of the screen, with its fields from [metrics] (by the height of the window,
 * [DockMetrics.of]) and the bottom system inset under them; without the tabs that is the navigation bar, over the tabs nothing.
 * [aboveKeyboard] lifts it with the keyboard (a form: «Сохранить» under the thumb while typing) — the roots give the screens their
 * insets without the keyboard, so the zone asks for it itself. While the keyboard is up, the content no longer runs under the zone:
 * it is measured to the top of the zone, with [LocalDockInset] 0 and no fade, so its scroll window shrinks and the scroll brings
 * the field in focus above the zone by itself (a fade there would lie over the line being typed). [padSides] false — the sides
 * are given by the column around it (the left column of landscape:
 * `AppDock(…, Modifier.width(280.dp), padSides = false, fade = DockDefaults.FadeLeftColumn)`). [pinned] false — the zone has left
 * its place for the end of the content, where its owner lays its rows with [DockRows] (a form lying over a keyboard too high for its
 * zone in one line and a field together, 5.29 R4): [dock] is not composed, nothing is drawn at the bottom, and over the keyboard the
 * content ends at the top of the keyboard — the whole room over it is the content's.
 * [DockScope.compact] and [DockScope.buttonHeight] tell its buttons their height: `AppButton(compact = compact)`, the living
 * «Начать занятие» at `Modifier.height(buttonHeight)`.
 *
 * Over the zone the content fades into the ground over [fade] — a gradient drawn above its top edge, not a layer; the zone itself
 * has no clip, no layer and no alpha, so the glow of the living «Начать занятие» (σ 12, 5.10) is not cut by it — a layer with an
 * alpha below 1 is drawn into its own buffer within its bounds and would cut it; dim the buttons, not the zone. The ground of the
 * zone takes the touches between its buttons — a touch there does not reach the list under it; the fade lets them through.
 *
 * On iOS the zone reports its top to the root ([LocalDockPlace]) while it is shown: the message of iOS stands 12 dp above it.
 *
 * Only in a bounded height — the root of a screen or of a column, never inside a vertical scroll. Not for sheets: a sheet has
 * [AppSheetButtons].
 */
@Composable
fun AppDock(
    dock: @Composable DockScope.() -> Unit,
    modifier: Modifier = Modifier,
    fade: Dp = DockDefaults.Fade,
    aboveKeyboard: Boolean = false,
    padSides: Boolean = true,
    metrics: DockMetrics = currentDockMetrics(),
    pinned: Boolean = true,
    content: @Composable () -> Unit,
) {
    // written while measuring, before the content is composed, and read by it: the content is recomposed only when it changes
    val inset = remember { mutableStateOf(0.dp) }
    val keyboard = WindowInsets.ime
    val density = LocalDensity.current
    // the keyboard is up under a zone that rises with it; derived, so a rising keyboard recomposes nothing until it flips
    val overKeyboard = remember(aboveKeyboard, keyboard, density) {
        derivedStateOf { aboveKeyboard && keyboard.getBottom(density) > 0 }
    }
    // the zone gone into the content leaves at the bottom only the room of the system — the keyboard, for one over it
    val zone: @Composable () -> Unit = if (pinned) {
        { DockZone(metrics, if (overKeyboard.value) 0.dp else fade, aboveKeyboard, padSides, dock) }
    } else {
        { Spacer(Modifier.fillMaxWidth().windowInsetsPadding(bottomInsets(aboveKeyboard))) }
    }
    val body: @Composable () -> Unit = { CompositionLocalProvider(LocalDockInset provides inset.value, content = content) }
    SubcomposeLayout(modifier) { constraints ->
        check(constraints.hasBoundedHeight) { "AppDock needs a bounded height: the root of a screen or of a column, not a vertical scroll" }
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val zonePlaceables = subcompose(DockSlot.Zone, zone).map { it.measure(loose) }
        val zoneHeight = zonePlaceables.maxOfOrNull { it.height } ?: 0
        // over the keyboard the content ends at the top of the zone: its scroll window shrinks, and the scroll keeps the field in
        // focus in sight; otherwise it runs under the zone and pads its end with the height of it
        val contentConstraints = if (overKeyboard.value) {
            val above = (constraints.maxHeight - zoneHeight).coerceAtLeast(0)
            constraints.copy(minHeight = constraints.minHeight.coerceAtMost(above), maxHeight = above)
        } else {
            constraints
        }
        inset.value = if (overKeyboard.value) 0.dp else zoneHeight.toDp()
        val contentPlaceables = subcompose(DockSlot.Content, body).map { it.measure(contentConstraints) }
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            (zonePlaceables + contentPlaceables).maxOfOrNull { it.width } ?: constraints.minWidth
        }
        val height = constraints.maxHeight
        layout(width, height) {
            contentPlaceables.forEach { it.place(0, 0) }
            // after the content: the zone is drawn over what scrolls under it
            zonePlaceables.forEach { it.place(0, height - it.height) }
        }
    }
}

@Composable
private fun DockZone(metrics: DockMetrics, fade: Dp, aboveKeyboard: Boolean, padSides: Boolean, dock: @Composable DockScope.() -> Unit) {
    val ground = MaterialTheme.colorScheme.surface
    val side = if (padSides) metrics.side else 0.dp
    DockRows(
        metrics = metrics,
        modifier = Modifier
            .fillMaxWidth()
            .then(reportedToRoot())
            .drawBehind {
                // the fade lies above the zone, outside its bounds: a gradient, not a layer — a layer would be dear on iOS
                val fadePx = fade.toPx()
                if (fadePx > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(ground.copy(alpha = 0f), ground), startY = -fadePx, endY = 0f),
                        topLeft = Offset(0f, -fadePx),
                        size = Size(size.width, fadePx),
                    )
                }
                drawRect(ground)
            }
            // a hit for touches between the buttons, so they do not reach the list under the zone; nothing is done with them
            .pointerInput(Unit) {}
            .windowInsetsPadding(bottomInsets(aboveKeyboard))
            .padding(start = side, end = side, top = metrics.top, bottom = metrics.bottom),
        dock = dock,
    )
}

/**
 * The rows of a bottom zone ([AppDock]): 10 apart, with the [DockScope] of [metrics] — the buttons know their height. In the zone
 * itself; or, while an [AppDock] is not pinned, where its owner lays them — at the end of what scrolls, without the ground, the
 * fade and the insets of the zone.
 */
@Composable
internal fun DockRows(metrics: DockMetrics, modifier: Modifier = Modifier, dock: @Composable DockScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
        val scope = remember(this, metrics) { DockScopeInstance(this, metrics.compact, metrics.button) }
        scope.dock()
    }
}

/** The insets under a zone: the bottom of the safe area — with the keyboard in it for a zone that rises with the keyboard. */
@Composable
private fun bottomInsets(aboveKeyboard: Boolean): WindowInsets = if (aboveKeyboard) {
    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
} else {
    WindowInsets.safeDrawing.exclude(WindowInsets.ime).only(WindowInsetsSides.Bottom)
}

/** On iOS — the top of the zone, reported to the root for the message; nothing on Android. */
@Composable
private fun reportedToRoot(): Modifier {
    val place = LocalDockPlace.current ?: return Modifier
    val owner = remember { Any() }
    DisposableEffect(place) { onDispose { place.withdraw(owner) } }
    return Modifier.onGloballyPositioned { coordinates ->
        val root = coordinates.findRootCoordinates()
        place.report(owner, (root.size.height - coordinates.positionInRoot().y).roundToInt())
    }
}
