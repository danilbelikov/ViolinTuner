package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.ByteOutput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.createNewOutput
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.sizeBytes
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

/**
 * The folders picked in Files for a copy. A pick gives the app the right to write into that folder for as long as it
 * holds the folder's address and says it uses it: the last picked folder is held until another one is picked.
 */
object IosPickedPlaces {
    private var held: NSURL? = null

    /**
     * The `file:` uri of [fileName] in [folder], the folder being held from now on. A name taken in that folder — a copy
     * made earlier the same day — gives way to «… (1).zip», as the system's «Save as» on Android does: an earlier copy is
     * never written over.
     */
    fun place(folder: NSURL, fileName: String): String? {
        held?.stopAccessingSecurityScopedResource()
        held = folder.takeIf { it.startAccessingSecurityScopedResource() } ?: folder
        val folderPath = folder.path ?: return null
        val files = NSFileManager.defaultManager
        // a file of iCloud not downloaded yet lies as «.name.icloud»: its name is taken all the same
        val name = freeFileName(fileName) { taken -> listOf(taken, ".$taken$ICLOUD_PLACEHOLDER").any { files.fileExistsAtPath("$folderPath/$it") } }
        return folder.URLByAppendingPathComponent(name)?.absoluteString
    }

    fun folderName(): String? = held?.lastPathComponent

    private const val ICLOUD_PLACEHOLDER = ".icloud"
}

/**
 * [fileName] when it is free, else «name (1).ext», «name (2).ext» … — the way Android names a document whose name is
 * taken (`FileUtils.buildUniqueFile`), so that a folder shared by both looks the same from either.
 */
internal fun freeFileName(fileName: String, taken: (String) -> Boolean): String {
    if (!taken(fileName)) return fileName
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    val extension = if (dot > 0) fileName.substring(dot) else ""
    var number = 1
    while (taken("$base ($number)$extension")) number++
    return "$base ($number)$extension"
}

/**
 * The places of copies on iOS are files: a `file:` uri in a folder picked in Files, or a copy the picker made. A copy
 * is written only into a file it makes itself, and only such a file goes when the copy does not get to its end.
 */
internal class IosBackupDocuments : BackupDocuments {
    /** Files made here for a copy. One job at a time, and its steps follow one another: no lock is needed. */
    private val made = mutableSetOf<String>()

    override fun openOutput(uri: String): ByteOutput? = fileOf(uri)?.createNewOutput()?.also { made += uri }

    override fun openInput(uri: String): ByteInput? = fileOf(uri)?.openInput()

    override fun delete(uri: String) {
        if (made.remove(uri)) fileOf(uri)?.deleteFile()
    }

    override fun placeOf(uri: String): String? = IosPickedPlaces.folderName()?.takeIf { fileOf(uri)?.path?.contains("/$it/") == true }

    override fun nameOf(uri: String): String? = fileOf(uri)?.path?.substringAfterLast('/')

    override fun sizeOf(uri: String): Long? = fileOf(uri)?.sizeBytes()

    private fun fileOf(uri: String): PlatformFile? = NSURL.URLWithString(uri)?.path?.let(::PlatformFile)
}
