package com.example.violintuner.feature.session

import com.example.violintuner.feature.sound.components.BackingHeardSwitch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.violintuner.core.ui.icons.IconSizes
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.history.components.sessionTitle
import com.example.violintuner.feature.session.components.FullscreenVideo
import com.example.violintuner.feature.session.components.NotePlace
import com.example.violintuner.feature.session.components.NoteSheet
import com.example.violintuner.feature.session.components.PianoRoll
import com.example.violintuner.feature.session.components.PlayerBar
import com.example.violintuner.feature.session.components.ProblemNotes
import com.example.violintuner.feature.session.components.SessionStatCards
import com.example.violintuner.feature.session.components.StickyVideo
import com.example.violintuner.feature.session.components.VideoFrame
import com.example.violintuner.feature.session.components.VideoMissingRow
import com.example.violintuner.feature.session.components.VideoSurfaceCallbacks
import com.example.violintuner.feature.sound.SoundCaption
import com.example.violintuner.feature.sound.captionName
import java.time.ZoneId
import kotlin.math.roundToInt

private val TopBarHeight = 56.dp
private val BackTarget = 48.dp
private val ContentPadding = 16.dp
private val SectionSpacing = 16.dp
private val MaxContentWidth = 560.dp
private val ActionIcon = 22.dp
private val LandscapeVideoColumn = 456.dp
private const val LANDSCAPE_VIDEO_SHARE = 0.62f

/** The fallback of the handoff, to be decided on a phone: false — the picture leaves with the scroll instead of shrinking into a row. */
private const val VIDEO_STICKS = true

private val NoVideoSurface = VideoSurfaceCallbacks(onSurface = {}, onSurfaceGone = {})
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
    videoSurface: VideoSurfaceCallbacks = NoVideoSurface,
) {
    val colors = MaterialTheme.colorScheme
    val loaded = state as? SessionState.Loaded
    val title = when {
        loaded == null -> ""
        else -> sessionTitle(loaded.content.title, loaded.content.pieceTitle, loaded.content.startedAtEpochMs, zone)
    }
    // The video over the whole screen is a state of this screen, not another one (spec 3.19):
    // the sound does not stop, and the way back is to where the scroll was.
    val picture = loaded?.video?.takeIf { it.pictured }
    if (loaded != null && loaded.fullscreen && picture != null) {
        FullscreenVideo(
            video = picture,
            content = loaded.content,
            title = title,
            player = loaded.player,
            callbacks = videoSurface,
            onPlayPause = { onIntent(SessionIntent.PlayPauseClicked) },
            onSeek = { onIntent(SessionIntent.SeekRequested(it)) },
            onOriginal = { onIntent(SessionIntent.OriginalSelected(it)) },
            onExit = { onIntent(SessionIntent.FullscreenChanged(false)) },
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        val content = loaded?.content
        TopBar(
            title = title,
            // «дубль · 18:42 · 2:05»: a take says so under its name (handoff 22f1); a free recording has one line, as before
            subtitle = content?.takeIf { it.pieceId != null }?.let {
                stringResource(R.string.session_take_subtitle, Formats.timeOfDay(it.startedAtEpochMs, zone), Formats.duration(it.durationMs))
            },
            best = content?.takeIf { it.pieceId != null }?.best,
            onBest = { onIntent(SessionIntent.BestClicked) },
            onBack = { onIntent(SessionIntent.BackClicked) },
            // Sending is what one does most after listening: in sight at once, and well away from «Удалить» (handoff 18a).
            onShare = if (loaded?.player != null) ({ onIntent(SessionIntent.ShareClicked) }) else null,
        )
        when (state) {
            SessionState.Loading -> Unit
            SessionState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.session_not_found),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            is SessionState.Loaded -> LoadedContent(state, title, onIntent, videoSurface)
        }
    }
}

@Composable
private fun LoadedContent(state: SessionState.Loaded, title: String, onIntent: (SessionIntent) -> Unit, videoSurface: VideoSurfaceCallbacks) {
    val content = state.content
    val video = state.video
    val picture = video?.takeIf { it.pictured }
    val onTap = { onIntent(SessionIntent.PlayPauseClicked) }
    val onFullscreen = { onIntent(SessionIntent.FullscreenChanged(true)) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val landscape = picture != null && screenWidth > screenHeight
        val columnWidth = minOf(maxWidth, MaxContentWidth) - ContentPadding * 2
        val scroll = rememberScrollState()
        // What scrolls under the picture: everything but the picture itself and, in landscape, the player beside it.
        val rest: @Composable (playerHere: Boolean) -> Unit = { playerHere ->
            if (video?.lost == true) VideoMissingRow(stringResource(R.string.video_lost_title), stringResource(R.string.video_lost_text))
            if (video?.undecodable == true) VideoMissingRow(stringResource(R.string.video_undecodable_title), stringResource(R.string.video_undecodable_text))
            Summary(content)
            PianoRoll(
                content = content,
                selectedSegment = state.selectedSegment,
                onSegmentClick = { onIntent(SessionIntent.SegmentClicked(it)) },
                cursorMs = state.player?.positionMs,
                followCursor = state.player?.playing == true,
            )
            if (playerHere) PlayerAndSound(state, onIntent)
            // An empty place where a player would be reads as something broken; a quiet line says what it is.
            if (!content.hasAudio) SilentLine()
            SessionStatCards(content)
            ProblemNotes(content.problemNotes)
            Actions(onIntent, sizeLine = video?.let { sizeLineOf(it) })
        }
        when {
            // Handoff 20d8: the picture and the player stay put on the left, the rest scrolls on the right.
            landscape && picture != null -> Row(modifier = Modifier.fillMaxSize()) {
                val left = minOf(screenWidth / 2, LandscapeVideoColumn)
                Column(
                    modifier = Modifier
                        .width(left)
                        .padding(start = ContentPadding, end = ContentPadding / 2, top = 8.dp, bottom = ContentPadding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val room = left - ContentPadding * 3 / 2
                    val frame = VideoLayoutMath.fit(VideoLayoutMath.aspectOf(picture.width, picture.height), room.value, screenHeight.value * LANDSCAPE_VIDEO_SHARE)
                    Box(Modifier.fillMaxWidth().background(ViolinTheme.videoColors.field, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        VideoFrame(picture, state.player?.playing == true, videoSurface, onTap, Modifier.size(frame.width.dp, frame.height.dp), corner = 14.dp, onFullscreen = onFullscreen)
                    }
                    PlayerAndSound(state, onIntent)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scroll)
                        .padding(start = ContentPadding / 2, end = ContentPadding, top = 8.dp, bottom = ContentPadding),
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) { rest(false) }
            }
            picture != null && VIDEO_STICKS -> {
                val block = VideoLayoutMath.blockHeight(VideoLayoutMath.aspectOf(picture.width, picture.height), columnWidth.value, screenHeight.value)
                Column(
                    modifier = Modifier
                        .widthIn(max = MaxContentWidth)
                        .verticalScroll(scroll)
                        .padding(start = ContentPadding, end = ContentPadding, top = 8.dp, bottom = ContentPadding),
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) {
                    // the room the picture takes while nothing is scrolled; the picture itself lies over the scroll and shrinks with it
                    Spacer(Modifier.height(block.dp))
                    rest(true)
                }
                StickyVideo(
                    video = picture,
                    content = content,
                    player = state.player,
                    scrolledPx = { scroll.value },
                    availableWidth = columnWidth.value,
                    screenHeight = screenHeight.value,
                    callbacks = videoSurface,
                    onTap = onTap,
                    onFullscreen = onFullscreen,
                    modifier = Modifier
                        .widthIn(max = MaxContentWidth)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = ContentPadding, end = ContentPadding, top = 8.dp),
                )
            }
            else -> Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .verticalScroll(scroll)
                    .padding(start = ContentPadding, end = ContentPadding, top = 8.dp, bottom = ContentPadding),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                // the fallback of the handoff (20d4): the picture leaves with the scroll
                if (picture != null) {
                    StickyVideo(picture, content, state.player, { 0 }, columnWidth.value, screenHeight.value, videoSurface, onTap, onFullscreen)
                }
                rest(true)
            }
        }
    }
    state.selectedSegment?.let { index ->
        NoteSheet(
            segment = content.segments[index],
            onDismiss = { onIntent(SessionIntent.NoteSheetDismissed) },
            // a place can be played only where there is something to play
            place = state.player?.let { NotePlace(video = picture != null) { onIntent(SessionIntent.PlaySegmentClicked(index)) } },
        )
    }
    when (state.dialog) {
        SessionDialog.RENAME -> RenameDialog(currentTitle = content.title.orEmpty(), placeholder = title, onIntent = onIntent)
        SessionDialog.DELETE -> DeleteDialog(onIntent, videoBytes = video?.takeIf { !it.lost }?.sizeBytes)
        null -> Unit
    }
}

private const val BEST_STAR_MS = 150
private const val BEST_IDLE_SCALE = 0.9f

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    subtitle: String? = null,
    /** Null for a recording that is not a take: no star. */
    best: Boolean? = null,
    onBest: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp),
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = if (subtitle == null) 20.sp else 17.sp, fontWeight = FontWeight.SemiBold),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                )
            }
        }
        if (best != null) {
            // The most familiar "favourite" button there is, and its state needs no words (handoff 22f): outline — not the best, filled — the best.
            val label = stringResource(if (best) R.string.best_clear else R.string.best_set)
            val scale by animateFloatAsState(if (best) 1f else BEST_IDLE_SCALE, tween(BEST_STAR_MS), label = "bestStar")
            Box(
                modifier = Modifier
                    .size(BackTarget)
                    .clip(CircleShape)
                    .toggleable(value = best, role = Role.Switch, onValueChange = { onBest() })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    icon = if (best) AppIcons.Star else AppIcons.StarOutline,
                    contentDescription = null,
                    tint = if (best) colors.primary else colors.onSurface,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }
        if (onShare != null) {
            val share = stringResource(R.string.sound_share)
            Box(
                modifier = Modifier
                    .size(BackTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onShare)
                    .semantics { contentDescription = share },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Share, contentDescription = null, tint = colors.onSurface) }
        }
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

/** The player and the way to «Звук» under it: in the scroll when the phone is upright, beside the picture when it lies on its side. */
@Composable
private fun PlayerAndSound(state: SessionState.Loaded, onIntent: (SessionIntent) -> Unit) {
    state.player?.let { player ->
        PlayerBar(
            player = player,
            onPlayPause = { onIntent(SessionIntent.PlayPauseClicked) },
            onSeek = { onIntent(SessionIntent.SeekRequested(it)) },
            onOriginal = { onIntent(SessionIntent.OriginalSelected(it)) },
        )
        if (player.hasBacking) {
            BackingHeardSwitch(heard = player.backingHeard, onHeard = { onIntent(SessionIntent.BackingHeardSelected(it)) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        state.sound?.let { SoundEntry(it, processed = player.processed) { onIntent(SessionIntent.SoundClicked) } }
    }
}

/** «видео · 1080p · 214 МБ» — beside «Удалить», where a size is also a warning (handoff 20d1). */
@Composable
private fun sizeLineOf(video: VideoUi): String = when {
    video.lost -> stringResource(R.string.video_size_lost)
    else -> stringResource(R.string.video_size, stringResource(R.string.video_resolution, minOf(video.width, video.height)), Formats.fileSize(video.sizeBytes))
}

@Composable
private fun Actions(onIntent: (SessionIntent) -> Unit, sizeLine: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colors.surfaceContainerHigh)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sizeLine != null) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppIcon(AppIcons.Video, contentDescription = null, tint = colors.onSurfaceVariant, size = IconSizes.InText)
                    Text(sizeLine, color = colors.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES))
                }
            }
            Action(
                label = stringResource(R.string.session_action_rename),
                color = colors.onSurfaceVariant,
                icon = AppIcons.Pencil,
                onClick = { onIntent(SessionIntent.RenameClicked) },
            )
            Action(
                label = stringResource(R.string.session_action_delete),
                color = ViolinTheme.destructive,
                icon = AppIcons.Trash,
                onClick = { onIntent(SessionIntent.DeleteClicked) },
            )
        }
    }
}

@Composable
private fun Action(label: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(icon, contentDescription = null, tint = color, size = ActionIcon)
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
private fun DeleteDialog(onIntent: (SessionIntent) -> Unit, videoBytes: Long? = null) {
    com.example.violintuner.core.ui.components.DeleteDialog(
        title = stringResource(R.string.session_delete_title),
        // a video is the heaviest thing that goes, and the one that cannot be played again (spec 3.19)
        text = videoBytes?.let { stringResource(R.string.video_delete_text, Formats.fileSize(it)) } ?: stringResource(R.string.session_delete_text),
        onConfirm = { onIntent(SessionIntent.DeleteConfirmed) },
        onDismiss = { onIntent(SessionIntent.DialogDismissed) },
    )
}

/** The way to the «Звук» screen, with what the recording sounds like in its second line; its icon is lit while the processing does something. */
@Composable
private fun SoundEntry(row: SoundRow, processed: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = captionName(row.caption)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(AppIcons.Sound, contentDescription = null, tint = if (processed) colors.primary else colors.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.sound_session_row), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(
                text = when {
                    !row.own -> stringResource(R.string.sound_caption_everyone, name)
                    !processed -> stringResource(R.string.sound_row_off)
                    row.caption == SoundCaption.Custom -> name
                    else -> stringResource(R.string.sound_caption_own, name)
                },
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            )
        }
        AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun SilentLine() {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(AppIcons.VolumeOff, contentDescription = null, tint = colors.onSurfaceVariant, size = 18.dp)
        Text(stringResource(R.string.sound_session_silent), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
    }
}
