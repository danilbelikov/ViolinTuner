package com.violinjourney.app.core.ui.components

import androidx.compose.ui.graphics.Paint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

// Android turns a blur radius into Skia's sigma the same way: sigma = radius · 0.57735 + 0.5
private const val SIGMA_PER_RADIUS = 0.57735f
private const val SIGMA_BIAS = 0.5f

actual fun Paint.blurMask(radius: Float) {
    asFrameworkPaint().maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, radius * SIGMA_PER_RADIUS + SIGMA_BIAS)
}
