package com.violinjourney.app.feature.journey.art

import androidx.compose.runtime.Composable

/**
 * Debug switches of the living pictures — the check that baking changes nothing seen (docs/plan-performance.md,
 * tools/perf/snap.py), and still screenshots on iOS. Read when a picture comes on the screen.
 */
object SceneDebug {
    /** Set by the app at its start: only a debug build listens to the switches. */
    var debugBuild: Boolean = false
}

/** Every living picture stops at this second (and still ticks); null — time runs. */
@Composable
expect fun sceneFrozenSeconds(): Float?

/** The living pictures are drawn without baking. */
@Composable
expect fun sceneNoBake(): Boolean
