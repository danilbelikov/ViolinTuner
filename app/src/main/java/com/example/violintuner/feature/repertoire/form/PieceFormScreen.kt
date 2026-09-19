package com.example.violintuner.feature.repertoire.form

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.repertoire.Accidental
import com.example.violintuner.core.domain.repertoire.KeyMode
import com.example.violintuner.core.domain.repertoire.MusicalKey
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.Tonic
import com.example.violintuner.core.ui.components.SegmentedSwitch
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.repertoire.components.TempoStepper
import com.example.violintuner.feature.repertoire.components.statusLabel

private val ScreenPadding = 16.dp
private val MaxContentWidth = 560.dp
private val TopBarHeight = 56.dp
private val TopBarButton = 48.dp
private val FieldCorner = 16.dp
private val FieldGap = 20.dp
private val TonicCell = 44.dp
private val TonicCorner = 12.dp
private val TonicGap = 6.dp
private val ChipHeight = 32.dp
private val ChipCorner = 8.dp
private val NotesMinHeight = 120.dp
private val DeleteHeight = 48.dp
private const val DISABLED_ALPHA = 0.4f
private const val ACCIDENTAL_SIZE = 20
private val QUICK_TEMPOS = listOf(80, 100, 120)

/** The form of a piece (spec 3.15, handoff 13e1–13e5). Stateless. «Сохранить» sits in the top bar, where no keyboard covers it. */
@Composable
fun PieceFormScreen(state: PieceFormState, onIntent: (PieceFormIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BackHandler { onIntent(PieceFormIntent.CloseClicked) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(state, onIntent)
        if (!state.loading) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(FieldGap),
            ) {
                Fields(state, onIntent)
            }
        }
    }
    when (state.dialog) {
        PieceFormDialog.DISCARD -> ConfirmDialog(
            title = stringResource(R.string.piece_form_discard_title),
            text = null,
            confirm = stringResource(R.string.piece_form_discard_confirm),
            destructive = false,
            onIntent = onIntent,
        )
        PieceFormDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.piece_delete_title, state.draft.title.trim()),
            text = stringResource(R.string.piece_delete_text),
            confirm = stringResource(R.string.piece_delete_confirm),
            destructive = true,
            onIntent = onIntent,
        )
        null -> Unit
    }
}

@Composable
private fun TopBar(state: PieceFormState, onIntent: (PieceFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val close = stringResource(R.string.piece_form_close)
        Box(
            modifier = Modifier
                .size(TopBarButton)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onIntent(PieceFormIntent.CloseClicked) }
                .semantics { contentDescription = close },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Close, contentDescription = null, tint = colors.onSurface)
        }
        Text(
            text = stringResource(if (state.isNew) R.string.piece_form_title_new else R.string.piece_form_title_edit),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            color = colors.onSurface,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        )
        // Stays tappable without a title: the tap is what makes the field say why it cannot be saved.
        TextButton(onClick = { onIntent(PieceFormIntent.SaveClicked) }, modifier = Modifier.alpha(if (state.canSave) 1f else DISABLED_ALPHA)) {
            Text(stringResource(R.string.practice_save), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun Fields(state: PieceFormState, onIntent: (PieceFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    // Every field owns its text: the state comes back a frame later, and a field fed from it
    // loses the cursor to fast typing. The view model only hears of changes.
    var title by rememberSaveable { mutableStateOf(draft.title) }
    var composer by rememberSaveable { mutableStateOf(draft.composer) }
    var notes by rememberSaveable { mutableStateOf(draft.notes) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FormField(
            value = title,
            onValueChange = {
                title = it.take(state.maxTitleLength)
                onIntent(PieceFormIntent.TitleChanged(title))
            },
            label = stringResource(R.string.piece_field_title),
            isError = state.titleError,
            capitalization = KeyboardCapitalization.Sentences,
        )
        if (state.titleError) {
            Text(
                text = stringResource(R.string.piece_title_error),
                modifier = Modifier.padding(start = 16.dp),
                color = ViolinTheme.repertoireColors.formError,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            )
        }
    }
    FormField(
        value = composer,
        onValueChange = {
            composer = it.take(state.maxComposerLength)
            onIntent(PieceFormIntent.ComposerChanged(composer))
        },
        label = stringResource(R.string.piece_field_composer),
        capitalization = KeyboardCapitalization.Words,
    )
    KeyPicker(draft.key, onIntent)
    TempoPicker(draft.tempoBpm, onIntent)
    Labeled(stringResource(R.string.piece_field_status)) {
        val statuses = PieceStatus.entries
        SegmentedSwitch(
            labels = statuses.map { statusLabel(it) },
            selectedIndex = statuses.indexOf(draft.status),
            onSelect = { onIntent(PieceFormIntent.StatusSelected(statuses[it])) },
        )
    }
    val notesFocus = remember { FocusRequester() }
    if (state.focusNotes) LaunchedEffect(Unit) { notesFocus.requestFocus() }
    FormField(
        value = notes,
        onValueChange = {
            notes = it.take(state.maxNotesLength)
            onIntent(PieceFormIntent.NotesChanged(notes))
        },
        label = stringResource(R.string.piece_field_notes),
        singleLine = false,
        capitalization = KeyboardCapitalization.Sentences,
        modifier = Modifier
            .heightIn(min = NotesMinHeight)
            .focusRequester(notesFocus),
        supporting = stringResource(R.string.profile_name_counter, notes.length, state.maxNotesLength),
    )
    if (!state.isNew) {
        val error = ViolinTheme.repertoireColors.formError
        OutlinedButton(
            onClick = { onIntent(PieceFormIntent.DeleteClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .height(DeleteHeight),
            shape = RoundedCornerShape(DeleteHeight / 2),
            border = BorderStroke(1.dp, SolidColor(colors.outlineVariant)),
        ) {
            CompositionLocalProvider(LocalContentColor provides error) {
                IconLabel(AppIcons.Trash, stringResource(R.string.piece_delete), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    capitalization: KeyboardCapitalization,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    singleLine: Boolean = true,
    supporting: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val error = ViolinTheme.repertoireColors.formError
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = isError,
        singleLine = singleLine,
        shape = RoundedCornerShape(FieldCorner),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        supportingText = supporting?.let { { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum")) } },
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outlineVariant,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            cursorColor = colors.primary,
            errorBorderColor = error,
            errorLabelColor = error,
            errorCursorColor = error,
        ),
    )
}

/** A caption with, to its right, what the controls below add up to: «Тональность   G-dur». */
@Composable
private fun Labeled(label: String, value: String? = null, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, modifier = Modifier.weight(1f), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
            if (value != null) {
                Text(value, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }
        }
        content()
    }
}

/** Three rows instead of a list of thirty: the tonic, its sign, the mode (handoff 13e1). Optional: a tap on the picked tonic clears it. */
@Composable
private fun KeyPicker(key: MusicalKey?, onIntent: (PieceFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Labeled(stringResource(R.string.piece_field_key), value = key?.germanName ?: stringResource(R.string.practice_no_value)) {
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(TonicGap)) {
            Tonic.entries.forEach { tonic ->
                val selected = key?.tonic == tonic
                val shape = RoundedCornerShape(TonicCorner)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(TonicCell)
                        .clip(shape)
                        .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
                        .border(1.dp, if (selected) colors.primaryContainer else colors.outlineVariant, shape)
                        .selectable(selected = selected, role = Role.RadioButton) { onIntent(PieceFormIntent.TonicClicked(tonic)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tonic.name,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
        // Without a tonic there is nothing for a sign or a mode to belong to.
        val dependent = Modifier.alpha(if (key != null) 1f else DISABLED_ALPHA)
        val accidentals = Accidental.entries
        SegmentedSwitch(
            labels = listOf("♭", "♮", "♯"),
            selectedIndex = key?.let { accidentals.indexOf(it.accidental) },
            onSelect = { onIntent(PieceFormIntent.AccidentalSelected(accidentals[it])) },
            modifier = dependent,
            fontSize = ACCIDENTAL_SIZE,
        )
        val modes = KeyMode.entries
        SegmentedSwitch(
            labels = listOf(stringResource(R.string.piece_key_major), stringResource(R.string.piece_key_minor)),
            selectedIndex = key?.let { modes.indexOf(it.mode) },
            onSelect = { onIntent(PieceFormIntent.ModeSelected(modes[it])) },
            modifier = dependent,
        )
    }
}

/** A mark on the music, not a metronome: nothing here ever sounds (spec 2, principle 2). */
@Composable
private fun TempoPicker(tempoBpm: Int?, onIntent: (PieceFormIntent) -> Unit) {
    Labeled(stringResource(R.string.piece_field_tempo)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TempoStepper(
                value = tempoBpm?.let { stringResource(R.string.piece_tempo_value, it) } ?: stringResource(R.string.piece_tempo_empty),
                onStep = { onIntent(PieceFormIntent.TempoStepped(it)) },
                downDescription = stringResource(R.string.piece_tempo_slower),
                upDescription = stringResource(R.string.piece_tempo_faster),
            )
            Box(Modifier.weight(1f))
            (listOf<Int?>(null) + QUICK_TEMPOS).forEach { bpm ->
                TempoChip(
                    text = bpm?.toString() ?: stringResource(R.string.practice_no_value),
                    selected = bpm == tempoBpm,
                    description = bpm?.let { stringResource(R.string.piece_tempo_value, it) } ?: stringResource(R.string.piece_tempo_clear),
                ) { onIntent(PieceFormIntent.TempoPicked(bpm)) }
            }
        }
    }
}

@Composable
private fun TempoChip(text: String, selected: Boolean, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(ChipCorner)
    Box(
        modifier = Modifier
            .height(ChipHeight)
            .widthIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String?, confirm: String, destructive: Boolean, onIntent: (PieceFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { onIntent(PieceFormIntent.DialogDismissed) },
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = { onIntent(PieceFormIntent.DialogConfirmed) }) {
                Text(confirm, color = if (destructive) ViolinTheme.repertoireColors.formError else colors.primary)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(PieceFormIntent.DialogDismissed) }) { Text(stringResource(R.string.dialog_cancel)) } },
        containerColor = colors.surfaceContainerHigh,
    )
}
