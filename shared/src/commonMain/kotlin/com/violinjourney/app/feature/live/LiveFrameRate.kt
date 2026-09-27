package com.violinjourney.app.feature.live

import androidx.compose.ui.Modifier

/**
 * The frame rate Live asks for, over every layer of it. On iOS the ProMotion screen would draw it up to 120 times a
 * second (the Info.plist lets the other screens scroll at that), while its glow, breath, waves, light and marker are slow:
 * 60 looks the same and costs half, and a phone on the stand stays cooler. Android draws as it did.
 */
internal expect fun Modifier.liveFrameRate(): Modifier
