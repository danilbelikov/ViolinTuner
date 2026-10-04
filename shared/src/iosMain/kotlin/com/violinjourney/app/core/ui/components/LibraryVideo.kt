package com.violinjourney.app.core.ui.components

import com.violinjourney.app.core.io.PickedCopies
import com.violinjourney.app.core.io.pathOfFileUri
import com.violinjourney.app.core.recording.video.VideoPick
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.IOException
import platform.Foundation.NSError
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.NSUnderlyingErrorKey
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.UniformTypeIdentifiers.conformsToType
import platform.darwin.NSObject

/**
 * Answers the moment a video is picked — with the library's promise of its file ([LibraryVideo]), made only when the importer asks
 * (spec 3.19, 0.94) — or with null when the picker was closed. Until 0.94 the answer waited for the file, and the screen said nothing
 * meanwhile: a minute of a download from iCloud looked as if nothing had been picked.
 */
internal class VideoPickerDelegate(private val onPicked: (VideoPick?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        onPicked(provider?.let { LibraryVideo(it) }?.let { video -> VideoPick.Coming { video.make() } })
    }
}

/**
 * The file of a video picked in the photo library (spec 3.19, 5.13). The library makes it when asked — downloads it from iCloud,
 * renders a trim, a slow motion or a cinematic video — and lends it for as long as its callback runs: it is copied into `tmp/picked/`
 * there ([PickedCopies]; on APFS a clone, not a second gigabyte), and the copy is the app's. The type asked for is that of the video's
 * own file — `com.apple.quicktime-movie` from the camera of an iPhone —, not `public.movie`: for that one the library may hand over a
 * re-encoded file, which is minutes more, or none at all. Cancelling the wait cancels the library's progress, and a copy made a moment
 * before goes.
 */
@OptIn(ExperimentalForeignApi::class)
internal class LibraryVideo(private val provider: NSItemProvider) {
    /** The `file:` URI of the app's copy of the video; throws when the library did not hand it over. */
    suspend fun make(): String {
        val handed = CompletableDeferred<String>()
        val progress = provider.loadFileRepresentationForTypeIdentifier(movieTypeOf(provider)) { lent, error -> hand(handed, lent, error) }
        return try {
            handed.await()
        } catch (e: CancellationException) {
            // closed before the library is told: a callback on its way finds it closed and copies nothing
            val late = !handed.completeExceptionally(e)
            progress.cancel()
            // whoever comes second lets the copy go — here, a copy made a moment before; in the callback, one made a moment after
            if (late) handed.copyOrNull()?.let(::releaseCopy)
            throw e
        }
    }

    /** On a queue of the system's; the lent file is gone once this returns. */
    private fun hand(handed: CompletableDeferred<String>, lent: NSURL?, error: NSError?) {
        // stopped meanwhile: nothing to keep
        if (handed.isCompleted) return
        val copy = lent?.let(::copyToTemporary)
        when {
            copy == null -> handed.completeExceptionally(IOException("the library did not hand the video over: ${reasonOf(error)}"))
            !handed.complete(copy) -> releaseCopy(copy)
        }
    }

    private companion object {
        const val MOVIE_TYPE = "public.movie"

        /**
         * The type of the video's own file among those the library offers: a provider gives no file for `public.movie` when what it has
         * is `com.apple.quicktime-movie` («Cannot load representation of type public.movie»). The first that is a movie; the first of all
         * when the types cannot tell (a process LaunchServices knows nothing of — a test — takes every type for a stranger); `public.movie`
         * when the library names none.
         */
        fun movieTypeOf(provider: NSItemProvider): String {
            val offered = provider.registeredTypeIdentifiers.mapNotNull { it as? String }
            return offered.firstOrNull { UTType.typeWithIdentifier(it)?.conformsToType(UTTypeMovie) == true } ?: offered.firstOrNull() ?: MOVIE_TYPE
        }

        /** What went wrong, with what the provider wrapped: its own error says only that the item is unavailable. */
        fun reasonOf(error: NSError?): String {
            if (error == null) return "no file and no error"
            val wrapped = error.userInfo[NSUnderlyingErrorKey] as? NSError
            return "${error.domain} ${error.code} ${error.localizedDescription}" + wrapped?.let { " ← ${it.domain} ${it.code} ${it.localizedDescription}" }.orEmpty()
        }

        fun releaseCopy(uri: String) {
            pathOfFileUri(uri)?.let { PickedCopies.release(it) }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun CompletableDeferred<String>.copyOrNull(): String? = if (isCompleted) runCatching { getCompleted() }.getOrNull() else null
    }
}
