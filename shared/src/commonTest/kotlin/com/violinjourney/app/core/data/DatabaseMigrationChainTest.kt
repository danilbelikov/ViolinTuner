package com.violinjourney.app.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every installed copy of the app, on Android and on iOS, updates its database through the shared
 * [DatabaseMigrations.ALL], one step at a time — and so does a copy of the data made by an older app, once
 * it is restored. A step missing there does not lose data — there is no destructive fallback — but the app
 * then fails at start for everyone who updates. The steps' contents are checked on a device by
 * `DatabaseMigrationTest` (and the whole chain on iOS's own SQLite by `IosDatabaseMigrationTest`); this one
 * only makes sure the chain is whole, on the JVM and on iOS, so a forgotten step fails the build and never
 * reaches a store. That the schema of every version is committed is `DatabaseSchemaFilesTest`'s.
 */
class DatabaseMigrationChainTest {
    @Test
    fun `every version up to the current one has exactly one step to the next`() {
        val steps = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }
        val expected = (1 until AppDatabase.VERSION).map { it to it + 1 }
        assertEquals(expected, steps.sortedBy { it.first }, "the chain of migrations, from 1 to ${AppDatabase.VERSION}")
    }
}
