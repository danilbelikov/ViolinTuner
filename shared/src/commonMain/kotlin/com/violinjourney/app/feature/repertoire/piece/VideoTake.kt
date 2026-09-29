package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.session.RecordingRibbon
import com.violinjourney.app.core.recording.video.VideoImport
import com.violinjourney.app.core.recording.video.VideoImportFailure
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.components.rememberSmallFileImage
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.video_cancel
import com.violinjourney.app.shared.resources.video_continue
import com.violinjourney.app.shared.resources.video_copying
import com.violinjourney.app.shared.resources.video_delete
import com.violinjourney.app.shared.resources.video_error_cannot_open
import com.violinjourney.app.shared.resources.video_error_no_notes
import com.violinjourney.app.shared.resources.video_error_no_notes_gallery
import com.violinjourney.app.shared.resources.video_error_no_sound
import com.violinjourney.app.shared.resources.video_error_no_space
import com.violinjourney.app.shared.resources.video_error_too_long
import com.violinjourney.app.shared.resources.video_listening
import com.violinjourney.app.shared.resources.video_ok
import com.violinjourney.app.shared.resources.video_percent
import com.violinjourney.app.shared.resources.video_remaining
import com.violinjourney.app.shared.resources.video_send
import com.violinjourney.app.shared.resources.video_stop_text
import com.violinjourney.app.shared.resources.video_stop_title
import com.violinjourney.app.shared.resources.video_stopped_title
import com.violinjourney.app.shared.resources.video_thumb
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val ThumbWidth = 64.dp
private val ThumbHeight = 36.dp
private val StripHeight = 48.dp
private val StripCorner = 10.dp
private val StripBar = 6.dp
private val StripCursor = 2.dp
private val FaceGap = 14.dp
private const val TABULAR_FIGURES = "tnum"

/** The faces of the sheet of a video on its way in: each a face of one frame (spec 3.36.3). */
private enum class ImportFace { WORKING, ASKING, FAILED }

/**
 * A video on its way to becoming a take (spec 3.19, 3.36.4, handoff 20b), in the frame of the sheets of R1. Closes neither by a tap
 * outside, nor by a swipe, nor by «назад»: behind it a file may be deleted — only the importer lets it go ([AppSheet] with
 * `dismissible = false`, no handle). What went wrong is a line here, not a toast; «Удалить» and the sign of a failure are coral.
 * Its buttons of 56 — «Отправить видео», «Понятно», «Отмена» — are 48 in a window no higher than 360, as those of
 * [com.violinjourney.app.core.ui.components.AppSheetButtons]; «Удалить» and «Продолжить» are 48 anyway.
 */
@Composable
fun VideoImportSheet(import: VideoImport, onIntent: (PieceIntent) -> Unit) {
    val shown = when (import) {
        VideoImport.Idle -> null
        is VideoImport.Working -> import.takeIf { it.visible }
        is VideoImport.Failed -> import
    }
    AppSheet(
        value = shown,
        onHide = {},
        dismissible = false,
        faceOf = { value ->
            when (value) {
                is VideoImport.Working -> if (value.asking) ImportFace.ASKING else ImportFace.WORKING
                else -> ImportFace.FAILED
            }
        },
    ) { value ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FaceGap)) {
            when {
                value is VideoImport.Working && value.asking -> Rescue(Res.string.video_stop_title, showContinue = true, onIntent)
                value is VideoImport.Working -> Working(value, onIntent)
                value is VideoImport.Failed -> Failed(value, onIntent)
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
            if (thumb != null) Image(thumb, contentDescription = stringResource(Res.string.video_thumb), contentScale = ContentScale.Crop, modifier = Modifier.size(ThumbWidth, ThumbHeight))
        }
        Text(
            text = stringResource(if (import.copying) Res.string.video_copying else Res.string.video_listening),
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        if (!import.copying) {
            Text(
                text = stringResource(Res.string.video_percent, import.percent),
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
            text = import.remainingSec?.let { stringResource(Res.string.video_remaining, it) }.orEmpty(),
            modifier = Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
        // an outline of 56, as every button of 56 of this sheet — 48 in a window no higher than 360 (spec 3.36.4; the rule of
        // AppSheetButtons, R3)
        AppButton(stringResource(Res.string.video_cancel), onClick = { onIntent(PieceIntent.VideoImportCancelClicked) }, style = AppButtonStyle.Outline, compact = currentDockMetrics().compact)
    }
}

/**
 * The wait is a first look at the result: the notes found so far stand as pieces in the colour of
 * their zone, left to right, as on the recording strip of Live; the cursor is the progress.
 */
@Composable
private fun EmergingStrip(bars: RecordingRibbon, fraction: Float) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    val cursor = colors.primary
    val pieces = bars.pieces.map { bars.share(it) to zoneColors.colorFor(it.zone) }
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
        Rescue(Res.string.video_stopped_title, showContinue = false, onIntent)
        return
    }
    val colors = MaterialTheme.colorScheme
    val title = when (import.reason) {
        VideoImportFailure.NO_SOUND -> stringResource(Res.string.video_error_no_sound)
        VideoImportFailure.TOO_LONG -> stringResource(Res.string.video_error_too_long)
        VideoImportFailure.CANNOT_OPEN -> stringResource(Res.string.video_error_cannot_open)
        VideoImportFailure.NO_NOTES -> stringResource(Res.string.video_error_no_notes)
        VideoImportFailure.NO_SPACE -> stringResource(Res.string.video_error_no_space, import.missingMb ?: 0)
        VideoImportFailure.STOPPED -> ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(AppIcons.Alert, contentDescription = null, tint = ViolinTheme.dangerSoft)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
            val more = when {
                // a shot exists only here — that is what the two ways out below are about
                import.rescuePath != null -> stringResource(Res.string.video_stop_text)
                import.reason == VideoImportFailure.NO_NOTES -> stringResource(Res.string.video_error_no_notes_gallery)
                else -> null
            }
            if (more != null) Text(more, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
        }
    }
    if (import.rescuePath != null) {
        RescueButtons(onIntent)
    } else {
        AppButton(stringResource(Res.string.video_ok), onClick = { onIntent(PieceIntent.VideoImportDismissed) }, modifier = Modifier.fillMaxWidth(), compact = currentDockMetrics().compact)
    }
}

/** A shot that did not become a take exists nowhere else: send it somewhere, or let it go. */
@Composable
private fun Rescue(title: StringResource, showContinue: Boolean, onIntent: (PieceIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(stringResource(title), color = colors.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
    Text(stringResource(Res.string.video_stop_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
    RescueButtons(onIntent)
    if (showContinue) {
        AppButton(stringResource(Res.string.video_continue), onClick = { onIntent(PieceIntent.VideoImportContinueClicked) }, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Quiet)
    }
}

/** «Отправить видео» — the main button, the only way to keep a shot that did not become a take; under it «Удалить», coral (R1). */
@Composable
private fun RescueButtons(onIntent: (PieceIntent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppButton(
            stringResource(Res.string.video_send), onClick = { onIntent(PieceIntent.VideoImportSendClicked) }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Share,
            compact = currentDockMetrics().compact,
        )
        AppButton(stringResource(Res.string.video_delete), onClick = { onIntent(PieceIntent.VideoImportDismissed) }, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Danger)
    }
}

