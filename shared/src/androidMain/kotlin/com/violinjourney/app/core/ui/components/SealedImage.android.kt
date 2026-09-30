package com.violinjourney.app.core.ui.components

import androidx.compose.ui.graphics.ImageBitmap

// HWUI uploads a bitmap once and keeps its texture by the bitmap's generation: nothing to seal
actual fun ImageBitmap.seal(): ImageBitmap = this
