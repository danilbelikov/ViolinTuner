package com.violinjourney.app.feature.history.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.eventKindWord
import com.violinjourney.app.feature.events.eventRecordTitle
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_take_mark
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.record_default_title
import com.violinjourney.app.shared.resources.record_tile_no_sound
import com.violinjourney.app.shared.resources.record_tile_only_video
import com.violinjourney.app.shared.resources.record_tile_record
import com.violinjourney.app.shared.resources.record_tile_sound
import com.violinjourney.app.shared.resources.record_tile_take
import com.violinjourney.app.shared.resources.selection_select
import com.violinjourney.app.shared.resources.session_default_title
import com.violinjourney.app.shared.resources.session_take_title
import com.violinjourney.app.shared.resources.take_best
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The card of a recording (spec 3.36.5, 5.29 R5; records.html 1).
private val CardMinHeight = 64.dp
private val CardRing = 1.5.dp
private val CardPaddingStart = 12.dp
private val CardPaddingVertical = 8.dp

/** At the end: 4 beside «⋯», whose target of 48 holds its icon of 24 in the middle; without it, as at the start. */
private val CardPaddingEndMenu = 4.dp
private val CardGap = 12.dp
private val TitleGap = 6.dp
private val LineTop = 2.dp
private val BestStar = 14.dp

/** The chevron of a card on the screen of an event, in the place of «⋯» (5.29 R9). */
private val ChevronSize = 24.dp

/** The sign of the backing in the line: as high as the words (13 sp gives 14, 5.29 R5), a gap of 5 after it, in the same scale. */
private val BackingSignSize = 1.08.em
private val BackingSignRoom = 1.46.em
private const val BACKING_SIGN = "backing"
private const val BEST_IN_MS = 150
private const val BEST_OUT_MS = 120
private const val BEST_FROM_SCALE = 0.6f
private const val HIGHLIGHT_FADE_MS = 1_500
private const val SELECT_FADE_MS = 150
private const val TILE_MORPH_MS = 200
private const val PRESS_MS = 100
private const val PRESSED_SCALE = 0.98f
private const val TABULAR_FIGURES = "tnum"

/** Between the words TalkBack hears of a card. */
private const val SAID_SEPARATOR = ", "

/**
 * Card of one recording under a date: the list of «Записи» (spec 3.11, 3.21, 3.36.5), «Записи этого дня» of the sheet of a day
 * on «Занятия» and the sheet «Слушать на…» of «Звук записей» ([RecordPlace.Sheet]; there the one listened on is [current]) — named by
 * its own name, its piece, or «Запись»; its line begins with the time of its start.
 */
@Composable
fun SessionCard(
    card: HistoryCard,
    zone: TimeZone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    place: RecordPlace = RecordPlace.Records,
    actions: CardActions? = null,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    current: Boolean = false,
) {
    RecordCard(
        card = card,
        title = recordCardTitle(card),
        // The date stands once, above the group (the day header of «Записи», the date of the sheet of a day): the card adds the time.
        // A reader going from card to card skips the headers, so to it the card says its date as well (spec 3.21).
        start = Formats.timeOfDay(card.startedAtEpochMs, zone),
        spokenDate = Formats.recordDate(card.date, card.otherYear),
        onClick = onClick,
        modifier = modifier,
        place = place,
        actions = actions,
        selected = selected,
        onLongClick = onLongClick,
        current = current,
    )
}

/**
 * What the card of a recording is called (spec 3.21, 3.35): its own name; else, of a recording of an event, the event and its date —
 * «Осенний концерт · 24 октября»; else its piece; else «Запись».
 */
@Composable
fun recordCardTitle(card: HistoryCard): String =
    card.title ?: card.event?.let { eventRecordTitle(it) } ?: card.pieceTitle ?: stringResource(Res.string.record_default_title)

/**
 * The one card of a recording — in «Записи», in «Записи этого дня» of «Занятия», a take on the screen of its piece, a recording on
 * the screen of its event (spec 3.21, 3.36.5, 3.36.9; which list — [place]). Quiet on purpose: a tile of one colour — of a video, a
 * frame of it (spec 3.38) — a title, one line — no score, no zone, no bars. A take marked as the best carries a star after its title;
 * [highlighted] belongs to a take recorded a moment ago.
 *
 * The line: [start] — the time of the start, or the date of a take with a name of its own — and the length, then what the recording
 * is ([RecordLine.wordsOf]): «18:42 · 2:05 · дубль», «· видео», of a recording of an event the word of its kind — «19:02 · 3:40 ·
 * выступление», its name as written for a kind of one's own (spec 3.36.9) — «· без звука» after the kind, the sign of the backing and «под
 * минусовку» last. One line: the words end in an ellipsis first, the length is never cut, the start gives way only to it.
 *
 * [selected] is null outside the selection mode (spec 3.18). Inside it the card is a checkbox: the tile turns into a mark of the same
 * shape, a tap picks instead of opening, «⋯» is gone. [onLongClick] is how the mode is entered from a card; the press itself shrinks
 * the card a little. «⋯» ([actions]) stands at every card of a list that has them, so the edge of the list is even; «Записи этого
 * дня» has none.
 *
 * TalkBack hears the card as one description: its kind — what its tile is, «звук» of the sound of an event — «лучший», the title,
 * [spokenDate] (for a list that writes the date above its cards, not on them), the time, the length, the word of the kind of its event,
 * «под минусовку», «без звука» — a word seen in the line is not heard twice; «⋯» is a button of its own, «Ещё».
 *
 * [current] — the one chosen where the card is only picked (the sheet «Слушать на…» of «Звук записей», spec 3.36.5): the outline of
 * 1.5 in the accent, without the fill of a fresh take; TalkBack hears it «выбрано».
 */
@Composable
fun RecordCard(
    card: HistoryCard,
    title: String,
    start: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    place: RecordPlace = RecordPlace.Records,
    actions: CardActions? = null,
    highlighted: Boolean = false,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    spokenDate: String? = null,
    current: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = AppShapes.M
    val selecting = selected != null
    // «⋯» is gone while picking: a tap picks, and the bin is up in the bar
    val menu = actions?.takeUnless { selecting }
    val length = Formats.duration(card.durationMs)
    val backingWords = stringResource(Res.string.backing_take_mark)
    val noSoundWords = stringResource(Res.string.record_tile_no_sound)
    // «выступление», «урок», the name of a kind of one's own as written (spec 3.36.9)
    val eventWord = card.event?.let { eventKindWord(it.kind, it.ownName) }
    val said = listOfNotNull(
        stringResource(spokenKindOf(RecordLine.kindOf(card))),
        stringResource(Res.string.take_best).takeIf { card.best },
        title,
        spokenDate,
        start,
        length,
        // in every list alike, as the kind: on the screen of its event too, where the line does not show it
        eventWord,
        backingWords.takeIf { card.underBacking },
        noSoundWords.takeIf { !card.hasAudio },
    ).joinToString(SAID_SEPARATOR)
    // A picked card looks like a fresh take (the handoff gives both the same fill and ring), only it gets there faster.
    val lit = if (selecting) selected == true else highlighted
    val ground = if (place == RecordPlace.Sheet) colors.surface else colors.surfaceContainer
    val fade = tween<Color>(if (selecting) SELECT_FADE_MS else HIGHLIGHT_FADE_MS)
    val background by animateColorAsState(if (lit) ViolinTheme.repertoireColors.takeNew else ground, fade, label = "recordBackground")
    val ring by animateColorAsState(if (lit || current) colors.primary else colors.primary.copy(alpha = 0f), fade, label = "recordRing")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onLongClick != null && !selecting) PRESSED_SCALE else 1f, tween(PRESS_MS), label = "recordPress")
    val longClickLabel = stringResource(Res.string.selection_select)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(CardRing, ring, shape)
            .then(
                if (selected != null) {
                    Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onLongClickLabel = longClickLabel.takeIf { onLongClick != null },
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                },
            )
            .semantics {
                contentDescription = said
                if (current) this.selected = true
            }
            .padding(
                start = CardPaddingStart,
                top = CardPaddingVertical,
                bottom = CardPaddingVertical,
                end = if (menu != null) CardPaddingEndMenu else CardPaddingStart,
            ),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // What is seen is said once, by the card: the tile, the title and the line say nothing of their own.
        Row(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(CardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(targetState = selected, animationSpec = tween(TILE_MORPH_MS), label = "recordTile") { mark ->
                if (mark == null) RecordTile(hasAudio = card.hasAudio, hasVideo = card.hasVideo, thumbPath = card.thumbPath) else SelectionMark(mark)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TitleGap), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f, fill = false),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                    )
                    // A star, not a chip with a word: in a quiet list one accent mark is seen at once; not on the tile, which turns into a checkbox.
                    AnimatedVisibility(visible = card.best, enter = fadeIn(tween(BEST_IN_MS)) + scaleIn(tween(BEST_IN_MS), initialScale = BEST_FROM_SCALE), exit = fadeOut(tween(BEST_OUT_MS))) {
                        AppIcon(AppIcons.Star, contentDescription = null, tint = colors.primary, size = BestStar)
                    }
                }
                RecordLineText(card, place, start, length, noSoundWords, backingWords, eventWord, Modifier.padding(top = LineTop))
            }
        }
        if (menu != null) CardMenuButton(card, menu)
        // here a recording is only opened (spec 3.36.9): the chevron says so where «⋯» stands in other lists
        if (place == RecordPlace.Event && !selecting) {
            AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = ViolinTheme.textTertiary, size = ChevronSize)
        }
    }
}

/**
 * «18:42 · 2:05 · дубль · без звука · [знак] под минусовку» (spec 3.36.5, 5.29 R5) — one line, 13 sp, the figures tabular, in three
 * pieces laid side by side by [RecordLinePolicy]: the length « · 2:05» is never cut; the start — the time, or the date of a take with a
 * name of its own — takes what is left beside it, and ends in an ellipsis only when the two cannot stand whole together (a date with
 * its year in German or Spanish on 360 at a large font); the words ([RecordLine.wordsOf], in their order) take what is left after both
 * and are the first to end in an ellipsis. The words of the kind are those TalkBack heard on the tile before; [eventWord] — the word of
 * the kind of the event of a recording of one (spec 3.36.9), heard after the length.
 */
@Composable
private fun RecordLineText(
    card: HistoryCard,
    place: RecordPlace,
    start: String,
    length: String,
    noSound: String,
    backing: String,
    eventWord: String?,
    modifier: Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES)
    val separator = stringResource(Res.string.dot_separator)
    val take = stringResource(Res.string.record_tile_take)
    val video = stringResource(Res.string.record_tile_only_video)
    val words = RecordLine.wordsOf(card, place)
    Layout(
        content = {
            Text(start, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style)
            Text(separator + length, color = color, maxLines = 1, softWrap = false, style = style)
            if (words.isNotEmpty()) {
                val tail = buildAnnotatedString {
                    words.forEach { word ->
                        append(separator)
                        when (word) {
                            LineWord.TAKE -> append(take)
                            LineWord.VIDEO -> append(video)
                            LineWord.NO_SOUND -> append(noSound)
                            LineWord.EVENT_KIND -> append(eventWord.orEmpty())
                            LineWord.BACKING -> {
                                appendInlineContent(BACKING_SIGN)
                                append(backing)
                            }
                        }
                    }
                }
                Text(
                    text = tail,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = if (LineWord.BACKING in words) backingSign(color) else emptyMap(),
                    style = style,
                )
            }
        },
        modifier = modifier,
        measurePolicy = RecordLinePolicy,
    )
}

/** The sign of the backing inside the words of the line: as high as the words, a gap after it (5.29 R5). */
private fun backingSign(color: Color): Map<String, InlineTextContent> = mapOf(
    BACKING_SIGN to InlineTextContent(Placeholder(BackingSignRoom, BackingSignSize, PlaceholderVerticalAlign.TextCenter)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            AppIcon(AppIcons.Backing, contentDescription = null, modifier = Modifier.fillMaxHeight().aspectRatio(1f), tint = color)
        }
    },
)

/** The pieces of [RecordLineText], in the order they are composed and stand. */
private const val LINE_START = 0
private const val LINE_LENGTH = 1
private const val LINE_WORDS = 2

/**
 * The line of a card on one baseline: the length is measured first, with the whole width, and is never cut; the start gets what is
 * left beside it; the words — what is left after both.
 */
private object RecordLinePolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val length = measurables[LINE_LENGTH].measure(loose)
        val start = measurables[LINE_START].measure(loose.copy(maxWidth = loose.maxWidth.less(length.width)))
        val words = measurables.getOrNull(LINE_WORDS)?.measure(loose.copy(maxWidth = loose.maxWidth.less(start.width + length.width)))
        val pieces = listOfNotNull(start, length, words)
        val baselines = pieces.map { piece -> piece[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: piece.height }
        val baseline = baselines.max()
        val height = pieces.indices.maxOf { baseline - baselines[it] + pieces[it].height }
        return layout(constraints.constrainWidth(pieces.sumOf { it.width }), constraints.constrainHeight(height)) {
            var x = 0
            pieces.forEachIndexed { index, piece ->
                piece.placeRelative(x, baseline - baselines[index])
                x += piece.width
            }
        }
    }
}

/** What is left of this width after [taken]; an unbounded width stays unbounded. */
private fun Int.less(taken: Int): Int = if (this == Constraints.Infinity) this else (this - taken).coerceAtLeast(0)

/** The first word TalkBack hears of a card. */
private fun spokenKindOf(kind: RecordKind): StringResource = when (kind) {
    RecordKind.VIDEO -> Res.string.record_tile_only_video
    RecordKind.TAKE -> Res.string.record_tile_take
    RecordKind.RECORD -> Res.string.record_tile_record
    RecordKind.SOUND -> Res.string.record_tile_sound
}

/**
 * What a recording is called where no date stands beside it — on its own screen and in the name
 * of the file that is shared (spec 3.21): its own name when it was given one; otherwise a recording of
 * an event is named after the event and its date — «Осенний концерт · 24 октября» (spec 3.35) — a take
 * after its piece — «Менуэт · 18 сентября» — and a free recording is «Запись · …». The cards of the
 * lists leave the date out: there it stands once, above them or in their line.
 */
@Composable
fun sessionTitle(title: String?, pieceTitle: String?, startedAtEpochMs: Long, zone: TimeZone, event: SessionEvent? = null): String {
    val date = Formats.dayAndMonth(startedAtEpochMs, zone)
    return title
        ?: event?.let { eventRecordTitle(it) }
        ?: pieceTitle?.let { stringResource(Res.string.session_take_title, it, date) }
        ?: stringResource(Res.string.session_default_title, date)
}
