package com.violinjourney.app.feature.live.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * True on the Live of `-PplainLive=true`: no picture behind it, the plain dark field with the gradient of the zone (spec 5.20). The
 * layout provides it; the controls read it through [liveGlassGround].
 */
val LocalLivePlain = staticCompositionLocalOf { false }

/**
 * The ground of a control of Live (spec 5.29 R6): null — the smoked glass over the picture; on the plain Live — surfaceContainer,
 * because the glass (#131318 at 0.72) melts into the dark field there. For `Modifier.glass(ground = …)`.
 */
@Composable
@ReadOnlyComposable
fun liveGlassGround(): Color? = if (LocalLivePlain.current) MaterialTheme.colorScheme.surfaceContainer else null
