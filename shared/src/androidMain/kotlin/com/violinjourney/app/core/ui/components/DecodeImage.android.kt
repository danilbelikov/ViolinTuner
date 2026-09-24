package com.violinjourney.app.core.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageFile(path: String): ImageBitmap? = BitmapFactory.decodeFile(path)?.asImageBitmap()
