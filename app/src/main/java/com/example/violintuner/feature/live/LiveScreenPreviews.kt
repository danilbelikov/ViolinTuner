package com.example.violintuner.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.home.HomeState
import com.example.violintuner.core.domain.session.RecordingBar
import com.example.violintuner.core.domain.venue.Venue
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.journey.LocalHomeLook
import com.example.violintuner.feature.live.block.BlockState
import com.example.violintuner.feature.live.block.Bookmark

// One preview per row of the state table in spec 3.4, mirroring handoff frames 8a–8f
// (the area above the navigation bar of the 412 × 892 base screen).

private const val A4 = 69
private const val D4 = 62
private const val F_SHARP_5 = 78

@Composable
private fun LivePreview(
    signal: LiveSignal,
    mode: LiveMode = LiveMode.PLAY,
    lockedString: ViolinString? = null,
    recording: RecordingState? = null,
    practiceMs: Long? = null,
    reduceMotion: Boolean = false,
    venue: Venue? = null,
    bookmark: Bookmark = Bookmark.Entry,
) {
    val config = IntonationConfig()
    val target = LiveTarget(mode, lockedString)
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
                glowTarget = LiveReducer.glowTargetOf(signal, config),
                glowStep = LiveReducer.glowTargetOf(signal, config, stepped = true),
                statusLine = LiveReducer.statusLineOf(target, signal),
                practiceMs = practiceMs,
                venue = venue,
            ),
            onIntent = {},
            reduceMotion = reduceMotion,
            block = BlockState(bookmark, sheet = null),
        )
        }
    }
}

@Preview(name = "12a2 InTune · A4, just hit: glow .6", widthDp = 412, heightDp = 788)
@Composable
private fun InTuneFreshPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.0, level = 0.5f),
)

@Preview(name = "InTune · A4, held 70 %: glow .88", widthDp = 412, heightDp = 788)
@Composable
private fun InTunePreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7, level = 0.6f),
)

@Preview(name = "12a3 InTune · A4, held 2 s: glow 1", widthDp = 412, heightDp = 788)
@Composable
private fun InTuneHeldPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 1.0, level = 0.8f),
)

@Preview(name = "12a9 animations removed: the step of the zone, no breath", widthDp = 412, heightDp = 788)
@Composable
private fun ReducedMotionPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7, level = 0.9f),
    reduceMotion = true,
)

@Preview(name = "Sharp · F#5, near", widthDp = 412, heightDp = 788)
@Composable
private fun SharpNearPreview() = LivePreview(
    LiveSignal.Sounding(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
)

@Preview(name = "Flat · D4, off", widthDp = 412, heightDp = 788)
@Composable
private fun FlatOffPreview() = LivePreview(
    LiveSignal.Sounding(Note(D4), cents = -27.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
)

@Preview(name = "Silence", widthDp = 412, heightDp = 788)
@Composable
private fun SilencePreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "TooNoisy", widthDp = 412, heightDp = 788)
@Composable
private fun TooNoisyPreview() = LivePreview(LiveSignal.TooNoisy)

@Preview(name = "MicUnavailable", widthDp = 412, heightDp = 788)
@Composable
private fun MicUnavailablePreview() = LivePreview(LiveSignal.MicUnavailable)

@Preview(name = "NoMicPermission", widthDp = 412, heightDp = 788)
@Composable
private fun NoMicPermissionPreview() = LivePreview(LiveSignal.NoMicPermission)

// Tuning mode, handoff frames 9a and 9b.

@Preview(name = "Tuning · auto, nearest string A", widthDp = 412, heightDp = 788)
@Composable
private fun TuningAutoPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 1.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.15),
    mode = LiveMode.TUNING,
)

@Preview(name = "Tuning · string D locked", widthDp = 412, heightDp = 788)
@Composable
private fun TuningLockedPreview() = LivePreview(
    LiveSignal.Sounding(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
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
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
)

@Preview(name = "Landscape · Silence", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapeSilencePreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "Landscape · NoMicPermission", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapeNoMicPreview() = LivePreview(LiveSignal.NoMicPermission)

@Preview(name = "Landscape · Tuning, D locked", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapeTuningPreview() = LivePreview(
    LiveSignal.Sounding(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    lockedString = ViolinString.D4,
)

@Preview(name = "Landscape · low 640 x 336, tuning", widthDp = 640, heightDp = 336)
@Composable
private fun LandscapeLowPreview() = LivePreview(
    LiveSignal.Sounding(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
    mode = LiveMode.TUNING,
)

@Preview(name = "Small phone 320 x 500", widthDp = 320, heightDp = 500)
@Composable
private fun SmallPhonePreview() = LivePreview(
    LiveSignal.Sounding(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
)

@Preview(name = "Large system font", widthDp = 412, heightDp = 788, fontScale = 1.5f)
@Composable
private fun LargeFontPreview() = LivePreview(
    LiveSignal.Sounding(Note(F_SHARP_5), cents = 14.0, zone = Zone.NEAR, direction = Direction.SHARP, holdProgress = 0.0),
    mode = LiveMode.TUNING,
)

// Recording (spec 3.9): red stop button, strip with timer and mini bar, switcher dimmed.

private val SampleRecording = RecordingState(
    elapsedMs = 47_000,
    bars = listOf(
        RecordingBar(0.20f, Zone.IN_TUNE), RecordingBar(0.08f, Zone.NEAR), RecordingBar(0.15f, Zone.IN_TUNE),
        RecordingBar(0.05f, Zone.OFF), RecordingBar(0.22f, Zone.IN_TUNE), RecordingBar(0.10f, Zone.NEAR),
    ),
)

@Preview(name = "Recording · portrait", widthDp = 412, heightDp = 788)
@Composable
private fun RecordingPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.4),
    recording = SampleRecording,
)

@Preview(name = "Recording · landscape", widthDp = 892, heightDp = 412)
@Composable
private fun RecordingLandscapePreview() = LivePreview(
    LiveSignal.Sounding(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    recording = SampleRecording,
)

// A practice is running, handoff frames 10h1–10h3 and 10h-land.

private const val PRACTICE_MS = 754_000L

@Preview(name = "Practice chip · play", widthDp = 412, heightDp = 788)
@Composable
private fun PracticeChipPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
    practiceMs = PRACTICE_MS,
)

@Preview(name = "Practice chip · recording", widthDp = 412, heightDp = 788)
@Composable
private fun PracticeChipRecordingPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
    recording = RecordingState(elapsedMs = 84_000, bars = listOf(RecordingBar(0.5f, Zone.IN_TUNE), RecordingBar(0.2f, Zone.NEAR))),
    practiceMs = PRACTICE_MS,
)

@Preview(name = "Practice chip · tuning", widthDp = 412, heightDp = 788)
@Composable
private fun PracticeChipTuningPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
    mode = LiveMode.TUNING,
    practiceMs = PRACTICE_MS,
)

@Preview(name = "Practice chip · landscape", widthDp = 892, heightDp = 412)
@Composable
private fun PracticeChipLandscapePreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
    practiceMs = PRACTICE_MS,
)

@Preview(name = "12e1 Tuning · D locked, silence", widthDp = 412, heightDp = 788)
@Composable
private fun TuningLockedSilencePreview() =
    LivePreview(LiveSignal.Silence, mode = LiveMode.TUNING, lockedString = ViolinString.D4)

@Preview(name = "12e4 Tuning · too noisy", widthDp = 412, heightDp = 788)
@Composable
private fun TuningNoisyPreview() = LivePreview(LiveSignal.TooNoisy, mode = LiveMode.TUNING)

@Preview(name = "12g1 small screen 360x640", widthDp = 360, heightDp = 576)
@Composable
private fun SmallScreenPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 1.0, level = 0.6f),
)

// In the room and in the halls (spec 3.27, handoff venue 29c, 29d): the light is on in silence and
// out while a note sounds; the pictures come from the assets, so only an interactive preview shows them.

@Preview(name = "29c1 In the room · silence, the light on", widthDp = 412, heightDp = 788)
@Composable
private fun RoomSilencePreview() = LivePreview(LiveSignal.Silence, venue = Venue.Home)

@Preview(name = "29c2 In the room · in tune, the light out", widthDp = 412, heightDp = 788)
@Composable
private fun RoomInTunePreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.3, level = 0.5f),
    venue = Venue.Home,
)

@Preview(name = "29d Vienna from the stage · silence", widthDp = 412, heightDp = 788)
@Composable
private fun HallSilencePreview() = LivePreview(LiveSignal.Silence, venue = Venue.Hall("vienna"))

@Preview(name = "29d Paris from the stage · off, the light out", widthDp = 412, heightDp = 788)
@Composable
private fun HallOffPreview() = LivePreview(
    LiveSignal.Sounding(Note(D4), cents = -27.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    venue = Venue.Hall("paris"),
)

@Preview(name = "29e In a hall · tuning, string D locked", widthDp = 412, heightDp = 788)
@Composable
private fun HallTuningPreview() = LivePreview(
    LiveSignal.Sounding(Note(D4), cents = -24.0, zone = Zone.OFF, direction = Direction.FLAT, holdProgress = 0.0),
    mode = LiveMode.TUNING,
    lockedString = ViolinString.D4,
    venue = Venue.Hall("vienna"),
)

@Preview(name = "29g Landscape in the room · in tune", widthDp = 892, heightDp = 412)
@Composable
private fun RoomLandscapePreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 1.0, level = 0.7f),
    venue = Venue.Home,
)

// The bookmark of blocks by the record key (spec 3.28, handoff 30b, 30c): five states, in play, tuning, recording, landscape.

private const val CONCERTO = "Концерт ля минор, I ч."

@Preview(name = "30b1 Bookmark · no practice: «Репертуар»", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkEntryPreview() = LivePreview(LiveSignal.Silence)

@Preview(name = "30b3 Bookmark · running, 7 min left", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkRunningPreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f))

@Preview(name = "30b4 Bookmark · the last minute", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkLastMinutePreview() = LivePreview(LiveSignal.Silence, practiceMs = 40 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 1, progress = 0.95f))

@Preview(name = "30b5 Bookmark · done: the rim closed, «готово»", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkDonePreview() = LivePreview(LiveSignal.Silence, practiceMs = 41 * 60_000L, bookmark = Bookmark.Done(CONCERTO))

@Preview(name = "30c2 Bookmark · done while a note sounds (stays whole)", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkDoneInPlayPreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 3.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.4, level = 0.5f),
    practiceMs = 41 * 60_000L,
    bookmark = Bookmark.Done(CONCERTO),
)

@Preview(name = "30c3 Bookmark · recording", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkRecordingPreview() = LivePreview(
    LiveSignal.Silence,
    recording = RecordingState(elapsedMs = 84_000, bars = emptyList()),
    practiceMs = 34 * 60_000L,
    bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f),
)

@Preview(name = "30c4 Bookmark · tuning", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkTuningPreview() = LivePreview(
    LiveSignal.Silence,
    mode = LiveMode.TUNING,
    practiceMs = 34 * 60_000L,
    bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f),
)

@Preview(name = "30c7 Bookmark · the longest name", widthDp = 412, heightDp = 788)
@Composable
private fun BookmarkLongNamePreview() = LivePreview(
    LiveSignal.Silence,
    practiceMs = 34 * 60_000L,
    bookmark = Bookmark.Running("Концерт ми минор, соч. 64, I. Allegro molto appassionato", minutesLeft = 12, progress = 0.4f),
)

@Preview(name = "30c8 Bookmark · 360 x 640", widthDp = 360, heightDp = 576)
@Composable
private fun BookmarkSmallPreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f))

@Preview(name = "30c9 Bookmark · landscape", widthDp = 892, heightDp = 412)
@Composable
private fun BookmarkLandscapePreview() = LivePreview(LiveSignal.Silence, practiceMs = 34 * 60_000L, bookmark = Bookmark.Running(CONCERTO, minutesLeft = 7, progress = 0.65f))

