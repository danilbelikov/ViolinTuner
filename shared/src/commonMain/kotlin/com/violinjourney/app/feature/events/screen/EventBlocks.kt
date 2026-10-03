package com.violinjourney.app.feature.events.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.components.dimmedWhen
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.eventKindName
import com.violinjourney.app.feature.events.eventNameOf
import com.violinjourney.app.feature.events.kindIcon
import com.violinjourney.app.feature.history.components.RecordCard
import com.violinjourney.app.feature.history.components.RecordPlace
import com.violinjourney.app.feature.history.components.recordCardTitle
import com.violinjourney.app.feature.practice.components.dashedFrame
import com.violinjourney.app.feature.repertoire.piece.BlockTitle
import com.violinjourney.app.feature.repertoire.piece.NotesCard
import com.violinjourney.app.feature.repertoire.sections.sectionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_played_title
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.event_add_record
import com.violinjourney.app.shared.resources.event_add_to_program
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.event_can_add
import com.violinjourney.app.shared.resources.event_can_add_from_repertoire
import com.violinjourney.app.shared.resources.event_can_add_note
import com.violinjourney.app.shared.resources.event_can_add_note_caption
import com.violinjourney.app.shared.resources.event_can_add_program
import com.violinjourney.app.shared.resources.event_can_add_record
import com.violinjourney.app.shared.resources.event_can_add_record_caption
import com.violinjourney.app.shared.resources.event_notes_ask_after
import com.violinjourney.app.shared.resources.event_notes_ask_before
import com.violinjourney.app.shared.resources.event_notes_ask_lesson
import com.violinjourney.app.shared.resources.event_program
import com.violinjourney.app.shared.resources.event_program_empty
import com.violinjourney.app.shared.resources.event_program_remove
import com.violinjourney.app.shared.resources.event_records_from
import com.violinjourney.app.shared.resources.event_repeat_biweekly_on
import com.violinjourney.app.shared.resources.event_repeat_until_end
import com.violinjourney.app.shared.resources.event_repeat_weekly_on
import com.violinjourney.app.shared.resources.event_time_range
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_notes_add
import com.violinjourney.app.shared.resources.practice_day_add
import com.violinjourney.app.shared.resources.practice_pair_description
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private const val TABULAR_FIGURES = "tnum"

/** What the parts of the screen of an event are drawn with, besides the state: the words of the screen are said by them. */
internal class EventBlockScope(
    val state: EventState.Loaded,
    val onIntent: (EventIntent) -> Unit,
    /** A recording runs: what would end it sleeps (spec 3.36.9). */
    val recording: Boolean,
    val zone: TimeZone,
)

/**
 * The head of an event (spec 3.36.9, 5.29 R9): the chip of its kind — the sign and the name in the colour of the kind on its plate, the
 * same sign as its mark in the calendar — its name large, wrapping whole, and the lines «когда · повтор · кто или где» with their icons:
 * «21 сентября, понедельник · 17:00–17:45», «Каждую неделю по понедельникам, до 28 декабря», the teacher or the place. No empty line.
 * [lying] — the head of the left column of landscape: the name 24 sp, the lines 14 sp, 4 apart. The name never breaks inside a word: it
 * is smaller where its longest word would not fit its line, down to 20 sp (18 lying).
 */
@Composable
internal fun EventHead(header: EventHeader, modifier: Modifier = Modifier, lying: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        KindChip(header)
        val titleSize = if (lying) EventsDimens.TitleTextLying else EventsDimens.TitleText
        val leastSp = if (lying) EventsDimens.TITLE_LYING_LEAST_SP else EventsDimens.TITLE_LEAST_SP
        Text(
            text = eventNameOf(header.name),
            modifier = Modifier.padding(top = EventsDimens.TitleTop, bottom = EventsDimens.TitleBottom).semantics { heading() },
            color = colors.onSurface,
            // whole words: smaller where the longest one would not fit its line (the left column lying at a large font)
            autoSize = remember(titleSize, leastSp) { WholeWordsFit(titleSize.value, leastSp) },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = titleSize, lineHeight = EventsDimens.TITLE_LINE.em, fontWeight = FontWeight.ExtraBold,
                letterSpacing = EventsDimens.TITLE_TRACKING.em,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(if (lying) EventsDimens.LinesGapLying else EventsDimens.LinesGap)) {
            HeadLine(AppIcons.Clock, whenLine(header), lying)
            header.repeat?.let { HeadLine(AppIcons.Repeat, repeatLine(it), lying) }
            if (header.person.isNotEmpty()) HeadLine(if (header.teacher) AppIcons.Person else AppIcons.Pin, header.person, lying)
        }
    }
}

/** The chip of the kind of an event: 32, a capsule, the sign 18 and the name 13 sp / 800 in the colour of the sign on its plate. */
@Composable
private fun KindChip(header: EventHeader) {
    val colors = ViolinTheme.eventsColors
    val look = header.kind.look
    val ground = MaterialTheme.colorScheme.surface
    val ink = colors.of(look.color).onPlate
    val name = eventKindName(header.kind)
    Row(
        modifier = Modifier
            .heightIn(min = EventsDimens.ChipHeight)
            .background(colors.plate(look.color, ground), CircleShape)
            .clearAndSetSemantics { contentDescription = name }
            .padding(start = EventsDimens.ChipPaddingStart, end = EventsDimens.ChipPaddingEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.ChipGap),
    ) {
        AppIcon(kindIcon(look.sign), contentDescription = null, tint = ink, size = EventsDimens.ChipSign)
        Text(
            text = name,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = EventsDimens.ChipText, lineHeight = EventsDimens.ChipTextHeight, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/** A line of the head: its icon of 18 in the third level of text and its words, wrapping whole (spec 3.36.9: nothing is cut here). */
@Composable
private fun HeadLine(icon: ImageVector, text: String, lying: Boolean) {
    val size = if (lying) EventsDimens.LineTextLying else EventsDimens.LineText
    val height = if (lying) EventsDimens.LineTextHeightLying else EventsDimens.LineTextHeight
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(EventsDimens.LineGap)) {
        // the icon stands in the middle of the first line
        Box(Modifier.heightIn(min = with(LocalDensity.current) { height.toDp() }), contentAlignment = Alignment.Center) {
            AppIcon(icon, contentDescription = null, tint = ViolinTheme.textTertiary, size = EventsDimens.LineIcon)
        }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = size, lineHeight = height, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/** «21 сентября, понедельник · 17:00–17:45»: the date as the sheet of the day says it, then the time, its end, or «весь день». */
@Composable
private fun whenLine(header: EventHeader): String {
    val start = header.startMinutes
    val time = when {
        start == null -> stringResource(Res.string.event_all_day)
        header.endMinutes != null -> stringResource(Res.string.event_time_range, Formats.clockOf(start), Formats.clockOf(header.endMinutes))
        else -> Formats.clockOf(start)
    }
    return Formats.dayWithWeekday(header.date) + stringResource(Res.string.dot_separator) + time
}

/** «Каждую неделю по понедельникам», «Раз в две недели по понедельникам», with its end — «…, до 28 декабря»: whole sentences. */
@Composable
private fun repeatLine(line: RepeatLine): String {
    val days = stringArrayResource(if (line.repeat == Repeat.BIWEEKLY) Res.array.event_repeat_biweekly_on else Res.array.event_repeat_weekly_on)
    val words = days.getOrElse(line.weekday.ordinal) { days.first() }
    return line.until?.let { stringResource(Res.string.event_repeat_until_end, words, Formats.dayAndMonth(it)) } ?: words
}

/** The parts of the screen in their order (spec 3.36.9), each under its title — upright all of them, lying those of the right column. */
@Composable
internal fun EventBlockScope.Sections(sections: List<EventSection>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        sections.forEach { section ->
            key(section) {
                when (section) {
                    EventSection.NOTES -> NotesSection()
                    EventSection.PROGRAM -> ProgramSection()
                    EventSection.RECORDS -> RecordsSection()
                    EventSection.RECORDS_LATER -> RecordsLater()
                    EventSection.CAN_ADD -> CanAddSection()
                }
            }
        }
    }
}

/**
 * «Заметки» (spec 3.36.9): the card of the notes of an element (3.15, R4) — six lines, then «ещё», which works while a recording runs.
 * None — a dashed card with the question of the kind and «Добавить заметку», which opens the form on its notes; a kind that asks nothing
 * (a rehearsal, «Другое», one's own) — «Добавить заметку» alone.
 */
@Composable
private fun EventBlockScope.NotesSection() {
    val notes = state.notes
    val ask = state.notesAsk?.let { askOf(it) }
    BlockTitle(stringResource(Res.string.piece_field_notes))
    if (notes.isNotBlank()) {
        NotesCard(notes, state.notesCollapsedLines)
        return
    }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashedFrame(colors.outlineVariant, EventsDimens.DashCorner)
            .padding(horizontal = EventsDimens.NotesEmptyPaddingH, vertical = EventsDimens.NotesEmptyPaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (ask != null) {
            Text(
                text = ask,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = EventsDimens.NotesEmptyText, lineHeight = EventsDimens.NotesEmptyTextHeight),
            )
        }
        AppButton(
            text = stringResource(Res.string.piece_notes_add),
            onClick = { onIntent(EventIntent.AddNotesClicked) },
            modifier = Modifier.dimmedWhen(recording),
            style = AppButtonStyle.Text,
        )
    }
}

@Composable
private fun askOf(ask: NotesAsk): String = stringResource(
    when (ask) {
        NotesAsk.LESSON -> Res.string.event_notes_ask_lesson
        NotesAsk.BEFORE -> Res.string.event_notes_ask_before
        NotesAsk.AFTER -> Res.string.event_notes_ask_after
    },
)

/**
 * «Программа» of a performance, «Что играли» of any other kind (spec 3.36.9): «+ Добавить» at the right of the title — the choice of
 * the repertoire — and the rows numbered in their order: the name in one line, the composer or the section under it, the cross that
 * takes the element away at once. An empty programme — a dashed card «Что будете играть?» with «Добавить»; an empty «Что играли» — its
 * title alone.
 */
@Composable
private fun EventBlockScope.ProgramSection() {
    val title = stringResource(if (state.performance) Res.string.event_program else Res.string.block_played_title)
    BlockTitle(title, action = { AddWord(stringResource(Res.string.event_add_to_program), busy = false) { onIntent(EventIntent.ProgramAddClicked) } }, actionWhole = true)
    val program = state.program
    if (program.isEmpty()) {
        if (state.performance) EmptyProgram()
        return
    }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .padding(horizontal = EventsDimens.ProgramPaddingH),
    ) {
        program.forEachIndexed { index, row ->
            key(row.pieceId) {
                if (index > 0) Box(Modifier.fillMaxWidth().heightIn(min = EventsDimens.Rule).background(colors.outlineVariant))
                ProgramLine(row)
            }
        }
    }
}

/** A row of the programme: «1, Концерт ля минор, 1 ч., А. Вивальди» for TalkBack, the cross «Убрать «…»» a button of its own. */
@Composable
private fun EventBlockScope.ProgramLine(row: ProgramRow) {
    val colors = MaterialTheme.colorScheme
    val second = row.composer ?: sectionName(row.section, row.sectionName)
    var said = row.number.toString()
    for (part in listOf(row.title, second).filter { it.isNotBlank() }) said = stringResource(Res.string.practice_pair_description, said, part)
    val remove = stringResource(Res.string.event_program_remove, row.title)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = EventsDimens.ProgramRowMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = EventsDimens.ProgramRowMin)
                .dimmedWhen(recording)
                .clickable(role = Role.Button) { onIntent(EventIntent.PieceClicked(row.pieceId)) }
                .clearAndSetSemantics {
                    contentDescription = said
                    role = Role.Button
                    onClick { onIntent(EventIntent.PieceClicked(row.pieceId)); true }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EventsDimens.ProgramGap),
        ) {
            Box(Modifier.size(EventsDimens.ProgramNumber).background(colors.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    text = row.number.toString(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = EventsDimens.ProgramNumberText, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = EventsDimens.ProgramTitle, lineHeight = EventsDimens.ProgramTitleHeight, fontWeight = FontWeight.Bold),
                )
                if (second.isNotBlank()) {
                    Text(
                        text = second,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.ProgramSecond, lineHeight = EventsDimens.ProgramSecondHeight),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                // its target reaches 12 into the field of the card, its icon 17 from the edge (events-views.html 5): the row takes 36
                // of it, and the row's own press goes up to it
                .layout { measurable, constraints ->
                    val cross = measurable.measure(constraints)
                    val out = EventsDimens.ProgramCrossOut.roundToPx()
                    layout(cross.width - out, cross.height) { cross.placeRelative(0, 0) }
                }
                .dimmedWhen(recording)
                .size(EventsDimens.ProgramCross)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onIntent(EventIntent.ProgramRemoved(row.pieceId)) }
                .semantics { contentDescription = remove },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Close, contentDescription = null, tint = ViolinTheme.textTertiary, size = EventsDimens.ProgramCrossIcon) }
    }
}

/** An empty programme of a performance: «Что будете играть?» and «Добавить» in a dashed card (spec 3.36.9). */
@Composable
private fun EventBlockScope.EmptyProgram() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashedFrame(colors.outlineVariant, EventsDimens.DashCorner)
            .padding(horizontal = EventsDimens.NotesEmptyPaddingH, vertical = EventsDimens.NotesEmptyPaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.event_program_empty),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = EventsDimens.NotesEmptyText, lineHeight = EventsDimens.NotesEmptyTextHeight),
        )
        AppButton(
            text = stringResource(Res.string.practice_day_add),
            onClick = { onIntent(EventIntent.ProgramAddClicked) },
            modifier = Modifier.dimmedWhen(recording),
            style = AppButtonStyle.Text,
            icon = AppIcons.Plus,
        )
    }
}

/**
 * «Записи» (spec 3.36.9): the cards of R5, newest first, each only opened here — no «⋯», no long press, a chevron — a fresh one
 * highlighted; «+ Добавить» at the right of the title of every kind but a performance, whose «Добавить запись» is pinned at the bottom.
 * No records of a performance — no title: its pinned button says what to do.
 */
@Composable
private fun EventBlockScope.RecordsSection() {
    if (state.records.isEmpty() && state.pinnedAddRecord) return
    val title = stringResource(Res.string.nav_history)
    if (state.pinnedAddRecord) {
        BlockTitle(title)
    } else {
        BlockTitle(title, action = { AddWord(stringResource(Res.string.event_add_record), busy = state.busyImport) { onIntent(EventIntent.AddRecordClicked) } }, actionWhole = true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(EventsDimens.RecordsGap)) {
        state.records.forEach { card ->
            key(card.id) {
                RecordCard(
                    card = card,
                    title = recordCardTitle(card),
                    start = Formats.timeOfDay(card.startedAtEpochMs, zone),
                    onClick = { onIntent(EventIntent.RecordClicked(card.id)) },
                    modifier = Modifier.dimmedWhen(recording),
                    place = RecordPlace.Event,
                    highlighted = card.id == state.newRecordId,
                    spokenDate = Formats.recordDate(card.date, card.otherYear),
                )
            }
        }
    }
}

/** «Записи появятся с 24 октября»: before the day of the event, in the place of its records — a dashed line with the tape. */
@Composable
private fun EventBlockScope.RecordsLater() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(top = EventsDimens.LaterTop)
            .fillMaxWidth()
            .heightIn(min = EventsDimens.LaterHeight)
            .dashedFrame(colors.outlineVariant, EventsDimens.LaterCorner)
            .padding(horizontal = EventsDimens.LaterPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.LaterGap),
    ) {
        AppIcon(AppIcons.Tape, contentDescription = null, tint = ViolinTheme.textTertiary, size = EventsDimens.LaterIcon)
        Text(
            text = stringResource(Res.string.event_records_from, Formats.dayAndMonth(state.header.date)),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.LaterText, lineHeight = EventsDimens.LaterTextHeight),
        )
    }
}

/**
 * «Можно добавить» (spec 3.36.9): from the day of the event on, when it has no notes, no programme and no records — one card in the place
 * of the three empty parts: «Заметку — что было, что запомнить» (the form at its notes), «Что играли — из репертуара» (of a performance
 * «Программу»), «Запись — звук или видео» (not of a performance: its pinned button does it).
 */
@Composable
private fun EventBlockScope.CanAddSection() {
    val colors = MaterialTheme.colorScheme
    BlockTitle(stringResource(Res.string.event_can_add))
    val rows = buildList<@Composable () -> Unit> {
        add {
            CanAddRow(AppIcons.Pencil, stringResource(Res.string.event_can_add_note), stringResource(Res.string.event_can_add_note_caption)) {
                onIntent(EventIntent.AddNotesClicked)
            }
        }
        add {
            CanAddRow(
                AppIcons.Sheet,
                stringResource(if (state.performance) Res.string.event_can_add_program else Res.string.block_played_title),
                stringResource(Res.string.event_can_add_from_repertoire),
            ) { onIntent(EventIntent.ProgramAddClicked) }
        }
        if (state.canAddRecord) {
            add {
                // the microphone, as the mockup draws it (events-views.html 5): the tape is the sign of «Записи появятся с …»
                CanAddRow(AppIcons.Mic, stringResource(Res.string.event_can_add_record), stringResource(Res.string.event_can_add_record_caption), busy = state.busyImport) {
                    onIntent(EventIntent.AddRecordClicked)
                }
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .padding(horizontal = EventsDimens.ProgramPaddingH),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().heightIn(min = EventsDimens.Rule).background(colors.outlineVariant))
            row()
        }
    }
}

/** A row of «Можно добавить»: a plate of 36 with its icon, the words and their caption, the plus in the accent. */
@Composable
private fun EventBlockScope.CanAddRow(icon: ImageVector, title: String, caption: String, busy: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EventsDimens.CanAddRowMin)
            .dimmedWhen(recording || busy)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.CanAddGap),
    ) {
        Box(
            Modifier.size(EventsDimens.CanAddPlate).background(colors.surfaceContainerHigh, RoundedCornerShape(EventsDimens.CanAddPlateCorner)),
            contentAlignment = Alignment.Center,
        ) { AppIcon(icon, contentDescription = null, tint = colors.onSurfaceVariant, size = EventsDimens.CanAddIcon) }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = EventsDimens.CanAddTitle, lineHeight = EventsDimens.CanAddTitleHeight, fontWeight = FontWeight.Bold),
            )
            Text(
                text = caption,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.CanAddCaption, lineHeight = EventsDimens.CanAddCaptionHeight, fontWeight = FontWeight.Medium),
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(EventsDimens.CanAddPlus), color = colors.primary, strokeWidth = EventsDimens.SpinnerStroke)
        } else {
            AppIcon(AppIcons.Plus, contentDescription = null, tint = colors.primary, size = EventsDimens.CanAddPlus)
        }
    }
}

/**
 * «+ Добавить» at the right of the title of a part (spec 3.36.9): a text button of 48 — read «Добавить в программу», «Добавить запись»
 * ([said]). Asleep while a recording runs; [busy] — a file on its way in: asleep, a spinner in the place of the plus (D37).
 */
@Composable
private fun EventBlockScope.AddWord(said: String, busy: Boolean, onClick: () -> Unit) {
    val asleep = recording || busy
    Box(
        Modifier
            .dimmedWhen(recording)
            .clearAndSetSemantics {
                contentDescription = said
                role = Role.Button
                if (!asleep) onClick { onClick(); true }
            },
    ) {
        AppButton(
            text = stringResource(Res.string.practice_day_add),
            onClick = { if (!asleep) onClick() },
            style = AppButtonStyle.Text,
            enabled = !busy,
            leading = if (busy) ({ CircularProgressIndicator(Modifier.size(EventsDimens.Spinner), color = MaterialTheme.colorScheme.primary, strokeWidth = EventsDimens.SpinnerStroke) }) else null,
            icon = AppIcons.Plus,
        )
    }
}
