package com.violinjourney.app.feature.journey.art

import androidx.compose.runtime.Composable
import platform.Foundation.NSProcessInfo

// xcrun simctl launch booted com.violinjourney.app.debug -sceneSeconds 12.5 [-noBake]
// the arguments of the process never change: read once, not on every composition that asks
private val arguments: List<String> by lazy { NSProcessInfo.processInfo.arguments.map { it.toString() } }

private fun argument(name: String): String? {
    val index = arguments.indexOf(name)
    return if (index < 0) null else arguments.getOrNull(index + 1) ?: ""
}

@Composable
actual fun sceneFrozenSeconds(): Float? = if (SceneDebug.debugBuild) argument("-sceneSeconds")?.toFloatOrNull() else null

@Composable
actual fun sceneNoBake(): Boolean = SceneDebug.debugBuild && argument("-noBake") != null
