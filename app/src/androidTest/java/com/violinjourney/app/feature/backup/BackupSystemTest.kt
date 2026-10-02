package com.violinjourney.app.feature.backup

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The system of a copy on Android wired to its gate (spec 5.29 R8, the lead's fix of stage 122), as `rememberBackupSystem` builds it:
 * the launchers of «Сохранить как…» and «Открыть» go to a registry of the test that only counts what it is asked to start and answers
 * when told, the chooser and the browser to a context that only counts them. A picker goes up once for a press and again only after it
 * has answered — a cancel as a pick; the sheet and the browser go up once and hold every window for the hold of the gate, on the
 * phone's own clock.
 */
@RunWith(AndroidJUnit4::class)
class BackupSystemTest {
    @get:Rule
    val compose = createComposeRule()

    /** The request codes of the pickers put up, in order. */
    private val launched = CopyOnWriteArrayList<Int>()
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += requestCode
        }
    }
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }

    /** What the chooser and the browser were started with. */
    private val started = CopyOnWriteArrayList<Intent>()
    private val places = CopyOnWriteArrayList<String?>()
    private val copies = CopyOnWriteArrayList<String?>()
    private lateinit var system: BackupSystem
    private lateinit var cache: File

    private fun show() {
        compose.setContent {
            val base = LocalContext.current
            val counting = remember(base) {
                cache = base.cacheDir
                object : ContextWrapper(base) {
                    override fun startActivity(intent: Intent) {
                        started += intent
                    }
                }
            }
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner, LocalContext provides counting) {
                system = rememberBackupSystem(onPlacePicked = { places += it }, onCopyPicked = { copies += it })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aPickerGoesUpOnceForAPressAndAgainOnceItHasAnsweredACancel() {
        show()
        compose.runOnIdle {
            system.pickPlace(FILE)
            system.pickPlace(FILE)
            system.pickCopy()
            system.openPrivacyPolicy()
        }
        compose.waitForIdle()
        assertEquals("one «Сохранить как…» for a double tap, and nothing else while it is up", 1, launched.size)
        assertEquals(emptyList<Intent>(), started.toList())
        compose.runOnIdle { registry.dispatchResult(launched.single(), Activity.RESULT_CANCELED, null) }
        assertEquals("the cancel reaches the screen", listOf<String?>(null), places.toList())
        compose.runOnIdle { system.pickPlace(FILE) }
        assertEquals("and the button answers again", 2, launched.size)
    }

    @Test
    fun aCopyPickedIsHandedOnAndTheNextPickerGoesUp() {
        show()
        compose.runOnIdle { system.pickCopy() }
        val picked = Uri.parse("content://com.android.providers.downloads.documents/document/1")
        compose.runOnIdle { registry.dispatchResult(launched.single(), Activity.RESULT_OK, Intent().setData(picked)) }
        assertEquals(listOf<String?>(picked.toString()), copies.toList())
        compose.runOnIdle { system.pickCopy() }
        assertEquals(2, launched.size)
    }

    @Test
    fun theSheetGoesUpOnceForAPressAndThePickersWaitForTheHold() {
        show()
        val file = File(cache, "share/$FILE").apply {
            parentFile?.mkdirs()
            writeText("PK")
        }
        compose.runOnIdle {
            system.shareFile(file.path)
            system.shareFile(file.path)
            system.pickPlace(FILE)
        }
        // the chooser is started once its title is read
        compose.waitUntil(WAIT_MS) { started.isNotEmpty() }
        compose.waitForIdle()
        assertEquals("one chooser for a double tap", 1, started.size)
        assertEquals("no picker over it", emptyList<Int>(), launched.toList())
        file.delete()
    }

    @Test
    fun theBrowserGoesUpOnceForAPressAndAPickerAfterTheHold() {
        show()
        compose.runOnIdle {
            system.openPrivacyPolicy()
            system.openPrivacyPolicy()
            system.pickPlace(FILE)
        }
        compose.waitForIdle()
        assertEquals("one browser for a double tap", 1, started.size)
        assertEquals("no picker over it", emptyList<Int>(), launched.toList())
        // the hold goes by the phone's clock (SystemClock.elapsedRealtime), not the test's
        Thread.sleep(SystemWindowGate.SHEET_HOLD_MS + PAST_HOLD_MS)
        compose.runOnIdle { system.pickPlace(FILE) }
        assertEquals("after the hold a picker goes up", 1, launched.size)
    }

    private companion object {
        const val FILE = "Интонация · копия · 2 октября 2026.zip"
        const val WAIT_MS = 5_000L
        const val PAST_HOLD_MS = 200L
    }
}
