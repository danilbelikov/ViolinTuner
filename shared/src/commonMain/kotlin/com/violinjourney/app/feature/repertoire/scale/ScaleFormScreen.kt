package com.violinjourney.app.feature.repertoire.scale

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.ui.components.SegmentedSwitch
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconLabel
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.components.LocalExerciseWords
import com.violinjourney.app.feature.repertoire.components.statusLabel
import com.violinjourney.app.feature.repertoire.form.FormField
import com.violinjourney.app.feature.repertoire.form.Labeled
import com.violinjourney.app.feature.repertoire.form.TempoPicker
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.piece_delete
import com.violinjourney.app.shared.resources.piece_delete_confirm
import com.violinjourney.app.shared.resources.piece_delete_text
import com.violinjourney.app.shared.resources.piece_delete_title
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_field_status
import com.violinjourney.app.shared.resources.piece_form_close
import com.violinjourney.app.shared.resources.piece_form_discard_confirm
import com.violinjourney.app.shared.resources.piece_form_discard_title
import com.violinjourney.app.shared.resources.profile_name_counter
import com.violinjourney.app.shared.resources.scale_edit
import com.violinjourney.app.shared.resources.scale_exists
import com.violinjourney.app.shared.resources.scale_key
import com.violinjourney.app.shared.resources.scale_kind
import com.violinjourney.app.shared.resources.scale_new
import com.violinjourney.app.shared.resources.scale_octaves
import com.violinjourney.app.shared.resources.scale_open
import com.violinjourney.app.shared.resources.scale_preview_empty
import com.violinjourney.app.shared.resources.section_save
import org.jetbrains.compose.resources.stringResource

private val TopBarHeight = 56.dp
private val TopBarButton = 48.dp
private val MaxContentWidth = 560.dp
private val FieldGap = 20.dp
private val TonicHeight = 44.dp
private val TonicCorner = 12.dp
private val KindHeight = 36.dp
private val KindCorner = 10.dp
private val OctavesWidth = 200.dp
private val PreviewCorner = 16.dp
private val NotesMinHeight = 120.dp
private val DeleteHeight = 48.dp
private const val DIMMED = 0.35f
private const val LOCKED = 0.5f
private const val DIM_MS = 150
private const val PREVIEW_MS = 200
private val SCALE_TEMPOS = listOf(60, 80)

/** The form of a scale (spec 3.22, handoff 24d): three choices, the name they add up to, and the notes — drawn anew at every change. Stateless. */
@Composable
fun ScaleFormScreen(state: ScaleFormState, onIntent: (ScaleFormIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BackHandler { onIntent(ScaleFormIntent.CloseClicked) }
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
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(FieldGap),
            ) {
                // «Выучено», not «В репертуаре»: a scale is an exercise
                CompositionLocalProvider(LocalExerciseWords provides true) { Fields(state, onIntent) }
            }
        }
    }
    when (state.dialog) {
        ScaleFormDialog.DISCARD -> Confirm(
            title = stringResource(Res.string.piece_form_discard_title), text = null,
            confirm = stringResource(Res.string.piece_form_discard_confirm), destructive = false, onIntent = onIntent,
        )
        ScaleFormDialog.DELETE -> Confirm(
            title = stringResource(Res.string.piece_delete_title, state.scale?.let { scaleTitle(it.spec) }.orEmpty()),
            text = stringResource(Res.string.piece_delete_text),
            confirm = stringResource(Res.string.piece_delete_confirm), destructive = true, onIntent = onIntent,
        )
        null -> Unit
    }
}

@Composable
private fun TopBar(state: ScaleFormState, onIntent: (ScaleFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val close = stringResource(Res.string.piece_form_close)
        Box(
            modifier = Modifier
                .size(TopBarButton)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onIntent(ScaleFormIntent.CloseClicked) }
                .semantics { contentDescription = close },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Close, contentDescription = null, tint = colors.onSurface) }
        Text(
            text = stringResource(if (state.isNew) Res.string.scale_new else Res.string.scale_edit),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            color = colors.onSurface,
            maxLines = 1,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        )
        TextButton(onClick = { onIntent(ScaleFormIntent.SaveClicked) }, enabled = state.canSave) {
            Text(stringResource(Res.string.section_save), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Fields(state: ScaleFormState, onIntent: (ScaleFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val draft = state.draft
    // The key and the kind of a scale that exists are locked: they look it, and a tap says why.
    val keyAlpha = if (state.isNew) 1f else LOCKED

    Labeled(stringResource(Res.string.scale_key)) {
        Column(modifier = Modifier.alpha(keyAlpha), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tonic.entries.forEach { tonic ->
                    val allowed = tonic in state.tonicsAllowed
                    val picked = tonic == draft.tonic
                    val alpha by animateFloatAsState(if (allowed) 1f else DIMMED, tween(DIM_MS), label = "tonicAlpha")
                    val shape = RoundedCornerShape(TonicCorner)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(TonicHeight)
                            .alpha(alpha)
                            .clip(shape)
                            .background(if (picked) colors.primaryContainer else colors.surfaceContainer)
                            .selectable(selected = picked, enabled = allowed || !state.isNew, role = Role.RadioButton) { onIntent(ScaleFormIntent.TonicClicked(tonic)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        // the German way, like every key of the app: B natural is H
                        Text(
                            text = if (tonic == Tonic.B) "H" else tonic.name,
                            color = if (picked) colors.onPrimaryContainer else colors.onSurface,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
            val accidentals = Accidental.entries
            SegmentedSwitch(
                labels = listOf("♭", "♮", "♯"),
                selectedIndex = accidentals.indexOf(draft.accidental),
                onSelect = { onIntent(ScaleFormIntent.AccidentalSelected(accidentals[it])) },
                fontSize = 18,
            )
        }
    }
    Labeled(stringResource(Res.string.scale_kind)) {
        FlowRow(modifier = Modifier.alpha(keyAlpha), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScaleKind.entries.forEach { kind ->
                val picked = kind == draft.kind
                val shape = RoundedCornerShape(KindCorner)
                Box(
                    modifier = Modifier
                        .height(KindHeight)
                        .clip(shape)
                        .background(if (picked) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .border(1.dp, if (picked) colors.primaryContainer else colors.outlineVariant, shape)
                        .selectable(selected = picked, role = Role.RadioButton) { onIntent(ScaleFormIntent.KindSelected(kind)) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(scaleKindLabel(kind), color = if (picked) colors.onPrimaryContainer else colors.onSurface, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)) }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.scale_octaves), modifier = Modifier.weight(1f), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
        OctavePicker(draft.octaves, state.octavesAllowed, Modifier.width(OctavesWidth)) { onIntent(ScaleFormIntent.OctavesSelected(it)) }
    }

    // What the three choices add up to: a name nobody types, and the range — it explains why F-dur has no third octave.
    val scale = state.scale
    if (scale != null) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = scaleTitle(scale.spec),
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            )
            Text(scaleRange(scale), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"))
        }
    }
    if (state.existingId != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(colors.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.scale_exists), modifier = Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
            TextButton(onClick = { onIntent(ScaleFormIntent.OpenExistingClicked) }) { Text(stringResource(Res.string.scale_open)) }
        }
    }
    Preview(state)

    TempoPicker(draft.tempoBpm, SCALE_TEMPOS, onStep = { onIntent(ScaleFormIntent.TempoStepped(it)) }, onPick = { onIntent(ScaleFormIntent.TempoPicked(it)) })
    Labeled(stringResource(Res.string.piece_field_status)) {
        val statuses = PieceStatus.entries
        SegmentedSwitch(
            labels = statuses.map { statusLabel(it) },
            selectedIndex = statuses.indexOf(draft.status),
            onSelect = { onIntent(ScaleFormIntent.StatusSelected(statuses[it])) },
        )
    }
    // The field owns its text, like every field of the app.
    var notes by rememberSaveable { mutableStateOf(draft.notes) }
    FormField(
        value = notes,
        onValueChange = {
            notes = it.take(state.maxNotesLength)
            onIntent(ScaleFormIntent.NotesChanged(notes))
        },
        label = stringResource(Res.string.piece_field_notes),
        singleLine = false,
        capitalization = KeyboardCapitalization.Sentences,
        modifier = Modifier.heightIn(min = NotesMinHeight),
        supporting = stringResource(Res.string.profile_name_counter, notes.length, state.maxNotesLength),
    )
    if (!state.isNew) {
        OutlinedButton(
            onClick = { onIntent(ScaleFormIntent.DeleteClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .height(DeleteHeight),
            shape = RoundedCornerShape(DeleteHeight / 2),
            border = BorderStroke(1.dp, SolidColor(colors.outlineVariant)),
        ) {
            CompositionLocalProvider(LocalContentColor provides ViolinTheme.repertoireColors.formError) {
                IconLabel(AppIcons.Trash, stringResource(Res.string.piece_delete), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** «1 · 2 · 3»; an octave that would leave the instrument is dimmed and deaf — the range beside the name says why. */
@Composable
private fun OctavePicker(selected: Int, allowed: Set<Int>, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .border(1.dp, colors.outlineVariant, shape),
    ) {
        (1..Scales.MAX_OCTAVES).forEach { octaves ->
            val picked = octaves == selected
            val enabled = octaves in allowed
            val alpha by animateFloatAsState(if (enabled) 1f else DIMMED, tween(DIM_MS), label = "octaveAlpha")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .alpha(alpha)
                    .background(if (picked) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = picked, enabled = enabled, role = Role.RadioButton) { onSelect(octaves) },
                contentAlignment = Alignment.Center,
            ) { Text(octaves.toString(), color = if (picked) colors.onPrimaryContainer else colors.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontFeatureSettings = "tnum")) }
        }
    }
}

/** The notes as they will stand on the scale's screen — redrawn on the spot, only the height of the card is animated. */
@Composable
private fun Preview(state: ScaleFormState) {
    val colors = MaterialTheme.colorScheme
    val scale = state.scale
    val shape = RoundedCornerShape(PreviewCorner)
    if (scale == null) {
        val outline = colors.outlineVariant
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .drawBehind {
                    drawRoundRect(
                        outline, cornerRadius = CornerRadius(PreviewCorner.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(Res.string.scale_preview_empty),
                modifier = Modifier.padding(horizontal = 24.dp),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceContainer)
                .animateContentSize(tween(PREVIEW_MS))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) { ScaleNotation(scale, NotationSizes.Preview, ViolinTheme.exerciseColors.inkOnDark, name = scaleTitle(scale.spec)) }
    }
}

@Composable
private fun Confirm(title: String, text: String?, confirm: String, destructive: Boolean, onIntent: (ScaleFormIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(ScaleFormIntent.DialogDismissed) },
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = { onIntent(ScaleFormIntent.DialogConfirmed) }) {
                Text(confirm, color = if (destructive) ViolinTheme.destructive else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(ScaleFormIntent.DialogDismissed) }) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}
