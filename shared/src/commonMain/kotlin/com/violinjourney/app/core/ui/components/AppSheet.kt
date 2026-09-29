package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.theme.ViolinTheme
import kotlin.math.max
import kotlin.math.min

// The frame of a sheet (spec 5.29; components.html, «Лист»).
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val HandleWidth = 36.dp
private val HandleHeight = 5.dp
private val ButtonsTop = 18.dp
private val ButtonsGap = 6.dp

/** Over the buttons at the bottom of a sheet, while what is above them still scrolls on: a fade to the colour of the sheet (5.29 R3). */
private val ButtonsFade = 16.dp

/** What the sheets of the app have in common. */
object AppSheetDefaults {
    /** 20 at the sides, 16 at the bottom — the bottom system inset comes on top of it, from the sheet itself. */
    val ContentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)

    /** A sheet is never wider than this, in landscape too, the recap of a practice as well (spec 3.36.1, rule 4). */
    @OptIn(ExperimentalMaterial3Api::class)
    val MaxWidth = BottomSheetDefaults.SheetMaxWidth

    /**
     * Between the top of a sheet and the status bar at the least (5.29 R2, R3): a long sheet scrolls rather than reach the top. Given
     * as `Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = TopClearance)` to the sheets that can grow that high.
     */
    val TopClearance = 16.dp
}

/**
 * The frame of every sheet (spec 3.36.1, 5.29): surfaceContainer at a corner of 28 at the top, the handle 36 × 5, the scrim of
 * black at 0.55, never wider than 640 dp — in the middle in landscape — and never under a cutout of the camera or a bar of the
 * system at a side of the window ([clearOfTheSides]; 5.29 R4). Shown while [value] is not null, and it slides away when the
 * owner drops it, as a swiped one does ([rememberHeldSheet]; [slideAway] false — another sheet takes its place and it goes at once).
 * The [content] gets the value it shows — the last one while it slides away — in a column with [contentPadding] that scrolls when
 * the sheet is higher than the window ([scroll] false for a value that lays out its own columns, each scrolling: the recap in a
 * low window, R3).
 *
 * One frame, many faces (spec 3.36.3): a new value while the sheet is up changes what it shows in place — no slide, no blink of the
 * scrim; the frame takes the height of the new face at once. A value given while the sheet is hidden — the parent of a nested face
 * after a swipe, the next gift — brings it up again; one given while it slides down brings it up once it is down. [faceOf] tells
 * which face a value is (its class, unless the owner says more: each gift of a row is a face of its own): a new face starts at its
 * top, and one that took the place of another while the frame stood does not take a press of its main button for the time of a
 * double tap — the finger that pressed the button before it, pressing again, lands on it (spec 5.29 R3); a swipe and «назад» are
 * not held.
 *
 * [onHide] — the sheet was swiped down, tapped outside or closed with «назад», and it is hidden; nothing more. It gets the value the
 * frame showed when it began to go down, not one that came while it slid (that one brings the frame up again), and it is not told
 * of a sheet its owner has already dropped. A swipe does not save, delete or cancel anything (the rule of the owner: hiding a sheet
 * is not a cancel) — only the buttons of the sheet decide. Where the spec gives a swipe a meaning — a sheet with one safe answer,
 * «Спасибо» of the gift, «Продолжаю заниматься» of the forgotten practice (R3) — the owner decides it in [onHide]. Either way the
 * owner must drop [value] or give another one.
 *
 * [onBack] — «назад» of a face that stands over another one (spec 3.36.3: «Время за день» over the sheet of the day): the owner puts
 * the parent back in place, and the frame stays where it is. Without it «назад» is [onHide], after the frame has slid down.
 *
 * [bottom] — the buttons of a value, pinned to the bottom of the sheet (spec 3.36.3, 5.29 R3): what is above them scrolls, and while
 * it still scrolls on, a fade of 16 to the colour of the sheet lies over the top of them. Null for a value without buttons: its
 * content scrolls to the bottom edge, as before. [buttonsInContentOverKeyboard] — a value whose buttons leave the bottom for the end
 * of what scrolls while the keyboard is up («Имя и фото» in a low window, 5.29 R3): the little the keyboard leaves of the sheet goes
 * to the field, and the keyboard's own «Готово» answers.
 *
 * [dismissible] false — a swipe, the scrim and «назад» do not close it, and it has no handle to pull (the analysis of a video
 * take, R4; the preparing of «Поделиться», R5): it goes only when the owner drops the value, and then it slides away as any sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> AppSheet(
    value: T?,
    onHide: (T) -> Unit,
    modifier: Modifier = Modifier,
    slideAway: Boolean = true,
    dismissible: Boolean = true,
    scroll: (T) -> Boolean = { true },
    contentPadding: PaddingValues = AppSheetDefaults.ContentPadding,
    onBack: (() -> Unit)? = null,
    bottom: (T) -> (@Composable ColumnScope.() -> Unit)? = { null },
    buttonsInContentOverKeyboard: (T) -> Boolean = { false },
    faceOf: (T) -> Any = { it::class },
    content: @Composable ColumnScope.(T) -> Unit,
) {
    // A sheet that may not be closed refuses to hide — until its owner lets it go: SheetState.hide() asks the same question, and
    // without the second half the sheet let go would vanish without sliding away. The question is remembered once:
    // rememberModalBottomSheetState keys its saved state on it, and a new lambda would be a new state at every recomposition.
    val canHide = rememberUpdatedState(dismissible || value == null)
    val confirm = remember { { next: SheetValue -> next != SheetValue.Hidden || canHide.value } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirm)
    val shown = rememberHeldSheet(value, sheetState, slideAway) ?: return
    // The value the frame showed while it stood or rose: what a hide means. Read here, the frame recomposes when it starts to go
    // down, and a value that comes while it slides is not taken for the one the finger took down.
    val goingDown = sheetState.targetValue == SheetValue.Hidden
    val onScreen = remember { OnScreen<T>() }
    SideEffect { if (!goingDown) onScreen.value = shown }
    RiseForNewValue(value, sheetState, takenDown = { onScreen.value })
    val back = rememberUpdatedState(onBack)
    val face = faceOf(shown)
    // a new face starts at its top, not where the one it replaced was scrolled to
    val scrollState = remember(face) { ScrollState(0) }
    val direction = LocalLayoutDirection.current
    val hide = rememberUpdatedState(onHide)
    val held = rememberUpdatedState(value)
    val latest = rememberUpdatedState(shown)
    // a sheet its owner has dropped already is sliding away by the owner's word: nothing to tell
    val dismiss = remember { { if (held.value != null) hide.value(onScreen.value ?: latest.value) } }
    // a face that came in the place of another while the frame stood holds its main button for a double tap
    val arrival = remember(face) { FaceArrival(inPlace = sheetState.isVisible || !goingDown) }
    val doubleTapMs = LocalViewConfiguration.current.doubleTapTimeoutMillis
    LaunchedEffect(arrival) {
        if (!arrival.inPlace) return@LaunchedEffect
        val start = withFrameMillis { it }
        // frame by frame, so that a test's clock that stands holds it too; a few frames, only after a face came in place
        do {
            val now = withFrameMillis { it }
        } while (now - start < doubleTapMs)
        arrival.settled = true
    }
    // made once: the same modifier each time, not a new chain of the sheet at every recomposition of its owner
    val sides = remember { Modifier.clearOfTheSides() }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        modifier = sides.then(modifier),
        sheetState = sheetState,
        sheetMaxWidth = AppSheetDefaults.MaxWidth,
        sheetGesturesEnabled = dismissible,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = ViolinTheme.sheetScrim,
        // Material makes the handle a button that closes the sheet: a sheet that cannot be closed has none
        dragHandle = if (dismissible) ({ SheetHandle() }) else null,
        // Material's own «назад» stays as it was when the window opened (its callback is not updated with the properties): the way
        // back of a face is a handler of its own, registered after Material's and so heard first.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible, shouldDismissOnClickOutside = dismissible),
    ) {
        if (onBack != null) BackHandler { back.value?.invoke() }
        // asked of the value shown, as its buttons are: a value held while it slides away keeps the layout it had
        val scrolls = scroll(shown)
        val scrolling = if (scrolls) Modifier.verticalScroll(scrollState) else Modifier
        val buttons = bottom(shown)
        CompositionLocalProvider(LocalFaceArrival provides arrival) {
            if (buttons == null) {
                Column(Modifier.fillMaxWidth().then(scrolling).padding(contentPadding)) {
                    if (!dismissible) Spacer(Modifier.height(NoHandleTop))
                    content(shown)
                }
            } else {
                // Over the keyboard the buttons of such a value go to the end of what scrolls. The content keeps its place in the
                // tree either way: the field in focus is not made anew — nor its focus lost — when the keyboard comes up.
                val keyboard = WindowInsets.ime
                val density = LocalDensity.current
                val keyboardUp = remember(keyboard, density) { derivedStateOf { keyboard.getBottom(density) > 0 } }
                val inContent = scrolls && buttonsInContentOverKeyboard(shown) && keyboardUp.value
                val fade = MaterialTheme.colorScheme.surfaceContainer
                PinnedBottom(
                    fadeColor = fade,
                    fadeWhen = { !inContent && scrolls && scrollState.canScrollForward },
                    content = {
                        Column(
                            Modifier.fillMaxWidth().then(scrolling).padding(
                                start = contentPadding.calculateStartPadding(direction),
                                top = contentPadding.calculateTopPadding(),
                                end = contentPadding.calculateEndPadding(direction),
                            ),
                        ) {
                            if (!dismissible) Spacer(Modifier.height(NoHandleTop))
                            content(shown)
                            if (inContent) {
                                Column(Modifier.fillMaxWidth().padding(bottom = contentPadding.calculateBottomPadding()), content = buttons)
                            }
                        }
                    },
                    bottom = {
                        if (!inContent) {
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    start = contentPadding.calculateStartPadding(direction),
                                    end = contentPadding.calculateEndPadding(direction),
                                    bottom = contentPadding.calculateBottomPadding(),
                                ),
                                content = buttons,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * The sheet within the part of its window a side of which no cutout of the camera and no bar of the system takes (spec 5.29 R4; the
 * review of stage 110): in the middle of the window while it is clear of them there — 892 × 412, a sheet of 640 — otherwise moved off
 * them and, where the window has no room for 640 between them, narrower: 640 × 360 with a cutout of 36.5 at its left, a sheet of
 * 603.5 from its edge. Its background goes no further either — the scrim stays under the cutout. The insets are the sheet's own
 * window's ([composed]: read where the sheet is laid out, not where it is asked for); the width is the parent's — the whole window.
 */
private fun Modifier.clearOfTheSides(): Modifier = composed {
    val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val window = constraints.maxWidth
        val left = sides.getLeft(this, layoutDirection)
        val right = sides.getRight(this, layoutDirection)
        val width = SheetSides.width(window, AppSheetDefaults.MaxWidth.roundToPx(), left, right)
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(window, placeable.height) { placeable.place(SheetSides.x(window, width, left, right), 0) }
    }
}

/** Where a sheet stands between the sides of its window that are not safe to draw in ([clearOfTheSides]). Pure; pixels. */
internal object SheetSides {
    /** As wide as it may be — [widest] — but no wider than the window between [left] and [right]. */
    fun width(window: Int, widest: Int, left: Int, right: Int): Int = min(widest, window - left - right).coerceAtLeast(0)

    /** In the middle of the [window], unless that puts it over [left] or [right]: then as near the middle as they let it. */
    fun x(window: Int, width: Int, left: Int, right: Int): Int = ((window - width) / 2).coerceIn(left, max(left, window - right - width))
}

/** The value the frame showed the last time it stood or rose. */
private class OnScreen<T : Any> {
    var value: T? = null
}

/**
 * How a face came into the frame: [inPlace] — in the place of another while the frame stood or rose, under the finger that pressed
 * the button before it; [settled] — the time of a double tap has passed since. Read by the main button of the face.
 */
internal class FaceArrival(val inPlace: Boolean) {
    var settled = false

    /** The main button of the face does not take a press yet. */
    val holds: Boolean get() = inPlace && !settled

    companion object {
        /** Outside a frame, or a face that rose with it: nothing is held. */
        val Risen = FaceArrival(inPlace = false)
    }
}

/** How the face whose buttons are composed came into its frame; [AppSheetButtons] holds its main button while it [FaceArrival.holds]. */
internal val LocalFaceArrival = compositionLocalOf { FaceArrival.Risen }

/**
 * A new value given while the frame is hidden brings it up again: the owner put back the parent of a face swiped away, or the next
 * gift after one swiped. One given while the frame goes down — the next gift, when «назад» came right after «Спасибо», or while a
 * finger took the frame — brings it up once it is down: it is never taken down unseen, and no hidden window is left over the screen.
 * The value the frame took down ([takenDown]) is its owner's to answer ([AppSheet]'s onHide), not to raise. The first value is
 * Material's to raise — it shows a sheet that enters the composition by itself, and a show() before the layout would put it in place
 * without sliding up. A sheet rising is left to itself and to [rememberHeldSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> RiseForNewValue(value: T?, sheetState: SheetState, takenDown: () -> T?) {
    val entered = remember { EnteredOnce() }
    LaunchedEffect(value) {
        if (!entered.done) {
            entered.done = true
            return@LaunchedEffect
        }
        if (value == null) return@LaunchedEffect
        // for as long as this value stands: each time the frame comes to rest down under it
        snapshotFlow { !sheetState.isVisible && sheetState.targetValue == SheetValue.Hidden && !sheetState.isAnimationRunning }
            .collect { down -> if (down && value != takenDown() && sheetState.hasExpandedState) sheetState.show() }
    }
}

private class EnteredOnce {
    var done = false
}

/**
 * The content over the buttons at the bottom of a sheet: the buttons are measured first and keep their height; the content gets what
 * is left and is placed from the top; the buttons stand at the bottom and draw [fadeColor] over the last 16 dp of the content while
 * [fadeWhen] says it goes on below. In a height without bound (a preview) the content is as tall as it is. A minimum height given to
 * it (a column as tall as its neighbour, the recap in a low window) puts the buttons at the bottom of that height.
 */
@Composable
internal fun PinnedBottom(
    fadeColor: Color,
    fadeWhen: () -> Boolean,
    content: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val faded: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxWidth().drawBehind {
                if (!fadeWhen()) return@drawBehind
                val fade = ButtonsFade.toPx()
                drawRect(
                    brush = Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor), startY = -fade, endY = 0f),
                    topLeft = Offset(0f, -fade),
                    size = Size(size.width, fade),
                )
            },
        ) { bottom() }
    }
    Layout(contents = listOf(content, faded), modifier = modifier.fillMaxWidth()) { (contentMeasurables, bottomMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val bottoms = bottomMeasurables.map { it.measure(loose) }
        val bottomHeight = bottoms.sumOf { it.height }
        val contentMax = if (constraints.hasBoundedHeight) (constraints.maxHeight - bottomHeight).coerceAtLeast(0) else Constraints.Infinity
        val contents = contentMeasurables.map { it.measure(loose.copy(maxHeight = contentMax)) }
        val contentHeight = contents.sumOf { it.height }
        val width = (contents + bottoms).maxOfOrNull { it.width }?.coerceIn(constraints.minWidth, constraints.maxWidth) ?: constraints.minWidth
        val height = (contentHeight + bottomHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            contents.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
            // placed after the content: its fade is drawn over the content's last lines
            var bottomY = height - bottomHeight
            bottoms.forEach { placeable ->
                placeable.placeRelative(0, bottomY)
                bottomY += placeable.height
            }
        }
    }
}

/** A sheet without a handle starts its content this far from its top edge. */
private val NoHandleTop = 20.dp

/**
 * The handle of a sheet: 36 × 5 in outlineVariant, in a touch area of 49 (22 above and below it, as Material lays it out) and with
 * Material's own words for TalkBack in every language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    BottomSheetDefaults.DragHandle(modifier = modifier, width = HandleWidth, height = HandleHeight, color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * The frame of [AppSheet] without its window, for a preview (a window does not draw in one), as AppDialogCard is for a dialog. With
 * [bottom], its buttons stand at the bottom of the card: in a card of a bounded height the content is cut under them, with the fade
 * of [AppSheet] — the look of a sheet whose content scrolls.
 */
@Composable
fun AppSheetCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = AppSheetDefaults.ContentPadding,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val direction = LocalLayoutDirection.current
    Column(
        modifier = modifier
            .widthIn(max = AppSheetDefaults.MaxWidth)
            .fillMaxWidth()
            .clip(SheetShape)
            .background(colors.surfaceContainer),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
        if (bottom == null) {
            Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
        } else {
            var shownHeight by remember { mutableIntStateOf(0) }
            var fullHeight by remember { mutableIntStateOf(0) }
            PinnedBottom(
                fadeColor = colors.surfaceContainer,
                fadeWhen = { fullHeight > shownHeight },
                content = {
                    Box(Modifier.fillMaxWidth().clipToBounds().onSizeChanged { shownHeight = it.height }) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                .onSizeChanged { fullHeight = it.height }
                                .padding(
                                    start = contentPadding.calculateStartPadding(direction),
                                    top = contentPadding.calculateTopPadding(),
                                    end = contentPadding.calculateEndPadding(direction),
                                ),
                            content = content,
                        )
                    }
                },
                bottom = {
                    Column(
                        Modifier.fillMaxWidth().padding(
                            start = contentPadding.calculateStartPadding(direction),
                            end = contentPadding.calculateEndPadding(direction),
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                        content = bottom,
                    )
                },
            )
        }
    }
}

/**
 * The buttons at the bottom of a sheet (components.html, «Лист»): the [main] one filled, as wide as the sheet — it says the outcome
 * («Сохранить 35 мин»); under it a [second] answer as an outline of 56 («Закончить сейчас · 3 ч 12 мин»), and last the refusal said
 * quietly ([quiet]), never a red button. A [main] that cannot be pressed yet is dimmed with its [mainReason] above it; one that goes
 * dim and bright under a stepper keeps the place of its reason while there is none ([mainReasonReserve]). In a face that has just
 * taken the place of another in its frame the [main] does not answer for the time of a double tap ([AppSheet]). 18 above them, 6
 * between them. In a window no higher than 360 dp the buttons of 56 are 48, as those of the bottom zone (3.36.1, п. 5): the pinned bottom
 * leaves the sheet's number room above it.
 */
@Composable
fun AppSheetButtons(
    main: String,
    onMain: () -> Unit,
    modifier: Modifier = Modifier,
    mainIcon: ImageVector? = null,
    mainEnabled: Boolean = true,
    mainReason: String? = null,
    mainReasonReserve: String? = null,
    second: String? = null,
    onSecond: () -> Unit = {},
    quiet: String? = null,
    onQuiet: () -> Unit = {},
) {
    val compact = currentDockMetrics().compact
    // a face that has just come in the place of another does not take the second tap of the finger that pressed the one before
    val arrival = LocalFaceArrival.current
    Column(modifier.fillMaxWidth().padding(top = ButtonsTop), verticalArrangement = Arrangement.spacedBy(ButtonsGap)) {
        AppButton(
            main, { if (!arrival.holds) onMain() }, Modifier.fillMaxWidth(), style = AppButtonStyle.Main, icon = mainIcon, enabled = mainEnabled,
            reason = mainReason, compact = compact, reasonReserve = mainReasonReserve,
        )
        if (second != null) AppButton(second, onSecond, Modifier.fillMaxWidth(), style = AppButtonStyle.Outline, compact = compact)
        if (quiet != null) AppButton(quiet, onQuiet, Modifier.fillMaxWidth(), style = AppButtonStyle.Quiet)
    }
}
