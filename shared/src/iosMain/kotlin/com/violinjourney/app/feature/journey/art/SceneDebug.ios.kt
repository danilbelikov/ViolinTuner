package com.violinjourney.app.feature.journey.art

import androidx.compose.runtime.Composable
import com.violinjourney.app.ios.LaunchArguments

// xcrun simctl launch booted com.violinjourney.app.debug -sceneSeconds 12.5 [-noBake]

@Composable
actual fun sceneFrozenSeconds(): Float? = if (SceneDebug.debugBuild) LaunchArguments.valueOf("-sceneSeconds")?.toFloatOrNull() else null

@Composable
actual fun sceneNoBake(): Boolean = SceneDebug.debugBuild && LaunchArguments.has("-noBake")
