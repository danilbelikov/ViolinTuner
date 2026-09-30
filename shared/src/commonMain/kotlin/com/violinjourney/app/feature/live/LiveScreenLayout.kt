package com.violinjourney.app.feature.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.session.RecordingRibbon
import com.violinjourney.app.core.time.monotonicNanos
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.feature.live.components.CentsScale
import com.violinjourney.app.feature.live.components.GlowRing
import com.violinjourney.app.feature.live.components.LiveDimens
import com.violinjourney.app.feature.live.components.LiveMotion
import com.violinjourney.app.feature.live.components.LocalLivePlain
import com.violinjourney.app.feature.live.components.MicPermissionPrompt
import com.violinjourney.app.feature.live.components.ModeSwitcher
import com.violinjourney.app.feature.live.components.NoteLabel
import com.violinjourney.app.feature.live.components.RecordKeyLight
import com.violinjourney.app.feature.live.components.StatusFit
import com.violinjourney.app.feature.live.components.StatusLineRow
import com.violinjourney.app.feature.live.components.StatusRow
import com.violinjourney.app.feature.live.components.StringRow
import com.violinjourney.app.feature.live.components.SwitcherFit
import com.violinjourney.app.feature.live.components.ZoneEllipse
import com.violinjourney.app.feature.live.components.rememberRingGlow
import com.violinjourney.app.feature.live.components.rememberStatusStep
import com.violinjourney.app.feature.live.components.rememberSwitcherPlan
import com.violinjourney.app.feature.live.components.zoneBackground
import com.violinjourney.app.feature.live.venue.VenueLook
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * What a platform draws on Live around the shared layout: the place behind it, the cards and the key under it, the gear, the
 * strip of a recording, the light it lends the tab bar and the sheets over it. Each gets the width or the light the layout has
 * worked out, so the dimming and the placing stay here, in one place for Android and iOS. Empty slots leave their room empty.
 */
@Immutable
class LiveSlots(
    /** The picture behind Live (spec 3.27); null — the plain dark field with the gradient of the zone. */
    val backdrop: (@Composable (LiveBackdrop) -> Unit)? = null,
    /** «Что играю» left of the key (spec 3.28, 3.36.6), [Dp] wide — both cards alike; the light of the room, read while drawing. */
    val bookmark: @Composable (width: Dp, light: () -> Float) -> Unit = { _, _ -> },
    /** The practice tag right of the key (spec 3.12, 3.36.6), as wide as «Что играю»; it dims with the light. */
    val tag: @Composable (width: Dp, light: () -> Float) -> Unit = { _, _ -> },
    /** The record key (spec 3.9, 3.36.6); [alpha] — its dimming, read while drawing. */
    val recordKey: @Composable (recording: Boolean, enabled: Boolean, alpha: () -> Float) -> Unit = { _, _, _ -> },
    /** The gear to «Настройки» (spec 3.8). */
    val gear: @Composable (enabled: Boolean, modifier: Modifier) -> Unit = { _, _ -> },
    /** The strip above the keys while a take is recorded (spec 3.9); its notes are read while drawing. */
    val recordingStrip: @Composable (recording: RecordingState, ribbon: () -> RecordingRibbon, modifier: Modifier) -> Unit = { _, _, _ -> },
    /**
     * The light of the room lent to the tab bar (spec 3.36.6, behind [LiveSwitches.DIM_TAB_BAR]): the same light the controls
     * dim with, in any shape of Live — whether a bar is there to dim is for the root to say, which shows it (upright) or not
     * (lying down), not for the shape of Live's own place: upright in a split screen Live may lay itself out lying down over a bar.
     */
    val light: @Composable (chrome: () -> Float) -> Unit = {},
    /** Over everything: the sheets of blocks. */
    val overlay: @Composable (landscape: Boolean) -> Unit = {},
)

/**
 * What the picture behind Live follows: the light, the glow of the ring and where the ring is — without the permission, where the card
 * «нет разрешения» is, its width in the place of the diameter (spec 3.36.6, 5.20: the veil from its middle).
 */
class LiveBackdrop(
    val landscape: Boolean,
    val darkness: () -> Float,
    val glow: () -> Float,
    val zoneColor: () -> Color,
    val zoneScale: () -> Float,
    val ringCenter: () -> Offset,
    val ringDiameter: () -> Float,
)

/**
 * Live screen (spec 3.1, handoff variant 8; in the room and in the halls — spec 3.27, handoff venue).
 * Stateless. Wider than tall gets the landscape layout (ring on the left, controls on the right),
 * anything else the portrait column.
 *
 * Behind it is the place the player is in: the room, or a hall seen from its stage — chosen on the
 * journey, only named here. While the violin
 * is silent the light is on; as soon as a note is held it goes down, the picture sinks into the dusk
 * and freezes, the colour of the zone lights it, and the controls one does not touch fade with it.
 * Without [LiveSlots.backdrop] it is the Live of before, on its plain dark field.
 */
@Composable
fun LiveScreenLayout(
    state: LiveState,
    onIntent: (LiveIntent) -> Unit,
    slots: LiveSlots,
    modifier: Modifier = Modifier,
    /** What moves with every frame of sound ([LiveGauge]): read only while drawing and in effects, never here. */
    gauge: () -> LiveGauge = NoGauge,
    /** System animations are switched off: the ring stands still and changes in steps (spec 3.14). */
    reduceMotion: Boolean = false,
    /** The pale note of the locked string in the silent ring of «Настройка» (spec 3.36.6) — behind its switch, off by default. */
    stringSilhouette: Boolean = LiveSwitches.STRING_SILHOUETTE,
) {
    val showVenue = slots.backdrop != null
    val zoneColors = LiveTheme.zoneColors
    val sounding = state.signal as? LiveSignal.Sounding

    // Gradient, status, ring fill and marker halo always share this one color (spec 3.4). It is read while drawing:
    // a change of zone fades it without composing the screen on every frame of the fade.
    val zoneColorState = animateColorAsState(
        targetValue = zoneColors.colorFor(sounding?.zone),
        animationSpec = tween(state.zoneCrossfadeMs),
        label = "zoneColor",
    )
    val zoneColor = remember(zoneColorState) { { zoneColorState.value } }
    // the ring and the light of the zone on the picture are lit by one number
    val currentGauge by rememberUpdatedState(gauge)
    val glowStep by rememberUpdatedState(state.glowStep)
    val glow = rememberRingGlow({ if (reduceMotion) glowStep else currentGauge().glowTarget }, reduceMotion)
    val darkness = rememberHouseLights(
        down = HouseLights.playing(state),
        quietSinceNanos = state.quietSinceNanos,
        reduceMotion = reduceMotion,
        enabled = showVenue,
    )
    val chrome = remember(darkness) { { VenueLook.chromeAlpha(darkness.value) } }
    val readGauge = remember { { currentGauge() } }

    // where the ring stands, for the veil and the light of the zone — or the card «нет разрешения», which takes its place
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var ringCenter by remember { mutableStateOf(Offset.Unspecified) }
    val ringDiameter = remember { mutableFloatStateOf(0f) }
    val ringModifier = remember {
        Modifier.onGloballyPositioned {
            ringCenter = it.centerIn(rootPosition)
            ringDiameter.floatValue = it.size.width.toFloat()
        }
    }
    val zoneScale by rememberUpdatedState(VenueLook.zoneScale(sounding?.zone))

    BoxWithConstraints(modifier = modifier.fillMaxSize().liveFrameRate()) {
        val landscape = LiveLayoutMath.isLandscape(maxWidth.value, maxHeight.value)
        // made once per shape: the picture behind reads everything through it and is not composed again with the words
        val look = remember(landscape) {
            LiveBackdrop(
                landscape = landscape,
                darkness = { darkness.value },
                glow = { glow.value },
                zoneColor = zoneColor,
                zoneScale = { zoneScale },
                ringCenter = { ringCenter },
                ringDiameter = { ringDiameter.floatValue },
            )
        }
        // the right column of landscape: tighter in a low window, in both modes alike (spec 5.29 R6)
        val column = LiveLayoutMath.landscapeColumn(maxHeight.value)
        slots.light(chrome)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .onGloballyPositioned { rootPosition = it.positionInRoot() }
                .then(
                    if (showVenue) {
                        Modifier
                    } else {
                        Modifier.zoneBackground(
                            gradient = zoneColors.gradientFor(sounding?.zone),
                            crossfadeMs = state.zoneCrossfadeMs,
                            ellipse = if (landscape) ZoneEllipse.LANDSCAPE else ZoneEllipse.PORTRAIT,
                            center = { ringCenter },
                        )
                    },
                ),
        ) {
            slots.backdrop?.invoke(look)
            // without the picture the glass of the controls would melt into the dark field: they stand on a card colour there
            CompositionLocalProvider(LocalLivePlain provides !showVenue) {
                val silhouette = silhouetteOf(state, stringSilhouette)
                if (landscape) {
                    LandscapeLayout(state, readGauge, zoneColor, glow, chrome, showVenue, onIntent, ringModifier, reduceMotion, slots, column, silhouette)
                } else {
                    PortraitLayout(state, readGauge, zoneColor, glow, chrome, showVenue, onIntent, ringModifier, reduceMotion, slots, silhouette)
                }
                slots.overlay(landscape)
            }
        }
    }
}

/**
 * The note of the locked string, pale in the ring of «Настройка» in silence, where to pull to (spec 3.36.6) — when its switch is
 * [on]. Not in auto, not in noise, not without the microphone or the permission, not while a note sounds.
 */
private fun silhouetteOf(state: LiveState, on: Boolean): Note? =
    state.tuning.lockedString?.note?.takeIf { on && state.mode == LiveMode.TUNING && state.signal == LiveSignal.Silence }

/**
 * The light in the hall (spec 3.27, 5.20): 0 — on, 1 — out. It goes out as soon as a note is held or
 * a recording starts, like a switch; it comes back like a dimmer, only after [LiveMotion.LIGHT_ON_AFTER_MS]
 * without either — a bow change, a breath, a rest in the music do not light it. Those seconds count from
 * [quietSinceNanos], the moment the model saw the last note or take end ([HouseLights]): a rotation or a short
 * trip away keeps the room dark to their end, and a return after them opens lit, not dark for another six.
 */
@Composable
private fun rememberHouseLights(down: Boolean, quietSinceNanos: Long?, reduceMotion: Boolean, enabled: Boolean): State<Float> {
    // a screen that opens on a note still sounding, or in the pause after one, starts with the light out
    var out by remember { mutableStateOf(enabled && (down || HouseLights.msStillOut(quietSinceNanos, monotonicNanos()) > 0)) }
    LaunchedEffect(down, quietSinceNanos, enabled) {
        if (!enabled) {
            out = false
        } else if (down) {
            out = true
        } else {
            delay(HouseLights.msStillOut(quietSinceNanos, monotonicNanos()))
            out = false
        }
    }
    val darkness = remember { Animatable(if (out) 1f else 0f) }
    LaunchedEffect(out, reduceMotion) {
        // «убрать анимации»: one plain cross-fade, no gathering speed
        val spec = if (out) {
            tween<Float>(LiveMotion.LIGHT_OFF_MS, easing = if (reduceMotion) LinearEasing else LiveMotion.LightOffEasing)
        } else {
            tween(LiveMotion.LIGHT_ON_MS, easing = if (reduceMotion) LinearEasing else LiveMotion.LightOnEasing)
        }
        darkness.animateTo(if (out) 1f else 0f, spec)
    }
    return darkness.asState()
}

/**
 * The alpha of a control one does not touch while playing: its own, times the light (handoff `chrome.dim`). Equal for an
 * equal [base] and the same [light], so a control whose words have not changed is not composed again with the screen.
 */
internal fun Modifier.chrome(base: Float, light: () -> Float): Modifier = this then ChromeElement(base, light)

/** A layer with the alpha of [chrome], as `graphicsLayer { }` makes it — but equal by what it does, not by the lambda. */
private data class ChromeElement(val base: Float, val light: () -> Float) : ModifierNodeElement<ChromeNode>() {
    override fun create() = ChromeNode(base, light)

    override fun update(node: ChromeNode) = node.set(base, light)
}

private class ChromeNode(base: Float, light: () -> Float) : Modifier.Node(), LayoutModifierNode {
    // the light is read in the layer: its change redraws the layer without measuring or composing anything
    private var layerBlock = layerOf(base, light)

    fun set(base: Float, light: () -> Float) {
        layerBlock = layerOf(base, light)
        invalidatePlacement()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layerBlock) }
    }

    private fun layerOf(base: Float, light: () -> Float): GraphicsLayerScope.() -> Unit = { alpha = base * light() }
}

/** No sound yet: the gauge of a screen that has none (previews, a state from elsewhere). */
internal val NoGauge: () -> LiveGauge = { LiveGauge() }

@Composable
private fun PortraitLayout(
    state: LiveState,
    gauge: () -> LiveGauge,
    zoneColor: () -> Color,
    glow: State<Float>,
    chrome: () -> Float,
    showVenue: Boolean,
    onIntent: (LiveIntent) -> Unit,
    ringModifier: Modifier,
    reduceMotion: Boolean,
    slots: LiveSlots,
    silhouette: Note?,
) {
    val noMic = state.signal == LiveSignal.NoMicPermission
    val sounding = state.signal as? LiveSignal.Sounding
    val chromeAlpha = if (noMic) LiveDimens.CHROME_ALPHA_NO_MIC else 1f
    val recording = state.recording
    val tuning = state.mode == LiveMode.TUNING
    // The scale stands right under the word and the cents (spec 3.36.6), inside the place of the ring: the room kept under the
    // ring grows by it as it unfolds, in the time of the string row, so the ring shrinks as smoothly as when the scale unfolded
    // under the place — and at the same height of the place it is as big as it was.
    val scaleShown = animateFloatAsState(
        targetValue = if (tuning && !noMic) 1f else 0f,
        animationSpec = tween(LiveMotion.STRING_ROW_EXPAND_MS),
        label = "scaleShown",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // the switcher in the middle, the gear in the right corner of its row; the place is not named on Live — its picture says it
        TopRow(
            state = state,
            onIntent = onIntent,
            chrome = chrome,
            chromeAlpha = chromeAlpha,
            slots = slots,
            landscape = false,
            modifier = Modifier.padding(top = LiveDimens.SwitcherTopPadding, start = TopRowSide, end = TopRowSide),
        )
        AnimatedVisibility(
            visible = tuning,
            enter = expandVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                fadeIn(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
            exit = shrinkVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                fadeOut(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
        ) {
            StringRow(
                tuning = state.tuning,
                onStringClick = { onIntent(LiveIntent.StringClicked(it)) },
                light = chrome,
                base = chromeAlpha,
                modifier = Modifier.padding(horizontal = LiveDimens.StringRowSide),
            )
        }
        // without the permission the line is not there, but its place is kept (spec 3.14, 3.36.6)
        StatusLineRow(
            line = state.statusLine,
            tuning = state.tuning,
            modifier = Modifier
                .padding(top = LiveDimens.StatusLineTopPadding)
                .chrome(1f, chrome),
            plate = showVenue,
        )
        if (noMic) {
            // No ring: the card «нет разрешения» in its place, its middle at 60 % of it (spec 3.36.6); in «Настройка» the place is over
            // the scale, which stays at the bottom, quieter, as before R6. The veil of the room follows the card.
            PromptPlace(
                onGrant = { onIntent(LiveIntent.GrantMicClicked) },
                ringModifier = ringModifier,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            ScaleSlot(
                state = state,
                gauge = gauge,
                zoneColor = zoneColor,
                visible = tuning,
                modifier = Modifier.padding(start = LiveDimens.ScreenPadding, end = LiveDimens.ScreenPadding, top = LiveDimens.ScaleTopGap),
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val shown = scaleShown.value
                val ringSize = ringSizeFor(state, landscape = false, maxWidth, maxHeight, LiveLayoutMath.reservedUnderRing(shown).dp)
                // the block spans the screen: the waves may go as far as its side edges
                val waveReach = GlowMath.waveReach(ringSize.value, maxWidth.value / 2)
                // A ring this small means a small screen: the word and the cents shrink with it.
                val compact = ringSize < LiveDimens.CompactStatusBelowRing
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        if (compact) LiveDimens.IndicatorSpacingCompact else LiveDimens.IndicatorSpacing,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Ring(state, gauge, zoneColor, glow, ringSize, waveReach, ringModifier, reduceMotion, silhouette)
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        StatusRow(
                            direction = sounding?.direction,
                            cents = sounding?.displayCents ?: 0,
                            color = zoneColor,
                            visible = sounding != null,
                            step = if (compact) StatusFit.COMPACT else StatusFit.FULL,
                        )
                        if (shown > 0f) {
                            UnderWordScale(state = state, gauge = gauge, zoneColor = zoneColor, shown = { scaleShown.value })
                        }
                    }
                }
            }
        }
        // the strip of a take on its own glass over the bottom row: the ring gives it its height while a take runs (spec 3.36.6)
        RecordingStripSlot(
            slots = slots,
            recording = recording,
            gauge = gauge,
            modifier = Modifier.padding(
                start = LiveDimens.RecordingStripSide,
                end = LiveDimens.RecordingStripSide,
                top = LiveDimens.RecordingStripTopPadding,
            ),
        )
        // «Что играю» · the key · the practice tag (spec 3.36.6; 3.28, 3.9, 3.12) — one phrase, «what I play · record · how long I
        // practise», the key in the middle. The ring gives up no height for them, and all that is touched in silence is down here.
        KeyRow(
            modifier = Modifier.padding(vertical = LiveDimens.KeyRowPadding),
            bookmark = { width -> slots.bookmark(width, chrome) },
            tag = { width -> slots.tag(width, chrome) },
        ) {
            RecordKey(slots, state, chrome)
        }
    }
}

/**
 * The place of the ring without the permission (spec 3.36.6, 5.29 R6): the card «нет разрешения» 26 from the sides of the screen,
 * not wider than 360, its middle at 60 % of the place — below the middle, the button under the thumb — and the whole of it in the
 * place ([LiveLayoutMath.promptTop]); what of the card does not fit, the card itself gives up ([MicPermissionPrompt]).
 */
@Composable
private fun PromptPlace(onGrant: () -> Unit, ringModifier: Modifier, modifier: Modifier) {
    Layout(
        content = { MicPermissionPrompt(onGrantClick = onGrant, modifier = ringModifier) },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val cardWidth = minOf(LiveDimens.PromptMaxWidth.roundToPx(), width - LiveDimens.PromptSide.roundToPx() * 2).coerceAtLeast(0)
        val card = measurables.single().measure(Constraints(minWidth = cardWidth, maxWidth = cardWidth, maxHeight = height))
        layout(width, height) {
            card.place((width - card.width) / 2, LiveLayoutMath.promptTop(height.toFloat(), card.height.toFloat()).roundToInt())
        }
    }
}

/**
 * The card «нет разрешения» lying down (spec 3.36.6): in the middle of the panel of the ring, not wider than 340, the ring's margin
 * round it; what does not fit, the card gives up.
 */
@Composable
private fun PromptPanel(onGrant: () -> Unit, ringModifier: Modifier, modifier: Modifier) {
    Layout(
        content = { MicPermissionPrompt(onGrantClick = onGrant, modifier = ringModifier) },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = LiveDimens.PromptPanelMargin.roundToPx()
        val cardWidth = minOf(LiveDimens.PromptMaxWidthLandscape.roundToPx(), width - margin * 2).coerceAtLeast(0)
        val card = measurables.single().measure(
            Constraints(minWidth = cardWidth, maxWidth = cardWidth, maxHeight = (height - margin * 2).coerceAtLeast(0)),
        )
        layout(width, height) { card.place((width - card.width) / 2, (height - card.height) / 2) }
    }
}

/** Upright the top row stands this far in from the sides: the disc of the gear 16 from the edge, its touch 4 nearer. */
private val TopRowSide = LiveDimens.GearEnd - (LiveDimens.GearTouch - LiveDimens.GearSize) / 2

/** In the right column of landscape the disc of the gear stands on the edge of the column, its touch 4 over it. */
private val GearOverhang = (LiveDimens.GearTouch - LiveDimens.GearSize) / 2

/**
 * The top row (spec 3.36.6, 5.29 R6): «Игра | Настройка» and the gear to «Настройки» at its right end, 48 high — the whole row
 * answers a touch. The switcher stands in the middle of its row, clear of the touch of the gear and of as much on the other side
 * ([SwitcherFit]). Upright that is the middle of the screen, and where its words do not fit there they step down to 12 sp first;
 * only then it moves off the middle up to the gear's touch, never under it. In the right column of landscape it is the middle of
 * the column — one axis with the word, the cents and the key under it (`live.html`, 5) — but the words keep their size there
 * (spec 3.36.6: the controls do not shrink): where the switcher at that size does not fit the middle, it stands as near to it as
 * the gear's touch lets it. The disc of the gear is on the column's edge. While a take records both are dimmed and do not answer
 * (spec 3.1, 3.9); without the permission the switcher is dimmed and works.
 */
@Composable
private fun TopRow(
    state: LiveState,
    onIntent: (LiveIntent) -> Unit,
    chrome: () -> Float,
    chromeAlpha: Float,
    slots: LiveSlots,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val recording = state.recording
    val overhang = if (landscape) GearOverhang else 0.dp
    // how far the touch of the gear reaches into the row from its end; the middle keeps as much free on the other side
    val gearReach = LiveDimens.GearTouch - overhang
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(LiveDimens.TopRowHeight),
    ) {
        val plan = rememberSwitcherPlan(centered = maxWidth - gearReach * 2, beside = maxWidth - gearReach, keepSize = landscape)
        Layout(
            content = {
                ModeSwitcher(
                    mode = state.mode,
                    onSelect = { onIntent(LiveIntent.SelectMode(it)) },
                    plan = plan,
                    enabled = recording == null,
                    modifier = Modifier.chrome(if (recording != null) LiveDimens.DISABLED_ALPHA else chromeAlpha, chrome),
                )
                Gear(slots, recording, chrome)
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val switcher = measurables[0].measure(loose)
            val gear = measurables.getOrNull(1)?.measure(loose)
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            layout(width, height) {
                val gearStart = width - (gear?.width ?: 0) + overhang.roundToPx()
                gear?.place(gearStart, (height - gear.height) / 2)
                switcher.place(SwitcherFit.start(width, switcher.width, gearStart, plan.centered), (height - switcher.height) / 2)
            }
        }
    }
}

/**
 * The scale under the word and the cents, 8 under them (spec 3.36.6): it unfolds and folds with [shown], the number the room under
 * the ring follows — read while laying out and drawing, so the ring and the scale move as one. Its clip and its layer span the whole
 * row and the line stands [LiveDimens.ScreenPadding] inside them: the slider and its halo at an end of the line (the cents of
 * «Настройка» go far past its ±50) stand whole in the side room, as before R6 (spec 5.29 R6: the slider and the halo as they were).
 */
@Composable
private fun UnderWordScale(state: LiveState, gauge: () -> LiveGauge, zoneColor: () -> Color, shown: () -> Float) {
    Scale(
        state = state,
        gauge = gauge,
        zoneColor = zoneColor,
        fade = shown,
        inset = LiveDimens.ScreenPadding,
        modifier = Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * shown()).roundToInt()) { placeable.place(0, 0) }
            }
            .padding(top = LiveDimens.ScaleTopGap),
    )
}

/**
 * The bottom row (spec 3.36.6, 5.29 R6): the key in the middle, «Что играю» and the practice tag 8 from it on either side — both as
 * wide as each other, 150 where the row allows it and narrower together where it does not ([LiveLayoutMath.keyCardWidth]), the row
 * 10 in from its sides.
 */
@Composable
private fun KeyRow(
    modifier: Modifier,
    bookmark: @Composable (Dp) -> Unit,
    tag: @Composable (Dp) -> Unit,
    key: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val card = LiveLayoutMath.keyCardWidth(maxWidth.value).dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LiveDimens.KeyRowSide),
            horizontalArrangement = Arrangement.spacedBy(LiveDimens.CardToKey, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(card)) { bookmark(card) }
            key()
            Box(Modifier.width(card)) { tag(card) }
        }
    }
}

/** The gear to «Настройки» (spec 3.8): it fades with the light, and with the switcher while a take is recorded. */
@Composable
private fun Gear(slots: LiveSlots, recording: RecordingState?, chrome: () -> Float, modifier: Modifier = Modifier) {
    slots.gear(recording == null, modifier.chrome(if (recording != null) LiveDimens.DISABLED_ALPHA else 1f, chrome))
}

/**
 * The record key: dimmed while it may not record — 0.4, times the light at rest; the key of a running recording stays whole in the
 * dark — it is how the performance ends ([RecordKeyLight]). The alpha is read while drawing.
 */
@Composable
private fun RecordKey(slots: LiveSlots, state: LiveState, chrome: () -> Float) {
    val recording = state.recording != null
    val enabled = state.canRecord || recording
    val alpha = remember(recording, enabled, chrome) { { RecordKeyLight.alpha(recording, enabled, chrome()) } }
    slots.recordKey(recording, enabled, alpha)
}

/** The light of what stays whole in the dark. */
private val Whole: () -> Float = { 1f }

/**
 * Handoff `v1-land`, spec 3.36.6: the ring panel on the left — without the permission the card «нет разрешения» in its middle; on the
 * right the top row, the strings of «Настройка», the plate of the status line (in «Настройка» too), the word and the cents, the scale
 * of «Настройка», the strip of a take and the bottom row, its cards as wide as the column allows. [column] — its padding, gaps and
 * scale: tighter in a low window, in both modes, so the switcher does not jump when the mode changes.
 */
@Composable
private fun LandscapeLayout(
    state: LiveState,
    gauge: () -> LiveGauge,
    zoneColor: () -> Color,
    glow: State<Float>,
    chrome: () -> Float,
    showVenue: Boolean,
    onIntent: (LiveIntent) -> Unit,
    ringModifier: Modifier,
    reduceMotion: Boolean,
    slots: LiveSlots,
    column: LiveLayoutMath.LandscapeColumn,
    silhouette: Note?,
) {
    val noMic = state.signal == LiveSignal.NoMicPermission
    val sounding = state.signal as? LiveSignal.Sounding
    val chromeAlpha = if (noMic) LiveDimens.CHROME_ALPHA_NO_MIC else 1f
    val recording = state.recording
    val tuning = state.mode == LiveMode.TUNING
    val gap = column.gap.dp

    Row(modifier = Modifier.fillMaxSize()) {
        val panel = Modifier
            .weight(LiveDimens.LANDSCAPE_RING_PANEL_FRACTION)
            .fillMaxHeight()
        if (noMic) {
            PromptPanel(onGrant = { onIntent(LiveIntent.GrantMicClicked) }, ringModifier = ringModifier, modifier = panel)
        } else {
            BoxWithConstraints(modifier = panel, contentAlignment = Alignment.Center) {
                val ringSize = ringSizeFor(state, landscape = true, maxWidth, maxHeight, reserved = 0.dp)
                // the ring stands in the middle of its panel: the waves may go as far as its left edge and the screen's top and bottom
                val waveReach = GlowMath.waveReach(ringSize.value, minOf(maxWidth, maxHeight).value / 2)
                Ring(state, gauge, zoneColor, glow, ringSize, waveReach, ringModifier, reduceMotion, silhouette)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f - LiveDimens.LANDSCAPE_RING_PANEL_FRACTION)
                .fillMaxHeight()
                .padding(
                    start = LiveDimens.LandscapePaddingStart,
                    end = LiveDimens.LandscapePaddingEnd,
                    top = column.padding.dp,
                    bottom = column.padding.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // the top row, and the strings unfolding under it in 200 ms with their gap, as upright (spec 3.6) — one child of the
            // column, so its spacing does not jump
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TopRow(state = state, onIntent = onIntent, chrome = chrome, chromeAlpha = chromeAlpha, slots = slots, landscape = true)
                AnimatedVisibility(
                    visible = tuning,
                    enter = expandVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                        fadeIn(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
                    exit = shrinkVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                        fadeOut(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
                ) {
                    StringRow(
                        tuning = state.tuning,
                        onStringClick = { onIntent(LiveIntent.StringClicked(it)) },
                        light = chrome,
                        base = chromeAlpha,
                        topPadding = gap,
                    )
                }
            }
            StatusLineRow(line = state.statusLine, tuning = state.tuning, modifier = Modifier.chrome(1f, chrome), plate = showVenue)
            // The word and the cents take the room the column leaves them (spec 5.29 R6): full, compact, then smaller down to the letters
            // of the strings — and where not even that stands, they give way whole, their place kept; a glyph is never clipped. Without
            // the permission their place is kept empty: the card is in the panel of the ring.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val step = if (noMic) null else rememberStatusStep(roomHeight = maxHeight, roomWidth = maxWidth)
                if (step != null) {
                    StatusRow(
                        direction = sounding?.direction,
                        cents = sounding?.displayCents ?: 0,
                        color = zoneColor,
                        visible = sounding != null,
                        height = Dp.Unspecified,
                        step = step,
                    )
                }
            }
            ScaleSlot(state = state, gauge = gauge, zoneColor = zoneColor, visible = tuning, height = column.scale.dp)
            RecordingStripSlot(slots = slots, recording = recording, gauge = gauge)
            // the same bottom row as upright, its cards as wide as the column allows (spec 3.36.6: 150 on 892 × 412, ≈ 104 on 640 × 360)
            KeyRow(
                modifier = Modifier.padding(top = LiveDimens.LandscapeRecordTopPadding),
                bookmark = { width -> slots.bookmark(width, chrome) },
                tag = { width -> slots.tag(width, chrome) },
            ) {
                RecordKey(slots, state, chrome)
            }
        }
    }
}

/**
 * The strip of a take unfolds over the bottom row and folds away again (200 ms) — upright the ring gives it its height as it comes;
 * while it folds it keeps showing the last numbers and notes, because the state is already back to "not recording".
 */
@Composable
private fun RecordingStripSlot(slots: LiveSlots, recording: RecordingState?, gauge: () -> LiveGauge, modifier: Modifier = Modifier) {
    var lastShown by remember { mutableStateOf(RecordingState(elapsedMs = 0)) }
    if (recording != null) SideEffect { lastShown = recording }
    val kept = remember(gauge) { KeptRibbon(gauge) }
    val ribbon = remember(kept) { { kept.current() } }
    AnimatedVisibility(
        visible = recording != null,
        enter = expandVertically(tween(LiveMotion.RECORD_MORPH_MS)) + fadeIn(tween(LiveMotion.RECORD_MORPH_MS)),
        exit = shrinkVertically(tween(LiveMotion.RECORD_MORPH_MS)) + fadeOut(tween(LiveMotion.RECORD_MORPH_MS)),
    ) {
        slots.recordingStrip(recording ?: lastShown, ribbon, modifier)
    }
}

/** The notes of the take as they come, read while drawing; once it is over, the last of them, for the strip folding away. */
private class KeptRibbon(private val gauge: () -> LiveGauge) {
    private var last = RecordingRibbon.EMPTY

    fun current(): RecordingRibbon = gauge().ribbon?.also { last = it } ?: last
}

private fun ringSizeFor(state: LiveState, landscape: Boolean, maxWidth: Dp, maxHeight: Dp, reserved: Dp): Dp {
    val design = LiveLayoutMath.designRing(landscape = landscape, tuning = state.mode == LiveMode.TUNING)
    val margin = LiveDimens.RingMargin.value * 2
    return LiveLayoutMath.ringDiameter(
        designDp = design,
        availableWidthDp = maxWidth.value - margin,
        availableHeightDp = maxHeight.value - if (landscape) margin else 0f,
        reservedHeightDp = reserved.value,
    ).dp
}

@Composable
private fun Ring(
    state: LiveState,
    gauge: () -> LiveGauge,
    zoneColor: () -> Color,
    glow: State<Float>,
    size: Dp,
    waveReach: Float,
    modifier: Modifier,
    reduceMotion: Boolean,
    silhouette: Note?,
) {
    val sounding = state.signal as? LiveSignal.Sounding
    // Silence has no count of its own; keeping the last one means going silent is not "a new note".
    var noteSerial by remember { mutableIntStateOf(sounding?.noteSerial ?: 0) }
    if (sounding != null) noteSerial = sounding.noteSerial
    val level = remember(gauge) { { gauge().level } }
    GlowRing(
        glow = glow,
        level = level,
        zoneColor = zoneColor,
        noteSerial = noteSerial,
        holdComplete = sounding?.holdComplete == true,
        reduceMotion = reduceMotion,
        size = size,
        modifier = modifier,
        waveReach = waveReach,
    ) {
        RingContent(state.signal, silhouette, noteScale = LiveLayoutMath.noteScale(size.value))
    }
}

/**
 * The scale with its marker belongs to tuning mode only (spec 3.14): turning a peg is a slow move made with the eyes on the gauge.
 * Here it unfolds [visible] in the time of the string row: in the right column of landscape, [height] high, and upright only
 * without the permission — then at the bottom of the place, 8 under the card «нет разрешения», as before R6 (with the permission the
 * scale is under the word, [UnderWordScale]).
 * It stays whole in the dark: it is read, not touched.
 */
@Composable
private fun ScaleSlot(
    state: LiveState,
    gauge: () -> LiveGauge,
    zoneColor: () -> Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = LiveDimens.ScaleHeight,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) + fadeIn(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
        exit = shrinkVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) + fadeOut(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
    ) {
        Scale(state, gauge, zoneColor, modifier, height)
    }
}

/**
 * The scale of «Настройка»: whole while a note sounds, quieter in silence and without the permission (spec 3.4, 3.14); [fade] — how
 * far it has come in, read in its layer with the rest of its alpha; [inset] — the line that far in from the sides of the layer, so
 * the slider at an end of it is not cut by the layer's bounds.
 */
@Composable
private fun Scale(
    state: LiveState,
    gauge: () -> LiveGauge,
    zoneColor: () -> Color,
    modifier: Modifier = Modifier,
    height: Dp = LiveDimens.ScaleHeight,
    fade: () -> Float = Whole,
    inset: Dp = 0.dp,
) {
    val sounding = state.signal as? LiveSignal.Sounding
    val scale = state.scale
    val markerFraction = remember(gauge, scale) { { gauge().cents?.let { ScaleMath.markerFraction(it, scale).toFloat() } } }
    val quiet = when {
        sounding != null -> 1f
        state.signal == LiveSignal.NoMicPermission -> LiveDimens.SCALE_ALPHA_NO_MIC
        else -> LiveDimens.SCALE_ALPHA_IDLE
    }
    CentsScale(
        markerVisible = sounding != null,
        markerFraction = markerFraction,
        inTuneFraction = ScaleMath.inTuneFraction(state.scale).toFloat(),
        haloColor = zoneColor,
        modifier = modifier
            .graphicsLayer { alpha = quiet * fade() }
            .padding(horizontal = inset),
        height = height,
    )
}

private enum class RingContentKind { NOTE, EMPTY, SILHOUETTE }

/**
 * What is inside the ring. Kinds cross-fade; the note itself changes at once, because the note name has to follow the playing
 * immediately. [silhouette] — the note of the locked string in silence, pale (spec 3.36.6, behind its switch): TalkBack does not
 * hear it, the hint and the string button say the target.
 */
@Composable
private fun RingContent(signal: LiveSignal, silhouette: Note?, noteScale: Float) {
    val note = (signal as? LiveSignal.Sounding)?.note
    var lastNote by remember { mutableStateOf<Note?>(null) }
    if (note != null) SideEffect { lastNote = note }
    var lastSilhouette by remember { mutableStateOf<Note?>(null) }
    if (silhouette != null) SideEffect { lastSilhouette = silhouette }

    val kind = when (signal) {
        is LiveSignal.Sounding -> RingContentKind.NOTE
        // The words for these are in the status line above: the ring is empty and calm (spec 3.14) — or holds the pale note of
        // the locked string, when its switch is on.
        LiveSignal.Silence -> if (silhouette != null) RingContentKind.SILHOUETTE else RingContentKind.EMPTY
        // without the permission there is no ring at all (spec 3.36.6): the card stands in its place
        LiveSignal.TooNoisy, LiveSignal.MicUnavailable, LiveSignal.NoMicPermission -> RingContentKind.EMPTY
    }
    // Both layers fill the ring, otherwise Crossfade stacks them from the top-start corner and
    // texts of different width sit side by side during the fade.
    Crossfade(
        targetState = kind,
        modifier = Modifier.fillMaxSize(),
        animationSpec = tween(LiveMotion.CONTENT_FADE_MS),
        label = "ringContent",
    ) { shown ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (shown) {
                // while fading out the signal is already silent: keep showing the last note
                RingContentKind.NOTE -> (note ?: lastNote)?.let { NoteLabel(it, scale = noteScale) }
                RingContentKind.EMPTY -> Unit
                // another locked string changes the note at once, the kind stays
                RingContentKind.SILHOUETTE -> (silhouette ?: lastSilhouette)?.let {
                    NoteLabel(it, modifier = Modifier.clearAndSetSemantics { }, scale = noteScale, alpha = LiveDimens.SILHOUETTE_ALPHA)
                }
            }
        }
    }
}

/** Center of this layout in the coordinates of the screen root placed at [rootPosition]. */
private fun LayoutCoordinates.centerIn(rootPosition: Offset): Offset =
    boundsInRoot().center - rootPosition
