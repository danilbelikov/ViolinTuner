package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.recording.video.VideoImport
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppMenu
import com.violinjourney.app.core.ui.components.AppMenuItem
import com.violinjourney.app.core.ui.components.AppSwitchMark
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.PermissionLine
import com.violinjourney.app.core.ui.components.ShiftedInteractionSource
import com.violinjourney.app.core.ui.components.appButtonOneLineSize
import com.violinjourney.app.core.ui.components.dimmedWhen
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.feature.repertoire.components.ReasonPlate
import com.violinjourney.app.feature.repertoire.components.RecordDot
import com.violinjourney.app.feature.repertoire.components.RecordingBar
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_chip
import com.violinjourney.app.shared.resources.backing_chip_off
import com.violinjourney.app.shared.resources.backing_chip_on
import com.violinjourney.app.shared.resources.backing_headphones
import com.violinjourney.app.shared.resources.backing_headphones_none
import com.violinjourney.app.shared.resources.backing_headphones_wired
import com.violinjourney.app.shared.resources.backing_needs_headphones
import com.violinjourney.app.shared.resources.backing_or_turn_off
import com.violinjourney.app.shared.resources.backing_preparing
import com.violinjourney.app.shared.resources.backing_unprepared
import com.violinjourney.app.shared.resources.take_grant_permission
import com.violinjourney.app.shared.resources.take_no_permission
import com.violinjourney.app.shared.resources.take_record
import com.violinjourney.app.shared.resources.video_pick
import com.violinjourney.app.shared.resources.video_pick_hint
import com.violinjourney.app.shared.resources.video_shoot
import com.violinjourney.app.shared.resources.video_shoot_backing
import com.violinjourney.app.shared.resources.video_shoot_backing_hint
import com.violinjourney.app.shared.resources.video_shoot_hint
import com.violinjourney.app.shared.resources.video_shoot_own_hint
import com.violinjourney.app.shared.resources.video_take
import com.violinjourney.app.shared.resources.video_take_busy
import org.jetbrains.compose.resources.stringResource

// The bottom zone of recording (spec 3.36.4, 5.29 R4, «Нижняя зона записи»; repertoire.html 3).
private val ToggleTouch = 48.dp
private val ToggleHeight = 40.dp
private val ToggleEdge = 4.dp
private val ToggleGap = 8.dp
private val ToggleEnd = 12.dp
private val HeadphonesIcon = 16.dp
private val VideoCircle = 56.dp
private val VideoCircleCompact = 48.dp
private val VideoBorder = 1.5.dp
private val VideoIcon = 24.dp
private val VideoMenuWidth = 248.dp
private val SpinnerStroke = 2.dp
private val DotRegular = 14.dp
private val DotCompact = 12.dp

/**
 * The least size of the words of «Записать дубль» kept on one line: 15 sp, the words of the key of 48 (5.29 R1). Below it they go
 * on two lines at a space — the French «Enregistrer une prise» beside the circle in the column of 300.
 */
private const val RECORD_KEY_MIN_SP = 15f

/** Which of the app's own camera's items «Видео-дубль» offers (spec 3.32). */
enum class OwnCamera { UNDER_BACKING, PLAIN }

/**
 * The bottom zone of the piece screen (spec 3.36.4): it records. While a take runs — the [RecordingBar] with its levels and the
 * backing's progress; otherwise, top down, the one reason «Записать дубль» sleeps ([RecordReason]: the line «нет разрешения» or a
 * plate), the capsule «С минусовкой» with the headphones while the piece has a backing, and the row of the round «Видео-дубль» and
 * «Записать дубль» with the red dot. [backing] null — it is still being read: only the room of the row, so nothing blinks up a frame
 * later. The whole zone steps aside at 0.38 while takes are picked ([selecting], spec 3.18). [compact] of the zone — a window no
 * higher than 360 dp: the buttons of 48, the dot of 12; the capsule and the plates stay (an exception of R4 to the zone of 64).
 */
@Composable
fun DockScope.RecordDock(
    /** Read inside: the bar follows twenty readings a second, the rest of the zone only the start and the end of a take. */
    take: State<TakeState>,
    backing: BackingUi?,
    videoImport: VideoImport,
    selecting: Boolean,
    onIntent: (PieceIntent) -> Unit,
    onPickVideo: () -> Unit,
) {
    if (backing == null) {
        Spacer(Modifier.height(buttonHeight))
        return
    }
    // the scope of the zone is a marked scope: its members are not seen from the rows inside it
    val compact = compact
    val recording by remember(take) { derivedStateOf { take.value.recording } }
    val micPermission by remember(take) { derivedStateOf { take.value.micPermission } }
    Column(Modifier.fillMaxWidth().dimmedWhen(selecting), verticalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
        if (recording) {
            TakeBar(take, compact, onIntent)
            return@Column
        }
        val reason = RecordReason.of(micPermission, backing)
        when (reason) {
            null -> Unit
            RecordReason.NO_MIC -> PermissionLine(
                reason = stringResource(Res.string.take_no_permission),
                onGrant = { onIntent(PieceIntent.GrantMicClicked) },
                modifier = Modifier.fillMaxWidth(),
                grant = stringResource(Res.string.take_grant_permission),
            )
            RecordReason.NO_HEADPHONES -> ReasonPlate(
                AppIcons.Headphones,
                Sentences.join(stringResource(Res.string.backing_needs_headphones), stringResource(Res.string.backing_or_turn_off)),
                Modifier.fillMaxWidth(),
            )
            RecordReason.PREPARING -> ReasonPlate(AppIcons.Backing, stringResource(Res.string.backing_preparing), Modifier.fillMaxWidth())
            RecordReason.UNPREPARED -> ReasonPlate(
                AppIcons.Alert,
                Sentences.join(stringResource(Res.string.backing_unprepared), stringResource(Res.string.backing_or_turn_off)),
                Modifier.fillMaxWidth(),
            )
        }
        if (backing.present) BackingToggleRow(backing, onIntent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
            VideoTakeCircle(
                // not asleep of its own while takes are picked: the whole zone is at 0.38 then and takes no touch (5.29 R4), and the
                // circle dimmed inside it would be dimmed twice
                enabled = videoImport == VideoImport.Idle,
                // a short analysis shows no sheet: the circle says what is going on instead
                busy = videoImport is VideoImport.Working && !videoImport.visible,
                size = if (compact) VideoCircleCompact else VideoCircle,
                // a piece with a backing is filmed by the app's own camera only: the system one knows nothing of the backing
                ownCamera = backing.takeIf { it.present }?.let { if (it.enabled) OwnCamera.UNDER_BACKING else OwnCamera.PLAIN },
                onShoot = { onIntent(if (backing.present) PieceIntent.OwnCameraClicked else PieceIntent.VideoShootClicked) },
                onPick = onPickVideo,
            )
            val dot = if (compact) DotCompact else DotRegular
            BoxWithConstraints(Modifier.weight(1f)) {
                val key = stringResource(Res.string.take_record)
                // one line: lying, beside the circle in the column of 300, «Записать дубль» of 17 sp would go on two; its words step
                // down, never below the size of the key of 48 — the dot is counted in whether it is there or not, so the words do not
                // change their size when the microphone is allowed
                val size = appButtonOneLineSize(key, maxWidth, AppButtonStyle.Main, compact, before = dot + IconSizes.ButtonGap, minSp = RECORD_KEY_MIN_SP)
                AppButton(
                    text = key,
                    onClick = { onIntent(PieceIntent.RecordClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = reason == null,
                    compact = compact,
                    // no dot without the microphone: the key sleeps under its line, it is no sign of recording now
                    leading = when (reason) {
                        RecordReason.NO_MIC -> null
                        RecordReason.PREPARING -> ({ Spinner(dot) })
                        else -> ({ RecordDot(dot) })
                    },
                    fontSize = size,
                )
            }
        }
    }
}

/** The bar of the running take, read where it is drawn: twenty readings a second recompose only it. */
@Composable
private fun TakeBar(take: State<TakeState>, compact: Boolean, onIntent: (PieceIntent) -> Unit) {
    val now = take.value
    RecordingBar(
        elapsedSeconds = now.elapsedSeconds,
        noisy = now.problem == TakeProblem.TOO_NOISY,
        onStop = { onIntent(PieceIntent.RecordClicked) },
        levels = now.levels,
        backingPlayedMs = now.backingPlayedMs,
        backingDurationMs = now.backingDurationMs,
        compact = compact,
    )
}

/**
 * «С минусовкой» (spec 3.32, 3.36.4): a capsule of 40 — pressed over 48 — with the switch of the app in it and the word; the choice
 * is kept by the piece. Beside it the headphones the sound goes to — their name, «Наушники» when the system gave none, «наушников
 * нет» — standing with the switch off too: what is plugged in is seen before it is turned on.
 */
@Composable
private fun BackingToggleRow(backing: BackingUi, onIntent: (PieceIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val on = backing.enabled
    val state = stringResource(if (on) Res.string.backing_chip_on else Res.string.backing_chip_off)
    val interaction = remember { MutableInteractionSource() }
    // the press comes over the 48 of the target, its ripple is drawn in the capsule of 40
    val capsulePresses = remember(interaction) { ShiftedInteractionSource(interaction) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .heightIn(min = ToggleTouch)
                .toggleable(value = on, interactionSource = interaction, indication = null, role = Role.Switch) { onIntent(PieceIntent.BackingChipToggled) }
                .semantics { stateDescription = state },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .onPlaced { capsulePresses.shift = it.positionInParent() }
                    .height(ToggleHeight)
                    .clip(RoundedCornerShape(ToggleHeight / 2))
                    .background(colors.surfaceContainer)
                    .indication(capsulePresses, ripple())
                    .padding(start = ToggleEdge, end = ToggleEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToggleGap),
            ) {
                AppSwitchMark(checked = on)
                Text(
                    stringResource(Res.string.backing_chip),
                    color = colors.onSurface,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        // the rest of the row, the name at its right end: a long one gives way with an ellipsis, the capsule never
        Headphones(backing, Modifier.weight(1f))
    }
}

@Composable
private fun Headphones(backing: BackingUi, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val route = backing.route
    val name = route.deviceName
    val words = when {
        !route.output.isHeadphones -> stringResource(Res.string.backing_headphones_none)
        name != null -> name
        else -> stringResource(Res.string.backing_headphones)
    }
    // «Наушники · Pixel Buds» for TalkBack; «наушников нет» and «Наушники» say it all by themselves
    val said = if (route.output.isHeadphones && name != null) stringResource(Res.string.backing_headphones_wired, name) else words
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
    ) {
        if (route.output.isHeadphones) AppIcon(AppIcons.Headphones, contentDescription = null, tint = colors.onSurfaceVariant, size = HeadphonesIcon)
        Text(
            words,
            modifier = Modifier.weight(1f, fill = false),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * «Видео-дубль» (spec 3.19, 3.36.4): a round outlined button left of «Записать дубль» — second by weight — with the menu of its two
 * ways and their captions; a piece with a backing is filmed by the app's own camera ([ownCamera]). While a short analysis runs
 * without its sheet ([busy]) a spinner turns in the circle. Asleep ([enabled] false) while another video is on its way in; a take is
 * not recorded meanwhile anyway — the zone shows its bar. While takes are picked the zone around it is dimmed as a whole and takes
 * no touch.
 */
@Composable
fun VideoTakeCircle(
    enabled: Boolean,
    busy: Boolean,
    size: Dp,
    ownCamera: OwnCamera?,
    onShoot: () -> Unit,
    onPick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val said = stringResource(if (busy) Res.string.video_take_busy else Res.string.video_take)
    Box(Modifier.dimmedWhen(!enabled && !busy)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(VideoBorder, colors.outlineVariant, CircleShape)
                .clickable(enabled = enabled && !busy, role = Role.Button) { menuOpen = true }
                .semantics { contentDescription = said },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) Spinner(VideoIcon, colors.onSurface) else AppIcon(AppIcons.Video, contentDescription = null, tint = colors.onSurface, size = VideoIcon)
        }
        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.width(VideoMenuWidth)) {
            when (ownCamera) {
                null -> AppMenuItem(stringResource(Res.string.video_shoot), icon = AppIcons.Video, caption = stringResource(Res.string.video_shoot_hint), onClick = { menuOpen = false; onShoot() })
                OwnCamera.UNDER_BACKING -> AppMenuItem(stringResource(Res.string.video_shoot_backing), icon = AppIcons.Backing, caption = stringResource(Res.string.video_shoot_backing_hint), onClick = { menuOpen = false; onShoot() })
                OwnCamera.PLAIN -> AppMenuItem(stringResource(Res.string.video_shoot), icon = AppIcons.Video, caption = stringResource(Res.string.video_shoot_own_hint), onClick = { menuOpen = false; onShoot() })
            }
            AppMenuItem(stringResource(Res.string.video_pick), icon = AppIcons.VideoGallery, caption = stringResource(Res.string.video_pick_hint), onClick = { menuOpen = false; onPick() })
        }
    }
}

/** The spinner of what is on its way — the backing prepared, a video analysed; in the colour of the words around it. */
@Composable
private fun Spinner(size: Dp, color: Color = LocalContentColor.current) {
    CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = SpinnerStroke)
}

