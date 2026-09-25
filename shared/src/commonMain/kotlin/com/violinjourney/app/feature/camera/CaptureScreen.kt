package com.violinjourney.app.feature.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_block_title
import com.violinjourney.app.shared.resources.backing_needs_headphones
import com.violinjourney.app.shared.resources.backing_preparing
import com.violinjourney.app.shared.resources.capture_camera_failed
import com.violinjourney.app.shared.resources.capture_close
import com.violinjourney.app.shared.resources.capture_grant
import com.violinjourney.app.shared.resources.capture_low_space
import com.violinjourney.app.shared.resources.capture_no_permission
import com.violinjourney.app.shared.resources.capture_open_settings
import com.violinjourney.app.shared.resources.capture_record
import com.violinjourney.app.shared.resources.capture_saving
import com.violinjourney.app.shared.resources.capture_stop
import com.violinjourney.app.shared.resources.capture_switch_camera
import com.violinjourney.app.shared.resources.live_mic_unavailable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme

private val RecordButton = 76.dp
private val Scrim = Color(0xB3000000)
private val ScrimTop = Color(0x8C000000)
private val OnPicture = Color.White
private const val DIM = 0.7f
private const val TABULAR_FIGURES = "tnum"

/**
 * «Снять под минусовку» (spec 3.32, mockup `docs/design/project/backing`): the viewfinder over the whole screen, blind
 * like any take — a red dot, the timer, the backing's bar. A tap on the picture focuses there. Stateless.
 */
@Composable
fun CaptureScreen(
    state: CaptureState,
    /** The picture of the camera, bound to the screen by the platform. */
    viewfinder: @Composable (Modifier) -> Unit,
    onIntent: (CaptureIntent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        viewfinder(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { at -> onIntent(CaptureIntent.FocusAt(at.x / width, at.y / height)) } },
        )
        when {
            state.cameraPermission == false || state.micPermission == false -> Refused(onIntent, onOpenSettings)
            state.cameraFailed -> Centered(stringResource(Res.string.capture_camera_failed))
        }
        if (landscape) {
            TopRow(state, onIntent, Modifier.align(Alignment.TopStart).fillMaxWidth(0.7f))
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(210.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Scrim)))
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Controls(state, onIntent, compact = true)
            }
        } else {
            TopRow(state, onIntent, Modifier.align(Alignment.TopStart).fillMaxWidth())
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim)))
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Controls(state, onIntent, compact = false)
            }
        }
        if (state.saving) Saving()
    }
}

@Composable
private fun TopRow(state: CaptureState, onIntent: (CaptureIntent) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(ScrimTop, Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onIntent(CaptureIntent.CloseClicked) }) {
            AppIcon(AppIcons.Close, contentDescription = stringResource(Res.string.capture_close), tint = OnPicture)
        }
        Text(
            state.title,
            color = OnPicture,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onIntent(CaptureIntent.SwitchCameraClicked) }, enabled = !state.recording) {
            AppIcon(AppIcons.Repeat, contentDescription = stringResource(Res.string.capture_switch_camera), tint = if (state.recording) OnPicture.copy(alpha = 0.4f) else OnPicture)
        }
    }
}

@Composable
private fun Controls(state: CaptureState, onIntent: (CaptureIntent) -> Unit, compact: Boolean) {
    val spaceMinutes = state.spaceMinutes
    val hint = when {
        state.underBacking && state.noHeadphones && !state.recording -> stringResource(Res.string.backing_needs_headphones)
        state.underBacking && state.preparing && !state.recording -> stringResource(Res.string.backing_preparing)
        state.micUnavailable -> stringResource(Res.string.live_mic_unavailable)
        spaceMinutes != null && !state.recording -> stringResource(Res.string.capture_low_space, spaceMinutes)
        else -> null
    }
    hint?.let {
        Text(it, color = OnPicture, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
    }
    if (state.recording) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(Color(0x80000000)).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(ViolinTheme.zoneColors.off))
            Text(Formats.timer(state.elapsedSeconds * MS_PER_SECOND), color = OnPicture, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES))
        }
    }
    val played = state.backingPlayedMs
    if (state.backingTitle != null && played != null && state.backingDurationMs > 0) {
        Column(modifier = Modifier.fillMaxWidth(if (compact) 0.9f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(AppIcons.Backing, contentDescription = null, tint = OnPicture.copy(alpha = DIM), size = 14.dp)
                Text(stringResource(Res.string.backing_block_title), color = OnPicture.copy(alpha = DIM), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
                Text(
                    "${Formats.duration(played.coerceAtMost(state.backingDurationMs))} / ${Formats.duration(state.backingDurationMs)}",
                    color = OnPicture.copy(alpha = DIM),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(OnPicture.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth((played.toFloat() / state.backingDurationMs).coerceIn(0f, 1f)).height(3.dp).background(OnPicture))
            }
        }
    }
    val enabled = state.recording || state.canRecord
    val label = stringResource(if (state.recording) Res.string.capture_stop else Res.string.capture_record)
    Box(
        modifier = Modifier
            .size(RecordButton)
            .clip(CircleShape)
            .border(4.dp, OnPicture.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) { onIntent(CaptureIntent.RecordClicked) },
        contentAlignment = Alignment.Center,
    ) {
        // the red of the record dot of every take, not the red of «мимо»: here no zone is shown
        val red = ViolinTheme.zoneColors.off.copy(alpha = if (enabled) 1f else 0.4f)
        if (state.recording) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(red))
        } else {
            Box(Modifier.size(58.dp).clip(CircleShape).background(red))
        }
    }
}

@Composable
private fun Refused(onIntent: (CaptureIntent) -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        AppIcon(AppIcons.Camera, contentDescription = null, tint = OnPicture, size = 40.dp)
        Text(stringResource(Res.string.capture_no_permission), color = OnPicture, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp))
        TextButton(onClick = { onIntent(CaptureIntent.RecordClicked) }) { Text(stringResource(Res.string.capture_grant)) }
        TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.capture_open_settings)) }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = OnPicture, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp))
    }
}

@Composable
private fun Saving() {
    Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = OnPicture)
            Text(stringResource(Res.string.capture_saving), color = OnPicture, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
        }
    }
}

private const val MS_PER_SECOND = 1_000L
