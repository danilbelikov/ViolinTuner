package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The menu of the redesign (spec 3.36.1, 5.29; components.html, «Меню ⋯»).
private val MenuMinWidth = 200.dp
private val MenuMaxWidth = 280.dp
private val MenuPaddingSide = 6.dp
/** What the card of a preview gives above and below its rows, as the popup of Material does. */
private val MenuPaddingVertical = 8.dp
private val RowHeight = 48.dp
private val RowPaddingSide = 12.dp
private val RowPaddingCaption = 8.dp
private val RowGap = 12.dp
private val DividerSide = 8.dp
private val DividerVertical = 4.dp
private const val DISABLED_ALPHA = 0.38f

/**
 * The dangerous item of a menu (spec 3.36.1): always the last, after a line, in the colour of danger with its icon. [divider] false —
 * no line over it: the item stands alone, as «Удалить…» of a recording without sound that is not a take (spec 3.36.5).
 */
class MenuDanger(val text: String, val onClick: () -> Unit, val icon: ImageVector = AppIcons.Trash, val divider: Boolean = true)

/**
 * The one look of the drop-down menus (spec 3.36.1, 5.29) — «⋯» of a recording, a section and a backing track, the section of a
 * form, the status and «+» of a piece, the menu of «Видео»: the colour of a dialog, a corner of 18, 200–280 dp wide, rows of 48 with
 * an icon of 24 where the icon means something. [danger] goes last, after a line. The items close the menu themselves.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    danger: MenuDanger? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(MenuMinWidth, MenuMaxWidth).then(modifier),
        shape = AppShapes.M,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        MenuRows(danger, content)
    }
}

/** The card of [AppMenu] without its popup: a preview cannot draw a popup, and a screen never needs the card alone. */
@Composable
fun AppMenuCard(modifier: Modifier = Modifier, danger: MenuDanger? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(MenuMinWidth, MenuMaxWidth)
            .then(modifier)
            .width(IntrinsicSize.Max)
            .clip(AppShapes.M)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = MenuPaddingVertical),
    ) {
        MenuRows(danger, content)
    }
}

@Composable
private fun MenuRows(danger: MenuDanger?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = MenuPaddingSide)) {
        content()
        if (danger != null) {
            if (danger.divider) {
                Box(
                    Modifier
                        .padding(horizontal = DividerSide, vertical = DividerVertical)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            MenuRow(danger.text, danger.onClick, danger.icon, caption = null, selected = false, enabled = true, tint = ViolinTheme.dangerSoft)
        }
    }
}

/**
 * A row of [AppMenu]: [icon] of 24 where it says something, the [text], and a [caption] under it that wraps — such a row grows to
 * about 64. [selected] — the value now chosen — carries a check mark on the right and a heavier word; a row that cannot be picked
 * now is dimmed and deaf.
 */
@Composable
fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    caption: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    MenuRow(text, onClick, icon, caption, selected, enabled, tint = null)
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit, icon: ImageVector?, caption: String?, selected: Boolean, enabled: Boolean, tint: Color?) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clip(AppShapes.S)
            .semantics { if (selected) this.selected = true }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = RowPaddingSide, vertical = if (caption != null) RowPaddingCaption else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        if (icon != null) AppIcon(icon, contentDescription = null, tint = tint ?: colors.onSurfaceVariant, size = IconSizes.Standalone)
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                color = tint ?: colors.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            )
            if (caption != null) {
                Text(caption, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
            }
        }
        if (selected) AppIcon(AppIcons.Check, contentDescription = null, tint = colors.primary, size = IconSizes.InFilledButton)
    }
}
