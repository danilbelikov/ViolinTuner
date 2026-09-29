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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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
 * [trailingIcon] stands after them, in the same size and without words of its own: the arrow of «В дорогу →» (R3).
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
 * as that column.
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
) {
    if (reason == null && reasonReserve == null) {
        StyledButton(text, onClick, modifier, style, icon, caption, enabled, compact, trailingIcon)
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
        StyledButton(text, onClick, Modifier.fillMaxWidth(), style, icon, caption, enabled, compact, trailingIcon)
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
) {
    val look = lookOf(style, compact)
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
        border = if (look.border) BorderStroke(OutlineBorder, MaterialTheme.colorScheme.outlineVariant) else null,
        contentPadding = if (withCaption) {
            PaddingValues(horizontal = CaptionPaddingSide, vertical = CaptionPaddingVertical)
        } else {
            PaddingValues(horizontal = look.padding)
        },
    ) {
        val words = wordsStyleOf(look)
        if (shown != null) {
            AppIcon(shown, contentDescription = null, size = look.icon)
            Spacer(Modifier.width(IconSizes.ButtonGap))
        }
        if (withCaption) {
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
