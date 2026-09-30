package com.violinjourney.app.feature.live.block

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the paper of «Что играю» shows (spec 3.28, 3.36.6, 5.21): while it drains into the glass (a block stopped, the practice over)
 * it keeps the line and the «готово» of the last block it showed — not a full line, not the minutes back over «готово»; a new block
 * after a done one starts clean.
 */
class BookmarkPaperTest {
    private val running = Bookmark.Running(TITLE, minutesLeft = 7, progress = 0.4f)
    private val done = Bookmark.Done(TITLE)

    @Test
    fun `a running block shows its line and a done one the whole line and its done`() {
        assertEquals(0.4f, BookmarkPaper.progress(BookmarkPaper.shown(running, running)), 0f)
        assertEquals(0f, BookmarkPaper.done(BookmarkPaper.shown(running, running)), 0f)
        assertEquals(1f, BookmarkPaper.progress(BookmarkPaper.shown(done, done)), 0f)
        assertEquals(1f, BookmarkPaper.done(BookmarkPaper.shown(done, done)), 0f)
    }

    @Test
    fun `a block stopped at 40 percent drains away with its line at 40 percent`() {
        val shown = BookmarkPaper.shown(Bookmark.Entry, last = running)
        assertEquals(running, shown)
        assertEquals(0.4f, BookmarkPaper.progress(shown), 0f)
        assertEquals(0f, BookmarkPaper.done(shown), 0f)
    }

    @Test
    fun `a done block drains away done when the practice is over`() {
        val shown = BookmarkPaper.shown(Bookmark.Entry, last = done)
        assertEquals(1f, BookmarkPaper.done(shown), 0f)
        assertEquals(1f, BookmarkPaper.progress(shown), 0f)
    }

    @Test
    fun `a new block after a done one starts clean`() {
        val next = Bookmark.Running(TITLE, minutesLeft = 15, progress = 0f)
        val shown = BookmarkPaper.shown(next, last = done)
        assertEquals(next, shown)
        assertEquals(0f, BookmarkPaper.done(shown), 0f)
        assertEquals(0f, BookmarkPaper.progress(shown), 0f)
    }

    private companion object {
        const val TITLE = "D-dur · 2 октавы"
    }
}
