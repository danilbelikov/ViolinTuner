package com.violinjourney.app.feature.journey.art

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// adb shell settings put global violintuner_scene_seconds 12.5   every living picture stops at that second, and still ticks
// adb shell settings put global violintuner_no_bake 1            the living pictures are drawn without baking
// `settings delete global …` takes them back.
private const val FROZEN = "violintuner_scene_seconds"
private const val NO_BAKE = "violintuner_no_bake"

@Composable
actual fun sceneFrozenSeconds(): Float? {
    val resolver = LocalContext.current.contentResolver
    return if (SceneDebug.debugBuild) Settings.Global.getString(resolver, FROZEN)?.toFloatOrNull() else null
}

@Composable
actual fun sceneNoBake(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return SceneDebug.debugBuild && Settings.Global.getString(resolver, NO_BAKE) == "1"
}
