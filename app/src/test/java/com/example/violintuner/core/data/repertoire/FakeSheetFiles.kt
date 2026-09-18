package com.example.violintuner.core.data.repertoire

import java.io.File
import kotlinx.coroutines.CompletableDeferred

/** Sheet storage without files: names only. An import of a uri containing "broken" fails. */
class FakeSheetFiles : SheetFiles {
    val names = mutableSetOf<String>()
    val cameraFiles = mutableListOf<File>()
    private var imports = 0

    /** Set to make every import wait until it is completed: the moment in between can then be looked at. */
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun import(sourceUri: String): SheetFiles.Stored? {
        hold?.await()
        if ("broken" in sourceUri) return null
        val id = ++imports
        return SheetFiles.Stored("page-$id.jpg", "page-$id-thumb.jpg").also { names += listOf(it.fileName, it.thumbFileName) }
    }

    override fun existing(name: String): File? = if (name in names) File("/sheets/$name") else null

    override suspend fun delete(names: Collection<String>) {
        this.names -= names.toSet()
    }

    override suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) {
        names.retainAll(referenced)
    }

    override fun newCameraFile(): File = File.createTempFile("shot", ".jpg").also { cameraFiles += it }
}
