package com.violinjourney.app.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which part of a copy a file of the recordings goes in (spec 3.20, plan D17): sound — of a take, and from a file — or video. */
class BackupPathsTest {
    @Test
    fun `the sound of a take and a sound brought in from a file are sound - whatever its extension`() {
        assertTrue(BackupPaths.isSound("sessions/take.m4a"))
        assertTrue(BackupPaths.isSound("sessions/1f2e.sound.mp3"), "an mp3 is sound: a copy «без видео» keeps it")
        assertTrue(BackupPaths.isSound("1f2e.sound.wav"))
        assertTrue(BackupPaths.isSound("sessions/1f2e.sound.3gp"), "a container that may hold a picture is sound by its mark")
        assertFalse(BackupPaths.isSound("sessions/video-1.mp4"))
        assertFalse(BackupPaths.isSound("sessions/video-1.mp4-thumb.jpg"))
        assertFalse(BackupPaths.isSound("sessions/shot.mov"))
    }

    @Test
    fun `a path goes with the part of its folder`() {
        assertEquals(BackupPart.AUDIO, BackupPaths.partOf("${BackupPaths.SESSIONS}/take.m4a"))
        assertEquals(BackupPart.AUDIO, BackupPaths.partOf("${BackupPaths.SESSIONS}/1f2e.sound.mp3"))
        assertEquals(BackupPart.VIDEO, BackupPaths.partOf("${BackupPaths.SESSIONS}/video-1.mp4"))
        assertEquals(BackupPart.VIDEO, BackupPaths.partOf("${BackupPaths.SESSIONS}/video-1.mp4-thumb.jpg"))
        assertEquals(BackupPart.AUDIO, BackupPaths.partOf("${BackupPaths.BACKINGS}/b1.m4a"))
        assertEquals(BackupPart.SHEETS, BackupPaths.partOf("${BackupPaths.SHEETS}/page-1.jpg"))
        assertEquals(BackupPart.DATA, BackupPaths.partOf(BackupPaths.DATABASE_ENTRY))
    }
}
