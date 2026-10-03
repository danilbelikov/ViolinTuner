package com.violinjourney.app.feature.sound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppDialog
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.components.FieldDialog
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.components.SegmentedSwitch
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.feature.history.DayGroup
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.feature.history.components.DayHeader
import com.violinjourney.app.feature.history.components.RecordPlace
import com.violinjourney.app.feature.history.components.RecordTile
import com.violinjourney.app.feature.history.components.SessionCard
import com.violinjourney.app.feature.history.components.sessionTitle
import com.violinjourney.app.feature.sound.components.BackingCard
import com.violinjourney.app.feature.sound.components.PlayerPanelShape
import com.violinjourney.app.feature.sound.components.SoundBlocks
import com.violinjourney.app.feature.sound.components.SoundPlayer
import com.violinjourney.app.feature.sound.components.currentPlayerDockMetrics
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.piece_delete_confirm
import com.violinjourney.app.shared.resources.record_tile_video
import com.violinjourney.app.shared.resources.session_back
import com.violinjourney.app.shared.resources.sound_affected_many
import com.violinjourney.app.shared.resources.sound_affected_one
import com.violinjourney.app.shared.resources.sound_caption_custom
import com.violinjourney.app.shared.resources.sound_caption_everyone
import com.violinjourney.app.shared.resources.sound_caption_off
import com.violinjourney.app.shared.resources.sound_caption_own
import com.violinjourney.app.shared.resources.sound_caption_saved_hint
import com.violinjourney.app.shared.resources.sound_dialog_delete_title
import com.violinjourney.app.shared.resources.sound_dialog_everyone_confirm
import com.violinjourney.app.shared.resources.sound_dialog_everyone_text
import com.violinjourney.app.shared.resources.sound_dialog_everyone_title
import com.violinjourney.app.shared.resources.sound_dialog_preset_hint
import com.violinjourney.app.shared.resources.sound_dialog_preset_save
import com.violinjourney.app.shared.resources.sound_dialog_preset_title
import com.violinjourney.app.shared.resources.sound_dialog_reset_text
import com.violinjourney.app.shared.resources.sound_dialog_reset_title
import com.violinjourney.app.shared.resources.sound_everyone_all
import com.violinjourney.app.shared.resources.sound_everyone_title
import com.violinjourney.app.shared.resources.sound_listen_latest
import com.violinjourney.app.shared.resources.sound_listen_none
import com.violinjourney.app.shared.resources.sound_listen_on
import com.violinjourney.app.shared.resources.sound_listen_other
import com.violinjourney.app.shared.resources.sound_mode_everyone
import com.violinjourney.app.shared.resources.sound_mode_everyone_text
import com.violinjourney.app.shared.resources.sound_mode_own
import com.violinjourney.app.shared.resources.sound_mode_own_text
import com.violinjourney.app.shared.resources.sound_preset_custom
import com.violinjourney.app.shared.resources.sound_preset_names
import com.violinjourney.app.shared.resources.sound_preset_save
import com.violinjourney.app.shared.resources.sound_reset
import com.violinjourney.app.shared.resources.sound_session_row
import com.violinjourney.app.shared.resources.sound_share
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// «Звук» (spec 3.17, 3.36.5, 5.29 R5; records.html 4, landscape.html «Остальные экраны»).
private val ScreenPadding = 16.dp
private val TopBarHeight = 56.dp
private val TopBarHeightLandscape = 48.dp
private val TopBarButton = 48.dp
private val TopBarSide = 4.dp
private val SubtitleGap = 4.dp
private val ContentTop = 4.dp
private val ContentTopLandscape = 12.dp
private val ContentBottom = 16.dp
private val MaxContentWidth = 560.dp

/** Lying, between the columns: 8 at the end of the left one and 8 at the start of the right one. */
private val ColumnGap = 8.dp

/** Lying, the sides of the rows of the player: 16 at the edge of the screen, 8 at the meeting of the columns. */
private val LeftColumnSides = PaddingValues(start = ScreenPadding, end = ColumnGap)

/** The caption of «Как у всех | Свои для записи» under it. */
private val ModeCaptionTop = 8.dp

/** The chips of the presets are seen 44 in a touch of 48: 2 of air over and under them, taken off the seen gaps. */
private val ChipAir = 2.dp
private val PresetsTop = 12.dp
private val PresetsTopUnderListening = 14.dp
private val PresetsBottom = 14.dp
private val PresetsGap = 8.dp

/** Lying, the scope and the blocks of the right column stand this far apart — the air under the presets of the upright screen. */
private val ScopeToBlocks = 14.dp
private val BlocksGap = 10.dp

/** The line «Слушать на» (5.29 R5): 56 at the least, fields 8 / 14. */
private val ListenMinHeight = 56.dp
private val ListenVertical = 8.dp
private val ListenSide = 14.dp
private val ListenGap = 12.dp

/** The sheet «Слушать на…»: the cards 8 apart, the list 8 under its title. */
private val SheetCardsGap = 8.dp
private val SheetListTop = 8.dp

/**
 * Lying: the left column is 340 (spec 3.36.5), but never wider than half of what the window leaves after its three fields of 16 —
 * on 640 × 360 with a cutout at a side (≈ 603.5) a column of 340 would leave the blocks 239 and break «Компрессор» by the letter.
 * On 892 × 412 — 340; on 640 — 296; on 603.5 — 277.75. Pure, with a test.
 */
internal object SoundColumns {
    val Left = 340.dp
    val Gap = 16.dp

    fun left(width: Dp): Dp = minOf(Left, ((width - Gap * 3) / 2).coerceAtLeast(0.dp))
}

/**
 * The «Звук» screen (spec 3.17, 3.36.5): the bar «Звук» with the name of the recording and its mode, and «Поделиться» — or, for all the
 * recordings, «Звук записей», how many recordings it touches and «Сбросить»; first «Как у всех | Свои для записи» with what it means —
 * or the line «Слушать на» that opens the sheet of the recordings; the presets in a ribbon of chips; the numbered cards of the chain,
 * all closed when the screen opens; and the player pinned at the bottom, a panel of its own that covers nothing. Lying — the bar and
 * the presets on the left over the player, the rest on the right. Stateless; [meters] is handed down as a state and read where it is
 * drawn, [position] the same.
 */
@Composable
fun SoundScreen(
    state: SoundState,
    meters: State<SoundMeters?>,
    onIntent: (SoundIntent) -> Unit,
    /** The view model's, not one of the screen's own: the sliders stand on the ranges the values are set by. */
    config: SoundConfig,
    backingConfig: BackingConfig,
    modifier: Modifier = Modifier,
    // asked once: a new zone on every recomposition is a new object each time, and on iOS a read of its file
    zone: TimeZone = remember { TimeZone.currentSystemDefault() },
    /** Where the player is, exactly; [SoundState.player] keeps it to the whole second. Read where the waveform is drawn. */
    position: () -> Long = { state.player?.positionMs ?: 0L },
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        if (state.loading) return@BoxWithConstraints
        val screen = SoundScreenParts(state, meters, onIntent, config, backingConfig, zone, position)
        if (maxWidth > maxHeight) Landscape(screen, width = maxWidth) else Portrait(screen)
    }
    ListenOnSheet(state, zone, onIntent)
    state.dialog?.let { Dialogs(it, onIntent) }
}

/** What the layouts are made of — one bag, not seven parameters twice. */
private class SoundScreenParts(
    val state: SoundState,
    val meters: State<SoundMeters?>,
    val onIntent: (SoundIntent) -> Unit,
    val config: SoundConfig,
    val backingConfig: BackingConfig,
    val zone: TimeZone,
    val position: () -> Long,
) {
    /** The panel of the player stands while a recording plays or its backing is made — known before the player is ready. */
    val panel: Boolean get() = state.listening || state.preparingBacking
}

/** Upright: the bar, the middle that scrolls — the scope, the presets, the cards — and the player at the bottom, ending the middle. */
@Composable
private fun Portrait(screen: SoundScreenParts) {
    val state = screen.state
    Column(Modifier.fillMaxSize()) {
        TopBar(state, screen.zone, TopBarHeight, screen.onIntent)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AppDock(
                dock = { SoundPlayer(state, screen.meters, screen.position, screen.onIntent) },
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
                fade = 0.dp,
                metrics = currentPlayerDockMetrics(),
                pinned = screen.panel,
                ground = MaterialTheme.colorScheme.surfaceContainer,
                shape = PlayerPanelShape,
            ) {
                // the middle ends at the top of the panel: nothing lies under the player, an opened card is brought into sight over it
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = LocalDockInset.current)
                        .verticalScroll(rememberScrollState())
                        .padding(top = ContentTop, bottom = ContentBottom),
                ) {
                    Column(Modifier.padding(horizontal = ScreenPadding)) { Scope(state, screen.zone, screen.onIntent) }
                    Presets(
                        state = state,
                        onIntent = screen.onIntent,
                        padding = PaddingValues(
                            start = ScreenPadding,
                            end = ScreenPadding,
                            top = (if (listensOn(state)) PresetsTopUnderListening else PresetsTop) - ChipAir,
                            bottom = PresetsBottom - ChipAir,
                        ),
                    )
                    Blocks(screen, Modifier.padding(horizontal = ScreenPadding))
                }
            }
        }
    }
}

/**
 * Lying (spec 3.36.5): on the left ([SoundColumns]) the bar of 48 and the presets over the player, compact in a window lower than 700
 * — its panel the whole column, its rows 16 at the edge and 8 at the meeting of the columns; the presets scroll over the player where
 * they do not fit. On the right, scrolling to the bottom of the screen: «Как у всех | Свои» with its caption, or «Слушать на», then the
 * cards.
 */
@Composable
private fun Landscape(screen: SoundScreenParts, width: Dp) {
    val state = screen.state
    Row(Modifier.fillMaxSize()) {
        AppDock(
            dock = { SoundPlayer(state, screen.meters, screen.position, screen.onIntent, sides = LeftColumnSides) },
            modifier = Modifier.width(SoundColumns.left(width)).fillMaxHeight(),
            fade = 0.dp,
            // the sides are the column's: the rows are given them, the ground is the column
            padSides = false,
            metrics = currentPlayerDockMetrics(),
            pinned = screen.panel,
            ground = MaterialTheme.colorScheme.surfaceContainer,
            shape = PlayerPanelShape,
        ) {
            Column(Modifier.fillMaxSize().padding(bottom = LocalDockInset.current)) {
                TopBar(state, screen.zone, TopBarHeightLandscape, screen.onIntent)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Presets(
                        state = state,
                        onIntent = screen.onIntent,
                        padding = PaddingValues(start = ScreenPadding, end = ColumnGap, top = PresetsTop - ChipAir, bottom = PresetsBottom - ChipAir),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = ColumnGap, end = ScreenPadding, top = ContentTopLandscape, bottom = ContentBottom),
        ) {
            Scope(state, screen.zone, screen.onIntent)
            Spacer(Modifier.height(ScopeToBlocks))
            Blocks(screen)
        }
    }
}

/** «Звук записей» with a recording to listen on: the line «Слушать на» stands where the mode would. */
private fun listensOn(state: SoundState): Boolean = state.mode == SoundMode.EVERYONE && state.recording != null

/**
 * The bar (spec 3.36.5, 5.29 R5): «назад», «Звук» — or «Звук записей» — and under it what the sound is: «<название записи> · <режим>»,
 * the name giving way to the mode where both do not stand, after the sign of a video for a video take; for all the recordings — how
 * many it touches, «для 23 записей», or «для всех записей». At the right «Поделиться» of a recording whose sound plays, or «Сбросить»
 * of the default, dimmed where there is nothing to reset.
 */
@Composable
private fun TopBar(state: SoundState, zone: TimeZone, height: Dp, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = TopBarSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(AppIcons.Back, stringResource(Res.string.session_back)) { onIntent(SoundIntent.BackClicked) }
        Column(modifier = Modifier.weight(1f).padding(start = TopBarSide)) {
            Text(
                text = stringResource(if (state.mode == SoundMode.EVERYONE) Res.string.sound_everyone_title else Res.string.sound_session_row),
                modifier = Modifier.semantics { heading() },
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
            )
            Subtitle(state, zone)
        }
        when (state.mode) {
            // sending is what one does after setting the sound — in sight at once (spec 3.36.5)
            SoundMode.RECORDING -> if (state.player != null) {
                BarButton(AppIcons.Share, stringResource(Res.string.sound_share)) { onIntent(SoundIntent.ShareClicked) }
            }
            SoundMode.EVERYONE -> AppButton(
                text = stringResource(Res.string.sound_reset),
                onClick = { onIntent(SoundIntent.ResetClicked) },
                style = AppButtonStyle.Text,
                enabled = state.canReset,
            )
        }
    }
}

/** What the sound of the bar is: «Менуэт соль мажор · 27 сентября · как у всех · Камерный зал»; for everyone — «для 23 записей». */
@Composable
private fun Subtitle(state: SoundState, zone: TimeZone) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES)
    if (state.mode == SoundMode.EVERYONE) {
        Text(
            text = if (state.affected > 0) stringResource(affectedWords(state.affected), state.affected) else stringResource(Res.string.sound_everyone_all),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = style,
        )
        return
    }
    val recording = state.recording ?: return
    val name = sessionTitle(recording.title, recording.pieceTitle, recording.startedAtEpochMs, zone, recording.event)
    val mode = modeWords(state)
    val video = stringResource(Res.string.record_tile_video).takeIf { recording.hasVideo }
    val spoken = listOfNotNull(video, name, mode).joinToString(SAID_SEPARATOR)
    val measurer = rememberTextMeasurer()
    // the least of the name worth showing: its first sign and «…»
    val leastName = remember(name, style, measurer) { measurer.measure(firstSign(name) + ELLIPSIS, style, softWrap = false, maxLines = 1).size.width }
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SubtitleGap),
    ) {
        // whose sound is being set: that of a video (spec 3.19). The screen itself stays without a picture — it is listened to with the ears
        if (video != null) AppIcon(AppIcons.Video, contentDescription = null, tint = colors.onSurfaceVariant, size = IconSizes.InText)
        val separator = stringResource(Res.string.dot_separator)
        Layout(
            contents = listOf(
                { Text(name, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style) },
                // the separator goes with the mode it leads: its spaces are measured as the words are
                { Text(separator + mode, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style) },
                { Text(mode, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style) },
            ),
        ) { (names, withSeparator, alone), constraints ->
            val width = constraints.maxWidth
            // the mode first, whole where the line holds it; the name takes the rest before it — or, with less than its least left,
            // is not placed, and neither is the separator: the mode stands alone
            val led = withSeparator.single().measure(Constraints(maxWidth = width))
            val nameWidth = SubtitleFit.nameWidth(width, led.width, leastName)
            val namePlaced = nameWidth?.let { names.single().measure(Constraints(maxWidth = it)) }
            val modePlaced = if (namePlaced != null) led else alone.single().measure(Constraints(maxWidth = width))
            val used = (namePlaced?.width ?: 0) + modePlaced.width
            val height = maxOf(modePlaced.height, namePlaced?.height ?: 0)
            layout(used.coerceAtMost(width), height) {
                namePlaced?.placeRelative(0, 0)
                modePlaced.placeRelative(namePlaced?.width ?: 0, 0)
            }
        }
    }
}

/**
 * The subtitle of a recording's «Звук» (spec 3.36.5): «<название> · <режим>» — the mode, with the separator that leads it, is
 * measured first and stays whole where the line holds it (else it ends in «…»); the name takes what is left and gives way with an
 * ellipsis. Where not even the least of the name ([leastName]: its first sign and «…») would stand there, neither the name nor the
 * separator is placed — the mode stands alone: the line never begins with a separator. Pure; pixels.
 */
internal object SubtitleFit {
    /** The width the name gets before the separator and the mode, [ledMode] px together; or null — the mode alone. */
    fun nameWidth(width: Int, ledMode: Int, leastName: Int): Int? = (width - ledMode).takeIf { it >= leastName }
}

/** The first sign of [text], a pair of surrogates kept whole. */
private fun firstSign(text: String): String = when {
    text.isEmpty() -> text
    text[0].isHighSurrogate() && text.length > 1 -> text.substring(0, 2)
    else -> text.substring(0, 1)
}

/**
 * The mode of a recording's sound, in the words of 3.17: «как у всех · Камерный зал», «свои настройки», «свои настройки · Тепло»; the
 * first seconds after the first change — «свои настройки · сохраняются сами».
 */
@Composable
private fun modeWords(state: SoundState): String {
    val preset = captionName(state.caption)
    return when {
        state.savedHint -> stringResource(Res.string.sound_caption_saved_hint)
        !state.own -> stringResource(Res.string.sound_caption_everyone, preset)
        state.caption == SoundCaption.Custom -> preset
        else -> stringResource(Res.string.sound_caption_own, preset)
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

/** A preset by its name, «свои настройки» for what is none, «без обработки» for the one that does nothing. */
@Composable
fun captionName(caption: SoundCaption): String = when (caption) {
    is SoundCaption.BuiltIn -> stringArrayResource(Res.array.sound_preset_names)[caption.preset.ordinal].let { name ->
        if (caption.preset == BuiltInPreset.OFF) stringResource(Res.string.sound_caption_off) else name
    }
    is SoundCaption.User -> caption.name
    SoundCaption.Custom -> stringResource(Res.string.sound_caption_custom)
}

/**
 * Whose sound this is (spec 3.36.5): a recording — «Как у всех | Свои для записи» the whole width, with what it means under it (its
 * labels on two smaller lines where a language does not fit them in one); everyone — the line «Слушать на» with the recording it is
 * heard on, or the word that there is none yet.
 */
@Composable
private fun Scope(state: SoundState, zone: TimeZone, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (state.mode) {
        SoundMode.RECORDING -> {
            SegmentedSwitch(
                labels = listOf(stringResource(Res.string.sound_mode_everyone), stringResource(Res.string.sound_mode_own)),
                selectedIndex = if (state.own) 1 else 0,
                onSelect = { onIntent(SoundIntent.ModeSelected(own = it == 1)) },
                shrinkToTwoLines = true,
            )
            Text(
                text = if (state.own) stringResource(Res.string.sound_mode_own_text) else stringResource(Res.string.sound_mode_everyone_text, captionName(state.caption)),
                modifier = Modifier.padding(top = ModeCaptionTop),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            )
        }
        SoundMode.EVERYONE -> {
            val recording = state.recording
            if (recording != null) {
                ListenOnRow(
                    recording = recording,
                    latest = state.recordings.firstOrNull()?.id == recording.sessionId,
                    canPick = state.recordings.size > 1,
                    zone = zone,
                    onPick = { onIntent(SoundIntent.ListenOnClicked) },
                )
            } else {
                Text(
                    text = stringResource(Res.string.sound_listen_none),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
        }
    }
}

/**
 * «Слушать на» (spec 3.36.5): the tile of the recording, «Слушать на» — «· последняя со звуком» while it is the newest — and its name;
 * «Другая» at the right. The line and «Другая» open the sheet of the recordings; with one recording there is no other and nothing
 * opens.
 */
@Composable
private fun ListenOnRow(recording: RecordingName, latest: Boolean, canPick: Boolean, zone: TimeZone, onPick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val caption = stringResource(Res.string.sound_listen_on) + if (latest) stringResource(Res.string.dot_separator) + stringResource(Res.string.sound_listen_latest) else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ListenMinHeight)
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .then(if (canPick) Modifier.clickable(role = Role.Button, onClick = onPick) else Modifier.semantics(mergeDescendants = true) {})
            .padding(start = ListenSide, end = if (canPick) 0.dp else ListenSide, top = ListenVertical, bottom = ListenVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ListenGap),
    ) {
        RecordTile(hasAudio = true, hasVideo = recording.hasVideo, thumbPath = recording.thumbPath)
        Column(Modifier.weight(1f)) {
            Text(caption, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
            Text(
                text = sessionTitle(recording.title, recording.pieceTitle, recording.startedAtEpochMs, zone, recording.event),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (canPick) AppButton(stringResource(Res.string.sound_listen_other), onClick = onPick, style = AppButtonStyle.Text)
    }
}

/**
 * «для N записей» in the words of the interface language (spec 3.26). After «для» Russian takes the genitive, where 2
 * and 5 recordings share a form — «для 2 записей», «для 5 записей», «для 21 записи» — so its few is its many.
 */
internal fun affectedWords(count: Int): StringResource =
    Formats.plural(count, Res.string.sound_affected_one, Res.string.sound_affected_many, Res.string.sound_affected_many)

/**
 * The presets as one ribbon of chips of choice (spec 3.36.5) that runs off the edge: «Свои» first once the settings are nobody's
 * preset, the built-in ones, the user's own — a long press offers to remove one — and «Сохранить как пресет» last, pressed only for
 * settings of one's own. One of them is chosen, as in a group of radio buttons.
 */
@Composable
private fun Presets(state: SoundState, onIntent: (SoundIntent) -> Unit, padding: PaddingValues) {
    val names = stringArrayResource(Res.array.sound_preset_names)
    // what the long press does, as a verb: TalkBack says «дважды нажмите и удерживайте, чтобы удалить» — the word of the dialog's button
    val remove = stringResource(Res.string.piece_delete_confirm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(PresetsGap),
    ) {
        if (state.custom) AppChip.Choice(stringResource(Res.string.sound_preset_custom), selected = true, onClick = {})
        state.chips.forEach { chip ->
            val ref = chip.ref
            AppChip.Choice(
                text = chip.userName ?: names[(ref as PresetRef.BuiltIn).preset.ordinal],
                selected = chip.selected,
                onClick = { onIntent(SoundIntent.PresetSelected(ref)) },
                onLongClick = if (ref is PresetRef.User) ({ onIntent(SoundIntent.PresetLongPressed(ref)) }) else null,
                onLongClickLabel = remove.takeIf { ref is PresetRef.User },
            )
        }
        AppChip.Choice(stringResource(Res.string.sound_preset_save), selected = null, onClick = { onIntent(SoundIntent.SavePresetClicked) }, enabled = state.custom)
    }
}

/** The four cards of the chain and — of a take under a backing — «Минусовка», the fifth, 10 apart. */
@Composable
private fun Blocks(screen: SoundScreenParts, modifier: Modifier = Modifier) {
    val state = screen.state
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BlocksGap)) {
        SoundBlocks(state.settings, state.expanded, state.band, state.details, screen.meters, screen.config, screen.onIntent)
        // last, after «Громкость»: it is not the violin's (spec 3.32)
        state.backing?.let { BackingCard(it, state.backingUnavailable, SoundCard.BACKING in state.expanded, screen.backingConfig, screen.onIntent) }
    }
}

/**
 * «Слушать на…» (spec 3.36.5): a bottom sheet of the recordings the default can be heard on — the cards of «Записи» under their days,
 * without «⋯», on the ground of the screen; the one listened on outlined in the accent. A card chooses and closes the sheet; a swipe,
 * a tap beside it and «назад» only hide it — the choice stays.
 */
@Composable
private fun ListenOnSheet(state: SoundState, zone: TimeZone, onIntent: (SoundIntent) -> Unit) {
    AppSheet(
        value = state.dialog as? SoundDialog.PickRecording,
        onHide = { onIntent(SoundIntent.DialogDismissed) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        // the list scrolls itself: hundreds of recordings on a phone played for long, only the cards in sight are made
        scroll = { false },
    ) {
        ListenOnSheetContent(state.recordings, state.recording?.sessionId, state.today, zone, onPick = { onIntent(SoundIntent.RecordingPicked(it)) })
    }
}

/**
 * The content of the sheet «Слушать на…»: its title as a label of a section, and the cards by days, newest first — opened with the
 * one listened on ([current]) in sight: under the header of its day where the two stand in the sheet together, else — deep in a long
 * day — first in the sheet itself. Public for the previews.
 */
@Composable
fun ListenOnSheetContent(recordings: List<HistoryCard>, current: Long?, today: LocalDate, zone: TimeZone, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val groups = remember(recordings, today) { recordings.groupBy { it.date }.map { (date, cards) -> DayGroup(date, today = date == today, cards = cards) } }
    val place = remember(groups, current) { ListenOnPlace.of(groups.map { group -> group.cards.map { it.id } }, current) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = place.header)
    LaunchedEffect(list) {
        if (current == null || groups.isEmpty()) return@LaunchedEffect
        // the header of its day first, as long as the card listened on is seen whole under it; else the card first
        val laid = snapshotFlow { list.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        val card = laid.visibleItemsInfo.firstOrNull { it.index == place.card }
        if (card == null || card.offset + card.size > laid.viewportEndOffset) list.scrollToItem(place.card)
    }
    Column(modifier) {
        SectionLabel(stringResource(Res.string.sound_listen_on))
        LazyColumn(
            state = list,
            modifier = Modifier.padding(top = SheetListTop),
            verticalArrangement = Arrangement.spacedBy(SheetCardsGap),
        ) {
            groups.forEach { group ->
                item(key = "day-${group.date}") { DayHeader(group) }
                items(group.cards, key = { it.id }) { card ->
                    SessionCard(card, zone, onClick = { onPick(card.id) }, place = RecordPlace.Sheet, current = card.id == current)
                }
            }
        }
    }
}

/**
 * Where the list of «Слушать на…» opens (spec 3.36.5): the item of the header of the day of the recording listened on and that of its
 * card, among the items of the list — a header, then the cards of its day, day after day ([days]: the ids of the cards of each day).
 * No such recording — the top of the list. Pure.
 */
internal data class ListenOnPlace(val header: Int, val card: Int) {
    companion object {
        fun of(days: List<List<Long>>, current: Long?): ListenOnPlace {
            var header = 0
            days.forEach { day ->
                val at = day.indexOf(current)
                if (at >= 0) return ListenOnPlace(header, header + 1 + at)
                header += day.size + 1
            }
            return ListenOnPlace(0, 0)
        }
    }
}

@Composable
private fun Dialogs(dialog: SoundDialog, onIntent: (SoundIntent) -> Unit) {
    val dismiss = { onIntent(SoundIntent.DialogDismissed) }
    val confirm = { onIntent(SoundIntent.DialogConfirmed) }
    when (dialog) {
        SoundDialog.BackToEveryone -> AppDialog(
            title = stringResource(Res.string.sound_dialog_everyone_title),
            text = stringResource(Res.string.sound_dialog_everyone_text),
            confirm = stringResource(Res.string.sound_dialog_everyone_confirm),
            onConfirm = confirm,
            onDismiss = dismiss,
        )
        SoundDialog.ResetEveryone -> AppDialog(
            title = stringResource(Res.string.sound_dialog_reset_title),
            text = stringResource(Res.string.sound_dialog_reset_text),
            confirm = stringResource(Res.string.sound_reset),
            onConfirm = confirm,
            onDismiss = dismiss,
        )
        is SoundDialog.DeletePreset -> DeleteDialog(
            title = stringResource(Res.string.sound_dialog_delete_title, dialog.name),
            text = null,
            confirm = stringResource(Res.string.piece_delete_confirm),
            onConfirm = confirm,
            onDismiss = dismiss,
        )
        SoundDialog.SavePreset -> {
            // the screen keeps a copy of the name: «Сохранить» wakes up with the first letter, not a frame later
            var name by rememberSaveable { mutableStateOf("") }
            FieldDialog(
                title = stringResource(Res.string.sound_dialog_preset_title),
                label = stringResource(Res.string.sound_dialog_preset_hint),
                initial = "",
                confirm = stringResource(Res.string.sound_dialog_preset_save),
                onConfirm = { onIntent(SoundIntent.PresetNameConfirmed(it)) },
                onDismiss = dismiss,
                onValueChange = { name = it },
                maxLength = PRESET_NAME_LENGTH,
                confirmEnabled = name.isNotBlank(),
            )
        }
        // a sheet of its own, made whatever the dialog: it slides away when the choice drops it
        SoundDialog.PickRecording -> Unit
    }
}

/** Mirrors `SoundConfig.maxPresetNameLength`; the repository cuts to it anyway, the field just does not let more in. */
private const val PRESET_NAME_LENGTH = 24
private const val TABULAR_FIGURES = "tnum"

/** Between the words TalkBack hears of the subtitle. */
private const val SAID_SEPARATOR = ", "

/** What a name given way to ends with. */
private const val ELLIPSIS = "…"
