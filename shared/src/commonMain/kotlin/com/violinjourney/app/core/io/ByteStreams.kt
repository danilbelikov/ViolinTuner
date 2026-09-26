package com.violinjourney.app.core.io

/** Bytes read one after another: `java.io.InputStream` on Android, a stream of the app's own on iOS. */
expect abstract class ByteInput : AutoCloseable {
    override fun close()
}

/** Bytes written one after another: `java.io.OutputStream` on Android. */
expect abstract class ByteOutput : AutoCloseable {
    override fun close()
}

/** Up to [count] bytes into [buffer] from [offset]; how many, or -1 at the end. */
expect fun ByteInput.readBytes(buffer: ByteArray, offset: Int = 0, count: Int = buffer.size - offset): Int

expect fun ByteOutput.writeBytes(buffer: ByteArray, offset: Int = 0, count: Int = buffer.size - offset)

/** Opens the file for reading; null when it is not there. */
expect fun PlatformFile.openInput(): ByteInput?

/** Opens the file for writing from its start, making it if need be; null when it cannot. */
expect fun PlatformFile.openOutput(): ByteOutput?

/**
 * What has been written is on the storage itself, not only in the system's cache: a sudden loss of power after this
 * leaves it whole. A stream with no file of this phone under it — a pipe to a cloud provider — has nothing here to sync
 * and passes; a disk that does not take the bytes throws.
 */
expect fun ByteOutput.syncToDisk()
