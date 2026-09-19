package com.example.violintuner.feature.sound

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.audio.fx.SoundMeters
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.ui.components.SegmentedSwitch
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.components.sessionTitle
import com.example.violintuner.feature.sound.components.MiniPlayer
import com.example.violintuner.feature.sound.components.MiniPlayerMetrics
import com.example.violintuner.feature.sound.components.SoundBlocks
import java.time.ZoneId

private val ScreenPadding = 16.dp
private val TopBarHeight = 56.dp
private val TopBarButton = 48.dp
private val ChipHeight = 36.dp
private val LandscapeLeft = 340.dp
private val CompactBelow = 700.dp
private const val DISABLED_ALPHA = 0.38f

/**
 * The «Звук» screen (spec 3.17, handoff 18b–18h): the mini player always in sight, the presets,
 * the four blocks. Stateless; [meters] is handed down as a state and read where it is drawn.
 */
@Composable
fun SoundScreen(
    state: SoundState,
    meters: State<SoundMeters?>,
    onIntent: (SoundIntent) -> Unit,
    modifier: Modifier = Modifier,
    config: SoundConfig = SoundConfig(),
    zone: ZoneId = ZoneId.systemDefault(),
    /** «Поделиться» comes with stage 32; until then the screen has no dead button. */
    shareAvailable: Boolean = false,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (state.loading) return@BoxWithConstraints
        val landscape = maxWidth > maxHeight
        val metrics = if (maxHeight < CompactBelow && !landscape) MiniPlayerMetrics.Compact else MiniPlayerMetrics.Regular
        val share: (@Composable () -> Unit)? = if (shareAvailable && state.mode == SoundMode.RECORDING && state.player != null) {
            { ShareButton(onIntent) }
        } else {
            null
        }
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                // What one listens and compares with stays put; the blocks scroll beside it.
                Column(
                    modifier = Modifier
                        .width(LandscapeLeft)
                        .fillMaxHeight()
                        .padding(bottom = 12.dp),
                ) {
                    TopBar(state, zone, onIntent)
                    Column(Modifier.padding(horizontal = ScreenPadding).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Player(state, meters, MiniPlayerMetrics.Compact, onIntent)
                        Presets(state, onIntent)
                    }
                    share?.let { Box(Modifier.padding(horizontal = ScreenPadding)) { it() } }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 4.dp, end = ScreenPadding, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Scope(state, zone, onIntent)
                    Blocks(state, meters, config, onIntent)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar(state, zone, onIntent)
                Box(Modifier.padding(horizontal = ScreenPadding).padding(bottom = 8.dp)) { Player(state, meters, metrics, onIntent) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenPadding)
                        .padding(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Scope(state, zone, onIntent)
                    Presets(state, onIntent)
                    Blocks(state, meters, config, onIntent)
                }
                share?.let { Box(Modifier.padding(horizontal = ScreenPadding).padding(bottom = 12.dp)) { it() } }
            }
        }
    }
    state.dialog?.let { Dialogs(it, state, zone, onIntent) }
}

@Composable
private fun TopBar(state: SoundState, zone: ZoneId, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val presetName = captionName(state.caption)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val back = stringResource(R.string.session_back)
        Box(
            modifier = Modifier
                .size(TopBarButton)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onIntent(SoundIntent.BackClicked) }
                .semantics { contentDescription = back },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface) }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (state.mode) {
                    SoundMode.EVERYONE -> stringResource(R.string.sound_everyone_title)
                    SoundMode.RECORDING -> state.recording?.let { sessionTitle(it.title, it.pieceTitle, it.startedAtEpochMs, zone) }.orEmpty()
                },
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = when {
                    state.mode == SoundMode.EVERYONE -> stringResource(R.string.sound_everyone_subtitle, presetName)
                    state.savedHint -> stringResource(R.string.sound_caption_saved_hint)
                    !state.own -> stringResource(R.string.sound_caption_everyone, presetName)
                    state.caption == SoundCaption.Custom -> presetName
                    else -> stringResource(R.string.sound_caption_own, presetName)
                },
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            )
        }
        TextButton(onClick = { onIntent(SoundIntent.ResetClicked) }, enabled = state.canReset, modifier = Modifier.alpha(if (state.canReset) 1f else DISABLED_ALPHA)) {
            IconLabel(AppIcons.Reset, stringResource(R.string.sound_reset), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        }
    }
}

/** A preset by its name, «свои настройки» for what is none, «без обработки» for the one that does nothing. */
@Composable
internal fun captionName(caption: SoundCaption): String = when (caption) {
    is SoundCaption.BuiltIn -> stringArrayResource(R.array.sound_preset_names)[caption.preset.ordinal].let { name ->
        if (caption.preset.ordinal == 0) stringResource(R.string.sound_caption_off) else name
    }
    is SoundCaption.User -> caption.name
    SoundCaption.Custom -> stringResource(R.string.sound_caption_custom)
}

@Composable
private fun Player(state: SoundState, meters: State<SoundMeters?>, metrics: MiniPlayerMetrics, onIntent: (SoundIntent) -> Unit) {
    val player = state.player
    if (player == null) {
        if (state.mode == SoundMode.EVERYONE && state.recordings.isEmpty()) {
            Text(
                text = stringResource(R.string.sound_listen_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
        return
    }
    MiniPlayer(
        player = player,
        waveform = state.waveform,
        meters = meters,
        metrics = metrics,
        onPlayPause = { onIntent(SoundIntent.PlayPauseClicked) },
        onSeek = { onIntent(SoundIntent.SeekRequested(it)) },
        onOriginal = { original, held -> onIntent(SoundIntent.OriginalSelected(original, held)) },
    )
}

/** Whose sound this is: the mode of a recording with what it means, or — for everyone — what it is listened on and whom it touches. */
@Composable
private fun Scope(state: SoundState, zone: ZoneId, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (state.mode) {
        SoundMode.RECORDING -> {
            SegmentedSwitch(
                labels = listOf(stringResource(R.string.sound_mode_everyone), stringResource(R.string.sound_mode_own)),
                selectedIndex = if (state.own) 1 else 0,
                onSelect = { onIntent(SoundIntent.ModeSelected(own = it == 1)) },
                height = 36.dp,
                fontSize = 13,
            )
            Text(
                text = if (state.own) stringResource(R.string.sound_mode_own_text) else stringResource(R.string.sound_mode_everyone_text, captionName(state.caption)),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            )
        }
        SoundMode.EVERYONE -> {
            val recording = state.recording
            if (recording != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainer)
                        .clickable(enabled = state.recordings.size > 1, role = Role.Button) { onIntent(SoundIntent.ListenOnClicked) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.sound_listen_on), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
                    Text(
                        text = sessionTitle(recording.title, recording.pieceTitle, recording.startedAtEpochMs, zone),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    if (state.recordings.firstOrNull()?.sessionId == recording.sessionId) {
                        Text(stringResource(R.string.sound_listen_latest), color = colors.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                    }
                    if (state.recordings.size > 1) AppIcon(AppIcons.ChevronDown, contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
            if (state.affected > 0) {
                val one = state.affected % 10 == 1 && state.affected % 100 != 11
                Text(
                    text = stringResource(if (one) R.string.sound_affected_one else R.string.sound_affected_many, state.affected),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                )
            }
        }
    }
}

/** The presets in a row that runs off the edge; «Свои» first once the settings are nobody's preset, «Сохранить как пресет» last. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Presets(state: SoundState, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val names = stringArrayResource(R.array.sound_preset_names)
    val removeHint = stringResource(R.string.sound_preset_remove_hint)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.custom) {
            Row(
                modifier = Modifier
                    .height(ChipHeight)
                    .clip(RoundedCornerShape(ChipHeight / 2))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).background(colors.primary, CircleShape))
                Text(stringResource(R.string.sound_preset_custom), color = colors.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        state.chips.forEach { chip ->
            val name = chip.userName ?: names[(chip.ref as PresetRef.BuiltIn).preset.ordinal]
            Box(
                modifier = Modifier
                    .height(ChipHeight)
                    .clip(RoundedCornerShape(ChipHeight / 2))
                    .background(if (chip.selected) colors.primaryContainer else Color.Transparent)
                    .border(1.dp, if (chip.selected) colors.primaryContainer else colors.outlineVariant, RoundedCornerShape(ChipHeight / 2))
                    .combinedClickable(
                        role = Role.RadioButton,
                        onLongClickLabel = removeHint.takeIf { chip.ref is PresetRef.User },
                        onLongClick = { onIntent(SoundIntent.PresetLongPressed(chip.ref)) },
                        onClick = { onIntent(SoundIntent.PresetSelected(chip.ref)) },
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(name, color = if (chip.selected) colors.onPrimaryContainer else colors.onSurface, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        Row(
            modifier = Modifier
                .height(ChipHeight)
                .alpha(if (state.custom) 1f else DISABLED_ALPHA)
                .clip(RoundedCornerShape(ChipHeight / 2))
                .border(1.dp, if (state.custom) colors.primary else colors.outlineVariant, RoundedCornerShape(ChipHeight / 2))
                .clickable(enabled = state.custom, role = Role.Button) { onIntent(SoundIntent.SavePresetClicked) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val tint = if (state.custom) colors.primary else colors.onSurfaceVariant
            AppIcon(AppIcons.Preset, contentDescription = null, tint = tint, size = 16.dp)
            Text(stringResource(R.string.sound_preset_save), color = tint, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun Blocks(state: SoundState, meters: State<SoundMeters?>, config: SoundConfig, onIntent: (SoundIntent) -> Unit) {
    Text(
        text = stringResource(R.string.sound_order),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
    )
    SoundBlocks(state.settings, state.expanded, state.band, state.details, meters, config, onIntent)
}

@Composable
private fun ShareButton(onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.primary)
            .clickable(role = Role.Button) { onIntent(SoundIntent.ShareClicked) },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.onPrimary) {
            IconLabel(AppIcons.Share, stringResource(R.string.sound_share), iconSize = 20.dp, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun Dialogs(dialog: SoundDialog, state: SoundState, zone: ZoneId, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dismiss = { onIntent(SoundIntent.DialogDismissed) }
    when (dialog) {
        SoundDialog.BackToEveryone -> Confirm(
            stringResource(R.string.sound_dialog_everyone_title), stringResource(R.string.sound_dialog_everyone_text),
            stringResource(R.string.sound_dialog_everyone_confirm), destructive = false, onIntent,
        )
        SoundDialog.ResetEveryone -> Confirm(
            stringResource(R.string.sound_dialog_reset_title), stringResource(R.string.sound_dialog_reset_text),
            stringResource(R.string.sound_reset), destructive = false, onIntent,
        )
        is SoundDialog.DeletePreset -> Confirm(
            stringResource(R.string.sound_dialog_delete_title, dialog.name), null, stringResource(R.string.piece_delete_confirm), destructive = true, onIntent,
        )
        SoundDialog.SavePreset -> {
            var name by rememberSaveable { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.sound_dialog_preset_title)) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(PRESET_NAME_LENGTH) },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.sound_dialog_preset_hint)) },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onIntent(SoundIntent.PresetNameConfirmed(name)) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.sound_dialog_preset_save)) }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.dialog_cancel)) } },
                containerColor = colors.surfaceContainerHigh,
            )
        }
        SoundDialog.PickRecording -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.sound_dialog_pick_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.recordings.forEach { recording ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { onIntent(SoundIntent.RecordingPicked(recording.sessionId)) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sessionTitle(recording.title, recording.pieceTitle, recording.startedAtEpochMs, zone),
                                color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), modifier = Modifier.weight(1f),
                            )
                            Text(Formats.timeOfDay(recording.startedAtEpochMs, zone), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.dialog_cancel)) } },
            containerColor = colors.surfaceContainerHigh,
        )
    }
}

@Composable
private fun Confirm(title: String, text: String?, confirm: String, destructive: Boolean, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { onIntent(SoundIntent.DialogDismissed) },
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = { onIntent(SoundIntent.DialogConfirmed) }) { Text(confirm, color = if (destructive) ViolinTheme.destructive else colors.primary) }
        },
        dismissButton = { TextButton(onClick = { onIntent(SoundIntent.DialogDismissed) }) { Text(stringResource(R.string.dialog_cancel)) } },
        containerColor = colors.surfaceContainerHigh,
    )
}

/** Mirrors `SoundConfig.maxPresetNameLength`; the repository cuts to it anyway, the field just does not let more in. */
private const val PRESET_NAME_LENGTH = 24
