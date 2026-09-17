package com.example.violintuner.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.TolerancePreset

// Handoff prototype: onboarding steps 2 and 3, reused by the settings screen.
private val SelectorHeight = 48.dp
private val SelectorCorner = 24.dp
private val SelectorBorder = 1.dp
private val PresetCorner = 16.dp
private val PresetBorder = 2.dp
private val PresetPaddingHorizontal = 15.dp
private val PresetPaddingVertical = 13.dp
private val PresetSpacing = 8.dp
private const val TABULAR_FIGURES = "tnum"

/** Segmented choice of the A4 reference pitch in hertz. */
@Composable
fun A4Selector(
    optionsHz: List<Int>,
    selectedHz: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(SelectorCorner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SelectorHeight)
            .clip(shape)
            .border(SelectorBorder, colors.outlineVariant, shape)
            .selectableGroup(),
    ) {
        optionsHz.forEachIndexed { index, hz ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(SelectorBorder)
                        .background(colors.outlineVariant),
                )
            }
            val selected = hz == selectedHz
            val description = stringResource(R.string.a4_option_description, hz)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(hz) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = hz.toString(),
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                )
            }
        }
    }
}

/** Cards "Новичок ±12 / Средний ±8 / Профи ±3": how wide the green zone is. */
@Composable
fun TolerancePresetList(
    selected: TolerancePreset,
    onSelect: (TolerancePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(PresetSpacing),
    ) {
        TolerancePreset.entries.forEach { preset ->
            PresetCard(preset, selected = preset == selected, onClick = { onSelect(preset) })
        }
    }
}

@Composable
private fun PresetCard(preset: TolerancePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(PresetCorner)
    val (nameRes, textRes) = when (preset) {
        TolerancePreset.BEGINNER -> R.string.tolerance_beginner_name to R.string.tolerance_beginner_text
        TolerancePreset.INTERMEDIATE -> R.string.tolerance_intermediate_name to R.string.tolerance_intermediate_text
        TolerancePreset.PRO -> R.string.tolerance_pro_name to R.string.tolerance_pro_text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.surfaceContainer else Color.Transparent)
            .border(PresetBorder, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = PresetPaddingHorizontal, vertical = PresetPaddingVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(nameRes),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            )
            Text(
                text = stringResource(textRes),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
        }
        Text(
            text = stringResource(R.string.tolerance_cents, preset.cents),
            color = colors.onSurface,
            style = TextStyle(
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
    }
}
