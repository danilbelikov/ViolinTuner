package com.violinjourney.app.feature.live.components

/**
 * How the two lines of a card of the bottom row of Live stand in it (spec 3.36.6, 5.29 R6: «Что играю» / «выбрать», «Начать» /
 * «занятие», the time, «ещё N мин»): at 13.5 and 12.5 sp where they fit on one line each; else both 0.5 sp smaller at a time, down to
 * 12; where they still do not, the first line goes on two lines at its spaces. The name of an element is one line ending in «…» and
 * makes nothing else give way; a time never wraps. No word breaks inside, anywhere.
 *
 * Beyond the spec, where even that leaves a word wider than the card (a single word at a large font, «ещё 12 мин» under the name of
 * an element): the lead gives the words its room — the same steps without it — and only then the second line goes on two lines at
 * its spaces, first with the lead and then without. Three lines stand closer than two where they must ([TIGHT_LINE_EM]). Where
 * nothing holds every word whole, the least size without the lead, a line that cannot wrap cut at its end ([lastResort]).
 *
 * Pure: the widths and heights come from the caller's text measurer, in its units (pixels); [Measure] says how the lines measure.
 */
internal object LiveCardWords {
    const val FIRST_SP = 13.5f
    const val SECOND_SP = 12.5f
    const val MIN_SP = 12f
    const val STEP_SP = 0.5f

    /** The lines of a card at 1.2 of their size (live.html, `.chipx`)… */
    const val LINE_EM = 1.2f

    /** …closer where three lines must stand in the card. */
    const val TIGHT_LINE_EM = 1.1f

    /** A line of a card wraps into at most this many. */
    const val MOST_LINES = 2

    /** From here on the letters are of the scripts of China, Japan and Korea, whose lines break between them: no word to keep whole. */
    const val CJK_FROM = '\u2E80'

    /** The words of [text] that must stand whole in a line: between its spaces, and not of a script whose lines break between letters. */
    fun wholeWords(text: String): List<String> = text.split(' ').filter { word -> word.isNotEmpty() && word.none { it >= CJK_FROM } }

    /** How the first line gives way: it wraps at its spaces; or — the name of an element — ends in «…»; or — a time — neither. */
    enum class First { WRAPS, ELLIPSIS, WHOLE }

    /** A line at a size: its [width] on one line and the width of its widest [word]. */
    class Line(val width: Float, val word: Float)

    /**
     * What the caller measures: each line at a size ([first], [second]); how many lines a line takes wrapped at its spaces in a room
     * ([wrapped], 0 — the first, 1 — the second); the height of a line of a size at a spacing in ems ([lineHeight]).
     */
    class Measure(
        val first: (sizeSp: Float) -> Line,
        val second: (sizeSp: Float) -> Line,
        val wrapped: (which: Int, sizeSp: Float, room: Float) -> Int,
        val lineHeight: (sizeSp: Float, em: Float) -> Float,
    )

    /** How the lines stand: with the lead or without, their sizes, how many lines each takes, their spacing and their height. */
    data class Fit(
        val lead: Boolean,
        val firstSp: Float,
        val secondSp: Float,
        val firstLines: Int,
        val secondLines: Int,
        val lineEm: Float,
        val height: Float,
    )

    /** The sizes of the two lines, largest first: 13.5 / 12.5, 13 / 12, 12.5 / 12, 12 / 12. */
    val steps: List<Pair<Float, Float>> = buildList {
        var first = FIRST_SP
        var second = SECOND_SP
        while (true) {
            add(first to second)
            if (first <= MIN_SP && second <= MIN_SP) break
            first = maxOf(MIN_SP, first - STEP_SP)
            second = maxOf(MIN_SP, second - STEP_SP)
        }
    }

    /**
     * The lines of a card [height] high: [leadRoom] — the room of the words beside the lead (null where the card is too narrow for
     * it), [bareRoom] — without it; [first] — how the first line gives way.
     */
    fun fit(leadRoom: Float?, bareRoom: Float, height: Float, first: First, measure: Measure): Fit {
        val rooms = listOfNotNull(leadRoom?.let { true to it }, false to bareRoom)
        for ((lead, room) in rooms) {
            oneLineEach(lead, room, height, first, measure)?.let { return it }
            firstWrapped(lead, room, height, first, measure)?.let { return it }
        }
        for ((lead, room) in rooms) secondWrapped(lead, room, height, first, measure)?.let { return it }
        return lastResort(bareRoom, height, first, measure)
    }

    /**
     * The bottom padding that keeps the lines [linesHeight] high over a brass line along the bottom of a card [cardHeight] high:
     * the lines stand in the middle of the card, and only where they would come nearer to the line than [clear] (from the top of
     * the card) they stand up to it. Null — they need the whole card and the brass line gives way (a large font, three lines).
     */
    fun liftOverBar(linesHeight: Float, cardHeight: Float, clear: Float): Float? =
        if (linesHeight > clear) null else maxOf(0f, cardHeight + linesHeight - 2 * clear)

    private fun oneLineEach(lead: Boolean, room: Float, height: Float, first: First, measure: Measure): Fit? {
        for ((firstSp, secondSp) in steps) {
            val firstStands = first == First.ELLIPSIS || measure.first(firstSp).width <= room
            if (firstStands && measure.second(secondSp).width <= room) {
                stood(lead, firstSp, secondSp, 1, 1, height, measure)?.let { return it }
            }
        }
        return null
    }

    private fun firstWrapped(lead: Boolean, room: Float, height: Float, first: First, measure: Measure): Fit? {
        if (first != First.WRAPS) return null
        if (measure.first(MIN_SP).word > room || measure.second(MIN_SP).width > room) return null
        val lines = measure.wrapped(0, MIN_SP, room)
        if (lines > MOST_LINES) return null
        return stood(lead, MIN_SP, MIN_SP, lines, 1, height, measure)
    }

    private fun secondWrapped(lead: Boolean, room: Float, height: Float, first: First, measure: Measure): Fit? {
        if (first != First.ELLIPSIS && measure.first(MIN_SP).width > room) return null
        if (measure.second(MIN_SP).word > room) return null
        val lines = measure.wrapped(1, MIN_SP, room)
        if (lines > MOST_LINES) return null
        return stood(lead, MIN_SP, MIN_SP, 1, lines, height, measure)
    }

    /**
     * Nothing holds every word whole: the least size without the lead; a line that wraps goes on as many lines as it takes, up to
     * two, where its words fit; a line whose word does not fit even alone stays one line, cut at its end — never broken inside.
     */
    private fun lastResort(room: Float, height: Float, first: First, measure: Measure): Fit {
        val a = measure.first(MIN_SP)
        val b = measure.second(MIN_SP)
        val firstLines = if (first == First.WRAPS && a.word <= room) measure.wrapped(0, MIN_SP, room).coerceIn(1, MOST_LINES) else 1
        val secondLines = if (b.word <= room) measure.wrapped(1, MIN_SP, room).coerceIn(1, MOST_LINES) else 1
        return stood(false, MIN_SP, MIN_SP, firstLines, secondLines, height, measure)
            ?: Fit(false, MIN_SP, MIN_SP, firstLines, secondLines, TIGHT_LINE_EM, linesHeight(firstLines, secondLines, MIN_SP, MIN_SP, TIGHT_LINE_EM, measure))
    }

    /** The lines at their spacing where they stand in [height], closer where only so they do; else null. */
    private fun stood(lead: Boolean, firstSp: Float, secondSp: Float, firstLines: Int, secondLines: Int, height: Float, measure: Measure): Fit? {
        for (em in listOf(LINE_EM, TIGHT_LINE_EM)) {
            val lines = linesHeight(firstLines, secondLines, firstSp, secondSp, em, measure)
            if (lines <= height) return Fit(lead, firstSp, secondSp, firstLines, secondLines, em, lines)
        }
        return null
    }

    private fun linesHeight(firstLines: Int, secondLines: Int, firstSp: Float, secondSp: Float, em: Float, measure: Measure): Float =
        firstLines * measure.lineHeight(firstSp, em) + secondLines * measure.lineHeight(secondSp, em)
}
