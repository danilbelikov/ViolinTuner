package com.violinjourney.app.core.io

actual typealias PlatformFile = java.io.File

actual val PlatformFile.filePath: String get() = path

actual fun PlatformFile.sizeBytes(): Long = length()

actual val PlatformFile.fileName: String get() = name

actual fun PlatformFile.deleteFile(): Boolean = delete()

actual fun platformFile(path: String): PlatformFile = java.io.File(path)

actual val PlatformFile.fileUri: String get() = toURI().toString()

actual fun PlatformFile.sibling(name: String): PlatformFile = java.io.File(parentFile, name)

actual fun PlatformFile.moveTo(target: PlatformFile): Boolean = renameTo(target)

actual fun PlatformFile.exists(): Boolean = exists()

actual fun PlatformFile.child(name: String): PlatformFile = java.io.File(this, name)

actual fun PlatformFile.makeDirectories(): Boolean = mkdirs() || isDirectory

actual fun PlatformFile.listNames(): List<String> = list()?.toList().orEmpty()

actual fun PlatformFile.deleteAll(): Boolean = deleteRecursively()
