package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The buttons of the redesign (spec 5.29; components.html, «Кнопки»).
private val Pill = RoundedCornerShape(percent = 50)
private val TallHeight = 56.dp
private val LowHeight = 48.dp
private val OutlineBorder = 1.5.dp
private val ReasonGap = 8.dp
private val CaptionPaddingVertical = 6.dp
private val CaptionPaddingSide = 20.dp
private const val LINE_HEIGHT = 1.15f
private const val CAPTION_LINE_HEIGHT = 1.25f
private const val CAPTION_ALPHA = 0.8f
private const val DISABLED_ALPHA = 0.38f
private const val TEXT_LINES = 2

/** The least size of the words of a button of [AppButton] with `oneLine` (spec 5.29 R7): the size of the words of a button of 48. */
private const val ONE_LINE_LEAST_SP = 15f

/** The caption of a button of one line: 13 sp, down to 12 where it does not fit (5.29 R7). */
private const val CAPTION_SP = 13f
private const val CAPTION_LEAST_SP = 12f
private const val TABULAR_FIGURES = "tnum"

/**
 * How important the action of an [AppButton] is — the weight of a button is the weight of its action (components.html):
 *
 * - [Main] — the one filled button of a screen or a sheet, in the accent: 56, a capsule, 17 sp / 800;
 * - [Outline] — the second action beside or under it: 56, a capsule, a frame of 1.5, 16 sp / 800 («Закончить занятие»);
 * - [Soft] — an action inside the content: 48 at a corner of 14 on surfaceContainerHigh, 15 sp / 700 («Изменить», «Добавить»);
 * - [Text] — a word in the accent, 48 («Все», «Разрешить доступ»);
 * - [Quiet] — the refusal of a sheet said quietly under its main button, 48, onSurfaceVariant («Не сохранять»), usually as wide as
 *   the sheet;
 * - [Danger] — «Удалить» as a coral word with the bin, 48: deleting is never filled;
 * - [DangerFilled] — the one filled dangerous button of the app, «Восстановить» (spec 3.20): coral with a dark word.
 */
enum class AppButtonStyle { Main, Outline, Soft, Text, Quiet, Danger, DangerFilled }

/**
 * The button of the redesign (spec 3.36.1, 5.29), in the [style] of its weight; a Material button underneath, so the ripple, the
 * role and the touch target are Material's. The words go on up to two lines, centred, never cut with an ellipsis; the button grows
 * for them. [icon] stands before the words — 20 dp in the buttons of 56, 18 in the ones of 48; [Danger] brings the bin by itself.
 * [trailingIcon] stands after them, in the same size and without words of its own: the arrow of «В дорогу →» (R3). [leading]
 * takes the place of [icon] with something of the caller's own before the words: the red dot of recording in «Записать дубль», or
 * the spinner while the backing is prepared (R4).
 *
 * [caption] — a second, smaller line under the words, only for [AppButtonStyle.Main] and [AppButtonStyle.Outline]: «В дорогу» ·
 * «Вена → хватает до Праги» of the home (R7); the button grows from 56 for it. [compact] makes a button of 56 one of 48 — the zone
 * of a window no higher than 360 dp ([DockScope.compact]) and the halves of a row of the zone; buttons of 48 stay as they are.
 *
 * No button is ever silently grey: a disabled one ([enabled] false) is dimmed to 0.38 in its own colours and its [reason] stands on
 * a line above it — or next to it, as [PermissionLine] stands over the sleeping key of recording, and then [reason] is not needed.
 * [reasonReserve] — the reason of a button that goes dim and bright again under the finger of a stepper («Добавить» of an empty
 * day, R3): its place above the button stays, unseen and unheard, while there is no reason, and nothing above the button moves.
 * With a [reason] or a [reasonReserve] the [modifier] belongs to the column of the reason and the button, and the button is as wide
 * as that column. [fontSize] — the words smaller than the size of the style, where a narrow button keeps them on one line
 * ([appButtonOneLineSize]); unspecified — the size of the style.
 *
 * [oneLine] — the words and the [caption] never wrap (spec 3.36.7, 5.29 R7: «В путь · Санкт-Петербург», «спишется 1 600 из
 * 47 884», «Live · Золотой зал»): each steps down where it does not fit — the words to 15 sp, the caption from 13 to 12, 0.5 sp at a
 * time ([ButtonFit]) — and then is cut with an ellipsis, measured in the button itself ([OneLineText]); the caption is tabular.
 * [keep] — the part of the [caption] that is never cut, the number: the caption is laid out as «before · keep · after», and the two
 * sides give way, each with its own ellipsis ([ButtonLine]) — in English the number of «Vienna → 1 128 to Prague» stands in the
 * middle. A button of one line still answers intrinsic measurements: a row of buttons of one height may hold it.
 *
 * [outline] — the colour of the frame of an [AppButtonStyle.Outline] where it stands on a picture, not on the screen: «Убрать» of the
 * try-on, white at 35 % on the veil over the room (spec 3.36.7, 5.29 R7); unspecified — the colour of the borders.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Main,
    icon: ImageVector? = null,
    caption: String? = null,
    enabled: Boolean = true,
    reason: String? = null,
    compact: Boolean = false,
    reasonReserve: String? = null,
    trailingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    oneLine: Boolean = false,
    keep: String? = null,
    outline: Color = Color.Unspecified,
) {
    val lines = ButtonLines(oneLine, keep)
    if (reason == null && reasonReserve == null) {
        StyledButton(text, onClick, modifier, style, icon, caption, enabled, compact, trailingIcon, leading, fontSize, lines, outline)
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            // the place the reason keeps, seen or not
            if (reasonReserve != null) Reason(reasonReserve, Modifier.alpha(0f).clearAndSetSemantics {})
            // said aloud when it appears: the button under it has just gone dim
            if (reason != null) Reason(reason, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Spacer(Modifier.height(ReasonGap))
        StyledButton(text, onClick, Modifier.fillMaxWidth(), style, icon, caption, enabled, compact, trailingIcon, leading, fontSize, lines, outline)
    }
}

/** How the words of a button stand: on up to two lines, or [oneLine] with the [keep] of its caption (see [AppButton]). */
@Immutable
private data class ButtonLines(val oneLine: Boolean, val keep: String?)

/**
 * The size of the words of an [AppButton] of [style] [width] wide that keeps [text] on one line: the size of the style where it
 * fits, else 0.5 sp smaller at a time down to [minSp] ([ButtonFit]); below it the words go on two lines at a space. [before] —
 * what stands before the words: [AppButton]'s `leading` or icon with its gap. For the key of a narrow bottom zone: «Записать дубль»
 * beside «Видео-дубль» in the left column of 300 lying (spec 3.36.4, 5.29 R4).
 */
@Composable
fun appButtonOneLineSize(text: String, width: Dp, style: AppButtonStyle, compact: Boolean, before: Dp, minSp: Float): TextUnit {
    val look = lookOf(style, compact)
    val words = wordsStyleOf(look)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, width, look, words, before, minSp, measurer, density) {
        val room = with(density) { (width - look.padding * 2 - before - OneLineSlack).toPx() }
        ButtonFit.size(room, look.fontSize.value, minSp) { sizeSp ->
            measurer.measure(text, words.copy(fontSize = sizeSp.sp, lineHeight = sizeSp.sp * LINE_HEIGHT), softWrap = false, maxLines = 1).size.width.toFloat()
        }.sp
    }
}

/** A button is laid out in whole pixels: words that fit only by a hair are not trusted. */
private val OneLineSlack = 1.dp

/** The size of the words of an [AppButton] and how wide it then stands ([appButtonBeside]). */
@Immutable
data class ButtonFitted(val fontSize: TextUnit, val width: Dp)

/**
 * The largest size of the words of an [AppButton] of [style] with [text] on one line at which the button is no wider than [width] —
 * the size of its style where it is, else 0.5 sp smaller at a time down to [minSp] ([ButtonFit]) — and how wide it then stands: its
 * fields and its words, whole, and never under Material's least width of a button. Null where not even [minSp] makes it that narrow:
 * the caller stands it elsewhere. For a button beside words of its own that must keep their room — «Остановить» in the card «Сейчас»
 * of «Что играем» (spec 5.29 R6), which goes under its lines where it does not fit beside them.
 */
@Composable
fun appButtonBeside(text: String, width: Dp, style: AppButtonStyle, compact: Boolean, minSp: Float): ButtonFitted? {
    val look = lookOf(style, compact)
    val words = wordsStyleOf(look)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, width, look, words, minSp, measurer, density) {
        with(density) {
            val fields = (look.padding * 2 + OneLineSlack).toPx()
            val least = ButtonDefaults.MinWidth.toPx()
            val widthAt = { sizeSp: Float ->
                val line = measurer.measure(text, words.copy(fontSize = sizeSp.sp, lineHeight = sizeSp.sp * LINE_HEIGHT), softWrap = false, maxLines = 1)
                maxOf(line.size.width + fields, least)
            }
            val room = width.toPx()
            ButtonFit.sharedSize(maxSp = look.fontSize.value, minSp = minSp) { sizeSp -> widthAt(sizeSp) - room }
                ?.let { sizeSp -> ButtonFitted(sizeSp.sp, widthAt(sizeSp).toDp()) }
        }
    }
}

/**
 * The one size of the words of [AppButton]s standing side by side, each [width] wide (a row of halves), at which every word of each
 * stays whole on its line — the words may go on two lines, but only at a space (spec 3.36.4: no word breaks inside, in the forms
 * too): the size of their styles where the widest word of each fits its button, else all of them 0.5 sp smaller together down to
 * [minSp] ([ButtonFit.sharedSize]). Null where not even [minSp] keeps every word whole: the caller stands the buttons one under the
 * other. For «Без тональности · Готово» of the sheet «Тональность» on a phone of 360 with a large font (5.29 R4). Words are split
 * at spaces only: a non-breaking space keeps its two words one. [icons] — each button has an icon before its words, its size by the
 * style with its gap: «Лавка» and «Обставить» of the zone of the home (5.29 R7); [AppButtonStyle.Danger] brings its bin anyway.
 */
@Composable
fun appButtonsSharedSize(buttons: List<Pair<String, AppButtonStyle>>, width: Dp, compact: Boolean, minSp: Float, icons: Boolean = false): TextUnit? {
    val looks = buttons.map { (_, style) -> lookOf(style, compact) }
    val words = looks.map { wordsStyleOf(it) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(buttons, width, looks, words, minSp, icons, measurer, density) {
        val rooms = buttons.mapIndexed { i, (_, style) ->
            val icon = if (icons || style == AppButtonStyle.Danger) looks[i].icon + IconSizes.ButtonGap else 0.dp
            with(density) { (width - looks[i].padding * 2 - icon - OneLineSlack).toPx() }
        }
        val split = buttons.map { (text, _) -> text.split(' ', '\n', '\t').filter { it.isNotEmpty() } }
        ButtonFit.sharedSize(maxSp = looks.minOf { it.fontSize.value }, minSp = minSp) { sizeSp ->
            buttons.indices.maxOf { i ->
                val style = words[i].copy(fontSize = sizeSp.sp, lineHeight = sizeSp.sp * LINE_HEIGHT)
                val widest = split[i].maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width.toFloat() } ?: 0f
                widest - rooms[i]
            }
        }?.sp
    }
}

/**
 * The narrowest an [AppButton] of [style] with [text] can be with each of its words whole on its line — its words go on up to two
 * lines, but a word is not broken: its fields, the icon with its gap ([icon]; [AppButtonStyle.Danger] has its bin anyway) and the
 * widest word, split at spaces. For a column that must not grow narrower than its button — the left column of «Репертуар» in
 * landscape (5.29 R4). Buttons with a caption are not measured.
 */
@Composable
fun appButtonMinWidth(text: String, style: AppButtonStyle = AppButtonStyle.Main, icon: Boolean = false, compact: Boolean = false): Dp {
    val look = lookOf(style, compact)
    val words = wordsStyleOf(look)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val withIcon = icon || style == AppButtonStyle.Danger
    return remember(text, look, words, withIcon, density, measurer) {
        val widest = text.split(' ', '\n', '\t').filter { it.isNotEmpty() }
            .maxOfOrNull { measurer.measure(it, words, softWrap = false, maxLines = 1).size.width } ?: 0
        look.padding * 2 + (if (withIcon) look.icon + IconSizes.ButtonGap else 0.dp) + with(density) { widest.toDp() }
    }
}

/**
 * How wide an [AppButton] of [style] stands with [text] on one line at the size of its style: its fields, the icon with its gap
 * ([icon]; [AppButtonStyle.Danger] has its bin anyway) and the words, never under Material's least width of a button. For a button as
 * wide as its words with something in the room it leaves: «Начать» of the introduction lying, with «У меня есть копия данных» beside
 * it (spec 3.36.8).
 */
@Composable
fun appButtonWidth(text: String, style: AppButtonStyle = AppButtonStyle.Main, icon: Boolean = false, compact: Boolean = false): Dp {
    val look = lookOf(style, compact)
    val words = wordsStyleOf(look)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val withIcon = icon || style == AppButtonStyle.Danger
    return remember(text, look, words, withIcon, measurer, density) {
        val line = measurer.measure(text, words, softWrap = false, maxLines = 1).size.width
        val iconPart = if (withIcon) look.icon + IconSizes.ButtonGap else 0.dp
        maxOf(look.padding * 2 + iconPart + with(density) { line.toDp() } + OneLineSlack, ButtonDefaults.MinWidth)
    }
}

/**
 * Whether the words of an [AppButton] of [style] stand in a button [width] wide on no more than [lines] lines with every word whole —
 * broken only at a space ([WholeWords]), the way the button lays them out. For a word button beside another one where it may take two
 * lines but not three, nor break a word: «У меня есть копия данных» beside «Начать» lying goes under it where it does not (spec
 * 3.36.8). [icon] — an icon before the words, its size by the style with its gap.
 */
@Composable
fun appButtonFitsLines(text: String, width: Dp, style: AppButtonStyle, lines: Int = TEXT_LINES, compact: Boolean = false, icon: Boolean = false): Boolean {
    val look = lookOf(style, compact)
    val words = wordsStyleOf(look)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val withIcon = icon || style == AppButtonStyle.Danger
    return remember(text, width, look, words, lines, withIcon, measurer, density, direction) {
        val iconPart = if (withIcon) look.icon + IconSizes.ButtonGap else 0.dp
        val room = with(density) { (width - look.padding * 2 - iconPart - OneLineSlack).roundToPx() }
        if (room <= 0) return@remember false
        val layout = measurer.measure(text, words, constraints = Constraints(maxWidth = room), layoutDirection = direction, density = density)
        layout.lineCount <= lines && WholeWords.of(layout)
    }
}

@Composable
private fun Reason(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    )
}

@Composable
private fun StyledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    style: AppButtonStyle,
    icon: ImageVector?,
    caption: String?,
    enabled: Boolean,
    compact: Boolean,
    trailingIcon: ImageVector?,
    leading: (@Composable () -> Unit)?,
    fontSize: TextUnit,
    lines: ButtonLines,
    outline: Color,
) {
    val look = lookOf(style, compact).let { if (fontSize.isSpecified) it.copy(fontSize = fontSize) else it }
    val shown = icon ?: if (style == AppButtonStyle.Danger) AppIcons.Trash else null
    val withCaption = caption != null && (style == AppButtonStyle.Main || style == AppButtonStyle.Outline)
    Button(
        onClick = onClick,
        enabled = enabled,
        // dimmed as a whole in its own colours: the disabled colours of Material would repaint it in its own grey
        modifier = modifier
            .heightIn(min = look.height)
            .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)),
        shape = look.shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = look.container,
            contentColor = look.content,
            disabledContainerColor = look.container,
            disabledContentColor = look.content,
        ),
        elevation = null,
        border = if (look.border) BorderStroke(OutlineBorder, outline.takeOrElse { MaterialTheme.colorScheme.outlineVariant }) else null,
        contentPadding = if (withCaption) {
            PaddingValues(horizontal = CaptionPaddingSide, vertical = CaptionPaddingVertical)
        } else {
            PaddingValues(horizontal = look.padding)
        },
    ) {
        val words = wordsStyleOf(look)
        if (leading != null) {
            leading()
            Spacer(Modifier.width(IconSizes.ButtonGap))
        } else if (shown != null) {
            AppIcon(shown, contentDescription = null, size = look.icon)
            Spacer(Modifier.width(IconSizes.ButtonGap))
        }
        if (lines.oneLine) {
            // what the icons leave, and no more: the words and the caption find their size in it, and the block stays in the middle
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (withCaption) Alignment.Start else Alignment.CenterHorizontally) {
                // with a caption the two lines must stand in the 48 of a low window: exact lines (ExactLines) — Android would pad each
                // back to Manrope's own 1.37 em, and the button grew to 50.7 (stage 117, the emulator lying at 603 × 308)
                OneLineText(
                    text = text,
                    style = words.copy(
                        lineHeight = look.fontSize * if (withCaption) CAPTION_LINE_HEIGHT else LINE_HEIGHT,
                        lineHeightStyle = if (withCaption) ExactLines else words.lineHeightStyle,
                    ),
                    minSp = ONE_LINE_LEAST_SP,
                )
                if (withCaption) {
                    val content = LocalContentColor.current
                    OneLineText(
                        text = caption.orEmpty(),
                        style = words.copy(
                            fontSize = CAPTION_SP.sp,
                            lineHeight = (CAPTION_SP * CAPTION_LINE_HEIGHT).sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = TABULAR_FIGURES,
                            lineHeightStyle = ExactLines,
                        ),
                        minSp = CAPTION_LEAST_SP,
                        color = content.copy(alpha = content.alpha * CAPTION_ALPHA),
                        keep = lines.keep,
                    )
                }
            }
        } else if (withCaption) {
            // the words and the caption under them start at one edge, beside the icon, as one block in the middle of the button
            Column(horizontalAlignment = Alignment.Start) {
                Text(text, style = words.copy(lineHeight = look.fontSize * CAPTION_LINE_HEIGHT), maxLines = TEXT_LINES)
                Text(
                    text = caption.orEmpty(),
                    modifier = Modifier.alpha(CAPTION_ALPHA),
                    style = words.copy(fontSize = 13.sp, lineHeight = 13.sp * CAPTION_LINE_HEIGHT, fontWeight = FontWeight.SemiBold),
                    maxLines = TEXT_LINES,
                )
            }
        } else {
            // with an icon after them the words take what the icons leave, so the arrow is never pushed out
            Text(
                text,
                modifier = if (trailingIcon != null) Modifier.weight(1f, fill = false) else Modifier,
                style = words,
                textAlign = TextAlign.Center,
                maxLines = TEXT_LINES,
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(IconSizes.ButtonGap))
            AppIcon(trailingIcon, contentDescription = null, size = look.icon)
        }
    }
}

/** The words of a button of [look] — the same style they are drawn and measured in ([appButtonMinWidth]). */
@Composable
private fun wordsStyleOf(look: ButtonLook): TextStyle =
    MaterialTheme.typography.labelLarge.copy(fontSize = look.fontSize, lineHeight = look.fontSize * LINE_HEIGHT, fontWeight = look.weight)

/** What a style looks like, all in one place. */
@Immutable
private data class ButtonLook(
    val height: Dp,
    val shape: Shape,
    val container: Color,
    val content: Color,
    val border: Boolean,
    val fontSize: TextUnit,
    val weight: FontWeight,
    val padding: Dp,
    val icon: Dp,
)

@Composable
private fun lookOf(style: AppButtonStyle, compact: Boolean): ButtonLook {
    val colors = MaterialTheme.colorScheme
    val tall = if (compact) LowHeight else TallHeight
    val tallPadding = if (compact) 16.dp else 24.dp
    return when (style) {
        AppButtonStyle.Main -> ButtonLook(
            height = tall, shape = Pill, container = colors.primary, content = colors.onPrimary, border = false,
            fontSize = if (compact) 15.sp else 17.sp, weight = FontWeight.ExtraBold, padding = tallPadding, icon = IconSizes.InFilledButton,
        )
        AppButtonStyle.Outline -> ButtonLook(
            height = tall, shape = Pill, container = Color.Transparent, content = colors.onSurface, border = true,
            fontSize = if (compact) 15.sp else 16.sp, weight = FontWeight.ExtraBold, padding = tallPadding, icon = IconSizes.InFilledButton,
        )
        AppButtonStyle.DangerFilled -> ButtonLook(
            height = tall, shape = Pill, container = ViolinTheme.dangerSoft, content = ViolinTheme.onDanger, border = false,
            fontSize = if (compact) 15.sp else 16.sp, weight = FontWeight.ExtraBold, padding = tallPadding, icon = IconSizes.InFilledButton,
        )
        AppButtonStyle.Soft -> ButtonLook(
            height = LowHeight, shape = AppShapes.Control, container = colors.surfaceContainerHigh, content = colors.onSurface, border = false,
            fontSize = 15.sp, weight = FontWeight.Bold, padding = 16.dp, icon = IconSizes.InButton,
        )
        AppButtonStyle.Text -> ButtonLook(
            height = LowHeight, shape = AppShapes.Control, container = Color.Transparent, content = colors.primary, border = false,
            fontSize = 15.sp, weight = FontWeight.Bold, padding = 12.dp, icon = IconSizes.InButton,
        )
        AppButtonStyle.Quiet -> ButtonLook(
            height = LowHeight, shape = AppShapes.Control, container = Color.Transparent, content = colors.onSurfaceVariant, border = false,
            fontSize = 15.sp, weight = FontWeight.Bold, padding = 12.dp, icon = IconSizes.InButton,
        )
        AppButtonStyle.Danger -> ButtonLook(
            height = LowHeight, shape = AppShapes.Control, container = Color.Transparent, content = ViolinTheme.dangerSoft, border = false,
            fontSize = 15.sp, weight = FontWeight.Bold, padding = 12.dp, icon = IconSizes.InButton,
        )
    }
}
