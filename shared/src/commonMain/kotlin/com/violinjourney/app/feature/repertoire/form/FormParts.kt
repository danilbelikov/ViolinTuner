package com.violinjourney.app.feature.repertoire.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.DockRows
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.ReasonLine
import com.violinjourney.app.core.ui.components.TypedLine
import com.violinjourney.app.core.ui.components.appFieldLeastHeight
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.components.ReasonPlate
import com.violinjourney.app.feature.repertoire.components.TempoStepper
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.piece_field_tempo
import com.violinjourney.app.shared.resources.piece_field_tempo_hint
import com.violinjourney.app.shared.resources.piece_form_close
import com.violinjourney.app.shared.resources.piece_tempo_clear
import com.violinjourney.app.shared.resources.piece_tempo_description
import com.violinjourney.app.shared.resources.piece_tempo_faster
import com.violinjourney.app.shared.resources.piece_tempo_slower
import com.violinjourney.app.shared.resources.practice_no_value
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource

// The forms of the repertoire (spec 3.36.4, 5.29 R4, «Формы»; repertoire.html 4 and 5, landscape.html 5).

/** The fields of the screen, and the sides of its bottom zone (5.29 R4: 16, not the 20 of R1). */
internal val FormSide = 16.dp

/** Between the fields. */
internal val FieldGap = 14.dp
private val MaxColumn = 560.dp
private val TopBarHeight = 56.dp
private val TopBarHeightLandscape = 48.dp
private val TopBarButton = 48.dp
private val TopBarSide = 4.dp
private val ContentTop = 8.dp
private val ContentEnd = 24.dp
private val CaptionGap = 6.dp
private val ChoiceHeight = 56.dp
private val ChoiceStart = 16.dp
private val ChoiceEnd = 8.dp
private val ChoiceGap = 12.dp
private val ChoiceVertical = 8.dp
private val DeleteHeight = 56.dp
private val DeleteTop = 24.dp
private val DeleteIcon = 20.dp
private val DeleteGap = 8.dp
private val ReasonToButton = 8.dp
private val PlateToButton = 10.dp
private val DockRowGap = 12.dp
private val TempoGap = 8.dp

/** What stays over the field in focus once it has come up: a little air under the edge of the column. */
private val FocusedAir = 8.dp

/** The button of the zone in one line (`AppButton(compact = true)`): how high the line is at the least, until it has been laid out. */
private val LineButton = 48.dp

/** The keyboard has stopped rising when its height stood still this long; then the field in focus comes up. */
private const val KEYBOARD_SETTLE_MS = 150L
private const val TITLE_SIZE = 18
private const val CAPTION_SIZE = 13

/**
 * The frame of a form of the repertoire (spec 3.36.1 rule 3, 3.36.4, 5.29 R4): the bar — ✕ and [title], 56 high, 48 lying — and under
 * it one column in the middle, no wider than 560, its fields 16 from the sides and its bottom zone as wide as it, pinned over the
 * keyboard ([AppDock] with `aboveKeyboard`): what is typed and «Сохранить» are both in sight. The zone gets its [FormDockMode]: where
 * less than 200 dp are left between the bar and the keyboard, one line; where the keyboard leaves less than that line and a field
 * together (a phone lying: 892 × 412 and 640 × 360), the line leaves its place for the end of the fields, and the field in focus gets
 * the whole room over the keyboard — the fields keep their place in the tree, and the one in focus its focus; the keyboard gone, the
 * zone is pinned again. Both are measured, not guessed: the line — its row as laid out, pinned or under the fields, and the paddings
 * of the zone; the field — [appFieldLeastHeight] and the air over it at the top of the column. [loading] — an edit still being read:
 * the bar and an empty zone of the height of its button, no fields and no reason, so nothing blinks. The fields ([content]) scroll
 * under the zone, 14 apart; the one in focus comes up to the top of the column ([riseWhenFocused]). Public for the tests of the app,
 * as [KeySheetContent] is.
 */
@Composable
fun FormFrame(
    title: String,
    loading: Boolean,
    onClose: () -> Unit,
    dock: @Composable DockScope.(FormDockMode) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            FormTopBar(title, if (landscape) TopBarHeightLandscape else TopBarHeight, onClose)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                // What of this box stands over the keyboard (over the bottom inset while there is none): only measured, never drawn.
                // Padded by the insets of the keyboard the screen's host has not taken, as the zone itself is.
                val room = remember { mutableIntStateOf(UNMEASURED) }
                Spacer(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime).onSizeChanged { room.intValue = it.height })
                val metrics = currentDockMetrics().copy(side = FormSide)
                // the rows of the zone as a line, as laid out — pinned or under the fields, as wide in both places
                val lineRow = remember { mutableIntStateOf(UNMEASURED) }
                val field = FocusedAir + appFieldLeastHeight()
                // read in the derived state, never a key: on iOS every read of the insets is a new object (whose height is live)
                val keyboard = rememberUpdatedState(WindowInsets.ime)
                // derived: the keyboard rising recomposes the zone only when it turns into another mode
                val mode = remember(density, metrics, field) {
                    derivedStateOf {
                        FormDockMode.of(
                            room = room.intValue.toDpOr(density, Dp.Unspecified),
                            keyboardUp = keyboard.value.getBottom(density) > 0,
                            line = metrics.top + lineRow.intValue.toDpOr(density, LineButton) + metrics.bottom,
                            field = field,
                        )
                    }
                }
                // an edit being read has no fields to go under: its empty zone stays pinned. Derived too: a line turning into a column
                // and back recomposes only the zone, as before.
                val inContent by remember(mode, loading) { derivedStateOf { !loading && mode.value == FormDockMode.IN_CONTENT } }
                val measureLine = remember { Modifier.onSizeChanged { lineRow.intValue = it.height } }
                AppDock(
                    dock = {
                        if (loading) {
                            Spacer(Modifier.height(buttonHeight))
                        } else {
                            // the rows in a column of their own, as the zone lays them — measured while they stand as a line
                            val now = mode.value
                            DockRows(metrics, Modifier.fillMaxWidth().then(if (now == FormDockMode.ROW) measureLine else Modifier)) { dock(now) }
                        }
                    },
                    modifier = Modifier.widthIn(max = MaxColumn).fillMaxSize(),
                    aboveKeyboard = true,
                    metrics = metrics,
                    pinned = !inContent,
                ) {
                    if (!loading) {
                        FormColumn(
                            content = content,
                            // the zone under the last field: its line, the paddings of the zone under it — then the keyboard
                            end = if (inContent) {
                                { DockRows(metrics, Modifier.fillMaxWidth().padding(bottom = metrics.bottom).then(measureLine)) { dock(FormDockMode.IN_CONTENT) } }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val UNMEASURED = -1

/** Pixels measured, in dp; [otherwise] while they are not. */
private fun Int.toDpOr(density: Density, otherwise: Dp): Dp = if (this == UNMEASURED) otherwise else with(density) { toDp() }

/** The height of the window the fields scroll in, for the field in focus to come up to its top. */
@Stable
internal class FormViewport {
    var height by mutableIntStateOf(0)
}

internal val LocalFormViewport = staticCompositionLocalOf<FormViewport?> { null }

/**
 * The fields, scrolling, and after them either the room the pinned zone needs ([end] null) or [end] — the zone come under the fields.
 * The fields come first either way: they keep their place in the tree while the zone moves, and the field in focus keeps its focus.
 */
@Composable
private fun FormColumn(content: @Composable ColumnScope.() -> Unit, end: (@Composable () -> Unit)?) {
    val viewport = remember { FormViewport() }
    CompositionLocalProvider(LocalFormViewport provides viewport) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport.height = it.height }
                .verticalScroll(rememberScrollState())
                .padding(start = FormSide, end = FormSide, top = ContentTop),
            verticalArrangement = Arrangement.spacedBy(FieldGap),
        ) {
            content()
            if (end != null) {
                end()
            } else {
                // the end of the fields stands over the zone, not under it
                Spacer(Modifier.height(LocalDockInset.current + ContentEnd))
            }
        }
    }
}

/**
 * The field in focus comes up to the top of the column (spec 3.36.4, «Клавиатура»): once the keyboard has stood still for 150 ms — so
 * 150 ms after the focus where it is up already — the field and what lies under it for the height of the column are brought into
 * view, which puts the top of the field at the top of the column, 8 under its edge. The rest waits below. Only the keyboard moves the
 * column so: the bottom zone growing or shrinking over it (a reason coming or going, the plate of a twin) and the form redrawn by a
 * tap elsewhere — the tempo, the status — leave it where the player scrolled it; typing on moves it only as the field itself asks, to
 * keep its cursor in sight. A column lower than the whole field with its air — any field over the keyboard in 640 × 360, the notes
 * lying — shows the line being typed instead ([FieldRise]): the line of the cursor ([typedLine], noted by the field) with the frame's
 * bottom padding stands on the keyboard, and the caption goes up out of sight; brought up by its top, the field would hide the very
 * line being typed. Nothing outside a [FormFrame]. Public for the tests of the app.
 */
@Composable
fun riseWhenFocused(typedLine: TypedLine? = null): Modifier {
    val viewport = LocalFormViewport.current ?: return Modifier
    val requester = remember { BringIntoViewRequester() }
    val field = remember { RisingField() }
    var focused by remember { mutableStateOf(false) }
    // Read in the flow, never a key of the effect: on iOS every read of the insets is a new object (whose height is live), and a key
    // would start the rise anew at each recomposition of the form.
    val keyboard = rememberUpdatedState(WindowInsets.ime)
    val density = LocalDensity.current
    LaunchedEffect(focused, viewport, density, typedLine) {
        if (!focused) return@LaunchedEffect
        val air = with(density) { FocusedAir.toPx() }
        // the height of the keyboard alone: the column's own height and the line being typed are read when the field comes up
        snapshotFlow { keyboard.value.getBottom(density) }.collectLatest {
            delay(KEYBOARD_SETTLE_MS)
            val height = viewport.height
            if (height > 0) requester.bringIntoView(FieldRise.target(field.height.toFloat(), height.toFloat(), air, typedLine?.bounds()))
        }
    }
    return Modifier
        .bringIntoViewRequester(requester)
        .onSizeChanged { field.height = it.height }
        .onFocusChanged { focused = it.isFocused }
}

/** What of the field in focus the column shows once the keyboard stands (spec 3.36.4 «Клавиатура», 5.29 R4). Pure; pixels of the field. */
internal object FieldRise {
    /**
     * A [field] the [window] holds with the [air] over it: from its top less the air, as high as the window — the field comes to the
     * top of the column. A higher one: the line being typed wins — as high as the window, ending at the bottom of [typedLine] (the line
     * of the cursor with the frame's bottom padding under it), so the text stands on the keyboard and the caption goes up out of
     * sight; the line not laid out yet, the bottom of the field.
     */
    fun target(field: Float, window: Float, air: Float, typedLine: Rect?): Rect =
        if (window >= field + air) {
            Rect(0f, -air, 1f, window - air)
        } else {
            val bottom = typedLine?.bottom ?: field
            Rect(0f, bottom - window, 1f, bottom)
        }
}

/** The height of the field that comes up, read when it comes up: not a state — the notes growing while typed ask for nothing. */
private class RisingField {
    var height = 0
}

/** The bar of a form: ✕ (with edits it asks «Не сохранять?» first) and the title of 18 sp / 800 on one line. No «Сохранить» here. */
@Composable
private fun FormTopBar(title: String, height: Dp, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = TopBarSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val close = stringResource(Res.string.piece_form_close)
        Box(
            modifier = Modifier
                .size(TopBarButton)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = close },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Close, contentDescription = null, tint = colors.onSurface) }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = TopBarSide)
                .semantics { heading() },
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = TITLE_SIZE.sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/**
 * A caption of parts (spec 3.36.4): on screen joined by « · » — «Композитор · необязательно», «Темп · это пометка, метронома нет» —
 * and aloud by a comma, as TalkBack and VoiceOver make a pause of it («Композитор, необязательно»).
 */
@Stable
internal class Caption(val shown: String, val said: String)

@Composable
internal fun captionOf(vararg parts: String): Caption =
    Caption(shown = parts.joinToString(stringResource(Res.string.dot_separator)), said = parts.joinToString(SAID_SEPARATOR))

private const val SAID_SEPARATOR = ", "

/** The caption over a control that is not a field — the tempo, the status, the tonics — 13 sp / 700 in the second level, 6 above it. */
@Composable
internal fun FormCaption(caption: Caption, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = caption.shown,
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(caption.said) },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = CAPTION_SIZE.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(CaptionGap))
        content()
    }
}

/**
 * A row of choice — «Раздел», «Тональность» (spec 3.36.4, 5.29 R4): 56 at a corner of 14 on surfaceContainer, the [label] of 15 sp / 600
 * in the second level, the [value] of 16 sp / 800 on the right on one line — nothing without one — and the chevron of 24 in the third
 * level; it opens its sheet. The label keeps its width, the value gives way with an ellipsis (a long name of one's own section). One
 * button for TalkBack: «Тональность, G-dur».
 *
 * The date and the time of an event (spec 3.36.9, 5.29 R9): no [label] — the value stands on the left — and in the place of the chevron
 * the [trailing] icon of [trailingSize] (the calendar, the clock of 18), the time in the accent in tabular figures ([valueStyle], laid
 * over the style of the value); [description] — what TalkBack says of a row without a label: «Дата, понедельник, 28 сентября».
 */
@Composable
internal fun ChoiceRow(
    label: String?,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector = AppIcons.ChevronRight,
    trailingSize: Dp = IconSizes.Standalone,
    valueStyle: TextStyle = TextStyle.Default,
    description: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val said = description ?: when {
        label == null -> value.orEmpty()
        value.isNullOrEmpty() -> label
        else -> label + SAID_SEPARATOR + value
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ChoiceHeight)
            .clip(AppShapes.Control)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = said
                role = Role.Button
            }
            .padding(start = ChoiceStart, end = ChoiceEnd, top = ChoiceVertical, bottom = ChoiceVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChoiceGap),
    ) {
        if (label != null) {
            Text(
                text = label,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Text(
            text = value.orEmpty(),
            modifier = Modifier.weight(1f),
            color = valueStyle.color.takeOrElse { colors.onSurface },
            textAlign = if (label == null) TextAlign.Start else TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold).merge(valueStyle),
        )
        AppIcon(trailing, contentDescription = null, tint = ViolinTheme.textTertiary, size = trailingSize)
    }
}

/**
 * «Темп · это пометка, метронома нет» (spec 3.36.4): the stepper «− [метроном] 100 +» and the quick chips of choice — «—» first, it
 * takes the tempo away (the stepper does not), then [quick] («80 · 100 · 120» of a piece, «60 · 80» of a scale). Beside each other
 * where the column holds them, the chips under the stepper where it does not. Nothing here ever sounds (spec 2, principle 2).
 * TalkBack: the chips as «Без темпа», «темп 80».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TempoField(tempoBpm: Int?, quick: List<Int>, onStep: (Int) -> Unit, onPick: (Int?) -> Unit) {
    val none = stringResource(Res.string.practice_no_value)
    val clear = stringResource(Res.string.piece_tempo_clear)
    FormCaption(captionOf(stringResource(Res.string.piece_field_tempo), stringResource(Res.string.piece_field_tempo_hint))) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TempoGap), verticalArrangement = Arrangement.spacedBy(TempoGap)) {
            TempoStepper(
                value = tempoBpm?.toString() ?: none,
                valueDescription = tempoBpm?.let { stringResource(Res.string.piece_tempo_description, it) } ?: clear,
                onStep = onStep,
                downDescription = stringResource(Res.string.piece_tempo_slower),
                upDescription = stringResource(Res.string.piece_tempo_faster),
            )
            // one piece of the flow: the chips go under the stepper together, never one of them alone
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(TempoGap), verticalAlignment = Alignment.CenterVertically) {
                // «—» first: no tempo
                (listOf<Int?>(null) + quick).forEach { bpm ->
                    val said = bpm?.let { stringResource(Res.string.piece_tempo_description, it) } ?: clear
                    AppChip.Choice(
                        text = bpm?.toString() ?: none,
                        selected = bpm == tempoBpm,
                        onClick = { onPick(bpm) },
                        modifier = Modifier.semantics { contentDescription = said },
                    )
                }
            }
        }
    }
}

/**
 * «Удалить …» of an edit (spec 3.36.4, 5.29 R4): the last line of what scrolls, not in the zone — the bin of 20 and the word of 15 sp /
 * 700 in the colour of danger, no frame, 56 high, 24 under the field above it. The dialog is its caller's.
 */
@Composable
internal fun FormDeleteRow(text: String, onClick: () -> Unit) {
    val danger = ViolinTheme.dangerSoft
    Row(
        modifier = Modifier
            .padding(top = DeleteTop - FieldGap)
            .fillMaxWidth()
            .heightIn(min = DeleteHeight)
            .clip(AppShapes.Control)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(DeleteGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.Trash, contentDescription = null, tint = danger, size = DeleteIcon)
        Text(text, color = danger, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

/** What stands over the main button of a form and why. */
internal sealed interface FormReason {
    /** Why the button sleeps: «Без названия не сохранить», «Сначала выберите тонику» — grey with the icon, as a field's error (R1). */
    data class Line(val text: String) : FormReason

    /** Not an error but a way out: the scale that is there already, over «Открыть её» (spec 3.36.4). */
    data class Plate(val icon: ImageVector, val text: String) : FormReason
}

/**
 * The bottom zone of a form (spec 3.36.4, 5.29 R4): the main [button] as wide as the column, its [reason] over it — a line 8 above it,
 * centred, or a plate 10 above it. [FormDockMode.ROW] — little room over the keyboard: one line, the reason on the left and the
 * button of 48 on the right; the explanation does not go. [FormDockMode.IN_CONTENT] — the same line under the last field, reached by
 * scrolling. [enabled] false — the button at 0.38; the reason says why.
 */
@Composable
internal fun DockScope.FormDock(mode: FormDockMode, button: String, onClick: () -> Unit, enabled: Boolean = true, reason: FormReason? = null) {
    // the scope of the zone is a marked scope: its members are not seen from the rows inside it
    val compact = compact
    when (mode) {
        FormDockMode.COLUMN -> Column(Modifier.fillMaxWidth()) {
            when (reason) {
                is FormReason.Line -> {
                    ReasonLine(reason.text, Modifier.fillMaxWidth(), centered = true)
                    Spacer(Modifier.height(ReasonToButton))
                }
                is FormReason.Plate -> {
                    ReasonPlate(reason.icon, reason.text, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(PlateToButton))
                }
                null -> Unit
            }
            AppButton(button, onClick, Modifier.fillMaxWidth(), enabled = enabled, compact = compact)
        }
        FormDockMode.ROW, FormDockMode.IN_CONTENT -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DockRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (reason) {
                is FormReason.Line -> ReasonLine(reason.text, Modifier.weight(1f))
                is FormReason.Plate -> ReasonPlate(reason.icon, reason.text, Modifier.weight(1f))
                null -> Unit
            }
            AppButton(button, onClick, if (reason == null) Modifier.weight(1f) else Modifier, enabled = enabled, compact = true)
        }
    }
}
