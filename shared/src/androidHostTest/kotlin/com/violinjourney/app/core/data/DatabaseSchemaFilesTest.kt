package com.violinjourney.app.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Room exports the schema of the shared database into `shared/schemas`, one file per version, beside the database and
 * its migrations; they are committed. A new version whose schema was not committed fails the build here — the
 * migration tests lay out their old files after these, and the next step of the chain is written against them.
 */
class DatabaseSchemaFilesTest {
    @Test
    fun `the schema of every version is exported and committed`() {
        // host tests run in the module's directory; Room writes there (ksp room.schemaLocation in shared/build.gradle.kts)
        val folder = File("schemas/${AppDatabase::class.java.name}")
        assertTrue(folder.isDirectory, "no exported schemas in ${folder.absolutePath}")
        val versions = folder.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()
        assertEquals((1..AppDatabase.VERSION).toList(), versions, "schema files in ${folder.path}")
    }
}
