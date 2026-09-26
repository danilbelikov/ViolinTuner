package com.violinjourney.app.feature.journey.art

import androidx.compose.ui.graphics.layer.CompositingStrategy

// a hardware layer: HWUI keeps the texture between frames and applies the colour filter as it lays it down
internal actual val keptLayerStrategy: CompositingStrategy = CompositingStrategy.Offscreen

internal actual val stillImages: StillImages? = null
