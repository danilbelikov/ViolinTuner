package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The chips of the redesign (spec 5.29; components.html, «Выбор»).
private val Capsule = RoundedCornerShape(percent = 50)
private val FilterHeight = 40.dp
private val ChoiceHeight = 44.dp
private val ChipPadding = 14.dp
private val ChipGap = 6.dp
private val FilterBorder = 1.5.dp
private val FilterIcon = 16.dp
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"

/**
 * A filter chip (spec 3.36.1, 5.29): a capsule of 40 with a frame of 1.5 — «Все 5», «Учу 2». [count] follows the word in the third
 * level of text, [icon] of 16 goes before it. The chosen one is filled as the pill of a tab: primaryContainer under the words of
 * onPrimaryContainer. The colour changes at once, without an animation, as the pill of a tab does.
 *
 * Pressed over 48 dp while 40 are seen: the caller lays the chips out in a row that leaves them that room, wraps the row in
 * `selectableGroup()` and scrolls the ribbon itself. Each chip reads as a radio button, chosen or not.
 */
@Composable
fun AppChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val ground = if (selected) colors.primaryContainer else Color.Transparent
    val frame = if (selected) colors.primaryContainer else colors.outlineVariant
    val words = if (selected) colors.onPrimaryContainer else colors.onSurface
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = FilterHeight)
            .clip(Capsule)
            .background(ground)
            .border(FilterBorder, frame, Capsule)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = ChipPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipGap, Alignment.CenterHorizontally),
    ) {
        if (icon != null) AppIcon(icon, contentDescription = null, size = FilterIcon, tint = words)
        Text(text, color = words, style = style, maxLines = 1)
        if (count != null) {
            Text(
                text = count.toString(),
                color = if (selected) colors.onPrimaryContainer else ViolinTheme.textTertiary,
                style = style.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                maxLines = 1,
            )
        }
    }
}

/** The chips of choice, [AppChip.Choice], beside the filter chip [AppChip] — two looks under one name, as `Color` and `Color(…)`. */
object AppChip {
    /**
     * A chip of choice (spec 3.36.1, 5.29): a value to pick — a duration, a date, «Сколько играть», a quick chip of a sheet. 44 at a
     * corner of 14, no frame, one step lighter than its ground: surfaceContainer on a screen, surfaceContainerHigh in a sheet
     * ([inSheet]); the words in onSurfaceVariant. The chosen one is primaryContainer under onPrimaryContainer, at once.
     *
     * [selected] null makes it an action chip — «+10 мин», «0» of «Время за день»: a button with no state of choice. [enabled] false
     * dims it to 0.38 and it is not pressed (the muted options of R4). Pressed over 48 dp while 44 are seen; a row of choices is
     * wrapped in `selectableGroup()` by the caller. A minimum width («не уже 60» of R3) comes with [modifier].
     */
    @Composable
    fun Choice(
        text: String,
        selected: Boolean?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        inSheet: Boolean = false,
        enabled: Boolean = true,
    ) {
        val colors = MaterialTheme.colorScheme
        val chosen = selected == true
        val ground = when {
            chosen -> colors.primaryContainer
            inSheet -> colors.surfaceContainerHigh
            else -> colors.surfaceContainer
        }
        val press = if (selected == null) {
            Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        } else {
            Modifier.selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        }
        Row(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = ChoiceHeight)
                .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
                .clip(AppShapes.Control)
                .background(ground)
                .then(press)
                .padding(horizontal = ChipPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                color = if (chosen) colors.onPrimaryContainer else colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                maxLines = 1,
            )
        }
    }
}
