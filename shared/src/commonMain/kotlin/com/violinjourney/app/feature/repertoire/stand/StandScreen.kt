package com.violinjourney.app.feature.repertoire.stand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.components.RecordDot
import com.violinjourney.app.feature.repertoire.components.RecordingBar
import com.violinjourney.app.feature.repertoire.piece.TakeProblem
import com.violinjourney.app.feature.repertoire.scale.NotationSizes
import com.violinjourney.app.feature.repertoire.scale.ScaleNotation
import com.violinjourney.app.feature.repertoire.scale.scaleTitle
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.piece_delete_confirm
import com.violinjourney.app.shared.resources.session_back
import com.violinjourney.app.shared.resources.stand_counter
import com.violinjourney.app.shared.resources.stand_delete_page
import com.violinjourney.app.shared.resources.stand_delete_text
import com.violinjourney.app.shared.resources.stand_delete_title
import com.violinjourney.app.shared.resources.stand_hint_card
import com.violinjourney.app.shared.resources.stand_next_page
import com.violinjourney.app.shared.resources.stand_page_description
import com.violinjourney.app.shared.resources.stand_previous_page
import com.violinjourney.app.shared.resources.take_record
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val TopField = 64.dp
private val BottomField = 112.dp
private val PanelButton = 48.dp
private val PanelShift = 12.dp
private val SheetCorner = 6.dp
private val PortraitSheetSide = 24.dp
private val PortraitSheetTop = 72.dp
private val LandscapeSheetSide = 96.dp
private val ScrollIndicatorWidth = 3.dp
private val LandscapeBar = 320.dp
private val BarPillCorner = 32.dp
private val BarPillStart = 16.dp
private val BarPillEnd = 4.dp
private val CapsuleHeight = 32.dp
private val CapsuleDot = 9.dp
private val EdgeFlashWidth = 56.dp
private val BounceDistance = 8.dp
private val HintInset = 8.dp
private val HintCorner = 14.dp
private val HintStroke = 2.dp
private val HintDash = 6.dp
private val HintDashGap = 5.dp
private val HintArrow = 36.dp
private val HintCardSide = 20.dp
private val HintCardBottom = 40.dp
private const val CAPSULE_ALPHA = 0.82f
private const val ICON_DISC_ALPHA = 0.7f
private const val TABULAR_FIGURES = "tnum"
private const val MS_PER_SECOND = 1_000L

/** Paper of a sheet whose photo is loading or lost: the proportions of a music page. */
private const val PAPER_RATIO = 3f / 4f

/**
 * The music stand (spec 3.15, 3.36.4): one sheet, as large as the screen lets it be, on a background
 * darker than anywhere else in the app — the sheet is the only bright thing here. The panel records with the main button of the
 * piece screen; the first visit shows its hint instead of the panel. Stateless; [take] is the piece's recording, shared with its
 * screen.
 */
@Composable
fun StandScreen(
    state: StandState,
    take: StandTake,
    onIntent: (StandIntent) -> Unit,
    onRecordClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ViolinTheme.repertoireColors
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.standBackground),
    ) {
        if (state.loading || state.pages.isEmpty()) return@BoxWithConstraints
        val landscape = maxWidth > maxHeight
        val pagerState = rememberPagerState(initialPage = state.initialPage) { state.pages.size }
        val zoom = remember { StandZoom() }
        // A deleted page leaves its index to the next one: another sheet at the same index is a new page, and starts at 1×.
        val settledPageId = state.pages.getOrNull(pagerState.settledPage)?.pageId
        LaunchedEffect(pagerState.settledPage, settledPageId) {
            zoom.reset()
            onIntent(StandIntent.PageSettled(pagerState.settledPage))
        }

        Sheets(state.pages, pagerState, zoom, landscape, hint = state.showHint, onIntent)
        // over the sheet, and deaf: a tap on an edge turns the page under it, one in the middle calls the panel
        FirstVisitHint(state.showHint)
        Panel(
            visible = state.panelVisible,
            // the first visit is the hint's alone: no panel, and no capsule of the count over it (repertoire.html 6, «Первый вход») —
            // but a running take stays in sight: the capsule «● 1:12 · 1 / 2» is its only sign while the panel is away
            capsule = !state.showHint || take.recording,
            counter = stringResource(Res.string.stand_counter, pagerState.currentPage + 1, state.pages.size),
            take = take,
            landscape = landscape,
            // a drawn scale is not a photo: there is nothing to delete (handoff 24g)
            canDelete = state.pages.getOrNull(pagerState.currentPage)?.drawn != true,
            onIntent = onIntent,
            onRecordClick = onRecordClick,
        )
    }
    if (state.deleteDialog) DeletePageDialog(onIntent)
}

/** The pager with everything that answers a touch of the sheet: turning, zooming, the edge flash, the bounce, the hint. */
@Composable
private fun Sheets(
    pages: List<StandPage>,
    pagerState: PagerState,
    zoom: StandZoom,
    landscape: Boolean,
    hint: Boolean,
    onIntent: (StandIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val primary = MaterialTheme.colorScheme.primary
    val bounce = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val flashSide = remember { mutableStateOf(StandZone.NEXT) }
    val pageCount by rememberUpdatedState(pages.size)
    val currentPages by rememberUpdatedState(pages)
    // A tap in the middle waits to see whether it is the first half of a double tap.
    val pendingPanelTap = remember { mutableStateOf<Job?>(null) }
    // What the stand leaves around the scaled layer of the sheet (see Sheet): the zoom is centred and held within it.
    val margins = with(density) {
        if (landscape) {
            IntSize(LandscapeSheetSide.roundToPx() * 2, 0)
        } else {
            IntSize(PortraitSheetSide.roundToPx() * 2, PortraitSheetTop.roundToPx() * 2)
        }
    }
    // Plain holders: read and written by the handlers and the effect below, never drawn.
    val tapTargetId = remember { mutableStateOf<Long?>(null) }
    val lastSettledId = remember { mutableStateOf<Long?>(null) }

    fun flashEdge(zone: StandZone) {
        flashSide.value = zone
        scope.launch {
            flash.snapTo(StandMotion.EDGE_FLASH_ALPHA)
            flash.animateTo(0f, tween(StandMotion.EDGE_FLASH_MS))
        }
    }

    fun turn(zone: StandZone) {
        onIntent(StandIntent.Touched)
        val target = StandMath.target(pagerState.currentPage, pageCount, zone)
        scope.launch {
            if (target == null) {
                // Nowhere to go: the sheet gives a little and comes back.
                val distance = with(density) { BounceDistance.toPx() } * if (zone == StandZone.NEXT) -1 else 1
                bounce.animateTo(distance, tween(StandMotion.BOUNCE_MS / 2))
                bounce.animateTo(0f, tween(StandMotion.BOUNCE_MS / 2))
            } else {
                // the tap lights its edge as it turns; the rest it comes to must not light it again
                tapTargetId.value = currentPages.getOrNull(target)?.pageId
                flashEdge(zone)
                pagerState.animateScrollToPage(target, animationSpec = tween(StandMotion.PAGE_TURN_MS, easing = FastOutSlowInEasing))
            }
        }
    }

    // A swipe lights the edge it turned through too (spec 3.15), once the sheet has come to rest: later than a tap's
    // flash, which comes with the tap. Compared by page, not by index, so a deleted page lights nothing.
    val settledId = pages.getOrNull(pagerState.settledPage)?.pageId
    LaunchedEffect(settledId) {
        val byTap = settledId != null && settledId == tapTargetId.value
        val edge = StandMath.edgeOfSettle(currentPages.map { it.pageId }, lastSettledId.value, settledId, byTap)
        tapTargetId.value = null
        lastSettledId.value = settledId
        if (edge != null) flashEdge(edge)
    }

    val previousLabel = stringResource(Res.string.stand_previous_page)
    val nextLabel = stringResource(Res.string.stand_next_page)
    val hintShown by rememberUpdatedState(hint)
    val touched by rememberUpdatedState { onIntent(StandIntent.Touched) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // a zoomed sheet is larger than the stand and must not spill under the system bars
            .clipToBounds()
            // the hint of the first visit goes at the first touch of any kind (spec 3.36.4) — a swipe, a pinch, a double tap too,
            // not only the taps the gestures below answer; seen before them, and nothing is taken from them
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (hintShown) touched()
                }
            }
            // Thirds of the screen mean nothing to TalkBack: the same two actions, by name.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(previousLabel) { turn(StandZone.PREVIOUS); true },
                    CustomAccessibilityAction(nextLabel) { turn(StandZone.NEXT); true },
                )
            }
            .standGestures(zoom, margins) { tap, area ->
                when (val zone = StandMath.zoneOf(tap.x, area.width.toFloat(), zoom.zoomed)) {
                    StandZone.PREVIOUS, StandZone.NEXT -> turn(zone)
                    StandZone.PANEL -> {
                        val pending = pendingPanelTap.value
                        if (pending?.isActive == true) {
                            pending.cancel()
                            pendingPanelTap.value = null
                            scope.launch { zoom.toggle(tap, area, margins) }
                        } else {
                            pendingPanelTap.value = scope.launch {
                                delay(doubleTapTimeout)
                                onIntent(StandIntent.PanelToggled)
                            }
                        }
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val strength = flash.value
                if (strength > 0f) {
                    val width = EdgeFlashWidth.toPx()
                    val lit = primary.copy(alpha = strength)
                    if (flashSide.value == StandZone.NEXT) {
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, lit), startX = size.width - width, endX = size.width),
                            topLeft = Offset(size.width - width, 0f), size = Size(width, size.height),
                        )
                    } else {
                        drawRect(Brush.horizontalGradient(listOf(lit, Color.Transparent), startX = 0f, endX = width), size = Size(width, size.height))
                    }
                }
            },
    ) {
        val viewportWidthPx = constraints.maxWidth
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoom.zoomed,
            key = { pages.getOrNull(it)?.pageId ?: it },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = bounce.value },
        ) { index ->
            val page = pages.getOrNull(index) ?: return@HorizontalPager
            Sheet(
                page = page,
                description = stringResource(Res.string.stand_page_description, index + 1, pages.size),
                // only the sheet on the stand is zoomed; its neighbours wait at 1×
                zoom = zoom.takeIf { index == pagerState.settledPage },
                landscape = landscape,
                viewportWidthPx = viewportWidthPx,
            )
        }
    }
}

@Composable
private fun Sheet(page: StandPage, description: String, zoom: StandZoom?, landscape: Boolean, viewportWidthPx: Int) {
    val side = if (landscape) LandscapeSheetSide else PortraitSheetSide
    val shownWidthPx = viewportWidthPx - with(LocalDensity.current) { side.roundToPx() } * 2
    // The unzoomed sheet needs no more pixels than it covers; a zoomed one gets the whole stored page.
    val image = rememberStandImage(page.path, if (zoom?.zoomed == true) Int.MAX_VALUE else shownWidthPx)
    val paper = ViolinTheme.repertoireColors.paper
    val zoomLayer = Modifier.graphicsLayer {
        if (zoom != null) {
            scaleX = zoom.scale
            scaleY = zoom.scale
            translationX = zoom.offset.x
            translationY = zoom.offset.y
        }
    }
    val sheet: @Composable (Modifier) -> Unit = { sheetModifier ->
        val ratio = image?.let { it.width.toFloat() / it.height } ?: PAPER_RATIO
        val shaped = sheetModifier
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(SheetCorner))
            .background(paper)
        val scale = page.scale
        if (scale != null) {
            // As tall as its systems, not as a sheet of paper: dark ink on the paper of the stand, a staff space of 10 dp (handoff 24g1).
            // One stop for a reader, like a photo: «Страница 1 из 1», then the notes of the scale by its name.
            Box(
                sheetModifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SheetCorner))
                    .background(paper)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(top = 14.dp, bottom = 18.dp),
            ) { ScaleNotation(scale, NotationSizes.Stand, ViolinTheme.exerciseColors.inkOnPaper, name = scaleTitle(scale.spec)) }
        } else if (image != null) {
            Image(bitmap = image, contentDescription = description, contentScale = ContentScale.Fit, modifier = shaped)
        } else {
            Box(shaped.semantics { contentDescription = description })
        }
    }
    // The zoom scales what is on screen — the layer stands before the scroll, never on the scrolled content — and is
    // centred on the area of gestures, as StandMath counts (the paddings are the margins Sheets passes to the zoom).
    // Each way of holding the phone has a scroll of its own: iOS turns the screen without composing it anew, and the
    // range of a lying sheet must not draw a bar over an upright photo, which does not scroll.
    val thumb = MaterialTheme.colorScheme.outline
    if (landscape) {
        // By width, read downwards: a sheet fitted into a lying screen would be a postage stamp.
        val scroll = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollIndicator(scroll, thumb)
                .padding(horizontal = side),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(zoomLayer)
                    .verticalScroll(scroll)
                    .padding(vertical = 16.dp),
            ) { sheet(Modifier.fillMaxWidth()) }
        }
    } else {
        val scroll = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = PortraitSheetTop)
                .then(if (page.drawn) Modifier.scrollIndicator(scroll, thumb) else Modifier)
                .padding(horizontal = side)
                .then(zoomLayer),
            contentAlignment = Alignment.Center,
        ) {
            // A long scale is taller than the stand (spec 3.22: the sheet is as tall as its systems) and scrolls, as the
            // lying sheet does; a short one wraps its systems and stays in the middle. A photo is fitted, never scrolled.
            if (page.drawn) Box(Modifier.verticalScroll(scroll)) { sheet(Modifier) } else sheet(Modifier)
        }
    }
}

/** A thin bar at the right edge while the sheet is longer than the stand: how far down the reading is. */
private fun Modifier.scrollIndicator(scroll: ScrollState, color: Color): Modifier = drawWithContent {
    drawContent()
    if (scroll.maxValue > 0) {
        val width = ScrollIndicatorWidth.toPx()
        val length = size.height * size.height / (size.height + scroll.maxValue)
        val top = (size.height - length) * scroll.value / scroll.maxValue
        drawRoundRect(color, Offset(size.width - width * 2, top), Size(width, length), CornerRadius(width / 2))
    }
}

/** Two gradient fields instead of bars: nothing opaque lies over the notes. With the panel away, a small capsule keeps the count — and a running take — in sight. */
@Composable
private fun Panel(
    visible: Boolean,
    capsule: Boolean,
    counter: String,
    take: StandTake,
    landscape: Boolean,
    canDelete: Boolean,
    onIntent: (StandIntent) -> Unit,
    onRecordClick: () -> Unit,
) {
    val scrim = ViolinTheme.repertoireColors.standScrim
    val shift = with(LocalDensity.current) { PanelShift.roundToPx() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(StandMotion.PANEL_IN_MS)) + slideInVertically(tween(StandMotion.PANEL_IN_MS)) { -shift },
            exit = fadeOut(tween(StandMotion.PANEL_OUT_MS)) + slideOutVertically(tween(StandMotion.PANEL_OUT_MS)) { -shift },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopField)
                    .background(Brush.verticalGradient(listOf(scrim, Color.Transparent)))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelIconButton(AppIcons.Back, stringResource(Res.string.session_back)) { onIntent(StandIntent.BackClicked) }
                Text(
                    text = counter,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
                    modifier = Modifier.weight(1f),
                )
                if (canDelete) {
                    PanelIconButton(AppIcons.Trash, stringResource(Res.string.stand_delete_page)) { onIntent(StandIntent.DeleteClicked) }
                } else {
                    Spacer(Modifier.size(PanelButtonSpace))
                }
            }
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(StandMotion.PANEL_IN_MS)) + slideInVertically(tween(StandMotion.PANEL_IN_MS)) { shift },
            exit = fadeOut(tween(StandMotion.PANEL_OUT_MS)) + slideOutVertically(tween(StandMotion.PANEL_OUT_MS)) { shift },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BottomField)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
                    .padding(start = 20.dp, end = 20.dp, bottom = if (landscape) 16.dp else 20.dp),
                contentAlignment = if (landscape) Alignment.BottomEnd else Alignment.BottomCenter,
            ) {
                val record = {
                    onIntent(StandIntent.Touched)
                    onRecordClick()
                }
                if (take.recording) {
                    // the bar of the piece screen, without the levels and the backing's progress (spec 3.36.4), on the ground of the
                    // bottom zone — an opaque pill: lying, the sheet runs under it, and the scrim alone left «запись» at 1.2 : 1
                    RecordingBar(
                        elapsedSeconds = take.elapsedSeconds,
                        noisy = take.problem == TakeProblem.TOO_NOISY,
                        onStop = record,
                        modifier = (if (landscape) Modifier.width(LandscapeBar) else Modifier)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(BarPillCorner))
                            .padding(start = BarPillStart, end = BarPillEnd, top = BarPillEnd, bottom = BarPillEnd),
                    )
                } else {
                    // the main button of the piece screen, where the hand already reaches (spec 3.36.4): lying, by its words; without
                    // the microphone it does not sleep — a press asks the system, or opens the settings once it asks no more
                    AppButton(
                        text = stringResource(Res.string.take_record),
                        onClick = record,
                        modifier = if (landscape) Modifier else Modifier.fillMaxWidth(),
                        leading = { RecordDot() },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = !visible && capsule,
            enter = fadeIn(tween(StandMotion.PANEL_IN_MS)),
            exit = fadeOut(tween(StandMotion.PANEL_IN_MS)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) { Capsule(counter, take) }
    }
}

/** A lone icon over the notes: on a dark disc of its own, or a zoomed bright sheet would swallow the outline (handoff `sizes`). */
@Composable
private fun PanelIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(PanelButton)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = ICON_DISC_ALPHA))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface) }
}

/** «● 1:12 · 2 / 4» with the panel away (5.29 R4): the take in sight, the notes not covered; without a take — «2 / 4». */
@Composable
private fun Capsule(counter: String, take: StandTake) {
    val colors = ViolinTheme.repertoireColors
    Row(
        modifier = Modifier
            .height(CapsuleHeight)
            .background(colors.standBackground.copy(alpha = CAPSULE_ALPHA), RoundedCornerShape(CapsuleHeight / 2))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (take.recording) Box(Modifier.size(CapsuleDot).background(ViolinTheme.recording, CircleShape))
        Text(
            text = if (take.recording) "${Formats.timer(take.elapsedSeconds * MS_PER_SECOND)} · $counter" else counter,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/**
 * The hint of the very first visit (spec 3.36.4): the left and the right third of the sheet — the zones that turn the pages — as
 * dashed outlines in the accent with their arrows, and a card «Тап по краю листа — следующая страница. Середина — панель.» at the
 * bottom. It comes and goes over 300 ms; how long it stays is the view model's (4 s, or until the first touch). It takes no touch:
 * a tap on an edge turns the page under it, one in the middle calls the panel. TalkBack reads the words of the card.
 */
@Composable
private fun FirstVisitHint(shown: Boolean) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = shown
    val accent = MaterialTheme.colorScheme.primary
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(StandMotion.HINT_FADE_MS)),
        exit = fadeOut(tween(StandMotion.HINT_FADE_MS)),
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val inset = HintInset.toPx()
                        val third = size.width / 3f
                        val zone = Size(third - inset * 2, size.height - inset * 2)
                        val corner = CornerRadius(HintCorner.toPx())
                        val stroke = Stroke(HintStroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(HintDash.toPx(), HintDashGap.toPx())))
                        listOf(inset, size.width - third + inset).forEach { x ->
                            drawRoundRect(accent.copy(alpha = StandMotion.HINT_FILL_ALPHA), Offset(x, inset), zone, corner)
                            drawRoundRect(accent.copy(alpha = StandMotion.HINT_ALPHA), Offset(x, inset), zone, corner, stroke)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { AppIcon(AppIcons.ChevronLeft, contentDescription = null, tint = accent, size = HintArrow) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = accent, size = HintArrow) }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = HintCardSide, end = HintCardSide, bottom = HintCardBottom)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, AppShapes.M)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(AppIcons.Hand, contentDescription = null, tint = accent)
                Text(
                    stringResource(Res.string.stand_hint_card),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun DeletePageDialog(onIntent: (StandIntent) -> Unit) {
    DeleteDialog(
        title = stringResource(Res.string.stand_delete_title),
        text = stringResource(Res.string.stand_delete_text),
        confirm = stringResource(Res.string.piece_delete_confirm),
        onConfirm = { onIntent(StandIntent.DeleteConfirmed) },
        onDismiss = { onIntent(StandIntent.DeleteDismissed) },
    )
}

private val PanelButtonSpace = 48.dp
