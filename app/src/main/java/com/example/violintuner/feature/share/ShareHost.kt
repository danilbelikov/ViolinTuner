package com.example.violintuner.feature.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.R
import com.example.violintuner.core.audio.share.ShareNames
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.sound.captionName
import java.io.File

private const val SWAP_MS = 200
private const val BYTES_PER_KB = 1_024L
private const val AUDIO_TYPE = "audio/mp4"
private const val VIDEO_TYPE = "video/mp4"
private const val LARGE_FILE_BYTES = 100L * 1024 * 1024
private const val MP4 = ".mp4"
private const val M4A = ".m4a"

/** Matches `android:authorities` of the FileProvider in the manifest. */
private const val FILES_AUTHORITY_SUFFIX = ".files"

/**
 * Hosts «Поделиться» on a screen: shows the sheet of [viewModel] and hands the finished file to
 * the system. A route puts it beside its screen and calls [ShareViewModel.start].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareHost(viewModel: ShareViewModel) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is ShareEffect.Send -> context.send(effect.file, effect.text)
                }
            }
        }
    }

    val shown = sheet ?: return
    ModalBottomSheet(
        onDismissRequest = { viewModel.onIntent(ShareIntent.Dismissed) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // One sheet, its content changes: choosing → preparing → (failed). Keyed by kind, so that a percent ticking does not fade.
        Crossfade(targetState = shown::class, animationSpec = tween(SWAP_MS), label = "shareSheet") { kind ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val current = sheet ?: shown
                when {
                    kind == ShareSheet.Choose::class && current is ShareSheet.Choose -> Choose(current, viewModel::onIntent)
                    kind == ShareSheet.Preparing::class && current is ShareSheet.Preparing -> Preparing(current, viewModel::onIntent)
                    kind == ShareSheet.Failed::class && current is ShareSheet.Failed -> Failed(current.info.video, viewModel::onIntent)
                }
            }
        }
    }
}

/** The receiver gets a temporary grant to read this one file; the app asks for no permission (spec 3.17). */
private fun Context.send(file: File, text: String?) {
    val uri = FileProvider.getUriForFile(this, "$packageName$FILES_AUTHORITY_SUFFIX", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (file.name.endsWith(ShareNames.VIDEO_EXTENSION)) VIDEO_TYPE else AUDIO_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
        // the chooser reads the grant and the preview from the clip data
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.share_no_app, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Choose(sheet: ShareSheet.Choose, onIntent: (ShareIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val info = sheet.info
    Text(stringResource(R.string.share_title), color = colors.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (info.video) {
            // From "what is heard" to "what was" (spec 3.19); the chip answers "and what file will that be?" before it is asked.
            if (info.processed) {
                Variant(
                    title = stringResource(R.string.share_video_processed),
                    caption = stringResource(R.string.share_processed_caption, captionName(info.caption)),
                    selected = sheet.variant == ShareVariant.PROCESSED, enabled = !sheet.busy, chip = MP4,
                ) { onIntent(ShareIntent.VariantSelected(ShareVariant.PROCESSED)) }
            }
            Variant(
                title = stringResource(if (info.processed) R.string.share_video_original else R.string.share_video),
                caption = stringResource(if (info.processed) R.string.share_video_original_caption else R.string.share_video_caption),
                selected = sheet.variant == ShareVariant.ORIGINAL, enabled = !sheet.busy, chip = MP4,
            ) { onIntent(ShareIntent.VariantSelected(ShareVariant.ORIGINAL)) }
            Variant(
                title = stringResource(R.string.share_sound_only),
                caption = stringResource(if (info.processed) R.string.share_sound_only_processed else R.string.share_sound_only_caption, sizeText(info.processedBytes)),
                selected = sheet.variant == ShareVariant.SOUND, enabled = !sheet.busy, chip = M4A,
            ) { onIntent(ShareIntent.VariantSelected(ShareVariant.SOUND)) }
        } else {
            Variant(
                title = stringResource(R.string.share_processed),
                caption = stringResource(R.string.share_processed_caption, captionName(info.caption)),
                selected = sheet.variant == ShareVariant.PROCESSED, enabled = !sheet.busy,
            ) { onIntent(ShareIntent.VariantSelected(ShareVariant.PROCESSED)) }
            Variant(
                title = stringResource(R.string.share_original),
                caption = stringResource(R.string.share_original_caption),
                selected = sheet.variant == ShareVariant.ORIGINAL, enabled = !sheet.busy,
            ) { onIntent(ShareIntent.VariantSelected(ShareVariant.ORIGINAL)) }
        }
    }
    val asVideo = info.video && sheet.variant != ShareVariant.SOUND
    val bytes = info.bytesOf(sheet.variant)
    val large = bytes >= LARGE_FILE_BYTES
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(if (asVideo) AppIcons.Video else AppIcons.FileAudio, contentDescription = null, tint = colors.onSurfaceVariant)
        Column {
            Text(info.fileNameOf(sheet.variant), color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            val details = if (asVideo) {
                stringResource(R.string.share_video_details, Formats.duration(info.durationMs), info.resolution, Formats.fileSize(bytes))
            } else {
                stringResource(R.string.share_file_details, Formats.duration(info.durationMs), sizeText(bytes))
            }
            Text(
                text = details,
                // a size that messengers squeeze or refuse is said in colour and, below, in words
                color = if (large) ViolinTheme.videoColors.sizeWarn else colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = if (large) FontWeight.Bold else FontWeight.Normal, fontFeatureSettings = "tnum"),
            )
            if (large) {
                Text(stringResource(R.string.share_large_file), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = sheet.withText, enabled = !sheet.busy, role = Role.Checkbox) { onIntent(ShareIntent.TextToggled(it)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = sheet.withText, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp))
        Text(stringResource(R.string.share_with_text, info.message), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
    }
    Button(
        onClick = { onIntent(ShareIntent.ContinueClicked) },
        enabled = !sheet.busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary, disabledContainerColor = colors.primaryContainer, disabledContentColor = colors.onPrimaryContainer),
    ) {
        Text(stringResource(if (sheet.busy) R.string.share_busy else R.string.share_continue), style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Variant(title: String, caption: String, selected: Boolean, enabled: Boolean, chip: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .border(1.5.dp, if (selected) colors.primary else colors.surfaceContainerHigh, RoundedCornerShape(14.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (selected) colors.primary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(10.dp).background(colors.primary, CircleShape)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(caption, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        }
        if (chip != null) {
            Text(
                text = chip,
                modifier = Modifier
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun Preparing(sheet: ShareSheet.Preparing, onIntent: (ShareIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Bottom) {
        Text(stringResource(R.string.share_preparing), color = colors.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Text("${sheet.percent} %", color = colors.primary, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
    }
    LinearProgressIndicator(
        progress = { sheet.percent / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = colors.primary,
        trackColor = colors.surfaceContainerHigh,
        drawStopIndicator = {},
    )
    val details = listOfNotNull(captionName(sheet.info.caption), Formats.duration(sheet.info.durationMs), sheet.remainingSec?.let { stringResource(R.string.share_remaining, it) })
    Text(details.joinToString(" · "), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"))
    OutlinedButton(onClick = { onIntent(ShareIntent.CancelClicked) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) {
        Text(stringResource(R.string.dialog_cancel))
    }
}

@Composable
private fun Failed(video: Boolean, onIntent: (ShareIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // With a video take it is the picture that is worth sending: the way out is «как снято», not the sound alone (spec 3.19).
    Text(stringResource(if (video) R.string.share_failed_video_title else R.string.share_failed_title), color = colors.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
    Text(stringResource(if (video) R.string.share_failed_video_text else R.string.share_failed_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { onIntent(ShareIntent.RetryClicked) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp)) { Text(stringResource(R.string.share_retry)) }
        Button(
            onClick = { onIntent(ShareIntent.SendOriginalClicked) },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        ) { Text(stringResource(if (video) R.string.share_send_as_shot else R.string.share_send_original), maxLines = 1) }
    }
}

/** «340 КБ», «2,1 МБ». */
@Composable
private fun sizeText(bytes: Long): String {
    val kb = bytes / BYTES_PER_KB
    return if (kb < BYTES_PER_KB) stringResource(R.string.share_size_kb, kb.coerceAtLeast(1)) else stringResource(R.string.share_size_mb, Formats.oneDecimal(kb / BYTES_PER_KB.toDouble()))
}
