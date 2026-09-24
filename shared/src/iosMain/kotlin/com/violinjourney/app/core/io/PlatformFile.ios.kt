package com.violinjourney.app.core.io

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber

/** A path in the app's sandbox. */
actual class PlatformFile(val path: String)

actual val PlatformFile.filePath: String get() = path

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformFile.sizeBytes(): Long =
    (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0

actual val PlatformFile.fileName: String get() = path.substringAfterLast('/')

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformFile.deleteFile(): Boolean =
    NSFileManager.defaultManager.removeItemAtPath(path, null) || !NSFileManager.defaultManager.fileExistsAtPath(path)
