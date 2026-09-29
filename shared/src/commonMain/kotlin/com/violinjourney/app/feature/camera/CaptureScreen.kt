package com.violinjourney.app.feature.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.GlassPlate
import com.violinjourney.app.core.ui.components.PermissionLine
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.components.BackingProgressLine
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_headphones
import com.violinjourney.app.shared.resources.backing_headphones_wired
import com.violinjourney.app.shared.resources.backing_needs_headphones
import com.violinjourney.app.shared.resources.backing_preparing
import com.violinjourney.app.shared.resources.backing_unprepared
import com.violinjourney.app.shared.resources.capture_backing_length
import com.violinjourney.app.shared.resources.capture_camera_failed
import com.violinjourney.app.shared.resources.capture_close
import com.violinjourney.app.shared.resources.capture_grant
import com.violinjourney.app.shared.resources.capture_low_space
import com.violinjourney.app.shared.resources.capture_no_permission
import com.violinjourney.app.shared.resources.capture_record
import com.violinjourney.app.shared.resources.capture_saving
import com.violinjourney.app.shared.resources.capture_stop
import com.violinjourney.app.shared.resources.capture_switch_camera
import com.violinjourney.app.shared.resources.live_mic_unavailable
import org.jetbrains.compose.resources.stringResource

// The own camera (spec 3.36.4, 5.29 R4; repertoire.html 7).
private val TopButton = 48.dp
private val Shutter = 76.dp
private val ShutterRing = 4.dp
private val ShutterDot = 58.dp
private val ShutterStop = 28.dp
private val ShutterStopCorner = 7.dp
private val TimerHeight = 34.dp
private val TimerDot = 10.dp
private val TimerTop = 8.dp
private val LineIcon = 16.dp
private val PlateIcon = 18.dp
private val PlateToShutter = 6.dp
private val LineToShutter = 12.dp
private val SideColumn = 210.dp
private val SavingCorner = 18.dp
private const val TOP_ROW_SHARE_LANDSCAPE = 0.7f
private const val ASLEEP_ALPHA = 0.38f
private val ScrimBottom = Color(0xBF000000)
private val ScrimTop = Color(0x99000000)
private const val TABULAR_FIGURES = "tnum"
private const val MS_PER_SECOND = 1_000L

/**
 * «Снять под минусовку» and «Снять видео» of a piece with a backing (spec 3.32, 3.36.4): the viewfinder over the whole screen, ✕,
 * the name of the piece and the turn of the camera above; the round shutter below, and over it always one thing ([CaptureAbove]):
 * the line «нет разрешения», a plate on dense glass, «наушники · минусовка», or the backing's progress during a shot. The shot is
 * blind like any take: the timer is a pill under the top row. A tap on the picture focuses there. Stateless.
 */
@Composable
fun CaptureScreen(
    state: CaptureState,
    /** The picture of the camera, bound to the screen by the platform. */
    viewfinder: @Composable (Modifier) -> Unit,
    onIntent: (CaptureIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        viewfinder(
            Modifier
                .fillMaxSize()
                // shares of the viewfinder as it is now: a turn of an iPhone changes its size without a new screen
                .pointerInput(Unit) { detectTapGestures { at -> onIntent(CaptureIntent.FocusAt(at.x / size.width, at.y / size.height)) } },
        )
        if (state.cameraFailed) Centered(stringResource(Res.string.capture_camera_failed))
        val above = CaptureAbove.of(state)
        if (landscape) {
            TopRow(state, onIntent, Modifier.align(Alignment.TopStart).fillMaxWidth(TOP_ROW_SHARE_LANDSCAPE))
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(SideColumn)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, ScrimBottom)))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                // from the top down: the plate or the line, the timer, the backing's progress, the shutter
                if (state.recording) TimerPill(state.elapsedSeconds)
                Above(above, state, onIntent)
                ShutterButton(state, onIntent)
            }
        } else {
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TopRow(state, onIntent, Modifier.fillMaxWidth())
                if (state.recording) TimerPill(state.elapsedSeconds, Modifier.padding(top = TimerTop))
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, ScrimBottom)))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (above != null) {
                    Above(above, state, onIntent)
                    Spacer(Modifier.height(if (above is CaptureAbove.NoPermission || above is CaptureAbove.Plate) PlateToShutter else LineToShutter))
                }
                ShutterButton(state, onIntent)
            }
        }
        if (state.saving) Saving()
    }
}

@Composable
private fun TopRow(state: CaptureState, onIntent: (CaptureIntent) -> Unit, modifier: Modifier) {
    val onPicture = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(ScrimTop, Color.Transparent)))
            // what of the bars the screen's host has not taken already: the named statusBarsPadding() of Compose
            // Multiplatform on iOS does not see the host's share and put the whole status bar here a second time
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // asleep under «Собираем видео…»: the screen closes by itself once the take is saved (spec 3.32)
        PictureButton(AppIcons.Close, stringResource(Res.string.capture_close), enabled = !state.saving) { onIntent(CaptureIntent.CloseClicked) }
        Text(
            state.title,
            color = onPicture,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.weight(1f),
        )
        PictureButton(AppIcons.Repeat, stringResource(Res.string.capture_switch_camera), enabled = !state.recording && !state.saving) {
            onIntent(CaptureIntent.SwitchCameraClicked)
        }
    }
}

/** A button of 48 over the picture: its icon light, and 0.38 while it sleeps. */
@Composable
private fun PictureButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(TopButton)
            .alpha(if (enabled) 1f else ASLEEP_ALPHA)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
}

/** «● 1:12» — the timer of a shot, a pill of dense glass: blind, like any take. */
@Composable
private fun TimerPill(elapsedSeconds: Long, modifier: Modifier = Modifier) {
    GlassPlate(modifier.height(TimerHeight), strong = true, contentPadding = PaddingValues(horizontal = 14.dp)) {
        Box(Modifier.size(TimerDot).background(ViolinTheme.recording, CircleShape))
        Text(
            Formats.timer(elapsedSeconds * MS_PER_SECOND),
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/** What stands over the shutter ([CaptureAbove]). */
@Composable
private fun ColumnScope.Above(above: CaptureAbove?, state: CaptureState, onIntent: (CaptureIntent) -> Unit) {
    val light = MaterialTheme.colorScheme.onSurface
    when (above) {
        null -> Unit
        CaptureAbove.NoPermission -> PermissionLine(
            reason = stringResource(Res.string.capture_no_permission),
            onGrant = { onIntent(CaptureIntent.GrantClicked) },
            modifier = Modifier.fillMaxWidth(),
            icon = AppIcons.Camera,
            grant = stringResource(Res.string.capture_grant),
            overPicture = true,
            shape = AppShapes.S,
        )
        is CaptureAbove.Plate -> Plate(above.plate, state)
        CaptureAbove.Ready -> ReadyLine(state)
        CaptureAbove.BackingProgress -> BackingProgressLine(
            playedMs = state.backingPlayedMs ?: 0,
            durationMs = state.backingDurationMs,
            color = light,
            track = light.copy(alpha = PROGRESS_TRACK_ALPHA),
        )
    }
}

private const val PROGRESS_TRACK_ALPHA = 0.25f

@Composable
private fun Plate(plate: CapturePlate, state: CaptureState) {
    val (icon, text) = when (plate) {
        CapturePlate.NO_HEADPHONES -> AppIcons.Headphones to stringResource(Res.string.backing_needs_headphones)
        CapturePlate.PREPARING -> AppIcons.Backing to stringResource(Res.string.backing_preparing)
        CapturePlate.UNPREPARED -> AppIcons.Alert to stringResource(Res.string.backing_unprepared)
        CapturePlate.MIC_UNAVAILABLE -> AppIcons.Mic to stringResource(Res.string.live_mic_unavailable)
        CapturePlate.LOW_SPACE -> AppIcons.Info to stringResource(Res.string.capture_low_space, state.spaceMinutes ?: 0)
    }
    GlassPlate(
        Modifier.fillMaxWidth(),
        strong = true,
        shape = AppShapes.S,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // light like its words: there is no grey on glass (spec 3.36.1, 5.29 R4)
        AppIcon(icon, contentDescription = null, size = PlateIcon, modifier = Modifier.align(Alignment.Top))
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
    }
}

/**
 * «[наушники] Pixel Buds · [минусовка] минусовка 3:40»: all that is checked before the start (spec 3.36.4) — one line, the
 * headphones at the start and the backing at the end, while the name keeps at least [ReadyNameLeast] beside the whole length
 * (or all of itself, when shorter; [ReadyLineFit]); the name gives way with an ellipsis, the length never. Where it does not — the
 * side column of 210 lying, a large font — the two stand one under the other, each on the whole width: the name still ellipsized
 * on its line, the length whole, going on a second line at its space if it must («acompañamiento / 3:40» at the font 1.3).
 */
@Composable
private fun ReadyLine(state: CaptureState) {
    val light = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES)
    val name = state.headphonesName ?: stringResource(Res.string.backing_headphones)
    val length = stringResource(Res.string.capture_backing_length, Formats.duration(state.backingDurationMs))
    val said = (state.headphonesName?.let { stringResource(Res.string.backing_headphones_wired, it) } ?: name) + ", " + length
    Layout(
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReadyIconGap)) {
                AppIcon(AppIcons.Headphones, contentDescription = null, tint = light, size = LineIcon)
                Text(name, modifier = Modifier.weight(1f, fill = false), color = light, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReadyIconGap)) {
                AppIcon(AppIcons.Backing, contentDescription = null, tint = light, size = LineIcon)
                Text(length, modifier = Modifier.weight(1f, fill = false), color = light, textAlign = TextAlign.Center, style = style)
            }
        },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = said },
    ) { (headphones, backing), constraints ->
        val width = constraints.maxWidth
        val lengthWhole = backing.maxIntrinsicWidth(Constraints.Infinity)
        val nameLeast = (LineIcon + ReadyIconGap + ReadyNameLeast).roundToPx()
        val gap = ReadyPartsGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        if (ReadyLineFit.oneRow(width, lengthWhole, headphones.maxIntrinsicWidth(Constraints.Infinity), nameLeast, gap)) {
            val end = backing.measure(loose.copy(maxWidth = lengthWhole))
            val start = headphones.measure(loose.copy(maxWidth = (width - lengthWhole - gap).coerceAtLeast(0)))
            val height = maxOf(start.height, end.height)
            layout(width, height) {
                start.placeRelative(0, (height - start.height) / 2)
                end.placeRelative(width - end.width, (height - end.height) / 2)
            }
        } else {
            val top = headphones.measure(loose)
            val under = backing.measure(loose)
            val between = ReadyLinesGap.roundToPx()
            layout(width, top.height + between + under.height) {
                top.placeRelative((width - top.width) / 2, 0)
                under.placeRelative((width - under.width) / 2, top.height + between)
            }
        }
    }
}

/** The name of the headphones keeps at least this much of the line beside the length («Pixel Bu…»); less — two lines. */
private val ReadyNameLeast = 64.dp
private val ReadyIconGap = 6.dp
private val ReadyPartsGap = 12.dp
private val ReadyLinesGap = 4.dp

/** The round shutter (spec 3.32): the red circle of recording in a white ring, «стоп» as a square; asleep at 0.38. */
@Composable
private fun ShutterButton(state: CaptureState, onIntent: (CaptureIntent) -> Unit) {
    val enabled = state.recording || state.canRecord
    val label = stringResource(if (state.recording) Res.string.capture_stop else Res.string.capture_record)
    Box(
        modifier = Modifier
            .size(Shutter)
            .alpha(if (enabled) 1f else ASLEEP_ALPHA)
            .clip(CircleShape)
            .border(ShutterRing, Color.White, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label) { onIntent(CaptureIntent.RecordClicked) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // the sign of recording, as the dot of every take
        if (state.recording) {
            Box(Modifier.size(ShutterStop).clip(RoundedCornerShape(ShutterStopCorner)).background(ViolinTheme.recording))
        } else {
            Box(Modifier.size(ShutterDot).clip(CircleShape).background(ViolinTheme.recording))
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp))
    }
}

/** «Собираем видео…» over everything, a card on the scrim of a sheet; it takes every touch: nothing under it answers meanwhile. */
@Composable
private fun Saving() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(ViolinTheme.sheetScrim)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .background(colors.surfaceContainer, RoundedCornerShape(SavingCorner))
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(22.dp), color = colors.primary, strokeWidth = 2.5.dp)
            Text(
                stringResource(Res.string.capture_saving),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
            )
        }
    }
}
