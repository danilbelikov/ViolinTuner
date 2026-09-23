package com.violinjourney.app.core.recording.video

import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.audio.recording.AppSessionAudioFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.testing.TestVideo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Videos of takes on a real disk, read by the real retriever. Works in `files/sessions/` beside whatever is there, and cleans up after itself. */
@RunWith(AndroidJUnit4::class)
class AppVideoFilesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val config = RepertoireConfig()
    private val files = AppVideoFiles(context, config, Dispatchers.IO)
    private val audioFiles = AppSessionAudioFiles(context, config)
    private val made = mutableListOf<File>()
    private val scratch = File(context.cacheDir, "video-files-test").apply { mkdirs() }

    @After
    fun tearDown() {
        made.forEach(files::discard)
        scratch.deleteRecursively()
    }

    private fun shoot(seconds: Int = 2, withSound: Boolean = true, rotation: Int = 0): File =
        TestVideo.make(files.newCameraFile(), seconds, withSound, rotation)

    @Test
    fun aShotMovesInFromTheCacheAndTellsAboutItself() {
        val shot = shoot(seconds = 3)
        val stored = files.adopt(shot)!!.also { made += it }
        assertFalse("moved, not copied", shot.exists())
        assertEquals(File(context.filesDir, "sessions"), stored.parentFile)
        assertEquals(stored, files.existing(stored.name))

        val info = files.info(stored)!!
        assertEquals(3_000.0, info.durationMs.toDouble(), 200.0)
        assertEquals(TestVideo.WIDTH to TestVideo.HEIGHT, info.width to info.height)
        assertTrue(info.hasSound)
    }

    @Test
    fun aTurnedVideoIsMeasuredAsItIsSeen() {
        val stored = files.adopt(shoot(rotation = 90))!!.also { made += it }
        val info = files.info(stored)!!
        assertEquals(TestVideo.HEIGHT to TestVideo.WIDTH, info.width to info.height)
    }

    @Test
    fun aMuteVideoSaysSo() {
        val stored = files.adopt(shoot(withSound = false))!!.also { made += it }
        assertFalse(files.info(stored)!!.hasSound)
    }

    @Test
    fun whatIsNotAVideoHasNothingToTell() {
        val junk = File(scratch, "junk.mp4").apply { writeBytes(ByteArray(2_048) { it.toByte() }) }
        assertNull(files.info(junk))
        assertNull(files.adopt(File(scratch, "missing.mp4")))
    }

    @Test
    fun aPickedVideoIsCopiedWholeAndLeavesNoPartialFile() = runBlocking {
        val source = TestVideo.make(File(scratch, "picked.mp4"), seconds = 2)
        val stored = files.import(source.toUri().toString())!!.also { made += it }
        assertEquals(source.length(), stored.length())
        assertTrue(source.exists())
        assertTrue(stored.parentFile!!.listFiles()!!.none { it.name.endsWith(".part") })
        assertNull(files.import(File(scratch, "missing.mp4").toUri().toString()))
        assertTrue(stored.parentFile!!.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun theThumbnailIsSmallAndGoesWithItsVideo() {
        val stored = files.adopt(shoot())!!.also { made += it }
        assertNull(files.thumbOf(stored.name))
        assertTrue(files.makeThumb(stored))
        val thumb = files.thumbOf(stored.name)!!
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(thumb.path, bounds)
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= config.thumbMaxSidePx)

        // deleting the session's sound — which for a video take is the video — takes the thumbnail along
        audioFiles.delete(stored.name)
        assertNull(files.existing(stored.name))
        assertFalse(thumb.exists())
    }

    @Test
    fun anOrphanVideoIsGivenADayAndAStoredOneKeepsItsThumbnail() {
        val kept = files.adopt(shoot())!!.also { made += it }
        val orphan = files.adopt(shoot())!!.also { made += it }
        val oldOrphan = files.adopt(shoot())!!.also { made += it }
        listOf(kept, orphan, oldOrphan).forEach { assertTrue(files.makeThumb(it)) }
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        // older than any take could be, younger than a day
        listOf(kept, orphan).forEach { it.setLastModified(now - 2 * hour); files.thumbOf(it.name)!!.setLastModified(now - 2 * hour) }
        oldOrphan.setLastModified(now - 25 * hour)
        files.thumbOf(oldOrphan.name)!!.setLastModified(now - 25 * hour)
        // whatever else lives in the folder is somebody's session: it is referenced, so that the test removes nothing of theirs
        val others = File(context.filesDir, "sessions").listFiles().orEmpty().map { it.name }.filter { name -> made.none { name.startsWith(it.nameWithoutExtension) } }

        audioFiles.deleteOrphans(referenced = others.toSet() + kept.name, nowEpochMs = now, minAgeMs = hour)

        assertNotNull(files.existing(kept.name))
        assertNotNull(files.thumbOf(kept.name))
        assertNotNull("it may be the only copy of a shot", files.existing(orphan.name))
        assertNull(files.existing(oldOrphan.name))
        assertNull(files.thumbOf(oldOrphan.name))
    }
}
