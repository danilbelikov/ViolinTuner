package com.violinjourney.app.core.data.profile

import java.io.File

/** Avatar storage without files: names only. An import of [unreadableUri] fails. */
class FakeAvatarFiles(private val unreadableUri: String = "content://broken") : AvatarFiles {
    val names = mutableSetOf<String>()
    private var imports = 0

    override suspend fun import(sourceUri: String): String? {
        if (sourceUri == unreadableUri) return null
        return "avatar-${++imports}.jpg".also { names += it }
    }

    override fun existing(name: String): File? = if (name in names) File(name) else null

    override suspend fun delete(name: String) {
        names -= name
    }

    override suspend fun deleteOrphans(referenced: String?) {
        names.retainAll(setOfNotNull(referenced))
    }
}
