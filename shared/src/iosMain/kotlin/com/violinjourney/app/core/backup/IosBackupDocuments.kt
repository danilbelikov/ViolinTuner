package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.ByteOutput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.sizeBytes
import platform.Foundation.NSURL

/**
 * The folders picked in Files for a copy. A pick gives the app the right to write into that folder for as long as it
 * holds the folder's address and says it uses it: the last picked folder is held until another one is picked.
 */
object IosPickedPlaces {
    private var held: NSURL? = null

    /** The `file:` uri of [fileName] in [folder], the folder being held from now on. */
    fun place(folder: NSURL, fileName: String): String? {
        held?.stopAccessingSecurityScopedResource()
        held = folder.takeIf { it.startAccessingSecurityScopedResource() } ?: folder
        return folder.URLByAppendingPathComponent(fileName)?.absoluteString
    }

    fun folderName(): String? = held?.lastPathComponent
}

/** The places of copies on iOS are files: a `file:` uri in a folder picked in Files, or a copy the picker made. */
internal class IosBackupDocuments : BackupDocuments {
    override fun openOutput(uri: String): ByteOutput? = fileOf(uri)?.openOutput()

    override fun openInput(uri: String): ByteInput? = fileOf(uri)?.openInput()

    override fun delete(uri: String) {
        fileOf(uri)?.deleteFile()
    }

    override fun placeOf(uri: String): String? = IosPickedPlaces.folderName()?.takeIf { fileOf(uri)?.path?.contains("/$it/") == true }

    override fun nameOf(uri: String): String? = fileOf(uri)?.path?.substringAfterLast('/')

    override fun sizeOf(uri: String): Long? = fileOf(uri)?.sizeBytes()

    private fun fileOf(uri: String): PlatformFile? = NSURL.URLWithString(uri)?.path?.let(::PlatformFile)
}
