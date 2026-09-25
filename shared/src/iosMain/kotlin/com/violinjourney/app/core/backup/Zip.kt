package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.ByteOutput
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.memset
import platform.zlib.MAX_WBITS
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.Deflater
import okio.DeflaterSink
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer

/**
 * The ZIP of a copy on iOS, written and read as a stream — what `ZipOutputStream` and `ZipInputStream` do on Android,
 * byte for byte in what matters: every entry deflated, its sizes and checksum in a data descriptor after it, ZIP64
 * where a size or an offset does not fit in 32 bits. So a copy made on one phone is read on the other.
 */
internal class ZipWriter(out: ByteOutput) {
    private val counted = CountingSink(ByteOutputSink(out))
    private val sink = counted.buffer()
    private val written = mutableListOf<Written>()
    private var current: Open? = null

    private class Written(val name: ByteArray, val crc: Int, val compressed: Long, val size: Long, val offset: Long)

    private inner class Open(val name: ByteArray, val offset: Long, level: Int) {
        val crc = Crc32()
        var size = 0L
        val compressedBefore = counted.bytes
        val deflating = DeflaterSink(NotClosing(sink), Deflater(level, true)).buffer()
    }

    /** [compress] false still deflates, at level 0: a stored entry would need its checksum before its bytes. */
    fun beginEntry(name: String, compress: Boolean) {
        check(current == null) { "an entry is open" }
        sink.emit()
        val bytes = name.encodeToByteArray()
        val offset = counted.bytes
        with(sink) {
            writeIntLe(LOCAL_HEADER)
            writeShortLe(VERSION_DEFAULT)
            writeShortLe(FLAGS)
            writeShortLe(DEFLATED)
            writeShortLe(0) // time
            writeShortLe(DOS_DATE_1980)
            writeIntLe(0) // crc, in the descriptor
            writeIntLe(0) // compressed size, in the descriptor
            writeIntLe(0) // size, in the descriptor
            writeShortLe(bytes.size)
            writeShortLe(0) // extra
            write(bytes)
        }
        // the header goes out first: what is counted from here on is the entry's data alone
        sink.emit()
        current = Open(bytes, offset, if (compress) DEFAULT_LEVEL else NO_COMPRESSION)
    }

    fun write(buffer: ByteArray, offset: Int, count: Int) {
        val entry = checkNotNull(current) { "no entry is open" }
        entry.crc.update(buffer, offset, count)
        entry.size += count
        entry.deflating.write(buffer, offset, count)
    }

    fun endEntry() {
        val entry = checkNotNull(current) { "no entry is open" }
        current = null
        // closing finishes the deflate stream; the sink under it stays open
        entry.deflating.close()
        sink.emit()
        val compressed = counted.bytes - entry.compressedBefore
        val crc = entry.crc.value
        sink.writeIntLe(DESCRIPTOR)
        sink.writeIntLe(crc)
        // as ZipInputStream decides: eight bytes where a size does not fit in four
        if (compressed > MAX_32 || entry.size > MAX_32) {
            sink.writeLongLe(compressed)
            sink.writeLongLe(entry.size)
        } else {
            sink.writeIntLe(compressed.toInt())
            sink.writeIntLe(entry.size.toInt())
        }
        written += Written(entry.name, crc, compressed, entry.size, entry.offset)
    }

    /** The central directory and its end; the stream under it is closed. */
    fun finish() {
        check(current == null) { "an entry is open" }
        sink.emit()
        val directoryOffset = counted.bytes
        for (entry in written) {
            val wideSize = entry.size > MAX_32
            val wideCompressed = entry.compressed > MAX_32
            val wideOffset = entry.offset > MAX_32
            val extra = Buffer()
            if (wideSize || wideCompressed || wideOffset) {
                val fields = Buffer()
                if (wideSize) fields.writeLongLe(entry.size)
                if (wideCompressed) fields.writeLongLe(entry.compressed)
                if (wideOffset) fields.writeLongLe(entry.offset)
                extra.writeShortLe(ZIP64_EXTRA)
                extra.writeShortLe(fields.size.toInt())
                extra.writeAll(fields)
            }
            val wide = extra.size > 0
            with(sink) {
                writeIntLe(CENTRAL_HEADER)
                writeShortLe(if (wide) VERSION_ZIP64 else VERSION_DEFAULT)
                writeShortLe(if (wide) VERSION_ZIP64 else VERSION_DEFAULT)
                writeShortLe(FLAGS)
                writeShortLe(DEFLATED)
                writeShortLe(0)
                writeShortLe(DOS_DATE_1980)
                writeIntLe(entry.crc)
                writeIntLe(if (wideCompressed) MAX_32.toInt() else entry.compressed.toInt())
                writeIntLe(if (wideSize) MAX_32.toInt() else entry.size.toInt())
                writeShortLe(entry.name.size)
                writeShortLe(extra.size.toInt())
                writeShortLe(0) // comment
                writeShortLe(0) // disk
                writeShortLe(0) // internal attributes
                writeIntLe(0) // external attributes
                writeIntLe(if (wideOffset) MAX_32.toInt() else entry.offset.toInt())
                write(entry.name)
                writeAll(extra)
            }
        }
        sink.emit()
        val directorySize = counted.bytes - directoryOffset
        val wide = written.size >= MAX_16 || directoryOffset > MAX_32 || directorySize > MAX_32
        if (wide) {
            val endOffset = counted.bytes
            with(sink) {
                writeIntLe(ZIP64_END)
                writeLongLe(ZIP64_END_SIZE)
                writeShortLe(VERSION_ZIP64)
                writeShortLe(VERSION_ZIP64)
                writeIntLe(0)
                writeIntLe(0)
                writeLongLe(written.size.toLong())
                writeLongLe(written.size.toLong())
                writeLongLe(directorySize)
                writeLongLe(directoryOffset)
                writeIntLe(ZIP64_LOCATOR)
                writeIntLe(0)
                writeLongLe(endOffset)
                writeIntLe(1)
            }
        }
        with(sink) {
            writeIntLe(END)
            writeShortLe(0)
            writeShortLe(0)
            writeShortLe(if (wide) MAX_16 else written.size)
            writeShortLe(if (wide) MAX_16 else written.size)
            writeIntLe(if (wide) MAX_32.toInt() else directorySize.toInt())
            writeIntLe(if (wide) MAX_32.toInt() else directoryOffset.toInt())
            writeShortLe(0)
        }
        sink.close()
    }
}

/** One entry of an archive being read: its name, and its bytes until [ZipReader.next] is called again. */
internal class ZipEntryReader(val name: String, private val source: Source) {
    fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        val chunk = Buffer()
        val got = source.read(chunk, count.toLong())
        if (got <= 0L) return -1
        // a buffer hands out at most one segment a call
        var at = offset
        while (!chunk.exhausted()) at += chunk.read(buffer, at, offset + got.toInt() - at)
        return got.toInt()
    }
}

/** Reads entries one after another from the start of an archive, checking the checksum and the size of each. */
internal class ZipReader(input: ByteInput) : AutoCloseable {
    private val source = ByteInputSource(input).buffer()
    private var open: Checked? = null

    /** The next entry, or null at the central directory — the end of the entries. Throws [ZipFormatException]. */
    fun next(): ZipEntryReader? {
        open?.let { finish(it) }
        open = null
        if (source.exhausted()) return null
        val signature = source.readIntLe()
        if (signature == CENTRAL_HEADER || signature == END) return null
        if (signature != LOCAL_HEADER) throw ZipFormatException("not a local header: ${signature.toUInt().toString(HEX)}")
        source.skip(2) // version
        val flags = source.readShortLe().toInt() and MAX_16
        val method = source.readShortLe().toInt() and MAX_16
        source.skip(4) // time, date
        val headerCrc = source.readIntLe()
        var compressed = source.readIntLe().toLong() and MAX_32
        var size = source.readIntLe().toLong() and MAX_32
        val nameLength = source.readShortLe().toInt() and MAX_16
        val extraLength = source.readShortLe().toInt() and MAX_16
        val name = source.readUtf8(nameLength.toLong())
        val extra = Buffer().also { source.readFully(it, extraLength.toLong()) }
        var zip64 = false
        while (extra.size >= 4) {
            val id = extra.readShortLe().toInt() and MAX_16
            val length = (extra.readShortLe().toInt() and MAX_16).toLong()
            if (id == ZIP64_EXTRA) {
                zip64 = true
                if (size == MAX_32 && length >= 8) size = extra.readLongLe()
                if (compressed == MAX_32 && length >= 16) compressed = extra.readLongLe()
                extra.clear()
            } else {
                extra.skip(minOf(length, extra.size))
            }
        }
        val described = flags and DESCRIPTOR_FLAG != 0
        val raw: Source = when (method) {
            DEFLATED -> RawInflateSource(source)
            STORED -> {
                if (described) throw ZipFormatException("a stored entry with a descriptor: $name")
                LimitedSource(source, compressed)
            }
            else -> throw ZipFormatException("method $method of $name")
        }
        val checked = Checked(name, raw, described, zip64, headerCrc, size)
        open = checked
        return ZipEntryReader(name, checked)
    }

    private fun finish(entry: Checked) {
        // whatever the caller did not read is read here: the checksum is of the whole entry
        val sink = Buffer()
        while (entry.read(sink, SKIP_CHUNK) != -1L) sink.clear()
        var crc = entry.headerCrc
        var size = entry.expectedSize
        if (entry.described) {
            var first = source.readIntLe()
            if (first == DESCRIPTOR) first = source.readIntLe()
            crc = first
            val wide = entry.zip64 || entry.size > MAX_32
            if (wide) {
                source.readLongLe()
                size = source.readLongLe()
            } else {
                source.readIntLe()
                size = source.readIntLe().toLong() and MAX_32
            }
        }
        if (crc != entry.crc.value || size != entry.size) throw ZipFormatException("${entry.name} is damaged: crc ${crc.toUInt().toString(16)} vs ${entry.crc.value.toUInt().toString(16)}, size $size vs ${entry.size}")
    }

    override fun close() = source.close()

    private class Checked(
        val name: String,
        private val raw: Source,
        val described: Boolean,
        val zip64: Boolean,
        val headerCrc: Int,
        val expectedSize: Long,
    ) : Source {
        val crc = Crc32()
        var size = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            val chunk = Buffer()
            val got = raw.read(chunk, byteCount)
            if (got > 0) {
                val bytes = chunk.readByteArray()
                crc.update(bytes, 0, bytes.size)
                size += bytes.size
                sink.write(bytes)
            }
            return got
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }
}

internal class ZipFormatException(message: String) : okio.IOException(message)

/** CRC-32 of ZIP, the reflected polynomial 0xEDB88320. */
internal class Crc32 {
    private var crc = 0.inv()
    val value: Int get() = crc.inv()

    fun update(bytes: ByteArray, offset: Int, count: Int) {
        var c = crc
        for (i in offset until offset + count) c = TABLE[(c xor bytes[i].toInt()) and 0xFF] xor (c ushr 8)
        crc = c
    }

    private companion object {
        val TABLE = IntArray(256) { n ->
            var c = n
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
            c
        }
    }
}

private class ByteInputSource(private val input: ByteInput) : Source {
    private val chunk = ByteArray(IO_CHUNK)

    override fun read(sink: Buffer, byteCount: Long): Long {
        val got = input.read(chunk, 0, minOf(byteCount, chunk.size.toLong()).toInt())
        if (got < 0) return -1
        sink.write(chunk, 0, got)
        return got.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = input.close()
}

private class ByteOutputSink(private val out: ByteOutput) : Sink {
    private val chunk = ByteArray(IO_CHUNK)

    override fun write(source: Buffer, byteCount: Long) {
        var left = byteCount
        while (left > 0) {
            val count = source.read(chunk, 0, minOf(left, chunk.size.toLong()).toInt())
            out.write(chunk, 0, count)
            left -= count
        }
    }

    override fun flush() = Unit

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = out.close()
}

private class CountingSink(private val delegate: Sink) : Sink {
    var bytes = 0L
        private set

    override fun write(source: Buffer, byteCount: Long) {
        delegate.write(source, byteCount)
        bytes += byteCount
    }

    override fun flush() = delegate.flush()

    override fun timeout(): Timeout = delegate.timeout()

    override fun close() = delegate.close()
}

/** The sink of the archive under one entry's deflater: finishing the entry must not close the archive. */
private class NotClosing(private val delegate: BufferedSink) : Sink {
    override fun write(source: Buffer, byteCount: Long) = delegate.write(source, byteCount)

    override fun flush() {
        delegate.emit()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
        delegate.emit()
    }
}

/**
 * Raw deflate data of one entry, inflated by the system's zlib, taking from [source] exactly the bytes the deflate
 * stream is made of: what follows it — the data descriptor — is left for the reader.
 */
@OptIn(ExperimentalForeignApi::class)
private class RawInflateSource(private val source: BufferedSource) : Source {
    private val stream = nativeHeap.alloc<z_stream>()
    private val input = ByteArray(IO_CHUNK)
    private val output = ByteArray(IO_CHUNK)
    private var finished = false

    init {
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        val status = inflateInit2_(stream.ptr, -MAX_WBITS, zlibVersion()?.toKString(), sizeOf<z_stream>().toInt())
        if (status != Z_OK) throw ZipFormatException("zlib: $status")
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (finished) return -1
        while (true) {
            if (!source.request(1)) throw ZipFormatException("cut short")
            val available = minOf(source.buffer.size, input.size.toLong()).toInt()
            val peeked = source.peek().readByteArray(available.toLong())
            peeked.copyInto(input)
            var produced = 0
            var consumed = 0
            var status = Z_OK
            input.usePinned { inPin ->
                output.usePinned { outPin ->
                    stream.next_in = inPin.addressOf(0).reinterpret()
                    stream.avail_in = available.convert()
                    stream.next_out = outPin.addressOf(0).reinterpret()
                    stream.avail_out = minOf(output.size.toLong(), byteCount).toInt().convert()
                    val before = stream.avail_out.toInt()
                    status = inflate(stream.ptr, Z_NO_FLUSH)
                    produced = before - stream.avail_out.toInt()
                    consumed = available - stream.avail_in.toInt()
                }
            }
            source.skip(consumed.toLong())
            when (status) {
                Z_STREAM_END -> {
                    finished = true
                    inflateEnd(stream.ptr)
                    nativeHeap.free(stream.rawPtr)
                }
                Z_OK, Z_BUF_ERROR -> Unit
                else -> throw ZipFormatException("inflate: $status")
            }
            if (produced > 0) {
                sink.write(output, 0, produced)
                return produced.toLong()
            }
            if (finished) return -1
        }
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
        if (!finished) {
            finished = true
            inflateEnd(stream.ptr)
            nativeHeap.free(stream.rawPtr)
        }
    }
}

private class LimitedSource(private val source: BufferedSource, private var left: Long) : Source {
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (left == 0L) return -1
        val got = source.read(sink, minOf(byteCount, left))
        if (got == -1L) throw ZipFormatException("cut short")
        left -= got
        return got
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}

private const val LOCAL_HEADER = 0x04034b50
private const val DESCRIPTOR = 0x08074b50
private const val CENTRAL_HEADER = 0x02014b50
private const val END = 0x06054b50
private const val ZIP64_END = 0x06064b50
private const val ZIP64_LOCATOR = 0x07064b50
private const val ZIP64_EXTRA = 0x0001
private const val ZIP64_END_SIZE = 44L
private const val VERSION_DEFAULT = 20
private const val VERSION_ZIP64 = 45

/** A data descriptor follows the data (bit 3); names are UTF-8 (bit 11). */
private const val FLAGS = 0x0808
private const val DESCRIPTOR_FLAG = 0x0008
private const val DEFLATED = 8
private const val STORED = 0

/** 1980-01-01, the first day DOS dates know: the time of a file in a copy means nothing. */
private const val DOS_DATE_1980 = (0 shl 9) or (1 shl 5) or 1
private const val DEFAULT_LEVEL = 6
private const val NO_COMPRESSION = 0
private const val MAX_32 = 0xFFFFFFFFL
private const val MAX_16 = 0xFFFF
private const val HEX = 16
private const val IO_CHUNK = 256 * 1024
private const val SKIP_CHUNK = 64 * 1024L
