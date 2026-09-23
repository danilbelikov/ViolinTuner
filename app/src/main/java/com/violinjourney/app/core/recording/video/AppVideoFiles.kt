package com.violinjourney.app.core.recording.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AppVideoFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: RepertoireConfig,
    @IoDispatcher private val io: CoroutineDispatcher,
) : VideoFiles {
    private val directory = File(context.filesDir, DIRECTORY)
    private val cameraDirectory = File(context.cacheDir, CAMERA_DIRECTORY)

    override fun newCameraFile(): File {
        cameraDirectory.mkdirs()
        return File(cameraDirectory, "${UUID.randomUUID()}$EXTENSION")
    }

    override fun adopt(cameraFile: File): File? {
        if (!cameraFile.isFile || cameraFile.length() == 0L) return null
        directory.mkdirs()
        val target = File(directory, "${UUID.randomUUID()}$EXTENSION")
        // cache and files are one volume, so this is a rename; the copy is for the phone where they are not
        if (cameraFile.renameTo(target)) return target
        return try {
            cameraFile.copyTo(target, overwrite = true)
            cameraFile.delete()
            target
        } catch (e: IOException) {
            Log.w(TAG, "cannot move the shot in", e)
            target.delete()
            null
        }
    }

    override fun sizeOf(uri: String): Long? = try {
        context.contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "no size for $uri", e)
        null
    }

    override fun freeBytes(): Long {
        directory.mkdirs()
        return StatFs(directory.path).availableBytes
    }

    override suspend fun import(uri: String): File? = withContext(io) {
        directory.mkdirs()
        val target = File(directory, "${UUID.randomUUID()}$EXTENSION")
        val part = File(directory, target.name + PARTIAL_SUFFIX)
        try {
            val input = context.contentResolver.openInputStream(uri.toUri()) ?: return@withContext null
            input.use { from ->
                part.outputStream().use { to ->
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = from.read(buffer)
                        if (read < 0) break
                        to.write(buffer, 0, read)
                    }
                }
            }
            if (part.renameTo(target)) target else null
        } catch (e: IOException) {
            Log.w(TAG, "cannot copy $uri", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "may not read $uri", e)
            null
        } finally {
            // whatever is left under the partial name — a failure, a cancellation — is not a video
            part.delete()
        }
    }

    override fun info(file: File): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            fun number(key: Int) = retriever.extractMetadata(key)?.toLongOrNull()
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == null) return null
            val rotation = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            val width = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val height = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            val turned = rotation % HALF_TURN != 0L
            VideoInfo(
                durationMs = number(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: 0,
                // as it is seen, not as it is stored
                width = if (turned) height else width,
                height = if (turned) width else height,
                createdAtEpochMs = VideoDates.parse(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)),
                hasSound = hasSoundTrack(file),
            )
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "not a video: ${file.name}", e)
            null
        } catch (e: RuntimeException) {
            // setDataSource throws a bare RuntimeException on what it cannot parse
            Log.w(TAG, "cannot read ${file.name}", e)
            null
        } finally {
            retriever.release()
        }
    }

    // METADATA_KEY_HAS_AUDIO says "yes" for a track no decoder here may open; the extractor is what the analysis will use.
    private fun hasSoundTrack(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        } catch (e: IOException) {
            Log.w(TAG, "cannot look for sound in ${file.name}", e)
            false
        } finally {
            extractor.release()
        }
    }

    override fun makeThumb(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val first = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return false
            // a video that fades in from black: the frame a second in says more
            val frame = if (isDark(first)) retriever.getFrameAtTime(SECOND_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: first else first
            val scale = config.thumbMaxSidePx.toFloat() / maxOf(frame.width, frame.height)
            val small = if (scale < 1f) Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true) else frame
            thumbFile(file.name).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it) }
        } catch (e: IOException) {
            Log.w(TAG, "no thumbnail for ${file.name}", e)
            false
        } catch (e: RuntimeException) {
            Log.w(TAG, "no frame in ${file.name}", e)
            false
        } finally {
            retriever.release()
        }
    }

    private fun isDark(frame: Bitmap): Boolean {
        var sum = 0L
        var count = 0
        val stepX = (frame.width / SAMPLES_PER_SIDE).coerceAtLeast(1)
        val stepY = (frame.height / SAMPLES_PER_SIDE).coerceAtLeast(1)
        for (y in 0 until frame.height step stepY) {
            for (x in 0 until frame.width step stepX) {
                val pixel = frame.getPixel(x, y)
                sum += ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3
                count++
            }
        }
        return count > 0 && sum / count < DARK_BELOW
    }

    private fun thumbFile(name: String) = File(directory, name.removeSuffix(EXTENSION) + THUMB_SUFFIX)

    override fun thumbOf(name: String): File? = thumbFile(name).takeIf { it.parentFile == directory && it.isFile }

    // Names come from the database; a name with a path in it is not one of ours.
    override fun existing(name: String): File? = File(directory, name).takeIf { it.parentFile == directory && it.isFile }

    override fun discard(file: File) {
        thumbFile(file.name).delete()
        file.delete()
    }

    private companion object {
        const val TAG = "VideoFiles"
        const val DIRECTORY = "sessions"
        const val CAMERA_DIRECTORY = "camera"
        const val EXTENSION = ".mp4"
        const val THUMB_SUFFIX = "-thumb.jpg"
        const val PARTIAL_SUFFIX = ".part"
        const val COPY_BUFFER = 256 * 1024
        const val THUMB_QUALITY = 85
        const val SECOND_US = 1_000_000L
        const val HALF_TURN = 180L
        const val SAMPLES_PER_SIDE = 16
        const val DARK_BELOW = 16
    }
}
