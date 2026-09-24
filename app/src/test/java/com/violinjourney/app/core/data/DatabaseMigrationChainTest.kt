package com.violinjourney.app.core.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every installed copy of the app updates its database through [DatabaseMigrations.ALL], one step at a
 * time. A step missing there does not lose data — there is no destructive fallback — but the app then
 * fails at start for everyone who updates. The steps' contents are checked on a device by
 * `DatabaseMigrationTest`; this one only makes sure the chain is whole, so a forgotten step fails the
 * build and never reaches a store.
 */
class DatabaseMigrationChainTest {
    @Test
    fun `every version up to the current one has exactly one step to the next`() {
        val steps = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }
        val expected = (1 until AppDatabase.VERSION).map { it to it + 1 }
        assertEquals("the chain of migrations, from 1 to ${AppDatabase.VERSION}", expected, steps.sortedBy { it.first })
    }

    @Test
    fun `the schema of every version is exported and committed`() {
        // Unit tests run in the module's directory; Room writes one file per version there (ksp room.schemaLocation).
        val folder = File("schemas/${AppDatabase::class.java.name}")
        assertTrue("no exported schemas in ${folder.absolutePath}", folder.isDirectory)
        val versions = folder.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()
        assertEquals("schema files in ${folder.path}", (1..AppDatabase.VERSION).toList(), versions)
    }
}
