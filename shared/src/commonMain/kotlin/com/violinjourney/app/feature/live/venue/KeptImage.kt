package com.violinjourney.app.feature.live.venue

import androidx.compose.runtime.mutableIntStateOf
import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock

/**
 * When a picture that stands still is to become an image, and whether an image made for a mark is
 * still the picture. It remembers only the last mark it tried: a frame of another mark forgets it, so
 * a picture that comes back — the light that came and went, «убрать анимации» — is tried again, and
 * one that could not be made is not tried again on every frame while it stands. Pure, with tests.
 */
internal class KeptPlan<M : Any> {
    private var tried: M? = null

    /** For a frame of [mark]: true once per stretch of frames of that mark that stand [still]. */
    fun makes(mark: M, still: Boolean): Boolean {
        if (tried != null && tried != mark) tried = null
        if (!still || tried != null) return false
        tried = mark
        return true
    }

    /** Whether an image made for [mark] is still the picture: no frame of another mark came since it was asked for. */
    fun wants(mark: M): Boolean = tried == mark

    /** Nothing is wanted any more: the picture has left the screen. */
    fun forget() {
        tried = null
    }
}

/**
 * The image of a picture that stands still, and whose it is to free ([free]): every image made is
 * freed exactly once — when a frame of another mark comes, when it comes too late to be wanted, or
 * when the picture leaves the screen ([release]). Images are made off the main thread and handed in
 * by [made] from any thread; the frames ask on the main thread.
 */
internal class KeptImage<M : Any, I : Any>(private val free: (I) -> Unit) {
    private val lock = PlatformLock()
    private val plan = KeptPlan<M>()
    private var released = false
    private var shown: I? = null
    private var shownMark: M? = null

    // read while drawing, bumped when an image comes: the picture is drawn again with it
    private val arrivals = mutableIntStateOf(0)

    /**
     * On the main thread, in a frame of [mark]: the image to lay down, or null — draw the picture. An image
     * of another mark is freed; [make] is asked for one when the picture stands [still] and none was tried
     * for this mark since it came, and hands it in by [made].
     */
    fun frame(mark: M, still: Boolean, make: (M) -> Unit): I? {
        arrivals.intValue // only read: the frame that asks is drawn again when an image comes
        var old: I? = null
        val makes = lock.withLock {
            if (shownMark != mark) {
                old = shown
                shown = null
                shownMark = null
            }
            !released && plan.makes(mark, still)
        }
        old?.let(free)
        if (makes) make(mark)
        return lock.withLock { shown }
    }

    /** From any thread: the image made for [mark]; kept if it is still the picture, freed at once if not. Null — it could not be made. */
    fun made(mark: M, image: I?) {
        if (image == null) return
        val keep = lock.withLock {
            val wanted = !released && plan.wants(mark) && shown == null
            if (wanted) {
                shown = image
                shownMark = mark
                arrivals.intValue += 1
            }
            wanted
        }
        if (!keep) free(image)
    }

    /** The picture has left the screen: its image is freed, and so is any that comes later. */
    fun release() {
        val old = lock.withLock {
            released = true
            plan.forget()
            shownMark = null
            shown.also { shown = null }
        }
        old?.let(free)
    }
}
