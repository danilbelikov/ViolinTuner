package com.example.violintuner.core.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.violintuner.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Photos in `files/profile/`, one at a time; all file work happens on the I/O dispatcher. */
class AppAvatarFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) : AvatarFiles {
    private val directory = File(context.filesDir, DIRECTORY)

    override suspend fun import(sourceUri: String): String? = withContext(io) {
        val uri = Uri.parse(sourceUri)
        try {
            val square = AvatarImage.squareOf { context.contentResolver.openInputStream(uri) } ?: return@withContext null
            directory.mkdirs()
            val target = File(directory, "$PREFIX${clock.millis()}$EXTENSION")
            // Written under another name first: a file with the final name is always whole.
            val partial = File(directory, target.name + PARTIAL_SUFFIX)
            val written = partial.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            square.recycle()
            if (written && partial.renameTo(target)) {
                target.name
            } else {
                partial.delete()
                null
            }
        } catch (e: IOException) {
            Log.w(TAG, "could not read the picked photo", e)
            null
        } catch (e: SecurityException) {
            // The grant of a picked photo is temporary; a stale uri ends here.
            Log.w(TAG, "no access to the picked photo", e)
            null
        }
    }

    override fun existing(name: String): File? = File(directory, name).takeIf { it.isFile }

    override suspend fun delete(name: String) {
        withContext(io) { File(directory, name).delete() }
    }

    override suspend fun deleteOrphans(referenced: String?) {
        withContext(io) {
            directory.listFiles()?.filter { it.name != referenced }?.forEach { it.delete() }
        }
    }

    private companion object {
        const val TAG = "AvatarFiles"
        const val DIRECTORY = "profile"
        const val PREFIX = "avatar-"
        const val EXTENSION = ".jpg"
        const val PARTIAL_SUFFIX = ".part"
        const val JPEG_QUALITY = 90
    }
}
