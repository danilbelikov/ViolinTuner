package com.violinjourney.app.core.ui.components

import androidx.compose.ui.graphics.Paint

/** A soft edge of [radius] on what [this] paints — Android's BlurMaskFilter.NORMAL; Skia takes the same as a sigma. */
expect fun Paint.blurMask(radius: Float)
