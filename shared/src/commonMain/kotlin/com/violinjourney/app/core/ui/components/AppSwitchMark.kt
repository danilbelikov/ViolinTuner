package com.violinjourney.app.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.theme.ViolinTheme

/**
 * What the switch takes (spec 5.29, «Строка списка»: 52 × 32): the place a row holds for it while what it would show is not known yet
 * ([ListRowEnd.Toggle] of null).
 */
internal val AppSwitchSize = DpSize(52.dp, 32.dp)

/**
 * The one switch of the app (spec 5.29, «Строка списка»; 5.29 R4, «Нижняя зона записи»): 52 × 32 ([AppSwitchSize]) — on, the track in
 * the accent under a thumb of onPrimary; off, the track surfaceContainerHigh with a frame of outlineVariant under a thumb of the third
 * level. It only draws: whatever it stands in is the target and toggles it — the whole row of a list ([ListRow] with
 * [ListRowEnd.Toggle]), the whole capsule «С минусовкой» of the piece screen (R4).
 */
@Composable
fun AppSwitchMark(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = null,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = colors.primary,
            checkedBorderColor = colors.primary,
            checkedThumbColor = colors.onPrimary,
            uncheckedTrackColor = colors.surfaceContainerHigh,
            uncheckedBorderColor = colors.outlineVariant,
            uncheckedThumbColor = ViolinTheme.textTertiary,
        ),
    )
}
