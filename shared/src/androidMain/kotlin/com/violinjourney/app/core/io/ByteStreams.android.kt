package com.violinjourney.app.core.io

import java.io.FileNotFoundException

actual typealias ByteInput = java.io.InputStream

actual typealias ByteOutput = java.io.OutputStream

actual fun ByteInput.readBytes(buffer: ByteArray, offset: Int, count: Int): Int = read(buffer, offset, count)

actual fun ByteOutput.writeBytes(buffer: ByteArray, offset: Int, count: Int) = write(buffer, offset, count)

actual fun PlatformFile.openInput(): ByteInput? = try {
    inputStream()
} catch (_: FileNotFoundException) {
    null // gone since it was listed: the caller skips it
}

actual fun PlatformFile.openOutput(): ByteOutput? = try {
    outputStream()
} catch (_: FileNotFoundException) {
    null
}
