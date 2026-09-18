package com.example.violintuner.core.data.profile

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
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bitmaps and EXIF only exist on a device. The source is a wide picture, red on the left and
 * blue on the right, so both the crop and the turn show in two pixels.
 */
@RunWith(AndroidJUnit4::class)
class AppAvatarFilesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directory = File(context.filesDir, "profile")
    private val source = File(context.cacheDir, "avatar-source.jpg")
    private var now = 1_000L
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }
    private val files = AppAvatarFiles(context, Dispatchers.IO, clock)

    @Before
    fun setUp() {
        directory.deleteRecursively()
        val bitmap = Bitmap.createBitmap(SOURCE_WIDTH, SOURCE_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawRect(0f, 0f, SOURCE_WIDTH / 2f, SOURCE_HEIGHT.toFloat(), Paint().apply { color = Color.RED })
            drawRect(SOURCE_WIDTH / 2f, 0f, SOURCE_WIDTH.toFloat(), SOURCE_HEIGHT.toFloat(), Paint().apply { color = Color.BLUE })
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
        source.delete()
    }

    private fun imported(): Bitmap {
        val name = runBlocking { files.import(Uri.fromFile(source).toString()) }
        assertNotNull(name)
        return BitmapFactory.decodeFile(files.existing(name!!)!!.path)
    }

    private fun Bitmap.isRedAt(x: Int, y: Int) = Color.red(getPixel(x, y)) > 200 && Color.blue(getPixel(x, y)) < 60

    private fun Bitmap.isBlueAt(x: Int, y: Int) = Color.blue(getPixel(x, y)) > 200 && Color.red(getPixel(x, y)) < 60

    @Test
    fun aWidePictureBecomesASquareOfItsCentre() {
        val avatar = imported()
        assertEquals(AvatarImage.AVATAR_SIZE_PX, avatar.width)
        assertEquals(AvatarImage.AVATAR_SIZE_PX, avatar.height)
        assertTrue(avatar.isRedAt(10, 256))
        assertTrue(avatar.isBlueAt(500, 256))
    }

    @Test
    fun aPictureIsTurnedUprightByItsExifOrientation() {
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val avatar = imported()
        // Turned a quarter clockwise, the left of the source is the top.
        assertTrue(avatar.isRedAt(256, 10))
        assertTrue(avatar.isBlueAt(256, 500))
    }

    @Test
    fun aSmallPictureIsNotBlownUp() {
        val small = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        source.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val avatar = imported()
        assertEquals(120, avatar.width)
        assertEquals(120, avatar.height)
    }

    @Test
    fun whatIsNotAPictureGivesNothingAndLeavesNoFile() {
        source.writeText("not a picture")
        assertNull(runBlocking { files.import(Uri.fromFile(source).toString()) })
        assertNull(runBlocking { files.import(Uri.fromFile(File(context.cacheDir, "missing.jpg")).toString()) })
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test
    fun everyImportHasItsOwnNameAndOrphansAreRemoved() = runBlocking {
        val first = files.import(Uri.fromFile(source).toString())!!
        now = 2_000L
        val second = files.import(Uri.fromFile(source).toString())!!
        assertNotEquals(first, second)

        files.deleteOrphans(referenced = second)
        assertNull(files.existing(first))
        assertNotNull(files.existing(second))

        files.delete(second)
        assertNull(files.existing(second))
        files.deleteOrphans(referenced = null)
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test
    fun largePhotosAreDecodedAtAFractionOfTheirSize() {
        assertEquals(1, AvatarImage.sampleSizeFor(800, 600))
        assertEquals(1, AvatarImage.sampleSizeFor(1023, 4000))
        assertEquals(2, AvatarImage.sampleSizeFor(1024, 4000))
        assertEquals(4, AvatarImage.sampleSizeFor(4000, 3000))
        assertEquals(8, AvatarImage.sampleSizeFor(8160, 6120))
    }

    private companion object {
        const val SOURCE_WIDTH = 2000
        const val SOURCE_HEIGHT = 1000
    }
}
