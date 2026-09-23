package com.violinjourney.app.core.data.repertoire

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bitmaps and EXIF only exist on a device. The source is a sheet lying on its side, red at its
 * left and blue at its right, so the turn and the fit show in two pixels.
 */
@RunWith(AndroidJUnit4::class)
class AppSheetFilesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directory = File(context.filesDir, "repertoire")
    private val cameraDirectory = File(context.cacheDir, "camera")
    private val source = File(context.cacheDir, "sheet-source.jpg")
    private val config = RepertoireConfig(pageMaxSidePx = 1_000, thumbMaxSidePx = 100)
    private val files = AppSheetFiles(context, Dispatchers.IO, config)

    @Before
    fun setUp() {
        directory.deleteRecursively()
        cameraDirectory.deleteRecursively()
        writeSource(SOURCE_WIDTH, SOURCE_HEIGHT)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
        cameraDirectory.deleteRecursively()
        source.delete()
    }

    private fun writeSource(width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }

    private fun import(): SheetFiles.Stored = runBlocking { files.import(Uri.fromFile(source).toString()) }.also { assertNotNull(it) }!!

    private fun bitmapOf(name: String): Bitmap = BitmapFactory.decodeFile(files.existing(name)!!.path)

    private fun Bitmap.isRedAt(x: Int, y: Int) = Color.red(getPixel(x, y)) > 200 && Color.blue(getPixel(x, y)) < 60

    private fun Bitmap.isBlueAt(x: Int, y: Int) = Color.blue(getPixel(x, y)) > 200 && Color.red(getPixel(x, y)) < 60

    @Test
    fun aPageKeepsItsShapeAndIsNoLargerThanAsked() {
        val stored = import()
        val page = bitmapOf(stored.fileName)
        assertEquals(1_000, page.width)
        assertEquals(500, page.height)
        assertTrue(page.isRedAt(10, 250))
        assertTrue(page.isBlueAt(990, 250))

        val thumb = bitmapOf(stored.thumbFileName)
        assertEquals(100, thumb.width)
        assertEquals(50, thumb.height)
        assertTrue(thumb.isRedAt(2, 25))
    }

    @Test
    fun aSheetPhotographedSidewaysIsTurnedUpright() {
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val page = bitmapOf(import().fileName)
        assertEquals(500, page.width)
        assertEquals(1_000, page.height)
        // Turned a quarter clockwise, the left of the source is the top.
        assertTrue(page.isRedAt(250, 10))
        assertTrue(page.isBlueAt(250, 990))
    }

    @Test
    fun aSmallPictureIsNotBlownUpAndItsThumbnailIsStillSmall() {
        writeSource(400, 300)
        val stored = import()
        assertEquals(400, bitmapOf(stored.fileName).width)
        assertEquals(100, bitmapOf(stored.thumbFileName).width)
    }

    @Test
    fun whatIsNotAPictureGivesNothingAndLeavesNoFile() {
        source.writeText("not a picture")
        assertNull(runBlocking { files.import(Uri.fromFile(source).toString()) })
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test
    fun deleteRemovesBothFilesAndOrphansAreOldFilesNobodyPointsAt() = runBlocking {
        val kept = import()
        val orphan = import()
        val fresh = import()
        val now = System.currentTimeMillis()
        listOf(orphan.fileName, orphan.thumbFileName).forEach { files.existing(it)!!.setLastModified(now - 3_600_000) }
        listOf(kept.fileName, kept.thumbFileName).forEach { files.existing(it)!!.setLastModified(now - 3_600_000) }

        files.deleteOrphans(referenced = setOf(kept.fileName, kept.thumbFileName), nowEpochMs = now, minAgeMs = 600_000)
        assertNotNull(files.existing(kept.fileName))
        assertNull(files.existing(orphan.fileName))
        assertNull(files.existing(orphan.thumbFileName))
        assertNotNull("an import in progress is not an orphan yet", files.existing(fresh.fileName))

        files.delete(listOf(kept.fileName, kept.thumbFileName))
        assertNull(files.existing(kept.fileName))
        assertNull(files.existing(kept.thumbFileName))
    }

    @Test
    fun aCameraShotIsImportedLikeAnyPictureAndAbandonedShotsAreCleanedUp() = runBlocking {
        val shot = files.newCameraFile()
        source.copyTo(shot)
        val stored = files.import(Uri.fromFile(shot).toString())
        assertNotNull(stored)

        shot.setLastModified(System.currentTimeMillis() - 3_600_000)
        files.deleteOrphans(referenced = setOf(stored!!.fileName, stored.thumbFileName), System.currentTimeMillis(), 600_000)
        assertTrue(!shot.exists())
        assertNotNull(files.existing(stored.fileName))
    }

    private companion object {
        const val SOURCE_WIDTH = 3_000
        const val SOURCE_HEIGHT = 1_500
    }
}
