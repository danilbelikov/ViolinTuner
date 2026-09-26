package com.violinjourney.app.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupEndMarkTest {
    @Test
    fun `a mark says the entries and the bytes it was written with`() {
        assertEquals("entries=4\nbytes=1360000\n", BackupEndMark.text(4, 1_360_000))
        assertEquals(4 to 1_360_000L, BackupEndMark.read(BackupEndMark.text(4, 1_360_000)))
    }

    @Test
    fun `a mark of an older copy says the entries only and garbage says nothing`() {
        assertEquals(3 to null, BackupEndMark.read("entries=3\n"))
        assertEquals(null to null, BackupEndMark.read("garbage"))
        assertEquals(null to null, BackupEndMark.read(""))
    }

    @Test
    fun `an archive agrees with its mark only in what the mark says`() {
        val seen = BackupSeen(entries = 3, bytes = 500, hasDatabase = true)
        assertTrue(BackupEndMark.agrees("entries=3\nbytes=500\n", seen))
        assertTrue(BackupEndMark.agrees("entries=3\n", seen))
        assertFalse(BackupEndMark.agrees("entries=2\n", seen))
        assertFalse(BackupEndMark.agrees("entries=3\nbytes=5\n", seen))
    }
}
