package com.violinjourney.app.feature.live

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.domain.session.RecordingRibbon
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.LocalHomeLook
import com.violinjourney.app.feature.live.block.BlockState
import com.violinjourney.app.feature.live.block.Bookmark
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.LocalTabBarLight
import com.violinjourney.app.navigation.TabBarLight
import com.violinjourney.app.navigation.TopLevelDestination
import kotlin.math.roundToInt

// One preview per row of the state table in spec 3.4, mirroring handoff frames 8a–8f
// (the area above the navigation bar of the 412 × 892 base screen).

private const val A4 = 69
private const val D4 = 62
private const val F_SHARP_5 = 78

/** A note as the chain hands it to Live: the words in the signal, the numbers that move every frame in the gauge. */
private class Heard(val signal: LiveSignal.Sounding, val gauge: LiveGauge)

private fun heard(note: Note, cents: Double, zone: Zone, direction: Direction?, holdProgress: Double, level: Float = 0f) = Heard(
    LiveSignal.Sounding(note, zone, direction, displayCents = cents.roundToInt(), holdComplete = holdProgress >= 1.0),
    LiveGauge(cents = cents, level = level, glowTarget = LiveReducer.glowTargetOf(zone, holdProgress, IntonationConfig())),
)

@Composable
private fun LivePreview(
    heard: Heard,
    mode: LiveMode = LiveMode.PLAY,
    lockedString: ViolinString? = null,
    recording: RecordingState? = null,
    ribbon: RecordingRibbon? = null,
    practiceMs: Long? = null,
    reduceMotion: Boolean = false,
    venue: Venue? = null,
    bookmark: Bookmark = Bookmark.Entry,
    silhouette: Boolean = LiveSwitches.STRING_SILHOUETTE,
    showVenue: Boolean = true,
) = LivePreview(heard.signal, heard.gauge, mode, lockedString, recording, ribbon, practiceMs, reduceMotion, venue, bookmark, silhouette, showVenue)

/**
 * [silhouette] — the switch of the pale note of the locked string (spec 3.36.6), off in the app; [showVenue] false — the plain Live of
 * `-PplainLive=true`, the controls on the card colour.
 */
@Composable
private fun LivePreview(
    signal: LiveSignal,
    gauge: LiveGauge = LiveGauge(),
    mode: LiveMode = LiveMode.PLAY,
    lockedString: ViolinString? = null,
    recording: RecordingState? = null,
    ribbon: RecordingRibbon? = null,
    practiceMs: Long? = null,
    reduceMotion: Boolean = false,
    venue: Venue? = null,
    bookmark: Bookmark = Bookmark.Entry,
    silhouette: Boolean = LiveSwitches.STRING_SILHOUETTE,
    showVenue: Boolean = true,
) {
    val config = IntonationConfig()
    val target = LiveTarget(mode, lockedString)
    val shownGauge = gauge.copy(ribbon = ribbon ?: RecordingRibbon.EMPTY.takeIf { recording != null })
    ViolinTheme {
        // the pictures are read from the assets: an interactive preview shows them, a static one the field
        CompositionLocalProvider(LocalHomeLook provides HomeState.EMPTY.copy(loaded = true)) {
        LiveScreen(
            state = LiveState(
                mode = mode,
                signal = signal,
                tuning = LiveReducer.tuningStateOf(LiveTarget(mode, lockedString), signal, config),
                recording = recording,
                canRecord = LiveReducer.canRecord(LiveTarget(mode, lockedString), signal),
                scale = ScaleSpec(config),
                zoneCrossfadeMs = config.zoneCrossfadeMs,
                glowStep = LiveReducer.glowStepOf(signal, config),
                statusLine = LiveReducer.statusLineOf(target, signal),
                practiceMs = practiceMs,
                venue = venue,
            ),
            onIntent = {},
            gauge = { shownGauge },
            reduceMotion = reduceMotion,
            showVenue = showVenue,
            block = BlockState(bookmark, sheet = null),
            stringSilhouette = silhouette,
        )
        }
    }
}

@Preview(name = "12a2 InTune · A4, just hit: glow .6", widthDp = 412, heightDp = 788)
@Composable
private fun InTuneFreshPreview() = LivePreview(
    heard(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.0, level = 0.5f),
)

@Preview(name = "InTune · A4, held 70 %: glow .88", widthDp = 412, heightDp = 788)
@Composable
private fun InTunePreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7, level = 0.6f),
)

@Preview(name = "12a3 InTune · A4, held 2 s: glow 1", widthDp = 412, heightDp = 788)
@Composable
private fun InTuneHeldPreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 1.0, level = 0.8f),
)

@Preview(name = "12a9 animations removed: the step of the zone, no breath", widthDp = 412, heightDp = 788)
@Composable
private fun ReducedMotionPreview() = LivePreview(
    heard(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7, level = 0.9f),
    reduceMotion = true,
)

@Preview(name = "Sharp · F#5, near", widthDp = 412, heightDp = 788)
@Composable
private fun SharpNearPreview() = LivePreview(
    heard(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
)

@Preview(name = "Flat · D4, off", widthDp = 412, heightDp = 788)
@Composable
private fun FlatOffPreview() = LivePreview(
    heard(Note(D4), cents = -27.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
)

@Preview(name = "Silence", widthDp = 412, heightDp = 788)
@Composable
private fun SilencePreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "TooNoisy", widthDp = 412, heightDp = 788)
@Composable
private fun TooNoisyPreview() = LivePreview(LiveSignal.TooNoisy)

@Preview(name = "MicUnavailable · the key 0.4 and no answer", widthDp = 412, heightDp = 788)
@Composable
private fun MicUnavailablePreview() = LivePreview(LiveSignal.MicUnavailable)

@Preview(name = "NoMicPermission · no ring: the card below the middle, «Что играю» and the tag whole", widthDp = 412, heightDp = 788)
@Composable
private fun NoMicPermissionPreview() = LivePreview(LiveSignal.NoMicPermission)

// Tuning mode, handoff frames 9a and 9b.

@Preview(name = "Tuning · auto, nearest string A", widthDp = 412, heightDp = 788)
@Composable
private fun TuningAutoPreview() = LivePreview(
    heard(Note(A4), cents = 1.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.15),
    mode = LiveMode.TUNING,
)

@Preview(name = "Tuning · string D locked", widthDp = 412, heightDp = 788)
@Composable
private fun TuningLockedPreview() = LivePreview(
    heard(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    lockedString = ViolinString.D4,
)

@Preview(name = "Tuning · auto, silence", widthDp = 412, heightDp = 788)
@Composable
private fun TuningSilencePreview() = LivePreview(LiveSignal.Silence, mode = LiveMode.TUNING)

// Landscape, handoff frame v1-land (892 x 412), plus the tight cases.

@Preview(name = "Landscape · InTune", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapeInTunePreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
)

@Preview(name = "Landscape · Silence", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapeSilencePreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "Landscape · NoMicPermission: the card in the panel of the ring, not wider than 340", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapeNoMicPreview() = LivePreview(LiveSignal.NoMicPermission)

@Preview(name = "Landscape · Tuning, D locked", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapeTuningPreview() = LivePreview(
    heard(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    lockedString = ViolinString.D4,
)

@Preview(name = "Landscape · low 640 x 336, tuning", widthDp = 640, heightDp = 336)
@Composable
private fun LandscapeLowPreview() = LivePreview(
    heard(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
    mode = LiveMode.TUNING,
)

@Preview(name = "Small phone 320 x 500", widthDp = 320, heightDp = 500)
@Composable
private fun SmallPhonePreview() = LivePreview(
    heard(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
)

@Preview(name = "Large system font", widthDp = 412, heightDp = 788, fontScale = 1.5f)
@Composable
private fun LargeFontPreview() = LivePreview(
    heard(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
    mode = LiveMode.TUNING,
)

// Recording (spec 3.9): red stop button, strip with timer and mini bar, switcher dimmed.

private val SampleRecording = RecordingState(elapsedMs = 47_000)

private val SampleRibbon = RecordingRibbon(
    listOf(
        RecordingBar(20, Zone.IN_TUNE), RecordingBar(8, Zone.NEAR), RecordingBar(15, Zone.IN_TUNE),
        RecordingBar(5, Zone.OFF), RecordingBar(22, Zone.IN_TUNE), RecordingBar(10, Zone.NEAR),
    ),
    span = 100f,
)

@Preview(name = "Recording · portrait: the strip on its glass of 50, the red key with its stop", widthDp = 412, heightDp = 788)
@Composable
private fun RecordingPreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.4),
    recording = SampleRecording,
    ribbon = SampleRibbon,
)

@Preview(name = "Recording · landscape", widthDp = 892, heightDp = 412)
@Composable
private fun RecordingLandscapePreview() = LivePreview(
    heard(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    recording = SampleRecording,
    ribbon = SampleRibbon,
)

// The practice tag by the record key, handoff nav_bar 35a (none yet: «Начать занятие») and 35b (running).

private const val PRACTICE_MS = 754_000L

@Preview(name = "Practice tag · none: glass, «Начать · занятие»", widthDp = 412, heightDp = 788)
@Composable
private fun PracticeTagIdlePreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "Practice tag · running: paper, the velvet dot, the time", widthDp = 412, heightDp = 788)
@Composable
private fun PracticeTagSilencePreview() = LivePreview(LiveSignal.Silence, practiceMs = PRACTICE_MS)

@Preview(name = "Practice tag · play", widthDp = 412, heightDp = 788)
@Composable
private fun PracticeTagPreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
    practiceMs = PRACTICE_MS,
)

@Preview(name = "Practice tag · recording", widthDp = 412, heightDp = 788)
@Composable
private fun PracticeTagRecordingPreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
    recording = RecordingState(elapsedMs = 84_000),
    ribbon = RecordingRibbon(listOf(RecordingBar(50, Zone.IN_TUNE), RecordingBar(20, Zone.NEAR)), span = 100f),
    practiceMs = PRACTICE_MS,
)

@Preview(name = "Practice tag · tuning", widthDp = 412, heightDp = 788)
@Composable
private fun PracticeTagTuningPreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
    mode = LiveMode.TUNING,
    practiceMs = PRACTICE_MS,
)

@Preview(name = "Practice tag · landscape", widthDp = 892, heightDp = 412)
@Composable
private fun PracticeTagLandscapePreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
    practiceMs = PRACTICE_MS,
)

@Preview(name = "12e1 Tuning · D locked, silence", widthDp = 412, heightDp = 788)
@Composable
private fun TuningLockedSilencePreview() =
    LivePreview(LiveSignal.Silence, mode = LiveMode.TUNING, lockedString = ViolinString.D4)

@Preview(name = "Tuning · D locked, silence · silhouette ON (the switch is off in the app)", widthDp = 412, heightDp = 788)
@Composable
private fun TuningSilhouettePreview() =
    LivePreview(LiveSignal.Silence, mode = LiveMode.TUNING, lockedString = ViolinString.D4, silhouette = true)

@Preview(name = "Tuning · D locked, the note sounds · silhouette ON: the note itself", widthDp = 412, heightDp = 788)
@Composable
private fun TuningSilhouetteSoundingPreview() = LivePreview(
    heard(Note(D4), cents = -6.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.3),
    mode = LiveMode.TUNING,
    lockedString = ViolinString.D4,
    silhouette = true,
)

@Preview(name = "Tuning · A far flat, −80 c: the slider and its halo whole at the start of the scale", widthDp = 412, heightDp = 788)
@Composable
private fun TuningFarFlatPreview() = LivePreview(
    heard(Note(A4), cents = -80.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    mode = LiveMode.TUNING,
)

@Preview(name = "Tuning · A far sharp, +80 c, plain build: the slider whole at the end of the scale", widthDp = 360, heightDp = 506)
@Composable
private fun TuningFarSharpPlainPreview() = LivePreview(
    heard(Note(A4), cents = 80.0, zone = Zone.OFF, direction = Direction.SHARP, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    showVenue = false,
)

@Preview(name = "Tuning · no permission: the card over the scale (0.3), the strings at 0.4", widthDp = 412, heightDp = 788)
@Composable
private fun TuningNoMicPreview() = LivePreview(LiveSignal.NoMicPermission, mode = LiveMode.TUNING, lockedString = ViolinString.D4)

@Preview(name = "Tuning · 360 x 640: the ring about 2 dp smaller than before R6", widthDp = 360, heightDp = 506)
@Composable
private fun TuningSmallPreview() = LivePreview(
    heard(Note(A4), cents = 12.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
    mode = LiveMode.TUNING,
)

@Preview(name = "Play · 360 x 640: the ring about 8 dp smaller than before R6 (the plate of 36)", widthDp = 360, heightDp = 506)
@Composable
private fun PlaySmallPreview() = LivePreview(
    heard(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.5),
)

@Preview(name = "Plain build · tuning, the controls on the card colour", widthDp = 412, heightDp = 788)
@Composable
private fun PlainTuningPreview() = LivePreview(
    heard(Note(A4), cents = 12.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    showVenue = false,
)

@Preview(name = "Plain build · silence, no plate under the status line", widthDp = 412, heightDp = 788)
@Composable
private fun PlainSilencePreview() = LivePreview(LiveSignal.Silence, showVenue = false)

@Preview(name = "Plain build · landscape, tuning", widthDp = 892, heightDp = 412)
@Composable
private fun PlainLandscapePreview() = LivePreview(
    heard(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    lockedString = ViolinString.D4,
    showVenue = false,
)

@Preview(name = "Landscape · low 640 x 336, play: the switcher where it stands in tuning", widthDp = 640, heightDp = 336)
@Composable
private fun LandscapeLowPlayPreview() = LivePreview(
    heard(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
)

@Preview(name = "Landscape · 603 x 336 (640 x 360 with a cutout), tuning, D locked", widthDp = 603, heightDp = 336)
@Composable
private fun LandscapeCutoutTuningPreview() = LivePreview(
    heard(Note(D4), cents = -9.0, zone = Zone.NEAR, direction = Direction.FLAT, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    lockedString = ViolinString.D4,
)

@Preview(name = "Landscape · 603 x 308 (the emulator's 640 x 360 lying down), tuning: the word gives way, its place kept", widthDp = 603, heightDp = 308, locale = "ru")
@Composable
private fun LandscapeEmulatorTuningPreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.5),
    mode = LiveMode.TUNING,
)

@Preview(name = "Landscape · 603 x 308, play: the word at its full size", widthDp = 603, heightDp = 308, locale = "ru")
@Composable
private fun LandscapeEmulatorPlayPreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.5),
)

@Preview(name = "Landscape · 603 x 336 at the font 1.3, tuning: the word steps down to 22 sp", widthDp = 603, heightDp = 336, fontScale = 1.3f, locale = "ru")
@Composable
private fun LandscapeCutoutLargeFontTuningPreview() = LivePreview(
    heard(Note(A4), cents = 12.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
    mode = LiveMode.TUNING,
)

@Preview(name = "Landscape · 603 x 336, ru: «Настройка» keeps 15 sp, off the middle up to the gear's touch", widthDp = 603, heightDp = 336, locale = "ru")
@Composable
private fun LandscapeCutoutRussianPreview() = LivePreview(LiveSignal.Silence, mode = LiveMode.TUNING, lockedString = ViolinString.D4)

@Preview(name = "Small phone 320 x 500 · tuning at the font 1.5", widthDp = 320, heightDp = 500, fontScale = 1.5f, locale = "ru")
@Composable
private fun SmallPhoneLargeFontTuningPreview() = LivePreview(LiveSignal.Silence, mode = LiveMode.TUNING, lockedString = ViolinString.E5)

@Preview(name = "de · 360 x 640 at the font 1.3, tuning", widthDp = 360, heightDp = 506, fontScale = 1.3f, locale = "de")
@Composable
private fun GermanTuningPreview() = LivePreview(LiveSignal.Silence, mode = LiveMode.TUNING, lockedString = ViolinString.A4)

@Preview(name = "fr · 360 x 640 at the font 1.3, tuning", widthDp = 360, heightDp = 506, fontScale = 1.3f, locale = "fr")
@Composable
private fun FrenchTuningPreview() = LivePreview(LiveSignal.Silence, mode = LiveMode.TUNING, lockedString = ViolinString.A4)

@Preview(name = "12e4 Tuning · too noisy", widthDp = 412, heightDp = 788)
@Composable
private fun TuningNoisyPreview() = LivePreview(LiveSignal.TooNoisy, mode = LiveMode.TUNING)

@Preview(name = "12g1 small screen 360x640", widthDp = 360, heightDp = 576)
@Composable
private fun SmallScreenPreview() = LivePreview(
    heard(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 1.0, level = 0.6f),
)

// In the room and in the halls (spec 3.27, handoff venue 29c, 29d): the light is on in silence and
// out while a note sounds; the pictures come from the assets, so only an interactive preview shows them.

@Preview(name = "29c1 In the room · silence, the light on", widthDp = 412, heightDp = 788)
@Composable
private fun RoomSilencePreview() = LivePreview(LiveSignal.Silence, venue = Venue.Home)

@Preview(name = "29c2 In the room · in tune, the light out", widthDp = 412, heightDp = 788)
@Composable
private fun RoomInTunePreview() = LivePreview(
    heard(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.3, level = 0.5f),
    venue = Venue.Home,
)

@Preview(name = "29d Vienna from the stage · silence", widthDp = 412, heightDp = 788)
@Composable
private fun HallSilencePreview() = LivePreview(LiveSignal.Silence, venue = Venue.Hall("vienna"))

@Preview(name = "29d Paris from the stage · off, the light out", widthDp = 412, heightDp = 788)
@Composable
private fun HallOffPreview() = LivePreview(
    heard(Note(D4), cents = -27.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    venue = Venue.Hall("paris"),
)

@Preview(name = "29e In a hall · tuning, string D locked", widthDp = 412, heightDp = 788)
@Composable
private fun HallTuningPreview() = LivePreview(
    heard(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    lockedString = ViolinString.D4,
    venue = Venue.Hall("vienna"),
)

@Preview(name = "29g Landscape in the room · in tune", widthDp = 892, heightDp = 412)
@Composable
private fun RoomLandscapePreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 1.0, level = 0.7f),
    venue = Venue.Home,
)

// The bookmark of blocks by the record key (spec 3.28, handoff 30b, 30c): five states, in play, tuning, recording, landscape.

private const val CONCERTO = "Концерт ля минор, I ч."

@Preview(name = "30b1 «Что играю» · no practice: glass, «выбрать»", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkEntryPreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "30b3 «Что играю» · a block runs: paper, «ещё 7 мин», the brass line", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkRunningPreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f))

@Preview(name = "30b4 «Что играю» · the last minute", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkLastMinutePreview() = LivePreview(LiveSignal.Silence, practiceMs = 40 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 1, progress = 0.95f))

@Preview(name = "30b5 «Что играю» · done: the brass rim, the check in ink, «готово»", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkDonePreview() = LivePreview(LiveSignal.Silence, practiceMs = 41 * 60_000L, bookmark = Bookmark.Done(CONCERTO))

@Preview(name = "30c2 «Что играю» · done while a note sounds: whole, the rest dimmed", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkDoneInPlayPreview() = LivePreview(
    heard(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.4, level = 0.5f),
    practiceMs = 41 * 60_000L,
    bookmark = Bookmark.Done(CONCERTO),
)

@Preview(name = "30c3 «Что играю» · recording", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkRecordingPreview() = LivePreview(
    LiveSignal.Silence,
    recording = RecordingState(elapsedMs = 84_000),
    practiceMs = 34 * 60_000L,
    bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f),
)

@Preview(name = "30c4 «Что играю» · tuning", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkTuningPreview() = LivePreview(
    LiveSignal.Silence,
    mode = LiveMode.TUNING,
    practiceMs = 34 * 60_000L,
    bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f),
)

@Preview(name = "30c7 «Что играю» · the longest name ends in «…»", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkLongNamePreview() = LivePreview(
    LiveSignal.Silence,
    practiceMs = 34 * 60_000L,
    bookmark = Bookmark.Running("Концерт ми минор, соч. 64, I. Allegro molto appassionato", minutesLeft = 12, progress = 0.4f),
)

@Preview(name = "30c8 «Что играю» · 360 x 640: both cards 124", widthDp = 360, heightDp = 576)
@Composable
private fun BookmarkSmallPreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f))

@Preview(name = "30c9 «Что играю» · landscape: both cards 150", widthDp = 892, heightDp = 412)
@Composable
private fun BookmarkLandscapePreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f))


// Stage 115 (spec 3.36.6): the pause of a take, the card «нет разрешения» in every window, the cards in long languages and at a large
// font, and the tab bar dimmed with the light behind its switch.

@Preview(name = "Recording · pause: «Играйте…» dimmed, the light stays out until «стоп»", widthDp = 412, heightDp = 788)
@Composable
private fun RecordingPausePreview() = LivePreview(LiveSignal.Silence, recording = RecordingState(elapsedMs = 91_000), ribbon = SampleRibbon)

@Preview(name = "Recording · 360 x 640: the ring gives the strip 58 (230 → 172)", widthDp = 360, heightDp = 506)
@Composable
private fun RecordingSmallPreview() = LivePreview(
    heard(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.4),
    recording = SampleRecording,
    ribbon = SampleRibbon,
)

@Preview(name = "NoMicPermission · 360 x 640: the plate of the icon gone, the whole text", widthDp = 360, heightDp = 506)
@Composable
private fun NoMicSmallPreview() = LivePreview(LiveSignal.NoMicPermission)

@Preview(name = "NoMicPermission · 360 x 640, tuning: the card over the scale, the title at 18 on one line, a whole line of the text", widthDp = 360, heightDp = 506)
@Composable
private fun NoMicSmallTuningPreview() = LivePreview(LiveSignal.NoMicPermission, mode = LiveMode.TUNING)

@Preview(name = "NoMicPermission · landscape 603 x 336 (640 x 360 with a cutout): the plate gone, the text scrolls in whole lines", widthDp = 603, heightDp = 336)
@Composable
private fun NoMicLowLandscapePreview() = LivePreview(LiveSignal.NoMicPermission, mode = LiveMode.TUNING)

@Preview(name = "NoMicPermission · 360 x 640 at the font 1.5, tuning: the title and the button seen, the text heard with the title", widthDp = 360, heightDp = 506, fontScale = 1.5f, locale = "ru")
@Composable
private fun NoMicLargeFontPreview() = LivePreview(LiveSignal.NoMicPermission, mode = LiveMode.TUNING)

@Preview(name = "Play · 360 x 640 at the font 1.5: the cards step down and wrap, no word broken", widthDp = 360, heightDp = 576, fontScale = 1.5f, locale = "ru")
@Composable
private fun LargeFontCardsPreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "de · 360 x 640 at the font 1.3: «Was ich spiele · wählen», «Starten · Üben»", widthDp = 360, heightDp = 576, fontScale = 1.3f, locale = "de")
@Composable
private fun GermanCardsPreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "de · 320 x 500 at the font 1.5, a block and a practice run", widthDp = 320, heightDp = 500, fontScale = 1.5f, locale = "de")
@Composable
private fun GermanRunningCardsPreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f))

@Preview(name = "fr · 360 x 640 at the font 1.3: «Ce que je joue · choisir», «Lancer · séance»", widthDp = 360, heightDp = 576, fontScale = 1.3f, locale = "fr")
@Composable
private fun FrenchCardsPreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "fr · 320 x 500 at the font 1.5, a block and a practice run", widthDp = 320, heightDp = 500, fontScale = 1.5f, locale = "fr")
@Composable
private fun FrenchRunningCardsPreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f))

@Preview(name = "pt · 360 x 640 at the font 1.3", widthDp = 360, heightDp = 576, fontScale = 1.3f, locale = "pt")
@Composable
private fun PortugueseCardsPreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "pt · 320 x 500 at the font 1.5, a block and a practice run", widthDp = 320, heightDp = 500, fontScale = 1.5f, locale = "pt")
@Composable
private fun PortugueseRunningCardsPreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f))

@Preview(name = "es · 360 x 640 at the font 1.3", widthDp = 360, heightDp = 576, fontScale = 1.3f, locale = "es")
@Composable
private fun SpanishCardsPreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "es · 320 x 500 at the font 1.5, a block and a practice run", widthDp = 320, heightDp = 500, fontScale = 1.5f, locale = "es")
@Composable
private fun SpanishRunningCardsPreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 12, progress = 0.4f))

/** A lifecycle at rest in RESUMED: Live lends the tab bar its light only while resumed. */
private object Resumed : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

@Preview(name = "Live + tab bar, dimming ON (the switch is off in the app): the items at 0.38 with the light, the ground whole", widthDp = 412, heightDp = 850)
@Composable
private fun TabBarDimmedPreview() {
    val light = remember { TabBarLight() }
    val heardNote = heard(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.4, level = 0.5f)
    val config = IntonationConfig()
    val target = LiveTarget(LiveMode.PLAY, null)
    ViolinTheme {
        CompositionLocalProvider(
            LocalHomeLook provides HomeState.EMPTY.copy(loaded = true),
            LocalTabBarLight provides light,
            LocalLifecycleOwner provides Resumed,
        ) {
            Column {
                LiveScreen(
                    state = LiveReducer.stateOf(target, config, heardNote.signal, practiceMs = PRACTICE_MS),
                    onIntent = {},
                    modifier = Modifier.weight(1f),
                    gauge = { heardNote.gauge },
                    dimTabBar = true,
                )
                AppBottomBar(current = TopLevelDestination.LIVE, onSelect = {}, practiceRunning = true, dimmed = light::alpha)
            }
        }
    }
}
