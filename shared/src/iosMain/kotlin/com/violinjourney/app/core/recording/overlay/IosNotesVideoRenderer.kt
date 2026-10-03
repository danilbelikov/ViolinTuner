package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.cinterop.ByteVar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderStatusFailed
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetReaderVideoCompositionOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVAssetWriterStatusWriting
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableVideoComposition
import platform.AVFoundation.AVMutableVideoCompositionInstruction
import platform.AVFoundation.AVMutableVideoCompositionLayerInstruction
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoColorPrimariesKey
import platform.AVFoundation.AVVideoColorPrimaries_ITU_R_709_2
import platform.AVFoundation.AVVideoColorPropertiesKey
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoExpectedSourceFrameRateKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoProfileLevelH264HighAutoLevel
import platform.AVFoundation.AVVideoProfileLevelKey
import platform.AVFoundation.AVVideoTransferFunctionKey
import platform.AVFoundation.AVVideoTransferFunction_ITU_R_709_2
import platform.AVFoundation.AVVideoWidthKey
import platform.AVFoundation.AVVideoYCbCrMatrixKey
import platform.AVFoundation.AVVideoYCbCrMatrix_ITU_R_709_2
import platform.AVFoundation.formatDescriptions
import platform.AVFoundation.naturalSize
import platform.AVFoundation.nominalFrameRate
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.setColorPrimaries
import platform.AVFoundation.setColorTransferFunction
import platform.AVFoundation.setColorYCbCrMatrix
import platform.AVFoundation.timeRange
import platform.AVFoundation.tracksWithMediaType
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMFormatDescriptionRef
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMTimeZero
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferPoolCreatePixelBuffer
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferRetain
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferHeightKey
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelBufferWidthKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.posix.memcpy

/**
 * [NotesVideoRenderer] of iOS (spec 3.37, 5.30). AVAssetReader hands the frames of the video through a video composition that
 * stands them as the player shows them, at the size of the file and at most 30 a second, in SDR BT.709 (HDR comes down to it
 * there); each frame is copied into a buffer of the writer and the overlay is drawn into its pixels by Skia — the same
 * [NotesOverlayPainter] as on Android; AVAssetWriter encodes H.264. The last frame is kept as it came and carries the summary for
 * three more seconds. The sound track of [sound] goes in as it is, sample by sample, up to the end of the summary.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotesVideoRenderer(
    /** Manrope is read from the resources once, the first time a file is made: it is not wanted before. */
    private val loadText: suspend () -> OverlayText,
    private val config: NotesVideoConfig,
    private val io: CoroutineDispatcher,
) : NotesVideoRenderer {
    private val textLock = Mutex()
    private var text: OverlayText? = null

    override suspend fun render(
        picture: PlatformFile,
        sound: PlatformFile,
        overlay: NotesOverlay,
        words: OverlayWords,
        target: PlatformFile,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val font = textLock.withLock { text ?: loadText().also { text = it } }
        return withContext(io) {
            // AVAssetWriter writes no file over one that is there
            target.deleteFile()
            var whole = false
            try {
                whole = encode(picture, sound, overlay, words, font, target, onProgress)
                whole
            } finally {
                if (!whole) target.deleteFile()
            }
        }
    }

    private suspend fun encode(
        picture: PlatformFile,
        sound: PlatformFile,
        overlay: NotesOverlay,
        words: OverlayWords,
        font: OverlayText,
        target: PlatformFile,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(picture.path), options = null)
        val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return false
        val shown = shownSize(track)
        val format = NotesVideoFormat.of(shown.first, shown.second, track.nominalFrameRate, config)
        val videoEnd = track.timeRange.useContents { CMTimeRangeGetEnd(readValue()) }
        val videoEndMs = (CMTimeGetSeconds(videoEnd) * MS_PER_SECOND).roundToLong()
        if (videoEndMs <= 0) return false
        val endMs = videoEndMs + config.summaryMs
        val frameSeconds = 1.0 / format.frameRate

        val pictures = reader(asset) ?: return false
        val frames = AVAssetReaderVideoCompositionOutput(videoTracks = listOf(track), videoSettings = bgra())
        frames.videoComposition = upright(track, format, videoEnd)
        frames.alwaysCopiesSampleData = false
        if (!pictures.canAddOutput(frames)) return false
        pictures.addOutput(frames)
        pictures.timeRange = CMTimeRangeMake(kCMTimeZero.readValue(), videoEnd)

        val soundAsset = if (sound.path == picture.path) asset else AVURLAsset(uRL = NSURL.fileURLWithPath(sound.path), options = null)
        val soundTrack = soundAsset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
        val sounds = soundTrack?.let { reader(soundAsset) }
        val samples = soundTrack?.let { AVAssetReaderTrackOutput(track = it, outputSettings = null) }
        if (sounds != null && samples != null) {
            if (!sounds.canAddOutput(samples)) return false
            sounds.addOutput(samples)
        }

        val writer = try {
            AVAssetWriter(uRL = NSURL.fileURLWithPath(target.path), fileType = AVFileTypeMPEG4, error = null)
        } catch (_: NullPointerException) {
            return false
        }
        val video = AVAssetWriterInput(mediaType = AVMediaTypeVideo, outputSettings = encoding(format))
        video.expectsMediaDataInRealTime = false
        val adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput = video, sourcePixelBufferAttributes = bgra(format.width, format.height))
        if (!writer.canAddInput(video)) return false
        writer.addInput(video)
        val audio = soundTrack?.let { passthrough(it) }
        if (audio != null) {
            if (!writer.canAddInput(audio)) return false
            writer.addInput(audio)
        }
        // the sound is there but cannot be read or written: no file is better than one without it
        if (soundTrack != null && (sounds == null || samples == null || audio == null)) return failed("the sound of ${sound.path.substringAfterLast('/')} cannot be read")

        val painter = NotesOverlayPainter(overlay, words, font, format.width.toFloat(), format.height.toFloat())
        var last: CVPixelBufferRef? = null
        try {
            if (!pictures.startReading()) return false
            if (sounds != null && !sounds.startReading()) return false
            if (!writer.startWriting()) return false
            writer.startSessionAtSourceTime(kCMTimeZero.readValue())

            var pictureAt = 0.0
            var soundAt = 0.0
            var framesLeft = true
            // the summary goes on the timeline of the picture, frame after frame, in the same loop as the sound: the writer
            // interleaves the two inputs and holds the one ahead — a sound longer than the picture would otherwise wait for frames
            // that came only after it, for ever
            val summaryFrames = (config.summaryMs * format.frameRate / MS_PER_SECOND_L).toInt()
            var summaryIndex = 0
            var summaryStart = 0.0
            var soundsLeft = audio != null
            while (framesLeft || summaryIndex < summaryFrames || soundsLeft) {
                currentCoroutineContext().ensureActive()
                if (writer.status != AVAssetWriterStatusWriting) return failed(writer.error?.localizedDescription)
                val nextPicture = (framesLeft || summaryIndex < summaryFrames) && (!soundsLeft || pictureAt <= soundAt)
                if (nextPicture) {
                    if (!video.readyForMoreMediaData) {
                        delay(WAIT_MS)
                        continue
                    }
                    if (framesLeft) {
                        val sample = frames.copyNextSampleBuffer()
                        if (sample == null) {
                            if (pictures.status == AVAssetReaderStatusFailed) return failed(pictures.error?.localizedDescription)
                            framesLeft = false
                            if (last == null) return failed("no frame in ${picture.path.substringAfterLast('/')}")
                            // after the last frame, never on it: the end of a track may sit a hair past the last frame's time
                            summaryStart = maxOf(CMTimeGetSeconds(videoEnd), pictureAt + frameSeconds)
                            continue
                        }
                        try {
                            val at = CMSampleBufferGetPresentationTimeStamp(sample)
                            val source = CMSampleBufferGetImageBuffer(sample) ?: continue
                            pictureAt = CMTimeGetSeconds(at)
                            val ms = (pictureAt * MS_PER_SECOND).roundToLong()
                            if (!appendDrawn(adaptor, source, at) { painter.draw(this, ms, videoEndMs) }) return failed(writer.error?.localizedDescription)
                            // kept as it came, without the notes: the summary is drawn on it
                            last?.let { CVPixelBufferRelease(it) }
                            last = CVPixelBufferRetain(source)
                            onProgress((ms.toFloat() / endMs).coerceIn(0f, 1f))
                        } finally {
                            CFRelease(sample)
                        }
                    } else {
                        // the summary: the last frame stands, the veil and the summary come in over it
                        val lastFrame = last ?: return failed("no frame in ${picture.path.substringAfterLast('/')}")
                        pictureAt = summaryStart + summaryIndex * frameSeconds
                        val ms = (pictureAt * MS_PER_SECOND).roundToLong()
                        val at = CMTimeMakeWithSeconds(pictureAt, VIDEO_TIMESCALE)
                        if (!appendDrawn(adaptor, lastFrame, at) { painter.draw(this, ms, videoEndMs) }) return failed(writer.error?.localizedDescription)
                        summaryIndex++
                        onProgress((ms.toFloat() / endMs).coerceIn(0f, 1f))
                    }
                } else {
                    val audioInput = audio ?: return failed("no input for the sound")
                    if (!audioInput.readyForMoreMediaData) {
                        delay(WAIT_MS)
                        continue
                    }
                    val sample = samples?.copyNextSampleBuffer()
                    if (sample == null) {
                        if (sounds?.status == AVAssetReaderStatusFailed) return failed(sounds.error?.localizedDescription)
                        soundsLeft = false
                        // finished at once: the writer stops waiting for sound to put beside the frames still to come
                        audioInput.markAsFinished()
                        continue
                    }
                    try {
                        soundAt = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample))
                        // what begins past the summary is not read; a buffer that runs over its end is cut by the end of the session
                        if (soundAt * MS_PER_SECOND >= endMs) {
                            soundsLeft = false
                            audioInput.markAsFinished()
                        } else if (!audioInput.appendSampleBuffer(sample)) {
                            return failed(writer.error?.localizedDescription)
                        }
                    } finally {
                        CFRelease(sample)
                    }
                }
            }
            video.markAsFinished()
            // a buffer of compressed sound holds many packets: the file ends where the summary does, not where its last buffer would
            writer.endSessionAtSourceTime(CMTimeMakeWithSeconds(summaryStart + summaryFrames * frameSeconds, VIDEO_TIMESCALE))
            val written = suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { writer.cancelWriting() }
                writer.finishWritingWithCompletionHandler { continuation.resume(writer.status == AVAssetWriterStatusCompleted) }
            }
            if (!written) return failed(writer.error?.localizedDescription)
            onProgress(1f)
            return true
        } finally {
            last?.let { CVPixelBufferRelease(it) }
            if (writer.status == AVAssetWriterStatusWriting) writer.cancelWriting()
            pictures.cancelReading()
            sounds?.cancelReading()
        }
    }

    /** A buffer of the writer's pool with the pixels of [source] and the overlay drawn on them, appended at [at]. */
    private fun appendDrawn(
        adaptor: AVAssetWriterInputPixelBufferAdaptor,
        source: CVPixelBufferRef,
        at: kotlinx.cinterop.CValue<platform.CoreMedia.CMTime>,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ): Boolean = memScoped {
        val pool = adaptor.pixelBufferPool ?: return@memScoped false
        val made = alloc<CVPixelBufferRefVar>()
        if (CVPixelBufferPoolCreatePixelBuffer(null, pool, made.ptr) != 0 || made.value == null) return@memScoped false
        val target = made.value!!
        try {
            copyPixels(source, target)
            CVPixelBufferLockBaseAddress(target, 0u)
            try {
                val width = CVPixelBufferGetWidth(target).toInt()
                val height = CVPixelBufferGetHeight(target).toInt()
                val base = CVPixelBufferGetBaseAddress(target) ?: return@memScoped false
                val surface = Surface.makeRasterDirect(
                    ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
                    base.rawValue,
                    CVPixelBufferGetBytesPerRow(target).toInt(),
                )
                try {
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, surface.canvas.asComposeCanvas(), Size(width.toFloat(), height.toFloat()), draw)
                } finally {
                    surface.close()
                }
            } finally {
                CVPixelBufferUnlockBaseAddress(target, 0u)
            }
            adaptor.appendPixelBuffer(target, at)
        } finally {
            CVPixelBufferRelease(target)
        }
    }

    /** The pixels of [from] into [to], row by row: the two may be padded differently. */
    private fun copyPixels(from: CVPixelBufferRef, to: CVPixelBufferRef) {
        CVPixelBufferLockBaseAddress(from, kCVPixelBufferLock_ReadOnly)
        CVPixelBufferLockBaseAddress(to, 0u)
        try {
            val source = CVPixelBufferGetBaseAddress(from)?.reinterpret<ByteVar>() ?: return
            val target = CVPixelBufferGetBaseAddress(to)?.reinterpret<ByteVar>() ?: return
            val sourceRow = CVPixelBufferGetBytesPerRow(from).toLong()
            val targetRow = CVPixelBufferGetBytesPerRow(to).toLong()
            val rows = minOf(CVPixelBufferGetHeight(from), CVPixelBufferGetHeight(to)).toLong()
            val bytes = minOf(sourceRow, targetRow)
            for (row in 0 until rows) memcpy(target + row * targetRow, source + row * sourceRow, bytes.convert())
        } finally {
            CVPixelBufferUnlockBaseAddress(to, 0u)
            CVPixelBufferUnlockBaseAddress(from, kCVPixelBufferLock_ReadOnly)
        }
    }

    /** The size the player shows: the stored size turned by the track's transform. */
    private fun shownSize(track: AVAssetTrack): Pair<Int, Int> {
        val (width, height) = track.naturalSize.useContents { width to height }
        val turned = CGRectApplyAffineTransform(CGRectMake(0.0, 0.0, width, height), track.preferredTransform)
        return turned.useContents { abs(size.width).toInt() to abs(size.height).toInt() }
    }

    /**
     * Frames as the player shows them, at the size of the file, no more often than its frame rate, in SDR BT.709: the
     * composition turns and scales them and brings HDR down — the overlay is drawn on a picture that stands.
     */
    private fun upright(track: AVAssetTrack, format: NotesVideoFormat, end: kotlinx.cinterop.CValue<platform.CoreMedia.CMTime>): AVMutableVideoComposition {
        val (shownWidth, _) = shownSize(track)
        val scale = format.width.toDouble() / shownWidth
        // the turn of the file, moved back to the origin: a camera writes the shift into its transform, a hand-made file may
        // turn without it — and its picture would land outside the frame, as a player never shows it
        val (width, height) = track.naturalSize.useContents { width to height }
        val (left, top) = CGRectApplyAffineTransform(CGRectMake(0.0, 0.0, width, height), track.preferredTransform).useContents { origin.x to origin.y }
        val turn = CGAffineTransformConcat(track.preferredTransform, CGAffineTransformMakeTranslation(-left, -top))
        val layer = AVMutableVideoCompositionLayerInstruction.videoCompositionLayerInstructionWithAssetTrack(track)
        layer.setTransform(CGAffineTransformConcat(turn, CGAffineTransformMakeScale(scale, scale)), atTime = kCMTimeZero.readValue())
        val instruction = AVMutableVideoCompositionInstruction()
        instruction.setTimeRange(CMTimeRangeMake(kCMTimeZero.readValue(), end))
        instruction.setLayerInstructions(listOf(layer))
        return AVMutableVideoComposition().apply {
            setRenderSize(CGSizeMake(format.width.toDouble(), format.height.toDouble()))
            setFrameDuration(CMTimeMake(1, format.frameRate))
            setInstructions(listOf(instruction))
            setColorPrimaries(AVVideoColorPrimaries_ITU_R_709_2)
            setColorTransferFunction(AVVideoTransferFunction_ITU_R_709_2)
            setColorYCbCrMatrix(AVVideoYCbCrMatrix_ITU_R_709_2)
        }
    }

    /** H.264 High at the bit rate of 5.30, SDR BT.709. */
    private fun encoding(format: NotesVideoFormat): Map<Any?, Any?> = mapOf(
        AVVideoCodecKey to AVVideoCodecTypeH264,
        AVVideoWidthKey to NSNumber(int = format.width),
        AVVideoHeightKey to NSNumber(int = format.height),
        AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(
            AVVideoAverageBitRateKey to NSNumber(int = format.bitrate),
            AVVideoProfileLevelKey to AVVideoProfileLevelH264HighAutoLevel,
            AVVideoExpectedSourceFrameRateKey to NSNumber(int = format.frameRate),
        ),
        AVVideoColorPropertiesKey to mapOf<Any?, Any?>(
            AVVideoColorPrimariesKey to AVVideoColorPrimaries_ITU_R_709_2,
            AVVideoTransferFunctionKey to AVVideoTransferFunction_ITU_R_709_2,
            AVVideoYCbCrMatrixKey to AVVideoYCbCrMatrix_ITU_R_709_2,
        ),
    )

    /** BGRA pixels, of [width] × [height] when given: what Skia draws into. */
    private fun bgra(width: Int? = null, height: Int? = null): Map<Any?, Any?> = buildMap {
        put(cfKey(kCVPixelBufferPixelFormatTypeKey), NSNumber(unsignedInt = kCVPixelFormatType_32BGRA))
        if (width != null) put(cfKey(kCVPixelBufferWidthKey), NSNumber(int = width))
        if (height != null) put(cfKey(kCVPixelBufferHeightKey), NSNumber(int = height))
    }

    /** The sound as it is: compressed samples straight into the file. */
    private fun passthrough(track: AVAssetTrack): AVAssetWriterInput? {
        val description = track.formatDescriptions.firstOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        val hint = CFBridgingRetain(description) as CMFormatDescriptionRef
        return try {
            AVAssetWriterInput(mediaType = AVMediaTypeAudio, outputSettings = null, sourceFormatHint = hint).apply { expectsMediaDataInRealTime = false }
        } finally {
            CFRelease(hint)
        }
    }

    /** A reader that cannot be made comes back as nil, which Kotlin/Native throws as an NPE. */
    private fun reader(asset: AVURLAsset): AVAssetReader? = try {
        AVAssetReader(asset = asset, error = null)
    } catch (_: NullPointerException) {
        null
    }

    private fun failed(why: String?): Boolean {
        // NSLog takes Objective-C objects for its arguments: the line is made whole here, its percent signs doubled
        NSLog("NotesVideoRenderer: the video could not be made: $why".replace("%", "%%"))
        return false
    }

    private companion object {
        const val MS_PER_SECOND = 1_000.0
        const val MS_PER_SECOND_L = 1_000L
        /** Times of the frames and of the end of the file: fine enough for every frame rate a camera has. */
        const val VIDEO_TIMESCALE = 30_000
        const val WAIT_MS = 2L

        /** A key of Core Foundation as the Objective-C string a dictionary of settings takes; the constant itself is not let go. */
        fun cfKey(key: CFStringRef?): Any? = CFBridgingRelease(CFRetain(key))
    }
}
