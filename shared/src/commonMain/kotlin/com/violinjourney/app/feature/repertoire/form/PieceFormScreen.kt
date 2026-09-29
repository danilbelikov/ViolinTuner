package com.violinjourney.app.feature.repertoire.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.text.codePointLength
import com.violinjourney.app.core.text.takeCodePoints
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppField
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.components.DiscardDialog
import com.violinjourney.app.core.ui.components.DiscardLoss
import com.violinjourney.app.core.ui.components.SegmentedSwitch
import com.violinjourney.app.core.ui.components.TypedLine
import com.violinjourney.app.feature.repertoire.SectionKeys
import com.violinjourney.app.feature.repertoire.components.LocalExerciseWords
import com.violinjourney.app.feature.repertoire.components.statusLabel
import com.violinjourney.app.feature.repertoire.sections.sectionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.etude_delete
import com.violinjourney.app.shared.resources.form_author
import com.violinjourney.app.shared.resources.form_new_element
import com.violinjourney.app.shared.resources.form_new_etude
import com.violinjourney.app.shared.resources.form_new_stroke
import com.violinjourney.app.shared.resources.form_optional
import com.violinjourney.app.shared.resources.form_section
import com.violinjourney.app.shared.resources.piece_delete
import com.violinjourney.app.shared.resources.piece_delete_confirm
import com.violinjourney.app.shared.resources.piece_delete_text
import com.violinjourney.app.shared.resources.piece_delete_title
import com.violinjourney.app.shared.resources.piece_field_composer
import com.violinjourney.app.shared.resources.piece_field_composer_placeholder
import com.violinjourney.app.shared.resources.piece_field_key
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_field_notes_placeholder
import com.violinjourney.app.shared.resources.piece_field_status
import com.violinjourney.app.shared.resources.piece_field_title
import com.violinjourney.app.shared.resources.piece_field_title_placeholder
import com.violinjourney.app.shared.resources.piece_form_title_edit
import com.violinjourney.app.shared.resources.piece_form_title_new
import com.violinjourney.app.shared.resources.piece_title_error
import com.violinjourney.app.shared.resources.practice_save
import com.violinjourney.app.shared.resources.profile_name_counter
import com.violinjourney.app.shared.resources.stroke_delete
import com.violinjourney.app.shared.resources.stroke_suggestions
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private val QUICK_TEMPOS = listOf(80, 100, 120)

/** The notes: three lines at the least — a field of 104, no lower than the 88 of 5.29 R4. */
internal const val NOTES_MIN_LINES = 3

/**
 * The form of a piece, an étude, a stroke and an element of one's own section (spec 3.15, 3.22, 3.36.4; repertoire.html 4,
 * landscape.html 5). Stateless. The captions stand over the fields and say what is optional; «Раздел» and «Тональность» are rows that
 * open their sheets; «Сохранить» is in the bottom zone over the keyboard, asleep under «Без названия не сохранить» while there is no
 * title — from the first frame; «Удалить …» of an edit is the last line of what scrolls.
 */
@Composable
fun PieceFormScreen(state: PieceFormState, onIntent: (PieceFormIntent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onIntent(PieceFormIntent.CloseClicked) }
    FormFrame(
        title = formTitle(state),
        loading = state.loading,
        onClose = { onIntent(PieceFormIntent.CloseClicked) },
        dock = { mode ->
            FormDock(
                mode = mode,
                button = stringResource(Res.string.practice_save),
                onClick = { onIntent(PieceFormIntent.SaveClicked) },
                enabled = state.canSave,
                // one reason, one place: over the button, not under the field
                reason = if (state.canSave) null else FormReason.Line(stringResource(Res.string.piece_title_error)),
            )
        },
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalExerciseWords provides SectionKeys.isExercise(state.section)) { Fields(state, onIntent) }
    }
    PieceFormSheets(state, onIntent)
    when (state.dialog) {
        PieceFormDialog.DISCARD -> DiscardDialog(
            loss = DiscardLoss.of(state.isNew, state.savedTitle),
            onDiscard = { onIntent(PieceFormIntent.DialogConfirmed) },
            onBack = { onIntent(PieceFormIntent.DialogDismissed) },
        )
        PieceFormDialog.DELETE -> DeleteDialog(
            title = stringResource(Res.string.piece_delete_title, state.savedTitle),
            text = stringResource(Res.string.piece_delete_text),
            confirm = stringResource(Res.string.piece_delete_confirm),
            onConfirm = { onIntent(PieceFormIntent.DialogConfirmed) },
            onDismiss = { onIntent(PieceFormIntent.DialogDismissed) },
        )
        null -> Unit
    }
}

/** «Новое произведение», «Новый этюд», «Новый штрих», «Новый элемент»; in an edit — «Произведение» or the name of the section. */
@Composable
private fun formTitle(state: PieceFormState): String = when {
    !state.isNew -> sectionElementTitle(state)
    state.stroke -> stringResource(Res.string.form_new_stroke)
    state.etude -> stringResource(Res.string.form_new_etude)
    state.section is SectionRef.Custom -> stringResource(Res.string.form_new_element)
    else -> stringResource(Res.string.piece_form_title_new)
}

@Composable
private fun Fields(state: PieceFormState, onIntent: (PieceFormIntent) -> Unit) {
    val draft = state.draft
    val focus = LocalFocusManager.current
    // Every field owns its text: the state comes back a frame later, and a field fed from it loses the cursor to fast typing. The
    // view model only hears of changes.
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.title, TextRange(draft.title.length))) }
    // The move into «Штрихи» clears the author in the draft (spec 3.22): the field starts again from the draft whenever the form turns
    // into a stroke or out of one, or a move there and back would show an author that will not be saved. The title and the notes need
    // no such key: nothing but the player's typing changes them.
    var composer by rememberSaveable(state.stroke, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(draft.composer, TextRange(draft.composer.length)))
    }
    var notes by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.notes, TextRange(draft.notes.length))) }

    if (state.stroke) {
        // The usual strokes are one tap away; typing anything else takes the highlight off (spec 3.22).
        StrokeSuggestions(selected = title.text.trim()) { word ->
            title = TextFieldValue(word, TextRange(word.length))
            onIntent(PieceFormIntent.TitleChanged(word))
        }
    }
    // where each field notes the line being typed, for the column to keep it on a keyboard too high for the whole field
    val titleLine = remember { TypedLine() }
    AppField(
        value = title,
        onValueChange = { next ->
            val before = title.text
            title = cut(next, state.maxTitleLength)
            if (title.text != before) onIntent(PieceFormIntent.TitleChanged(title.text))
        },
        label = stringResource(Res.string.piece_field_title),
        modifier = riseWhenFocused(titleLine),
        placeholder = stringResource(Res.string.piece_field_title_placeholder),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        typedLine = titleLine,
    )
    if (!state.stroke) {
        // an étude has an author — Kayser, Kreutzer, Mazas — rather than a composer
        val caption = captionOf(
            stringResource(if (state.etude) Res.string.form_author else Res.string.piece_field_composer),
            stringResource(Res.string.form_optional),
        )
        val composerLine = remember { TypedLine() }
        AppField(
            value = composer,
            onValueChange = { next ->
                val before = composer.text
                composer = cut(next, state.maxComposerLength)
                if (composer.text != before) onIntent(PieceFormIntent.ComposerChanged(composer.text))
            },
            label = caption.shown,
            labelDescription = caption.said,
            modifier = riseWhenFocused(composerLine),
            placeholder = stringResource(Res.string.piece_field_composer_placeholder),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            typedLine = composerLine,
        )
    }
    if (state.sections.isNotEmpty()) {
        ChoiceRow(
            label = stringResource(Res.string.form_section),
            value = sectionName(state.section, state.sections.firstOrNull { it.ref == state.section }?.name),
            onClick = {
                // the keyboard would stand over the sheet
                focus.clearFocus()
                onIntent(PieceFormIntent.SectionRowClicked)
            },
        )
    }
    if (!state.stroke) {
        ChoiceRow(
            label = stringResource(Res.string.piece_field_key),
            value = draft.key?.germanName,
            onClick = {
                focus.clearFocus()
                onIntent(PieceFormIntent.KeyRowClicked)
            },
        )
    }
    TempoField(draft.tempoBpm, QUICK_TEMPOS, onStep = { onIntent(PieceFormIntent.TempoStepped(it)) }, onPick = { onIntent(PieceFormIntent.TempoPicked(it)) })
    val statusCaption = stringResource(Res.string.piece_field_status)
    FormCaption(captionOf(statusCaption)) {
        val statuses = PieceStatus.entries
        SegmentedSwitch(
            labels = statuses.map { statusLabel(it) },
            selectedIndex = statuses.indexOf(draft.status),
            onSelect = { onIntent(PieceFormIntent.StatusSelected(statuses[it])) },
            // the same switch as on the screen of the element (spec 3.36.4): its words are never cut
            wholeWords = true,
            description = statusCaption,
        )
    }
    val notesFocus = remember { FocusRequester() }
    if (state.focusNotes) LaunchedEffect(Unit) { notesFocus.requestFocus() }
    val notesLine = remember { TypedLine() }
    AppField(
        value = notes,
        onValueChange = { next ->
            val before = notes.text
            notes = cut(next, state.maxNotesLength)
            if (notes.text != before) onIntent(PieceFormIntent.NotesChanged(notes.text))
        },
        label = stringResource(Res.string.piece_field_notes),
        modifier = Modifier.focusRequester(notesFocus).then(riseWhenFocused(notesLine)),
        placeholder = stringResource(Res.string.piece_field_notes_placeholder),
        counter = stringResource(Res.string.profile_name_counter, notes.text.codePointLength(), state.maxNotesLength),
        singleLine = false,
        minLines = NOTES_MIN_LINES,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        typedLine = notesLine,
    )
    if (!state.isNew) {
        // the word by the kind (spec 3.36.4): the pieces and the sections of one's own — «Удалить произведение»
        val word = when {
            state.etude -> Res.string.etude_delete
            state.stroke -> Res.string.stroke_delete
            else -> Res.string.piece_delete
        }
        FormDeleteRow(stringResource(word)) { onIntent(PieceFormIntent.DeleteClicked) }
    }
}

/**
 * What is typed or pasted, no longer than [max] code points — an emoji at the edge is never cut in half; a cut puts the cursor at the
 * end, as the dialogs of R1 do.
 */
internal fun cut(value: TextFieldValue, max: Int): TextFieldValue {
    val text = value.text.takeCodePoints(max)
    return if (text == value.text) value else TextFieldValue(text, TextRange(text.length))
}

@Composable
private fun sectionElementTitle(state: PieceFormState): String = when {
    state.stroke || state.etude || state.section is SectionRef.Custom -> sectionName(state.section, state.sections.firstOrNull { it.ref == state.section }?.name)
    else -> stringResource(Res.string.piece_form_title_edit)
}

/**
 * The sheets over the form (spec 3.36.4): «Тональность» with its buttons pinned at its bottom, and «Раздел». Up while the view model
 * says so ([PieceFormState.sheet]); a swipe, a tap outside and «назад» only hide them ([PieceFormIntent.SheetHidden]) — every choice is
 * in the draft already. Never wider than 640, clear of the status bar.
 */
@Composable
private fun PieceFormSheets(state: PieceFormState, onIntent: (PieceFormIntent) -> Unit) {
    AppSheet(
        value = state.sheet,
        onHide = { onIntent(PieceFormIntent.SheetHidden) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        bottom = { sheet -> if (sheet == PieceFormSheet.KEY) ({ KeySheetButtons(onIntent) }) else null },
        // two faces of one frame, never one in the place of the other: the frame goes down between them
        faceOf = { it },
    ) { sheet ->
        when (sheet) {
            PieceFormSheet.KEY -> KeySheetContent(state.draft.key, onIntent)
            PieceFormSheet.SECTION -> SectionSheetContent(state, onIntent)
        }
    }
}

/** The usual strokes as chips of choice over the title (spec 3.22, 3.36.4); the one the title is now is chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrokeSuggestions(selected: String, onPick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SuggestionGap),
        verticalArrangement = Arrangement.spacedBy(SuggestionGap),
    ) {
        stringArrayResource(Res.array.stroke_suggestions).forEach { word ->
            AppChip.Choice(text = word, selected = word.equals(selected, ignoreCase = true), onClick = { onPick(word) })
        }
    }
}

private val SuggestionGap = 8.dp
