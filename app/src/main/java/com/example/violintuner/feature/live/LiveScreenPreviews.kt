package com.example.violintuner.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.ui.theme.ViolinTheme

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
) {
    val config = IntonationConfig()
    ViolinTheme {
        LiveScreen(
            state = LiveState(
                mode = mode,
                signal = signal,
                tuning = LiveReducer.tuningStateOf(LiveTarget(mode, lockedString), signal, config),
                scale = ScaleSpec(config),
                zoneCrossfadeMs = config.zoneCrossfadeMs,
            ),
            onIntent = {},
        )
    }
}

@Preview(name = "InTune · A4, ring 70 %", widthDp = 412, heightDp = 788)
@Composable
private fun InTunePreview() = LivePreview(
    LiveSignal.Sounding(Note(A4), cents = 2.0, zone = Zone.IN_TUNE, direction = null, holdProgress = 0.7),
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
