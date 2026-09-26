package com.violinjourney.app.core.io

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * fsync(2) on a real descriptor of the platform: a file of the phone is synced, a pipe — what a document provider hands
 * out for a cloud folder — has nothing to sync and passes, and a descriptor that cannot be synced for any other reason
 * fails the copy (spec 5.14).
 */
@RunWith(AndroidJUnit4::class)
class SyncToDiskTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = File(context.cacheDir, "sync-to-disk.bin")

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun aFileOfThePhoneIsSynced() {
        FileOutputStream(file).use { out ->
            out.write(ByteArray(4_096) { 7 })
            out.syncToDisk()
        }
        assertEquals(4_096L, file.length())
    }

    @Test
    fun aPipeOfAProviderHasNothingToSyncAndPasses() {
        val (read, write) = ParcelFileDescriptor.createPipe()
        read.use {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                out.write(ByteArray(1_024) { 7 })
                out.syncToDisk()
            }
        }
    }

    @Test
    fun aDescriptorThatCannotBeSyncedFailsTheCopy() {
        val out = FileOutputStream(file)
        out.close()
        assertThrows(IOException::class.java) { out.syncToDisk() }
    }
}
