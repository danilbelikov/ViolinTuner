package com.example.violintuner.feature.repertoire.piece

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.session.RecordingBar
import com.example.violintuner.core.recording.video.VideoImport
import com.example.violintuner.core.recording.video.VideoImportFailure
import com.example.violintuner.core.ui.components.dimmedWhen
import com.example.violintuner.core.ui.components.rememberSmallFileImage
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.theme.ViolinTheme

private val ButtonHeight = 40.dp
private val MenuWidth = 248.dp
private val ThumbWidth = 64.dp
private val ThumbHeight = 36.dp
private val StripHeight = 48.dp
private val StripCorner = 10.dp
private val StripBar = 6.dp
private val StripCursor = 2.dp
private const val SWAP_MS = 200
private const val TABULAR_FIGURES = "tnum"

/** Which of the app's own camera's items «Видео-дубль» offers (spec 3.32). */
enum class OwnCamera { UNDER_BACKING, PLAIN }

/**
 * The quiet second way to a take (spec 3.19, handoff 20a1): a small outlined button under the
 * words of the loud round one, with a menu of two. The second lines of the menu warn of the two
 * things about this that are not obvious. [busy] is a short analysis that shows no sheet.
 */
@Composable
fun VideoTakeButton(
    enabled: Boolean,
    busy: Boolean,
    onShoot: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Null — the system camera (spec 3.19); otherwise the app's own, the only one for a piece with a backing (spec 3.32):
     * under it while the chip is on, a plain video while it is off.
     */
    ownCamera: OwnCamera? = null,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ButtonHeight / 2)
    Box(modifier = modifier.dimmedWhen(!enabled && !busy)) {
        Row(
            modifier = Modifier
                .height(ButtonHeight)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .border(1.dp, colors.outlineVariant, shape)
                .clickable(enabled = enabled && !busy, role = Role.Button) { menuOpen = true }
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(AppIcons.Video, contentDescription = null, tint = colors.onSurface, size = 20.dp)
            Text(
                text = stringResource(if (busy) R.string.video_take_busy else R.string.video_take),
                color = colors.onSurface,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            AppIcon(AppIcons.ChevronDown, contentDescription = null, tint = colors.onSurfaceVariant, size = 16.dp)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.width(MenuWidth), containerColor = colors.surfaceContainerHigh) {
            when (ownCamera) {
                null -> MenuItem(AppIcons.Video, R.string.video_shoot, R.string.video_shoot_hint) { menuOpen = false; onShoot() }
                OwnCamera.UNDER_BACKING -> MenuItem(AppIcons.Backing, R.string.video_shoot_backing, R.string.video_shoot_backing_hint) { menuOpen = false; onShoot() }
                OwnCamera.PLAIN -> MenuItem(AppIcons.Video, R.string.video_shoot, R.string.video_shoot_own_hint) { menuOpen = false; onShoot() }
            }
            MenuItem(AppIcons.VideoGallery, R.string.video_pick, R.string.video_pick_hint) { menuOpen = false; onPick() }
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, hint: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        leadingIcon = { AppIcon(icon, contentDescription = null, tint = colors.onSurface, size = 20.dp) },
        text = {
            Column {
                Text(stringResource(title), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
                Text(stringResource(hint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        },
        onClick = onClick,
    )
}

/**
 * A video on its way to becoming a take (spec 3.19, handoff 20b). Closes neither by a tap
 * outside nor by a swipe: behind it a file may be deleted. What went wrong is a line here, not a toast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoImportSheet(import: VideoImport, onIntent: (PieceIntent) -> Unit) {
    val shown = when (import) {
        VideoImport.Idle -> false
        is VideoImport.Working -> import.visible
        is VideoImport.Failed -> true
    }
    if (!shown) return
    // It may open, it may not be swiped away: only the importer closes it.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        val kind = when (import) {
            is VideoImport.Working -> if (import.asking) "asking" else "working"
            is VideoImport.Failed -> "failed"
            VideoImport.Idle -> "idle"
        }
        Crossfade(targetState = kind, animationSpec = tween(SWAP_MS), modifier = Modifier.animateContentSize(tween(SWAP_MS)), label = "videoImport") { target ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // the content is drawn from what is current; `target` only says which of the faces is fading in or out
                when {
                    target == "working" && import is VideoImport.Working -> Working(import, onIntent)
                    target == "asking" && import is VideoImport.Working -> Rescue(R.string.video_stop_title, showContinue = true, onIntent)
                    target == "failed" && import is VideoImport.Failed -> Failed(import, onIntent)
                }
            }
        }
    }
}

@Composable
private fun Working(import: VideoImport.Working, onIntent: (PieceIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val thumb = import.thumbPath?.let { rememberSmallFileImage(it) }
        Box(
            Modifier
                .size(ThumbWidth, ThumbHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceContainerHighest),
        ) {
            if (thumb != null) Image(thumb, contentDescription = stringResource(R.string.video_thumb), contentScale = ContentScale.Crop, modifier = Modifier.size(ThumbWidth, ThumbHeight))
        }
        Text(
            text = stringResource(if (import.copying) R.string.video_copying else R.string.video_listening),
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        if (!import.copying) {
            Text(
                text = stringResource(R.string.video_percent, import.percent),
                color = colors.primary,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
    if (import.copying) {
        // no number: a copy is seconds as a rule, and no estimate of it is worth the name
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.surface)
    } else {
        EmergingStrip(import.bars, import.percent / 100f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            // the line keeps its height while the speed is still being measured
            text = import.remainingSec?.let { stringResource(R.string.video_remaining, it) }.orEmpty(),
            modifier = Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
        OutlinedButton(onClick = { onIntent(PieceIntent.VideoImportCancelClicked) }) { Text(stringResource(R.string.video_cancel)) }
    }
}

/**
 * The wait is a first look at the result: the notes found so far stand as pieces in the colour of
 * their zone, left to right, as on the recording strip of Live; the cursor is the progress.
 */
@Composable
private fun EmergingStrip(bars: List<RecordingBar>, fraction: Float) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    val cursor = colors.primary
    val pieces = bars.map { it.fraction to zoneColors.colorFor(it.zone) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .clip(RoundedCornerShape(StripCorner))
            .background(colors.surface)
            .drawBehind {
                val bar = StripBar.toPx()
                val top = (size.height - bar) / 2
                var x = 0f
                pieces.forEach { (share, color) ->
                    val width = share * size.width
                    // a hairline between the pieces, so two notes in one zone stay two
                    drawRoundRect(color, Offset(x, top), Size((width - 1.dp.toPx()).coerceAtLeast(1f), bar), CornerRadius(bar / 2))
                    x += width
                }
                val at = (fraction.coerceIn(0f, 1f) * size.width).coerceAtMost(size.width - StripCursor.toPx())
                drawRect(cursor, Offset(at, 0f), Size(StripCursor.toPx(), size.height))
            },
    )
}

@Composable
private fun Failed(import: VideoImport.Failed, onIntent: (PieceIntent) -> Unit) {
    if (import.reason == VideoImportFailure.STOPPED) {
        Rescue(R.string.video_stopped_title, showContinue = false, onIntent)
        return
    }
    val colors = MaterialTheme.colorScheme
    val title = when (import.reason) {
        VideoImportFailure.NO_SOUND -> stringResource(R.string.video_error_no_sound)
        VideoImportFailure.TOO_LONG -> stringResource(R.string.video_error_too_long)
        VideoImportFailure.CANNOT_OPEN -> stringResource(R.string.video_error_cannot_open)
        VideoImportFailure.NO_NOTES -> stringResource(R.string.video_error_no_notes)
        VideoImportFailure.NO_SPACE -> stringResource(R.string.video_error_no_space, import.missingMb ?: 0)
        VideoImportFailure.STOPPED -> ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(AppIcons.Alert, contentDescription = null, tint = ViolinTheme.destructive)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
            val more = when {
                // a shot exists only here — that is what the two ways out below are about
                import.rescuePath != null -> stringResource(R.string.video_stop_text)
                import.reason == VideoImportFailure.NO_NOTES -> stringResource(R.string.video_error_no_notes_gallery)
                else -> null
            }
            if (more != null) Text(more, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
        }
        if (import.rescuePath == null) Button(onClick = { onIntent(PieceIntent.VideoImportDismissed) }) { Text(stringResource(R.string.video_ok)) }
    }
    if (import.rescuePath != null) RescueButtons(onIntent)
}

/** A shot that did not become a take exists nowhere else: send it somewhere, or let it go. */
@Composable
private fun Rescue(title: Int, showContinue: Boolean, onIntent: (PieceIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(stringResource(title), color = colors.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
    Text(stringResource(R.string.video_stop_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
    RescueButtons(onIntent)
    if (showContinue) {
        TextButton(onClick = { onIntent(PieceIntent.VideoImportContinueClicked) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.video_continue)) }
    }
}

@Composable
private fun RescueButtons(onIntent: (PieceIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onIntent(PieceIntent.VideoImportDismissed) }, modifier = Modifier.weight(1f)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides ViolinTheme.destructive) {
                IconLabel(AppIcons.Trash, stringResource(R.string.video_delete))
            }
        }
        Button(onClick = { onIntent(PieceIntent.VideoImportSendClicked) }, modifier = Modifier.weight(1f)) {
            IconLabel(AppIcons.Share, stringResource(R.string.video_send), iconSize = 20.dp)
        }
    }
}

