package com.violinjourney.app.core.io

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey
import platform.posix.remove

/** A path in the app's sandbox. */
actual class PlatformFile(val path: String)

actual val PlatformFile.filePath: String get() = path

@OptIn(ExperimentalForeignApi::class)
actual fun PlatformFile.sizeBytes(): Long =
    (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0

actual val PlatformFile.fileName: String get() = path.substringAfterLast('/')

/**
 * A file, or an empty folder — never a folder with things in it, as `java.io.File.delete` on Android: `removeItemAtPath`
 * would take a whole folder, and a wrong name would cost everything in it. [deleteAll] is for folders.
 */
actual fun PlatformFile.deleteFile(): Boolean = remove(path) == 0 || !exists()

/** A file in the plain sense — there, and not a folder. */
@OptIn(ExperimentalForeignApi::class)
internal fun PlatformFile.isRegularFile(): Boolean = memScoped {
    val folder = alloc<BooleanVar>()
    NSFileManager.defaultManager.fileExistsAtPath(path, isDirectory = folder.ptr) && !folder.value
}

/**
 * The room for something the person asked to keep — a copy brought back, a video, a backing: what iOS would give such a
 * write, the space it frees on demand (caches, what iCloud can fetch again) included. `NSFileSystemFreeSize` leaves that
 * out, and a nearly full iPhone said «не хватает» where the system's settings showed gigabytes. The larger of the two:
 * some volumes answer the first with nothing. Blocking, and not on the main thread: the system works out what it could
 * free, which is slower than counting free blocks.
 */
internal fun PlatformFile.availableBytes(): Long = roomOf(importantUsageBytes(), freeSystemBytes())

/** [availableBytes] of two readings: what the system would give a wanted write, never less than the free blocks. */
internal fun roomOf(importantUsage: Long?, freeBlocks: Long): Long = maxOf(importantUsage ?: 0L, freeBlocks)

/** `volumeAvailableCapacityForImportantUsage` of the volume this lies on; null when the system does not say. */
@OptIn(ExperimentalForeignApi::class)
internal fun PlatformFile.importantUsageBytes(): Long? {
    val key = NSURLVolumeAvailableCapacityForImportantUsageKey
    return (NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(key), null)?.get(key) as? NSNumber)?.longLongValue
}

/** `NSFileSystemFreeSize`: free blocks alone, without what the system would free. */
@OptIn(ExperimentalForeignApi::class)
internal fun PlatformFile.freeSystemBytes(): Long =
    (NSFileManager.defaultManager.attributesOfFileSystemForPath(path, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0

actual fun platformFile(path: String): PlatformFile = PlatformFile(path)

actual val PlatformFile.fileUri: String get() = NSURL.fileURLWithPath(path).absoluteString.orEmpty()

/**
 * The path of a `file:` URI as [fileUri] writes it, its escapes undone: the data of the app lies in «Application Support»,
 * and its URI says `Application%20Support`, which is no path. A bare absolute path (the photo picker hands one over) is
 * returned as it is; anything else — a URI of another scheme, a relative name — is null. An importer of a URI on iOS undoes
 * its escapes (through this, or `NSURL.path` itself) and never cuts `file://` off.
 */
internal fun pathOfFileUri(uri: String): String? = when {
    uri.startsWith(FILE_SCHEME) -> NSURL.URLWithString(uri)?.path?.takeIf { it.startsWith('/') }
    uri.startsWith('/') -> uri
    else -> null
}

private const val FILE_SCHEME = "file:"

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
