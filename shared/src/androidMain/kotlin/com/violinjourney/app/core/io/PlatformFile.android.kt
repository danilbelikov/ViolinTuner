package com.violinjourney.app.core.io

actual typealias PlatformFile = java.io.File

actual val PlatformFile.filePath: String get() = path

actual fun PlatformFile.sizeBytes(): Long = length()
