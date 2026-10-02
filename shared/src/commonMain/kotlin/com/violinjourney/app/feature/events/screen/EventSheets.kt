package com.violinjourney.app.feature.events.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.ListRow
import com.violinjourney.app.core.ui.components.ListRowEnd
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.ScopeAnswer
import com.violinjourney.app.feature.events.ScopeAnswers
import com.violinjourney.app.feature.events.ScopeSheetContent
import com.violinjourney.app.feature.events.eventNameOf
import com.violinjourney.app.feature.events.scopeAnswersPinned
import com.violinjourney.app.feature.live.block.PickerRow
import com.violinjourney.app.feature.live.block.PickerSectionHead
import com.violinjourney.app.feature.live.block.PickerSelection
import com.violinjourney.app.feature.live.block.pickerSections
import com.violinjourney.app.feature.practice.components.dashedFrame
import com.violinjourney.app.feature.repertoire.piece.Sentences
import com.violinjourney.app.feature.repertoire.sections.SectionPlate
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_offer_later
import com.violinjourney.app.shared.resources.block_open_repertoire
import com.violinjourney.app.shared.resources.block_played_title
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.event_add_camera_caption
import com.violinjourney.app.shared.resources.event_add_file
import com.violinjourney.app.shared.resources.event_add_file_caption
import com.violinjourney.app.shared.resources.event_add_mic
import com.violinjourney.app.shared.resources.event_add_mic_caption
import com.violinjourney.app.shared.resources.event_add_record
import com.violinjourney.app.shared.resources.event_delete_text_played
import com.violinjourney.app.shared.resources.event_delete_text_program
import com.violinjourney.app.shared.resources.event_mic_reason
import com.violinjourney.app.shared.resources.event_program
import com.violinjourney.app.shared.resources.event_program_done
import com.violinjourney.app.shared.resources.event_program_empty_text_played
import com.violinjourney.app.shared.resources.event_program_empty_text_program
import com.violinjourney.app.shared.resources.event_program_empty_title
import com.violinjourney.app.shared.resources.event_rest_on_weekdays
import com.violinjourney.app.shared.resources.event_series_delete_q_event
import com.violinjourney.app.shared.resources.event_series_delete_q_lesson
import com.violinjourney.app.shared.resources.event_series_delete_q_rehearsal
import com.violinjourney.app.shared.resources.event_series_following_event
import com.violinjourney.app.shared.resources.event_series_following_lesson
import com.violinjourney.app.shared.resources.event_series_following_rehearsal
import com.violinjourney.app.shared.resources.event_series_from_date
import com.violinjourney.app.shared.resources.event_series_past_short_event
import com.violinjourney.app.shared.resources.event_series_past_short_lesson
import com.violinjourney.app.shared.resources.event_series_past_short_rehearsal
import com.violinjourney.app.shared.resources.event_series_this_event
import com.violinjourney.app.shared.resources.event_series_this_lesson
import com.violinjourney.app.shared.resources.event_series_this_rehearsal
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.shared.resources.session_take_title
import com.violinjourney.app.shared.resources.take_grant_permission
import com.violinjourney.app.shared.resources.video_pick
import com.violinjourney.app.shared.resources.video_pick_hint
import com.violinjourney.app.shared.resources.video_shoot
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** The sides of the faces of the frame: 20 (5.29 R1, «Лист»), 16 under the last of them. */
private val SheetSide = 20.dp
private val SheetBottom = 16.dp
private val NoPadding = PaddingValues(0.dp)
private val LabelBottom = 6.dp

/**
 * The one frame of the sheets of the screen of an event (spec 3.36.9, plan D21): its faces — «Добавить запись», the choice of the
 * programme, the sheet of the deletion of an event of a repeat — follow the state; one in the place of another changes what the frame
 * shows without sliding away. A swipe, a tap beside it and «назад» only hide it: nothing is saved, deleted or begun. The choice of the
 * programme scrolls its list over «Готово · N» pinned at its bottom — an empty repertoire, its card and its buttons, as a whole; the other
 * faces scroll as a whole. The answers of a deletion are pinned at the bottom, but in a window no higher than 360 dp they end what
 * scrolls ([scopeAnswersPinned]).
 */
@Composable
internal fun EventSheetHost(state: EventState.Loaded, onIntent: (EventIntent) -> Unit) {
    val pinned = scopeAnswersPinned(currentDockMetrics().compact)
    AppSheet(
        value = state.sheet,
        onHide = { onIntent(EventIntent.SheetHidden) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        // the list of the repertoire scrolls by itself over «Готово»; an empty one is a card that may not fit a low window either
        scroll = { sheet -> sheet !is EventSheet.Program || sheet.sections.isEmpty() },
        contentPadding = NoPadding,
        bottom = { sheet -> buttonsOf(sheet, onIntent, pinned) },
    ) { sheet -> SheetFace(sheet, state, onIntent, pinned, inCard = false) }
}

/**
 * The face of [EventState.Loaded.sheet] in the frame without its window ([AppSheetCard]) — for a preview, where a window does not draw,
 * and for a test: the same content and the same buttons at its bottom as [EventSheetHost] shows. The card lays its content out at the
 * height it wants, so the repertoire of the choice of the programme stands in it as a column, not as a list that scrolls by itself.
 */
@Composable
fun EventSheetCard(state: EventState.Loaded, modifier: Modifier = Modifier, onIntent: (EventIntent) -> Unit = {}) {
    val sheet = state.sheet ?: return
    val pinned = scopeAnswersPinned(currentDockMetrics().compact)
    AppSheetCard(modifier = modifier, contentPadding = NoPadding, bottom = buttonsOf(sheet, onIntent, pinned)) {
        SheetFace(sheet, state, onIntent, pinned, inCard = true)
    }
}

/**
 * What a face of the frame shows above its buttons — and the answers of a deletion after its note where they are not [pinned]. [inCard] —
 * laid out in [EventSheetCard], whose content has no height to give a list.
 */
@Composable
private fun ColumnScope.SheetFace(sheet: EventSheet, state: EventState.Loaded, onIntent: (EventIntent) -> Unit, pinned: Boolean, inCard: Boolean) {
    when (sheet) {
        EventSheet.AddRecord -> AddRecordSheetContent(state.micPermission, onIntent)
        is EventSheet.Program -> ProgramSheetContent(sheet, state, onIntent, inCard)
        is EventSheet.DeleteScope -> {
            ScopeSheetContent(
                question = deleteQuestionOf(sheet),
                note = Sentences.join(
                    stringResource(if (state.performance) Res.string.event_delete_text_program else Res.string.event_delete_text_played),
                    stringResource(byWord(sheet.word, Res.string.event_series_past_short_lesson, Res.string.event_series_past_short_rehearsal, Res.string.event_series_past_short_event)),
                ),
                modifier = Modifier.padding(start = SheetSide, end = SheetSide),
                bin = true,
            )
            if (!pinned) DeleteScopeAnswers(sheet, onIntent)
        }
    }
}

/**
 * The buttons pinned at the bottom of a face: «Готово · N» of the choice, the answers of a deletion — unless they are not [pinned] in a
 * low window, where they end what scrolls; none for «Добавить запись».
 */
private fun buttonsOf(sheet: EventSheet, onIntent: (EventIntent) -> Unit, pinned: Boolean): (@Composable ColumnScope.() -> Unit)? = when (sheet) {
    EventSheet.AddRecord -> null
    is EventSheet.Program -> {
        { ProgramButtons(sheet, onIntent) }
    }
    is EventSheet.DeleteScope -> if (pinned) {
        { DeleteScopeAnswers(sheet, onIntent) }
    } else {
        null
    }
}

/**
 * «Добавить запись» (spec 3.36.9, 5.29 R9): the label and four rows by how often they are used — «Видео из галереи», «Звук из файла»,
 * «Записать звук», «Снять видео» — the rows of «Что добавить?» of R4 without a chevron: a press closes the sheet and begins the way.
 * Without the microphone «Записать звук» says why in the place of its caption, and «Разрешить доступ» stands at its end — the row itself
 * is not pressed then; allowed, the row wakes, and the recording begins only by its press.
 */
@Composable
fun AddRecordSheetContent(micPermission: Boolean?, onIntent: (EventIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = SheetSide, end = SheetSide, bottom = SheetBottom)) {
        SectionLabel(stringResource(Res.string.event_add_record), Modifier.padding(bottom = LabelBottom))
        WayRow(AppIcons.VideoGallery, stringResource(Res.string.video_pick), stringResource(Res.string.video_pick_hint)) {
            onIntent(EventIntent.AddRecordWay(RecordWay.GALLERY))
        }
        WayRow(AppIcons.FileAudio, stringResource(Res.string.event_add_file), stringResource(Res.string.event_add_file_caption)) {
            onIntent(EventIntent.AddRecordWay(RecordWay.FILE))
        }
        if (micPermission == false) {
            WayRow(
                AppIcons.Mic, stringResource(Res.string.event_add_mic), stringResource(Res.string.event_mic_reason),
                end = ListRowEnd.TextAction(stringResource(Res.string.take_grant_permission)) { onIntent(EventIntent.GrantMicClicked) },
            ) {}
        } else {
            WayRow(AppIcons.Mic, stringResource(Res.string.event_add_mic), stringResource(Res.string.event_add_mic_caption)) {
                onIntent(EventIntent.AddRecordWay(RecordWay.MIC))
            }
        }
        WayRow(AppIcons.Camera, stringResource(Res.string.video_shoot), stringResource(Res.string.event_add_camera_caption)) {
            onIntent(EventIntent.AddRecordWay(RecordWay.CAMERA))
        }
    }
}

/** A row of «Добавить запись»: the plate of 40 with the icon of 24 in the accent, the words of 16 sp / 700 and their caption. */
@Composable
private fun WayRow(icon: ImageVector, text: String, caption: String, end: ListRowEnd = ListRowEnd.None, onClick: () -> Unit) {
    ListRow(
        text = text,
        onClick = onClick,
        caption = caption,
        end = end,
        leading = { SectionPlate(icon, size = EventsDimens.AddPlate, iconSize = EventsDimens.AddPlateIcon) },
        strong = true,
    )
}

/**
 * The choice of the programme (spec 3.36.9, 5.29 R9): the label — «Программа» of a performance, «Что играли» of any other kind — and
 * under it quietly «Осенний концерт · сб 24 октября»; then the repertoire on the list of «Что играем» (R6), each row a box with its mark
 * at its end ([PickerSelection.Multi]) — the marked ones are the programme. The repertoire empty — a dashed card that says how to fill it.
 */
@Composable
internal fun ColumnScope.ProgramSheetContent(sheet: EventSheet.Program, state: EventState.Loaded, onIntent: (EventIntent) -> Unit, inCard: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = SheetSide, end = SheetSide)) {
        SectionLabel(stringResource(if (state.performance) Res.string.event_program else Res.string.block_played_title))
        Text(
            text = stringResource(Res.string.session_take_title, eventNameOf(state.header.name), Formats.weekdayDayMonth(state.header.date)),
            modifier = Modifier.padding(top = EventsDimens.ProgramSheetSubtitleTop),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.ProgramSheetSubtitle, lineHeight = EventsDimens.ProgramSheetSubtitleHeight),
        )
    }
    if (sheet.sections.isEmpty()) {
        EmptyRepertoire(state.performance)
        return
    }
    val checked = sheet.checked.toSet()
    val selection = PickerSelection.Multi(checked)
    if (inCard) {
        // a card has no height to give a list that scrolls: the same heads and rows, one under another
        Column(Modifier.fillMaxWidth().padding(horizontal = EventsDimens.ProgramListSide)) {
            sheet.sections.filter { it.pieces.isNotEmpty() }.forEach { section ->
                PickerSectionHead(section)
                section.pieces.forEach { piece ->
                    key(piece.id) {
                        PickerRow(piece, selected = piece.id in checked, enabled = true, onClick = { onIntent(EventIntent.ProgramToggled(piece.id)) }, selection = selection)
                    }
                }
            }
        }
        return
    }
    // the list takes what the frame leaves over its pinned «Готово» and scrolls in it; a short one is as tall as it is
    LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), contentPadding = PaddingValues(horizontal = EventsDimens.ProgramListSide)) {
        pickerSections(
            sections = sheet.sections,
            isSelected = { it.id in checked },
            onPick = { onIntent(EventIntent.ProgramToggled(it.id)) },
            pickable = { true },
            selection = selection,
        )
    }
}

/** The repertoire is empty (spec 3.36.9): «В репертуаре пока пусто» and how to fill it, in a dashed card. */
@Composable
private fun EmptyRepertoire(performance: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .padding(start = SheetSide, end = SheetSide, top = EventsDimens.LaterTop)
            .fillMaxWidth()
            .dashedFrame(colors.outlineVariant, EventsDimens.DashCorner)
            .padding(horizontal = EventsDimens.EmptyPaddingH, vertical = EventsDimens.EmptyPaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.event_program_empty_title),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = EventsDimens.EmptyTitle, lineHeight = EventsDimens.EmptyTitleHeight, fontWeight = FontWeight.ExtraBold),
        )
        Text(
            text = stringResource(if (performance) Res.string.event_program_empty_text_program else Res.string.event_program_empty_text_played),
            modifier = Modifier.padding(top = EventsDimens.EmptyGap),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.EmptyText, lineHeight = EventsDimens.EmptyTextHeight),
        )
    }
}

/**
 * «Готово · 2» with the number of the marked ones — «Готово» with none — at the bottom of the choice; of an empty repertoire «Открыть
 * репертуар» (the tab «Репертуар») and «Не сейчас» quietly.
 */
@Composable
private fun ProgramButtons(sheet: EventSheet.Program, onIntent: (EventIntent) -> Unit) {
    val modifier = Modifier.padding(start = SheetSide, end = SheetSide, bottom = SheetBottom)
    if (sheet.sections.isEmpty()) {
        AppSheetButtons(
            main = stringResource(Res.string.block_open_repertoire),
            onMain = { onIntent(EventIntent.OpenRepertoireClicked) },
            modifier = modifier,
            quiet = stringResource(Res.string.block_offer_later),
            onQuiet = { onIntent(EventIntent.SheetHidden) },
        )
    } else {
        val count = sheet.checked.size
        AppSheetButtons(
            main = if (count == 0) stringResource(Res.string.profile_done) else stringResource(Res.string.event_program_done, count),
            onMain = { onIntent(EventIntent.ProgramDone) },
            modifier = modifier,
        )
    }
}

/** «Удалить урок 19 октября?» — the word of the kind and the date of the event, whole sentences in every language. */
@Composable
private fun deleteQuestionOf(sheet: EventSheet.DeleteScope): String = stringResource(
    byWord(sheet.word, Res.string.event_series_delete_q_lesson, Res.string.event_series_delete_q_rehearsal, Res.string.event_series_delete_q_event),
    Formats.dayAndMonth(sheet.date),
)

/**
 * The answers of a deletion of an event of a repeat (spec 3.36.9): «Только этот урок · остальные — по понедельникам» and «Этот и
 * следующие · с 19 октября и дальше» — both coral outlines, neither filled: no answer of a deletion is the reasonable one.
 */
@Composable
private fun DeleteScopeAnswers(sheet: EventSheet.DeleteScope, onIntent: (EventIntent) -> Unit) {
    val rest = stringArrayResource(Res.array.event_rest_on_weekdays)
    ScopeAnswers(
        answers = listOf(
            ScopeAnswer(
                title = stringResource(byWord(sheet.word, Res.string.event_series_this_lesson, Res.string.event_series_this_rehearsal, Res.string.event_series_this_event)),
                caption = rest.getOrElse(sheet.weekday.ordinal) { rest.first() },
                style = AppButtonStyle.OutlineDanger,
                onClick = { onIntent(EventIntent.DeleteConfirmed(EditScope.ONLY_THIS)) },
            ),
            ScopeAnswer(
                title = stringResource(
                    byWord(sheet.word, Res.string.event_series_following_lesson, Res.string.event_series_following_rehearsal, Res.string.event_series_following_event),
                ),
                caption = stringResource(Res.string.event_series_from_date, Formats.dayAndMonth(sheet.from)),
                style = AppButtonStyle.OutlineDanger,
                onClick = { onIntent(EventIntent.DeleteConfirmed(EditScope.FOLLOWING)) },
            ),
        ),
        cancel = stringResource(Res.string.dialog_cancel),
        onCancel = { onIntent(EventIntent.SheetHidden) },
        modifier = Modifier.padding(start = SheetSide, end = SheetSide, bottom = SheetBottom),
    )
}

/** The string of the word of the kind: «урок», «репетиция», «событие». */
private fun byWord(word: SeriesWord, lesson: StringResource, rehearsal: StringResource, event: StringResource): StringResource = when (word) {
    SeriesWord.LESSON -> lesson
    SeriesWord.REHEARSAL -> rehearsal
    SeriesWord.EVENT -> event
}
