package com.violinjourney.app.feature.journey.art

import android.content.Context
import android.provider.Settings
import com.violinjourney.app.BuildConfig

/**
 * Debug builds only — the check that baking changes nothing seen (docs/plan-performance.md, tools/perf/snap.py):
 *
 *     adb shell settings put global violintuner_scene_seconds 12.5   every living picture stops at that second, and still ticks
 *     adb shell settings put global violintuner_no_bake 1            the living pictures are drawn without baking
 *
 * `settings delete global …` takes them back. Read when a picture comes on the screen.
 */
internal object SceneDebug {
    fun frozenSeconds(context: Context): Float? =
        if (BuildConfig.DEBUG) Settings.Global.getString(context.contentResolver, FROZEN)?.toFloatOrNull() else null

    fun noBake(context: Context): Boolean =
        BuildConfig.DEBUG && Settings.Global.getString(context.contentResolver, NO_BAKE) == "1"

    private const val FROZEN = "violintuner_scene_seconds"
    private const val NO_BAKE = "violintuner_no_bake"
}
