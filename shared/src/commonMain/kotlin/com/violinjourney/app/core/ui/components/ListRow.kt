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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"

/** What stands at the end of a [ListRow]. */
@Immutable
sealed interface ListRowEnd {
    /** A chevron in the third level of text: the row opens something. */
    data object Chevron : ListRowEnd

    /** Nothing. */
    data object None : ListRowEnd

    /** An arrow of 24 in the colour of the words: the row leads on to more of the same («Все трофеи» of «Мой путь»). */
    data object Arrow : ListRowEnd

    /** A value in tabular figures, onSurfaceVariant: «440 Гц», «Средний ±8». */
    data class Value(val text: String) : ListRowEnd

    /** The switch of the app ([AppSwitchMark], 52 × 32); the whole row toggles it. */
    data class Toggle(val checked: Boolean) : ListRowEnd

    /** A check in the accent on the chosen one of a list of choices; the whole row chooses. */
    data class Check(val checked: Boolean) : ListRowEnd
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
 * toggled as a switch ([ListRowEnd.Toggle] — the switch draws only, as `AnalyticsRow` does), or chosen as a radio button
 * ([ListRowEnd.Check]); [onClick] hears it in every case. [accent] — the words and the icon in the accent, bolder, no chevron;
 * «Все трофеи» asks for the arrow ([ListRowEnd.Arrow]) in the accent too. [enabled] false dims the icon, the words and the end to 0.38 and the row is not pressed; its ground stays whole
 * — dimmed, it would let the lines of the group show through and stand out lighter than its neighbours — and so does the
 * [caption]: the caption of a dimmed row says why (R8, «Сначала дождитесь…»), and at 0.38 it would not read.
 *
 * [strong] — the words at 700 instead of 600: the rows of the sheets of R4, «Что добавить?» and «Раздел» (5.29 R4), where each row
 * names a kind of thing rather than a setting. [singleLine] — the words stay on one line and end in an ellipsis: a name the player
 * gave, «В «Двойные ноты»», which may be long.
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
) {
    val colors = MaterialTheme.colorScheme
    val ground = LocalListGroupGround.current
    val press = when (end) {
        is ListRowEnd.Toggle -> Modifier.toggleable(value = end.checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
        is ListRowEnd.Check -> Modifier.selectable(selected = end.checked, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        else -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    // the ground and the caption stay whole: only what names and marks the row is dimmed
    val dim = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)
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
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                modifier = dim,
                color = words,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (accent || strong) FontWeight.Bold else FontWeight.SemiBold,
                ),
            )
            if (caption != null) {
                Text(caption, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.5.sp))
            }
        }
        RowEnd(if (accent && end == ListRowEnd.Chevron) ListRowEnd.None else end, dim, words)
    }
}

@Composable
private fun RowEnd(end: ListRowEnd, modifier: Modifier, words: Color) {
    val colors = MaterialTheme.colorScheme
    when (end) {
        ListRowEnd.Chevron -> AppIcon(AppIcons.ChevronRight, contentDescription = null, modifier = modifier, tint = ViolinTheme.textTertiary)
        ListRowEnd.Arrow -> AppIcon(AppIcons.ArrowRight, contentDescription = null, modifier = modifier, tint = words)
        ListRowEnd.None -> Unit
        is ListRowEnd.Value -> Text(
            text = end.text,
            modifier = modifier,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
        )
        is ListRowEnd.Toggle -> AppSwitchMark(checked = end.checked, modifier = modifier)
        // the unchosen keep the place of the check: the words of a list of choices do not move
        is ListRowEnd.Check -> if (end.checked) {
            AppIcon(AppIcons.Check, contentDescription = null, modifier = modifier, tint = colors.primary)
        } else {
            Spacer(Modifier.size(IconSizes.Standalone))
        }
    }
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
