package com.violinjourney.app.feature.journey.art

import androidx.compose.runtime.Composable
import platform.Foundation.NSProcessInfo

// xcrun simctl launch booted com.violinjourney.app.debug -sceneSeconds 12.5 [-noBake]
private fun argument(name: String): String? {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val index = arguments.indexOf(name)
    return if (index < 0) null else arguments.getOrNull(index + 1) ?: ""
}

@Composable
actual fun sceneFrozenSeconds(): Float? = if (SceneDebug.debugBuild) argument("-sceneSeconds")?.toFloatOrNull() else null

@Composable
actual fun sceneNoBake(): Boolean = SceneDebug.debugBuild && argument("-noBake") != null
