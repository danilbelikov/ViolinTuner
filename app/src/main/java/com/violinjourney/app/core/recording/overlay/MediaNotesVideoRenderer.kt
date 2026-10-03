package com.violinjourney.app.core.recording.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.recording.video.VideoMuxer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [NotesVideoRenderer] of Android (spec 3.37, 5.30): Media3 Transformer encodes the picture again — the video without its sound,
 * then its last frame for the three seconds of the summary — with the overlay drawn over every frame by [NotesOverlayPainter]
 * into a bitmap; HDR comes down to SDR, the size to 1080 on the short side, the frames to 30 a second. [VideoMuxer.splice] then
 * puts the sound of the variant beside the new picture, as it puts a rendered sound beside the picture of a plain video. Transformer,
 * the effects and the overlay are the «unstable» API of Media3: what changes between its versions is followed when it is bumped.
 */
@OptIn(UnstableApi::class)
class MediaNotesVideoRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    /** Manrope and the icon of the signature are read the first time a file is made: they are not wanted before. */
    private val text: OverlayTextLoader,
    private val config: NotesVideoConfig,
    @IoDispatcher private val io: CoroutineDispatcher,
) : NotesVideoRenderer {

    override suspend fun render(
        picture: File,
        sound: File,
        overlay: NotesOverlay,
        words: OverlayWords,
        target: File,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val encoded = File(target.parentFile, target.name + PICTURE_SUFFIX)
        val lastFrame = File(target.parentFile, target.name + LAST_FRAME_SUFFIX)
        var whole = false
        try {
            // the folder of a file to share is made by whoever writes into it first: here that may be this renderer — the sound
            // of the video goes in as it is, and nothing has written there yet
            val probe = withContext(io) {
                target.parentFile?.mkdirs()
                VideoProbe.of(picture)
            } ?: return false
            val format = NotesVideoFormat.of(probe.width, probe.height, probe.frameRate, config)
            if (!withContext(io) { saveLastFrame(picture, probe, format, lastFrame) }) return false
            val font = withContext(io) { text.get() }
            val painter = NotesOverlayPainter(overlay, words, font, format.width.toFloat(), format.height.toFloat())
            val drawing = NotesBitmapOverlay(painter, probe.durationUs / US_PER_MS, config.summaryFadeMs, format.width, format.height)
            if (!transform(picture, lastFrame, probe, format, drawing, encoded) { onProgress(it * config.pictureShare) }) return false
            val job = currentCoroutineContext().job
            whole = withContext(io) {
                VideoMuxer.splice(
                    encoded, sound, target, pictureShiftUs = 0,
                    onProgress = { onProgress(config.pictureShare + it * (1f - config.pictureShare)) },
                    keepGoing = { job.ensureActive() },
                    // the file ends with its summary: a hall that rings on past it is cut, as on iOS
                    soundUntilUs = probe.durationUs + config.summaryMs * US_PER_MS,
                )
            }
            if (whole) onProgress(1f)
            return whole
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "cannot read or write for ${picture.name}", e)
            return false
        } catch (e: RuntimeException) {
            // a retriever, a bitmap or the muxer refusing this video
            Log.w(TAG, "the notes could not be put on ${picture.name}", e)
            return false
        } finally {
            withContext(NonCancellable + io) {
                encoded.delete()
                lastFrame.delete()
                if (!whole) target.delete()
            }
        }
    }

    /**
     * The picture with the notes into [output], no sound. Transformer lives on the main thread: it is built, started, asked for
     * its progress and cancelled there; it draws the overlay on a thread of its own.
     */
    private suspend fun transform(
        picture: File,
        lastFrame: File,
        probe: VideoProbe,
        format: NotesVideoFormat,
        drawing: NotesBitmapOverlay,
        output: File,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.Main) {
        val done = CompletableDeferred<Boolean>()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(format.bitrate).build())
                    .setEnableFallback(true)
                    .build(),
            )
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(true)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.w(TAG, "the picture of ${picture.name} could not be encoded", exportException)
                        done.complete(false)
                    }
                },
            )
            .build()
        transformer.start(composition(picture, lastFrame, probe, format, drawing), output.absolutePath)
        try {
            // by the frames the overlay has drawn, against the video and its summary: Media3 shares its own progress evenly
            // between the items of a sequence, and the still of three seconds would be half of it
            val totalUs = (probe.durationUs + config.summaryMs * US_PER_MS).toFloat()
            while (!done.isCompleted) {
                onProgress((drawing.lastUs / totalUs).coerceIn(0f, 1f))
                withTimeoutOrNull(PROGRESS_EVERY_MS) { done.await() }
            }
            done.await()
        } finally {
            // given up: the export stops and leaves its file unfinished; the caller deletes it
            if (!done.isCompleted) transformer.cancel()
        }
    }

    /** The video without its sound, then its last frame for the summary; at the size of the file, with the overlay on top. */
    private fun composition(picture: File, lastFrame: File, probe: VideoProbe, format: NotesVideoFormat, drawing: NotesBitmapOverlay): Composition {
        // frames are dropped where there are too many, or may be — a file that does not say its rate; a stream of 30 frames
        // or fewer passes the effect whole
        val dropFrames = probe.frameRate <= 0f || probe.frameRate > format.frameRate + FRAME_RATE_SLACK
        val videoEffects: List<Effect> = if (dropFrames) listOf(FrameDropEffect.createDefaultFrameDropEffect(format.frameRate.toFloat())) else emptyList()
        // the item ends with its picture: Media3 would put the next one after the longest track, the sound left out included
        val clipped = MediaItem.Builder()
            .setUri(Uri.fromFile(picture))
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(probe.durationUs / US_PER_MS).build())
            .build()
        val video = EditedMediaItem.Builder(clipped)
            .setRemoveAudio(true)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
        val still = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(Uri.fromFile(lastFrame)).setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(config.summaryMs).build(),
        )
            .setFrameRate(format.frameRate)
            .build()
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItem(video).addItem(still).build()
        return Composition.Builder(sequence)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(Presentation.createForWidthAndHeight(format.width, format.height, Presentation.LAYOUT_SCALE_TO_FIT), OverlayEffect(listOf(drawing))),
                ),
            )
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }

    /**
     * The last frame as the player shows it, at the size of the file, into [to]: the picture the summary lies on. A retriever
     * that hands the frame as it is stored, not turned, gets it turned here.
     */
    private fun saveLastFrame(picture: File, probe: VideoProbe, format: NotesVideoFormat, to: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(picture.absolutePath)
            val at = (probe.durationUs - LAST_FRAME_BACK_US).coerceAtLeast(0)
            val frame = retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
                ?: return false
            val upright = if (probe.turned && frame.width == probe.storedWidth && frame.height == probe.storedHeight && probe.storedWidth != probe.storedHeight) {
                Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(probe.rotation.toFloat()) }, true)
            } else {
                frame
            }
            val sized = Bitmap.createScaledBitmap(upright, format.width, format.height, true)
            to.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, LAST_FRAME_QUALITY, it) }
            true
        } catch (e: IOException) {
            Log.w(TAG, "the last frame of ${picture.name} could not be kept", e)
            false
        } catch (e: RuntimeException) {
            Log.w(TAG, "no last frame in ${picture.name}", e)
            false
        } finally {
            retriever.release()
        }
    }

    /** What the video track says about itself: stored size and turn, frames a second (0 when it does not say) and length. */
    private class VideoProbe(val storedWidth: Int, val storedHeight: Int, val rotation: Int, val frameRate: Float, val durationUs: Long) {
        val turned: Boolean get() = rotation % HALF_TURN != 0
        val width: Int get() = if (turned) storedHeight else storedWidth
        val height: Int get() = if (turned) storedWidth else storedHeight

        companion object {
            fun of(file: File): VideoProbe? {
                val extractor = MediaExtractor()
                return try {
                    extractor.setDataSource(file.absolutePath)
                    val format = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                        .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return null
                    VideoProbe(
                        storedWidth = format.getInteger(MediaFormat.KEY_WIDTH),
                        storedHeight = format.getInteger(MediaFormat.KEY_HEIGHT),
                        rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0,
                        frameRate = frameRateOf(format),
                        durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L,
                    ).takeIf { it.durationUs > 0 && it.storedWidth > 0 && it.storedHeight > 0 }
                } catch (e: IOException) {
                    Log.w(TAG, "cannot open ${file.name}", e)
                    null
                } finally {
                    extractor.release()
                }
            }

            /** The frame rate is an integer in some files and a float in others. */
            private fun frameRateOf(format: MediaFormat): Float {
                if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) return 0f
                return try {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                } catch (e: ClassCastException) {
                    format.getFloat(MediaFormat.KEY_FRAME_RATE)
                }
            }
        }
    }

    /**
     * The overlay of Media3: drawn again for every frame — the lane moves — until the summary has come in; from then on every
     * frame is the same, and the bitmap is neither drawn nor uploaded again. The shader of Media3 mixes an overlay as colours not
     * multiplied by their alpha (`insert_overlay_fragment_shader_methods.glsl`), while a bitmap keeps them multiplied: what is drawn
     * goes to [upload] unmultiplied, or every half-clear colour — the dimmed notes, the glass, the edges of letters — would come
     * out darker than drawn. While the video runs only the band of the lane changes — and the band of the opening title over its
     * first seconds (spec 5.30) — and only they are copied.
     */
    @OptIn(UnstableApi::class)
    private class NotesBitmapOverlay(
        private val painter: NotesOverlayPainter,
        private val videoEndMs: Long,
        fadeMs: Long,
        private val width: Int,
        private val height: Int,
    ) : BitmapOverlay() {
        private val drawn = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private val upload = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { isPremultiplied = false }
        private val pixels = IntArray(width * height)
        private val canvas = Canvas(drawn.asImageBitmap())
        private val scope = CanvasDrawScope()
        private val size = Size(width.toFloat(), height.toFloat())
        private val laneBandTop = painter.geometry.scrimTop.toInt().coerceIn(0, height)

        /** The band of the opening title, above the lane's (spec 5.30): it never reaches the lane, and is cut where it would. */
        private val openingBandBottom = ceil(painter.openingBottom).toInt().coerceIn(0, laneBandTop)
        private val settledAtMs = videoEndMs + fadeMs
        private var settled = false
        private var openingInUpload = false

        /** The time of the last frame drawn: how far the picture has come. */
        @Volatile var lastUs = 0L
            private set

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            lastUs = presentationTimeUs
            if (settled) return upload
            val nowMs = presentationTimeUs / US_PER_MS
            drawn.eraseColor(Color.TRANSPARENT)
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { painter.draw(this, nowMs, videoEndMs) }
            if (nowMs >= videoEndMs) {
                copy(0, height)
            } else {
                copy(laneBandTop, height)
                // the opening title is copied while it shows, and once more after it, so that its last frame is wiped
                val opening = painter.openingShows(nowMs, videoEndMs)
                if (opening || openingInUpload) copy(0, openingBandBottom)
                openingInUpload = opening
            }
            settled = nowMs >= settledAtMs
            return upload
        }

        /** The rows from [top] to [bottom] of what is drawn, into [upload]: getPixels gives the colours unmultiplied, an unmultiplied bitmap keeps them so. */
        private fun copy(top: Int, bottom: Int) {
            val rows = bottom - top
            if (rows <= 0) return
            drawn.getPixels(pixels, 0, width, 0, top, width, rows)
            upload.setPixels(pixels, 0, width, 0, top, width, rows)
        }
    }

    private companion object {
        const val TAG = "NotesVideoRenderer"
        const val PICTURE_SUFFIX = ".picture.mp4"
        const val LAST_FRAME_SUFFIX = ".last.jpg"
        const val US_PER_MS = 1_000L
        const val HALF_TURN = 180
        const val PROGRESS_EVERY_MS = 100L

        /** The last frame is asked a little before the end: a retriever finds no frame at the very end of some files. */
        const val LAST_FRAME_BACK_US = 50_000L
        const val LAST_FRAME_QUALITY = 92

        /** A video of 30.02 frames is a video of 30. */
        const val FRAME_RATE_SLACK = 0.5f
    }
}
