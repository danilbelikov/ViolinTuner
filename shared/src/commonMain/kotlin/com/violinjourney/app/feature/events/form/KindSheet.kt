package com.violinjourney.app.feature.events.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.KindNameProblem
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.text.codePointLength
import com.violinjourney.app.core.ui.components.AppField
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.drawCheckMark
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.KindSignPlate
import com.violinjourney.app.feature.events.builtInKindName
import com.violinjourney.app.feature.events.kindIcon
import com.violinjourney.app.feature.practice.components.CalendarMetrics
import com.violinjourney.app.feature.practice.components.DayCell
import com.violinjourney.app.feature.repertoire.form.cut
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_color_blue
import com.violinjourney.app.shared.resources.event_color_ice
import com.violinjourney.app.shared.resources.event_color_lime
import com.violinjourney.app.shared.resources.event_color_orchid
import com.violinjourney.app.shared.resources.event_color_powder
import com.violinjourney.app.shared.resources.event_color_rose
import com.violinjourney.app.shared.resources.event_color_sea
import com.violinjourney.app.shared.resources.event_color_turquoise
import com.violinjourney.app.shared.resources.event_count_few
import com.violinjourney.app.shared.resources.event_count_many
import com.violinjourney.app.shared.resources.event_count_one
import com.violinjourney.app.shared.resources.event_kind_built_in
import com.violinjourney.app.shared.resources.event_kind_built_in_note
import com.violinjourney.app.shared.resources.event_kind_color_line
import com.violinjourney.app.shared.resources.event_kind_delete
import com.violinjourney.app.shared.resources.event_kind_name_label
import com.violinjourney.app.shared.resources.event_kind_name_needed
import com.violinjourney.app.shared.resources.event_kind_name_taken
import com.violinjourney.app.shared.resources.event_kind_new
import com.violinjourney.app.shared.resources.event_kind_own
import com.violinjourney.app.shared.resources.event_kind_sheet_title
import com.violinjourney.app.shared.resources.event_kind_sign_line
import com.violinjourney.app.shared.resources.event_sign_arc
import com.violinjourney.app.shared.resources.event_sign_bolt
import com.violinjourney.app.shared.resources.event_sign_book
import com.violinjourney.app.shared.resources.event_sign_bowtie
import com.violinjourney.app.shared.resources.event_sign_chat
import com.violinjourney.app.shared.resources.event_sign_hat
import com.violinjourney.app.shared.resources.event_sign_heart
import com.violinjourney.app.shared.resources.event_sign_keys
import com.violinjourney.app.shared.resources.event_sign_leaf
import com.violinjourney.app.shared.resources.event_sign_mask
import com.violinjourney.app.shared.resources.event_sign_moon
import com.violinjourney.app.shared.resources.event_sign_taken
import com.violinjourney.app.shared.resources.event_sign_ticket
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.shared.resources.profile_name_counter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The names of the eight colours by their number (spec 5.29 R9): the caption «Цвет · Синий» and a reader say them. */
private val ColorNames = listOf(
    Res.string.event_color_blue, Res.string.event_color_sea, Res.string.event_color_rose, Res.string.event_color_orchid,
    Res.string.event_color_ice, Res.string.event_color_turquoise, Res.string.event_color_powder, Res.string.event_color_lime,
)

/** The name of a colour of the set; one out of it is the first. */
internal fun colorName(index: Int): StringResource = ColorNames.getOrElse(index) { ColorNames.first() }

/** The name of a sign of one's own; none of a built-in one (plan D29: a built-in sign is never named — its kind is). */
internal fun signName(sign: KindSign): StringResource? = when (sign) {
    KindSign.BOOK -> Res.string.event_sign_book
    KindSign.HAT -> Res.string.event_sign_hat
    KindSign.KEYS -> Res.string.event_sign_keys
    KindSign.MASK -> Res.string.event_sign_mask
    KindSign.TICKET -> Res.string.event_sign_ticket
    KindSign.CHAT -> Res.string.event_sign_chat
    KindSign.HEART -> Res.string.event_sign_heart
    KindSign.MOON -> Res.string.event_sign_moon
    KindSign.LEAF -> Res.string.event_sign_leaf
    KindSign.BOLT -> Res.string.event_sign_bolt
    KindSign.BOWTIE -> Res.string.event_sign_bowtie
    KindSign.ARC -> Res.string.event_sign_arc
    KindSign.LESSON, KindSign.REHEARSAL, KindSign.PERFORMANCE, KindSign.OTHER -> null
}

/**
 * The sheet «Вид» (spec 3.36.9; events-kinds.html 6): its head — «Вид» of a built-in kind, «Свой вид» of one's own with «Удалить вид…» at
 * its right (not of a new one); the preview — the sign on its plate, the name large, «встроенный вид» / «новый вид» / «12 событий», and the
 * day of the form with the mark of the kind, as it lies in a month. A built-in kind: its name as text and why it is not renamed, its colour.
 * One's own: «Имя» — the field of a sheet with «N / 24» under it — its colour and its sign; a sign another kind wears has a dot. The reason
 * why «Готово» sleeps stands over it ([KindSheetButtons]). Public for the tests of the app.
 */
@Composable
fun KindSheetContent(sheet: EventFormSheet.Kind, state: EventFormState, onIntent: (EventFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val draft = sheet.draft
    val builtIn = sheet.builtIn
    val look = KindLook(draft.sign, draft.color)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(if (builtIn != null) Res.string.event_kind_sheet_title else Res.string.event_kind_own), Modifier.weight(1f))
        if (sheet.deletable) DeleteWord { onIntent(EventFormIntent.KindDeleteClicked) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = EventsDimens.PreviewTop),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.PreviewGap),
    ) {
        KindSignPlate(look, EventsDimens.PreviewPlate, EventsDimens.PreviewPlateCorner, EventsDimens.PreviewSign, colors.surfaceContainer)
        Column(Modifier.weight(1f)) {
            Text(
                text = builtIn?.let { stringResource(builtInKindName(it)) } ?: draft.name.trim(),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = EventsDimens.SheetValue, lineHeight = EventsDimens.SheetValueHeight, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = EventsDimens.PREVIEW_TRACKING.em,
                ),
            )
            Text(
                text = when {
                    builtIn != null -> stringResource(Res.string.event_kind_built_in)
                    draft.ref == null -> stringResource(Res.string.event_kind_new)
                    else -> stringResource(Formats.plural(sheet.events, Res.string.event_count_one, Res.string.event_count_few, Res.string.event_count_many), sheet.events)
                },
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.PreviewCaption, lineHeight = EventsDimens.PreviewCaptionHeight),
            )
        }
        DayCell(
            cell = sheet.cell,
            metrics = CalendarMetrics.Portrait,
            modifier = Modifier.size(EventsDimens.PreviewCellWidth, EventsDimens.PreviewCellHeight),
            ground = colors.surfaceContainer,
            timeFill = false,
            interactive = false,
            onClick = {},
        )
    }
    if (builtIn != null) {
        Text(
            text = stringResource(Res.string.event_kind_built_in_note),
            modifier = Modifier.padding(top = EventsDimens.KindPartTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.KindNote, lineHeight = EventsDimens.KindNoteHeight),
        )
    } else {
        NameField(draft, state.maxKindNameLength, onIntent)
    }
    PartTitle(stringResource(Res.string.event_kind_color_line, stringResource(colorName(draft.color))))
    Swatches(draft.color) { onIntent(EventFormIntent.KindColorPicked(it)) }
    if (builtIn == null) {
        signName(draft.sign)?.let { PartTitle(stringResource(Res.string.event_kind_sign_line, stringResource(it))) }
        Signs(draft, sheet.taken) { onIntent(EventFormIntent.KindSignPicked(it)) }
    }
}

/**
 * «Готово» of «Вид» (spec 3.36.9): asleep at 0.38 while the name cannot be saved, the reason over it — «Нужно имя», «Такой вид уже есть —
 * „Урок“» — one line, as over «Сохранить» of the forms of R4 (decision 37).
 */
@Composable
fun KindSheetButtons(sheet: EventFormSheet.Kind, onIntent: (EventFormIntent) -> Unit) {
    val reason = when (val problem = sheet.problem) {
        null -> null
        KindNameProblem.Empty -> stringResource(Res.string.event_kind_name_needed)
        is KindNameProblem.Taken -> stringResource(Res.string.event_kind_name_taken, problem.name)
    }
    AppSheetButtons(
        main = stringResource(Res.string.profile_done),
        onMain = { onIntent(EventFormIntent.SheetDone) },
        mainEnabled = reason == null,
        mainReason = reason,
    )
}

/**
 * «Удалить вид…» in the head of the sheet: the bin 18 and the words 15 sp / 700 in the colour of danger, 48 high — but the head keeps the
 * height of its label (review of stage 98б): the button stands over the label's middle and reaches 15 over and under it, as the mockup's
 * negative margins have it (events-kinds.html 6, `.sh-head`), so that the label and all under it stand where they stand in a new kind and
 * in the other sheets.
 */
@Composable
private fun DeleteWord(onClick: () -> Unit) {
    val danger = ViolinTheme.dangerSoft
    Row(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                // no height of its own in the row: placed over the middle of the label, half of it over the row and half under
                layout(placeable.width, 0) { placeable.place(0, -placeable.height / 2) }
            }
            .heightIn(min = EventsDimens.KindDeleteMin)
            .clip(RoundedCornerShape(EventsDimens.ChangeCorner))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.ChangeGap / 2),
    ) {
        AppIcon(AppIcons.Trash, contentDescription = null, size = EventsDimens.KindDeleteIcon, tint = danger)
        Text(
            text = stringResource(Res.string.event_kind_delete),
            color = danger,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = EventsDimens.KindDeleteText, fontWeight = FontWeight.Bold),
        )
    }
}

/** «Имя» of a kind of one's own: the field of a sheet (R3), its counter «N / 24» always under it. */
@Composable
private fun NameField(draft: KindDraft, max: Int, onIntent: (EventFormIntent) -> Unit) {
    var name by rememberSaveable(draft.ref, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.name, TextRange(draft.name.length))) }
    Spacer(Modifier.height(EventsDimens.KindPartTop))
    AppField(
        value = name,
        onValueChange = { next ->
            val before = name.text
            name = cut(next, max)
            if (name.text != before) onIntent(EventFormIntent.KindNameChanged(name.text))
        },
        label = stringResource(Res.string.event_kind_name_label),
        counter = stringResource(Res.string.profile_name_counter, name.text.codePointLength(), max),
        inSheet = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
    )
}

/** The caption of a part of the sheet: «Цвет · Синий», «Знак · книга» — 13 sp / 700 in the second level of text. */
@Composable
private fun PartTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = EventsDimens.KindPartTop, bottom = EventsDimens.KindPartBottom),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = EventsDimens.ChangeLabel, fontWeight = FontWeight.Bold),
    )
}

/**
 * The eight colours (5.29 R9): 4 × 2 across the sheet — the saturated ones over the light ones — four equal columns 8 apart, as the grid
 * of the mockup (events-kinds.html 6, `.sw2`: `repeat(4, 1fr)`) and as the signs under them stand across the row; each cell a target of 48
 * high, the whole cell, with a circle of 36 in its middle; the chosen one has a ring of 3 in the colour of the sheet and one of 2 in the
 * first level of text around it and a tick of 20 in the colour of the ground. A radio button each: «Орхидея, выбран».
 */
@Composable
private fun Swatches(chosen: Int, onPick: (Int) -> Unit) {
    val events = ViolinTheme.eventsColors
    val colors = MaterialTheme.colorScheme
    val sheet = colors.surfaceContainer
    val ink = colors.onSurface
    val tick = colors.surface
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(EventsDimens.SwatchGapV)) {
        ColorNames.indices.chunked(EventsDimens.SWATCHES_IN_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EventsDimens.SwatchGapH)) {
                row.forEach { index ->
                    val name = stringResource(colorName(index))
                    val selected = index == chosen
                    val fill = events.of(index).color
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(EventsDimens.SwatchTarget)
                            .clip(CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(index) })
                            .semantics { contentDescription = name }
                            .drawBehind {
                                val circle = EventsDimens.SwatchCircle.toPx() / 2
                                drawCircle(fill, circle)
                                if (selected) {
                                    val inner = EventsDimens.SwatchRingInner.toPx()
                                    val outer = EventsDimens.SwatchRingOuter.toPx()
                                    drawCircle(sheet, circle + inner / 2, style = Stroke(inner))
                                    drawCircle(ink, circle + inner + outer / 2, style = Stroke(outer))
                                    val side = EventsDimens.SwatchTick.toPx()
                                    drawCheckMark(tick, Offset((size.width - side) / 2, (size.height - side) / 2), side, EventsDimens.SWATCH_TICK_LINE * density)
                                }
                            },
                    )
                }
            }
        }
    }
}

/**
 * The twelve signs of one's own (5.29 R9): six in a row where the row holds them with a touch of 48, else four in three rows
 * ([KindSheetMath]); a cell of 48 on the ground of the screen at a corner of 14, the sign 24; the chosen one — an inner ring of 2 and the
 * sign in the colour of the kind; one another kind wears — a dot. A radio button each: «книга, выбран», «полукруг, уже у вида „Оркестр“».
 */
@Composable
private fun Signs(draft: KindDraft, taken: Map<KindSign, String>, onPick: (KindSign) -> Unit) {
    val events = ViolinTheme.eventsColors
    val colors = MaterialTheme.colorScheme
    val kindColor = events.of(draft.color).color
    val dot = ViolinTheme.textTertiary
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val grid = KindSheetMath.signGrid(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(grid.gap)) {
            KindSign.OWN.chunked(grid.columns).forEach { row ->
                // the cells share the row by weight, not by [KindSheetMath.cellWidth] each: rounded to pixels one by one, six cells
                // and five gaps could come out wider than the row (on 360 dp at 2.625, 841 px of 839), and the last cell would shrink
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(grid.gap)) {
                    row.forEach { sign ->
                        val selected = sign == draft.sign
                        val name = signName(sign)?.let { stringResource(it) }.orEmpty()
                        val said = taken[sign]?.let { holder -> stringResource(Res.string.event_sign_taken, name, holder) } ?: name
                        val shape = RoundedCornerShape(EventsDimens.SignCellCorner)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(EventsDimens.SignCell)
                                .clip(shape)
                                .background(colors.surface, shape)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(sign) })
                                .semantics { contentDescription = said }
                                .drawBehind {
                                    if (selected) {
                                        val ring = EventsDimens.SignRing.toPx()
                                        drawRoundRect(
                                            kindColor, Offset(ring / 2, ring / 2), Size(size.width - ring, size.height - ring),
                                            CornerRadius(EventsDimens.SignCellCorner.toPx() - ring / 2), style = Stroke(ring),
                                        )
                                    }
                                    if (sign in taken) {
                                        val radius = EventsDimens.SignDot.toPx() / 2
                                        val inset = EventsDimens.SignDotInset.toPx()
                                        drawCircle(dot, radius, Offset(size.width - inset - radius, size.height - inset - radius))
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(kindIcon(sign), contentDescription = null, size = EventsDimens.SignIcon, tint = if (selected) kindColor else colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
