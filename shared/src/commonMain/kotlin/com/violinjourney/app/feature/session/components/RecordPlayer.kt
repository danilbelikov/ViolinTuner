package com.violinjourney.app.feature.session.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.SessionIntent
import com.violinjourney.app.feature.session.SessionState
import com.violinjourney.app.feature.session.SoundRow
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.feature.sound.captionName
import com.violinjourney.app.feature.sound.components.AbSegment
import com.violinjourney.app.feature.sound.components.BackingSegment
import com.violinjourney.app.feature.sound.components.PlayRow
import com.violinjourney.app.feature.sound.components.PreparingRow
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.sound_caption_everyone
import com.violinjourney.app.shared.resources.sound_caption_own
import com.violinjourney.app.shared.resources.sound_row_off
import com.violinjourney.app.shared.resources.sound_session_icon
import com.violinjourney.app.shared.resources.sound_session_row
import org.jetbrains.compose.resources.stringResource

// The rows of the player of a recording (spec 3.36.5, 5.29 R5).
/** A/B beside the line «Звук»: two segments of 48 — «A | B» — whatever the panel. */
private val AbWidth = 96.dp
private val RowsGap = 8.dp
private val SoundLineHeight = 52.dp
private val SoundLinePadding = 12.dp
private val SoundLineIcon = 18.dp
private val SoundIconTarget = 48.dp
private val TrailingGap = 8.dp

/**
 * The player at the bottom of a recording (spec 3.36.5, 5.29 R5), the rows of the panel of [com.violinjourney.app.core.ui.components.AppDock]
 * — «play» and the wave with the time; under them, 10 apart, A/B — while the processing does something — and the line «Звук» on the
 * rest of the width; 8 under those, of a take under a backing, «С минусовкой | Только скрипка» the whole width. In the compact panel
 * (a window lower than 700 dp) one row — «play» 48, the lower wave, the compact A/B and the icon «Звук» — and the backing's row under it.
 *
 * What the rows are is known before the player is ready — the settings say whether there is an A/B, the recording whether it was made
 * under a backing — so the panel stands at its height from the first frame: until the player is ready its first row is empty; while
 * the backing is made (spec 5.25) the spinner and «Готовим минусовку…» stand in it, and A/B and the backing's row are dimmed and deaf,
 * the line «Звук» answers. A backing that could not be made takes its row away once the player says so. No row moves on its own.
 *
 * [sides] — the fields of the rows at the sides where the column gives them, not the panel (`AppDock(padSides = false)`): lying, the
 * rows stand flush with the summary and the picture over them — 16 at the screen's edge, 8 at the meeting of the columns — while the
 * ground of the panel is the whole column.
 */
@Composable
internal fun DockScope.RecordPlayer(
    state: SessionState.Loaded,
    position: () -> Long,
    onIntent: (SessionIntent) -> Unit,
    sides: PaddingValues = PaddingValues(),
) {
    // the rows 10 apart, as those of the zone; in one column of their own for the sides they may be given
    val dock = this
    Column(Modifier.padding(sides), verticalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
        dock.RecordPlayerRows(state, position, onIntent)
    }
}

@Composable
private fun DockScope.RecordPlayerRows(state: SessionState.Loaded, position: () -> Long, onIntent: (SessionIntent) -> Unit) {
    val player = state.player
    val preparing = player == null && state.preparingBacking
    val sound = state.sound
    val processed = player?.processed ?: sound?.processed ?: false
    // until the player says whether the backing could be made, the recording says it was made under one
    val backing = player?.hasBacking ?: state.content.underBacking
    val onOriginal = { original: Boolean, held: Boolean -> onIntent(SessionIntent.OriginalSelected(original, held)) }
    val onHeard = { heard: Boolean -> onIntent(SessionIntent.BackingHeardSelected(heard)) }
    val onSound = { onIntent(SessionIntent.SoundClicked) }
    val first: @Composable (trailing: @Composable RowScope.() -> Unit) -> Unit = { trailing ->
        when {
            player != null -> PlayRow(
                player = player,
                position = position,
                waveform = state.waveform,
                compact = compact,
                onPlayPause = { onIntent(SessionIntent.PlayPauseClicked) },
                onSeek = { onIntent(SessionIntent.SeekRequested(it)) },
                trailing = trailing,
            )
            preparing -> PreparingRow(compact, trailing = trailing)
            // the player is not ready yet: its place is kept, with nothing in it
            else -> Row(Modifier.fillMaxWidth().height(buttonHeight), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
    }
    if (compact) {
        // the backing's row stands right under the first: its touch of 48 begins where that row ends, 10 dp of air above its 28
        Column {
            first {
                if (processed) {
                    AbSegment(player?.original == true, onOriginal, compact = true, modifier = Modifier.padding(start = TrailingGap).width(AbWidth), enabled = !preparing)
                }
                if (sound != null) SoundIconButton(sound, processed, onSound)
            }
            if (backing) {
                BackingSegment(player?.backingHeard ?: true, onHeard, compact = true, modifier = Modifier.fillMaxWidth(), enabled = !preparing, containerColor = MaterialTheme.colorScheme.surface)
            }
        }
        return
    }
    first {}
    if (processed || sound != null || backing) {
        Column(verticalArrangement = Arrangement.spacedBy(RowsGap)) {
            if (processed || sound != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RowsGap)) {
                    if (processed) AbSegment(player?.original == true, onOriginal, compact = false, modifier = Modifier.width(AbWidth), enabled = !preparing)
                    if (sound != null) SoundLine(sound, processed, onSound, Modifier.weight(1f))
                }
            }
            if (backing) {
                BackingSegment(player?.backingHeard ?: true, onHeard, compact = false, modifier = Modifier.fillMaxWidth(), enabled = !preparing, containerColor = MaterialTheme.colorScheme.surface)
            }
        }
    }
}

/**
 * «Звук · как у всех · Камерный зал» (spec 3.17, 3.36.5): the way to the screen «Звук», with what the recording sounds like — «Звук»
 * bold, the rest in the second level, one line that gives way at its end; the icon in the accent while the processing does something.
 */
@Composable
internal fun SoundLine(row: SoundRow, processed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val caption = soundCaptionOf(row, processed)
    val separator = stringResource(Res.string.dot_separator)
    Row(
        modifier = modifier
            .heightIn(min = SoundLineHeight)
            .clip(AppShapes.Control)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SoundLinePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowsGap),
    ) {
        AppIcon(AppIcons.Sound, contentDescription = null, tint = if (processed) colors.primary else colors.onSurfaceVariant, size = SoundLineIcon)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append(stringResource(Res.string.sound_session_row)) }
                append(separator)
                append(caption)
            },
            modifier = Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        )
        AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = ViolinTheme.textTertiary, size = SoundLineIcon)
    }
}

/** «Звук» of the compact panel: its icon in a target of 48 — «Звук: как у всех · Камерный зал» to TalkBack. */
@Composable
internal fun SoundIconButton(row: SoundRow, processed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(Res.string.sound_session_icon, soundCaptionOf(row, processed))
    Box(
        modifier = modifier
            .size(SoundIconTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcons.Sound, contentDescription = null, tint = if (processed) colors.primary else colors.onSurfaceVariant)
    }
}

/** What the recording sounds like (spec 3.17): «как у всех · Камерный зал», «выключен», «свои настройки», «свои настройки · Тепло». */
@Composable
private fun soundCaptionOf(row: SoundRow, processed: Boolean): String {
    val name = captionName(row.caption)
    return when {
        !row.own -> stringResource(Res.string.sound_caption_everyone, name)
        !processed -> stringResource(Res.string.sound_row_off)
        row.caption == SoundCaption.Custom -> name
        else -> stringResource(Res.string.sound_caption_own, name)
    }
}
