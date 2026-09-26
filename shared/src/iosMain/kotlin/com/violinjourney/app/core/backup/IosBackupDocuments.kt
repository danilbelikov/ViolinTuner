package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.ByteOutput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.createNewOutput
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.sizeBytes
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingWithoutChanges
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSURL

/**
 * The folders picked in Files for a copy, and the copies picked there to bring back. A pick gives the app the right to
 * that folder or file for as long as it holds the address and says it uses it: the last picked folder, and the last
 * picked copy, are held until another one is picked.
 */
object IosPickedPlaces {
    private var held: NSURL? = null
    private var heldCopy: NSURL? = null

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

    /**
     * The `file:` uri of a copy picked to bring back, read where it lies (spec 3.20) — not a copy of it the system made in
     * the app's tmp first, which took the room of the whole archive once more and stayed there. The file is held from now
     * on, until another copy is picked.
     */
    fun copy(file: NSURL): String? {
        heldCopy?.stopAccessingSecurityScopedResource()
        heldCopy = file.takeIf { it.startAccessingSecurityScopedResource() }
        return file.absoluteString
    }

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
 * The places of copies on iOS are files: a `file:` uri in a folder picked in Files, or a copy picked there to bring back.
 * A copy is written only into a file it makes itself, and only such a file goes when the copy does not get to its end.
 */
internal class IosBackupDocuments : BackupDocuments {
    /** Files made here for a copy. One job at a time, and its steps follow one another: no lock is needed. */
    private val made = mutableSetOf<String>()

    override fun openOutput(uri: String): ByteOutput? = fileOf(uri)?.createNewOutput()?.also { made += uri }

    /**
     * A copy in iCloud Drive or in another app's storage may not lie on the phone yet: a coordinated read has it brought
     * down whole first — under «Открываем копию…», with no progress of its own. The file is opened inside the coordination
     * and stays open after it. Where the coordination itself is refused (a process the system gives no coordination to —
     * the simulator's tests), the file is opened as it lies: a file that is not on the phone then does not open, and the
     * screen says «Файл повреждён или недокачан», nothing changed. Blocking: called on the io dispatcher only.
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun openInput(uri: String): ByteInput? {
        val url = NSURL.URLWithString(uri)?.takeIf { it.path != null } ?: return null
        var input: ByteInput? = null
        val refused = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(url, NSFileCoordinatorReadingWithoutChanges, error.ptr) { at ->
                input = at?.path?.let(::PlatformFile)?.openInput()
            }
            error.value?.let { "${it.domain} ${it.code}" }
        }
        if (refused == null) return input
        input?.close()
        NSLog("Backup: no coordinated read of the copy ($refused), it is opened as it lies".replace("%", "%%"))
        return fileOf(uri)?.openInput()
    }

    override fun delete(uri: String) {
        if (made.remove(uri)) fileOf(uri)?.deleteFile()
    }

    override fun placeOf(uri: String): String? = IosPickedPlaces.folderName()?.takeIf { fileOf(uri)?.path?.contains("/$it/") == true }

    override fun nameOf(uri: String): String? = fileOf(uri)?.path?.substringAfterLast('/')

    override fun sizeOf(uri: String): Long? = fileOf(uri)?.sizeBytes()

    private fun fileOf(uri: String): PlatformFile? = NSURL.URLWithString(uri)?.path?.let(::PlatformFile)
}
