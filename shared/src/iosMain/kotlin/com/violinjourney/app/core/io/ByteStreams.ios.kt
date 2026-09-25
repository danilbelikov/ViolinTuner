package com.violinjourney.app.core.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.EINTR
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
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

@OptIn(ExperimentalForeignApi::class)
private class FileInput(private val fd: Int) : ByteInput() {
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (count == 0) return 0
        while (true) {
            val got = buffer.usePinned { read(fd, it.addressOf(offset), count.toULong()) }
            if (got < 0 && errno == EINTR) continue
            if (got < 0) throw okio.IOException("read failed: ${strerror(errno)?.toKString()}")
            return if (got == 0L) -1 else got.toInt()
        }
    }

    override fun close() {
        close(fd)
    }

    companion object {
        fun open(path: String): FileInput? = open(path, O_RDONLY).takeIf { it >= 0 }?.let(::FileInput)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FileOutput(private val fd: Int) : ByteOutput() {
    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        var done = 0
        while (done < count) {
            val put = buffer.usePinned { write(fd, it.addressOf(offset + done), (count - done).toULong()) }
            if (put < 0 && errno == EINTR) continue
            if (put < 0) throw okio.IOException("write failed: ${strerror(errno)?.toKString()}")
            done += put.toInt()
        }
    }

    override fun close() {
        close(fd)
    }

    companion object {
        private const val MODE = 0x1A4 // rw-r--r--

        fun open(path: String): FileOutput? = open(path, O_WRONLY or O_CREAT or O_TRUNC, MODE).takeIf { it >= 0 }?.let(::FileOutput)
    }
}
