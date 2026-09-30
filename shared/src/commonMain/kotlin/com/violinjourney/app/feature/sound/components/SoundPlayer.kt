package com.violinjourney.app.feature.sound.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.feature.sound.SoundIntent
import com.violinjourney.app.feature.sound.SoundMode
import com.violinjourney.app.feature.sound.SoundState

/** Between the large A/B and «С минусовкой | Только скрипка» under it: 8, not the 10 of the other rows (5.29 R5). */
private val BackingRowGap = 8.dp

/**
 * The player at the bottom of «Звук» (spec 3.36.5, 5.29 R5): the same panel as the recording's — «play» and the wave with the time;
 * under them the large A/B the whole width, «A оригинал | B обработка» (B chosen by default; a finger held on A — «пока держишь»),
 * dimmed where the processing does nothing; 8 under it, of a take under a backing, «С минусовкой | Только скрипка»; under all of it the
 * output meter. In the compact panel (a window lower than 700 dp) one row of «play» 48 and the lower wave, the meter in the line of
 * the time, then the compact A/B with its words and the compact backing, each the whole width, each pressed over 48.
 *
 * What the rows are is known before the player is ready — the settings say whether A/B answers, the take whether it was made under
 * a backing (the screen of everyone plays none) — so the panel stands at its height from the first frame: until the player is ready
 * its first row is empty; while the backing is made (spec 5.25) the spinner and «Готовим минусовку…» stand in it, A/B and the backing
 * are dimmed and deaf. A backing that could not be made takes its row away once the player says so. No row moves on its own.
 *
 * [sides] — the fields of the rows where the column gives them, not the panel (`AppDock(padSides = false)`): lying, 16 at the edge of
 * the screen and 8 at the meeting of the columns, while the ground of the panel is the whole column.
 */
@Composable
internal fun DockScope.SoundPlayer(
    state: SoundState,
    meters: State<SoundMeters?>,
    position: () -> Long,
    onIntent: (SoundIntent) -> Unit,
    sides: PaddingValues = PaddingValues(),
) {
    val dock = this
    // the rows 10 apart, as those of the zone; in one column of their own for the sides they may be given
    Column(Modifier.padding(sides), verticalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
        dock.SoundPlayerRows(state, meters, position, onIntent)
    }
}

@Composable
private fun DockScope.SoundPlayerRows(state: SoundState, meters: State<SoundMeters?>, position: () -> Long, onIntent: (SoundIntent) -> Unit) {
    val player = state.player
    val preparing = player == null && state.preparingBacking
    // the settings of the screen are those of the player: what they do is known before it is ready
    val processed = player?.processed ?: !SoundRules.isNeutral(state.settings)
    // the screen of everyone sets the violin: a take under a backing is heard there without it (spec 3.36.5)
    val backing = state.mode == SoundMode.RECORDING && (player?.hasBacking ?: (state.backing != null))
    val onOriginal = { original: Boolean, held: Boolean -> onIntent(SoundIntent.OriginalSelected(original, held)) }
    val onHeard = { heard: Boolean -> onIntent(SoundIntent.BackingHeardSelected(heard)) }
    val first: @Composable () -> Unit = {
        when {
            player != null -> PlayRow(
                player = player,
                position = position,
                waveform = state.waveform,
                compact = compact,
                onPlayPause = { onIntent(SoundIntent.PlayPauseClicked) },
                onSeek = { onIntent(SoundIntent.SeekRequested(it)) },
                // the compact panel has the meter in the line of the time (5.29 R5)
                timeMiddle = if (compact) ({ OutputMeter(meters, Modifier.fillMaxWidth()) }) else null,
            )
            preparing -> PreparingRow(compact)
            // the player is not ready yet: its place is kept, with nothing in it
            else -> Spacer(Modifier.fillMaxWidth().height(buttonHeight))
        }
    }
    val ab: @Composable () -> Unit = {
        AbSegment(player?.original == true, onOriginal, compact = compact, modifier = Modifier.fillMaxWidth(), enabled = processed && !preparing, words = true)
    }
    val heard: @Composable () -> Unit = {
        BackingSegment(
            player?.backingHeard ?: true, onHeard, compact = compact, modifier = Modifier.fillMaxWidth(), enabled = !preparing,
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
    if (compact) {
        // each compact switch right under what is over it: its touch of 48 begins where that ends, 10 of air over its 28
        Column {
            first()
            ab()
            if (backing) heard()
        }
        return
    }
    first()
    Column(verticalArrangement = Arrangement.spacedBy(BackingRowGap)) {
        ab()
        if (backing) heard()
    }
    OutputMeter(meters, Modifier.fillMaxWidth())
}
