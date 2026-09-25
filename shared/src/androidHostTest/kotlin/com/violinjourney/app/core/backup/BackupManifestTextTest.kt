package com.violinjourney.app.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The passport of an iPhone's copy is read by Android's Properties, and the other way round (spec 3.20). */
class BackupManifestTextTest {
    private val manifest = BackupManifest(
        formatVersion = 1,
        appVersion = "1.4 (22)",
        databaseVersion = 13,
        createdAtEpochMs = 1_790_000_000_000,
        device = "iPhone de Ana: 15 Pro = #1 ! скрипка \\ 🎻",
        parts = setOf(BackupPart.DATA, BackupPart.AUDIO),
        counts = BackupCounts(sessions = 12, takes = 7, pieces = 3, pages = 9, practiceDays = 40, trophies = 2, level = 5, withSound = 11, videos = 1),
        bytes = mapOf(BackupPart.DATA to 1_234_567L, BackupPart.AUDIO to 9_876_543_210L, BackupPart.VIDEO to 0L),
    )

    @Test
    fun `what an iPhone writes Android reads`() {
        val bytes = BackupManifestText.write(manifest).encodeToByteArray()
        assertEquals(manifest, BackupManifest.readFrom(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `what Android writes an iPhone reads`() {
        val out = ByteArrayOutputStream()
        manifest.writeTo(out)
        assertEquals(manifest, BackupManifestText.read(out.toByteArray().decodeToString()))
    }

    @Test
    fun `a stranger's text is not a passport`() {
        assertNull(BackupManifestText.read("hello=world\n"))
        assertNull(BackupManifestText.read("violin-intonation-backup=1\nformat=\\u12\n"))
    }
}
