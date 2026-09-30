package com.violinjourney.app.core.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap

actual fun ImageBitmap.seal(): ImageBitmap = apply { asSkiaBitmap().setImmutable() }
