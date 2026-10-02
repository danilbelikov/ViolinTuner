package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The row of a list (spec 5.29; components.html, «Строки»).
private val RowHeight = 56.dp
private val RowPaddingSide = 16.dp
private val RowPaddingVertical = 8.dp
private val RowGap = 14.dp
private val GroupLine = 1.dp

/** From the caption to what stands under it — the thin bar of a copy on its way (spec 5.29 R8). */
private val BelowCaption = 8.dp

/** Between a value and the chevron after it («Язык», spec 5.29 R8). */
private val ValueToChevron = 4.dp
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"

/**
 * The words of a row, 16 sp — and where a word of them does not stand whole in its line (360 dp at the font 1.3: «конфиденциальности»,
 * «Datenschutzerklärung»), all of them a step smaller, 0.5 sp at a time, down to the size of its caption, 13 sp — as the names of the
 * sections of R4: no smaller than 13 (spec 5.29 R4, R8; stage 121). Past it the word breaks: the limit.
 */
private const val WORDS_SP = 16f
private const val WORDS_LEAST_SP = 13f
private val WordsFit = WholeWordsFit(WORDS_SP, WORDS_LEAST_SP)

/**
 * The caption, 13 sp — and where a word of it does not stand whole in its line (320 dp at the font 1.5: de «Wiederherstellung», 181 dp
 * in 180), a step smaller, down to 12 sp, the least of the small words of R1 and R8 (stage 121). Past it the word breaks: the limit.
 */
private const val CAPTION_SP = 13f
private const val CAPTION_LEAST_SP = 12f
private val CaptionFit = WholeWordsFit(CAPTION_SP, CAPTION_LEAST_SP)

/** What an empty caption shows: nothing, one line high. */
private const val HELD_LINE = "\u00A0"

/** What stands at the end of a [ListRow]. */
@Immutable
sealed interface ListRowEnd {
    /** A chevron in the third level of text: the row opens something. */
    data object Chevron : ListRowEnd

    /** Nothing. */
    data object None : ListRowEnd

    /** An arrow of 24 in the colour of the words: the row leads on to more of the same («Все трофеи» of «Мой путь»). */
    data object Arrow : ListRowEnd

    /**
     * A value in tabular figures, onSurfaceVariant: «440 Гц», «Средний ±8». With [chevron] the chevron of the third level follows it —
     * the row shows what is set and opens another screen to change it («Язык» of «Настройки», spec 3.36.8).
     */
    data class Value(val text: String, val chevron: Boolean = false) : ListRowEnd

    /**
     * The switch of the app ([AppSwitchMark], 52 × 32); the whole row toggles it. [checked] null — not known yet (the settings not
     * read, spec 3.36.8 «Загрузка»): the place of the switch is held empty and the row toggles nothing, so a default never flips to
     * the setting before the eye.
     */
    data class Toggle(val checked: Boolean?) : ListRowEnd

    /** A check in the accent on the chosen one of a list of choices; the whole row chooses. */
    data class Check(val checked: Boolean) : ListRowEnd

    /**
     * A word in the accent, a text button of 48 ([AppButtonStyle.Text]) — «Разрешить доступ» of «Записать звук» without the microphone
     * (spec 3.36.9): the row itself is not pressed then, only the word. It stands beside the words while they keep their lines, else
     * under them at the end of the row; the caption — the reason — is never cut.
     */
    data class TextAction(val label: String, val onClick: () -> Unit) : ListRowEnd
}

/**
 * The ground a row inside a [ListGroup] paints itself with; unspecified outside a group — a row on a screen or in a sheet has none.
 * Any other child of a group paints its ground with it too, or the lines of the group show through it whole.
 */
val LocalListGroupGround = staticCompositionLocalOf { Color.Unspecified }

/**
 * A row of a list (spec 3.36.1, 5.29): 56 dp at the least (≈ 64 with a [caption] on two lines), an [icon] of 24 — or, in its
 * place, anything of its own ([leading]: the photo of 32 at «Имя и фото», 3.36.2) — the [text] of 16 sp / 600 and, at the
 * [end], a chevron, an arrow, a value, a switch or a check. The whole row is one target: pressed as a button, or
 * toggled as a switch ([ListRowEnd.Toggle] — the switch draws only: «Помогать улучшать приложение», R8), or chosen as a radio button
 * ([ListRowEnd.Check]); [onClick] hears it in every case. [accent] — the words and the icon in the accent, bolder, no chevron;
 * «Все трофеи» asks for the arrow ([ListRowEnd.Arrow]) in the accent too. [enabled] false dims the icon, the words and the end to 0.38 and the row is not pressed; its ground stays whole
 * — dimmed, it would let the lines of the group show through and stand out lighter than its neighbours — and so does the
 * [caption]: the caption of a dimmed row says why (R8, «сначала дождитесь…»), and at 0.38 it would not read.
 *
 * [strong] — the words at 700 instead of 600: the rows of the sheets of R4, «Что добавить?» and «Раздел» (5.29 R4), where each row
 * names a kind of thing rather than a setting. [singleLine] — the words stay on one line and end in an ellipsis: a name the player
 * gave, «В «Двойные ноты»», which may be long. Otherwise the words wrap at their spaces and a word is not broken while it can stand:
 * where one does not stand whole at 16 sp, the words step down to 13 ([WordsFit]), and a value at the end gives way to their widest
 * word, cut with an ellipsis («Язык» on 320 at the font 1.5: «Sprache» whole, «Deutsch» cut; as the row of choice of a form, R4).
 *
 * The [caption] wraps at its spaces and is never cut — a word that does not stand whole takes it a step smaller, down to 12 sp
 * ([CaptionFit]) — its figures tabular: «56 % · Видео 7 из 12» does not shift as its numbers change; an empty
 * one holds the place of its line and says nothing (the date of the last copy not read yet, spec 3.36.8). [below] — what stands 8
 * under the caption, whole even in a dimmed row: the thin bar of a copy or a restore on its way («Данные» of «Настройки», R8).
 *
 * Inside a [ListGroup] the row paints the ground of the group; outside one it has no ground of its own — a row of a sheet.
 */
@Composable
fun ListRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    caption: String? = null,
    end: ListRowEnd = ListRowEnd.Chevron,
    accent: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    strong: Boolean = false,
    singleLine: Boolean = false,
    below: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val ground = LocalListGroupGround.current
    val press = when (end) {
        // nothing to toggle while it is not known: no switch, no press
        is ListRowEnd.Toggle -> end.checked?.let { checked -> Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() }) } ?: Modifier
        is ListRowEnd.Check -> Modifier.selectable(selected = end.checked, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        // only the word at the end is pressed
        is ListRowEnd.TextAction -> Modifier
        else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    // the ground and the caption stay whole: only what names and marks the row is dimmed
    val dim = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)
    val wordsStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = WORDS_SP.sp,
        lineHeight = 22.sp,
        fontWeight = if (accent || strong) FontWeight.Bold else FontWeight.SemiBold,
    )
    // a value at the end leaves the widest word of the words its room, and the gap before the end; the chevron after it stays
    val giveWay = if (end is ListRowEnd.Value && !singleLine) {
        val reserved = widestWord(text, wordsStyle)
        val density = LocalDensity.current
        val gap = with(density) { RowGap.roundToPx() }
        val least = if (end.chevron) with(density) { (IconSizes.Standalone + ValueToChevron).roundToPx() } else 0
        Modifier.giveWayTo(reserved + gap, least)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // in a group the group rounds the corners; alone, the ripple keeps a corner of its own
            .then(if (ground.isSpecified) Modifier.background(ground) else Modifier.clip(AppShapes.S))
            .then(press)
            .heightIn(min = RowHeight)
            .padding(horizontal = RowPaddingSide, vertical = RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        val lead = if (accent) colors.primary else colors.onSurfaceVariant
        val words = if (accent) colors.primary else colors.onSurface
        when {
            leading != null -> Box(dim) { leading() }
            icon != null -> AppIcon(icon, contentDescription = null, modifier = dim, tint = lead)
        }
        val wordsColumn: @Composable (Modifier) -> Unit = { columnModifier ->
            Column(columnModifier) {
                Text(
                    text = text,
                    modifier = dim,
                    color = words,
                    autoSize = if (singleLine) null else WordsFit,
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                    style = wordsStyle,
                )
                if (caption != null) {
                    Text(
                        // an empty caption holds the place of its line: a no-break space is a line of the same height, and it is silent
                        text = caption.ifEmpty { HELD_LINE },
                        modifier = if (caption.isEmpty()) Modifier.clearAndSetSemantics {} else Modifier,
                        color = colors.onSurfaceVariant,
                        autoSize = CaptionFit,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = CAPTION_SP.sp, lineHeight = 17.5.sp, fontFeatureSettings = TABULAR_FIGURES),
                    )
                }
                if (below != null) {
                    Spacer(Modifier.height(BelowCaption))
                    below()
                }
            }
        }
        if (end is ListRowEnd.TextAction) {
            WordsAndAction(
                words = { wordsColumn(Modifier) },
                action = { AppButton(end.label, onClick = end.onClick, modifier = dim, style = AppButtonStyle.Text) },
                modifier = Modifier.weight(1f),
            )
        } else {
            wordsColumn(Modifier.weight(1f))
            RowEnd(if (accent && end == ListRowEnd.Chevron) ListRowEnd.None else end, dim, words, giveWay)
        }
    }
}

/**
 * The words of a row and the word at its end ([ListRowEnd.TextAction]): side by side while the words stand beside it on no more lines
 * than they would across the whole row — the caption, the reason, is never cut (spec 3.36.9: up to two lines); otherwise the word goes
 * under them, at the end of the row, and the reason takes the whole width. On a phone upright that is under: «Разрешить доступ» beside
 * «Чтобы записать звук, нужен доступ к микрофону.» would leave it a third of the row. Decided by the intrinsic heights, measured once.
 */
@Composable
private fun WordsAndAction(words: @Composable () -> Unit, action: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(contents = listOf(words, action), modifier = modifier) { (wordsMeasurables, actionMeasurables), constraints ->
        val wordsPart = wordsMeasurables.single()
        val actionPart = actionMeasurables.single()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val full = loose.maxWidth
        val gap = RowGap.roundToPx()
        val actionWidth = actionPart.maxIntrinsicWidth(loose.maxHeight).coerceAtMost(full)
        val besideWidth = (full - actionWidth - gap).coerceAtLeast(0)
        val beside = besideWidth > 0 && wordsPart.minIntrinsicHeight(besideWidth) <= wordsPart.minIntrinsicHeight(full)
        val placedWords = wordsPart.measure(loose.copy(maxWidth = if (beside) besideWidth else full))
        val placedAction = actionPart.measure(loose)
        if (beside) {
            val height = maxOf(placedWords.height, placedAction.height)
            layout(full, height) {
                placedWords.placeRelative(0, (height - placedWords.height) / 2)
                placedAction.placeRelative(full - placedAction.width, (height - placedAction.height) / 2)
            }
        } else {
            layout(full, placedWords.height + placedAction.height) {
                placedWords.placeRelative(0, 0)
                placedAction.placeRelative(full - placedAction.width, placedWords.height)
            }
        }
    }
}

/**
 * The widest word of [text] in [style], in pixels: what the words of a row keep beside a value at its end, so that the value gives
 * way rather than a word breaking.
 */
@Composable
private fun widestWord(text: String, style: TextStyle): Int {
    val measurer = rememberTextMeasurer()
    return remember(text, style, measurer) {
        text.split(' ', '\n', '\t').filter { it.isNotEmpty() }.maxOfOrNull { word -> measurer.measure(word, style, softWrap = false, maxLines = 1).size.width } ?: 0
    }
}

/**
 * What stands at the end of a row, measured in what the row leaves it once [reserved] px stand before it — the widest word of the words
 * and the gap to the end — but never narrower than [least] px (the chevron after a value and its gap): a value then gives way with an
 * ellipsis. Where the room is not short it is measured as it would be without this, to the pixel.
 */
private fun Modifier.giveWayTo(reserved: Int, least: Int): Modifier = layout { measurable, constraints ->
    val room = if (constraints.hasBoundedWidth) (constraints.maxWidth - reserved).coerceAtLeast(least).coerceIn(constraints.minWidth, constraints.maxWidth) else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(maxWidth = room))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** [giveWay] — what a value at the end measures in ([giveWayTo]): it gives way to the widest word of the words before it. */
@Composable
private fun RowEnd(end: ListRowEnd, modifier: Modifier, words: Color, giveWay: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    when (end) {
        ListRowEnd.Chevron -> AppIcon(AppIcons.ChevronRight, contentDescription = null, modifier = modifier, tint = ViolinTheme.textTertiary)
        ListRowEnd.Arrow -> AppIcon(AppIcons.ArrowRight, contentDescription = null, modifier = modifier, tint = words)
        ListRowEnd.None -> Unit
        is ListRowEnd.Value -> if (end.chevron) {
            // the value and the chevron together; a long value gives way to the chevron and to the words, not the other way round
            Row(giveWay.then(modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ValueToChevron)) {
                RowValue(end.text, Modifier.weight(1f, fill = false))
                AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = ViolinTheme.textTertiary)
            }
        } else {
            RowValue(end.text, giveWay.then(modifier))
        }
        is ListRowEnd.Toggle -> if (end.checked != null) {
            AppSwitchMark(checked = end.checked, modifier = modifier)
        } else {
            Spacer(Modifier.size(AppSwitchSize))
        }
        // the unchosen keep the place of the check: the words of a list of choices do not move
        is ListRowEnd.Check -> if (end.checked) {
            AppIcon(AppIcons.Check, contentDescription = null, modifier = modifier, tint = colors.primary)
        } else {
            Spacer(Modifier.size(IconSizes.Standalone))
        }
        // laid out with the words ([WordsAndAction])
        is ListRowEnd.TextAction -> Unit
    }
}

/** The value at the end of a row: 15 sp, 700, tabular figures, onSurfaceVariant, one line. */
@Composable
private fun RowValue(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
    )
}

/**
 * A group of rows (spec 5.29): a card at a corner of 18 on surfaceContainer, a line of 1 dp between rows across the whole width. The
 * lines are the colour of the group showing through the gaps between rows that paint their own ground ([LocalListGroupGround]), so
 * a row that is not shown leaves no line behind.
 */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalListGroupGround provides colors.surfaceContainer) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(AppShapes.M)
                .background(colors.outlineVariant),
            verticalArrangement = Arrangement.spacedBy(GroupLine),
            content = content,
        )
    }
}
