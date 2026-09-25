package com.violinjourney.app.core.audio.recording

import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreAudioTypes.AudioBufferList
import platform.AudioToolbox.ExtAudioFileCreateWithURL
import platform.AudioToolbox.ExtAudioFileDispose
import platform.AudioToolbox.ExtAudioFileRefVar
import platform.AudioToolbox.ExtAudioFileSetProperty
import platform.AudioToolbox.ExtAudioFileWrite
import platform.AudioToolbox.kAudioFileFlags_EraseFile
import platform.AudioToolbox.kAudioFileM4AType
import platform.AudioToolbox.kExtAudioFileProperty_ClientDataFormat
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatFlagIsSignedInteger
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_sync

/**
 * AAC in an `.m4a`, mono, at the rate of the take — what `AacFileEncoder` writes on Android (spec 5.5). The audio
 * thread only copies a hop and hands it to a serial queue of its own; ExtAudioFile encodes there. A queue longer than
 * [QUEUE_HOPS] means the encoder fell behind: [offer] says so and the take goes on without sound, as on Android.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAacEncoder(private val file: PlatformFile, private val sampleRateHz: Int) : PcmEncoder {
    private val queue = dispatch_queue_create("aac-encoder", null)
    private val queued = AtomicInt(0)
    private val failed = AtomicInt(0)
    private val ref = createFile()

    override fun offer(hop: ShortArray, count: Int): Boolean {
        if (failed.value != 0) return false
        if (queued.incrementAndGet() > QUEUE_HOPS) {
            failed.value = 1
            return false
        }
        val samples = hop.copyOf(count)
        dispatch_async(queue) {
            if (failed.value == 0 && !write(samples)) failed.value = 1
            queued.decrementAndGet()
        }
        return true
    }

    override fun finish(): Boolean {
        var complete = false
        dispatch_sync(queue) {
            // disposing is what writes the end of the file
            complete = ExtAudioFileDispose(ref) == NO_ERROR && failed.value == 0
        }
        if (!complete) file.deleteFile()
        return complete
    }

    private fun write(samples: ShortArray): Boolean = memScoped {
        val list = alloc<AudioBufferList>()
        list.mNumberBuffers = 1u
        samples.usePinned { pinned ->
            list.mBuffers[0].mNumberChannels = CHANNELS.convert()
            list.mBuffers[0].mDataByteSize = (samples.size * BYTES_PER_SAMPLE).convert()
            list.mBuffers[0].mData = pinned.addressOf(0)
            ExtAudioFileWrite(ref, samples.size.convert(), list.ptr) == NO_ERROR
        }
    }

    private fun createFile() = memScoped {
        val aac = alloc<AudioStreamBasicDescription>().apply {
            mSampleRate = sampleRateHz.toDouble()
            mFormatID = kAudioFormatMPEG4AAC
            mChannelsPerFrame = CHANNELS.convert()
        }
        val pcm = pcmFormat()
        val out = alloc<ExtAudioFileRefVar>()
        val url = NSURL.fileURLWithPath(file.path)
        @Suppress("UNCHECKED_CAST")
        val cfUrl = CFBridgingRetain(url) as CFURLRef
        val created = ExtAudioFileCreateWithURL(cfUrl, kAudioFileM4AType, aac.ptr, null, kAudioFileFlags_EraseFile, out.ptr)
        CFRelease(cfUrl)
        check(created == NO_ERROR) { "ExtAudioFileCreateWithURL failed: $created" }
        val ref = checkNotNull(out.value)
        val set = ExtAudioFileSetProperty(ref, kExtAudioFileProperty_ClientDataFormat, sizeOf<AudioStreamBasicDescription>().convert(), pcm.ptr)
        if (set != NO_ERROR) {
            ExtAudioFileDispose(ref)
            error("the client format was not taken: $set")
        }
        ref
    }

    private fun kotlinx.cinterop.MemScope.pcmFormat() = alloc<AudioStreamBasicDescription>().apply {
        mSampleRate = sampleRateHz.toDouble()
        mFormatID = kAudioFormatLinearPCM
        mFormatFlags = kAudioFormatFlagIsSignedInteger or kAudioFormatFlagIsPacked
        mChannelsPerFrame = CHANNELS.convert()
        mBitsPerChannel = (BYTES_PER_SAMPLE * BITS_PER_BYTE).convert()
        mBytesPerFrame = (BYTES_PER_SAMPLE * CHANNELS).convert()
        mFramesPerPacket = 1u
        mBytesPerPacket = (BYTES_PER_SAMPLE * CHANNELS).convert()
    }

    private companion object {
        const val CHANNELS = 1
        const val NO_ERROR = 0
        const val BYTES_PER_SAMPLE = 2
        const val BITS_PER_BYTE = 8

        // about a second and a half of hops of 512 at 48 kHz, as on Android
        const val QUEUE_HOPS = 200
    }
}
