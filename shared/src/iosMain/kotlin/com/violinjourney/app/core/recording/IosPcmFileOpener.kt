package com.violinjourney.app.core.recording

import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.roundToLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderStatusReading
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVLinearPCMIsNonInterleaved
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.duration
import platform.AVFoundation.formatDescriptions
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreMedia.CMAudioFormatDescriptionGetStreamBasicDescription
import platform.CoreMedia.CMAudioFormatDescriptionRef
import platform.CoreMedia.CMBlockBufferCopyDataBytes
import platform.CoreMedia.CMBlockBufferGetDataLength
import platform.CoreMedia.CMSampleBufferGetDataBuffer
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSNumber
import platform.Foundation.NSURL

/**
 * The sound track of a file on iOS — a recording or a video — as PCM16 mono at its own rate, read by AVAssetReader:
 * what `PcmDecoder` is on Android. Channels beyond the first are averaged into one.
 */
@OptIn(ExperimentalForeignApi::class)
object IosPcmFileOpener : PcmFileOpener {
    override fun open(file: PlatformFile): OpenedPcm? {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(file.path), options = null)
        val track = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return null
        val description = track.formatDescriptions.firstOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        val format = CMAudioFormatDescriptionGetStreamBasicDescription(description as CMAudioFormatDescriptionRef)?.pointed ?: return null
        val rate = format.mSampleRate.toInt()
        val channels = format.mChannelsPerFrame.toInt().coerceAtLeast(1)
        if (rate <= 0) return null
        val reader = AVAssetReader(asset = asset, error = null)
        val output = AVAssetReaderTrackOutput(
            track = track,
            outputSettings = mapOf<Any?, Any?>(
                AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatLinearPCM),
                AVLinearPCMBitDepthKey to NSNumber(int = BITS),
                AVLinearPCMIsFloatKey to NSNumber(bool = false),
                AVLinearPCMIsBigEndianKey to NSNumber(bool = false),
                AVLinearPCMIsNonInterleaved to NSNumber(bool = false),
            ),
        )
        if (!reader.canAddOutput(output)) return null
        reader.addOutput(output)
        if (!reader.startReading()) return null
        val seconds = CMTimeGetSeconds(asset.duration)
        return Reader(reader, output, rate, channels, (seconds * rate).roundToLong())
    }

    private class Reader(
        private val reader: AVAssetReader,
        private val output: AVAssetReaderTrackOutput,
        override val sampleRate: Int,
        private val channels: Int,
        override val totalSamples: Long,
    ) : OpenedPcm {
        /** Samples of the last buffer not handed out yet: a buffer of the reader and [read]'s array rarely fit. */
        private var pending = ShortArray(0)
        private var pendingAt = 0

        override fun read(out: ShortArray): Int {
            var written = 0
            while (written < out.size) {
                if (pendingAt >= pending.size && !refill()) break
                val count = minOf(out.size - written, pending.size - pendingAt)
                pending.copyInto(out, written, pendingAt, pendingAt + count)
                pendingAt += count
                written += count
            }
            return if (written == 0) PcmSource.END else written
        }

        private fun refill(): Boolean {
            if (reader.status != AVAssetReaderStatusReading) return false
            val sample = output.copyNextSampleBuffer() ?: return false
            try {
                val block = CMSampleBufferGetDataBuffer(sample) ?: return true
                val length = CMBlockBufferGetDataLength(block).toInt()
                val bytes = ByteArray(length)
                if (length > 0) bytes.usePinned { CMBlockBufferCopyDataBytes(block, 0u, length.convert(), it.addressOf(0)) }
                val frames = length / BYTES_PER_SAMPLE / channels
                pending = ShortArray(frames) { frame ->
                    var sum = 0
                    for (c in 0 until channels) {
                        val at = (frame * channels + c) * BYTES_PER_SAMPLE
                        sum += (bytes[at].toInt() and BYTE_MASK) or (bytes[at + 1].toInt() shl BITS_PER_BYTE)
                    }
                    (sum / channels).toShort()
                }
                pendingAt = 0
                return true
            } finally {
                CFRelease(sample)
            }
        }

        override fun release() = reader.cancelReading()
    }

    private const val BITS = 16
    private const val BYTES_PER_SAMPLE = 2
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF
}
