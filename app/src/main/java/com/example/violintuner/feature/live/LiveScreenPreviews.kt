package com.example.violintuner.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.ui.theme.ViolinTheme

// One preview per row of the state table in spec 3.4, mirroring handoff frames 8a–8f
// (the area above the navigation bar of the 412 × 892 base screen).

private const val A4 = 69
private const val D4 = 62
private const val F_SHARP_5 = 78

@Composable
private fun LivePreview(signal: LiveSignal, mode: LiveMode = LiveMode.PLAY) {
    val config = IntonationConfig()
    ViolinTheme {
        LiveScreen(
            state = LiveState(
                mode = mode,
                signal = signal,
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

@Preview(name = "NoMicPermission", widthDp = 412, heightDp = 788)
@Composable
private fun NoMicPermissionPreview() = LivePreview(LiveSignal.NoMicPermission)
