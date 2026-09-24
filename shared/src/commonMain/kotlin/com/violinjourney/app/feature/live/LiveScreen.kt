package com.violinjourney.app.feature.live

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 */
@Composable
fun LiveScreen(
    state: LiveState,
    onIntent: (LiveIntent) -> Unit,
    modifier: Modifier = Modifier,
    /** System animations are switched off: the ring stands still and changes in steps (spec 3.14). */
    reduceMotion: Boolean = false,
    showVenue: Boolean = true,
    /** The bookmark by the record key and its sheets (spec 3.28): a state of their own, changing once a second. */
    block: BlockState = BlockState.NONE,
    onBlockIntent: (BlockIntent) -> Unit = {},
) {
    val home = LocalHomeLook.current
    val slots = LiveSlots(
        backdrop = if (showVenue) {
            { look ->
                VenueBackdrop(
                    venue = state.venue,
                    picture = rememberVenuePicture(state.venue, home),
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
            BlockBookmark(
                bookmark = block.bookmark,
                width = width,
                onClick = { onBlockIntent(BlockIntent.BookmarkClicked) },
                // it fades with the light like the practice tag — but «готово» stays whole: the end of a block
                // comes while the violin sounds, and it is the one thing left for the corner of the eye (handoff 30c2)
                modifier = Modifier.graphicsLayer { alpha = if (block.bookmark is Bookmark.Done) 1f else chrome() },
                reduceMotion = reduceMotion,
            )
        },
        tag = { maxWidth, tagModifier ->
            PracticeTag(
                practiceMs = state.practiceMs,
                maxWidth = maxWidth,
                onClick = { onIntent(LiveIntent.PracticeTagClicked) },
                modifier = tagModifier,
                reduceMotion = reduceMotion,
            )
        },
        recordKey = { recording, enabled, keyModifier ->
            RecordButton(
                recording = recording,
                enabled = enabled,
                onClick = { onIntent(LiveIntent.RecordClicked) },
                modifier = keyModifier,
            )
        },
        gear = { enabled, gearModifier ->
            SettingsGear(onClick = { onIntent(LiveIntent.SettingsClicked) }, enabled = enabled, modifier = gearModifier)
        },
        recordingStrip = { recording, stripModifier -> RecordingStrip(recording = recording, modifier = stripModifier) },
        overlay = { landscape -> BlockSheetHost(block.sheet, landscape, onBlockIntent, reduceMotion) },
    )
    LiveScreenLayout(state = state, onIntent = onIntent, slots = slots, modifier = modifier, reduceMotion = reduceMotion)
}
