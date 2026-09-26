package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.readBytes
import com.violinjourney.app.core.io.writeBytes
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.getStackTraceAddresses
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.Dl_info
import platform.posix.dladdr

/** One frame of a Kotlin crash, as the console of the statistics shows and groups it (spec 3.34). */
data class KotlinCrashFrame(
    /** The Kotlin class of the function, or its package for a function at the top level; the image for a frame without a Kotlin name. */
    val className: String,
    /** The function; a symbol of the system; or `0x…`, the offset in the image, where the build keeps no names. */
    val methodName: String,
    /** The source file where the build knows it; otherwise the image and the offset in it (`ViolinJourney+0x1a2b`), for `atos`. */
    val fileName: String,
    /** The line in [fileName]; 0 where the build does not know it. */
    val line: Int,
)

/** A Kotlin exception that ended a run: its class, its message, the chain of its causes, where it was thrown. */
internal data class KotlinCrash(val type: String, val message: String?, val causes: String?, val frames: List<KotlinCrashFrame>)

/**
 * An unhandled Kotlin exception ends the iOS app through `abort()`, and the system's report of it (KSCrash inside
 * AppMetricaCrashes) is a SIGABRT whose frames are those of the coroutine machinery — no type, no message, not the place it
 * was thrown. So at the moment of the fall the app keeps these itself in a small file in Caches (not in the phone's backup,
 * not in a copy of the data), and tells them at the next start as a crash of its own ([IosAppMetricaAnalytics.crashed],
 * spec 3.34). Telling from the hook itself would not do: the library sends later, from its queue, and the process is gone
 * by then — and an address of this run means nothing in the next, so each frame is kept as an image and an offset in it.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
internal object KotlinCrashes {
    fun defaultRecord(): PlatformFile =
        PlatformFile("${NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String}/$RECORD")

    /** From here on an unhandled Kotlin exception is kept in [record], and the app ends as it did without the hook. */
    fun install(record: PlatformFile) {
        setUnhandledExceptionHook { throwable ->
            keep(record, throwable)
            terminateWithUnhandledException(throwable)
        }
    }

    /**
     * Writes [throwable] to [record]; false when it could not. The process is ending: a record that cannot be written is
     * simply missing — the system's report still has the fall — and a second throw here would hide the first.
     */
    fun keep(record: PlatformFile, throwable: Throwable): Boolean = try {
        val bytes = encode(crashOf(throwable)).encodeToByteArray()
        record.openOutput()?.let { output ->
            try {
                output.writeBytes(bytes)
            } finally {
                output.close()
            }
            true
        } ?: false
    } catch (e: Throwable) {
        false
    }

    /** The crash kept by the last run, once: the record goes as it is read. Null when there is none, or it is not one of ours. */
    fun takeKept(record: PlatformFile): KotlinCrash? {
        val input = record.openInput() ?: return null
        return try {
            decode(readAll(input).decodeToString())
        } catch (e: okio.IOException) {
            null
        } finally {
            input.close()
            record.deleteFile()
        }
    }

    fun crashOf(throwable: Throwable): KotlinCrash {
        val addresses = throwable.getStackTraceAddresses()
        // the lines of the stack carry the source file and line where the build keeps them; one line per address
        val lines = throwable.getStackTrace().takeIf { it.size == addresses.size }
        val frames = addresses.take(MAX_FRAMES).mapIndexed { index, address -> frameOf(address, lines?.get(index)) }
        val causes = generateSequence(throwable.cause) { it.cause }.take(MAX_CAUSES).joinToString(" ← ") { "${nameOf(it)}: ${it.message}" }
        return KotlinCrash(nameOf(throwable), throwable.message?.take(MAX_MESSAGE), causes.take(MAX_CAUSES_TEXT).ifEmpty { null }, frames)
    }

    /**
     * `kfun:com.example.Foo#bar(kotlin.Int){}` is `com.example.Foo` and `bar`, and so is a private one,
     * `kfun:com.example.Foo.bar#internal`; a symbol of the system keeps its image as the class; a frame without a name is
     * its offset in the image.
     */
    fun namesOf(symbol: String?, image: String, offset: Long): Pair<String, String> {
        if (symbol == null) return image to "0x${offset.toString(HEX)}"
        if (!symbol.startsWith(KOTLIN_FUNCTION)) return image to symbol
        val name = symbol.removePrefix(KOTLIN_FUNCTION).substringBefore('(')
        if ('#' !in name) return image to name
        val owner = name.substringBefore('#')
        val function = name.substringAfter('#')
        return if (function == PRIVATE && '.' in owner) owner.substringBeforeLast('.') to owner.substringAfterLast('.') else owner to function
    }

    private fun frameOf(address: Long, line: String?): KotlinCrashFrame = memScoped {
        val info = alloc<Dl_info>()
        val found = dladdr(address.toCPointer<ByteVar>(), info.ptr) != 0
        val image = (if (found) info.dli_fname?.toKString()?.substringAfterLast('/') else null) ?: UNKNOWN_IMAGE
        val offset = if (found) address - info.dli_fbase.toLong() else address
        val (owner, function) = namesOf(if (found) info.dli_sname?.toKString() else null, image, offset)
        val source = line?.let { SOURCE.find(it) }
        KotlinCrashFrame(
            className = owner,
            methodName = function,
            fileName = source?.groupValues?.get(1)?.substringAfterLast('/') ?: "$image+0x${offset.toString(HEX)}",
            line = source?.groupValues?.get(2)?.toIntOrNull() ?: 0,
        )
    }

    private fun nameOf(throwable: Throwable) = throwable::class.qualifiedName ?: throwable::class.simpleName ?: "Throwable"

    private fun readAll(input: ByteInput): ByteArray {
        val chunk = ByteArray(CHUNK)
        var all = ByteArray(0)
        while (true) {
            val got = input.readBytes(chunk)
            if (got < 0) return all
            all += chunk.copyOf(got)
        }
    }

    /** A header, then the type, the message and the causes, then a frame a line with its fields between tabs. */
    fun encode(crash: KotlinCrash): String = buildString {
        appendLine(HEADER)
        appendLine(escape(crash.type))
        appendLine(escape(crash.message.orEmpty()))
        appendLine(escape(crash.causes.orEmpty()))
        crash.frames.forEach { frame ->
            appendLine(listOf(frame.className, frame.methodName, frame.fileName, frame.line.toString()).joinToString("\t", transform = ::escape))
        }
    }

    fun decode(text: String): KotlinCrash? {
        val lines = text.split('\n')
        if (lines.size < FIRST_FRAME || lines[0] != HEADER) return null
        val frames = lines.drop(FIRST_FRAME).filter { it.isNotEmpty() }.map { line ->
            val fields = line.split('\t').map(::unescape)
            if (fields.size != FRAME_FIELDS) return null
            KotlinCrashFrame(fields[0], fields[1], fields[2], fields[3].toIntOrNull() ?: 0)
        }
        return KotlinCrash(unescape(lines[1]), unescape(lines[2]).ifEmpty { null }, unescape(lines[3]).ifEmpty { null }, frames)
    }

    private fun escape(field: String): String = buildString {
        field.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun unescape(field: String): String = buildString {
        var escaped = false
        field.forEach { c ->
            if (escaped) {
                append(
                    when (c) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> c
                    },
                )
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else {
                append(c)
            }
        }
    }

    private const val RECORD = "last-kotlin-crash.txt"
    private const val HEADER = "kotlin-crash 1"
    private const val FIRST_FRAME = 4
    private const val FRAME_FIELDS = 4
    private const val KOTLIN_FUNCTION = "kfun:"
    private const val PRIVATE = "internal"
    private const val UNKNOWN_IMAGE = "?"
    private const val HEX = 16
    private const val CHUNK = 8_192

    /** The limits of a crash of a plugin in AppMetrica: 200 frames, a message of 1000 characters, a value of 2000. */
    private const val MAX_FRAMES = 200
    private const val MAX_MESSAGE = 1_000
    private const val MAX_CAUSES_TEXT = 2_000
    private const val MAX_CAUSES = 5

    /** `… (/path/to/HopSplitter.kt:26:5)` at the end of a line of the stack. */
    private val SOURCE = Regex("""\(([^()]+):(\d+):(\d+)\)\s*$""")
}
