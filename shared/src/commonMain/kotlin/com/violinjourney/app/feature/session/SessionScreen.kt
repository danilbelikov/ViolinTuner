package com.violinjourney.app.feature.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.AppMenu
import com.violinjourney.app.core.ui.components.AppMenuItem
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.FieldDialog
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.MenuDanger
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.components.RecordDeleteDialog
import com.violinjourney.app.feature.history.components.sessionTitle
import com.violinjourney.app.feature.session.components.DashedPlate
import com.violinjourney.app.feature.session.components.DriftCard
import com.violinjourney.app.feature.session.components.FullscreenVideo
import com.violinjourney.app.feature.session.components.NotePlace
import com.violinjourney.app.feature.session.components.NoteSheet
import com.violinjourney.app.feature.session.components.NotesCard
import com.violinjourney.app.feature.session.components.RecordPlayer
import com.violinjourney.app.feature.session.components.StickyVideo
import com.violinjourney.app.feature.session.components.StringsCard
import com.violinjourney.app.feature.session.components.SummaryCard
import com.violinjourney.app.feature.session.components.VideoCorner
import com.violinjourney.app.feature.session.components.VideoFrame
import com.violinjourney.app.feature.session.components.VideoSurfaceCallbacks
import com.violinjourney.app.feature.sound.components.PlayerPanelShape
import com.violinjourney.app.feature.sound.components.currentPlayerDockMetrics
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_take_mark
import com.violinjourney.app.shared.resources.best_clear
import com.violinjourney.app.shared.resources.best_set
import com.violinjourney.app.shared.resources.card_menu
import com.violinjourney.app.shared.resources.card_menu_delete
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.record_meta
import com.violinjourney.app.shared.resources.record_tile_only_video
import com.violinjourney.app.shared.resources.session_action_rename
import com.violinjourney.app.shared.resources.session_back
import com.violinjourney.app.shared.resources.session_menu_piece
import com.violinjourney.app.shared.resources.session_not_found
import com.violinjourney.app.shared.resources.session_rename_confirm
import com.violinjourney.app.shared.resources.session_rename_hint
import com.violinjourney.app.shared.resources.session_rename_label
import com.violinjourney.app.shared.resources.session_rename_title
import com.violinjourney.app.shared.resources.session_take_subtitle
import com.violinjourney.app.shared.resources.session_video_subtitle
import com.violinjourney.app.shared.resources.sound_session_silent
import com.violinjourney.app.shared.resources.sound_share
import com.violinjourney.app.shared.resources.video_lost_text
import com.violinjourney.app.shared.resources.video_lost_title
import com.violinjourney.app.shared.resources.video_resolution
import com.violinjourney.app.shared.resources.video_size
import com.violinjourney.app.shared.resources.video_size_lost
import com.violinjourney.app.shared.resources.video_undecodable_text
import com.violinjourney.app.shared.resources.video_undecodable_title
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

// The recording (spec 3.36.5, 5.29 R5; records.html 2, landscape.html 3).
private val TopBarHeight = 56.dp
private val TopBarHeightLandscape = 48.dp
private val TopBarButton = 48.dp
private val TopBarSide = 4.dp
private val SubtitleGap = 4.dp
private val ScreenPadding = 16.dp
private val CardGap = 12.dp
private val ContentTop = 8.dp
private val ContentBottom = 16.dp
private val MaxContentWidth = 560.dp

/** Lying: the left column — the picture or the summary over the player — is half the window, and never wider than this. */
private val LandscapeLeftColumn = 456.dp

/** Lying, between the columns: 8 at the end of the left one and 8 at the start of the right one. */
private val ColumnGap = 8.dp

/** Lying, the sides of the rows of the player: those of the column over it — the summary, the picture — not the panel's 16 / 16. */
private val LeftColumnSides = PaddingValues(start = ScreenPadding, end = ColumnGap)

/** Lying, the picture of a video stands this far over the panel of the player. */
private val PictureOverPlayer = 12.dp

/** The fallback of the handoff, to be decided on a phone: false — the picture leaves with the scroll instead of shrinking into a row. */
private const val VIDEO_STICKS = true

private val NoVideoSurface = VideoSurfaceCallbacks(onSurface = {}, onSurfaceGone = {})
private const val TABULAR_FIGURES = "tnum"

/**
 * The recording (spec 3.10, 3.36.5): «где уходит и как это звучит». The bar — its name, what it is, and what is done with it whole: the
 * star of a take, «Поделиться» while its sound plays, «⋯» with «Переименовать», «К произведению» and «Удалить…». Upright, from the top:
 * the video, the summary, the notes, «Что уходит», «По струнам» — the middle scrolls, and the player is pinned at the bottom, a panel of
 * its own that covers nothing; lying (any recording whose sound plays) two columns — the picture or the summary over the player on the
 * left, the rest on the right; without a sound to play, one column with a dashed line first. Stateless.
 */
@Composable
fun SessionScreen(
    state: SessionState,
    onIntent: (SessionIntent) -> Unit,
    modifier: Modifier = Modifier,
    // asked once: a new zone on every recomposition is a new object each time, and on iOS a read of its file
    zone: TimeZone = remember { TimeZone.currentSystemDefault() },
    videoSurface: VideoSurfaceCallbacks = NoVideoSurface,
    /**
     * Where the player is, exactly: read where it is drawn — the cursor, the wave, the note strip — so that the screen, whose player
     * keeps the position to the whole second, recomposes once a second while it plays.
     */
    position: () -> Long = { (state as? SessionState.Loaded)?.player?.positionMs ?: 0L },
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
            position = position,
            callbacks = videoSurface,
            onPlayPause = { onIntent(SessionIntent.PlayPauseClicked) },
            onSeek = { onIntent(SessionIntent.SeekRequested(it)) },
            onOriginal = { onIntent(SessionIntent.OriginalSelected(it)) },
            onExit = { onIntent(SessionIntent.FullscreenChanged(false)) },
        )
        return
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            TopBar(title, loaded, zone, if (landscape) TopBarHeightLandscape else TopBarHeight, onIntent)
            when (state) {
                SessionState.Loading -> Unit
                SessionState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.session_not_found),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is SessionState.Loaded -> LoadedContent(state, landscape, onIntent, videoSurface, position)
            }
        }
    }
    if (loaded != null) {
        NoteSheet(
            index = loaded.selectedSegment,
            segments = loaded.content.segments,
            nearCents = loaded.content.nearCents,
            // a place can be played only where there is something to play — or will be, once the backing is made
            place = when {
                loaded.player != null -> NotePlace(video = picture != null, ready = true) { onIntent(SessionIntent.PlaySegmentClicked(it)) }
                loaded.preparingBacking -> NotePlace(video = picture != null, ready = false) { }
                else -> null
            },
            onHide = { onIntent(SessionIntent.NoteSheetDismissed) },
        )
        when (loaded.dialog) {
            SessionDialog.RENAME -> RenameDialog(currentTitle = loaded.content.title.orEmpty(), placeholder = title, onIntent = onIntent)
            SessionDialog.DELETE -> RecordDeleteDialog(
                videoBytes = loaded.video?.takeIf { !it.lost }?.sizeBytes,
                onConfirm = { onIntent(SessionIntent.DeleteConfirmed) },
                onDismiss = { onIntent(SessionIntent.DialogDismissed) },
            )
            null -> Unit
        }
    }
}

@Composable
private fun LoadedContent(
    state: SessionState.Loaded,
    landscape: Boolean,
    onIntent: (SessionIntent) -> Unit,
    videoSurface: VideoSurfaceCallbacks,
    position: () -> Long,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val screen = Screen(state, onIntent, videoSurface, position, width = maxWidth, height = maxHeight)
        when {
            // lying, any recording whose sound plays: two columns (spec 3.36.5 — before, a video only)
            landscape && state.playable -> TwoColumns(screen)
            state.playable -> AppDock(
                dock = { RecordPlayer(state, position, onIntent) },
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
                fade = 0.dp,
                metrics = currentPlayerDockMetrics(),
                ground = MaterialTheme.colorScheme.surfaceContainer,
                shape = PlayerPanelShape,
            ) {
                // the middle ends at the top of the panel: nothing lies under the player, the roll is never under it
                Box(Modifier.fillMaxSize().padding(bottom = LocalDockInset.current)) { Scrolling(screen) }
            }
            // nothing to play: one column, the dashed line first
            else -> Box(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) { Scrolling(screen) }
        }
    }
}

/** What the layouts are made of — one bag, not six parameters thrice. [width] and [height] — of the room under the bar. */
private class Screen(
    val state: SessionState.Loaded,
    val onIntent: (SessionIntent) -> Unit,
    val videoSurface: VideoSurfaceCallbacks,
    val position: () -> Long,
    val width: Dp,
    val height: Dp,
) {
    val picture: VideoUi? get() = state.video?.takeIf { it.pictured }
    val onTap: () -> Unit = { onIntent(SessionIntent.PlayPauseClicked) }
    val onFullscreen: () -> Unit = { onIntent(SessionIntent.FullscreenChanged(true)) }
}

/**
 * One column that scrolls: the dashed line of what is not there, the video that shrinks into a row under the bar as the rest scrolls
 * (spec 3.19, 5.13 — not higher than 40 % of the room), the summary and the rest.
 */
@Composable
private fun Scrolling(screen: Screen) {
    val state = screen.state
    val picture = screen.picture
    val scroll = rememberScrollState()
    val columnWidth = minOf(screen.width, MaxContentWidth) - ScreenPadding * 2
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = ScreenPadding, end = ScreenPadding, top = ContentTop, bottom = ContentBottom),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            if (picture != null) {
                if (VIDEO_STICKS) {
                    // the room the picture takes while nothing is scrolled; the picture itself lies over the scroll and shrinks with it
                    val block = VideoLayoutMath.blockHeight(VideoLayoutMath.aspectOf(picture.width, picture.height), columnWidth.value, screen.height.value)
                    Spacer(Modifier.height(block.dp))
                } else {
                    // the fallback of the handoff (20d4): the picture leaves with the scroll
                    StickyVideo(
                        picture, state.content, state.player, { 0 }, columnWidth.value, screen.height.value, screen.videoSurface,
                        screen.onTap, screen.onFullscreen, waiting = state.preparingBacking,
                    )
                }
            }
            Plates(state)
            Cards(screen, summary = true)
        }
        if (picture != null && VIDEO_STICKS) {
            StickyVideo(
                video = picture,
                content = state.content,
                player = state.player,
                scrolledPx = { scroll.value },
                availableWidth = columnWidth.value,
                screenHeight = screen.height.value,
                callbacks = screen.videoSurface,
                onTap = screen.onTap,
                onFullscreen = screen.onFullscreen,
                waiting = state.preparingBacking,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = ScreenPadding, end = ScreenPadding, top = ContentTop),
            )
        }
    }
}

/**
 * Lying (spec 3.36.5, landscape.html 3): the bar of 48 over two columns. On the left — min(half the window, 456) — the picture as high as
 * what is left over the player, on its dark field, or the summary short, scrolling over the player where it does not fit (a large font,
 * a take under a backing on 640 × 360) with a fade of 16; at its bottom the player, compact in a window lower than 700 — its panel the
 * whole column, its rows flush with the summary and the picture over them (16 at the edge, 8 at the meeting of the columns). On the
 * right, scrolling to the bottom of the screen: the summary first under a picture, then the notes, «Что уходит» and «По струнам».
 */
@Composable
private fun TwoColumns(screen: Screen) {
    val state = screen.state
    val picture = screen.picture
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxSize()) {
        AppDock(
            dock = { RecordPlayer(state, screen.position, screen.onIntent, sides = LeftColumnSides) },
            modifier = Modifier.width(minOf(screen.width / 2, LandscapeLeftColumn)).fillMaxHeight(),
            fade = if (picture != null) 0.dp else DockDefaults.FadeLeftColumn,
            // the sides are the column's, as those of the summary and the picture: the rows are given them, the ground is the column
            padSides = false,
            metrics = currentPlayerDockMetrics(),
            ground = colors.surfaceContainer,
            shape = PlayerPanelShape,
        ) {
            if (picture != null) {
                LeftPicture(screen, picture)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = LocalDockInset.current)
                        .verticalScroll(rememberScrollState())
                        .padding(start = ScreenPadding, end = ColumnGap, top = ContentTop, bottom = ContentBottom),
                    verticalArrangement = Arrangement.spacedBy(CardGap),
                ) {
                    Plates(state)
                    SummaryCard(state.content, compact = true)
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = ColumnGap, end = ScreenPadding, top = ContentTop, bottom = ContentBottom),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            Cards(screen, summary = picture != null)
        }
    }
}

/** The picture lying: as high as what is left over the player, on the dark field of a video, «на весь экран» in its corner, no shrinking. */
@Composable
private fun LeftPicture(screen: Screen, picture: VideoUi) {
    val state = screen.state
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = ScreenPadding, end = ColumnGap, top = ContentTop, bottom = LocalDockInset.current + PictureOverPlayer)
            .background(ViolinTheme.videoColors.field, RoundedCornerShape(VideoCorner)),
        contentAlignment = Alignment.Center,
    ) {
        val frame = VideoLayoutMath.fit(VideoLayoutMath.aspectOf(picture.width, picture.height), maxWidth.value, maxHeight.value)
        VideoFrame(
            video = picture,
            playing = state.player?.playing == true,
            callbacks = screen.videoSurface,
            onTap = screen.onTap,
            modifier = Modifier.size(frame.width.dp, frame.height.dp),
            onFullscreen = screen.onFullscreen,
            waiting = state.preparingBacking,
        )
    }
}

/**
 * What is not there, first (spec 3.36.5): a video that is gone — the plate of it, its sound went with it; a picture this phone cannot
 * show — the plate of that, the sound plays; no sound to play — the quiet line of a recording without sound.
 */
@Composable
private fun Plates(state: SessionState.Loaded) {
    val video = state.video
    when {
        video?.lost == true -> DashedPlate(AppIcons.VideoOff, stringResource(Res.string.video_lost_text), title = stringResource(Res.string.video_lost_title))
        video?.undecodable == true -> DashedPlate(AppIcons.VideoOff, stringResource(Res.string.video_undecodable_text), title = stringResource(Res.string.video_undecodable_title))
    }
    if ((!state.content.hasAudio && video?.lost != true) || state.soundFailed) {
        DashedPlate(AppIcons.Info, stringResource(Res.string.sound_session_silent))
    }
}

/** The summary ([summary]), the notes, «Что уходит» and «По струнам», 12 apart. */
@Composable
private fun ColumnScope.Cards(screen: Screen, summary: Boolean) {
    val state = screen.state
    val onIntent = screen.onIntent
    if (summary) SummaryCard(state.content)
    NotesCard(
        content = state.content,
        selectedSegment = state.selectedSegment,
        onSegment = { onIntent(SessionIntent.SegmentClicked(it)) },
        cursor = screen.position.takeIf { state.player != null },
        follow = state.player?.playing == true,
    )
    DriftCard(state.content.problemNotes, onNote = { onIntent(SessionIntent.ProblemNoteClicked(it)) })
    StringsCard(state.content)
}

private const val BEST_STAR_MS = 150
private const val BEST_IDLE_SCALE = 0.9f

/**
 * The bar (spec 3.36.5, 5.29 R5): «назад», the name in one line and under it what the recording is — «дубль · 18:42 · 2:05», «видео ·
 * 18:42 · 2:05», «09:15 · 0:36» — after the sign of the backing for a take made under one; at the right what is done with it whole:
 * the star of a take, «Поделиться» while its sound plays, «⋯». While it is read, and when it is not found, the bar has no actions.
 */
@Composable
private fun TopBar(title: String, loaded: SessionState.Loaded?, zone: TimeZone, height: Dp, onIntent: (SessionIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = TopBarSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(AppIcons.Back, stringResource(Res.string.session_back)) { onIntent(SessionIntent.BackClicked) }
        Column(modifier = Modifier.weight(1f).padding(start = TopBarSide)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
            )
            if (loaded != null) Subtitle(loaded.content, zone)
        }
        if (loaded == null) return@Row
        val content = loaded.content
        // a take only (spec 3.21): the most familiar «favourite» button there is — outline, not the best; filled, the best
        if (content.pieceId != null) BestStar(content.best) { onIntent(SessionIntent.BestClicked) }
        // sending is what one does most after listening — in sight at once, away from «Удалить…» in «⋯» (spec 3.36.5)
        if (loaded.player != null) BarButton(AppIcons.Share, stringResource(Res.string.sound_share)) { onIntent(SessionIntent.ShareClicked) }
        MoreMenu(loaded, onIntent)
    }
}

/** What the recording is, under its name — and, for TalkBack, «под минусовку» where the sign of the backing stands before it. */
@Composable
private fun Subtitle(content: SessionContent, zone: TimeZone) {
    val colors = MaterialTheme.colorScheme
    val time = Formats.timeOfDay(content.startedAtEpochMs, zone)
    val length = Formats.duration(content.durationMs)
    val words = when {
        content.hasVideo -> stringResource(Res.string.session_video_subtitle, time, length)
        content.pieceId != null -> stringResource(Res.string.session_take_subtitle, time, length)
        // a free recording: no «с Live» — where it was made is not kept (spec 3.15)
        else -> stringResource(Res.string.record_meta, time, length)
    }
    val backing = if (content.underBacking) stringResource(Res.string.backing_take_mark) else null
    val spoken = listOfNotNull(words, backing).joinToString(", ")
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SubtitleGap),
    ) {
        if (backing != null) AppIcon(AppIcons.Backing, contentDescription = null, tint = colors.onSurfaceVariant, size = IconSizes.InText)
        Text(
            text = words,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(TopBarButton)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun BestStar(best: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (best) Res.string.best_clear else Res.string.best_set)
    val scale by animateFloatAsState(if (best) 1f else BEST_IDLE_SCALE, tween(BEST_STAR_MS), label = "bestStar")
    Box(
        modifier = Modifier
            .size(TopBarButton)
            .clip(CircleShape)
            .toggleable(value = best, role = Role.Switch, onValueChange = { onToggle() })
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

/**
 * «⋯» of the bar (spec 3.36.5): «Переименовать»; «К произведению» — a take whose piece is there; after a line «Удалить…», of a video
 * with what goes with it on a second line — «видео · 1080p · 214 МБ», from 100 MB bold in the colour of danger; «видео не найдено».
 */
@Composable
private fun MoreMenu(loaded: SessionState.Loaded, onIntent: (SessionIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val close = { open = false }
    val video = loaded.video
    Box {
        BarButton(AppIcons.More, stringResource(Res.string.card_menu)) { open = true }
        AppMenu(
            expanded = open,
            onDismissRequest = close,
            danger = MenuDanger(
                text = stringResource(Res.string.card_menu_delete),
                onClick = {
                    close()
                    onIntent(SessionIntent.DeleteClicked)
                },
                caption = video?.let { sizeLineOf(it) },
                captionStrong = video?.large == true,
            ),
        ) {
            AppMenuItem(
                text = stringResource(Res.string.session_action_rename),
                icon = AppIcons.Pencil,
                onClick = {
                    close()
                    onIntent(SessionIntent.RenameClicked)
                },
            )
            if (loaded.content.pieceId != null) {
                AppMenuItem(
                    text = stringResource(Res.string.session_menu_piece),
                    icon = AppIcons.TabRepertoire.normal,
                    onClick = {
                        close()
                        onIntent(SessionIntent.OpenPieceClicked)
                    },
                )
            }
        }
    }
}

/** «видео · 1080p · 214 МБ» (spec 3.19); the picture not looked into yet — «видео · 214 МБ»; the file gone — «видео не найдено». */
@Composable
private fun sizeLineOf(video: VideoUi): String {
    val side = minOf(video.width, video.height)
    return when {
        video.lost -> stringResource(Res.string.video_size_lost)
        side > 0 -> stringResource(Res.string.video_size, stringResource(Res.string.video_resolution, side), Formats.fileSize(video.sizeBytes))
        else -> stringResource(Res.string.record_tile_only_video) + stringResource(Res.string.dot_separator) + Formats.fileSize(video.sizeBytes)
    }
}

/**
 * «Переименовать запись» (spec 3.10, 3.36.1): the title says the action, the field says «Название»; «Сохранить» never goes dim —
 * an empty field gives the recording back its name by date, and the line under the field says so.
 */
@Composable
private fun RenameDialog(currentTitle: String, placeholder: String, onIntent: (SessionIntent) -> Unit) {
    FieldDialog(
        title = stringResource(Res.string.session_rename_title),
        label = stringResource(Res.string.session_rename_label),
        initial = currentTitle,
        confirm = stringResource(Res.string.session_rename_confirm),
        onConfirm = { onIntent(SessionIntent.RenameConfirmed(it)) },
        onDismiss = { onIntent(SessionIntent.DialogDismissed) },
        hint = stringResource(Res.string.session_rename_hint),
        placeholder = placeholder,
    )
}
