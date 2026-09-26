package com.violinjourney.app.core.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.ECONNRESET
import platform.posix.EDQUOT
import platform.posix.EHOSTDOWN
import platform.posix.EHOSTUNREACH
import platform.posix.EINTR
import platform.posix.EIO
import platform.posix.ENETDOWN
import platform.posix.ENETUNREACH
import platform.posix.ENODEV
import platform.posix.ENOENT
import platform.posix.ENOSPC
import platform.posix.ENOTCONN
import platform.posix.ENXIO
import platform.posix.EPIPE
import platform.posix.ESTALE
import platform.posix.ETIMEDOUT
import platform.posix.O_CREAT
import platform.posix.O_EXCL
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.open
import platform.posix.read
import platform.posix.strerror
import platform.posix.write
import kotlinx.cinterop.toKString

/** Bytes of a file of the app, read by the system calls themselves: no buffering here, callers read in large pieces. */
actual abstract class ByteInput : AutoCloseable {
    abstract fun read(buffer: ByteArray, offset: Int, count: Int): Int

    actual override fun close() = Unit
}

actual abstract class ByteOutput : AutoCloseable {
    abstract fun write(buffer: ByteArray, offset: Int, count: Int)

    actual override fun close() = Unit
}

actual fun ByteInput.readBytes(buffer: ByteArray, offset: Int, count: Int): Int = read(buffer, offset, count)

actual fun ByteOutput.writeBytes(buffer: ByteArray, offset: Int, count: Int) = write(buffer, offset, count)

actual fun PlatformFile.openInput(): ByteInput? = FileInput.open(path)

actual fun PlatformFile.openOutput(): ByteOutput? = FileOutput.open(path)

actual fun ByteOutput.syncToDisk() {
    (this as? FileOutput)?.sync()
}

/**
 * A file that is not there yet, made and opened for writing in one step; null when a file of that name is there already.
 * What somebody else put in a place is never emptied by it — [openOutput] would cut such a file to nothing.
 */
internal fun PlatformFile.createNewOutput(): ByteOutput? = FileOutput.create(path)

/**
 * What a refusal of the file system means for a copy (spec 3.20): no room — on the disk or over a quota; the place gone —
 * a disk pulled out, a folder of a server that no longer answers; anything else is a failure of its own.
 */
internal fun storageFailureOf(code: Int): StorageFailure = when (code) {
    ENOSPC, EDQUOT -> StorageFailure.NO_SPACE
    ENOENT, EIO, ENODEV, ENXIO, EPIPE, ENOTCONN, ESTALE, ETIMEDOUT, ENETDOWN, ENETUNREACH, EHOSTDOWN, EHOSTUNREACH, ECONNRESET -> StorageFailure.GONE
    else -> StorageFailure.OTHER
}

/** The errno of the call that failed, read before anything else may change it. */
@OptIn(ExperimentalForeignApi::class)
private fun storageException(what: String): StorageException {
    val code = errno
    return StorageException(storageFailureOf(code), "$what failed: ${strerror(code)?.toKString()}")
}

@OptIn(ExperimentalForeignApi::class)
private class FileInput(private var fd: Int) : ByteInput() {
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (count == 0) return 0
        while (true) {
            val got = buffer.usePinned { read(fd, it.addressOf(offset), count.toULong()) }
            if (got < 0 && errno == EINTR) continue
            if (got < 0) throw storageException("read")
            return if (got == 0L) -1 else got.toInt()
        }
    }

    // once, as an InputStream closes: a descriptor closed twice may by then be another file's — the database's, maybe
    override fun close() {
        if (fd < 0) return
        close(fd)
        fd = -1
    }

    companion object {
        fun open(path: String): FileInput? = open(path, O_RDONLY).takeIf { it >= 0 }?.let(::FileInput)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FileOutput(private var fd: Int) : ByteOutput() {
    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        var done = 0
        while (done < count) {
            val put = buffer.usePinned { write(fd, it.addressOf(offset + done), (count - done).toULong()) }
            if (put < 0 && errno == EINTR) continue
            if (put < 0) throw storageException("write")
            done += put.toInt()
        }
    }

    /** Plain fsync, not F_FULLFSYNC: enough against a crash of the system or a phone that dies; the drive's cache is its own. */
    fun sync() {
        if (fsync(fd) != 0) throw storageException("fsync")
    }

    // once: a descriptor closed twice may by then be another file's — and a write after the close goes nowhere, not into it
    override fun close() {
        if (fd < 0) return
        close(fd)
        fd = -1
    }

    companion object {
        private const val MODE = 0x1A4 // rw-r--r--

        fun open(path: String): FileOutput? = open(path, O_WRONLY or O_CREAT or O_TRUNC, MODE).takeIf { it >= 0 }?.let(::FileOutput)

        fun create(path: String): FileOutput? = open(path, O_WRONLY or O_CREAT or O_EXCL, MODE).takeIf { it >= 0 }?.let(::FileOutput)
    }
}
