package com.violinjourney.app.feature.live

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.violinjourney.app.feature.journey.LocalHomeLook
import com.violinjourney.app.feature.live.block.BlockBookmark
import com.violinjourney.app.feature.live.block.BlockIntent
import com.violinjourney.app.feature.live.block.BlockSheetHost
import com.violinjourney.app.feature.live.block.BlockState
import com.violinjourney.app.feature.live.components.LiveRecordKey
import com.violinjourney.app.feature.live.components.PracticeTag
import com.violinjourney.app.feature.live.components.RecordingStrip
import com.violinjourney.app.feature.live.components.SettingsGear
import com.violinjourney.app.feature.live.venue.VenueBackdrop
import com.violinjourney.app.feature.live.venue.rememberVenuePicture
import com.violinjourney.app.navigation.LocalTabBarLight

/**
 * Live screen (spec 3.1, 3.27, 3.36.6). The layout, the ring and the light are [LiveScreenLayout], shared with iOS; here go in the
 * parts only the app has yet: the picture of the place, «Что играю» and its sheets, the practice tag, the record key and its strip,
 * the gear, and the light lent to the tab bar. [showVenue] false is the Live of before, on its plain dark field (a build for
 * comparison).
 *
 * The slots are made once and read what they show through state, so that a tick of «Что играю» or of the practice composes the
 * slot that shows it and not the whole screen.
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
    /** «Что играю» by the record key and its sheets (spec 3.28, 3.36.6): a state of their own, changing once a second. */
    block: BlockState = BlockState.NONE,
    onBlockIntent: (BlockIntent) -> Unit = {},
    /** The pale note of the locked string in the silent ring of «Настройка» (spec 3.36.6): its switch; a preview may turn it on. */
    stringSilhouette: Boolean = LiveSwitches.STRING_SILHOUETTE,
    /** The tab bar dims with the controls (spec 3.36.6): its switch; a preview may turn it on. */
    dimTabBar: Boolean = LiveSwitches.DIM_TAB_BAR,
) {
    val home = LocalHomeLook.current
    val currentVenue by rememberUpdatedState(state.venue)
    val currentPracticeMs by rememberUpdatedState(state.practiceMs)
    val currentRecording by rememberUpdatedState(state.recording != null)
    val currentPlaying by rememberUpdatedState(HouseLights.playing(state))
    val currentHome by rememberUpdatedState(home)
    val currentBlock by rememberUpdatedState(block)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentOnBlockIntent by rememberUpdatedState(onBlockIntent)
    val currentReduceMotion by rememberUpdatedState(reduceMotion)
    val slots = remember(showVenue, dimTabBar) {
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
            bookmark = { width, light ->
                BlockBookmark(
                    bookmark = currentBlock.bookmark,
                    width = width,
                    onClick = { currentOnBlockIntent(BlockIntent.BookmarkClicked) },
                    reduceMotion = currentReduceMotion,
                    // it dims with the light like the practice tag — but «готово» stays whole: the end of a block comes while the
                    // violin sounds, and it is the one thing left for the corner of the eye (spec 3.36.6)
                    light = light,
                )
            },
            tag = { width, light ->
                val practiceMs = currentPracticeMs
                PracticeTag(
                    practiceMs = practiceMs,
                    width = width,
                    onClick = { currentOnIntent(LiveIntent.PracticeTagClicked) },
                    reduceMotion = currentReduceMotion,
                    // a running practice cannot be finished while a take records: leaving Live would end it (spec 3.12)
                    enabled = practiceMs == null || !currentRecording,
                    light = light,
                )
            },
            recordKey = { recording, enabled, alpha ->
                LiveRecordKey(
                    recording = recording,
                    enabled = enabled,
                    onClick = { currentOnIntent(LiveIntent.RecordClicked) },
                    alpha = alpha,
                    reduceMotion = currentReduceMotion,
                )
            },
            gear = { enabled, gearModifier ->
                SettingsGear(onClick = { currentOnIntent(LiveIntent.SettingsClicked) }, enabled = enabled, modifier = gearModifier)
            },
            recordingStrip = { recording, ribbon, stripModifier -> RecordingStrip(recording = recording, ribbon = ribbon, modifier = stripModifier) },
            light = { chrome -> if (dimTabBar) LendTabBarLight(light = chrome) },
            overlay = { landscape ->
                BlockSheetHost(
                    sheet = currentBlock.sheet,
                    landscape = landscape,
                    onIntent = currentOnBlockIntent,
                    // while one plays only the ring and the dot of a take move on Live: the lights of «Начать занятие» stand still
                    calm = { currentPlaying },
                    reduceMotion = currentReduceMotion,
                )
            },
        )
    }
    LiveScreenLayout(
        state = state,
        onIntent = onIntent,
        slots = slots,
        modifier = modifier,
        gauge = gauge,
        reduceMotion = reduceMotion,
        stringSilhouette = stringSilhouette,
    )
}

/**
 * Live lends its [light] to the tab bar ([com.violinjourney.app.navigation.TabBarLight], spec 3.36.6, behind
 * [LiveSwitches.DIM_TAB_BAR]) while it is resumed: the items of the bar dim with the controls, its ground does not, touches pass. It
 * takes the light back as soon as it pauses — leaving Live pauses its entry at once, before the cross-fade of the navigation ends;
 * only the one who lent it takes it back, so a Live that comes in first keeps its own. Whatever its own shape: whether there is a bar
 * under Live the root decides — `MainActivity` by the orientation of the window, `IosApp` by its shape — and shows none on Live lying
 * down, where the lent light dims nothing; upright in a split screen Live may lay itself out lying down while the root shows the bar
 * under it, and the bar dims with the controls there as well. Public for the instrumented test of the lending.
 */
@Composable
fun LendTabBarLight(light: () -> Float) {
    val bar = LocalTabBarLight.current
    val owner = remember { Any() }
    LifecycleResumeEffect(bar, light) {
        bar.lend(owner, light)
        onPauseOrDispose { bar.takeBack(owner) }
    }
}
