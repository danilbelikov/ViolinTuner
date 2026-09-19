package com.example.violintuner.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.components.sessionTitle
import com.example.violintuner.feature.session.components.NoteSheet
import com.example.violintuner.feature.session.components.PianoRoll
import com.example.violintuner.feature.session.components.PlayerBar
import com.example.violintuner.feature.session.components.ProblemNotes
import com.example.violintuner.feature.session.components.SessionStatCards
import java.time.ZoneId
import kotlin.math.roundToInt

private val TopBarHeight = 56.dp
private val BackTarget = 48.dp
private val ContentPadding = 16.dp
private val SectionSpacing = 16.dp
private val MaxContentWidth = 560.dp
private val ActionIcon = 22.dp
private const val TABULAR_FIGURES = "tnum"
private val ScoreStyle = TextStyle(fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR_FIGURES)
private val PercentStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold)

/** Session analysis (spec 3.10, handoff 4a). Stateless. */
@Composable
fun SessionScreen(
    state: SessionState,
    onIntent: (SessionIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = MaterialTheme.colorScheme
    val loaded = state as? SessionState.Loaded
    val title = when {
        loaded == null -> ""
        else -> sessionTitle(loaded.content.title, loaded.content.pieceTitle, loaded.content.startedAtEpochMs, zone)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        TopBar(title = title, onBack = { onIntent(SessionIntent.BackClicked) })
        when (state) {
            SessionState.Loading -> Unit
            SessionState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.session_not_found),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            is SessionState.Loaded -> LoadedContent(state, title, onIntent)
        }
    }
}

@Composable
private fun LoadedContent(state: SessionState.Loaded, title: String, onIntent: (SessionIntent) -> Unit) {
    val content = state.content
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .verticalScroll(rememberScrollState())
                .padding(start = ContentPadding, end = ContentPadding, top = 8.dp, bottom = ContentPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Summary(content)
            PianoRoll(
                content = content,
                selectedSegment = state.selectedSegment,
                onSegmentClick = { onIntent(SessionIntent.SegmentClicked(it)) },
                cursorMs = state.player?.positionMs,
                followCursor = state.player?.playing == true,
            )
            state.player?.let { player ->
                PlayerBar(
                    player = player,
                    onPlayPause = { onIntent(SessionIntent.PlayPauseClicked) },
                    onSeek = { onIntent(SessionIntent.SeekRequested(it)) },
                )
            }
            SessionStatCards(content)
            ProblemNotes(content.problemNotes)
            Actions(onIntent)
        }
    }
    state.selectedSegment?.let { index ->
        NoteSheet(segment = content.segments[index], onDismiss = { onIntent(SessionIntent.NoteSheetDismissed) })
    }
    when (state.dialog) {
        SessionDialog.RENAME -> RenameDialog(currentTitle = content.title.orEmpty(), placeholder = title, onIntent = onIntent)
        SessionDialog.DELETE -> DeleteDialog(onIntent)
        null -> Unit
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(start = 4.dp, end = ContentPadding, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(BackTarget)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.session_back), role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface)
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun Summary(content: SessionContent) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = content.scorePercent.toString(),
                    modifier = Modifier.alignByBaseline(),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.displayLarge.merge(ScoreStyle),
                )
                Text(
                    text = "%",
                    modifier = Modifier.alignByBaseline(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.headlineMedium.merge(PercentStyle),
                )
            }
            Text(
                text = stringResource(
                    R.string.session_meta,
                    Formats.duration(content.durationMs),
                    content.toleranceCents.roundToInt(),
                ),
                modifier = Modifier.padding(top = 6.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            )
        }
        Column(modifier = Modifier.padding(bottom = 4.dp), horizontalAlignment = Alignment.End) {
            val biasZone = content.biasZone
            Text(
                text = if (biasZone == null) {
                    stringResource(R.string.session_bias_none)
                } else {
                    stringResource(R.string.session_bias_mean, Formats.signedCents(content.biasCents))
                },
                color = biasZone?.let { ViolinTheme.zoneColors.colorFor(it) } ?: ViolinTheme.zoneColors.inTune,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = stringResource(
                    when {
                        biasZone == null -> R.string.session_bias_hint_none
                        content.biasCents < 0 -> R.string.session_bias_hint_flat
                        else -> R.string.session_bias_hint_sharp
                    },
                ),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .widthIn(max = 170.dp),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun Actions(onIntent: (SessionIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colors.surfaceContainerHigh)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Action(
                label = stringResource(R.string.session_action_rename),
                color = colors.onSurfaceVariant,
                iconCorner = 6.dp,
                onClick = { onIntent(SessionIntent.RenameClicked) },
            )
            Action(
                label = stringResource(R.string.session_action_delete),
                color = ViolinTheme.zoneColors.off,
                iconCorner = 4.dp,
                onClick = { onIntent(SessionIntent.DeleteClicked) },
            )
        }
    }
}

@Composable
private fun Action(label: String, color: Color, iconCorner: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(ActionIcon)
                .border(2.dp, color, RoundedCornerShape(iconCorner)),
        )
        Text(label, color = color, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
    }
}

@Composable
private fun RenameDialog(currentTitle: String, placeholder: String, onIntent: (SessionIntent) -> Unit) {
    var text by rememberSaveable { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = { onIntent(SessionIntent.DialogDismissed) },
        title = { Text(stringResource(R.string.session_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                supportingText = { Text(stringResource(R.string.session_rename_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onIntent(SessionIntent.RenameConfirmed(text)) }) {
                Text(stringResource(R.string.session_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(SessionIntent.DialogDismissed) }) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun DeleteDialog(onIntent: (SessionIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(SessionIntent.DialogDismissed) },
        title = { Text(stringResource(R.string.session_delete_title)) },
        text = { Text(stringResource(R.string.session_delete_text)) },
        confirmButton = {
            TextButton(onClick = { onIntent(SessionIntent.DeleteConfirmed) }) {
                Text(stringResource(R.string.session_delete_confirm), color = ViolinTheme.zoneColors.off)
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(SessionIntent.DialogDismissed) }) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
