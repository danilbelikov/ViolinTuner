package com.violinjourney.app.feature.sound.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.ui.components.DockMetrics
import com.violinjourney.app.core.ui.components.PlayPauseGlyph
import com.violinjourney.app.core.ui.components.SegmentedSwitch
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_heard_violin
import com.violinjourney.app.shared.resources.backing_heard_with
import com.violinjourney.app.shared.resources.backing_preparing
import com.violinjourney.app.shared.resources.session_player_pause
import com.violinjourney.app.shared.resources.session_player_play
import com.violinjourney.app.shared.resources.session_player_position
import com.violinjourney.app.shared.resources.sound_ab_original
import com.violinjourney.app.shared.resources.sound_ab_original_word
import com.violinjourney.app.shared.resources.sound_ab_processed
import com.violinjourney.app.shared.resources.sound_ab_processed_word
import org.jetbrains.compose.resources.stringResource

// The rows of the player at the bottom of a recording and of «Звук» (spec 3.36.5, 5.29 R5).
private val PlayRegular = 56.dp
private val PlayCompact = 48.dp
private val GlyphRegular = 26.dp
private val GlyphCompact = 22.dp
private val WaveRegular = 34.dp
private val WaveCompact = 24.dp
private val PlayGap = 12.dp
private val TimeTop = 2.dp

/** The time under the wave never runs into itself: a position and a length closer than this leave the length out. */
private val TimeGap = 8.dp
private val SpinnerSize = 22.dp
private val SpinnerStroke = 3.dp
private const val SPINNER_TURN_MS = 1_000
private const val SPINNER_TRACK_ALPHA = 0.25f
private const val FULL_TURN = 360f

/** The quarter of the ring that is lit, from its top left to its top right — the border-top of the mockup's spinner. */
private const val SPINNER_ARC_START = -135f
private const val SPINNER_ARC_SWEEP = 90f

/** Handoff `sizes`, «Форма волны»: a wave lower than this is not drawn — a plain 4 dp slider stands instead (spec 3.17). */
private val PlainTrackBelow = 20.dp
private val PlainTrack = 4.dp
private val PlainThumb = 7.dp
private const val BAR_STEP_DP = 3f
private const val BAR_WIDTH_DP = 2f
private const val MIN_BAR = 0.08f

/** What of the wave is still to play: the third level of text at 55 % (5.29 R5). */
private const val WAVE_REST_ALPHA = 0.55f
private const val TABULAR_FIGURES = "tnum"
private const val SIDE_A = "A"
private const val SIDE_B = "B"

/**
 * The panel of a player at the bottom (spec 3.36.5, 5.29 R5): [Regular] — fields 12 / 16 / 12 around «play» 56; [Compact] — in a
 * window lower than 700 dp, portrait 360 × 640 and every phone lying: 8 / 16 / 10 around «play» 48. Pure, with a test.
 */
object PlayerDockMetrics {
    val Regular = DockMetrics(top = 12.dp, side = 16.dp, bottom = 12.dp, button = PlayRegular)
    val Compact = DockMetrics(top = 8.dp, side = 16.dp, bottom = 10.dp, button = PlayCompact)

    /** Lower than 700 dp, in any turn of the phone: the compact panel. */
    fun compact(windowHeight: Dp): Boolean = windowHeight < DockMetrics.LowBelow

    fun of(windowHeight: Dp): DockMetrics = if (compact(windowHeight)) Compact else Regular
}

/** The panel of the player for the height of the window the app is in now. */
@Composable
fun currentPlayerDockMetrics(): DockMetrics {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return PlayerDockMetrics.of(height)
}

/** The outline of the panel of a player: rounded 24 at the top, square at the bottom edge of the screen (5.29 R5). */
val PlayerPanelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * The first row of the player (spec 3.36.5, 5.29 R5): «play» 56 — 48 in the [compact] panel — the waveform (34, compact 24) that is
 * also the seek bar, and under it the time where the finger or the sound is and the length of the recording; [trailing] — what the
 * compact panel of a recording puts on the same row: its A/B and the icon of «Звук»; [timeMiddle] — what the compact panel of «Звук»
 * puts in the line of the time, between the two times: the output meter. [player] keeps its position to the whole second; the played
 * part of the wave follows [position] where it is drawn.
 */
@Composable
internal fun PlayRow(
    player: PlayerState,
    position: () -> Long,
    waveform: List<Float>?,
    compact: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    timeMiddle: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val dragged = remember { mutableStateOf<Float?>(null) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PlayButton(player.playing, compact, onPlayPause)
        Column(Modifier.weight(1f).padding(start = PlayGap)) {
            SeekWave(player, position, waveform, if (compact) WaveCompact else WaveRegular, onSeek, Modifier.fillMaxWidth(), dragged)
            TimeLine(player, dragged, Modifier.fillMaxWidth().padding(top = TimeTop), timeMiddle)
        }
        trailing()
    }
}

/**
 * The first row while the backing of a take is made for the mix (spec 5.25, 3.36.5): on the place of «play» a circle of its size with
 * the one spinner of the screen, on the place of the wave «Готовим минусовку…» — said once by TalkBack when it appears — and after it
 * what the compact panel puts on the row ([trailing]: its dimmed A/B and the icon «Звук»). The words stand whole ([PreparingFit]):
 * beside what follows them in up to two lines of 15 sp, a little smaller if need be; where they would break a word there (a narrow
 * column lying, a long language, a large font), they take the row alone and what followed them stands a row lower, at the end.
 */
@Composable
internal fun PreparingRow(compact: Boolean, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val words = stringResource(Res.string.backing_preparing)
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = PreparingFit.MAX_SP.sp, lineHeight = PreparingLineHeight, fontWeight = FontWeight.ExtraBold)
    val measurer = rememberTextMeasurer()
    val circle = if (compact) PlayCompact else PlayRegular
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val after = subcompose(PreparingSlot.After) { Row(verticalAlignment = Alignment.CenterVertically, content = trailing) }
            .map { it.measure(Constraints(maxWidth = width)) }
        val afterWidth = after.maxOfOrNull { it.width } ?: 0
        val afterHeight = after.maxOfOrNull { it.height } ?: 0
        val head = (circle + PlayGap).roundToPx()
        val alone = (width - head).coerceAtLeast(0)
        val beside = (alone - afterWidth).coerceAtLeast(0)
        val slack = PreparingSlack.toPx()
        val plan = PreparingFit.plan(beside.toFloat(), alone.toFloat()) { room, sizeSp ->
            measurer.standsWhole(words, style.copy(fontSize = sizeSp.sp), room - slack)
        }
        val room = if (plan.stacked) alone else beside
        val row = subcompose(PreparingSlot.Words) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(circle)
                        .background(colors.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { PlayerSpinner() }
                Text(
                    text = words,
                    modifier = Modifier
                        .padding(start = PlayGap)
                        .width(room.toDp())
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = colors.onSurface,
                    maxLines = PREPARING_LINES,
                    overflow = TextOverflow.Ellipsis,
                    style = style.copy(fontSize = plan.sizeSp.sp),
                )
            }
        }.map { it.measure(Constraints(maxWidth = width)) }
        val rowHeight = row.maxOfOrNull { it.height } ?: 0
        val height = if (plan.stacked) rowHeight + afterHeight else maxOf(rowHeight, afterHeight)
        layout(width, height) {
            row.forEach { it.placeRelative(0, if (plan.stacked) 0 else (height - it.height) / 2) }
            // what followed the words: at the end of their row, or at the end of a row of its own under them
            after.forEach { it.placeRelative(width - it.width, if (plan.stacked) rowHeight else (height - it.height) / 2) }
        }
    }
}

private enum class PreparingSlot { After, Words }

/** «Готовим минусовку…» — up to two lines (5.29 R5: 15 sp, 800), their height kept when the words step smaller. */
private const val PREPARING_LINES = 2
private val PreparingLineHeight = 20.sp

/** The row is laid out in whole pixels: words that fit only by a hair are not trusted. */
private val PreparingSlack = 1.dp

/** [words] stand whole in at most [PREPARING_LINES] of [room] px in [style]: no word wider than the room, no line more than two. */
private fun TextMeasurer.standsWhole(words: String, style: TextStyle, room: Float): Boolean {
    if (room <= 0f) return false
    val widest = words.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
    if (widest > room) return false
    return !measure(words, style, maxLines = PREPARING_LINES, constraints = Constraints(maxWidth = room.toInt())).didOverflowHeight
}

/**
 * A/B of the player (spec 3.17, 3.36.5): «A | B» on the ground of the screen, 800 — A the recording as recorded, B with its processing,
 * which is heard by default. A tap chooses; a finger held on A plays the original only while it stays, and B comes back when it lets
 * go ([onOriginal] with `held`). TalkBack hears «A, оригинал» and «B, обработка» with the mark of the choice.
 *
 * [words] — the large A/B of «Звук» the whole width: «A оригинал | B обработка», the letters 15 sp / 800 before words of 14 / 700; words
 * too wide for their half go on two lines of 12 in the same segment, in the compact one on one line of 12 or a step smaller
 * ([SegmentedSwitch]'s `shrinkToTwoLines`) — a word is never cut.
 */
@Composable
internal fun AbSegment(
    original: Boolean,
    onOriginal: (original: Boolean, held: Boolean) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    words: Boolean = false,
) {
    val current by rememberUpdatedState(onOriginal)
    val sides = listOf(SIDE_A, SIDE_B)
    SegmentedSwitch(
        labels = if (words) listOf(stringResource(Res.string.sound_ab_original_word), stringResource(Res.string.sound_ab_processed_word)) else sides,
        selectedIndex = if (original) 0 else 1,
        onSelect = { current(it == 0, false) },
        modifier = modifier,
        compact = compact,
        containerColor = MaterialTheme.colorScheme.surface,
        enabled = enabled,
        strong = !words,
        prefixes = sides.takeIf { words },
        segmentDescriptions = listOf(stringResource(Res.string.sound_ab_original), stringResource(Res.string.sound_ab_processed)),
        // only A is held: its start and its end are the start and the end of «пока держишь»
        onHold = { index, held -> if (index == 0) current(held, true) },
        shrinkToTwoLines = words,
    )
}

/**
 * «С минусовкой | Только скрипка» of a take under a backing (spec 3.32, 3.36.5): what is heard, not a setting — it is not kept past
 * the screen. Words too wide for their half at 14 sp go on two lines of 12 in the same segment — the [compact] one keeps one line, 12
 * sp or a step smaller, its halves by the words where need be (spec 5.29 R5: seen 28, pressed 48); [containerColor] — the ground
 * under it, the screen's on the panel of a player.
 */
@Composable
internal fun BackingSegment(
    heard: Boolean,
    onHeard: (Boolean) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Unspecified,
) {
    SegmentedSwitch(
        labels = listOf(stringResource(Res.string.backing_heard_with), stringResource(Res.string.backing_heard_violin)),
        selectedIndex = if (heard) 0 else 1,
        onSelect = { onHeard(it == 0) },
        modifier = modifier,
        compact = compact,
        containerColor = containerColor,
        enabled = enabled,
        strong = true,
        shrinkToTwoLines = true,
    )
}

@Composable
private fun PlayButton(playing: Boolean, compact: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (playing) Res.string.session_player_pause else Res.string.session_player_play)
    Box(
        modifier = Modifier
            .size(if (compact) PlayCompact else PlayRegular)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseGlyph(playing = playing, tint = colors.onPrimary, size = if (compact) GlyphCompact else GlyphRegular)
    }
}

/**
 * The time under the wave: where the finger drags it — or where the sound is, to the whole second — and the length of the recording
 * at the other end. Where the two do not stand apart (a narrow column lying, an hour of recording) the length gives way: the summary
 * says it too. [middle] — what stands between the two, taking what they leave (the output meter of the compact panel of «Звук»).
 */
@Composable
private fun TimeLine(player: PlayerState, dragged: State<Float?>, modifier: Modifier, middle: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES)
    val duration = player.durationMs.coerceAtLeast(1)
    val at = dragged.value?.let { (it * duration).toLong() } ?: player.positionMs
    if (middle != null) {
        Layout(
            content = {
                Text(Formats.duration(at), color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
                Text(Formats.duration(player.durationMs), color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
                Box(propagateMinConstraints = true) { middle() }
            },
            modifier = modifier,
            measurePolicy = TimeWithMiddlePolicy,
        )
        return
    }
    Layout(
        content = {
            Text(Formats.duration(at), color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
            Text(Formats.duration(player.durationMs), color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0)
        val shown = measurables[0].measure(loose)
        val length = measurables[1].measure(loose)
        val width = constraints.maxWidth
        val both = shown.width + TimeGap.roundToPx() + length.width <= width
        layout(width, maxOf(shown.height, length.height)) {
            shown.placeRelative(0, 0)
            if (both) length.placeRelative(width - length.width, 0)
        }
    }
}

/**
 * The line of the time with something between the times: the two times keep their width, the middle takes what they leave with a gap
 * of [TimeGap] at each side; where that is less than the least width of the middle, the length gives way, as it does in the plain line.
 */
private object TimeWithMiddlePolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = TimeGap.roundToPx()
        val shown = measurables[0].measure(loose)
        val length = measurables[1].measure(loose)
        val width = constraints.maxWidth
        val middle = measurables[2]
        val least = middle.minIntrinsicWidth(constraints.maxHeight)
        val beside = width - shown.width - length.width - gap * 2
        val both = beside >= least
        val room = (if (both) beside else width - shown.width - gap).coerceAtLeast(0)
        val placed = middle.measure(Constraints(minWidth = room, maxWidth = room, maxHeight = loose.maxHeight))
        val height = maxOf(shown.height, length.height, placed.height)
        return layout(width, height) {
            shown.placeRelative(0, (height - shown.height) / 2)
            placed.placeRelative(shown.width + gap, (height - placed.height) / 2)
            if (both) length.placeRelative(width - length.width, (height - length.height) / 2)
        }
    }
}

/**
 * The waveform as the seek bar (spec 3.17, 5.11): the played part in the accent, what is still to play in the third level of text at
 * 55 %; the finger goes anywhere along it. Until reckoned — and lower than 20 dp — a plain track. The played part follows [position]
 * in the draw phase, in a layer of its own: a chunk of sound redraws the wave and nothing around it; what TalkBack is told goes by the
 * whole second. [dragged] — where the finger holds it, 0…1: the time under it follows the finger.
 */
@Composable
internal fun SeekWave(
    player: PlayerState,
    position: () -> Long,
    waveform: List<Float>?,
    height: Dp,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    dragged: MutableState<Float?> = remember { mutableStateOf(null) },
) {
    val colors = MaterialTheme.colorScheme
    val rest = ViolinTheme.textTertiary.copy(alpha = WAVE_REST_ALPHA)
    val duration = player.durationMs.coerceAtLeast(1)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val description = stringResource(Res.string.session_player_position)
    val spoken = dragged.value ?: (player.positionMs.toFloat() / duration)
    Box(
        modifier = modifier
            .height(height)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(spoken, 0f..1f)
                setProgress { fraction -> currentOnSeek((fraction.coerceIn(0f, 1f) * duration).toLong()); true }
                // A reader moves the position by swiping. Its activation does nothing: without an action of its own it would
                // come as a touch in the middle of the wave and seek there.
                onClick { true }
            }
            .pointerInput(duration) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(x: Float) = (x / size.width).coerceIn(0f, 1f)
                    dragged.value = at(down.position.x)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        dragged.value = at(change.position.x)
                    }
                    dragged.value?.let { currentOnSeek((it * duration).toLong()) }
                    dragged.value = null
                }
            }
            .graphicsLayer()
            .drawBehind {
                val played = dragged.value ?: (position().toFloat() / duration)
                val bars = waveform
                if (bars == null || size.height < PlainTrackBelow.toPx()) {
                    val track = PlainTrack.toPx()
                    val y = (size.height - track) / 2
                    drawRoundRect(rest, Offset(0f, y), Size(size.width, track), CornerRadius(track / 2))
                    drawRoundRect(colors.primary, Offset(0f, y), Size(size.width * played, track), CornerRadius(track / 2))
                    drawCircle(colors.primary, PlainThumb.toPx(), Offset(size.width * played, size.height / 2))
                } else {
                    val step = BAR_STEP_DP.dp.toPx()
                    val width = BAR_WIDTH_DP.dp.toPx()
                    val count = (size.width / step).toInt().coerceAtLeast(1)
                    for (index in 0 until count) {
                        // as many columns as fit: the 120 reckoned ones are spread over them
                        val level = bars[(index * bars.size / count).coerceIn(0, bars.lastIndex)].coerceAtLeast(MIN_BAR)
                        val barHeight = size.height * level
                        val x = index * step
                        drawRoundRect(
                            color = if (x / size.width <= played) colors.primary else rest,
                            topLeft = Offset(x, (size.height - barHeight) / 2),
                            size = Size(width, barHeight),
                            cornerRadius = CornerRadius(width / 2),
                        )
                    }
                }
            },
    )
}

/**
 * The one motion of the screen while the backing is made (5.29 R5): a ring of the accent at 25 % with a quarter of it lit, a turn a
 * second. «Убрать анимации» — it stands.
 */
@Composable
private fun PlayerSpinner(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val turn = if (LocalReduceMotion.current) {
        null
    } else {
        rememberInfiniteTransition(label = "spinner").animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN,
            animationSpec = infiniteRepeatable(tween(SPINNER_TURN_MS, easing = LinearEasing)),
            label = "turn",
        )
    }
    Canvas(modifier.size(SpinnerSize).graphicsLayer { rotationZ = turn?.value ?: 0f }) {
        val stroke = SpinnerStroke.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val ring = Size(size.width - stroke, size.height - stroke)
        drawArc(color.copy(alpha = SPINNER_TRACK_ALPHA), 0f, FULL_TURN, useCenter = false, topLeft = topLeft, size = ring, style = Stroke(stroke))
        drawArc(color, SPINNER_ARC_START, SPINNER_ARC_SWEEP, useCenter = false, topLeft = topLeft, size = ring, style = Stroke(stroke))
    }
}
