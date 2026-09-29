package com.violinjourney.app.feature.session.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.Finger
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.ButtonFit
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.RollSegment
import com.violinjourney.app.feature.session.SessionContentMapper
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_preparing
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.finger_first
import com.violinjourney.app.shared.resources.finger_fourth
import com.violinjourney.app.shared.resources.finger_higher_position
import com.violinjourney.app.shared.resources.finger_open
import com.violinjourney.app.shared.resources.finger_second
import com.violinjourney.app.shared.resources.finger_third
import com.violinjourney.app.shared.resources.session_bias_mean
import com.violinjourney.app.shared.resources.session_note_duration
import com.violinjourney.app.shared.resources.session_note_duration_value
import com.violinjourney.app.shared.resources.session_note_min_max
import com.violinjourney.app.shared.resources.session_note_min_max_value
import com.violinjourney.app.shared.resources.session_note_on_string
import com.violinjourney.app.shared.resources.session_note_range
import com.violinjourney.app.shared.resources.session_note_range_value
import com.violinjourney.app.shared.resources.session_note_times_few
import com.violinjourney.app.shared.resources.session_note_times_many
import com.violinjourney.app.shared.resources.session_note_times_one
import com.violinjourney.app.shared.resources.session_tip_flat
import com.violinjourney.app.shared.resources.session_tip_flat_more
import com.violinjourney.app.shared.resources.session_tip_sharp
import com.violinjourney.app.shared.resources.session_tip_sharp_more
import com.violinjourney.app.shared.resources.session_tip_stable
import com.violinjourney.app.shared.resources.session_tip_wandering
import com.violinjourney.app.shared.resources.session_tip_wandering_more
import com.violinjourney.app.shared.resources.video_listen_place
import com.violinjourney.app.shared.resources.video_watch_place
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val TABULAR_FIGURES = "tnum"
private const val MS_PER_SECOND = 1_000.0

// The sheet of a note (spec 3.36.5, 5.29 R5).
private val NoteStyle = TextStyle(fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em)
private val MeanStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES)
private val NoteGap = 14.dp
private val MeanSign = 20.dp
private val MeanSignGap = 4.dp
private val TilesTop = 14.dp
private val TilesGap = 8.dp
private val TilePadding = 10.dp
private val TileValueTop = 2.dp
private const val TILES = 3

/** The labels of the tiles (5.29 R5): 12 sp, and a step smaller together where one of them would not stand whole in its tile. */
private const val TILE_LABEL_SP = 12f

/**
 * The least size of the labels of the tiles: Russian «длительность» stands whole at it in a tile of a sheet of 360 at the font 1.3
 * (≈ 81 dp for the word; 108 at 12 sp). Below it the label is cut with an ellipsis — no word is broken by the letter.
 */
private const val TILE_LABEL_MIN_SP = 8.5f
private val TileLabelLineHeight = 16.sp

/** The tiles are laid out in whole pixels: a label that fits only by a hair is not trusted. */
private val TileLabelSlack = 1.dp

/** Over the button of the sheet (5.29 R5: «кнопка — главная 56, сверху 16»), not the 18 of the sheets of R1. */
private val ButtonTop = 16.dp
private val AdviceTop = 14.dp
private val AdviceHorizontal = 14.dp
private val AdviceVertical = 12.dp
private val AdviceGap = 10.dp
private val AdviceSign = 20.dp
private val AdviceSignTop = 1.dp

/** The fields of the sheet (5.29 R5): 20 at the sides and at the bottom, the system inset on top of that. */
private val NoteSheetPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)

/**
 * The way from a note to its place in the recording (spec 3.19, 3.36.5): «Смотреть это место» of a video that shows its picture,
 * «Слушать это место» of sound — the one action of the sheet, its main button. [ready] false — the backing is still being made
 * (spec 5.25): the button is dimmed with «Готовим минусовку…» over it. A recording without sound has none.
 */
class NotePlace(val video: Boolean, val ready: Boolean, val onPlay: (index: Int) -> Unit)

/**
 * The details of one note (spec 3.10 п. 7, 3.36.5) in the frame of the sheets of R1, opened by a note of the roll and by a row of «Что
 * уходит»: [index] is the segment shown, null — no sheet. A swipe, a tap outside and «назад» only hide it ([onHide]); the outline on
 * the roll goes with it.
 */
@Composable
fun NoteSheet(index: Int?, segments: List<RollSegment>, nearCents: Int, place: NotePlace?, onHide: () -> Unit) {
    AppSheet(
        value = index?.takeIf { it in segments.indices },
        onHide = { onHide() },
        contentPadding = NoteSheetPadding,
        bottom = { shown -> if (place == null) null else ({ NoteSheetButtons(place, shown) }) },
    ) { shown ->
        segments.getOrNull(shown)?.let { NoteSheetContent(it, nearCents) }
    }
}

/**
 * «Слушать это место» / «Смотреть это место» at the bottom of the sheet: a second before the note, and it plays (spec 5.13); dimmed with
 * its reason while the backing is made.
 */
@Composable
fun NoteSheetButtons(place: NotePlace, index: Int) {
    AppSheetButtons(
        main = stringResource(if (place.video) Res.string.video_watch_place else Res.string.video_listen_place),
        onMain = { place.onPlay(index) },
        mainIcon = AppIcons.PlayCircle,
        mainEnabled = place.ready,
        mainReason = if (place.ready) null else stringResource(Res.string.backing_preparing),
        top = ButtonTop,
    )
}

/**
 * The note large and «в среднем −22 ц» in the colour of its zone with the arrow of its sign (a dot in tune) on one line; «на струне E
 * · 2-й палец · 3 раза за запись» under them; three tiles — «мин / макс», «длительность», «размах»; the advice on a plate with the
 * sign «инфо» in the colour of the zone, its first sentence bold. [nearCents] — the border of «рядом» the recording was made with, of
 * «Размах больше 20 ц». TalkBack hears the head as one heading: «F#5, в среднем −22 ц, на струне E, 2-й палец, 3 раза за запись».
 */
@Composable
fun NoteSheetContent(segment: RollSegment, nearCents: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val color = ViolinTheme.zoneColors.colorFor(segment.zone)
    val mean = stringResource(Res.string.session_bias_mean, Formats.signedCents(segment.meanCents))
    val where = listOf(
        stringResource(Res.string.session_note_on_string, segment.position.string.note.letter.toString()),
        stringResource(fingerRes(segment.position.finger)),
        stringResource(timesWords(segment.sameNoteCount), segment.sameNoteCount),
    )
    val spoken = (listOf(segment.note.name, mean) + where).joinToString(", ")
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.clearAndSetSemantics { heading(); contentDescription = spoken }) {
            Row(horizontalArrangement = Arrangement.spacedBy(NoteGap)) {
                Text(
                    text = segment.note.name,
                    modifier = Modifier.alignByBaseline(),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.displayMedium.merge(NoteStyle),
                )
                Row(Modifier.alignByBaseline(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MeanSignGap)) {
                    DeviationSign(segment.meanCents, level = segment.zone == Zone.IN_TUNE, color = color, size = MeanSign)
                    Text(mean, color = color, style = MaterialTheme.typography.titleMedium.merge(MeanStyle))
                }
            }
            Text(
                text = where.joinToString(stringResource(Res.string.dot_separator)),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        Tiles(
            tiles = listOf(
                stringResource(Res.string.session_note_min_max) to
                    stringResource(Res.string.session_note_min_max_value, Formats.signedCents(segment.minCents), Formats.signedCents(segment.maxCents)),
                stringResource(Res.string.session_note_duration) to
                    stringResource(Res.string.session_note_duration_value, Formats.oneDecimal((segment.endMs - segment.startMs) / MS_PER_SECOND)),
                // never «±20» beside «Размах больше 20 ц»: the tile says the swing as the advice judges it
                stringResource(Res.string.session_note_range) to
                    stringResource(Res.string.session_note_range_value, SessionContentMapper.halfRangeShown(segment, nearCents)),
            ),
            modifier = Modifier.padding(top = TilesTop),
        )
        Advice(segment, nearCents, color, Modifier.padding(top = AdviceTop))
    }
}

/** The advice of the note (spec 3.10 п. 7 — which one, by the same rules; 3.36.5 — on a plate, with its second sentence). */
@Composable
private fun Advice(segment: RollSegment, nearCents: Int, color: Color, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val first: String
    val more: String?
    when {
        segment.zone == Zone.IN_TUNE && !segment.steady -> {
            first = stringResource(Res.string.session_tip_wandering)
            more = stringResource(Res.string.session_tip_wandering_more, nearCents)
        }
        segment.zone == Zone.IN_TUNE -> {
            first = stringResource(Res.string.session_tip_stable)
            more = null
        }
        segment.meanCents < 0 -> {
            first = stringResource(Res.string.session_tip_flat)
            more = stringResource(Res.string.session_tip_flat_more)
        }
        else -> {
            first = stringResource(Res.string.session_tip_sharp)
            more = stringResource(Res.string.session_tip_sharp_more)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh, AppShapes.Control)
            .padding(horizontal = AdviceHorizontal, vertical = AdviceVertical),
        horizontalArrangement = Arrangement.spacedBy(AdviceGap),
    ) {
        AppIcon(AppIcons.Info, contentDescription = null, modifier = Modifier.padding(top = AdviceSignTop), tint = color, size = AdviceSign)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append(first) }
                if (more != null) {
                    append(' ')
                    append(more)
                }
            },
            modifier = Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        )
    }
}

/**
 * «мин / макс», «длительность», «размах» — three tiles of one width and one height (spec 3.36.5, 5.29 R5), each label whole on one
 * line: where one would not stand in its tile at 12 sp (Russian «длительность» on a phone of 360 — 83 dp in 81, at the font 1.3 108),
 * the three step down together by 0.5 sp to [TILE_LABEL_MIN_SP], as the labels of the tabs do; a word is never broken by the letter.
 */
@Composable
private fun Tiles(tiles: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.bodySmall.copy(
        fontSize = TILE_LABEL_SP.sp,
        lineHeight = TileLabelLineHeight,
        fontWeight = FontWeight.SemiBold,
        // no tracking of bodySmall: 0.4 sp a letter is 5 dp of «длительность»
        letterSpacing = 0.sp,
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val room = with(density) { ((maxWidth - TilesGap * (TILES - 1)) / TILES - TilePadding * 2 - TileLabelSlack).toPx() }
        val labels = tiles.map { it.first }
        val labelSp = remember(labels, base, room, measurer) {
            ButtonFit.size(room, TILE_LABEL_SP, TILE_LABEL_MIN_SP) { sizeSp ->
                labels.maxOf { measurer.measure(it, base.copy(fontSize = sizeSp.sp), softWrap = false, maxLines = 1).size.width.toFloat() }
            }
        }
        val labelStyle = base.copy(fontSize = labelSp.sp)
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TilesGap),
        ) {
            tiles.forEach { (label, value) -> Tile(label, value, labelStyle, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, labelStyle: TextStyle, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(colors.surface, AppShapes.Control)
            .padding(TilePadding),
    ) {
        Text(label, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = labelStyle)
        Text(
            text = value,
            modifier = Modifier.padding(top = TileValueTop),
            color = colors.onSurface,
            // «−31 / −12» at a large font goes on at its space, not off the tile
            maxLines = 2,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/** «3 раза за запись» in the words of the interface language (spec 3.26): Russian needs three forms. */
internal fun timesWords(count: Int): StringResource =
    Formats.plural(count, Res.string.session_note_times_one, Res.string.session_note_times_few, Res.string.session_note_times_many)

private fun fingerRes(finger: Finger): StringResource = when (finger) {
    Finger.OPEN -> Res.string.finger_open
    Finger.FIRST -> Res.string.finger_first
    Finger.SECOND -> Res.string.finger_second
    Finger.THIRD -> Res.string.finger_third
    Finger.FOURTH -> Res.string.finger_fourth
    Finger.HIGHER_POSITION -> Res.string.finger_higher_position
}
