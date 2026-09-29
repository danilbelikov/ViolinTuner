package com.violinjourney.app.feature.repertoire.scale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.text.codePointLength
import com.violinjourney.app.core.ui.components.AppField
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.components.DiscardDialog
import com.violinjourney.app.core.ui.components.DiscardLoss
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.ReasonLine
import com.violinjourney.app.core.ui.components.SegmentedSwitch
import com.violinjourney.app.core.ui.components.TypedLine
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.components.LocalExerciseWords
import com.violinjourney.app.feature.repertoire.components.TonicGrid
import com.violinjourney.app.feature.repertoire.components.statusLabel
import com.violinjourney.app.feature.repertoire.form.FormCaption
import com.violinjourney.app.feature.repertoire.form.FormDeleteRow
import com.violinjourney.app.feature.repertoire.form.FormDock
import com.violinjourney.app.feature.repertoire.form.FormDockMode
import com.violinjourney.app.feature.repertoire.form.FormFrame
import com.violinjourney.app.feature.repertoire.form.FormReason
import com.violinjourney.app.feature.repertoire.form.NOTES_MIN_LINES
import com.violinjourney.app.feature.repertoire.form.TempoField
import com.violinjourney.app.feature.repertoire.form.captionOf
import com.violinjourney.app.feature.repertoire.form.cut
import com.violinjourney.app.feature.repertoire.form.riseWhenFocused
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.key_pick_tonic_first
import com.violinjourney.app.shared.resources.piece_delete_confirm
import com.violinjourney.app.shared.resources.piece_delete_text
import com.violinjourney.app.shared.resources.piece_delete_title
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_field_notes_placeholder
import com.violinjourney.app.shared.resources.piece_field_status
import com.violinjourney.app.shared.resources.profile_name_counter
import com.violinjourney.app.shared.resources.repertoire_no_takes
import com.violinjourney.app.shared.resources.scale_delete
import com.violinjourney.app.shared.resources.scale_edit
import com.violinjourney.app.shared.resources.scale_key
import com.violinjourney.app.shared.resources.scale_kind
import com.violinjourney.app.shared.resources.scale_locked_reason
import com.violinjourney.app.shared.resources.scale_new
import com.violinjourney.app.shared.resources.scale_octaves
import com.violinjourney.app.shared.resources.scale_octaves_unfit
import com.violinjourney.app.shared.resources.scale_open_twin
import com.violinjourney.app.shared.resources.scale_preview_empty
import com.violinjourney.app.shared.resources.scale_tonics_gray
import com.violinjourney.app.shared.resources.scale_twin
import com.violinjourney.app.shared.resources.section_add_scale
import com.violinjourney.app.shared.resources.section_save
import com.violinjourney.app.shared.resources.takes_few
import com.violinjourney.app.shared.resources.takes_many
import com.violinjourney.app.shared.resources.takes_one
import org.jetbrains.compose.resources.stringResource

// The form of a scale (spec 3.36.4, 5.29 R4, «Форма гаммы»; repertoire.html 5).
private val SummaryPadding = 14.dp
private val SummaryNotesTop = 10.dp
private val BetweenRows = 10.dp
private val ReasonTop = 6.dp
private val KindGap = 8.dp
private val KindHeight = 48.dp
private val KindSide = 12.dp
private val KindVertical = 6.dp

/** A tile is laid out in whole pixels: a word that fits only by a hair is not trusted. */
private val KindSlack = 1.dp
private const val DISABLED_ALPHA = 0.38f
private const val SUMMARY_TITLE_SIZE = 20
private const val SUMMARY_TRACKING = -0.01f
private const val SIGN_SIZE = 26
private val Signs = listOf("♭", "♮", "♯")
private val SCALE_TEMPOS = listOf(60, 80)

/**
 * The form of a scale (spec 3.22, 3.36.4; repertoire.html 5). Stateless. First what the choices add up to — the name, the range and
 * the notes, redrawn at every change; under it the tonics C … H and the sign, the four kinds, the octaves, then the tempo, the status
 * and the notes. What cannot be picked is dimmed and says why under it: grey tonics (eight signs), the octave off the instrument, and
 * — in an edit — the key and the kind of a scale that exists. The bottom zone adds the scale («Добавить гамму», in an edit
 * «Сохранить»), sleeps under «Сначала выберите тонику», or — the scale is there already — opens it («Открыть её») under the plate that
 * says where it is.
 */
@Composable
fun ScaleFormScreen(state: ScaleFormState, onIntent: (ScaleFormIntent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onIntent(ScaleFormIntent.CloseClicked) }
    // «Выучено», not «В репертуаре»: a scale is an exercise — in the switch and on the plate of the twin alike
    CompositionLocalProvider(LocalExerciseWords provides true) {
        FormFrame(
            title = stringResource(if (state.isNew) Res.string.scale_new else Res.string.scale_edit),
            loading = state.loading,
            onClose = { onIntent(ScaleFormIntent.CloseClicked) },
            dock = { mode -> ScaleDock(state, mode, onIntent) },
            modifier = modifier,
        ) { Fields(state, onIntent) }
    }
    when (state.dialog) {
        ScaleFormDialog.DISCARD, ScaleFormDialog.DISCARD_AND_OPEN -> DiscardDialog(
            loss = DiscardLoss.of(state.isNew, state.savedTitle),
            onDiscard = { onIntent(ScaleFormIntent.DialogConfirmed) },
            onBack = { onIntent(ScaleFormIntent.DialogDismissed) },
        )
        ScaleFormDialog.DELETE -> DeleteDialog(
            // the name in the list; the scale as the form now says it only if the stored one has not been read — never met, the
            // bin is under the fields, and they wait for the scale
            title = stringResource(Res.string.piece_delete_title, state.savedTitle ?: state.scale?.let { scaleTitle(it.spec) }.orEmpty()),
            text = stringResource(Res.string.piece_delete_text),
            confirm = stringResource(Res.string.piece_delete_confirm),
            onConfirm = { onIntent(ScaleFormIntent.DialogConfirmed) },
            onDismiss = { onIntent(ScaleFormIntent.DialogDismissed) },
        )
        null -> Unit
    }
}

/** The bottom zone: the twin opens, a scale without a tonic waits for one, any other is added or saved. */
@Composable
private fun DockScope.ScaleDock(state: ScaleFormState, mode: FormDockMode, onIntent: (ScaleFormIntent) -> Unit) {
    val twin = state.twin
    if (twin != null) {
        // not an error but a way out (spec 3.36.4)
        FormDock(
            mode = mode,
            button = stringResource(Res.string.scale_open_twin),
            onClick = { onIntent(ScaleFormIntent.OpenExistingClicked) },
            reason = FormReason.Plate(AppIcons.Scale, twinWords(twin)),
        )
    } else {
        FormDock(
            mode = mode,
            button = stringResource(if (state.isNew) Res.string.section_add_scale else Res.string.section_save),
            onClick = { onIntent(ScaleFormIntent.SaveClicked) },
            enabled = state.canSave,
            reason = if (state.draft.tonic == null) FormReason.Line(stringResource(Res.string.key_pick_tonic_first)) else null,
        )
    }
}

/** «Такая гамма уже есть — в «Гаммах», статус «Учу», 2 дубля.» — without takes «нет дублей». */
@Composable
private fun twinWords(twin: ScaleTwin): String {
    val takes = if (twin.takes == 0) {
        stringResource(Res.string.repertoire_no_takes)
    } else {
        stringResource(Formats.plural(twin.takes, Res.string.takes_one, Res.string.takes_few, Res.string.takes_many), twin.takes)
    }
    return stringResource(Res.string.scale_twin, statusLabel(twin.status), takes)
}

@Composable
private fun Fields(state: ScaleFormState, onIntent: (ScaleFormIntent) -> Unit) {
    val draft = state.draft
    // the key and the kind of a scale that exists are its own (3.22): they sleep, and the line under the kind says why
    val keyOpen = state.isNew
    Summary(state)
    FormCaption(captionOf(stringResource(Res.string.scale_key))) {
        TonicGrid(
            selected = draft.tonic,
            onClick = { onIntent(ScaleFormIntent.TonicClicked(it)) },
            enabled = { keyOpen && it in state.tonicsAllowed },
            // the German way, like every key of the app: B natural is H (5.9)
            label = { if (it == Tonic.B) "H" else it.name },
        )
        Spacer(Modifier.height(BetweenRows))
        val accidentals = Accidental.entries
        SegmentedSwitch(
            labels = Signs,
            selectedIndex = accidentals.indexOf(draft.accidental),
            onSelect = { onIntent(ScaleFormIntent.AccidentalSelected(accidentals[it])) },
            fontSize = SIGN_SIZE,
            enabled = keyOpen,
        )
        // in an edit the reason is one — the line under the kind
        if (keyOpen && state.tonicsAllowed.size < Tonic.entries.size) {
            ReasonLine(stringResource(Res.string.scale_tonics_gray), Modifier.padding(top = ReasonTop))
        }
    }
    FormCaption(captionOf(stringResource(Res.string.scale_kind))) {
        KindTiles(draft.kind, enabled = keyOpen) { onIntent(ScaleFormIntent.KindSelected(it)) }
        if (!keyOpen) ReasonLine(stringResource(Res.string.scale_locked_reason), Modifier.padding(top = ReasonTop))
    }
    FormCaption(captionOf(stringResource(Res.string.scale_octaves))) {
        val octaves = (1..Scales.MAX_OCTAVES).toList()
        SegmentedSwitch(
            labels = octaves.map { it.toString() },
            selectedIndex = octaves.indexOf(draft.octaves).takeIf { it >= 0 },
            onSelect = { onIntent(ScaleFormIntent.OctavesSelected(octaves[it])) },
            segmentEnabled = { octaves[it] in state.octavesAllowed },
        )
        // only the third octave can leave the violin (G3 … E7, the lowest tonic no higher than F#4): «не помещается», not «сломалось»
        val startNote = state.startNote
        if (startNote != null && octaves.any { it !in state.octavesAllowed }) {
            ReasonLine(stringResource(Res.string.scale_octaves_unfit, startNote), Modifier.padding(top = ReasonTop))
        }
    }
    TempoField(draft.tempoBpm, SCALE_TEMPOS, onStep = { onIntent(ScaleFormIntent.TempoStepped(it)) }, onPick = { onIntent(ScaleFormIntent.TempoPicked(it)) })
    val statusCaption = stringResource(Res.string.piece_field_status)
    FormCaption(captionOf(statusCaption)) {
        val statuses = PieceStatus.entries
        SegmentedSwitch(
            labels = statuses.map { statusLabel(it) },
            selectedIndex = statuses.indexOf(draft.status),
            onSelect = { onIntent(ScaleFormIntent.StatusSelected(statuses[it])) },
            // the same switch as on the screen of the element (spec 3.36.4): its words are never cut
            wholeWords = true,
            description = statusCaption,
        )
    }
    // The field owns its text, like every field of the app.
    var notes by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.notes, TextRange(draft.notes.length))) }
    // «Добавить заметку» of the scale's screen opens the form at this field, as a piece's does (3.15, 3.36.4)
    val notesFocus = remember { FocusRequester() }
    if (state.focusNotes) LaunchedEffect(Unit) { notesFocus.requestFocus() }
    // where the field notes the line being typed, for the column to keep it on a keyboard too high for the whole field
    val notesLine = remember { TypedLine() }
    AppField(
        value = notes,
        onValueChange = { next ->
            val before = notes.text
            notes = cut(next, state.maxNotesLength)
            if (notes.text != before) onIntent(ScaleFormIntent.NotesChanged(notes.text))
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
    if (!state.isNew) FormDeleteRow(stringResource(Res.string.scale_delete)) { onIntent(ScaleFormIntent.DeleteClicked) }
}

/**
 * What the choices add up to, first (spec 3.36.4): a card of surfaceContainer at a corner of 18 — the name nobody types («F-dur · 2
 * октавы», 20 sp / 800) and on its right the range, which explains why F-dur has no third octave; under them the notes as the scale's
 * screen will draw them. No tonic yet — «Выберите тонику — ноты нарисуются здесь».
 */
@Composable
private fun Summary(state: ScaleFormState) {
    val colors = MaterialTheme.colorScheme
    val scale = state.scale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .padding(SummaryPadding),
    ) {
        if (scale == null) {
            Text(
                text = stringResource(Res.string.scale_preview_empty),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
            )
        } else {
            val title = scaleTitle(scale.spec)
            Row {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = SUMMARY_TITLE_SIZE.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = SUMMARY_TRACKING.em,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Text(
                    text = scaleRange(scale),
                    modifier = Modifier.alignByBaseline(),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
            }
            Spacer(Modifier.height(SummaryNotesTop))
            ScaleNotation(scale, NotationSizes.Preview, ViolinTheme.exerciseColors.inkOnDark, name = title)
        }
    }
}

/**
 * The four kinds as tiles two to a row (spec 3.36.4, 5.29 R4): 48 at the least at a corner of 12 on surfaceContainer, 14 sp / 700 in
 * the second level; the chosen one in primaryContainer with the words in the colour of text. The words are whole: smaller together
 * where a word would not fit its tile, one tile a row where even then ([ScaleKindFit]). [enabled] false — an edit: all at 0.38, deaf.
 */
@Composable
private fun KindTiles(selected: ScaleKind, enabled: Boolean, onSelect: (ScaleKind) -> Unit) {
    val labels = ScaleKind.entries.map { scaleKindLabel(it) }
    val base = MaterialTheme.typography.labelLarge.copy(fontSize = ScaleKindFit.MAX_SP.sp, lineHeight = (ScaleKindFit.MAX_SP * LINE_HEIGHT).sp, fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        val plan = remember(labels, base, measurer, density, width) {
            val halfRoom = with(density) { ((width - KindGap) / 2 - KindSide * 2 - KindSlack).toPx() }
            ScaleKindFit.plan(halfRoom) { sizeSp ->
                val style = base.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * LINE_HEIGHT).sp)
                labels.flatMap { it.split(' ') }.filter { it.isNotEmpty() }
                    .maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width.toFloat() }
            }
        }
        val style = base.copy(fontSize = plan.sizeSp.sp, lineHeight = (plan.sizeSp * LINE_HEIGHT).sp)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(KindGap)) {
            ScaleKind.entries.chunked(plan.columns).forEach { row ->
                // the tiles of a row are as tall as the taller one
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(KindGap)) {
                    row.forEach { kind ->
                        KindTile(labels[kind.ordinal], picked = kind == selected, enabled = enabled, style = style, modifier = Modifier.weight(1f).fillMaxHeight()) {
                            onSelect(kind)
                        }
                    }
                }
            }
        }
    }
}

private const val LINE_HEIGHT = 1.3f

@Composable
private fun KindTile(label: String, picked: Boolean, enabled: Boolean, style: TextStyle, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = KindHeight)
            .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .clip(AppShapes.S)
            .background(if (picked) colors.primaryContainer else colors.surfaceContainer)
            .selectable(selected = picked, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = KindSide, vertical = KindVertical),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = if (picked) colors.onPrimaryContainer else colors.onSurfaceVariant, style = style)
    }
}
