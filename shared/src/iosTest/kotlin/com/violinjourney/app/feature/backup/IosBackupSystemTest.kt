package com.violinjourney.app.feature.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController

/**
 * The system of a copy on iOS wired to its gate (spec 5.29 R8, the lead's fix of stage 122): a picker goes up once for a press and
 * again only after its delegate has answered — a cancel as a pick — and the sheet of «Поделиться» and the browser go up once and hold
 * every window for the hold. What would be put in front of the app is only counted here, and the delegates are answered by hand.
 */
class IosBackupSystemTest {
    private var clockMs = 1_000L
    private val presented = mutableListOf<UIViewController>()
    private val shared = mutableListOf<String>()
    private val opened = mutableListOf<NSURL>()
    private val places = mutableListOf<String?>()
    private val copies = mutableListOf<String?>()

    private fun system() = iosBackupSystem(
        gate = SystemWindowGate(now = { clockMs }),
        onPlacePicked = { places += it },
        onCopyPicked = { copies += it },
        restart = {},
        present = { presented += it },
        share = { shared += it },
        openUrl = { opened += it },
    )

    /** The picker put up last. */
    private fun picker(): UIDocumentPickerViewController = assertIs<UIDocumentPickerViewController>(presented.last())

    @Test
    fun `a picker goes up once for a press and again once its delegate has answered a cancel`() {
        val system = system()
        system.pickPlace(FILE)
        system.pickPlace(FILE)
        system.pickCopy()
        system.shareFile(PATH)
        system.openPrivacyPolicy()
        assertEquals(1, presented.size, "one «Сохранить как…» for a double tap, and nothing else while it is up")
        assertEquals(emptyList(), shared)
        assertEquals(emptyList(), opened)
        val picker = picker()
        assertNotNull(picker.delegate, "the delegate is kept by the system: UIKit holds it weakly").documentPickerWasCancelled(picker)
        assertEquals(listOf<String?>(null), places, "the cancel reaches the screen")
        system.pickPlace(FILE)
        assertEquals(2, presented.size, "and the button answers again")
    }

    @Test
    fun `a copy picked is handed on and the next picker goes up`() {
        val system = system()
        system.pickCopy()
        val picker = picker()
        val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + FILE)
        assertNotNull(picker.delegate).documentPicker(picker, didPickDocumentsAtURLs = listOf(file))
        assertEquals(listOf<String?>(file.absoluteString), copies)
        system.pickCopy()
        assertEquals(2, presented.size)
    }

    @Test
    fun `the sheet and the browser go up once for a press and hold the pickers for the hold`() {
        val system = system()
        system.shareFile(PATH)
        system.shareFile(PATH)
        system.openPrivacyPolicy()
        system.pickPlace(FILE)
        assertEquals(listOf(PATH), shared, "one sheet for a double tap")
        assertEquals(emptyList(), opened)
        assertEquals(emptyList(), presented)
        clockMs += SystemWindowGate.SHEET_HOLD_MS
        system.openPrivacyPolicy()
        system.openPrivacyPolicy()
        assertEquals(1, opened.size, "one browser for a double tap")
        clockMs += SystemWindowGate.SHEET_HOLD_MS
        system.pickPlace(FILE)
        assertEquals(1, presented.size, "after the hold a picker goes up")
    }

    private companion object {
        const val FILE = "Интонация · копия · 2 октября 2026.zip"
        const val PATH = "/tmp/share/$FILE"
    }
}
