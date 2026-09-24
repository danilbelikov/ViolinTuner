package com.violinjourney.app.feature.sound.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.feature.sound.BackingBlockState
import com.violinjourney.app.feature.sound.SoundFormats
import com.violinjourney.app.feature.sound.SoundIntent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_block_title
import com.violinjourney.app.shared.resources.backing_gain
import com.violinjourney.app.shared.resources.backing_heard_violin
import com.violinjourney.app.shared.resources.backing_heard_with
import com.violinjourney.app.shared.resources.backing_offset
import com.violinjourney.app.shared.resources.backing_offset_hint
import com.violinjourney.app.shared.resources.backing_offset_recorded
import com.violinjourney.app.shared.resources.backing_preparing
import org.jetbrains.compose.resources.stringResource

private val SwitchHeight = 36.dp
private val CardCorner = 16.dp
private const val TABULAR_FIGURES = "tnum"

/**
 * «с минусовкой / только скрипка» under the player of a take made under a backing (spec 3.32). Two halves of one
 * pill, the chosen one filled: a switch of what is heard, not a setting — it is not remembered past the screen.
 */
@Composable
fun BackingHeardSwitch(heard: Boolean, onHeard: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SwitchHeight / 2))
            .background(colors.surfaceContainerHigh)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(true to Res.string.backing_heard_with, false to Res.string.backing_heard_violin).forEach { (value, label) ->
            val selected = heard == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SwitchHeight - 6.dp)
                    .clip(RoundedCornerShape(SwitchHeight / 2))
                    .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
                    .selectable(selected = selected, role = Role.RadioButton) { onHeard(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(label),
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Where the player will be, while the backing's sound is made for it (spec 5.25): a take under a backing cannot be
 * listened to before — the screen says so instead of showing nothing.
 */
@Composable
fun BackingPreparingRow(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
        Text(stringResource(Res.string.backing_preparing), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
    }
}

/** The block «Минусовка» of the «Звук» screen (spec 3.32): the backing's level and shift, last after «Громкость». */
@Composable
fun BackingBlock(state: BackingBlockState, config: BackingConfig, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(colors.surfaceContainer)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(Res.string.backing_block_title), color = colors.onSurface, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        ParamSlider(
            model = SliderModel(
                label = stringResource(Res.string.backing_gain),
                hint = null,
                valueText = SoundFormats.decibels(state.gainDb.toDouble(), signed = true),
                fraction = gainFraction(state.gainDb, config),
                defaultFraction = gainFraction(config.defaultGainDb, config),
                bipolar = false,
            ),
            enabled = true,
            onFraction = { onIntent(SoundIntent.BackingGainChanged(it)) },
            onStep = { onIntent(SoundIntent.BackingGainStepped(it)) },
            onReset = { onIntent(SoundIntent.BackingGainReset) },
        )
        ParamSlider(
            model = SliderModel(
                label = stringResource(Res.string.backing_offset),
                hint = stringResource(Res.string.backing_offset_hint),
                valueText = SoundFormats.signedMs(state.offsetMs),
                fraction = offsetFraction(state.offsetMs, config),
                // the scale's zero, where the fill starts; «Как записано» is the button under it
                defaultFraction = offsetFraction(0, config),
                bipolar = true,
            ),
            enabled = true,
            onFraction = { onIntent(SoundIntent.BackingOffsetChanged(it)) },
            onStep = { onIntent(SoundIntent.BackingOffsetStepped(it)) },
            onReset = { onIntent(SoundIntent.BackingOffsetRecorded) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            TextButton(onClick = { onIntent(SoundIntent.BackingOffsetRecorded) }, enabled = state.offsetMs != state.recordedOffsetMs) {
                Text(
                    stringResource(Res.string.backing_offset_recorded, SoundFormats.signedMs(state.recordedOffsetMs)),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
    }
}

internal fun gainFraction(gainDb: Float, config: BackingConfig): Float = ((gainDb - config.minGainDb) / (config.maxGainDb - config.minGainDb)).coerceIn(0f, 1f)

internal fun offsetFraction(offsetMs: Int, config: BackingConfig): Float =
    ((offsetMs - config.minOffsetMs).toFloat() / (config.maxOffsetMs - config.minOffsetMs)).coerceIn(0f, 1f)
