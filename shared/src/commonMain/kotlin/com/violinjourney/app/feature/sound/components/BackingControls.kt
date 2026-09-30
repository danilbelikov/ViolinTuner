package com.violinjourney.app.feature.sound.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.sound.BackingBlockState
import com.violinjourney.app.feature.sound.BackingSliders
import com.violinjourney.app.feature.sound.RecordedWith
import com.violinjourney.app.feature.sound.SliderValues
import com.violinjourney.app.feature.sound.SoundCard
import com.violinjourney.app.feature.sound.SoundFormats
import com.violinjourney.app.feature.sound.SoundIntent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_block_title
import com.violinjourney.app.shared.resources.backing_gain
import com.violinjourney.app.shared.resources.backing_offset
import com.violinjourney.app.shared.resources.backing_offset_hint
import com.violinjourney.app.shared.resources.backing_offset_recorded
import com.violinjourney.app.shared.resources.backing_recorded_in
import com.violinjourney.app.shared.resources.backing_recorded_in_latency
import com.violinjourney.app.shared.resources.backing_recorded_wired
import com.violinjourney.app.shared.resources.backing_take_unprepared
import org.jetbrains.compose.resources.stringResource

/** «Минусовка» is the fifth card, after the four blocks of the chain (spec 3.36.5): it is not the violin's. */
private const val BACKING_NUMBER = 5

/** The line of the headphones: 10 under what is over it, the sign of 16 six from the words (5.29 R5). */
private val RecordedTop = 10.dp
private val RecordedSign = 16.dp
private val RecordedGap = 6.dp

/**
 * The card «Минусовка» of «Звук» (spec 3.32, 3.36.5), the fifth and last, after «Громкость»: under its name the name of the backing's
 * file and its length — «фортепиано · 3:40»; no switch — whether it is heard is «С минусовкой | Только скрипка» of the player, which is
 * not kept. It opens and closes as the others ([expanded]); opened — «Громкость минусовки», «Сдвиг» with its hint, «Как записано» and
 * the line of what the take was recorded in. A backing that could not be prepared ([unavailable]) — only the violin is heard — does
 * not open and has no chevron: under its name the line that says so, in place of sliders that would change nothing.
 */
@Composable
fun BackingCard(state: BackingBlockState, unavailable: Boolean, expanded: Boolean, config: BackingConfig, onIntent: (SoundIntent) -> Unit) {
    SoundCardFrame(
        number = BACKING_NUMBER,
        title = stringResource(Res.string.backing_block_title),
        summary = listOfNotNull(state.title.takeIf { it.isNotBlank() }, state.durationMs.takeIf { it > 0 }?.let(Formats::duration)),
        open = expanded && !unavailable,
        onToggle = if (unavailable) null else ({ onIntent(SoundIntent.CardToggled(SoundCard.BACKING)) }),
        note = if (unavailable) stringResource(Res.string.backing_take_unprepared) else null,
    ) {
        ParamSlider(
            model = SliderModel(
                label = stringResource(Res.string.backing_gain),
                hint = null,
                valueText = SoundFormats.decibels(state.gainDb.toDouble(), signed = true),
                fraction = BackingSliders.gainFraction(state.gainDb, config),
                defaultFraction = BackingSliders.gainFraction(config.defaultGainDb, config),
                bipolar = false,
                valueReserve = SliderValues.ofBackingGain(config),
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
                fraction = BackingSliders.offsetFraction(state.offsetMs, config),
                // the scale's zero, where the fill starts; «Как записано» is the button under it
                defaultFraction = BackingSliders.offsetFraction(0, config),
                bipolar = true,
                // its reset is «Как записано», not the zero of the scale
                resetFraction = BackingSliders.offsetFraction(state.recordedOffsetMs, config),
                valueReserve = SliderValues.ofBackingOffset(config),
            ),
            enabled = true,
            onFraction = { onIntent(SoundIntent.BackingOffsetChanged(it)) },
            onStep = { onIntent(SoundIntent.BackingOffsetStepped(it)) },
            onReset = { onIntent(SoundIntent.BackingOffsetRecorded) },
        )
        // the line of the headphones stands 10 under «Как записано», not the 12 of the card's rows
        Column(verticalArrangement = Arrangement.spacedBy(RecordedTop)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                AppButton(
                    text = stringResource(Res.string.backing_offset_recorded, SoundFormats.signedMs(state.recordedOffsetMs)),
                    onClick = { onIntent(SoundIntent.BackingOffsetRecorded) },
                    style = AppButtonStyle.Soft,
                    icon = AppIcons.Reset,
                    enabled = state.offsetMs != state.recordedOffsetMs,
                )
            }
            state.recordedWith?.let { RecordedWithLine(it) }
        }
    }
}

/**
 * What the take was recorded in (spec 3.36.5): «Записано в Pixel Buds · +200 мс учтено» — the lag the guess added to the clocks
 * (5.25), left out where nothing was added; «Записано в проводных наушниках». 13 sp in the second level, the sign of the headphones.
 */
@Composable
private fun RecordedWithLine(recordedWith: RecordedWith) {
    val colors = MaterialTheme.colorScheme
    val words = when (recordedWith) {
        RecordedWith.Wired -> stringResource(Res.string.backing_recorded_wired)
        is RecordedWith.Wireless -> if (recordedWith.latencyMs > 0) {
            stringResource(Res.string.backing_recorded_in_latency, recordedWith.name, SoundFormats.signedMs(recordedWith.latencyMs))
        } else {
            stringResource(Res.string.backing_recorded_in, recordedWith.name)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RecordedGap),
    ) {
        AppIcon(AppIcons.Headphones, contentDescription = null, tint = colors.onSurfaceVariant, size = RecordedSign)
        Text(words, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
    }
}
