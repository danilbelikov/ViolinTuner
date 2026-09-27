package com.violinjourney.app.core.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The same place on screen shown another file — a thumbnail whose page was replaced, the profile photo changed: the
 * picture follows the new path, rather than keeping the first one for good.
 */
@RunWith(AndroidJUnit4::class)
class SmallFileImageTest {
    @get:Rule
    val compose = createComposeRule()

    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "small-file-image-test").apply { mkdirs() }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun picture(name: String, side: Int): String {
        val file = File(dir, "$name-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.path
    }

    @Test
    fun aNewPathBringsItsOwnPicture() {
        val small = picture("small", SMALL)
        val large = picture("large", LARGE)
        var path by mutableStateOf<String?>(small)
        var image: ImageBitmap? = null
        compose.setContent { image = rememberSmallFileImage(path) }
        compose.waitUntil(TIMEOUT_MS) { image?.width == SMALL }

        compose.runOnIdle { path = large }
        compose.waitUntil(TIMEOUT_MS) { image?.width == LARGE }

        // and back: the first picture is in the cache now
        compose.runOnIdle { path = small }
        compose.waitUntil(TIMEOUT_MS) { image?.width == SMALL }

        compose.runOnIdle { path = null }
        compose.waitUntil(TIMEOUT_MS) { image == null }
        assertNull(image)
    }

    private companion object {
        const val SMALL = 4
        const val LARGE = 8
        const val TIMEOUT_MS = 2_000L
    }
}
