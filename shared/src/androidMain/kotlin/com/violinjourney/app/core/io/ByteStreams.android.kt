package com.violinjourney.app.core.io

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.SyncFailedException

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

actual fun ByteOutput.syncToDisk() {
    flush()
    // a document provider hands out a FileOutputStream too (ParcelFileDescriptor.AutoCloseOutputStream), over a file or a pipe
    val file = this as? FileOutputStream ?: return
    try {
        file.fd.sync()
    } catch (e: SyncFailedException) {
        // FileDescriptor.sync says «sync failed» and nothing more; fsync(2) asked once more says why. Only a descriptor
        // with nothing to sync answers the same again — a pipe or a socket, EINVAL or EROFS: a cloud provider's pipe has no
        // file on this phone, and the bytes it took are the provider's to keep. After a disk error a second fsync may pass,
        // and the first answer still counts: any other outcome is a disk that did not take the bytes.
        val why = try {
            Os.fsync(file.fd)
            null
        } catch (errno: ErrnoException) {
            errno
        }
        if (why == null || (why.errno != OsConstants.EINVAL && why.errno != OsConstants.EROFS)) {
            if (why != null && e.cause == null) e.initCause(why)
            throw e
        }
        Log.w(TAG, "nothing to sync under this stream: ${OsConstants.errnoName(why.errno)}")
    }
}

private const val TAG = "ByteStreams"
