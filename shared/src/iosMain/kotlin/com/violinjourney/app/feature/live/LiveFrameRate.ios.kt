package com.violinjourney.app.feature.live

import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import com.violinjourney.app.feature.live.components.LiveMotion

internal actual fun Modifier.liveFrameRate(): Modifier = preferredFrameRate(LiveMotion.IOS_FRAMES_PER_SECOND)
