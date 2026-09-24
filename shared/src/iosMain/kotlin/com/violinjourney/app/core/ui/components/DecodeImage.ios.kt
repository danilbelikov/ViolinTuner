package com.violinjourney.app.core.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageFile(path: String): ImageBitmap? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: IllegalArgumentException) {
        null // not a picture: what BitmapFactory answers with null on Android
    }
}

/** Skia decodes the whole picture: one decode at full size, never again. */
actual fun decodeImageFileSampled(path: String, wantedWidthPx: Int, loadedSample: Int): Pair<ImageBitmap, Int>? =
    if (loadedSample <= FULL_SIZE) null else decodeImageFile(path)?.let { it to FULL_SIZE }

private const val FULL_SIZE = 1
