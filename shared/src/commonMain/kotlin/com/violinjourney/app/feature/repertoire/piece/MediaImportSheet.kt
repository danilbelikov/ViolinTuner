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
import com.violinjourney.app.core.recording.ImportFailure
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.components.rememberSmallFileImage
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_video_error_no_notes
import com.violinjourney.app.shared.resources.file_copying
import com.violinjourney.app.shared.resources.file_error_cannot_open
import com.violinjourney.app.shared.resources.file_error_no_notes
import com.violinjourney.app.shared.resources.file_error_no_sound
import com.violinjourney.app.shared.resources.file_error_too_long
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
private val ThumbIcon = 20.dp
private val StripHeight = 48.dp
private val StripCorner = 10.dp
private val StripBar = 6.dp
private val StripCursor = 2.dp
private val FaceGap = 14.dp
private const val TABULAR_FIGURES = "tnum"

/**
 * Whose words the sheet speaks (plan D19): a video that is to be a take of a piece («дубль не добавлен»), a video of an event that is
 * to be a recording («запись не добавлена» — it is no take, spec 3.36.9), a sound from a file («Добавляем файл…», «Не получилось
 * открыть файл», spec 3.35, 5.28).
 */
enum class ImportWords { VIDEO_TAKE, VIDEO_RECORD, SOUND_FILE }

/** What a button of the sheet asks of the importer; the screen hands it on as its own intent. */
enum class ImportAction {
    /** «Отмена»: a picked file goes, a shot is asked about. */
    Cancel,

    /** «Продолжить разбор». */
    Continue,

    /** «Удалить» of a shot that did not become a recording, and «Понятно» of any other failure. */
    Dismiss,

    /** «Отправить видео»: a shot that did not become a recording goes to the system sheet. */
    Send,
}

/** The faces of the sheet of a file on its way in: each a face of one frame (spec 3.36.3). */
private enum class ImportFace { WORKING, ASKING, FAILED }

/**
 * A file on its way to becoming a recording (spec 3.19, 3.35, 3.36.4, handoff 20b) — a video, shot or picked, or a sound from a file —
 * in the frame of the sheets of R1, in the [words] of whose it is to be. Closes neither by a tap outside, nor by a swipe, nor by
 * «назад»: behind it a file may be deleted — only the importer lets it go ([AppSheet] with `dismissible = false`, no handle). What went
 * wrong is a line here, not a toast; «Удалить» and the sign of a failure are coral. Its buttons of 56 — «Отправить видео», «Понятно»,
 * «Отмена» — are 48 in a window no higher than 360, as those of [com.violinjourney.app.core.ui.components.AppSheetButtons]; «Удалить»
 * and «Продолжить» are 48 anyway. A sound has no first frame: its place under the thumbnail holds the sign of a file of sound.
 */
@Composable
fun MediaImportSheet(import: MediaImport, words: ImportWords, onAction: (ImportAction) -> Unit) {
    val shown = when (import) {
        MediaImport.Idle -> null
        is MediaImport.Working -> import.takeIf { it.visible }
        is MediaImport.Failed -> import
    }
    AppSheet(
        value = shown,
        onHide = {},
        dismissible = false,
        faceOf = { value ->
            when (value) {
                is MediaImport.Working -> if (value.asking) ImportFace.ASKING else ImportFace.WORKING
                else -> ImportFace.FAILED
            }
        },
    ) { value ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FaceGap)) {
            when {
                value is MediaImport.Working && value.asking -> Rescue(Res.string.video_stop_title, showContinue = true, onAction)
                value is MediaImport.Working -> Working(value, words, onAction)
                value is MediaImport.Failed -> Failed(value, words, onAction)
            }
        }
    }
}

@Composable
private fun Working(import: MediaImport.Working, words: ImportWords, onAction: (ImportAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val thumb = import.thumbPath?.let { rememberSmallFileImage(it) }
        Box(
            Modifier
                .size(ThumbWidth, ThumbHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) Image(thumb, contentDescription = stringResource(Res.string.video_thumb), contentScale = ContentScale.Crop, modifier = Modifier.size(ThumbWidth, ThumbHeight))
            if (words == ImportWords.SOUND_FILE) AppIcon(AppIcons.FileAudio, contentDescription = null, tint = colors.onSurfaceVariant, size = ThumbIcon)
        }
        Text(
            text = stringResource(
                when {
                    !import.copying -> Res.string.video_listening
                    words == ImportWords.SOUND_FILE -> Res.string.file_copying
                    else -> Res.string.video_copying
                },
            ),
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
        AppButton(stringResource(Res.string.video_cancel), onClick = { onAction(ImportAction.Cancel) }, style = AppButtonStyle.Outline, compact = currentDockMetrics().compact)
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
private fun Failed(import: MediaImport.Failed, words: ImportWords, onAction: (ImportAction) -> Unit) {
    if (import.reason == ImportFailure.STOPPED) {
        Rescue(Res.string.video_stopped_title, showContinue = false, onAction)
        return
    }
    val colors = MaterialTheme.colorScheme
    val sound = words == ImportWords.SOUND_FILE
    val title = when (import.reason) {
        ImportFailure.NO_SOUND -> stringResource(if (sound) Res.string.file_error_no_sound else Res.string.video_error_no_sound)
        ImportFailure.TOO_LONG -> stringResource(if (sound) Res.string.file_error_too_long else Res.string.video_error_too_long)
        ImportFailure.CANNOT_OPEN -> stringResource(if (sound) Res.string.file_error_cannot_open else Res.string.video_error_cannot_open)
        ImportFailure.NO_NOTES -> stringResource(
            when (words) {
                ImportWords.VIDEO_TAKE -> Res.string.video_error_no_notes
                ImportWords.VIDEO_RECORD -> Res.string.event_video_error_no_notes
                ImportWords.SOUND_FILE -> Res.string.file_error_no_notes
            },
        )
        ImportFailure.NO_SPACE -> stringResource(Res.string.video_error_no_space, import.missingMb ?: 0)
        ImportFailure.STOPPED -> ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(AppIcons.Alert, contentDescription = null, tint = ViolinTheme.dangerSoft)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
            val more = when {
                // a shot exists only here — that is what the two ways out below are about
                import.rescuePath != null -> stringResource(Res.string.video_stop_text)
                // a video from the gallery is still there; a file of sound is wherever it was picked, and says nothing of a gallery
                import.reason == ImportFailure.NO_NOTES && !sound -> stringResource(Res.string.video_error_no_notes_gallery)
                else -> null
            }
            if (more != null) Text(more, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
        }
    }
    if (import.rescuePath != null) {
        RescueButtons(onAction)
    } else {
        AppButton(stringResource(Res.string.video_ok), onClick = { onAction(ImportAction.Dismiss) }, modifier = Modifier.fillMaxWidth(), compact = currentDockMetrics().compact)
    }
}

/** A shot that did not become a recording exists nowhere else: send it somewhere, or let it go. */
@Composable
private fun Rescue(title: StringResource, showContinue: Boolean, onAction: (ImportAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(stringResource(title), color = colors.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
    Text(stringResource(Res.string.video_stop_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
    RescueButtons(onAction)
    if (showContinue) {
        AppButton(stringResource(Res.string.video_continue), onClick = { onAction(ImportAction.Continue) }, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Quiet)
    }
}

/** «Отправить видео» — the main button, the only way to keep a shot that did not become a recording; under it «Удалить», coral (R1). */
@Composable
private fun RescueButtons(onAction: (ImportAction) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppButton(
            stringResource(Res.string.video_send), onClick = { onAction(ImportAction.Send) }, modifier = Modifier.fillMaxWidth(), icon = AppIcons.Share,
            compact = currentDockMetrics().compact,
        )
        AppButton(stringResource(Res.string.video_delete), onClick = { onAction(ImportAction.Dismiss) }, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Danger)
    }
}
