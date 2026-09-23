package com.violinjourney.app.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SwitchHeight = 40.dp
private val SwitchCorner = 20.dp
private const val SWITCH_MS = 200

/**
 * A row of equal text segments, one selected — the look of the mode switch of Live (handoff
 * «Игра | Настройка»), without icons: the sections of «Записи», major and minor, flat / natural /
 * sharp of the piece form.
 */
@Composable
fun SegmentedSwitch(
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = SwitchHeight,
    /** Larger for segments that are a single sign, like ♭ ♮ ♯. */
    fontSize: Int = 14,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(SwitchCorner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(1.dp, colors.outlineVariant, shape)
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                if (selected) colors.primaryContainer else colors.surface.copy(alpha = 0f), tween(SWITCH_MS), label = "segment",
            )
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(colors.outlineVariant),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(background)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
