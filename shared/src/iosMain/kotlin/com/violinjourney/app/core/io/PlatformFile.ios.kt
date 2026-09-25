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

actual fun platformFile(path: String): PlatformFile = PlatformFile(path)

actual val PlatformFile.fileUri: String get() = platform.Foundation.NSURL.fileURLWithPath(path).absoluteString.orEmpty()

actual fun PlatformFile.sibling(name: String): PlatformFile = PlatformFile("${path.substringBeforeLast('/')}/$name")

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformFile.moveTo(target: PlatformFile): Boolean = NSFileManager.defaultManager.moveItemAtPath(path, target.path, null)

actual fun PlatformFile.exists(): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

actual fun PlatformFile.child(name: String): PlatformFile = PlatformFile("$path/$name")

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformFile.makeDirectories(): Boolean =
    NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformFile.listNames(): List<String> =
    NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null)?.mapNotNull { it as? String }.orEmpty()

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformFile.deleteAll(): Boolean =
    NSFileManager.defaultManager.removeItemAtPath(path, null) || !NSFileManager.defaultManager.fileExistsAtPath(path)
