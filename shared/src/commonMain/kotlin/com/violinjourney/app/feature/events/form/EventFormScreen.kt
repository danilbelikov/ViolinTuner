package com.violinjourney.app.feature.events.form

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.text.codePointLength
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppField
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.components.DiscardDialog
import com.violinjourney.app.core.ui.components.DiscardLoss
import com.violinjourney.app.core.ui.components.SegmentedSwitch
import com.violinjourney.app.core.ui.components.TypedLine
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.components.arrivalPresses
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.builtInKindName
import com.violinjourney.app.feature.events.eventKindName
import com.violinjourney.app.feature.events.eventNameOf
import com.violinjourney.app.feature.events.kindIcon
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.feature.practice.components.dashedFrame
import com.violinjourney.app.feature.repertoire.form.ChoiceRow
import com.violinjourney.app.feature.repertoire.form.FormCaption
import com.violinjourney.app.feature.repertoire.form.FormDock
import com.violinjourney.app.feature.repertoire.form.FormFrame
import com.violinjourney.app.feature.repertoire.form.FormSide
import com.violinjourney.app.feature.repertoire.form.NOTES_MIN_LINES
import com.violinjourney.app.feature.repertoire.form.captionOf
import com.violinjourney.app.feature.repertoire.form.cut
import com.violinjourney.app.feature.repertoire.form.riseWhenFocused
import com.violinjourney.app.feature.repertoire.piece.Sentences
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.event_count_few
import com.violinjourney.app.shared.resources.event_count_many
import com.violinjourney.app.shared.resources.event_count_one
import com.violinjourney.app.shared.resources.event_duration_none
import com.violinjourney.app.shared.resources.event_duration_other
import com.violinjourney.app.shared.resources.event_end_next_day
import com.violinjourney.app.shared.resources.event_field_date
import com.violinjourney.app.shared.resources.event_field_duration
import com.violinjourney.app.shared.resources.event_field_place
import com.violinjourney.app.shared.resources.event_field_repeat
import com.violinjourney.app.shared.resources.event_field_teacher
import com.violinjourney.app.shared.resources.event_field_time
import com.violinjourney.app.shared.resources.event_field_title
import com.violinjourney.app.shared.resources.event_form_new
import com.violinjourney.app.shared.resources.event_form_what
import com.violinjourney.app.shared.resources.event_kind_color_of
import com.violinjourney.app.shared.resources.event_kind_delete_keeps
import com.violinjourney.app.shared.resources.event_kind_delete_text
import com.violinjourney.app.shared.resources.event_kind_delete_text_empty
import com.violinjourney.app.shared.resources.event_kind_delete_title
import com.violinjourney.app.shared.resources.event_kind_edit_of
import com.violinjourney.app.shared.resources.event_kind_own
import com.violinjourney.app.shared.resources.event_kind_tile_said
import com.violinjourney.app.shared.resources.event_kinds_full
import com.violinjourney.app.shared.resources.event_lessons_few
import com.violinjourney.app.shared.resources.event_lessons_many
import com.violinjourney.app.shared.resources.event_lessons_one
import com.violinjourney.app.shared.resources.event_place_hint
import com.violinjourney.app.shared.resources.event_rehearsals_few
import com.violinjourney.app.shared.resources.event_rehearsals_many
import com.violinjourney.app.shared.resources.event_rehearsals_one
import com.violinjourney.app.shared.resources.event_repeat_biweekly
import com.violinjourney.app.shared.resources.event_repeat_no_end
import com.violinjourney.app.shared.resources.event_repeat_none
import com.violinjourney.app.shared.resources.event_repeat_until_link
import com.violinjourney.app.shared.resources.event_repeat_weekly
import com.violinjourney.app.shared.resources.event_sheet_until
import com.violinjourney.app.shared.resources.event_teacher_hint
import com.violinjourney.app.shared.resources.event_until_date
import com.violinjourney.app.shared.resources.event_until_time
import com.violinjourney.app.shared.resources.event_weekdays_on
import com.violinjourney.app.shared.resources.field_counter_said_few
import com.violinjourney.app.shared.resources.field_counter_said_many
import com.violinjourney.app.shared.resources.field_counter_said_one
import com.violinjourney.app.shared.resources.form_optional
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_field_notes_placeholder
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.practice_save
import com.violinjourney.app.shared.resources.profile_name_counter
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private const val TABULAR_FIGURES = "tnum"

/**
 * The form of an event (spec 3.35, 3.36.9; events-form.html 1, practice-sheets.html 10): a screen above the tabs on the frame of the forms
 * of R4 ([FormFrame]) — ✕ and «Новое событие» (in an edit, the name of the kind), one column no wider than 560 and «Сохранить» in the bottom
 * zone over the keyboard, never asleep: every field has a default or may stay empty. Top down: the tiles of the kinds, the row of the kind,
 * the date and the time, the length (not of «весь день»), the repeat and its line, the teacher of a lesson or the place of anything else,
 * the title, the notes of an edit. The sheets are [EventFormSheetHost]; «Не сохранять?» and «Удалить вид?» are the dialogs of R1.
 * Stateless.
 */
@Composable
fun EventFormScreen(state: EventFormState, onIntent: (EventFormIntent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onIntent(EventFormIntent.CloseClicked) }
    // the form comes in under the finger that opened it: its «Сохранить» stands where «Добавить выступление» of «Выступления» and the main
    // button of a sheet of a day stood, and the second tap of a double tap would store an event nobody filled in (5.29 R9, review of
    // stage 99) — not heard for the time of a double tap from the first frame of the form
    val onSave = arrivalPresses(onIntent)
    FormFrame(
        // an edit being read has no kind yet: the placeholder's «Урок» would blink into the kind of the event (spec 3.36.9: «шапка и
        // пустая нижняя зона», review of stage 98б)
        title = when {
            state.isNew -> stringResource(Res.string.event_form_new)
            state.loading -> ""
            else -> eventKindName(state.kind)
        },
        loading = state.loading,
        onClose = { onIntent(EventFormIntent.CloseClicked) },
        dock = { mode -> FormDock(mode, stringResource(Res.string.practice_save), onClick = { onSave(EventFormIntent.SaveClicked) }) },
        modifier = modifier,
    ) { Fields(state, onIntent) }
    EventFormSheetHost(state, onIntent)
    when (val dialog = state.dialog) {
        EventFormDialog.Discard -> DiscardDialog(
            loss = DiscardLoss.of(state.isNew, state.savedName?.let { eventNameOf(it) }),
            onDiscard = { onIntent(EventFormIntent.DialogConfirmed) },
            onBack = { onIntent(EventFormIntent.DialogDismissed) },
        )
        is EventFormDialog.DeleteKind -> DeleteDialog(
            title = stringResource(Res.string.event_kind_delete_title),
            text = if (dialog.events == 0) {
                stringResource(Res.string.event_kind_delete_text_empty, dialog.name)
            } else {
                Sentences.join(
                    stringResource(Res.string.event_kind_delete_text, dialog.name, dialog.events, stringResource(builtInKindName(BuiltInKind.OTHER))),
                    stringResource(Res.string.event_kind_delete_keeps),
                )
            },
            onConfirm = { onIntent(EventFormIntent.DialogConfirmed) },
            onDismiss = { onIntent(EventFormIntent.DialogDismissed) },
        )
        null -> Unit
    }
}

@Composable
private fun ColumnScope.Fields(state: EventFormState, onIntent: (EventFormIntent) -> Unit) {
    val draft = state.draft
    val focus = LocalFocusManager.current
    // a sheet over a keyboard would stand under it: every row that opens one lets the keyboard go first
    val open: (EventFormIntent) -> Unit = { intent ->
        focus.clearFocus()
        onIntent(intent)
    }
    KindPart(state, open)
    DateTimeRows(state, open)
    if (draft.startMinutes != null) DurationPart(state, onIntent, open)
    RepeatPart(state, onIntent, open)
    TextFields(state, onIntent)
}

/**
 * «Что это» (spec 3.36.9): the tiles of the kinds in a row that scrolls sideways to the edges of the screen — the four built-in ones, then
 * one's own by the alphabet, last the dashed «Свой вид», or «Своих видов — 20 из 20» in its place; under them the row of the kind: «Цвет
 * вида „Урок“» of a built-in kind, «Изменить вид „Сольфеджио“» of one's own — the sheet «Вид», as the second tap of the chosen tile is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindPart(state: EventFormState, onIntent: (EventFormIntent) -> Unit) {
    FormCaption(captionOf(stringResource(Res.string.event_form_what))) {
        val side = FormSide
        val scroll = rememberScrollState()
        KeepChosenInSight(scroll, state.kinds.indexOfFirst { it.ref == state.kind.ref })
        Row(
            modifier = Modifier
                // to the edges of the screen: the column keeps 16 at its sides, the row takes them back and scrolls inside them
                .layout { measurable, constraints ->
                    val extra = (side * 2).roundToPx()
                    val wide = constraints.maxWidth + extra
                    val placeable = measurable.measure(Constraints(minWidth = wide, maxWidth = wide, minHeight = constraints.minHeight, maxHeight = constraints.maxHeight))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
                }
                .horizontalScroll(scroll)
                .selectableGroup()
                .padding(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(EventsDimens.TileGap),
        ) {
            val chosen = state.kind.ref
            state.kinds.forEach { kind -> KindTile(kind, selected = kind.ref == chosen) { onIntent(EventFormIntent.KindPicked(kind.ref)) } }
            if (state.canAddOwn) {
                OwnKindTile { onIntent(EventFormIntent.OwnKindClicked) }
            } else {
                Text(
                    text = stringResource(Res.string.event_kinds_full, state.ownKinds, state.maxOwnKinds),
                    modifier = Modifier.align(Alignment.CenterVertically).width(EventsDimens.TileSize * 2),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.KindsFullText),
                )
            }
        }
        KindRow(state.kind) { onIntent(EventFormIntent.KindRowClicked) }
    }
}

/**
 * The chosen tile in sight (review of stage 98б): a kind of one's own is the fifth tile and further — past the edge of a phone of 360 —
 * and is the kind of an edit of its event, of a new form after one (D28), of the form after «Готово» of a new kind; «Другое» after the
 * kind chosen is deleted may be behind the left edge. When the form opens, when the kind changes and when the row changes its width, the
 * row goes — at once: the form has no motion of its own (5.29 R9) — to where the tile stands whole, 16 from the edge that cut it; a tile
 * standing whole does not move it.
 */
@Composable
private fun KeepChosenInSight(scroll: ScrollState, index: Int) {
    val density = LocalDensity.current
    LaunchedEffect(scroll, index, density) {
        if (index < 0) return@LaunchedEffect
        snapshotFlow { scroll.viewportSize }.filter { it > 0 }.collect { viewport ->
            val target = with(density) {
                KindTiles.scrollToShow(index, EventsDimens.TileSize.roundToPx(), EventsDimens.TileGap.roundToPx(), FormSide.roundToPx(), viewport, scroll.value)
            }
            if (target != null) scroll.scrollTo(target)
        }
    }
}

/**
 * A tile of a kind (5.29 R9): 80 wide and as high as its name needs, at least 80; the sign 24 in the colour it has on its plate and the
 * name of 12 sp / 700 on two lines at most, smaller down to 10 rather than broken inside a word. The chosen one — a frame of 2 in the
 * colour of the kind over its tone at 12 %, the name in the first level of text. A radio button for TalkBack: «Урок, вид, выбрано».
 */
@Composable
private fun KindTile(kind: EventKind, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val events = ViolinTheme.eventsColors
    val look = kind.look
    val name = eventKindName(kind)
    val said = stringResource(Res.string.event_kind_tile_said, name)
    val shape = RoundedCornerShape(EventsDimens.TileCorner)
    val ground = if (selected) events.tile(look.color, colors.surfaceContainer) else colors.surfaceContainer
    Column(
        modifier = Modifier
            .width(EventsDimens.TileSize)
            .heightIn(min = EventsDimens.TileSize)
            .clip(shape)
            .background(ground)
            .border(EventsDimens.TileBorder, if (selected) events.of(look.color).color else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(EventsDimens.TileBorder),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EventsDimens.TileSignGap, Alignment.CenterVertically),
    ) {
        AppIcon(kindIcon(look.sign), contentDescription = null, size = EventsDimens.TileSign, tint = events.of(look.color).onPlate)
        // the name is said as «Урок, вид» — merged into the tile, which says the choice and takes the press
        TileName(name, if (selected) colors.onSurface else colors.onSurfaceVariant, Modifier.semantics { contentDescription = said })
    }
}

/** «Свой вид» (5.29 R9): a dashed frame of 2 without a ground, the plus 24 in the second level of text. */
@Composable
private fun OwnKindTile(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(EventsDimens.TileCorner)
    Column(
        modifier = Modifier
            .width(EventsDimens.TileSize)
            .heightIn(min = EventsDimens.TileSize)
            .clip(shape)
            .dashedFrame(colors.outlineVariant, EventsDimens.TileCorner, EventsDimens.TileDash)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(EventsDimens.TileBorder),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EventsDimens.TileSignGap, Alignment.CenterVertically),
    ) {
        AppIcon(AppIcons.Plus, contentDescription = null, size = EventsDimens.TilePlus, tint = colors.onSurfaceVariant)
        TileName(stringResource(Res.string.event_kind_own), colors.onSurfaceVariant)
    }
}

/**
 * The name on a tile: 12 sp / 700 in the middle, two lines at most, smaller by half a sp at a time where a word would break, down to 10
 * dp — at a large font too: 10 sp at 1.3 would not hold «Выступление» on 76 (spec 3.36.9: «слово не режется»).
 */
@Composable
private fun TileName(text: String, color: Color, modifier: Modifier = Modifier) {
    val least = with(LocalDensity.current) { EventsDimens.TileTextLeast.toSp() }.value
    val most = EventsDimens.TileText.value
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = EventsDimens.TILE_TEXT_LINES,
        autoSize = remember(most, least) { WholeWordsFit(most, minOf(least, most)) },
        style = MaterialTheme.typography.labelMedium.copy(fontSize = EventsDimens.TileText, lineHeight = EventsDimens.TILE_TEXT_LINE.em, fontWeight = FontWeight.Bold),
    )
}

/** The row of the kind (5.29 R9): 48, the pencil 18, «Цвет вида „Урок“» 14 sp / 700 with the name in the first level of text, the chevron. */
@Composable
private fun KindRow(kind: EventKind, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = eventKindName(kind)
    val words = stringResource(if (kind.ref is KindRef.Custom) Res.string.event_kind_edit_of else Res.string.event_kind_color_of, name)
    val text = remember(words, name, colors.onSurface) {
        buildAnnotatedString {
            append(words)
            val at = words.indexOf(name)
            if (at >= 0) addStyle(SpanStyle(color = colors.onSurface), at, at + name.length)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EventsDimens.KindRowMin)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.KindRowGap),
    ) {
        AppIcon(AppIcons.Pencil, contentDescription = null, size = EventsDimens.KindRowIcon, tint = colors.onSurfaceVariant)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = EventsDimens.KindRowText, lineHeight = EventsDimens.KindRowTextHeight, fontWeight = FontWeight.Bold),
        )
        AppIcon(AppIcons.ChevronRight, contentDescription = null, size = EventsDimens.KindRowIcon, tint = ViolinTheme.textTertiary)
    }
}

/**
 * The date and the time (spec 3.36.9): two rows of choice of R4 without captions — «Пн, 28 сент.» with the calendar, «17:00» or «Весь
 * день» in the accent with the clock — side by side 8 apart, their widths 1.2 : 1; where either value does not fit its share whole, one under
 * the other. TalkBack: «Дата, понедельник, 28 сентября», «Время, 17:00».
 */
@Composable
private fun DateTimeRows(state: EventFormState, onIntent: (EventFormIntent) -> Unit) {
    val draft = state.draft
    val time = draft.startMinutes?.let { Formats.clockOf(it) }
    val allDay = stringResource(Res.string.event_all_day).replaceFirstChar { it.titlecase() }
    val dateLabel = stringResource(Res.string.event_field_date)
    val timeLabel = stringResource(Res.string.event_field_time)
    val date = @Composable {
        ChoiceRow(
            label = null,
            value = Formats.weekdayCommaDate(draft.date),
            onClick = { onIntent(EventFormIntent.DateRowClicked) },
            trailing = AppIcons.Calendar,
            trailingSize = EventsDimens.DateTimeIcon,
            description = stringResource(Res.string.practice_pair_description, dateLabel, Formats.weekdayFullDate(draft.date)),
        )
    }
    val clock = @Composable {
        ChoiceRow(
            label = null,
            value = time ?: allDay,
            onClick = { onIntent(EventFormIntent.TimeRowClicked) },
            trailing = AppIcons.Clock,
            trailingSize = EventsDimens.DateTimeIcon,
            // the value of the time in the accent — «Весь день» as well (events-form.html 1, 3: the class `val` of both)
            valueStyle = TextStyle(color = MaterialTheme.colorScheme.primary, fontFeatureSettings = TABULAR_FIGURES),
            description = stringResource(Res.string.practice_pair_description, timeLabel, time ?: stringResource(Res.string.event_all_day)),
        )
    }
    val gap = EventsDimens.DateTimeGap
    Layout(contents = listOf(date, clock), modifier = Modifier.fillMaxWidth()) { (dates, clocks), constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val shares = EventsDimens.DATE_SHARE + EventsDimens.TIME_SHARE
        val dateWidth = ((width - gapPx) * EventsDimens.DATE_SHARE / shares).toInt()
        val timeWidth = width - gapPx - dateWidth
        val dateRow = dates.first()
        val clockRow = clocks.first()
        // side by side only where each value stands whole on its line in its share
        val beside = dateRow.maxIntrinsicWidth(Constraints.Infinity) <= dateWidth && clockRow.maxIntrinsicWidth(Constraints.Infinity) <= timeWidth
        if (beside) {
            val d = dateRow.measure(Constraints.fixedWidth(dateWidth))
            val c = clockRow.measure(Constraints.fixedWidth(timeWidth))
            val height = maxOf(d.height, c.height)
            layout(width, height) {
                d.place(0, 0)
                c.place(dateWidth + gapPx, 0)
            }
        } else {
            val d = dateRow.measure(Constraints.fixedWidth(width))
            val c = clockRow.measure(Constraints.fixedWidth(width))
            layout(width, d.height + gapPx + c.height) {
                d.place(0, 0)
                c.place(0, d.height + gapPx)
            }
        }
    }
}

/**
 * «Длительность» (spec 3.36.9): its caption with the end at its right — «до 17:45», after midnight «до 01:00, вс 25 окт.»; the chips of
 * choice «30 мин · 45 мин · 1 ч · 1,5 ч», and in a row under them «Другая…» with the pencil — or the length of one's own on a chip with the
 * pencil, chosen; both open the sheet «Длительность» — and «Без длительности». The caption is measured first and stands whole; the end
 * takes the rest of the row, at its right, and wraps there by its words (320 at 1.3 past midnight: «до 01:00, / вт 29 сент.»).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPart(state: EventFormState, onIntent: (EventFormIntent) -> Unit, open: (EventFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val length = state.draft.durationMinutes
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EventsDimens.EndGap)) {
            Text(
                text = stringResource(Res.string.event_field_duration),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = EventsDimens.EndText, fontWeight = FontWeight.Bold),
            )
            state.end?.let { end ->
                Text(
                    text = endWords(end),
                    modifier = Modifier.weight(1f),
                    color = colors.onSurface,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = EventsDimens.EndText, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
        Spacer(Modifier.height(EventsDimens.SheetValueTop))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
            verticalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
        ) {
            state.quickDurations.forEach { minutes ->
                AppChip.Choice(text = Formats.quickDuration(minutes), selected = length == minutes, onClick = { onIntent(EventFormIntent.DurationPicked(minutes)) })
            }
        }
        Spacer(Modifier.height(EventsDimens.ChoiceGap))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
            verticalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
        ) {
            val own = length != null && length !in state.quickDurations
            AppChip.Choice(
                text = if (own) Formats.quickDuration(length) else stringResource(Res.string.event_duration_other),
                selected = own,
                onClick = { open(EventFormIntent.DurationOtherClicked) },
                icon = AppIcons.Pencil,
            )
            AppChip.Choice(
                text = stringResource(Res.string.event_duration_none),
                selected = length == null,
                onClick = { onIntent(EventFormIntent.DurationPicked(null)) },
            )
        }
    }
}

/** «до 17:45», and past midnight with the day it ends on — «до 01:00, вс 25 окт.». */
@Composable
internal fun endWords(end: FormEnd): String {
    val clock = Formats.clockOf(end.minutes)
    return end.nextDay?.let { stringResource(Res.string.event_end_next_day, clock, Formats.weekdayDate(it)) }
        ?: stringResource(Res.string.event_until_time, clock)
}

/**
 * «Повтор» (spec 3.36.9): the switch of R1 «Не повторять · Каждую неделю · Раз в две недели», its words whole on two lines at most; under it
 * the line of the repeat — «по понедельникам · без конца · до…», with an end «по понедельникам · до 31 дек. · 14 уроков», the end a link to
 * «Повторять до» (700, the accent, a touch of 48). In an edit of an event of a repeat the line says the repeat as it is, without a link.
 */
@Composable
private fun RepeatPart(state: EventFormState, onIntent: (EventFormIntent) -> Unit, open: (EventFormIntent) -> Unit) {
    val caption = stringResource(Res.string.event_field_repeat)
    val repeats = listOf(Repeat.NONE, Repeat.WEEKLY, Repeat.BIWEEKLY)
    FormCaption(captionOf(caption)) {
        SegmentedSwitch(
            labels = listOf(
                stringResource(Res.string.event_repeat_none), stringResource(Res.string.event_repeat_weekly), stringResource(Res.string.event_repeat_biweekly),
            ),
            selectedIndex = repeats.indexOf(state.draft.repeat),
            onSelect = { onIntent(EventFormIntent.RepeatSelected(repeats[it])) },
            wholeWords = true,
            description = caption,
        )
        state.summary?.let { RepeatLine(it, link = !state.inSeries) { open(EventFormIntent.UntilClicked) } }
    }
}

/**
 * The line of the repeat: whole sentences of the language, the end a link where it may be changed. One text (review of stage 98б): it
 * wraps by its words as one sentence — de at 1.3 on 360, «montags · bis 31. Dez. · 14 Unterrichtsstunden» — and stands 8 under the switch,
 * as the line of an edit does; the link is a span of it, its touch the 48 Compose gives a small target without growing the line. A reader
 * hears the line as the line of an edit says it — «по понедельникам · без конца» — a button that opens «Повторять до».
 */
@Composable
private fun RepeatLine(summary: RepeatSummary, link: Boolean, onUntil: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val separator = stringResource(Res.string.dot_separator)
    val days = stringArrayResource(Res.array.event_weekdays_on)
    val day = days.getOrElse(summary.weekday.ordinal) { days.first() }
    val until = summary.until
    val end = if (until == null) stringResource(Res.string.event_repeat_no_end) else stringResource(Res.string.event_until_date, Formats.shortDate(until))
    val count = summary.count?.let { countWords(it, summary.word) }
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.SummaryText, lineHeight = EventsDimens.SummaryTextHeight)
    val plain = listOfNotNull(day, end, count).joinToString(separator)
    if (!link) {
        Text(text = plain, modifier = Modifier.padding(top = EventsDimens.SummaryTop), color = colors.onSurfaceVariant, style = style)
        return
    }
    val linkWords = if (until == null) stringResource(Res.string.event_repeat_until_link) else end
    val before = if (until == null) day + separator + end + separator else day + separator
    val after = count?.let { separator + it }
    val sheet = stringResource(Res.string.event_sheet_until)
    val currentOnUntil by rememberUpdatedState(onUntil)
    val accent = colors.primary
    val text = remember(before, linkWords, after, accent) {
        buildAnnotatedString {
            append(before)
            withLink(LinkAnnotation.Clickable(UNTIL_LINK, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold))) { currentOnUntil() }) {
                append(linkWords)
            }
            if (after != null) append(after)
        }
    }
    Text(
        text = text,
        modifier = Modifier.padding(top = EventsDimens.SummaryTop).semantics {
            contentDescription = plain
            role = Role.Button
            onClick(label = sheet) {
                currentOnUntil()
                true
            }
        },
        color = colors.onSurfaceVariant,
        style = style,
    )
}

/** The tag of the link «до…» in the line of the repeat. */
private const val UNTIL_LINK = "until"

/** «14 уроков», «7 репетиций», «12 событий». */
@Composable
private fun countWords(count: Int, word: SeriesWord): String {
    val forms: Triple<StringResource, StringResource, StringResource> = when (word) {
        SeriesWord.LESSON -> Triple(Res.string.event_lessons_one, Res.string.event_lessons_few, Res.string.event_lessons_many)
        SeriesWord.REHEARSAL -> Triple(Res.string.event_rehearsals_one, Res.string.event_rehearsals_few, Res.string.event_rehearsals_many)
        SeriesWord.EVENT -> Triple(Res.string.event_count_one, Res.string.event_count_few, Res.string.event_count_many)
    }
    return stringResource(Formats.plural(count, forms.first, forms.second, forms.third), count)
}

/**
 * The fields of words (spec 3.36.9): «Преподаватель · необязательно» of a lesson — «Место · необязательно» of anything else — with its
 * counter from 45 of 60; «Название · необязательно», the name of the kind in it, its counter from 60 of 80; «Заметки» of an edit, the
 * counter always, the focus at once where the form was opened on them («Добавить заметку»). Each field owns its text: the state comes back
 * a frame later, and a field fed from it loses the cursor to fast typing.
 */
@Composable
private fun TextFields(state: EventFormState, onIntent: (EventFormIntent) -> Unit) {
    val draft = state.draft
    var place by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.place, TextRange(draft.place.length))) }
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.title, TextRange(draft.title.length))) }
    var notes by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.notes, TextRange(draft.notes.length))) }
    val lesson = EventFormReducer.isLesson(state.kind.ref)
    val placeCaption = captionOf(stringResource(if (lesson) Res.string.event_field_teacher else Res.string.event_field_place), stringResource(Res.string.form_optional))
    val placeLine = remember { TypedLine() }
    val placeCounter = counterOf(place.text, state.maxPlaceLength, EventsDimens.PLACE_COUNTER_FROM)
    AppField(
        value = place,
        onValueChange = { next ->
            val before = place.text
            place = cut(next, state.maxPlaceLength)
            if (place.text != before) onIntent(EventFormIntent.PlaceChanged(place.text))
        },
        label = placeCaption.shown,
        labelDescription = placeCaption.said,
        modifier = riseWhenFocused(placeLine),
        placeholder = stringResource(if (lesson) Res.string.event_teacher_hint else Res.string.event_place_hint),
        keyboardOptions = KeyboardOptions(capitalization = if (lesson) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        typedLine = placeLine,
        counter = placeCounter?.shown,
        counterDescription = placeCounter?.said,
        counterColor = placeCounter?.color ?: Color.Unspecified,
    )
    val titleCaption = captionOf(stringResource(Res.string.event_field_title), stringResource(Res.string.form_optional))
    val titleLine = remember { TypedLine() }
    val titleCounter = counterOf(title.text, state.maxTitleLength, EventsDimens.TITLE_COUNTER_FROM)
    AppField(
        value = title,
        onValueChange = { next ->
            val before = title.text
            title = cut(next, state.maxTitleLength)
            if (title.text != before) onIntent(EventFormIntent.TitleChanged(title.text))
        },
        label = titleCaption.shown,
        labelDescription = titleCaption.said,
        modifier = riseWhenFocused(titleLine),
        // an empty title is the name of the kind (spec 3.35): the field shows it
        placeholder = eventKindName(state.kind),
        singleLine = false,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (state.isNew) ImeAction.Done else ImeAction.Next),
        typedLine = titleLine,
        counter = titleCounter?.shown,
        counterDescription = titleCounter?.said,
        counterColor = titleCounter?.color ?: Color.Unspecified,
    )
    if (!state.isNew) {
        val notesFocus = remember { FocusRequester() }
        if (state.focusNotes) LaunchedEffect(Unit) { notesFocus.requestFocus() }
        val notesLine = remember { TypedLine() }
        val notesCounter = counterOf(notes.text, state.maxNotesLength, from = 0, nearAtLimitOnly = true)
        AppField(
            value = notes,
            onValueChange = { next ->
                val before = notes.text
                notes = cut(next, state.maxNotesLength)
                if (notes.text != before) onIntent(EventFormIntent.NotesChanged(notes.text))
            },
            label = stringResource(Res.string.piece_field_notes),
            modifier = Modifier.focusRequester(notesFocus).then(riseWhenFocused(notesLine)),
            placeholder = stringResource(Res.string.piece_field_notes_placeholder),
            singleLine = false,
            minLines = NOTES_MIN_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            typedLine = notesLine,
            counter = notesCounter?.shown,
            counterDescription = notesCounter?.said,
            counterColor = notesCounter?.color ?: Color.Unspecified,
        )
    }
}

/** The counter of a field: its figures, its words for a reader — «69 из 80 знаков» — and its colour. */
private class FieldCounter(val shown: String, val said: String, val color: Color)

/**
 * The counter of a field (decision 36): none under [from] characters; the title and the place show it near their limit, in the first level
 * of text from the moment it comes; the notes always ([from] 0, [nearAtLimitOnly]) — in the second level, the first at the limit.
 */
@Composable
private fun counterOf(text: String, max: Int, from: Int, nearAtLimitOnly: Boolean = false): FieldCounter? {
    val length = text.codePointLength()
    if (length < from) return null
    val shown = stringResource(Res.string.profile_name_counter, length, max)
    val said = stringResource(Formats.plural(max, Res.string.field_counter_said_one, Res.string.field_counter_said_few, Res.string.field_counter_said_many), length, max)
    val color = if (!nearAtLimitOnly || length >= max) MaterialTheme.colorScheme.onSurface else Color.Unspecified
    return FieldCounter(shown, said, color)
}
