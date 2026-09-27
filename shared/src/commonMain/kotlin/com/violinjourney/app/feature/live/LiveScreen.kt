package com.violinjourney.app.feature.live

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.violinjourney.app.feature.journey.LocalHomeLook
import com.violinjourney.app.feature.live.block.BlockBookmark
import com.violinjourney.app.feature.live.block.BlockIntent
import com.violinjourney.app.feature.live.block.BlockSheetHost
import com.violinjourney.app.feature.live.block.BlockState
import com.violinjourney.app.feature.live.block.Bookmark
import com.violinjourney.app.feature.live.components.PracticeTag
import com.violinjourney.app.feature.live.components.RecordButton
import com.violinjourney.app.feature.live.components.RecordingStrip
import com.violinjourney.app.feature.live.components.SettingsGear
import com.violinjourney.app.feature.live.venue.VenueBackdrop
import com.violinjourney.app.feature.live.venue.rememberVenuePicture

/**
 * Live screen (spec 3.1, 3.27). The layout, the ring and the light are [LiveScreenLayout], shared with iOS; here
 * go in the parts only the app has yet: the picture of the place, the bookmark of blocks and its sheets, the
 * practice tag, the record key and its strip, the gear. [showVenue] false is the Live of before, on its plain
 * dark field (a build for comparison).
 *
 * The slots are made once and read what they show through state, so that a tick of the bookmark or of the practice
 * composes the slot that shows it and not the whole screen.
 */
@Composable
fun LiveScreen(
    state: LiveState,
    onIntent: (LiveIntent) -> Unit,
    modifier: Modifier = Modifier,
    /** What moves with every frame of sound ([LiveGauge]): read only while drawing. */
    gauge: () -> LiveGauge = NoGauge,
    /** System animations are switched off: the ring stands still and changes in steps (spec 3.14). */
    reduceMotion: Boolean = false,
    showVenue: Boolean = true,
    /** The bookmark by the record key and its sheets (spec 3.28): a state of their own, changing once a second. */
    block: BlockState = BlockState.NONE,
    onBlockIntent: (BlockIntent) -> Unit = {},
) {
    val home = LocalHomeLook.current
    val currentVenue by rememberUpdatedState(state.venue)
    val currentPracticeMs by rememberUpdatedState(state.practiceMs)
    val currentRecording by rememberUpdatedState(state.recording != null)
    val currentHome by rememberUpdatedState(home)
    val currentBlock by rememberUpdatedState(block)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentOnBlockIntent by rememberUpdatedState(onBlockIntent)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)
    val slots = remember(showVenue) {
        LiveSlots(
            backdrop = if (showVenue) {
                { look ->
                    VenueBackdrop(
                        venue = currentVenue,
                        picture = rememberVenuePicture(currentVenue, currentHome),
                        landscape = look.landscape,
                        darkness = look.darkness,
                        glow = look.glow,
                        zoneColor = look.zoneColor,
                        zoneScale = look.zoneScale,
                        ringCenter = look.ringCenter,
                        ringDiameter = look.ringDiameter,
                        // upright the tab bar lies under the picture: the floor fades into it
                        fadeInto = if (look.landscape) null else MaterialTheme.colorScheme.surfaceContainer,
                    )
                }
            } else {
                null
            },
            bookmark = { width, chrome ->
                val bookmark = currentBlock.bookmark
                BlockBookmark(
                    bookmark = bookmark,
                    width = width,
                    onClick = { currentOnBlockIntent(BlockIntent.BookmarkClicked) },
                    // it fades with the light like the practice tag — but «готово» stays whole: the end of a block
                    // comes while the violin sounds, and it is the one thing left for the corner of the eye (handoff 30c2)
                    modifier = Modifier.graphicsLayer { alpha = if (bookmark is Bookmark.Done) 1f else chrome() },
                    reduceMotion = currentReduceMotion,
                )
            },
            tag = { maxWidth, tagModifier ->
                val practiceMs = currentPracticeMs
                PracticeTag(
                    practiceMs = practiceMs,
                    maxWidth = maxWidth,
                    onClick = { currentOnIntent(LiveIntent.PracticeTagClicked) },
                    modifier = tagModifier,
                    reduceMotion = currentReduceMotion,
                    // a running practice cannot be finished while a take records: leaving Live would end it (spec 3.12)
                    enabled = practiceMs == null || !currentRecording,
                )
            },
            recordKey = { recording, enabled, keyModifier ->
                RecordButton(
                    recording = recording,
                    enabled = enabled,
                    onClick = { currentOnIntent(LiveIntent.RecordClicked) },
                    modifier = keyModifier,
                )
            },
            gear = { enabled, gearModifier ->
                SettingsGear(onClick = { currentOnIntent(LiveIntent.SettingsClicked) }, enabled = enabled, modifier = gearModifier)
            },
            recordingStrip = { recording, ribbon, stripModifier -> RecordingStrip(recording = recording, ribbon = ribbon, modifier = stripModifier) },
            overlay = { landscape -> BlockSheetHost(currentBlock.sheet, landscape, currentOnBlockIntent, currentReduceMotion) },
        )
    }
    LiveScreenLayout(state = state, onIntent = onIntent, slots = slots, modifier = modifier, gauge = gauge, reduceMotion = reduceMotion)
}

