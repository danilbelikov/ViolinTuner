package com.violinjourney.app.feature.repertoire.stand

import com.violinjourney.app.feature.repertoire.scale.ScaleNotation
import com.violinjourney.app.feature.repertoire.scale.NotationSizes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.piece.TakeProblem
import com.violinjourney.app.feature.repertoire.piece.TakeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TopField = 64.dp
private val BottomField = 112.dp
private val PanelButton = 48.dp
private val PanelShift = 12.dp
private val SheetCorner = 6.dp
private val PortraitSheetSide = 24.dp
private val PortraitSheetTop = 72.dp
private val LandscapeSheetSide = 96.dp
private val ScrollIndicatorWidth = 3.dp
private val PillHeight = 56.dp
private val PillButton = 44.dp
private val PillGlyph = 16.dp
private val RecDot = 10.dp
private val CapsuleHeight = 28.dp
private val CapsuleDot = 8.dp
private val EdgeFlashWidth = 56.dp
private val BounceDistance = 8.dp
private val HintInset = 8.dp
private const val PILL_ALPHA = 0.9f
private const val CAPSULE_ALPHA = 0.7f
private const val ICON_DISC_ALPHA = 0.7f
private const val TABULAR_FIGURES = "tnum"
private const val MS_PER_SECOND = 1_000L

/** Paper of a sheet whose photo is loading or lost: the proportions of a music page. */
private const val PAPER_RATIO = 3f / 4f

/**
 * The music stand (spec 3.15): one sheet, as large as the screen lets it be, on a background
 * darker than anywhere else in the app — the sheet is the only bright thing here. Stateless;
 * [take] is the piece's recording, shared with its screen.
 */
@Composable
fun StandScreen(
    state: StandState,
    take: TakeState,
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
        LaunchedEffect(pagerState.settledPage) {
            zoom.reset()
            onIntent(StandIntent.PageSettled(pagerState.settledPage))
        }

        Sheets(state.pages, pagerState, zoom, landscape, state.showHint, onIntent)
        Panel(
            visible = state.panelVisible,
            counter = stringResource(R.string.stand_counter, pagerState.currentPage + 1, state.pages.size),
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
    showHint: Boolean,
    onIntent: (StandIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val primary = MaterialTheme.colorScheme.primary
    val bounce = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val flashSide = remember { mutableStateOf(StandZone.NEXT) }
    val hint = remember { Animatable(0f) }
    val pageCount by rememberUpdatedState(pages.size)
    // A tap in the middle waits to see whether it is the first half of a double tap.
    val pendingPanelTap = remember { mutableStateOf<Job?>(null) }

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
                flashSide.value = zone
                launch {
                    flash.snapTo(StandMotion.EDGE_FLASH_ALPHA)
                    flash.animateTo(0f, tween(StandMotion.EDGE_FLASH_MS))
                }
                pagerState.animateScrollToPage(target, animationSpec = tween(StandMotion.PAGE_TURN_MS, easing = FastOutSlowInEasing))
            }
        }
    }

    if (showHint) {
        LaunchedEffect(Unit) {
            hint.animateTo(StandMotion.HINT_ALPHA, tween(StandMotion.HINT_FADE_MS))
            delay(StandMotion.HINT_HOLD_MS)
            hint.animateTo(0f, tween(StandMotion.HINT_FADE_MS))
            onIntent(StandIntent.HintShown)
        }
    }

    val previousLabel = stringResource(R.string.stand_previous_page)
    val nextLabel = stringResource(R.string.stand_next_page)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // a zoomed sheet is larger than the stand and must not spill under the system bars
            .clipToBounds()
            // Thirds of the screen mean nothing to TalkBack: the same two actions, by name.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(previousLabel) { turn(StandZone.PREVIOUS); true },
                    CustomAccessibilityAction(nextLabel) { turn(StandZone.NEXT); true },
                )
            }
            .standGestures(zoom) { tap, area ->
                when (val zone = StandMath.zoneOf(tap.x, area.width.toFloat(), zoom.zoomed)) {
                    StandZone.PREVIOUS, StandZone.NEXT -> turn(zone)
                    StandZone.PANEL -> {
                        val pending = pendingPanelTap.value
                        if (pending?.isActive == true) {
                            pending.cancel()
                            pendingPanelTap.value = null
                            scope.launch { zoom.toggle(tap, area) }
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
                val outline = hint.value
                if (outline > 0f) {
                    val inset = HintInset.toPx()
                    val third = size.width / 3f
                    val stroke = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
                    val zone = Size(third - inset * 2, size.height - inset * 2)
                    val corner = CornerRadius(12.dp.toPx())
                    val tint = primary.copy(alpha = outline)
                    drawRoundRect(tint, Offset(inset, inset), zone, corner, stroke)
                    drawRoundRect(tint, Offset(size.width - third + inset, inset), zone, corner, stroke)
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
                description = stringResource(R.string.stand_page_description, index + 1, pages.size),
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
            Box(
                sheetModifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SheetCorner))
                    .background(paper)
                    .padding(top = 14.dp, bottom = 18.dp),
            ) { ScaleNotation(scale, NotationSizes.Stand, ViolinTheme.exerciseColors.inkOnPaper, name = description) }
        } else if (image != null) {
            Image(bitmap = image, contentDescription = description, contentScale = ContentScale.Fit, modifier = shaped)
        } else {
            Box(shaped.semantics { contentDescription = description })
        }
    }
    if (landscape) {
        // By width, read downwards: a sheet fitted into a lying screen would be a postage stamp.
        val scroll = rememberScrollState()
        val thumb = MaterialTheme.colorScheme.outline
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    if (scroll.maxValue > 0) {
                        val width = ScrollIndicatorWidth.toPx()
                        val length = size.height * size.height / (size.height + scroll.maxValue)
                        val top = (size.height - length) * scroll.value / scroll.maxValue
                        drawRoundRect(thumb, Offset(size.width - width * 2, top), Size(width, length), CornerRadius(width / 2))
                    }
                }
                .padding(horizontal = side),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(vertical = 16.dp)
                    .then(zoomLayer),
            ) { sheet(Modifier.fillMaxWidth()) }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = side, end = side, top = PortraitSheetTop, bottom = PortraitSheetTop)
                .then(zoomLayer),
            contentAlignment = Alignment.Center,
        ) { sheet(Modifier) }
    }
}

/** Two gradient fields instead of bars: nothing opaque lies over the notes. With the panel away, a small capsule keeps the count — and a running take — in sight. */
@Composable
private fun Panel(
    visible: Boolean,
    counter: String,
    take: TakeState,
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
                PanelIconButton(AppIcons.Back, stringResource(R.string.session_back)) { onIntent(StandIntent.BackClicked) }
                Text(
                    text = counter,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                    modifier = Modifier.weight(1f),
                )
                if (canDelete) {
                    PanelIconButton(AppIcons.Trash, stringResource(R.string.stand_delete_page)) { onIntent(StandIntent.DeleteClicked) }
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
                    .height(BottomField)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
                    .padding(start = 20.dp, end = 20.dp, bottom = if (landscape) 16.dp else 20.dp),
                contentAlignment = if (landscape) Alignment.BottomEnd else Alignment.BottomCenter,
            ) {
                RecordPill(take) {
                    onIntent(StandIntent.Touched)
                    onRecordClick()
                }
            }
        }
        AnimatedVisibility(
            visible = !visible,
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

/** «● Записать дубль» at rest; «dot · timer · stop» while a take runs. Blind like the row on the piece screen: nothing about the notes. */
@Composable
private fun RecordPill(take: TakeState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val off = ViolinTheme.zoneColors.off
    val label = stringResource(if (take.recording) R.string.record_stop else R.string.take_record)
    Row(
        modifier = Modifier
            .height(PillHeight)
            .clip(RoundedCornerShape(PillHeight / 2))
            .background(colors.surfaceContainerHigh.copy(alpha = PILL_ALPHA))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(start = if (take.recording) 18.dp else 6.dp, end = if (take.recording) 6.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (take.recording) {
            PulsingDot(RecDot)
            val time = Formats.timer(take.elapsedSeconds * MS_PER_SECOND)
            val description = stringResource(R.string.stand_recording_description, time)
            Text(
                text = time,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            )
            take.problem?.let { problem ->
                Text(
                    text = stringResource(if (problem == TakeProblem.TOO_NOISY) R.string.live_too_noisy else R.string.live_mic_unavailable),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                )
            }
            PillGlyphButton(container = off, glyph = ViolinTheme.zoneColors.onOff, glyphCorner = 3.dp)
        } else {
            PillGlyphButton(container = colors.primary, glyph = colors.onPrimary, glyphCorner = PillGlyph / 2)
            Text(label, color = colors.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun PillGlyphButton(container: Color, glyph: Color, glyphCorner: Dp) {
    Box(
        modifier = Modifier
            .size(PillButton)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(PillGlyph)
                .background(glyph, RoundedCornerShape(glyphCorner)),
        )
    }
}

@Composable
private fun Capsule(counter: String, take: TakeState) {
    val colors = ViolinTheme.repertoireColors
    Row(
        modifier = Modifier
            .height(CapsuleHeight)
            .background(colors.standBackground.copy(alpha = CAPSULE_ALPHA), RoundedCornerShape(CapsuleHeight / 2))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (take.recording) PulsingDot(CapsuleDot)
        Text(
            text = if (take.recording) "${Formats.timer(take.elapsedSeconds * MS_PER_SECOND)} · $counter" else counter,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

@Composable
private fun PulsingDot(size: Dp) {
    val pulse by rememberInfiniteTransition(label = "standRec").animateFloat(
        initialValue = 1f,
        targetValue = StandMotion.REC_PULSE_MIN_ALPHA,
        animationSpec = InfiniteRepeatableSpec(tween(StandMotion.REC_PULSE_MS / 2), RepeatMode.Reverse),
        label = "standRecAlpha",
    )
    val color = ViolinTheme.zoneColors.off
    Box(
        Modifier
            .size(size)
            .graphicsLayer { alpha = pulse }
            .background(color, CircleShape),
    )
}

@Composable
private fun DeletePageDialog(onIntent: (StandIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { onIntent(StandIntent.DeleteDismissed) },
        title = { Text(stringResource(R.string.stand_delete_title)) },
        text = { Text(stringResource(R.string.stand_delete_text)) },
        confirmButton = {
            TextButton(onClick = { onIntent(StandIntent.DeleteConfirmed) }) {
                Text(stringResource(R.string.piece_delete_confirm), color = ViolinTheme.repertoireColors.formError)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(StandIntent.DeleteDismissed) }) { Text(stringResource(R.string.dialog_cancel)) } },
        containerColor = colors.surfaceContainerHigh,
    )
}

private val PanelButtonSpace = 48.dp
