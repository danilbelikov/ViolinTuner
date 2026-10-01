package com.violinjourney.app.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The choice of «Эталон» and the hint of «Микрофон» in a low room (spec 3.36.8, 5.29 R8; the review of stage 120). The heights are dp
 * of the emulator's 640 × 360 lying — the words of 603 × 308 have 172 between the row of the strip and the button — and of a phone:
 * the title 24/28, the words 15/22, the hint 14 in lines of 20.3 with its fields of 12, the buttons 64.
 */
class SetupWordsMathTest {

    @Test
    fun `lying on 640 x 360 the buttons of the reference stand right under the title`() {
        // «К чему настраиваетесь?» on two lines, «Частота ноты A4…» on two: the buttons would be 128…192 — under the fade from 148
        assertTrue(choiceFirst(viewport = 172f, title = 56f, text = 44f))
        // with the status bar hidden (603 × 336) still: 192 against the fade from 176
        assertTrue(choiceFirst(viewport = 200f, title = 56f, text = 44f))
    }

    @Test
    fun `where they stand whole above the edge the order of the spec stays`() {
        // 892 × 412 lying (848 × 360): a column of 392, the title and the words on one line and two — 164 against the fade from 200
        assertFalse(choiceFirst(viewport = 224f, title = 28f, text = 44f))
        // the buttons ending exactly where the fade starts (28 + 10 + 28 + 18 + 64 = 148) stand whole; a dp more — under the title
        assertFalse(choiceFirst(viewport = 172f, title = 28f, text = 28f))
        assertTrue(choiceFirst(viewport = 172f, title = 28f, text = 29f))
    }

    @Test
    fun `lying on 640 x 360 the hint of the microphone stands over the button`() {
        // the title on two lines, the words on five, the hint on three (85): 275 in 172 — pinned, the title 56 above it whole
        assertTrue(hintPinned(viewport = 172f, title = 56f, text = 110f, hint = 85f))
    }

    @Test
    fun `where all of it stands the hint stays under the words`() {
        // a phone upright: the words of «Микрофон» with the hint stand in their room
        assertFalse(hintPinned(viewport = 494f, title = 72f, text = 96f, hint = 65f))
        // exactly as high as the room — it stands
        assertFalse(hintPinned(viewport = 225f, title = 56f, text = 75f, hint = 70f))
    }

    @Test
    fun `a hint that would push the title out stays under the words`() {
        // de at the font 1.3 on 603 × 308: the title 73, the hint 130 — pinned, the title would not stand above it
        assertFalse(hintPinned(viewport = 172f, title = 73f, text = 230f, hint = 130f))
        // on the edge — the title just whole above it: pinned
        assertTrue(hintPinned(viewport = 172f, title = 73f, text = 230f, hint = 85f))
    }

    private fun choiceFirst(viewport: Float, title: Float, text: Float) =
        SetupWordsMath.choiceFirst(viewport, title, text, choice = A4, titleToText = TITLE_TO_TEXT, toChoice = TO_CHOICE, fade = FADE)

    private fun hintPinned(viewport: Float, title: Float, text: Float, hint: Float) =
        SetupWordsMath.hintPinned(viewport, title, text, hint, titleToText = TITLE_TO_TEXT, toHint = TO_HINT)

    private companion object {
        /** The buttons of the reference 64, 18 under the words; the words 10 under the title; the hint 14 under what it explains; the fade 24 (5.29 R8). */
        const val A4 = 64f
        const val TO_CHOICE = 18f
        const val TITLE_TO_TEXT = 10f
        const val TO_HINT = 14f
        const val FADE = 24f
    }
}
