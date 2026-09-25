package com.violinjourney.app.core.io

actual typealias PlatformFile = java.io.File

actual val PlatformFile.filePath: String get() = path

actual fun PlatformFile.sizeBytes(): Long = length()

actual val PlatformFile.fileName: String get() = name

actual fun PlatformFile.deleteFile(): Boolean = delete()

actual fun platformFile(path: String): PlatformFile = java.io.File(path)

actual val PlatformFile.fileUri: String get() = toURI().toString()
