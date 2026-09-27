package com.violinjourney.app.feature.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
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
import com.violinjourney.app.feature.live.components.MicGlyph
import com.violinjourney.app.feature.live.components.MicPermissionPrompt
import com.violinjourney.app.feature.live.components.ModeSwitcher
import com.violinjourney.app.feature.live.components.NoteLabel
import com.violinjourney.app.feature.live.components.StatusLineRow
import com.violinjourney.app.feature.live.components.StatusRow
import com.violinjourney.app.feature.live.components.StringRow
import com.violinjourney.app.feature.live.components.ZoneEllipse
import com.violinjourney.app.feature.live.components.rememberRingGlow
import com.violinjourney.app.feature.live.components.zoneBackground
import com.violinjourney.app.feature.live.venue.VenueLook
import kotlinx.coroutines.delay

/**
 * What a platform draws on Live around the shared layout: the place behind it, the keys under it, the gear, the
 * strip of a recording and the sheets over it. Each gets the modifier or the light the layout has worked out, so the
 * dimming and the placing stay here, in one place for Android and iOS. Empty slots leave their room empty.
 */
@Immutable
class LiveSlots(
    /** The picture behind Live (spec 3.27); null — the plain dark field with the gradient of the zone. */
    val backdrop: (@Composable (LiveBackdrop) -> Unit)? = null,
    /** The bookmark of blocks left of the key (spec 3.28), at most [Dp] wide; the light lets it decide how it dims. */
    val bookmark: @Composable (width: Dp, chrome: () -> Float) -> Unit = { _, _ -> },
    /** The practice tag right of the key (spec 3.12), at most [Dp] wide. */
    val tag: @Composable (maxWidth: Dp, modifier: Modifier) -> Unit = { _, _ -> },
    /** The record key (spec 3.9). */
    val recordKey: @Composable (recording: Boolean, enabled: Boolean, modifier: Modifier) -> Unit = { _, _, _ -> },
    /** The gear to «Настройки» (spec 3.8). */
    val gear: @Composable (enabled: Boolean, modifier: Modifier) -> Unit = { _, _ -> },
    /** The strip above the key while a take is recorded (spec 3.9); its notes are read while drawing. */
    val recordingStrip: @Composable (recording: RecordingState, ribbon: () -> RecordingRibbon, modifier: Modifier) -> Unit = { _, _, _ -> },
    /** Over everything: the sheets of blocks. */
    val overlay: @Composable (landscape: Boolean) -> Unit = {},
)

/** What the picture behind Live follows: the light, the glow of the ring and where the ring is. */
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
        down = sounding != null || state.recording != null,
        quietSinceNanos = state.quietSinceNanos,
        reduceMotion = reduceMotion,
        enabled = showVenue,
    )
    val chrome = remember(darkness) { { VenueLook.chromeAlpha(darkness.value) } }
    val readGauge = remember { { currentGauge() } }

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
            if (landscape) {
                LandscapeLayout(state, readGauge, zoneColor, glow, chrome, showVenue, onIntent, ringModifier, reduceMotion, slots)
            } else {
                PortraitLayout(state, readGauge, zoneColor, glow, chrome, showVenue, onIntent, ringModifier, reduceMotion, slots)
            }
            slots.overlay(landscape)
        }
    }
}

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
) {
    val noMic = state.signal == LiveSignal.NoMicPermission
    val sounding = state.signal as? LiveSignal.Sounding
    val chromeAlpha = if (noMic) LiveDimens.CHROME_ALPHA_NO_MIC else 1f
    val recording = state.recording

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // the switcher in the middle, the gear in the right corner of its row (handoff nav_bar 35); the place is not named
        // on Live any more — its picture says it
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LiveDimens.SwitcherTopPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            ModeSwitcher(
                mode = state.mode,
                onSelect = { onIntent(LiveIntent.SelectMode(it)) },
                enabled = recording == null,
                modifier = Modifier.chrome(if (recording != null) LiveDimens.DISABLED_ALPHA else chromeAlpha, chrome),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Gear(slots, recording, chrome, Modifier.padding(end = LiveDimens.GearEnd - (LiveDimens.GearTouch - LiveDimens.GearSize) / 2))
            }
        }
        AnimatedVisibility(
            visible = state.mode == LiveMode.TUNING,
            enter = expandVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                fadeIn(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
            exit = shrinkVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                fadeOut(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
        ) {
            StringRow(
                tuning = state.tuning,
                onStringClick = { onIntent(LiveIntent.StringClicked(it)) },
                modifier = Modifier.chrome(chromeAlpha, chrome),
            )
        }
        StatusLineRow(
            line = state.statusLine,
            tuning = state.tuning,
            modifier = Modifier
                .padding(top = LiveDimens.StatusLineTopPadding)
                .chrome(1f, chrome),
            plate = showVenue,
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val reserved = if (noMic) {
                LiveDimens.PromptReservedHeight
            } else {
                LiveDimens.IndicatorSpacing + LiveDimens.StatusRowHeight
            }
            val ringSize = ringSizeFor(state, landscape = false, maxWidth, maxHeight, reserved)
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
                Ring(state, gauge, zoneColor, glow, ringSize, waveReach, ringModifier, reduceMotion)
                if (noMic) {
                    MicPermissionPrompt(onGrantClick = { onIntent(LiveIntent.GrantMicClicked) })
                } else {
                    StatusRow(
                        direction = sounding?.direction,
                        cents = sounding?.displayCents ?: 0,
                        color = zoneColor,
                        visible = sounding != null,
                        compact = compact,
                    )
                }
            }
        }
        ScaleSlot(
            state = state,
            gauge = gauge,
            zoneColor = zoneColor,
            modifier = Modifier.padding(
                start = LiveDimens.ScreenPadding,
                end = LiveDimens.ScreenPadding,
                bottom = LiveDimens.ScaleBottomPadding,
            ),
        )
        RecordingStripSlot(
            slots = slots,
            recording = recording,
            gauge = gauge,
            modifier = Modifier.padding(
                start = LiveDimens.ScreenPadding,
                end = LiveDimens.ScreenPadding,
                top = LiveDimens.RecordingStripTopPadding,
            ),
        )
        // The key stays in the middle; the bookmark of blocks lies to its left, the practice tag to its right (spec 3.28,
        // 3.12; handoff 30a2, nav_bar 35) — «what I play · record · how long I practise». The ring gives up no height
        // for them, and all that is touched in silence is down here, under the thumb.
        KeyRow(
            modifier = Modifier.padding(vertical = LiveDimens.RecordPaddingVertical),
            maxBookmark = LiveDimens.BookmarkWidth,
            bookmark = { width -> slots.bookmark(width, chrome) },
            tag = { width -> Tag(slots, width, chrome) },
        ) {
            RecordKey(slots, state, chrome)
        }
    }
}

/**
 * The row of the record key: the key in the middle, the bookmark of blocks hugging it from the left, as wide as the
 * half row allows but never wider than [maxBookmark]; the practice tag hugging it from the right, its mirror.
 */
@Composable
private fun KeyRow(
    modifier: Modifier,
    maxBookmark: Dp,
    bookmark: @Composable (Dp) -> Unit,
    tag: @Composable (Dp) -> Unit,
    key: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(end = LiveDimens.BookmarkToKey),
            contentAlignment = Alignment.CenterEnd,
        ) {
            bookmark(minOf(maxBookmark, maxWidth - LiveDimens.BookmarkMargin))
        }
        key()
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(start = LiveDimens.BookmarkToKey),
            contentAlignment = Alignment.CenterStart,
        ) {
            tag(maxWidth - LiveDimens.BookmarkMargin)
        }
    }
}

/** The practice tag (spec 3.12): it fades with the light like the bookmark — the time is for a look in silence. */
@Composable
private fun Tag(slots: LiveSlots, width: Dp, chrome: () -> Float) {
    slots.tag(width, Modifier.chrome(1f, chrome))
}

/** The gear to «Настройки» (spec 3.8): it fades with the light, and with the switcher while a take is recorded. */
@Composable
private fun Gear(slots: LiveSlots, recording: RecordingState?, chrome: () -> Float, modifier: Modifier = Modifier) {
    slots.gear(recording == null, modifier.chrome(if (recording != null) LiveDimens.DISABLED_ALPHA else 1f, chrome))
}

/** The record key: dimmed while it may not record; the key of a running recording stays whole in the dark — it is how the performance ends. */
@Composable
private fun RecordKey(slots: LiveSlots, state: LiveState, chrome: () -> Float) {
    val recording = state.recording != null
    val light = remember(recording, chrome) { if (recording) Whole else chrome }
    slots.recordKey(
        recording,
        state.canRecord || recording,
        Modifier.chrome(if (state.canRecord || recording) 1f else LiveDimens.DISABLED_ALPHA, light),
    )
}

/** The light of what stays whole in the dark. */
private val Whole: () -> Float = { 1f }

/** Handoff `v1-land`: ring panel on the left; switcher, status, scale and record on the right. */
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
) {
    val noMic = state.signal == LiveSignal.NoMicPermission
    val sounding = state.signal as? LiveSignal.Sounding
    val chromeAlpha = if (noMic) LiveDimens.CHROME_ALPHA_NO_MIC else 1f
    val recording = state.recording

    Row(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(LiveDimens.LANDSCAPE_RING_PANEL_FRACTION)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            val ringSize = ringSizeFor(state, landscape = true, maxWidth, maxHeight, reserved = 0.dp)
            // the ring stands in the middle of its panel: the waves may go as far as its left edge and the screen's top and bottom
            val waveReach = GlowMath.waveReach(ringSize.value, minOf(maxWidth, maxHeight).value / 2)
            Ring(state, gauge, zoneColor, glow, ringSize, waveReach, ringModifier, reduceMotion)
        }
        Column(
            modifier = Modifier
                .weight(1f - LiveDimens.LANDSCAPE_RING_PANEL_FRACTION)
                .fillMaxHeight()
                .padding(
                    start = LiveDimens.LandscapePaddingStart,
                    end = LiveDimens.LandscapePaddingEnd,
                    top = LiveDimens.LandscapePaddingVertical,
                    bottom = LiveDimens.LandscapePaddingVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(LiveDimens.LandscapeSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // the switcher, and the gear to its right (handoff 29g, nav_bar 35); the pegs unfold under them in 200 ms,
            // their gap with them, as upright (spec 3.6) — one child of the column, so its spacing does not jump
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ModeSwitcher(
                        mode = state.mode,
                        onSelect = { onIntent(LiveIntent.SelectMode(it)) },
                        enabled = recording == null,
                        modifier = Modifier.chrome(if (recording != null) LiveDimens.DISABLED_ALPHA else chromeAlpha, chrome),
                    )
                    Spacer(Modifier.weight(1f))
                    // the edge of the disc on the edge of the column, like the edge of the plank under it
                    Gear(slots, recording, chrome, Modifier.offset(x = (LiveDimens.GearTouch - LiveDimens.GearSize) / 2))
                }
                AnimatedVisibility(
                    visible = state.mode == LiveMode.TUNING,
                    enter = expandVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                        fadeIn(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
                    exit = shrinkVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) +
                        fadeOut(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
                ) {
                    StringRow(
                        tuning = state.tuning,
                        onStringClick = { onIntent(LiveIntent.StringClicked(it)) },
                        modifier = Modifier
                            .padding(top = LiveDimens.LandscapeSpacing)
                            .chrome(chromeAlpha, chrome),
                        topPadding = LiveDimens.StringPegHeadRise,
                    )
                }
            }
            StatusLineRow(line = state.statusLine, tuning = state.tuning, modifier = Modifier.chrome(1f, chrome), plate = showVenue)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (noMic) {
                    MicPermissionPrompt(onGrantClick = { onIntent(LiveIntent.GrantMicClicked) })
                } else {
                    val statusHeight = maxHeight.coerceIn(LiveDimens.LandscapeStatusMinHeight, LiveDimens.StatusRowHeight)
                    StatusRow(
                        direction = sounding?.direction,
                        cents = sounding?.displayCents ?: 0,
                        color = zoneColor,
                        visible = sounding != null,
                        height = statusHeight,
                        // tuning mode plus the practice chip leave little height: smaller beats clipped
                        compact = statusHeight < LiveDimens.LandscapeStatusCompactHeight,
                    )
                }
            }
            ScaleSlot(state = state, gauge = gauge, zoneColor = zoneColor)
            RecordingStripSlot(slots = slots, recording = recording, gauge = gauge)
            // the tag beside the key, as upright: the row is wide enough for both (the handoff would put it under the bookmark)
            KeyRow(
                modifier = Modifier.padding(top = LiveDimens.LandscapeRecordTopPadding),
                maxBookmark = LiveDimens.BookmarkWidthLandscape,
                bookmark = { width -> slots.bookmark(width, chrome) },
                tag = { width -> Tag(slots, width, chrome) },
            ) {
                RecordKey(slots, state, chrome)
            }
        }
    }
}

/**
 * The recording strip unfolds above the record button and folds away again; while it folds it
 * keeps showing the last numbers and notes, because the state is already back to "not recording".
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
    val design = LiveLayoutMath.designRing(
        landscape = landscape,
        tuning = state.mode == LiveMode.TUNING,
        noMic = state.signal == LiveSignal.NoMicPermission,
    )
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
        RingContent(state.signal, noteScale = LiveLayoutMath.noteScale(size.value))
    }
}

/**
 * The scale with its marker belongs to tuning mode only (spec 3.14): turning a peg is a slow
 * move made with the eyes on the gauge. In play mode its place is empty until a recording
 * puts its strip there. It stays whole in the dark: it is read, not touched.
 */
@Composable
private fun ScaleSlot(state: LiveState, gauge: () -> LiveGauge, zoneColor: () -> Color, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.mode == LiveMode.TUNING,
        enter = expandVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) + fadeIn(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
        exit = shrinkVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) + fadeOut(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
    ) {
        Scale(state, gauge, zoneColor, modifier)
    }
}

@Composable
private fun Scale(state: LiveState, gauge: () -> LiveGauge, zoneColor: () -> Color, modifier: Modifier = Modifier) {
    val sounding = state.signal as? LiveSignal.Sounding
    val scale = state.scale
    val markerFraction = remember(gauge, scale) { { gauge().cents?.let { ScaleMath.markerFraction(it, scale).toFloat() } } }
    CentsScale(
        markerVisible = sounding != null,
        markerFraction = markerFraction,
        inTuneFraction = ScaleMath.inTuneFraction(state.scale).toFloat(),
        haloColor = zoneColor,
        modifier = modifier.graphicsLayer {
            alpha = when {
                sounding != null -> 1f
                state.signal == LiveSignal.NoMicPermission -> LiveDimens.SCALE_ALPHA_NO_MIC
                else -> LiveDimens.SCALE_ALPHA_IDLE
            }
        },
    )
}

private enum class RingContentKind { NOTE, EMPTY, NO_MIC }

/**
 * What is inside the ring. Kinds cross-fade; the note itself changes at once, because the note
 * name has to follow the playing immediately.
 */
@Composable
private fun RingContent(signal: LiveSignal, noteScale: Float) {
    val note = (signal as? LiveSignal.Sounding)?.note
    var lastNote by remember { mutableStateOf<Note?>(null) }
    if (note != null) SideEffect { lastNote = note }

    val kind = when (signal) {
        is LiveSignal.Sounding -> RingContentKind.NOTE
        // The words for these are in the status line above: the ring is empty and calm (spec 3.14).
        LiveSignal.Silence, LiveSignal.TooNoisy, LiveSignal.MicUnavailable -> RingContentKind.EMPTY
        LiveSignal.NoMicPermission -> RingContentKind.NO_MIC
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
                RingContentKind.NO_MIC -> MicGlyph()
            }
        }
    }
}

/** Center of this layout in the coordinates of the screen root placed at [rootPosition]. */
private fun LayoutCoordinates.centerIn(rootPosition: Offset): Offset =
    boundsInRoot().center - rootPosition
