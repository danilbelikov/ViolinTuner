package com.violinjourney.app.feature.onboarding

/**
 * The words of a step of the setup in their room (spec 3.36.8, 5.29 R8): where the choice of «Эталон» and the hint of «Микрофон»
 * stand when the room is low — 640 × 360 lying is 603 × 308 under its bars, and there they opened under the edge of the words (the
 * review of stage 120). Pure, in the caller's units, with a test.
 */
internal object SetupWordsMath {
    /**
     * «Эталон»: the four buttons stand right under the title, and its words under them, where under the title and the words they would
     * not stand whole above the fade of the edge of the words ([fade]) as the step opens — the choice is what the step is for. In a
     * room where they do, the order of the spec: the title, the words, the buttons. [viewport] — the room of the words; [title],
     * [text], [choice] — the heights; [titleToText] and [toChoice] — the gaps over the words and over the buttons.
     */
    fun choiceFirst(viewport: Float, title: Float, text: Float, choice: Float, titleToText: Float, toChoice: Float, fade: Float): Boolean =
        title + titleToText + text + toChoice + choice > viewport - fade

    /**
     * «Микрофон»: the hint stands over the button, out of what scrolls, where under the words it would not stand whole in their room —
     * what comes after the system's question is said before it (3.36.8) — but only while the title still stands whole above it; else
     * it stays under the words and scrolls with them. [toHint] — the gap over the hint.
     */
    fun hintPinned(viewport: Float, title: Float, text: Float, hint: Float, titleToText: Float, toHint: Float): Boolean =
        title + titleToText + text + toHint + hint > viewport && title + toHint + hint <= viewport
}
