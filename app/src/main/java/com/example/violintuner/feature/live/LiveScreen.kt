package com.example.violintuner.feature.live

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.journey.LocalHomeLook
import com.example.violintuner.feature.live.components.CentsScale
import com.example.violintuner.feature.live.components.GlowRing
import com.example.violintuner.feature.live.components.LiveDimens
import com.example.violintuner.feature.live.components.LiveMotion
import com.example.violintuner.feature.live.components.MicGlyph
import com.example.violintuner.feature.live.components.MicPermissionPrompt
import com.example.violintuner.feature.live.components.ModeSwitcher
import com.example.violintuner.feature.live.components.NoteLabel
import com.example.violintuner.feature.live.components.PracticeChipSlot
import com.example.violintuner.feature.live.components.RecordButton
import com.example.violintuner.feature.live.components.RecordingStrip
import com.example.violintuner.feature.live.components.StatusLineRow
import com.example.violintuner.feature.live.components.StatusRow
import com.example.violintuner.feature.live.components.StringRow
import com.example.violintuner.feature.live.components.ZoneEllipse
import com.example.violintuner.feature.live.components.rememberRingGlow
import com.example.violintuner.feature.live.components.zoneBackground
import com.example.violintuner.feature.live.venue.VenueBackdrop
import com.example.violintuner.feature.live.venue.VenueLabel
import com.example.violintuner.feature.live.venue.VenueLook
import com.example.violintuner.feature.live.venue.rememberVenuePicture
import kotlinx.coroutines.delay

/**
 * Live screen (spec 3.1, handoff variant 8; in the room and in the halls — spec 3.27, handoff venue).
 * Stateless. Wider than tall gets the landscape layout (ring on the left, controls on the right),
 * anything else the portrait column.
 *
 * Behind it is the place the player is in: the room, or a hall seen from its stage — chosen on the
 * journey, only named here. While the violin
 * is silent the light is on; as soon as a note is held it goes down, the picture sinks into the dusk
 * and freezes, the colour of the zone lights it, and the controls one does not touch fade with it.
 * [showVenue] false is the Live of before, on its plain dark field (a build for comparison).
 */
@Composable
fun LiveScreen(
    state: LiveState,
    onIntent: (LiveIntent) -> Unit,
    modifier: Modifier = Modifier,
    /** System animations are switched off: the ring stands still and changes in steps (spec 3.14). */
    reduceMotion: Boolean = false,
    showVenue: Boolean = true,
) {
    val zoneColors = ViolinTheme.zoneColors
    val sounding = state.signal as? LiveSignal.Sounding

    // Gradient, status, ring fill and marker halo always share this one color (spec 3.4).
    val zoneColor by animateColorAsState(
        targetValue = zoneColors.colorFor(sounding?.zone),
        animationSpec = tween(state.zoneCrossfadeMs),
        label = "zoneColor",
    )
    // the ring and the light of the zone on the picture are lit by one number
    val glow = rememberRingGlow(if (reduceMotion) state.glowStep else state.glowTarget, reduceMotion)
    val darkness = rememberHouseLights(down = sounding != null || state.recording != null, reduceMotion = reduceMotion, enabled = showVenue)
    val chrome = { VenueLook.chromeAlpha(darkness.value) }

    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var ringCenter by remember { mutableStateOf(Offset.Unspecified) }
    val ringDiameter = remember { mutableFloatStateOf(0f) }
    val ringModifier = Modifier.onGloballyPositioned {
        ringCenter = it.centerIn(rootPosition)
        ringDiameter.floatValue = it.size.width.toFloat()
    }
    val home = LocalHomeLook.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = LiveLayoutMath.isLandscape(maxWidth.value, maxHeight.value)
        val zoneScale = VenueLook.zoneScale(sounding?.zone)
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
            if (showVenue) {
                VenueBackdrop(
                    venue = state.venue,
                    picture = rememberVenuePicture(state.venue, home),
                    landscape = landscape,
                    darkness = { darkness.value },
                    glow = { glow.value },
                    zoneColor = { zoneColor },
                    zoneScale = { zoneScale },
                    ringCenter = { ringCenter },
                    ringDiameter = { ringDiameter.floatValue },
                    // upright the tab bar lies under the picture: the floor fades into it
                    fadeInto = if (landscape) null else MaterialTheme.colorScheme.surfaceContainer,
                )
            }
            if (landscape) {
                LandscapeLayout(state, zoneColor, glow, chrome, showVenue, onIntent, ringModifier, reduceMotion)
            } else {
                PortraitLayout(state, zoneColor, glow, chrome, showVenue, onIntent, ringModifier, reduceMotion)
            }
        }
    }
}

/**
 * The light in the hall (spec 3.27, 5.20): 0 — on, 1 — out. It goes out as soon as a note is held or
 * a recording starts, like a switch; it comes back like a dimmer, only after [LiveMotion.LIGHT_ON_AFTER_MS]
 * without either — a bow change, a breath, a rest in the music do not light it. Whether it is out
 * survives a rotation, so a note held through it does not flash the room.
 */
@Composable
private fun rememberHouseLights(down: Boolean, reduceMotion: Boolean, enabled: Boolean): State<Float> {
    // a screen that opens on a note already sounding starts with the light out
    var out by rememberSaveable { mutableStateOf(down && enabled) }
    LaunchedEffect(down, enabled) {
        if (!enabled) {
            out = false
        } else if (down) {
            out = true
        } else {
            delay(LiveMotion.LIGHT_ON_AFTER_MS)
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

/** The alpha of a control one does not touch while playing: its own, times the light (handoff `chrome.dim`). */
private fun Modifier.chrome(base: Float, chrome: () -> Float): Modifier = graphicsLayer { alpha = base * chrome() }

@Composable
private fun PortraitLayout(
    state: LiveState,
    zoneColor: Color,
    glow: State<Float>,
    chrome: () -> Float,
    showVenue: Boolean,
    onIntent: (LiveIntent) -> Unit,
    ringModifier: Modifier,
    reduceMotion: Boolean,
) {
    val noMic = state.signal == LiveSignal.NoMicPermission
    val sounding = state.signal as? LiveSignal.Sounding
    val chromeAlpha = if (noMic) LiveDimens.CHROME_ALPHA_NO_MIC else 1f
    val recording = state.recording

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ModeSwitcher(
            mode = state.mode,
            onSelect = { onIntent(LiveIntent.SelectMode(it)) },
            enabled = recording == null,
            modifier = Modifier
                .padding(
                    start = LiveDimens.ScreenPadding,
                    end = LiveDimens.ScreenPadding,
                    top = LiveDimens.SwitcherTopPadding,
                )
                .chrome(if (recording != null) LiveDimens.DISABLED_ALPHA else chromeAlpha, chrome),
        )
        // the practice tag on the left, the place on the right (handoff 29k)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = LiveDimens.ScreenPadding, end = LiveDimens.ScreenPadding, top = LiveDimens.PlaceRowTopPadding)
                .height(LiveDimens.PlaceRowHeight)
                .chrome(1f, chrome),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PracticeChipSlot(practiceMs = state.practiceMs, onClick = { onIntent(LiveIntent.PracticeChipClicked) })
            Spacer(Modifier.weight(1f))
            if (showVenue) state.venue?.let { VenueLabel(it) }
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
            // A ring this small means a small screen: the word and the cents shrink with it.
            val compact = ringSize < LiveDimens.CompactStatusBelowRing
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    if (compact) LiveDimens.IndicatorSpacingCompact else LiveDimens.IndicatorSpacing,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Ring(state, zoneColor, glow, ringSize, ringModifier, reduceMotion)
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
            zoneColor = zoneColor,
            modifier = Modifier.padding(
                start = LiveDimens.ScreenPadding,
                end = LiveDimens.ScreenPadding,
                bottom = LiveDimens.ScaleBottomPadding,
            ),
        )
        RecordingStripSlot(
            recording = recording,
            modifier = Modifier.padding(
                start = LiveDimens.ScreenPadding,
                end = LiveDimens.ScreenPadding,
                top = LiveDimens.RecordingStripTopPadding,
            ),
        )
        RecordButton(
            recording = recording != null,
            enabled = state.canRecord || recording != null,
            onClick = { onIntent(LiveIntent.RecordClicked) },
            modifier = Modifier
                .padding(vertical = LiveDimens.RecordPaddingVertical)
                // the key of a running recording stays whole in the dark: it is how the performance ends
                .chrome(if (state.canRecord || recording != null) 1f else LiveDimens.DISABLED_ALPHA) { if (recording != null) 1f else chrome() },
        )
    }
}

/** Handoff `v1-land`: ring panel on the left; switcher, status, scale and record on the right. */
@Composable
private fun LandscapeLayout(
    state: LiveState,
    zoneColor: Color,
    glow: State<Float>,
    chrome: () -> Float,
    showVenue: Boolean,
    onIntent: (LiveIntent) -> Unit,
    ringModifier: Modifier,
    reduceMotion: Boolean,
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
            Ring(state, zoneColor, glow, ringSize, ringModifier, reduceMotion)
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
            // The only sign of a running practice here: there is no tab bar in landscape.
            PracticeChipSlot(practiceMs = state.practiceMs, onClick = { onIntent(LiveIntent.PracticeChipClicked) }, modifier = Modifier.chrome(1f, chrome))
            // the switcher, and the place to its right (handoff 29g)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ModeSwitcher(
                    mode = state.mode,
                    onSelect = { onIntent(LiveIntent.SelectMode(it)) },
                    enabled = recording == null,
                    modifier = Modifier.chrome(if (recording != null) LiveDimens.DISABLED_ALPHA else chromeAlpha, chrome),
                )
                Spacer(Modifier.weight(1f))
                if (showVenue) state.venue?.let { VenueLabel(it, modifier = Modifier.chrome(1f, chrome)) }
            }
            if (state.mode == LiveMode.TUNING) {
                StringRow(
                    tuning = state.tuning,
                    onStringClick = { onIntent(LiveIntent.StringClicked(it)) },
                    modifier = Modifier.chrome(chromeAlpha, chrome),
                    topPadding = LiveDimens.StringPegHeadRise,
                )
            }
            StatusLineRow(line = state.statusLine, tuning = state.tuning, modifier = Modifier.chrome(1f, chrome))
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
            ScaleSlot(state = state, zoneColor = zoneColor)
            RecordingStripSlot(recording = recording)
            RecordButton(
                recording = recording != null,
                enabled = state.canRecord || recording != null,
                onClick = { onIntent(LiveIntent.RecordClicked) },
                modifier = Modifier
                    .padding(top = LiveDimens.LandscapeRecordTopPadding)
                    .chrome(if (state.canRecord || recording != null) 1f else LiveDimens.DISABLED_ALPHA) { if (recording != null) 1f else chrome() },
            )
        }
    }
}

/**
 * The recording strip unfolds above the record button and folds away again; while it folds it
 * keeps showing the last numbers, because the state is already back to "not recording".
 */
@Composable
private fun RecordingStripSlot(recording: RecordingState?, modifier: Modifier = Modifier) {
    var lastShown by remember { mutableStateOf(RecordingState(elapsedMs = 0, bars = emptyList())) }
    if (recording != null) SideEffect { lastShown = recording }
    AnimatedVisibility(
        visible = recording != null,
        enter = expandVertically(tween(LiveMotion.RECORD_MORPH_MS)) + fadeIn(tween(LiveMotion.RECORD_MORPH_MS)),
        exit = shrinkVertically(tween(LiveMotion.RECORD_MORPH_MS)) + fadeOut(tween(LiveMotion.RECORD_MORPH_MS)),
    ) {
        RecordingStrip(recording = recording ?: lastShown, modifier = modifier)
    }
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
private fun Ring(state: LiveState, zoneColor: Color, glow: State<Float>, size: Dp, modifier: Modifier, reduceMotion: Boolean) {
    val sounding = state.signal as? LiveSignal.Sounding
    // Silence has no count of its own; keeping the last one means going silent is not "a new note".
    var noteSerial by remember { mutableIntStateOf(sounding?.noteSerial ?: 0) }
    if (sounding != null) noteSerial = sounding.noteSerial
    GlowRing(
        glow = glow,
        level = sounding?.level ?: 0f,
        zoneColor = zoneColor,
        noteSerial = noteSerial,
        holdComplete = sounding != null && sounding.holdProgress >= 1.0,
        reduceMotion = reduceMotion,
        size = size,
        modifier = modifier,
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
private fun ScaleSlot(state: LiveState, zoneColor: Color, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.mode == LiveMode.TUNING,
        enter = expandVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) + fadeIn(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
        exit = shrinkVertically(tween(LiveMotion.STRING_ROW_EXPAND_MS)) + fadeOut(tween(LiveMotion.STRING_ROW_EXPAND_MS)),
    ) {
        Scale(state, zoneColor, modifier)
    }
}

@Composable
private fun Scale(state: LiveState, zoneColor: Color, modifier: Modifier = Modifier) {
    val sounding = state.signal as? LiveSignal.Sounding
    CentsScale(
        markerFraction = sounding?.let { ScaleMath.markerFraction(it.cents, state.scale).toFloat() },
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
