package com.violinjourney.app.core.data.repertoire

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.net.toUri
import com.violinjourney.app.core.data.image.ImageImport
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.io.isOwnFileName
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Sheet photos in `files/repertoire/`; shots of the camera pass through `cache/camera/`. File work is on the I/O dispatcher. */
class AppSheetFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val config: RepertoireConfig,
) : SheetFiles {
    private val directory = File(context.filesDir, DIRECTORY)
    private val cameraDirectory = File(context.cacheDir, CAMERA_DIRECTORY)

    override suspend fun import(sourceUri: String): SheetFiles.Stored? = withContext(io) {
        val uri = sourceUri.toUri()
        try {
            val page = ImageImport.fitted({ context.contentResolver.openInputStream(uri) }, config.pageMaxSidePx)
                ?: return@withContext null
            val thumb = ImageImport.scaledDown(page, config.thumbMaxSidePx)
            directory.mkdirs()
            val id = UUID.randomUUID().toString()
            val stored = SheetFiles.Stored(fileName = "$id$EXTENSION", thumbFileName = "$id$THUMB_SUFFIX$EXTENSION")
            val written = write(page, stored.fileName) && write(thumb, stored.thumbFileName)
            if (thumb !== page) thumb.recycle()
            page.recycle()
            if (written) {
                stored
            } else {
                File(directory, stored.fileName).delete()
                File(directory, stored.thumbFileName).delete()
                null
            }
        } catch (e: IOException) {
            Log.w(TAG, "could not read the picture", e)
            null
        } catch (e: SecurityException) {
            // The grant of a picked photo is temporary; a stale uri ends here.
            Log.w(TAG, "no access to the picture", e)
            null
        }
    }

    // Written under another name first: a file with the final name is always whole.
    private fun write(bitmap: Bitmap, name: String): Boolean {
        val target = File(directory, name)
        val partial = File(directory, name + PARTIAL_SUFFIX)
        val written = partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, config.pageJpegQuality, it) }
        if (written && partial.renameTo(target)) return true
        partial.delete()
        return false
    }

    // Names come from the database, and a database may come from a copy: a name that leaves the folder is not one of ours.
    override fun existing(name: String): File? = if (isOwnFileName(name)) File(directory, name).takeIf { it.isFile } else null

    override suspend fun delete(names: Collection<String>) {
        withContext(io) { names.filter(::isOwnFileName).forEach { File(directory, it).delete() } }
    }

    override suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) {
        withContext(io) {
            directory.listFiles()
                ?.filter { it.name !in referenced && nowEpochMs - it.lastModified() >= minAgeMs }
                ?.forEach { it.delete() }
            // A shot the camera wrote and nobody imported: the app died in between.
            cameraDirectory.listFiles()?.filter { nowEpochMs - it.lastModified() >= minAgeMs }?.forEach { it.delete() }
        }
    }

    override fun newCameraFile(): File {
        cameraDirectory.mkdirs()
        return File(cameraDirectory, "${UUID.randomUUID()}$EXTENSION")
    }

    private companion object {
        const val TAG = "SheetFiles"
        const val DIRECTORY = "repertoire"
        const val CAMERA_DIRECTORY = "camera"
        const val EXTENSION = ".jpg"
        const val THUMB_SUFFIX = "-thumb"
        const val PARTIAL_SUFFIX = ".part"
    }
}
